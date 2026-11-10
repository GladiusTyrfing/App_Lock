package com.vaultlock.app.lock

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.vaultlock.app.R
import com.vaultlock.app.app
import com.vaultlock.app.ui.MainActivity

object Notifications {

    const val CHANNEL_PROTECTION = "protection"
    const val CHANNEL_ALERTS = "alerts"
    const val CHANNEL_HIDDEN = "hidden"
    const val ID_SERVICE = 1001
    const val ID_HIDDEN = 1002
    const val TAG_HIDDEN_PREFIX = "hidden:"

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_PROTECTION, "Protection status", NotificationManager.IMPORTANCE_MIN).apply {
                description = "Keeps app protection running in the background"
                setShowBadge(false)
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ALERTS, "Alerts", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "New app suggestions and security alerts"
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_HIDDEN, "Hidden notifications", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Stand-ins for notifications from apps whose content you chose to hide"
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            },
        )
    }

    private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun service(context: Context, lockedCount: Int, ready: Boolean): Notification =
        NotificationCompat.Builder(context, CHANNEL_PROTECTION)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(if (ready) "Protection active" else "Setup needed")
            .setContentText(
                if (ready) "$lockedCount ${if (lockedCount == 1) "app is" else "apps are"} locked"
                else "Grant app detection access to start protecting apps",
            )
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(openApp(context))
            .build()

    /** Replaces the hidden notifications of [packageName] with a single one that shows no content. */
    fun hidden(context: Context, packageName: String, label: String, count: Int, hideName: Boolean, contentIntent: PendingIntent?) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
            android.os.Build.VERSION.SDK_INT >= 33
        ) return
        val tap = contentIntent ?: context.packageManager.getLaunchIntentForPackage(packageName)?.let {
            PendingIntent.getActivity(context, packageName.hashCode(), it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
        val builder = NotificationCompat.Builder(context, CHANNEL_HIDDEN)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(if (hideName) "New notification" else label)
            .setContentText(if (count == 1) "1 new notification" else "$count new notifications")
            .setNumber(count)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(tap)
        if (!hideName) context.app.apps.icon(packageName)?.let { builder.setLargeIcon(it) }
        runCatching { NotificationManagerCompat.from(context).notify(TAG_HIDDEN_PREFIX + packageName, ID_HIDDEN, builder.build()) }
    }

    fun newApp(context: Context, packageName: String, label: String) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
            android.os.Build.VERSION.SDK_INT >= 33
        ) return
        val id = packageName.hashCode()
        val lockIntent = PendingIntent.getBroadcast(
            context,
            id,
            Intent(context, ActionReceiver::class.java)
                .setAction(ActionReceiver.ACTION_LOCK_APP)
                .putExtra(ActionReceiver.EXTRA_PACKAGE, packageName)
                .putExtra(ActionReceiver.EXTRA_NOTIFICATION, id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("$label was installed")
            .setContentText("Lock it to keep it private")
            .setAutoCancel(true)
            .setContentIntent(openApp(context))
            .addAction(0, "Lock now", lockIntent)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(id, notification) }
    }
}
