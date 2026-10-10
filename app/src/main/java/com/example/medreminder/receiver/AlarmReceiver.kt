package com.example.medreminder.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.medreminder.service.ReminderOverlayService
import com.example.medreminder.util.Prefs
import com.example.medreminder.util.ReminderScheduler

/**
 * AlarmManager 触发 → 启动悬浮窗 Service → 排下一次闹钟
 *
 * 注意：Android 12+ 精确闹钟可能被降级，Service 启动本身是安全的；
 *       真正的"是否再排下一次"由手动确认按钮控制。
 */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val rawId = intent.getIntExtra("alarm_id", 0)

        // 测试闹钟（1101/1102）映射成正式 ID（1001/1002），Service 里用正式 ID 显示标题
        val isTest = rawId == ReminderScheduler.ALARM_TEST_MORNING || rawId == ReminderScheduler.ALARM_TEST_EVENING
        val alarmId = when (rawId) {
            ReminderScheduler.ALARM_TEST_MORNING -> ReminderScheduler.ALARM_MORNING
            ReminderScheduler.ALARM_TEST_EVENING -> ReminderScheduler.ALARM_EVENING
            else -> rawId
        }
        Log.d(TAG, "AlarmReceiver fired rawId=$rawId mapped=$alarmId test=$isTest")

        // 正式闹钟：今天已确认过 → 取消不显示
        if (!isTest) {
            if (alarmId == ReminderScheduler.ALARM_MORNING && Prefs.isMorningDone(context)) {
                ReminderScheduler.cancelAll(context); return
            }
            if (alarmId == ReminderScheduler.ALARM_EVENING && Prefs.isEveningDone(context)) {
                ReminderScheduler.cancelAll(context); return
            }
        }

        // 启动前台 Service 显示悬浮窗
        val svcIntent = Intent(context, ReminderOverlayService::class.java)
            .apply { putExtra(ReminderOverlayService.EXTRA_ALARM_ID, alarmId) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(svcIntent)
        } else {
            context.startService(svcIntent)
        }

        // 只有正式闹钟才排下一次循环；测试闹钟只弹一次
        if (!isTest) {
            val cfg = Prefs.getConfig(context)
            val interval = when (alarmId) {
                ReminderScheduler.ALARM_EVENING -> cfg.eveningIntervalMin
                else -> cfg.morningIntervalMin
            }
            ReminderScheduler.scheduleNext(context, alarmId, interval * 60L * 1000L)
        }
    }

    companion object { private const val TAG = "AlarmReceiver" }
}
