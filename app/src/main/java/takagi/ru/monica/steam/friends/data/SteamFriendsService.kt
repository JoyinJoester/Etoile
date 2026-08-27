package takagi.ru.monica.steam.friends.data

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.diagnostics.SteamDiagLogger
import takagi.ru.monica.steam.friends.domain.SteamFriend
import takagi.ru.monica.steam.friends.domain.SteamFriendActionResult
import takagi.ru.monica.steam.friends.domain.SteamFriendRelationship
import takagi.ru.monica.steam.friends.domain.SteamFriendRelationshipAction
import takagi.ru.monica.steam.friends.domain.SteamFriendsGateway
import takagi.ru.monica.steam.friends.domain.SteamFriendsSnapshot
import takagi.ru.monica.steam.friends.domain.SteamPersonaState
import takagi.ru.monica.steam.friends.nickname.data.SteamFriendNicknameService
import takagi.ru.monica.steam.friends.nickname.domain.SteamFriendNicknameGateway
import takagi.ru.monica.steam.market.SteamInventoryService
import takagi.ru.monica.steam.network.SteamApiClient
import takagi.ru.monica.steam.network.SteamProtoReader
import takagi.ru.monica.steam.network.SteamProtoWriter
import takagi.ru.monica.steam.network.cm.SteamCmClient
import takagi.ru.monica.steam.network.cm.SteamCmGateway
import takagi.ru.monica.steam.network.cm.SteamCmProtocol

