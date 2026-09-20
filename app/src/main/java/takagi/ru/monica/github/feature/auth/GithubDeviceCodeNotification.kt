package takagi.ru.monica.github.feature.auth

import android.Manifest
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.PersistableBundle
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import takagi.ru.monica.R

internal object GithubDeviceCodeNotification {
    private const val CHANNEL = "github_device_login"
    private const val ID = 7101
    internal const val CODE = "device_user_code"
    internal const val EXPIRY = "device_code_expiry"

    fun show(context: Context, code: String, expiresAt: Long): Boolean {
        val ttl = expiresAt - System.currentTimeMillis()
        if (ttl <= 0 || GithubDeviceCodeAutofill.normalize(code) == null) return false
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return false
        val manager = context.getSystemService(NotificationManager::class.java)
        if (!manager.areNotificationsEnabled()) return false
        manager.createNotificationChannel(NotificationChannel(CHANNEL,
            context.getString(R.string.github_login_notification_channel), NotificationManager.IMPORTANCE_LOW))
        if (manager.getNotificationChannel(CHANNEL)?.importance == NotificationManager.IMPORTANCE_NONE) return false
        val copy = PendingIntent.getActivity(context, ID,
            Intent(context, CopyDeviceCodeActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
                .putExtra(CODE, code).putExtra(EXPIRY, expiresAt),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val publicVersion = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_login_key)
            .setContentTitle(context.getString(R.string.github_login_notification_title))
            .setContentText(context.getString(R.string.github_login_notification_unlock)).build()
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_login_key)
            .setContentTitle(context.getString(R.string.github_login_notification_title))
            .setContentText(context.getString(R.string.github_login_notification_copy, code))
            .setContentIntent(copy)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setTimeoutAfter(ttl)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion)
            .build()
        return runCatching { manager.notify(ID, notification); true }.getOrDefault(false)
    }

    fun cancel(context: Context) = context.getSystemService(NotificationManager::class.java).cancel(ID)

    fun copy(context: Context, code: String) {
        val clip = ClipData.newPlainText(context.getString(R.string.github_device_code_label), code)
        if (Build.VERSION.SDK_INT >= 33) {
            clip.description.extras = PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE", true) }
        }
        context.getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
    }
}

/** A foreground activity makes notification copying work with modern clipboard restrictions. */
class CopyDeviceCodeActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val code = intent.getStringExtra(GithubDeviceCodeNotification.CODE)
        val valid = code != null && GithubDeviceCodeAutofill.normalize(code) != null &&
            intent.getLongExtra(GithubDeviceCodeNotification.EXPIRY, 0L) > System.currentTimeMillis()
        if (valid) GithubDeviceCodeNotification.copy(this, code!!)
        Toast.makeText(this, if (valid) R.string.github_device_code_copied else R.string.github_device_sign_in_expired,
            Toast.LENGTH_SHORT).show()
        GithubDeviceCodeNotification.cancel(this)
        finish()
    }
}
