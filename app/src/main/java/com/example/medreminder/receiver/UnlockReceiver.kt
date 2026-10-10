package com.example.medreminder.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.medreminder.service.ReminderOverlayService
import com.example.medreminder.util.Prefs
import com.example.medreminder.util.ReminderScheduler
import java.util.Calendar

/**
 * 监听 ACTION_USER_PRESENT（手机解锁）
 *
 * 行为：
 * 1. 当前时间 >= morningStart 且今天早上还没触发过 → **立即弹悬浮窗** + 排后续 5 分钟重提醒
 * 2. 当前时间 >= eveningStart 且今天晚上没确认过 → 立即弹出 + 排后续 1 小时重提醒
 * 3. 已确认 → 什么都不做
 *
 * 关键（吸取之前"只排闹钟不立即弹窗"的教训）：
 *   首次触发时除了 ReminderScheduler.startXxxSeries（排后续闹钟），
 *   还必须显式 startForegroundService 让用户解锁后马上看到半屏提醒。
 */
class UnlockReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_USER_PRESENT) return

        Log.d(TAG, "USER_PRESENT received")
        val cfg = Prefs.getConfig(context)
        val now = Calendar.getInstance()

        // 早上：首次解锁 + 时间窗口 + 未完成
        if (!Prefs.wasMorningTriggeredToday(context)) {
            val morningStart = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, cfg.morningStartHour)
                set(Calendar.MINUTE, cfg.morningStartMinute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            if (!now.before(morningStart) && !Prefs.isMorningDone(context)) {
                Log.d(TAG, "→ trigger morning series + immediate overlay")
                Prefs.recordMorningTriggered(context)
                ReminderScheduler.startMorningSeries(context)
                showOverlayNow(context, ReminderScheduler.ALARM_MORNING)
            }
        }

        // 晚上：过了晚间起点 + 未完成 → 每次解锁都检查并立即弹出
        val eveningStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, cfg.eveningStartHour)
            set(Calendar.MINUTE, cfg.eveningStartMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (!now.before(eveningStart) && !Prefs.isEveningDone(context)) {
            Log.d(TAG, "→ trigger evening series + immediate overlay")
            ReminderScheduler.startEveningSeries(context)
            showOverlayNow(context, ReminderScheduler.ALARM_EVENING)
        }
    }

    private fun showOverlayNow(ctx: Context, alarmId: Int) {
        val svc = Intent(ctx, ReminderOverlayService::class.java)
            .apply { putExtra(ReminderOverlayService.EXTRA_ALARM_ID, alarmId) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ctx.startForegroundService(svc)
        } else {
            ctx.startService(svc)
        }
    }

    companion object { private const val TAG = "UnlockReceiver" }
}
