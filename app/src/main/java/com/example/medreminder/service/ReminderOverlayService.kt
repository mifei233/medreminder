package com.example.medreminder.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.example.medreminder.MainActivity

import com.example.medreminder.util.PermissionHelper
import com.example.medreminder.util.Prefs
import com.example.medreminder.util.ReminderScheduler

/**
 * 半屏悬浮窗 Service
 *
 * 显示形式：从屏幕底部弹出半屏弹窗，列出今日提醒清单 + "我已完成"按钮。
 * 手动点击按钮后：
 *   1. Prefs 记录今日已完成（早/晚分别标记）
 *   2. 取消后续的挂起闹钟
 *   3. 关闭悬浮窗
 */
class ReminderOverlayService : Service() {

    companion object {
        const val EXTRA_ALARM_ID = "alarm_id"
        const val CHANNEL_ID = "med_reminder_channel"
        const val NOTIFICATION_ID = 1
    }

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var currentAlarmId: Int = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 前台 Service 保活（Android 8+ 要求）
        startForeground(NOTIFICATION_ID, buildNotification())

        if (intent == null) return START_STICKY
        currentAlarmId = intent.getIntExtra(EXTRA_ALARM_ID, ReminderScheduler.ALARM_MORNING)

        if (!PermissionHelper.canDrawOverlays(this)) {
            // 悬浮窗权限缺失 → 发一个 heads-up 通知作为 fallback，
            // 避免 5 分钟循环在后台空转用户看不到任何东西
            showFallbackNotification()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        showOverlay()
        return START_NOT_STICKY
    }

    @SuppressLint("InflateParams")
    private fun showOverlay() {
        if (overlayView != null) return // 已经在显示了

        val cfg = Prefs.getConfig(this)
        val label = if (currentAlarmId == ReminderScheduler.ALARM_EVENING) "🌙 睡前提醒" else "☀️ 早上提醒"

        // 用代码动态构建半屏布局，避免写 layout XML 也要处理
        val context = this
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFFF5F5F5.toInt())
            setPadding(48, 48, 48, 48)
        }

        val title = TextView(context).apply {
            text = label
            textSize = 26f
            setTextColor(0xFF222222.toInt())
        }
        root.addView(title)

        val subtitle = TextView(context).apply {
            text = "手动确认后将停止后续提醒"
            textSize = 14f
            setTextColor(0xFF888888.toInt())
            setPadding(0, 12, 0, 24)
        }
        root.addView(subtitle)

        // 清单
        val listLayout = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        cfg.items.forEachIndexed { _, item ->
            val row = TextView(context).apply {
                text = "✅  $item"
                textSize = 18f
                setTextColor(0xFF333333.toInt())
                setPadding(0, 16, 0, 16)
            }
            listLayout.addView(row)
        }

        val scroll = ScrollView(context).apply {
            addView(listLayout)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0, 1f
            )
        }
        root.addView(scroll)

        val confirmBtn = Button(context).apply {
            text = "我已完成"
            textSize = 18f
            setPadding(0, 40, 0, 40)
            setBackgroundColor(0xFF4CAF50.toInt())
            setTextColor(0xFFFFFFFF.toInt())
            setOnClickListener { handleConfirm() }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 32 }
        }
        root.addView(confirmBtn)

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            (resources.displayMetrics.heightPixels * 0.5f).toInt(), // 半屏高度
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM
        }

        overlayView = root
        windowManager!!.addView(root, params)
    }

    private fun handleConfirm() {
        // 标记今日完成
        if (currentAlarmId == ReminderScheduler.ALARM_MORNING) {
            Prefs.confirmMorningDone(this)
        } else {
            Prefs.confirmEveningDone(this)
        }
        // 取消后续所有挂起闹钟
        ReminderScheduler.cancelAll(this)
        // 关闭悬浮窗
        removeOverlay()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun removeOverlay() {
        overlayView?.let {
            windowManager?.removeView(it)
            overlayView = null
        }
    }

    override fun onDestroy() {
        removeOverlay()
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL_ID, "MedReminder 前台服务", NotificationManager.IMPORTANCE_LOW)
            nm.createNotificationChannel(ch)
        }
        val pi = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("MedReminder")
            .setContentText("提醒进行中…")
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }

    /**
     * 悬浮窗权限缺失时的 fallback：发 heads-up 通知（高优先级，锁屏也能看到）。
     */
    private fun showFallbackNotification() {
        val nm = getSystemService(NotificationManager::class.java)
        val fallbackCh = "med_fallback_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(fallbackCh, "MedReminder 提醒",
                NotificationManager.IMPORTANCE_HIGH).apply {
                enableLights(true); enableVibration(true)
            }
            nm.createNotificationChannel(ch)
        }
        val cfg = Prefs.getConfig(this)
        val body = cfg.items.joinToString(" / ") { "•$it" }
        val pi = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        val n = NotificationCompat.Builder(this, fallbackCh)
            .setContentTitle("💊 MedReminder")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        nm.notify(9999, n)
    }
}
