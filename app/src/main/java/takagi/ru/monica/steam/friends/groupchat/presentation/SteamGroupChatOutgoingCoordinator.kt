package takagi.ru.monica.steam.friends.groupchat.presentation

import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.diagnostics.SteamDiagLogger
import takagi.ru.monica.steam.friends.groupchat.data.SteamGroupChatOutbox
import takagi.ru.monica.steam.friends.groupchat.domain.SteamGroupChatDeliveryState
import takagi.ru.monica.steam.friends.groupchat.domain.SteamGroupChatGateway
import takagi.ru.monica.steam.friends.groupchat.domain.SteamGroupChatMessage
import takagi.ru.monica.steam.friends.groupchat.domain.mergeSteamGroupMessages
import takagi.ru.monica.steam.outbox.domain.SteamOutboxStatus
import takagi.ru.monica.steam.session.domain.SteamAccountSessionResolver
import takagi.ru.monica.steam.session.domain.resolveOrKeep

internal class SteamGroupChatOutgoingCoordinator(
    private val scope: CoroutineScope,
    private val gateway: SteamGroupChatGateway,
    private val sessionResolver: SteamAccountSessionResolver?,
    private val ioDispatcher: CoroutineDispatcher,
    private val outbox: SteamGroupChatOutbox? = null
) {
    private val jobs = mutableMapOf<String, Job>()

    fun dispatch(
        account: SteamAccount,
        accountKey: String = "${account.id}|${account.steamId}",
        pending: SteamGroupChatMessage,
        verifyBeforeSend: Boolean,
        forceRetry: Boolean = false,
        isCurrent: () -> Boolean,
        onSessionRefreshed: (SteamAccount) -> Unit,
        onUpdate: (SteamGroupChatMessage) -> Unit
    ) {
        if (jobs[pending.clientMessageId]?.isActive == true) return
        val job = scope.launch {
            val outboxRecord = runGroupChatCatching {
                withContext(ioDispatcher) { outbox?.enqueue(account, pending, accountKey) }
            }.onFailure { logGroupChatSendFailure("outbox_enqueue", it) }.getOrElse {
                if (isCurrent()) {
                    onUpdate(pending.copy(deliveryState = SteamGroupChatDeliveryState.FAILED_RETRYABLE))
                }
                return@launch
            }
            if (outboxRecord?.status == SteamOutboxStatus.COMPLETED) {
                if (isCurrent()) onUpdate(pending.copy(deliveryState = SteamGroupChatDeliveryState.SENT))
                return@launch
            }
            if (outboxRecord?.status in TERMINAL_GROUP_OUTBOX_FAILURES) {
                if (isCurrent()) {
                    onUpdate(pending.copy(deliveryState = SteamGroupChatDeliveryState.FAILED_PERMANENT))
                }
                return@launch
            }
            val needsVerification = verifyBeforeSend ||
                outboxRecord?.status == SteamOutboxStatus.IN_FLIGHT ||
                outboxRecord?.status == SteamOutboxStatus.AWAITING_CONFIRMATION
            if (needsVerification) {
                verify(account, pending, onSessionRefreshed)?.let { confirmed ->
                    completeOutbox(pending.clientMessageId)
                    if (isCurrent()) onUpdate(confirmed)
                    return@launch
                }
                if (outboxRecord?.status == SteamOutboxStatus.IN_FLIGHT ||
                    outboxRecord?.status == SteamOutboxStatus.AWAITING_CONFIRMATION
                ) {
                    val retryStatus = retryOutbox(
                        pending.clientMessageId,
                        "unconfirmed-after-restart"
                    )
                    if (isCurrent()) {
                        onUpdate(
                            pending.copy(deliveryState = retryStatus.toGroupFailureState())
                        )
                    }
                    return@launch
                }
            }
            if (outbox != null) {
                val claimed = runGroupChatCatching {
                    withContext(ioDispatcher) {
                        outbox.claim(pending.clientMessageId, force = forceRetry)
                    }
                }.onFailure { logGroupChatSendFailure("outbox_claim", it) }.isSuccess
                if (!claimed) {
                    if (isCurrent()) {
                        onUpdate(pending.copy(deliveryState = SteamGroupChatDeliveryState.FAILED_RETRYABLE))
                    }
                    return@launch
                }
            }
            if (isCurrent()) {
                onUpdate(pending.copy(deliveryState = SteamGroupChatDeliveryState.SENDING))
            }
            val result = runGroupChatCatching {
                withContext(ioDispatcher) {
                    val resolved = sessionResolver.resolveOrKeep(account)
                    if (hasGroupSessionChanged(account, resolved)) onSessionRefreshed(resolved)
                    gateway.sendMessage(
                        resolved,
                        pending.groupId,
                        pending.chatId,
                        pending.body
                    )
                }
            }
            result.getOrNull()?.let { response ->
                completeOutbox(pending.clientMessageId)
                if (isCurrent()) onUpdate(response.asConfirmedGroupEchoOf(pending))
                return@launch
            }
            val error = result.exceptionOrNull() ?: return@launch
            logGroupChatSendFailure("send", error)
            if (error is IOException) {
                runGroupChatCatching {
                    withContext(ioDispatcher) {
                        outbox?.awaitingConfirmation(pending.clientMessageId)
                    }
                }.onFailure { logGroupChatSendFailure("outbox_await_confirmation", it) }
                val verifying = pending.copy(deliveryState = SteamGroupChatDeliveryState.VERIFYING)
                if (isCurrent()) onUpdate(verifying)
                val verified = verify(account, verifying, onSessionRefreshed)
                if (verified != null) {
                    completeOutbox(pending.clientMessageId)
                    if (isCurrent()) onUpdate(verified)
                } else {
                    val retryStatus = retryOutbox(
                        pending.clientMessageId,
                        error.groupOutboxDiagnostic()
                    )
                    if (isCurrent()) {
                        onUpdate(
                            verifying.copy(deliveryState = retryStatus.toGroupFailureState())
                        )
                    }
                }
            } else {
                runGroupChatCatching {
                    withContext(ioDispatcher) {
                        outbox?.permanentFailure(
                            pending.clientMessageId,
                            error.groupOutboxDiagnostic()
                        )
                    }
                }.onFailure { logGroupChatSendFailure("outbox_permanent_failure", it) }
                if (isCurrent()) {
                    onUpdate(pending.copy(deliveryState = SteamGroupChatDeliveryState.FAILED_PERMANENT))
                }
            }
        }
        jobs[pending.clientMessageId] = job
        job.invokeOnCompletion { jobs.remove(pending.clientMessageId, job) }
    }

    private suspend fun verify(
        account: SteamAccount,
        pending: SteamGroupChatMessage,
        onSessionRefreshed: (SteamAccount) -> Unit
    ): SteamGroupChatMessage? {
        val page = runGroupChatCatching {
            withContext(ioDispatcher) {
                val resolved = sessionResolver.resolveOrKeep(account)
                if (hasGroupSessionChanged(account, resolved)) onSessionRefreshed(resolved)
                gateway.getHistory(resolved, pending.groupId, pending.chatId)
            }
        }.onFailure { logGroupChatSendFailure("send_verify", it) }.getOrNull() ?: return null
        return mergeSteamGroupMessages(listOf(pending), page.messages)
            .firstOrNull {
                it.clientMessageId == pending.clientMessageId && it.ordinal != Int.MAX_VALUE
            }
            ?.copy(deliveryState = SteamGroupChatDeliveryState.SENT)
    }

    private suspend fun completeOutbox(clientMessageId: String) {
        runGroupChatCatching {
            withContext(ioDispatcher) { outbox?.complete(clientMessageId) }
        }.onFailure { logGroupChatSendFailure("outbox_complete", it) }
    }

    private suspend fun retryOutbox(
        clientMessageId: String,
        error: String?
    ): SteamOutboxStatus? = runGroupChatCatching {
        withContext(ioDispatcher) { outbox?.retry(clientMessageId, error) }
    }.onFailure { logGroupChatSendFailure("outbox_retry", it) }
        .getOrNull()
        ?.status
}

