package takagi.ru.monica.steam.network.optimization.domain

data class SteamHostsRule(
    val hostname: String,
    val addresses: List<String>
) {
    val targetCount: Int get() = addresses.size
}
