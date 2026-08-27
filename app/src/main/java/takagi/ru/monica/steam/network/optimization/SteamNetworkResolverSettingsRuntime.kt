package takagi.ru.monica.steam.network.optimization

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import takagi.ru.monica.steam.diagnostics.SteamDiagLogger
import takagi.ru.monica.steam.network.SteamHttpClientProvider
import takagi.ru.monica.steam.network.optimization.domain.SteamDnsOptimizationScanResult
import takagi.ru.monica.steam.network.optimization.domain.SteamDnsProvider
import takagi.ru.monica.steam.network.optimization.domain.SteamNetworkResolverSettings
import takagi.ru.monica.steam.network.optimization.domain.SteamResolverInputValidator

object SteamNetworkResolverSettingsRuntime {
    private const val PREFERENCES_NAME = "steam_network_optimization"
    private const val KEY_USE_SYSTEM_DNS = "resolver_use_system_dns"
    private const val KEY_USE_BUILT_IN_DOH = "resolver_use_built_in_doh"
    private const val KEY_CUSTOM_DNS = "resolver_custom_dns"
    private const val KEY_CUSTOM_DOH = "resolver_custom_doh"
    private const val KEY_CUSTOM_DOH_BOOTSTRAP = "resolver_custom_doh_bootstrap"
    private const val KEY_PREFERRED_PROVIDER_IDS = "resolver_preferred_provider_ids"
    private const val KEY_DYNAMIC_DNS_ENABLED = "resolver_dynamic_dns_enabled"
    private const val KEY_DISABLED_BUILT_IN_PROVIDER_IDS = "resolver_disabled_builtin_provider_ids"
    private const val KEY_DISABLED_CUSTOM_PROVIDER_IDS = "resolver_disabled_custom_provider_ids"
    private const val KEY_PREFER_IPV6 = "resolver_prefer_ipv6"

    private val mutableSettings = MutableStateFlow(SteamNetworkResolverSettings())
    val settings: StateFlow<SteamNetworkResolverSettings> = mutableSettings.asStateFlow()

    @Volatile
    private var initialized = false
    private lateinit var preferences: SharedPreferences

    @Synchronized
    fun initialize(context: Context) {
        if (initialized) return
        preferences = context.applicationContext.getSharedPreferences(
            PREFERENCES_NAME,
            Context.MODE_PRIVATE
        )
        val validBuiltInIds = SteamDnsProvider.DEFAULTS
            .filterNot { it.isSystem }
            .map(SteamDnsProvider::id)
            .toSet()
        val customDnsServers = preferences.getStringSet(KEY_CUSTOM_DNS, emptySet())
            .orEmpty()
            .mapNotNull(SteamResolverInputValidator::normalizeDnsServer)
            .distinct()
            .sorted()
        val customDohEndpoints = preferences.getStringSet(KEY_CUSTOM_DOH, emptySet())
            .orEmpty()
            .mapNotNull(SteamResolverInputValidator::normalizeDohEndpoint)
            .distinct()
            .sorted()
        val customDohBootstrapAddresses = loadDohBootstrapAddresses(customDohEndpoints.toSet())
        val validCustomIds = buildSet {
            customDnsServers.mapTo(this) { SteamDnsProvider.customDns(it).id }
            customDohEndpoints.mapTo(this) { endpoint ->
                SteamDnsProvider.customDoh(
                    endpoint,
                    customDohBootstrapAddresses[endpoint].orEmpty()
                ).id
            }
        }
        updateSettings(
            SteamNetworkResolverSettings(
                useSystemDns = preferences.getBoolean(KEY_USE_SYSTEM_DNS, true),
                useBuiltInDoh = preferences.getBoolean(KEY_USE_BUILT_IN_DOH, true),
                customDnsServers = customDnsServers,
                customDohEndpoints = customDohEndpoints,
                customDohBootstrapAddresses = customDohBootstrapAddresses,
                preferredProviderIds = preferences.getString(KEY_PREFERRED_PROVIDER_IDS, "")
                    .orEmpty()
                    .lineSequence()
                    .map(String::trim)
                    .filter(String::isNotEmpty)
                    .distinct()
                    .toList(),
                dynamicDnsEnabled = preferences.getBoolean(KEY_DYNAMIC_DNS_ENABLED, false),
                disabledBuiltInProviderIds = preferences
                    .getStringSet(KEY_DISABLED_BUILT_IN_PROVIDER_IDS, emptySet())
                    .orEmpty()
                    .filterTo(linkedSetOf()) { it in validBuiltInIds },
                disabledCustomProviderIds = preferences
                    .getStringSet(KEY_DISABLED_CUSTOM_PROVIDER_IDS, emptySet())
                    .orEmpty()
                    .filterTo(linkedSetOf()) { it in validCustomIds },
                preferIpv6 = preferences.getBoolean(KEY_PREFER_IPV6, false)
            )
        )
        initialized = true
    }