private fun SteamGroupChatMessage.asConfirmedGroupEchoOf(
    pending: SteamGroupChatMessage
): SteamGroupChatMessage = copy(
    clientMessageId = pending.clientMessageId,
    localCreatedAtMillis = pending.localCreatedAtMillis,
    deliveryState = SteamGroupChatDeliveryState.SENT
)

private fun hasGroupSessionChanged(previous: SteamAccount, current: SteamAccount): Boolean =
    previous.accessToken != current.accessToken ||
        previous.refreshToken != current.refreshToken ||
        previous.steamLoginSecure != current.steamLoginSecure

private fun Throwable.groupOutboxDiagnostic(): String =
    javaClass.simpleName.ifBlank { "SteamGroupWriteFailure" }.take(96)

private val TERMINAL_GROUP_OUTBOX_FAILURES = setOf(
    SteamOutboxStatus.PERMANENT_FAILURE,
    SteamOutboxStatus.CANCELLED
)

private fun SteamOutboxStatus?.toGroupFailureState(): SteamGroupChatDeliveryState =
    if (this == SteamOutboxStatus.PERMANENT_FAILURE || this == SteamOutboxStatus.CANCELLED) {
        SteamGroupChatDeliveryState.FAILED_PERMANENT
    } else {
        SteamGroupChatDeliveryState.FAILED_RETRYABLE
    }

internal suspend inline fun <T> runGroupChatCatching(
    crossinline block: suspend () -> T
): Result<T> = try {
    Result.success(block())
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (error: Throwable) {
    Result.failure(error)
}

internal fun logGroupChatSendFailure(operation: String, error: Throwable) {
    runCatching {
        SteamDiagLogger.append(
            "group_chat $operation failed type=" +
                error.javaClass.simpleName.ifBlank { "Unknown" }.take(96)
        )
    }
}
