package com.example.medreminder

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.medreminder.service.BootReceiverService
import com.example.medreminder.service.ReminderOverlayService
import com.example.medreminder.ui.SettingsActivity
import com.example.medreminder.util.PermissionHelper
import com.example.medreminder.util.Prefs
import com.example.medreminder.util.ReminderScheduler

/**
 * 主界面
 * - 今日状态（早上/晚上提醒是否已确认）
 * - 配置预览
 * - 手动触发测试按钮
 * - 进入设置页 + 权限引导
 */
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildLayout())
        BootReceiverService.start(this)  // 启动常驻前台，动态注册 UnlockReceiver
        requestNotificationPermissionIfNeeded()
        refresh()
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val cfg = Prefs.getConfig(this)
        val morningDone = Prefs.isMorningDone(this)
        val eveningDone = Prefs.isEveningDone(this)
        val morningTriggered = Prefs.wasMorningTriggeredToday(this)

        statusMorning.text = "☀️ 早上提醒: ${
            if (morningDone) "✅ 已完成" else if (morningTriggered) "⏰ 进行中" else "⏳ 待触发"
        }  (${String.format("%02d:%02d", cfg.morningStartHour, cfg.morningStartMinute)})"

        statusEvening.text = "🌙 晚上提醒: ${
            if (eveningDone) "✅ 已完成" else "⏳ 待触发"
        }  (${String.format("%02d:%02d", cfg.eveningStartHour, cfg.eveningStartMinute)})"

        itemsText.text = cfg.items.joinToString("\n") { "• $it" }
    }

    // —— 动态构建布局（节省 XML 资源文件） ——

    private lateinit var statusMorning: TextView
    private lateinit var statusEvening: TextView
    private lateinit var itemsText: TextView

    private fun buildLayout(): LinearLayout {
        val ctx = this
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(72, 72, 72, 72)
            setBackgroundColor(0xFFFFFFFF.toInt())
        }

        root.addView(TextView(ctx).apply {
            text = "💊 MedReminder"
            textSize = 32f
            setTextColor(0xFF2E7D32.toInt())
            setPadding(0, 0, 0, 16)
        })
        root.addView(TextView(ctx).apply {
            text = "每天提醒你擦药、滴眼药水、吃补剂"
            textSize = 14f
            setTextColor(0xFF888888.toInt())
            setPadding(0, 0, 0, 48)
        })

        // 状态区
        statusMorning = TextView(ctx).apply { textSize = 16f; setTextColor(0xFF333333.toInt()); setPadding(0, 8, 0, 8) }
        statusEvening = TextView(ctx).apply { textSize = 16f; setTextColor(0xFF333333.toInt()); setPadding(0, 8, 0, 48) }
        root.addView(statusMorning); root.addView(statusEvening)

        root.addView(TextView(ctx).apply {
            text = "📋 今日清单："
            textSize = 18f; setTextColor(0xFF333333.toInt()); setPadding(0, 0, 0, 16)
        })
        itemsText = TextView(ctx).apply {
            textSize = 16f; setTextColor(0xFF555555.toInt()); setPadding(0, 0, 0, 48)
        }
        root.addView(ScrollView(ctx).apply { addView(itemsText) })

        // 按钮
        fun makeBtn(text: String, color: Int, onClick: () -> Unit): Button =
            Button(ctx).apply {
                this.text = text; textSize = 16f
                setBackgroundColor(color); setTextColor(0xFFFFFFFF.toInt())
                setPadding(0, 40, 0, 40)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = 24 }
                setOnClickListener { onClick() }
            }

        root.addView(makeBtn("🔧 设置", 0xFF2196F3.toInt()) {
            startActivity(Intent(ctx, SettingsActivity::class.java))
        })
        root.addView(makeBtn("🧪 手动测试早上提醒", 0xFF4CAF50.toInt()) {
            Prefs.recordMorningTriggered(ctx)
            ReminderScheduler.startMorningSeries(ctx)
            val svc = Intent(ctx, ReminderOverlayService::class.java)
                .apply { putExtra(ReminderOverlayService.EXTRA_ALARM_ID, ReminderScheduler.ALARM_MORNING) }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(svc)
            else startService(svc)
            refresh()
        })
        root.addView(makeBtn("🌙 手动测试晚上提醒", 0xFF795548.toInt()) {
            val svc = Intent(ctx, ReminderOverlayService::class.java)
                .apply { putExtra(ReminderOverlayService.EXTRA_ALARM_ID, ReminderScheduler.ALARM_EVENING) }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(svc)
            else startService(svc)
            refresh()
        })
        root.addView(makeBtn("📝 取消今日全部提醒", 0xFFF44336.toInt()) {
            ReminderScheduler.cancelAll(ctx)
            Prefs.confirmMorningDone(ctx)
            Prefs.confirmEveningDone(ctx)
            refresh()
            Toast.makeText(ctx, "已取消", Toast.LENGTH_SHORT).show()
        })

        root.addView(TextView(ctx).apply {
            text = "首次使用请允许：悬浮窗权限 · 精确闹钟权限 · 电池优化白名单"
            textSize = 12f; setTextColor(0xFF999999.toInt())
            gravity = Gravity.CENTER; setPadding(0, 48, 0, 0)
        })

        // 权限快速引导
        root.addView(makeBtn("⚙️ 打开悬浮窗权限", 0xFF607D8B.toInt()) {
            if (!PermissionHelper.canDrawOverlays(ctx)) PermissionHelper.gotoOverlaySettings(ctx)
            else Toast.makeText(ctx, "已授权", Toast.LENGTH_SHORT).show()
        })
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            root.addView(makeBtn("⏰ 打开精确闹钟权限", 0xFF607D8B.toInt()) {
                if (!PermissionHelper.canScheduleExactAlarms(ctx)) PermissionHelper.gotoExactAlarmSettings(ctx)
                else Toast.makeText(ctx, "已授权", Toast.LENGTH_SHORT).show()
            })
        }

        return root
    }
}
