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
 * 三个按钮：
 *   ✅ 我已完成 → 标记到期 items 完成 + 标记时段完成 + cancelAll + 关窗
 *   ⏸ 稍后再说 → 只关窗，不标记完成；AlarmReceiver 排的后续闹钟还会继续弹
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
        startForeground(NOTIFICATION_ID, buildNotification())

        if (intent == null) return START_STICKY
        currentAlarmId = intent.getIntExtra(EXTRA_ALARM_ID, ReminderScheduler.ALARM_MORNING)

        if (!PermissionHelper.canDrawOverlays(this)) {
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
        if (overlayView != null) return

        // 只显示"今天到期"的 items（考虑到 intervalDays）
        val dueItems = Prefs.getDueItems(this)

        val label = if (currentAlarmId == ReminderScheduler.ALARM_EVENING) "🌙 睡前提醒" else "☀️ 早上提醒"
        val ctx = this

        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFFF5F5F5.toInt())
            setPadding(48, 48, 48, 48)
        }

        root.addView(TextView(ctx).apply {
            text = label; textSize = 26f; setTextColor(0xFF222222.toInt())
        })
        root.addView(TextView(ctx).apply {
            text = "手动确认后停止后续提醒；稍后再说则继续"
            textSize = 13f; setTextColor(0xFF888888.toInt()); setPadding(0, 8, 0, 24)
        })

        val listLayout = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        if (dueItems.isEmpty()) {
            listLayout.addView(TextView(ctx).apply {
                text = "🎉 今日所有提醒已完成！"
                textSize = 18f; setTextColor(0xFF4CAF50.toInt())
                setPadding(0, 16, 0, 16)
            })
        } else {
            dueItems.forEach { item ->
                listLayout.addView(TextView(ctx).apply {
                    text = "●  ${item.text}${if (item.intervalDays > 1) "  (每${item.intervalDays}天)" else ""}"
                    textSize = 18f; setTextColor(0xFF333333.toInt())
                    setPadding(0, 16, 0, 16)
                })
            }
        }

        root.addView(ScrollView(ctx).apply {
            addView(listLayout)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            )
        })

        // 主按钮：我已完成（绿色，全宽高亮）
        root.addView(Button(ctx).apply {
            text = "✅ 我已完成"; textSize = 18f
            setPadding(0, 40, 0, 40)
            setBackgroundColor(0xFF4CAF50.toInt()); setTextColor(0xFFFFFFFF.toInt())
            setOnClickListener { handleConfirm() }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 32 }
        })

        // 次按钮：稍后再说（灰色，不标记完成）
        root.addView(Button(ctx).apply {
            text = "⏸ 稍后再说"; textSize = 16f
            setPadding(0, 28, 0, 28)
            setBackgroundColor(0xFF9E9E9E.toInt()); setTextColor(0xFFFFFFFF.toInt())
            setOnClickListener { handleSnooze() }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 16 }
        })

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            (resources.displayMetrics.heightPixels * 0.5f).toInt(),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.BOTTOM }

        overlayView = root
        windowManager!!.addView(root, params)
    }

    /** ✅ 我已完成：标记到期 items + 标记时段 + cancelAll + 关窗 */
    private fun handleConfirm() {
        Prefs.markDueItemsDone(this)
        if (currentAlarmId == ReminderScheduler.ALARM_MORNING) Prefs.confirmMorningDone(this)
        else Prefs.confirmEveningDone(this)
        ReminderScheduler.cancelAll(this)
        removeOverlay()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /** ⏸ 稍后再说：只关窗，不标记完成；AlarmReceiver 已排好的下一次闹钟继续弹 */
    private fun handleSnooze() {
        removeOverlay()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun removeOverlay() {
        overlayView?.let { windowManager?.removeView(it); overlayView = null }
    }

    override fun onDestroy() { removeOverlay(); super.onDestroy() }

    private fun buildNotification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(NotificationChannel(
                CHANNEL_ID, "MedReminder 前台服务", NotificationManager.IMPORTANCE_LOW))
        }
        val pi = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("MedReminder").setContentText("提醒进行中…")
            .setSmallIcon(android.R.drawable.ic_menu_info_details).setContentIntent(pi).setOngoing(true).build()
    }

    private fun showFallbackNotification() {
        val nm = getSystemService(NotificationManager::class.java)
        val ch = "med_fallback_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(NotificationChannel(
                ch, "MedReminder 提醒", NotificationManager.IMPORTANCE_HIGH).apply {
                enableLights(true); enableVibration(true)
            })
        }
        val dueItems = Prefs.getDueItems(this)
        val body = if (dueItems.isEmpty()) "今日提醒已全部完成"
            else dueItems.joinToString(" / ") { "•${it.text}" }
        val pi = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        nm.notify(9999, NotificationCompat.Builder(this, ch)
            .setContentTitle("💊 MedReminder").setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setPriority(NotificationCompat.PRIORITY_HIGH).setAutoCancel(true)
            .setContentIntent(pi).build())
    }
}
