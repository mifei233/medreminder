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
        val alarmId = intent.getIntExtra("alarm_id", 0)
        Log.d(TAG, "AlarmReceiver fired alarmId=$alarmId")

        // 今天该时段已确认过 → 直接取消，不显示
        if (alarmId == ReminderScheduler.ALARM_MORNING && Prefs.isMorningDone(context)) {
            ReminderScheduler.cancelAll(context)
            return
        }
        if (alarmId == ReminderScheduler.ALARM_EVENING && Prefs.isEveningDone(context)) {
            ReminderScheduler.cancelAll(context)
            return
        }

        // 启动前台 Service 显示悬浮窗
        val svcIntent = Intent(context, ReminderOverlayService::class.java)
            .apply { putExtra(ReminderOverlayService.EXTRA_ALARM_ID, alarmId) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(svcIntent)
        } else {
            context.startService(svcIntent)
        }

        // 排下一次（用户没手动确认就会一直响）
        val cfg = Prefs.getConfig(context)
        val interval = when (alarmId) {
            ReminderScheduler.ALARM_EVENING -> cfg.eveningIntervalMin
            else -> cfg.morningIntervalMin
        }
        ReminderScheduler.scheduleNext(context, alarmId, interval * 60L * 1000L)
    }

    companion object { private const val TAG = "AlarmReceiver" }
}