class SteamFriendsService(
    private val api: SteamApiClient = SteamApiClient(),
    private val cm: SteamCmGateway = SteamCmClient(),
    private val nicknameGateway: SteamFriendNicknameGateway = SteamFriendNicknameService(
        cm = cm,
        api = api
    ),
    private val inviteLinkResolver: SteamFriendInviteLinkResolver =
        SteamFriendInviteLinkRedirectResolver()
) : SteamFriendsGateway {
    override fun fetch(account: SteamAccount, fetchedAt: Long): SteamFriendsSnapshot {
        require(account.hasRealSteamId) { "real Steam ID required" }
        val accessToken = account.accessToken?.takeIf(String::isNotBlank)
            ?: throw IllegalStateException("Steam access token required")
        val relationshipsPayload = api.steamApiGetJson(
            path = "/ISteamUserOAuth/GetFriendList/v1/",
            query = linkedMapOf(
                "steamid" to account.steamId,
                "relationship" to "all"
            ),
            accessToken = accessToken
        )
        val relationships = SteamFriendsParser.parseRelationships(relationshipsPayload)
        fetchPendingIncomingSteamIds(account).forEach { steamId ->
            if (steamId == account.steamId) return@forEach
            val existing = relationships[steamId]
            relationships[steamId] = SteamFriendRelationshipRecord(
                steamId = steamId,
                relationship = SteamFriendRelationship.REQUEST_INCOMING,
                friendSince = existing?.friendSince ?: 0L,
                nickname = existing?.nickname.orEmpty()
            )
        }
        if (relationships.isEmpty()) return SteamFriendsSnapshot(fetchedAt = fetchedAt)

        val profiles = relationships.keys.chunked(MAX_PROFILE_BATCH).flatMap { steamIds ->
            runCatching {
                val profilePayload = api.steamApiGetJson(
                    path = "/ISteamUserOAuth/GetUserSummaries/v1/",
                    query = mapOf("steamids" to steamIds.joinToString(",")),
                    accessToken = accessToken
                )
                SteamFriendsParser.parseProfiles(profilePayload)
            }.getOrDefault(emptyList())
        }.associateBy(SteamFriendProfile::steamId)

        val nicknames = runCatching { nicknameGateway.fetch(account) }
            .onFailure { error ->
                SteamDiagLogger.append(
                    "friends nickname_sync failed type=${error.javaClass.simpleName}"
                )
            }
            .getOrDefault(emptyMap())

        return SteamFriendsSnapshot(
            friends = SteamFriendsParser.merge(relationships, profiles, nicknames),
            fetchedAt = fetchedAt
        )
    }

    private fun fetchPendingIncomingSteamIds(account: SteamAccount): List<String> = runCatching {
        val sessionId = SteamInventoryService.newSessionId()
        val html = api.communityGetText(
            path = "/my/friends/pending",
            query = mapOf("l" to "english"),
            cookies = SteamInventoryService.marketCookies(account, sessionId),
            referer = "https://steamcommunity.com/my/friends/"
        )
        SteamPendingFriendRequestsParser.parseSteamIds(html)
    }.onFailure { error ->
        SteamDiagLogger.append(
            "friends pending_sync failed type=${error.javaClass.simpleName}"
        )
    }.getOrDefault(emptyList())

    override fun respondToInvite(
        account: SteamAccount,
        friendSteamId: String,
        accept: Boolean
    ): SteamFriendActionResult {
        require(account.hasRealSteamId) { "real Steam ID required" }
        require(friendSteamId.matches(Regex("7656119\\d{10}"))) { "valid friend Steam ID required" }
        require(
            !account.steamLoginSecure.isNullOrBlank() || !account.accessToken.isNullOrBlank()
        ) { "Steam community session required" }
        val sessionId = SteamInventoryService.newSessionId()
        val path = if (accept) "/actions/AddFriendAjax" else "/actions/IgnoreFriendInviteAjax"
        val form = linkedMapOf(
            "sessionID" to listOf(sessionId),
            "sessionid" to listOf(sessionId),
            "steamid" to listOf(friendSteamId)
        )
        if (accept) form["accept_invite"] = listOf("1")
        val payload = api.communityPostJson(
            path = path,
            form = form,
            cookies = SteamInventoryService.marketCookies(account, sessionId),
            referer = "https://steamcommunity.com/my/friends/pending"
        )
        val success = payload["success"]?.let { payload.successCode() == 1 } ?: true
        return SteamFriendActionResult(
            success = success,
            message = payload.text("error")
                .ifBlank { payload.text("message") }
                .ifBlank { payload.text("results") }
                .takeIf(String::isNotBlank)
        )
    }

    override fun changeRelationship(
        account: SteamAccount,
        friendSteamId: String,
        action: SteamFriendRelationshipAction
    ): SteamFriendActionResult {
        require(account.hasRealSteamId) { "real Steam ID required" }
        val friendId = friendSteamId.toSteamId64()
        val accessToken = account.accessToken?.takeIf(String::isNotBlank)
            ?: throw IllegalStateException("Steam access token required")
        val request = when (action) {
            SteamFriendRelationshipAction.ADD -> SteamCmFriendAction(
                SteamCmProtocol.EMSG_CLIENT_ADD_FRIEND,
                SteamCmProtocol.EMSG_CLIENT_ADD_FRIEND_RESPONSE,
                SteamProtoWriter().apply { writeFixed64(1, friendId) }.toByteArray()
            )
            SteamFriendRelationshipAction.REMOVE -> SteamCmFriendAction(
                SteamCmProtocol.EMSG_CLIENT_REMOVE_FRIEND,
                SteamCmProtocol.EMSG_CLIENT_FRIENDS_LIST,
                SteamProtoWriter().apply { writeFixed64(1, friendId) }.toByteArray()
            )
            SteamFriendRelationshipAction.BLOCK,
            SteamFriendRelationshipAction.UNBLOCK -> SteamCmFriendAction(
                SteamCmProtocol.EMSG_CLIENT_HIDE_FRIEND,
                SteamCmProtocol.EMSG_CLIENT_FRIENDS_LIST,
                SteamProtoWriter().apply {
                    writeFixed64(1, friendId)
                    writeBool(2, action == SteamFriendRelationshipAction.BLOCK)
                }.toByteArray()
            )
        }
        val body = cm.exchangeClientMessage(
            account,
            requestEMsg = request.requestEMsg,
            responseEMsg = request.responseEMsg,
            request = request.body
        )
        val eresult = if (action == SteamFriendRelationshipAction.ADD) {
            SteamProtoReader(body).parse()[1]?.asInt ?: 2
        } else {
            null
        }
        return SteamFriendActionResult(
            success = eresult == null || eresult == 1,
            message = eresult?.takeIf { it != 1 }?.let { "Steam result $it" }
        )
    }

    override fun findCandidates(
        account: SteamAccount,
        query: String
    ): List<SteamFriend> {
        require(account.hasRealSteamId) { "real Steam ID required" }
        val accessToken = account.accessToken?.takeIf(String::isNotBlank)
            ?: throw IllegalStateException("Steam access token required")
        val normalizedQuery = query.trim()
        if (normalizedQuery.isBlank()) return emptyList()
        val sessionId = SteamInventoryService.newSessionId()
        val cookies = SteamInventoryService.marketCookies(account, sessionId)
        val hits = when (val lookup = SteamFriendDiscoveryParser.classify(normalizedQuery)) {
            is SteamFriendLookup.SteamId -> listOf(
                SteamFriendSearchHit(
                    steamId = lookup.value,
                    personaName = "",
                    avatarUrl = "",
                    profileUrl = "https://steamcommunity.com/profiles/${lookup.value}/"
                )
            )
            is SteamFriendLookup.VanityName -> {
                val html = api.communityGetText(
                    path = "/id/${lookup.value}/",
                    query = mapOf("xml" to "1"),
                    cookies = cookies
                )
                SteamFriendDiscoveryParser.parseProfileSteamId(html)?.let { steamId ->
                    listOf(
                        SteamFriendSearchHit(
                            steamId = steamId,
                            personaName = "",
                            avatarUrl = "",
                            profileUrl = "https://steamcommunity.com/id/${lookup.value}/"
                        )
                    )
                }.orEmpty()
            }
            is SteamFriendLookup.QuickInvite -> {
                val target = inviteLinkResolver.resolve(lookup.url)
                val html = api.communityGetText(
                    path = target.encodedPath,
                    query = target.queryParameterNames.associateWith { name ->
                        target.queryParameter(name).orEmpty()
                    },
                    cookies = cookies,
                    referer = lookup.url.takeIf { it.startsWith("https://steamcommunity.com/") }
                )
                SteamFriendDiscoveryParser.parseProfileSteamId(
                    payload = html,
                    baseUrl = target.toString(),
                    excludedSteamId = account.steamId
                )?.let { steamId ->
                    listOf(
                        SteamFriendSearchHit(
                            steamId = steamId,
                            personaName = "",
                            avatarUrl = "",
                            profileUrl = "https://steamcommunity.com/profiles/$steamId/"
                        )
                    )
                }.orEmpty()
            }
            is SteamFriendLookup.PersonaName -> searchCommunity(
                account = account,
                query = lookup.value,
                sessionId = sessionId,
                cookies = cookies
            )
        }.filterNot { it.steamId == account.steamId }
            .distinctBy(SteamFriendSearchHit::steamId)
            .take(MAX_SEARCH_RESULTS)
        if (hits.isEmpty()) return emptyList()

        val profiles = fetchProfiles(
            accessToken = accessToken,
            steamIds = hits.map(SteamFriendSearchHit::steamId)
        ).associateBy(SteamFriendProfile::steamId)
        return hits.map { hit ->
            val profile = profiles[hit.steamId]
            SteamFriend(
                steamId = hit.steamId,
                personaName = profile?.personaName.orEmpty().ifBlank { hit.personaName },
                realName = profile?.realName.orEmpty(),
                avatarUrl = profile?.avatarUrl.orEmpty().ifBlank { hit.avatarUrl },
                profileUrl = profile?.profileUrl.orEmpty().ifBlank { hit.profileUrl },
                personaState = profile?.personaState
                    ?: SteamPersonaState.OFFLINE,
                lastLogoff = profile?.lastLogoff ?: 0L,
                gameId = profile?.gameId.orEmpty(),
                gameName = profile?.gameName.orEmpty(),
                primaryClanId = profile?.primaryClanId.orEmpty(),
                countryCode = profile?.countryCode.orEmpty()
            )
        }
    }

    private fun searchCommunity(
        account: SteamAccount,
        query: String,
        sessionId: String,
        cookies: Map<String, String>
    ): List<SteamFriendSearchHit> {
        if (query.isBlank()) return emptyList()
        val payload = api.communityGetJson(
            path = "/search/SearchCommunityAjax",
            query = linkedMapOf(
                "text" to query.take(MAX_SEARCH_QUERY_LENGTH),
                "filter" to "users",
                "sessionid" to sessionId,
                "steamid_user" to account.steamId,
                "page" to "1"
            ),
            cookies = cookies,
            referer = "https://steamcommunity.com/search/users/"
        )
        return SteamFriendDiscoveryParser.parseSearchHtml(payload.text("html"))
    }

    private fun fetchProfiles(
        accessToken: String,
        steamIds: List<String>
    ): List<SteamFriendProfile> = steamIds.chunked(MAX_PROFILE_BATCH).flatMap { batch ->
        val payload = api.steamApiGetJson(
            path = "/ISteamUserOAuth/GetUserSummaries/v1/",
            query = mapOf("steamids" to batch.joinToString(",")),
            accessToken = accessToken
        )
        SteamFriendsParser.parseProfiles(payload)
    }

    private fun String.toSteamId64(): Long {
        require(matches(Regex("7656119\\d{10}"))) { "valid friend Steam ID required" }
        return toLong()
    }

    private data class SteamCmFriendAction(
        val requestEMsg: Int,
        val responseEMsg: Int,
        val body: ByteArray
    )

    private fun JsonObject.successCode(): Int {
        val primitive = this["success"] as? JsonPrimitive ?: return 0
        return primitive.intOrNull
            ?: primitive.contentOrNull?.toIntOrNull()
            ?: if (primitive.booleanOrNull == true) 1 else 0
    }

    private fun JsonObject.text(key: String): String =
        (this[key] as? JsonPrimitive)?.contentOrNull.orEmpty()

    private companion object {
        const val MAX_PROFILE_BATCH = 100
        const val MAX_SEARCH_RESULTS = 20
        const val MAX_SEARCH_QUERY_LENGTH = 100
    }
}
