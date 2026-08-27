package takagi.ru.monica.ui.base

import android.content.Context
import android.os.Bundle
import android.os.Build
import android.view.View
import android.view.WindowManager
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import takagi.ru.monica.data.AppSettings
import takagi.ru.monica.data.Language
import takagi.ru.monica.utils.LocaleHelper
import takagi.ru.monica.utils.ScreenshotProtectionUtil
import takagi.ru.monica.utils.SettingsManager

/**
 * Etoile 的统一基类 Activity
 *
 * - attachBaseContext：按保存的语言偏好包裹 Locale 上下文，带超时保护
 * - Theme：监听 SettingsManager 并缓存设置，供主题与防截屏使用
 * - ScreenshotProtection：统一处理防截屏开关
 */
abstract class BaseMonicaActivity : FragmentActivity() {
    
    protected lateinit var settingsManager: SettingsManager
    
    // 缓存的设置，供子类使用
    protected var cachedSettings: AppSettings? = null
    
    override fun attachBaseContext(newBase: Context?) {
        if (newBase != null) {
            val tempSettingsManager = SettingsManager(newBase)
            // 使用超时保护，防止 ANR
            val language = try {
                runBlocking {
                    withTimeout(200) {
                        try {
                            tempSettingsManager.settingsFlow.first().language
                        } catch (e: Exception) {
                            Language.SYSTEM
                        }
                    }
                }
            } catch (e: Exception) {
                // 超时或出错，回退到默认
                Language.SYSTEM
            }
            super.attachBaseContext(LocaleHelper.setLocale(newBase, language))
        } else {
            super.attachBaseContext(newBase)
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        disableSystemAutofillForAppUi()
        
        settingsManager = SettingsManager(applicationContext)
        
        // 监听设置变化，更新缓存与截图保护开关
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                settingsManager.settingsFlow.collect { settings ->
                    cachedSettings = settings
                    
                    // 更新截图保护
                    applyScreenshotProtection(settings.screenshotProtectionEnabled)
                }
            }
        }
    }
    
    override fun onResume() {
        super.onResume()

        // Keep Etoile's own UI out of the platform Autofill pipeline so the app
        // never suggests or saves credentials for its own internal forms.
        disableSystemAutofillForAppUi()
    }
    
    /**
     * 应用截图保护设置
     */
    protected fun applyScreenshotProtection(enabled: Boolean) {
        if (enabled) {
            ScreenshotProtectionUtil.enableScreenshotProtection(this)
        } else {
            ScreenshotProtectionUtil.disableScreenshotProtection(this)
        }
    }

    private fun disableSystemAutofillForAppUi() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        window?.decorView?.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        findViewById<View?>(android.R.id.content)?.importantForAutofill =
            View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
    }
}
