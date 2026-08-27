package takagi.ru.monica.steam.friends.voice.background

import takagi.ru.monica.steam.friends.voice.domain.SteamVoiceCallState

internal enum class SteamVoiceCallServiceMode {
    IDLE,
    INCOMING,
    ACTIVE
}

internal fun SteamVoiceCallState.voiceServiceMode(): SteamVoiceCallServiceMode = when {
    isActive -> SteamVoiceCallServiceMode.ACTIVE
    incomingRequest != null -> SteamVoiceCallServiceMode.INCOMING
    else -> SteamVoiceCallServiceMode.IDLE
}
