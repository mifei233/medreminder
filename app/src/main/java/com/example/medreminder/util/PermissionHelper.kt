package com.example.medreminder.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

/**
 * 权限引导工具
 * - SYSTEM_ALERT_WINDOW：悬浮窗（半屏弹窗）必需
 * - SCHEDULE_EXACT_ALARM：精确闹钟（Android 12+）
 * - POST_NOTIFICATIONS：通知（Android 13+）
 * - REQUEST_IGNORE_BATTERY_OPTIMIZATIONS：国产 ROM 后台保活
 */
object PermissionHelper {

    fun canDrawOverlays(ctx: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            Settings.canDrawOverlays(ctx) else true

    fun gotoOverlaySettings(ctx: Context) {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${ctx.packageName}")
        )
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(intent)
    }

    fun canScheduleExactAlarms(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
        return am.canScheduleExactAlarms()
    }

    fun gotoExactAlarmSettings(ctx: Context) {
        val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
            Uri.parse("package:${ctx.packageName}"))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(intent)
    }

    fun gotoBatteryOptimizationSettings(ctx: Context) {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:${ctx.packageName}"))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(intent)
    }
}
