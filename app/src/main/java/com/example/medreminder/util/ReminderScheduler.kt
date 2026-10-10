package com.example.medreminder.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.medreminder.receiver.AlarmReceiver

/**
 * 闹钟调度统一入口
 *
 * 设计原则（吸取之前多源时间配置不一致的教训）：
 * - 所有闹钟调度都必须走这里，唯一的时间来源是 Prefs.getConfig()
 * - Android 12+ (API 31+) 自动检测 canScheduleExactAlarms()，
 *   无精确闹钟权限时降级为 setAndAllowWhileIdle
 * - 每次只排下一次，不在 setRepeating 里硬编码间隔
 * - CONFIRMED_ACTION 用于"手动确认"时取消所有挂起闹钟
 */
object ReminderScheduler {

    private const val TAG = "ReminderScheduler"

    const val ALARM_MORNING = 1001
    const val ALARM_EVENING = 1002
    const val ALARM_ACTION = "com.example.medreminder.ALARM_TRIGGERED"

    /** 触发早上的提醒序列（第一次由 UnlockReceiver 调） */
    fun startMorningSeries(ctx: Context) {
        val cfg = Prefs.getConfig(ctx)
        scheduleNext(ctx, ALARM_MORNING, cfg.morningIntervalMin * 60L * 1000L)
    }

    /** 触发晚上的提醒序列（由 UnlockReceiver 判断当前时间 >= 晚上起点时调） */
    fun startEveningSeries(ctx: Context) {
        val cfg = Prefs.getConfig(ctx)
        scheduleNext(ctx, ALARM_EVENING, cfg.eveningIntervalMin * 60L * 1000L)
    }

    /** 闹钟触发时调自己，排下一次 —— 这样就实现了"每次手动确认后才停止" */
    fun scheduleNext(ctx: Context, alarmId: Int, intervalMs: Long) {
        val triggerAt = System.currentTimeMillis() + intervalMs
        setAlarm(ctx, alarmId, triggerAt)
        Log.d(TAG, "scheduleNext alarmId=$alarmId intervalMs=$intervalMs nextAt=$triggerAt")
    }

    /** 手动确认 → 取消今天所有挂起的闹钟 */
    fun cancelAll(ctx: Context) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pendingIntentFor(ctx, ALARM_MORNING))
        am.cancel(pendingIntentFor(ctx, ALARM_EVENING))
        Log.d(TAG, "cancelAll")
    }

    /**
     * 开机恢复：检查当前处于哪个时间段，如果还有今天的提醒没确认，继续排
     */
    fun rescheduleAfterBoot(ctx: Context) {
        val cfg = Prefs.getConfig(ctx)
        val now = java.util.Calendar.getInstance()
        val morningStart = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, cfg.morningStartHour)
            set(java.util.Calendar.MINUTE, cfg.morningStartMinute)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        val eveningStart = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, cfg.eveningStartHour)
            set(java.util.Calendar.MINUTE, cfg.eveningStartMinute)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }

        if (!Prefs.isMorningDone(ctx) && now.after(morningStart)) {
            scheduleNext(ctx, ALARM_MORNING, cfg.morningIntervalMin * 60L * 1000L)
        }
        if (!Prefs.isEveningDone(ctx) && now.after(eveningStart)) {
            scheduleNext(ctx, ALARM_EVENING, cfg.eveningIntervalMin * 60L * 1000L)
        }
    }

    // ===================== 内部 =====================

    private fun setAlarm(ctx: Context, alarmId: Int, triggerAtMs: Long) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = pendingIntentFor(ctx, alarmId)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (am.canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMs, pi)
                return
            }
        }
        // 降级：不精确但可靠
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMs, pi)
    }

    private fun pendingIntentFor(ctx: Context, alarmId: Int): PendingIntent {
        val intent = Intent(ctx, AlarmReceiver::class.java).apply {
            action = ALARM_ACTION
            putExtra("alarm_id", alarmId)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        return PendingIntent.getBroadcast(ctx, alarmId, intent, flags)
    }
}
