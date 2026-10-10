package com.example.medreminder.ui

import android.content.Context
import android.os.Bundle
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.TimePicker
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.medreminder.model.ReminderConfig
import com.example.medreminder.model.ReminderItem
import com.example.medreminder.util.Prefs
import com.example.medreminder.util.ReminderScheduler

/**
 * 设置页：整页可滚，每个提醒项有独立的间隔天数
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var morningPicker: TimePicker
    private lateinit var eveningPicker: TimePicker
    private lateinit var morningIntervalEdit: EditText
    private lateinit var eveningIntervalEdit: EditText
    private lateinit var itemsContainer: LinearLayout

    // 每个条目一对 EditText：(内容, 间隔天数)
    private val itemPairs = mutableListOf<Pair<EditText, EditText>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildLayout())
        loadConfig()
    }

    private fun loadConfig() {
        val cfg = Prefs.getConfig(this)
        morningPicker.hour = cfg.morningStartHour
        morningPicker.minute = cfg.morningStartMinute
        eveningPicker.hour = cfg.eveningStartHour
        eveningPicker.minute = cfg.eveningStartMinute
        morningIntervalEdit.setText(cfg.morningIntervalMin.toString())
        eveningIntervalEdit.setText(cfg.eveningIntervalMin.toString())

        cfg.items.forEach { addItemRow(it.text, it.intervalDays) }
        if (itemPairs.isEmpty()) addItemRow("", 1)
    }

    /** 添加一行：内容 + 间隔天数（天），右侧带删除按钮 */
    private fun addItemRow(text: String, intervalDays: Int) {
        val ctx = this
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 16 }
        }

        val etContent = EditText(ctx).apply {
            hint = "提醒内容"
            setText(text)
            textSize = 15f
            setPadding(48, 32, 24, 32)
            setBackgroundResource(android.R.drawable.edit_text)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val etInterval = EditText(ctx).apply {
            hint = "每N天"
            setText(intervalDays.toString())
            inputType = InputType.TYPE_CLASS_NUMBER
            textSize = 15f
            setPadding(24, 32, 24, 32)
            setBackgroundResource(android.R.drawable.edit_text)
            layoutParams = LinearLayout.LayoutParams(180, LinearLayout.LayoutParams.WRAP_CONTENT)
        }

        val btnDel = Button(ctx).apply {
            setText("✕")
            textSize = 14f
            setPadding(0, 16, 0, 16)
            setBackgroundColor(0xFFE57373.toInt()); setTextColor(0xFFFFFFFF.toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setOnClickListener {
                itemPairs.removeAt(itemPairs.indexOfFirst { it.first === etContent })
                itemsContainer.removeView(row)
            }
        }

        row.addView(etContent)
        row.addView(etInterval)
        row.addView(btnDel)
        itemPairs.add(Pair(etContent, etInterval))
        itemsContainer.addView(row)
    }

    private fun save() {
        val items = itemPairs.mapNotNull { (contentEt, intervalEt) ->
            val text = contentEt.text.toString().trim()
            if (text.isEmpty()) return@mapNotNull null
            val interval = intervalEt.text.toString().toIntOrNull()?.coerceAtLeast(1) ?: 1
            ReminderItem(text = text, intervalDays = interval)
        }
        if (items.isEmpty()) {
            Toast.makeText(this, "请至少添加一项提醒内容", Toast.LENGTH_SHORT).show(); return
        }

        val cfg = ReminderConfig(
            morningStartHour = morningPicker.hour,
            morningStartMinute = morningPicker.minute,
            eveningStartHour = eveningPicker.hour,
            eveningStartMinute = eveningPicker.minute,
            morningIntervalMin = morningIntervalEdit.text.toString().toIntOrNull()?.coerceIn(1, 120) ?: 5,
            eveningIntervalMin = eveningIntervalEdit.text.toString().toIntOrNull()?.coerceIn(1, 240) ?: 60,
            items = items
        )
        Prefs.saveConfig(this, cfg)
        ReminderScheduler.cancelAll(this)
        ReminderScheduler.rescheduleAfterBoot(this)

        Toast.makeText(this, "已保存", Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun buildLayout(): ScrollView {
        val ctx = this
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(72, 72, 72, 72)
            setBackgroundColor(0xFFFFFFFF.toInt())
        }

        root.addView(TextView(ctx).apply {
            text = "⚙️ MedReminder 设置"
            textSize = 28f; setTextColor(0xFF222222.toInt()); setPadding(0, 0, 0, 48)
        })

        root.addView(sectionLabel("☀️ 早上触发时间"))
        morningPicker = TimePicker(ctx).apply { setIs24HourView(true)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 24 } }
        root.addView(morningPicker)

        root.addView(sectionLabel("🌙 晚上触发时间"))
        eveningPicker = TimePicker(ctx).apply { setIs24HourView(true)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 24 } }
        root.addView(eveningPicker)

        val row1 = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        intervalField(ctx, "早间隔(分)").let { (c, et) -> morningIntervalEdit = et; row1.addView(c) }
        intervalField(ctx, "晚间隔(分)").let { (c, et) -> eveningIntervalEdit = et; row1.addView(c) }
        root.addView(row1)

        root.addView(sectionLabel("📋 提醒清单（可设置每N天提醒一次）"))
        root.addView(TextView(ctx).apply {
            text = "左侧：提醒内容   右侧：间隔天数（1=每天）"
            textSize = 12f; setTextColor(0xFF888888.toInt()); setPadding(0, 0, 0, 8)
        })
        itemsContainer = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        root.addView(itemsContainer)

        root.addView(Button(ctx).apply {
            text = "＋ 添加一项"
            setBackgroundColor(0xFF81C784.toInt()); setTextColor(0xFF1B5E20.toInt())
            setOnClickListener { addItemRow("", 1) }
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = 8; bottomMargin = 24 }
        })

        root.addView(Button(ctx).apply {
            text = "✅ 保存并生效"; textSize = 18f
            setBackgroundColor(0xFF2196F3.toInt()); setTextColor(0xFFFFFFFF.toInt())
            setPadding(0, 48, 0, 48)
            setOnClickListener { save() }
        })

        return ScrollView(ctx).apply { addView(root); setBackgroundColor(0xFFFFFFFF.toInt()) }
    }

    private fun sectionLabel(text: String) = TextView(this).apply {
        this.text = text; textSize = 18f; setTextColor(0xFF333333.toInt()); setPadding(0, 24, 0, 8)
    }

    private fun intervalField(ctx: Context, label: String): Pair<LinearLayout, EditText> {
        val c = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = 24 }
            addView(TextView(ctx).apply { text = label; textSize = 12f; setPadding(0, 0, 0, 8) })
        }
        val et = EditText(ctx).apply {
            inputType = InputType.TYPE_CLASS_NUMBER; setTextSize(16f)
            setPadding(24, 24, 24, 24); setBackgroundResource(android.R.drawable.edit_text)
        }
        c.addView(et)
        return Pair(c, et)
    }
}
