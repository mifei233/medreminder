package com.example.medreminder.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.medreminder.MainActivity
import com.example.medreminder.receiver.UnlockReceiver

/**
 * 常驻前台 Service，**在运行时动态注册 UnlockReceiver**。
 *
 * 关键背景：Android 8.0（API 26+）起，隐式广播 ACTION_USER_PRESENT
 * 对 manifest 静态注册的 Receiver 有限制 → 必须运行时 registerReceiver() 才能收到。
 *
 * 生命周期：
 * - APP 首次启动后由 MainActivity 启动
 * - BOOT_COMPLETED 后由 BootReceiver 启动
 * - 常驻前台，保证 UnlockReceiver 持续接收解锁广播
 */
class BootReceiverService : Service() {

    companion object {
        const val CHANNEL_ID = "med_boot_channel"
        const val NOTIFICATION_ID = 2

        fun start(context: Context) {
            val intent = Intent(context, BootReceiverService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    private val unlockReceiver = UnlockReceiver()

    override fun onCreate() {
        super.onCreate()
        // 动态注册 —— 这是 Android 8+ 能收到 USER_PRESENT 的关键
        val filter = IntentFilter(Intent.ACTION_USER_PRESENT)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(unlockReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(unlockReceiver, filter)
        }
    }

    override fun onDestroy() {
        unregisterReceiver(unlockReceiver)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification())
        return START_STICKY
    }

    private fun buildNotification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL_ID, "MedReminder 后台监听",
                NotificationManager.IMPORTANCE_MIN)
            nm.createNotificationChannel(ch)
        }
        val pi = android.app.PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) android.app.PendingIntent.FLAG_IMMUTABLE else 0)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("MedReminder")
            .setContentText("正在监听解锁事件…")
            .setSmallIcon(android.R.drawable.ic_menu_agenda)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }
}
