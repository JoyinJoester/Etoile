package takagi.ru.monica.acceptance

import android.content.Intent
import android.content.res.Configuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import java.io.File
import java.util.Locale
import java.util.regex.Pattern
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.junit.runner.RunWith
import takagi.ru.monica.R

/** Black-box production navigation; never selects a preference, signs in or clears app data. */
@RunWith(AndroidJUnit4::class)
class SettingsDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val target = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)
    private val timeout = 15_000L
    private val evidence by lazy {
        val run = InstrumentationRegistry.getArguments().getString("evidenceRunId", "manual")
        require(run.matches(Regex("[A-Za-z0-9_-]+")))
        File(target.getExternalFilesDir(null), "settings-acceptance/$run").apply { mkdirs() }
    }

    @get:Rule
    val captureFailure = object : TestWatcher() {
        override fun failed(error: Throwable, description: Description) {
            capture("FAILED-${description.methodName}")
        }
    }

    @Before
    fun openProductionSettings() {
        // Clear the navigation task, preserving all account data, drafts and preferences.
        val launch = requireNotNull(target.packageManager.getLaunchIntentForPackage(target.packageName))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        target.startActivity(launch)
        await(By.pkg(target.packageName).depth(0))
        await(By.desc(label(R.string.github_settings))).click()
        settingsIsVisible()
    }

    @Test
    fun settingsChildrenReturnToTheSameSettingsPage() {
        capture("settings")
        for ((title, content, name) in listOf(
            Triple(R.string.github_appearance, R.string.github_design_style, "appearance"),
            Triple(R.string.github_language, R.string.github_language_description, "language"),
            Triple(R.string.github_display_scale, R.string.github_display_scale_description, "display-scale"),
        )) {
            await(By.text(label(title))).click()
            await(By.text(label(content)))
            capture(name)
            await(By.desc(label(R.string.github_back))).click()
            settingsIsVisible()
        }
    }

    @Test
    fun bundledLegalDocumentsHaveContentAndReturnToAbout() {
        await(By.text(label(R.string.github_about))).click()
        await(By.text(label(R.string.github_about_unofficial)))
        for ((title, content, name) in listOf(
            Triple(R.string.github_privacy, "Accounts and authorization|账户与授权", "privacy"),
            Triple(R.string.github_license, "GNU GENERAL PUBLIC LICENSE", "license"),
            Triple(R.string.github_third_party_notices, "(?i:Third.party notices)", "notices"),
        )) {
            val row = By.text(label(title))
            // About can be taller than the viewport with the user's large-font setting.
            repeat(4) {
                if (!device.hasObject(row)) {
                    await(By.scrollable(true)).scroll(Direction.DOWN, 0.55f)
                }
            }
            await(row).click()
            await(By.text(Pattern.compile("(?s).*(?:$content).*")))
            capture(name)
            await(By.desc(label(R.string.github_back))).click()
            await(By.text(label(R.string.github_about_unofficial)))
        }
        await(By.desc(label(R.string.github_back))).click()
        settingsIsVisible()
    }

    @Test
    fun repeatedSettingsBackNavigationKeepsHomeUsable() {
        repeat(5) {
            device.pressBack()
            await(By.desc(label(R.string.github_settings))).click()
            settingsIsVisible()
        }
        device.pressBack()
        await(By.desc(label(R.string.github_settings)))
        assertTrue("Home must remain in the target application", device.currentPackageName == target.packageName)
        capture("home-after-five-round-trips")
    }

    private fun settingsIsVisible() {
        await(By.text(label(R.string.github_settings_subtitle)))
        await(By.text(label(R.string.github_appearance)))
        await(By.text(label(R.string.github_about)))
        await(By.desc(label(R.string.github_back)))
    }

    private fun await(selector: BySelector): UiObject2 {
        val found = device.wait(Until.findObject(selector), timeout)
        assertNotNull("Missing accessible element: $selector", found)
        return requireNotNull(found)
    }

    private fun label(id: Int): Pattern {
        // Compose may override the app locale independently of the test process.
        val translations = listOf("en", "zh", "vi", "ja", "ru").map { tag ->
            val configuration = Configuration(target.resources.configuration).apply {
                setLocale(Locale.forLanguageTag(tag))
            }
            Pattern.quote(target.createConfigurationContext(configuration).getString(id))
        }.distinct()
        return Pattern.compile(translations.joinToString("|", "(?:", ")"))
    }

    private fun capture(name: String) {
        device.waitForIdle()
        assertTrue("Could not save screenshot $name", device.takeScreenshot(File(evidence, "$name.png")))
        device.dumpWindowHierarchy(File(evidence, "$name.xml"))
    }
}
