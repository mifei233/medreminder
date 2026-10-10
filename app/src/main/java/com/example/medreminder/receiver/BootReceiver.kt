package com.example.medreminder.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.medreminder.util.ReminderScheduler

/**
 * 开机（或应用更新后）恢复今日的挂起闹钟
 *
 * 注意：不再需要启动常驻前台 Service。
 * ACTION_USER_PRESENT 可以直接在 manifest 里静态注册，解锁时系统自动唤起进程。
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED) return

        Log.d(TAG, "BootReceiver: $action → reschedule alarms")
        ReminderScheduler.rescheduleAfterBoot(context)
    }

    companion object { private const val TAG = "BootReceiver" }
}