    @Synchronized
    fun setDynamicDnsEnabled(context: Context, enabled: Boolean) {
        initialize(context)
        val acceptedEnabled = enabled && mutableSettings.value.hasResolver
        if (mutableSettings.value.dynamicDnsEnabled == acceptedEnabled) return
        preferences.edit().putBoolean(KEY_DYNAMIC_DNS_ENABLED, acceptedEnabled).apply()
        mutableSettings.value = mutableSettings.value.copy(dynamicDnsEnabled = acceptedEnabled)
        notifyResolverChanged()
        runCatching { SteamDiagLogger.append("dynamic_dns enabled=$acceptedEnabled") }
    }

    @Synchronized
    fun setPreferIpv6(context: Context, enabled: Boolean) {
        initialize(context)
        if (mutableSettings.value.preferIpv6 == enabled) return
        preferences.edit().putBoolean(KEY_PREFER_IPV6, enabled).apply()
        mutableSettings.value = mutableSettings.value.copy(preferIpv6 = enabled)
        notifyResolverChanged()
        runCatching { SteamDiagLogger.append("dynamic_dns prefer_ipv6=$enabled") }
    }

    @Synchronized
    fun setUseSystemDns(context: Context, enabled: Boolean) {
        initialize(context)
        preferences.edit().putBoolean(KEY_USE_SYSTEM_DNS, enabled).apply()
        updateSettings(mutableSettings.value.copy(useSystemDns = enabled))
        notifyResolverChanged()
    }

    @Synchronized
    fun setUseBuiltInDoh(context: Context, enabled: Boolean) {
        initialize(context)
        preferences.edit().putBoolean(KEY_USE_BUILT_IN_DOH, enabled).apply()
        updateSettings(mutableSettings.value.copy(useBuiltInDoh = enabled))
        notifyResolverChanged()
    }

    @Synchronized
    fun setBuiltInProviderEnabled(context: Context, providerId: String, enabled: Boolean) {
        initialize(context)
        val provider = SteamDnsProvider.DEFAULTS.firstOrNull {
            it.id == providerId && !it.isSystem
        } ?: return
        val allBuiltInIds = SteamDnsProvider.DEFAULTS
            .filterNot { it.isSystem }
            .map(SteamDnsProvider::id)
        val disabled = if (enabled && !mutableSettings.value.useBuiltInDoh) {
            allBuiltInIds.toMutableSet()
        } else {
            mutableSettings.value.disabledBuiltInProviderIds.toMutableSet()
        }
        if (enabled) disabled.remove(provider.id) else disabled.add(provider.id)
        val editor = preferences.edit()
            .putStringSet(KEY_DISABLED_BUILT_IN_PROVIDER_IDS, disabled)
        if (enabled) editor.putBoolean(KEY_USE_BUILT_IN_DOH, true)
        editor.apply()
        updateSettings(
            mutableSettings.value.copy(
                useBuiltInDoh = if (enabled) true else mutableSettings.value.useBuiltInDoh,
                disabledBuiltInProviderIds = disabled.toSet()
            )
        )
        notifyResolverChanged()
        runCatching {
            SteamDiagLogger.append(
                "dynamic_dns builtin_provider id=${provider.id} enabled=$enabled"
            )
        }
    }

    @Synchronized
    fun setCustomProviderEnabled(context: Context, providerId: String, enabled: Boolean) {
        initialize(context)
        val current = mutableSettings.value
        val customProviders = buildList {
            addAll(current.customDnsServers.map(SteamDnsProvider::customDns))
            addAll(
                current.customDohEndpoints.map { endpoint ->
                    SteamDnsProvider.customDoh(
                        endpoint,
                        current.customDohBootstrapAddresses[endpoint].orEmpty()
                    )
                }
            )
        }
        val provider = customProviders.firstOrNull { it.id == providerId } ?: return
        val disabled = current.disabledCustomProviderIds.toMutableSet()
        if (enabled) disabled.remove(provider.id) else disabled.add(provider.id)
        preferences.edit().putStringSet(KEY_DISABLED_CUSTOM_PROVIDER_IDS, disabled).apply()
        updateSettings(
            current.copy(
                disabledCustomProviderIds = disabled.toSet()
            )
        )
        notifyResolverChanged()
        runCatching {
            SteamDiagLogger.append(
                "dynamic_dns custom_provider id=${provider.id} enabled=$enabled"
            )
        }
    }

