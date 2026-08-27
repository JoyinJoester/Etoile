package takagi.ru.monica.steam.network.cm

import takagi.ru.monica.steam.data.SteamAccount

/** Stable routing key used by the shared CM pool and its realtime event stream. */
internal fun steamCmAccountKey(account: SteamAccount): String =
    "${account.id}|${account.steamId}"
