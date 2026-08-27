package takagi.ru.monica.utils

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import takagi.ru.monica.R
import takagi.ru.monica.data.AppLauncherIcon
import takagi.ru.monica.data.AppLauncherLabel

object AppLauncherIconManager {
    private const val TAG = "AppLauncherIconManager"
    private const val STANDALONE_MAIN_ACTIVITY = "takagi.ru.monica.EtoileActivity"
    private const val LEGACY_MAIN_ACTIVITY = "takagi.ru.monica.MainActivity"
    private const val COMPAT_MODERN_ALIAS = "takagi.ru.monica.ModernLauncherAlias"
    private const val COMPAT_CLASSIC_ALIAS = "takagi.ru.monica.LockLauncherAlias"
    private const val HOME_MODERN_ALIAS = "takagi.ru.monica.ModernHomeLauncherAlias"
    private const val HOME_CLASSIC_ALIAS = "takagi.ru.monica.ClassicHomeLauncherAlias"
    private const val VISIBLE_MODERN_PASS_ALIAS = "takagi.ru.monica.ModernVisibleLauncherAlias"
    private const val VISIBLE_CLASSIC_PASS_ALIAS = "takagi.ru.monica.ClassicVisibleLauncherAlias"
    private const val VISIBLE_MODERN_MONICA_ALIAS = "takagi.ru.monica.ModernVisibleLauncherAliasMonica"
    private const val VISIBLE_CLASSIC_MONICA_ALIAS = "takagi.ru.monica.ClassicVisibleLauncherAliasMonica"

    fun apply(context: Context, icon: AppLauncherIcon, label: AppLauncherLabel) {
        repairCompatibilityLaunchTargets(context)
        applyVisibleLauncherSelection(context, label)
    }

    fun repairLegacyDisabledComponents(context: Context) {
        repairCompatibilityLaunchTargets(context)
    }

    fun repairLaunchEntryPointsAfterUpgrade(
        context: Context,
        icon: AppLauncherIcon,
        label: AppLauncherLabel
    ) {
        repairCompatibilityLaunchTargets(context)
        applyVisibleLauncherSelection(context, label)
    }

    fun getCurrentSelection(context: Context): AppLauncherIcon {
        return AppLauncherIcon.MODERN
    }

    fun resolveBrandingIconRes(context: Context): Int {
        return R.drawable.monica_launcher
    }

    fun applyBiometricPromptBranding(context: Context, promptInfoBuilder: Any) {
        val builderClass = promptInfoBuilder.javaClass
        val iconRes = resolveBrandingIconRes(context)

        runCatching {
            builderClass.methods.firstOrNull { method ->
                method.name == "setLogoRes" &&
                    method.parameterTypes.size == 1 &&
                    method.parameterTypes[0] == Int::class.javaPrimitiveType
            }?.invoke(promptInfoBuilder, iconRes)
        }

        runCatching {
            builderClass.methods.firstOrNull { method ->
                method.name == "setLogoDescription" &&
                    method.parameterTypes.size == 1 &&
                    CharSequence::class.java.isAssignableFrom(method.parameterTypes[0])
            }?.invoke(promptInfoBuilder, context.getString(R.string.app_name))
        }
    }

    private fun repairCompatibilityLaunchTargets(context: Context) {
        val packageManager = context.packageManager
        val components = listOf(
            component(context, STANDALONE_MAIN_ACTIVITY),
            component(context, LEGACY_MAIN_ACTIVITY),
            component(context, COMPAT_MODERN_ALIAS),
            component(context, COMPAT_CLASSIC_ALIAS),
            component(context, HOME_MODERN_ALIAS),
            component(context, HOME_CLASSIC_ALIAS)
        )

        filterDeclaredLauncherComponents(components) { launchComponent ->
            packageManager.hasDeclaredActivity(launchComponent)
        }
            .forEach { launchComponent ->
                packageManager.setComponentEnabledSettingSafely(
                    launchComponent,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP
                )
            }
    }

    private fun applyVisibleLauncherSelection(
        context: Context,
        label: AppLauncherLabel
    ) {
        val packageManager = context.packageManager
        val states = mapOf(
            component(context, VISIBLE_MODERN_PASS_ALIAS) to componentStateFor(
                label == AppLauncherLabel.MONICA_PASS
            ),
            component(context, VISIBLE_CLASSIC_PASS_ALIAS) to
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            component(context, VISIBLE_MODERN_MONICA_ALIAS) to componentStateFor(
                label == AppLauncherLabel.MONICA
            ),
            component(context, VISIBLE_CLASSIC_MONICA_ALIAS) to
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        ).filterKeys { launchComponent ->
            packageManager.hasDeclaredActivity(launchComponent)
        }

        if (states.isEmpty()) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.setComponentEnabledSettingsSafely(
                states.map { (launchComponent, state) ->
                    PackageManager.ComponentEnabledSetting(
                        launchComponent,
                        state,
                        PackageManager.DONT_KILL_APP
                    )
                }
            )
            return
        }

        states.forEach { (component, state) ->
            packageManager.setComponentEnabledSettingSafely(
                component,
                state,
                PackageManager.DONT_KILL_APP
            )
        }
    }

    private fun componentStateFor(shouldEnable: Boolean): Int {
        return if (shouldEnable) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }
    }

    private fun component(context: Context, className: String): ComponentName =
        ComponentName(context.packageName, className)

    /**
     * The standalone APK intentionally omits Monica's legacy launcher aliases.
     * Keep the filtering separate so a missing component can never reach the
     * PackageManager binder (Android 16 throws instead of ignoring it).
     */
    internal fun <T> filterDeclaredLauncherComponents(
        components: List<T>,
        isDeclared: (T) -> Boolean
    ): List<T> = components.filter { component ->
        runCatching { isDeclared(component) }.getOrDefault(false)
    }

    private fun PackageManager.hasDeclaredActivity(component: ComponentName): Boolean =
        runCatching {
            getActivityInfo(component, PackageManager.MATCH_DISABLED_COMPONENTS)
        }.isSuccess

    private fun PackageManager.setComponentEnabledSettingSafely(
        component: ComponentName,
        newState: Int,
        flags: Int
    ) {
        runCatching { setComponentEnabledSetting(component, newState, flags) }
            .onFailure { error ->
                Log.w(TAG, "Unable to update launcher component $component", error)
            }
    }

    private fun PackageManager.setComponentEnabledSettingsSafely(
        settings: List<PackageManager.ComponentEnabledSetting>
    ) {
        runCatching { setComponentEnabledSettings(settings) }
            .onFailure { error ->
                Log.w(TAG, "Unable to update launcher component batch", error)
                // Some OEM PackageManager implementations reject a batch even
                // when every component was declared. Fall back one-by-one.
                settings.forEach { setting ->
                    val component = setting.componentName ?: return@forEach
                    setComponentEnabledSettingSafely(
                        component,
                        setting.enabledState,
                        setting.enabledFlags
                    )
                }
            }
    }
}