    @Synchronized
    fun addCustomDns(context: Context, raw: String): Boolean {
        initialize(context)
        val value = SteamResolverInputValidator.normalizeDnsServer(raw) ?: return false
        val current = mutableSettings.value.customDnsServers
        if (value in current || current.size >= SteamNetworkResolverSettings.MAX_CUSTOM_DNS) {
            return false
        }
        val updated = (current + value).distinct().sorted()
        saveStringSet(KEY_CUSTOM_DNS, updated)
        updateSettings(mutableSettings.value.copy(customDnsServers = updated))
        notifyResolverChanged()
        return true
    }

    @Synchronized
    fun removeCustomDns(context: Context, value: String) {
        initialize(context)
        val providerId = SteamDnsProvider.customDns(value).id
        val updated = mutableSettings.value.customDnsServers - value
        val disabled = mutableSettings.value.disabledCustomProviderIds - providerId
        val preferred = mutableSettings.value.preferredProviderIds - providerId
        preferences.edit()
            .putStringSet(KEY_CUSTOM_DNS, updated.toSet())
            .putStringSet(KEY_DISABLED_CUSTOM_PROVIDER_IDS, disabled)
            .putString(KEY_PREFERRED_PROVIDER_IDS, preferred.joinToString("\n"))
            .apply()
        updateSettings(
            mutableSettings.value.copy(
                customDnsServers = updated,
                disabledCustomProviderIds = disabled,
                preferredProviderIds = preferred
            )
        )
        notifyResolverChanged()
    }

    @Synchronized
    fun addCustomDoh(
        context: Context,
        raw: String,
        bootstrapRaw: String = ""
    ): Boolean {
        initialize(context)
        val value = SteamResolverInputValidator.normalizeDohEndpoint(raw) ?: return false
        val bootstrapAddresses =
            SteamResolverInputValidator.normalizeBootstrapAddresses(bootstrapRaw) ?: return false
        val currentSettings = mutableSettings.value
        val current = currentSettings.customDohEndpoints
        val existing = value in current
        if (!existing && current.size >= SteamNetworkResolverSettings.MAX_CUSTOM_DOH) {
            return false
        }
        if (existing && currentSettings.customDohBootstrapAddresses[value].orEmpty() == bootstrapAddresses) {
            return false
        }

        val updated = if (existing) current else (current + value).distinct().sorted()
        val updatedBootstrap = currentSettings.customDohBootstrapAddresses.toMutableMap()
        if (bootstrapAddresses.isEmpty()) {
            updatedBootstrap.remove(value)
        } else {
            updatedBootstrap[value] = bootstrapAddresses
        }
        preferences.edit()
            .putStringSet(KEY_CUSTOM_DOH, updated.toSet())
            .putStringSet(
                KEY_CUSTOM_DOH_BOOTSTRAP,
                SteamDohBootstrapPreferencesCodec.encode(updatedBootstrap)
            )
            .apply()
        updateSettings(
            currentSettings.copy(
                customDohEndpoints = updated,
                customDohBootstrapAddresses = updatedBootstrap.toMap()
            )
        )
        notifyResolverChanged()
        runCatching {
            SteamDiagLogger.append(
                "dynamic_dns custom_doh ${if (existing) "updated" else "added"} " +
                    "host=${SteamDnsProvider.customDoh(value).displayName} " +
                    "bootstrap=${bootstrapAddresses.size}"
            )
        }
        return true
    }

    @Synchronized
    fun removeCustomDoh(context: Context, value: String) {
        initialize(context)
        val providerId = SteamDnsProvider.customDoh(value).id
        val updated = mutableSettings.value.customDohEndpoints - value
        val updatedBootstrap = mutableSettings.value.customDohBootstrapAddresses - value
        val disabled = mutableSettings.value.disabledCustomProviderIds - providerId
        val preferred = mutableSettings.value.preferredProviderIds - providerId
        preferences.edit()
            .putStringSet(KEY_CUSTOM_DOH, updated.toSet())
            .putStringSet(
                KEY_CUSTOM_DOH_BOOTSTRAP,
                SteamDohBootstrapPreferencesCodec.encode(updatedBootstrap)
            )
            .putStringSet(KEY_DISABLED_CUSTOM_PROVIDER_IDS, disabled)
            .putString(KEY_PREFERRED_PROVIDER_IDS, preferred.joinToString("\n"))
            .apply()
        updateSettings(
            mutableSettings.value.copy(
                customDohEndpoints = updated,
                customDohBootstrapAddresses = updatedBootstrap,
                disabledCustomProviderIds = disabled,
                preferredProviderIds = preferred
            )
        )
        notifyResolverChanged()
    }

