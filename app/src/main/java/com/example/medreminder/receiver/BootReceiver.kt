package com.example.medreminder.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.medreminder.service.BootReceiverService
import com.example.medreminder.util.ReminderScheduler

/**
 * 开机（或应用更新后）恢复今日的挂起闹钟 + 启动常驻前台监听
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED) return

        Log.d(TAG, "BootReceiver: $action → reschedule + start foreground")
        ReminderScheduler.rescheduleAfterBoot(context)
        BootReceiverService.start(context)
    }

    companion object { private const val TAG = "BootReceiver" }
}