    @Synchronized
    fun applyScanPreference(
        context: Context,
        result: SteamDnsOptimizationScanResult
    ): Boolean {
        initialize(context)
        if (!result.isApplicable) return false

        val secureProvidersById = mutableSettings.value.activeProviders
            .filterNot { it.isSystem }
            .associateBy { it.id }
        if (secureProvidersById.isEmpty()) return false

        val latencySamplesByProvider = linkedMapOf<String, MutableList<Long>>()
        result.selectedRoutes.forEach { route ->
            route.providerIds.forEach { providerId ->
                if (providerId in secureProvidersById) {
                    latencySamplesByProvider
                        .getOrPut(providerId) { mutableListOf() }
                        .add(route.latencyMillis)
                }
            }
        }
        val preferredProviderIds = latencySamplesByProvider
            .map { (providerId, latencies) ->
                ProviderScore(
                    providerId = providerId,
                    routeCount = latencies.size,
                    averageLatencyMillis = latencies.average()
                )
            }
            .sortedWith(
                compareByDescending<ProviderScore> { it.routeCount }
                    .thenBy { it.averageLatencyMillis }
                    .thenBy { it.providerId }
            )
            .map(ProviderScore::providerId)

        if (preferredProviderIds.isEmpty()) return false
        preferences.edit()
            .putString(KEY_PREFERRED_PROVIDER_IDS, preferredProviderIds.joinToString("\n"))
            .putBoolean(KEY_DYNAMIC_DNS_ENABLED, true)
            .apply()
        mutableSettings.value = mutableSettings.value.copy(
            preferredProviderIds = preferredProviderIds,
            dynamicDnsEnabled = true
        )
        notifyResolverChanged()
        runCatching {
            SteamDiagLogger.append(
                "dynamic_dns preference_applied providers=${preferredProviderIds.joinToString(",")} " +
                    "routes=${result.selectedRoutes.size}"
            )
        }
        return true
    }

    @Synchronized
    fun clearScanPreference(context: Context) {
        initialize(context)
        if (mutableSettings.value.preferredProviderIds.isEmpty()) return
        preferences.edit().remove(KEY_PREFERRED_PROVIDER_IDS).apply()
        mutableSettings.value = mutableSettings.value.copy(preferredProviderIds = emptyList())
        notifyResolverChanged()
        runCatching { SteamDiagLogger.append("dynamic_dns preference_cleared") }
    }

    private fun loadDohBootstrapAddresses(validEndpoints: Set<String>): Map<String, List<String>> {
        return SteamDohBootstrapPreferencesCodec.decode(
            entries = preferences.getStringSet(KEY_CUSTOM_DOH_BOOTSTRAP, emptySet()).orEmpty(),
            validEndpoints = validEndpoints
        )
    }

    private fun saveStringSet(key: String, values: Collection<String>) {
        preferences.edit().putStringSet(key, values.toSet()).apply()
    }

    private fun updateSettings(next: SteamNetworkResolverSettings) {
        val accepted = if (next.dynamicDnsEnabled && !next.hasResolver) {
            preferences.edit().putBoolean(KEY_DYNAMIC_DNS_ENABLED, false).apply()
            next.copy(dynamicDnsEnabled = false)
        } else {
            next
        }
        mutableSettings.value = accepted
    }

    private fun notifyResolverChanged() {
        runCatching { SteamHttpClientProvider.onResolverSettingsChanged() }
    }

    private data class ProviderScore(
        val providerId: String,
        val routeCount: Int,
        val averageLatencyMillis: Double
    )
}

internal object SteamDohBootstrapPreferencesCodec {
    private const val ENTRY_SEPARATOR = "\t"

    fun decode(
        entries: Set<String>,
        validEndpoints: Set<String>
    ): Map<String, List<String>> {
        if (entries.isEmpty() || validEndpoints.isEmpty()) return emptyMap()
        val result = linkedMapOf<String, List<String>>()
        entries.forEach { entry ->
            val separator = entry.indexOf(ENTRY_SEPARATOR)
            if (separator <= 0) return@forEach
            val endpoint = entry.substring(0, separator)
            if (endpoint !in validEndpoints) return@forEach
            val addresses = SteamResolverInputValidator.normalizeBootstrapAddresses(
                entry.substring(separator + ENTRY_SEPARATOR.length)
            ) ?: return@forEach
            if (addresses.isNotEmpty()) result[endpoint] = addresses
        }
        return result
    }

    fun encode(values: Map<String, List<String>>): Set<String> =
        values.mapNotNullTo(linkedSetOf()) { (endpoint, addresses) ->
            val normalizedEndpoint = SteamResolverInputValidator.normalizeDohEndpoint(endpoint)
                ?: return@mapNotNullTo null
            val normalizedAddresses = SteamResolverInputValidator.normalizeBootstrapAddresses(
                addresses.joinToString(",")
            ) ?: return@mapNotNullTo null
            normalizedAddresses.takeIf { it.isNotEmpty() }?.let { accepted ->
                "$normalizedEndpoint$ENTRY_SEPARATOR${accepted.joinToString(",")}"
            }
        }
}
