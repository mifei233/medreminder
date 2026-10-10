package com.example.medreminder.ui

import android.content.Context
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.TimePicker
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.medreminder.model.ReminderConfig
import com.example.medreminder.util.Prefs
import com.example.medreminder.util.ReminderScheduler

/**
 * 设置页
 * - 早上/晚上触发时间（TimePicker，默认 05:00 / 22:00）
 * - 早上/晚上提醒间隔（分钟）
 * - 可编辑的提醒清单（动态增删）
 * - 保存后立即生效
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var morningPicker: TimePicker
    private lateinit var eveningPicker: TimePicker
    private lateinit var morningIntervalEdit: EditText
    private lateinit var eveningIntervalEdit: EditText
    private lateinit var itemsContainer: LinearLayout
    private val itemEditTexts = mutableListOf<EditText>()

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

        cfg.items.forEach { addItemEdit(it) }
        // 保证至少有一个空白输入框
        if (itemEditTexts.isEmpty()) addItemEdit("")
    }

    private fun addItemEdit(text: String) {
        val et = EditText(this).apply {
            hint = "提醒内容，例如：擦药 / 眼药水 / 补剂"
            setText(text)
            textSize = 16f
            setPadding(48, 32, 48, 32)
            setBackgroundResource(android.R.drawable.edit_text)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 16 }
        }
        itemEditTexts.add(et)
        itemsContainer.addView(et)
    }

    private fun save() {
        val items = itemEditTexts.map { it.text.toString().trim() }.filter { it.isNotEmpty() }
        if (items.isEmpty()) {
            Toast.makeText(this, "请至少添加一项提醒内容", Toast.LENGTH_SHORT).show()
            return
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

        // 取消旧闹钟，按新配置重排（如果今天还没过）
        ReminderScheduler.cancelAll(this)
        ReminderScheduler.rescheduleAfterBoot(this)

        Toast.makeText(this, "已保存", Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun buildLayout(): LinearLayout {
        val ctx = this
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(72, 72, 72, 72)
            setBackgroundColor(0xFFFFFFFF.toInt())
        }

        root.addView(TextView(ctx).apply {
            text = "⚙️ MedReminder 设置"
            textSize = 28f; setTextColor(0xFF222222.toInt())
            setPadding(0, 0, 0, 48)
        })

        // 早上时间
        root.addView(sectionLabel("☀️ 早上触发时间"))
        morningPicker = TimePicker(ctx).apply {
            setIs24HourView(true)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 24 }
        }
        root.addView(morningPicker)

        // 晚上时间
        root.addView(sectionLabel("🌙 晚上触发时间"))
        eveningPicker = TimePicker(ctx).apply {
            setIs24HourView(true)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 24 }
        }
        root.addView(eveningPicker)

        // 间隔
        val row1 = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        intervalField(ctx, "早间隔(分)").let { (container, et) ->
            morningIntervalEdit = et
            row1.addView(container)
        }
        intervalField(ctx, "晚间隔(分)").let { (container, et) ->
            eveningIntervalEdit = et
            row1.addView(container)
        }
        root.addView(row1)

        // 编辑清单
        root.addView(sectionLabel("📋 提醒清单"))
        itemsContainer = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(ctx).apply {
            addView(itemsContainer)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            ).apply { topMargin = 8; bottomMargin = 16 }
        }
        root.addView(scroll)

        // 添加按钮
        root.addView(Button(ctx).apply {
            text = "＋ 添加一项"
            setBackgroundColor(0xFF81C784.toInt()); setTextColor(0xFF1B5E20.toInt())
            setOnClickListener { addItemEdit("") }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 24 }
        })

        // 保存
        root.addView(Button(ctx).apply {
            text = "✅ 保存并生效"
            textSize = 18f
            setBackgroundColor(0xFF2196F3.toInt()); setTextColor(0xFFFFFFFF.toInt())
            setPadding(0, 48, 0, 48)
            setOnClickListener { save() }
        })

        return root
    }

    private fun sectionLabel(text: String) = TextView(this).apply {
        this.text = text; textSize = 18f; setTextColor(0xFF333333.toInt())
        setPadding(0, 24, 0, 8)
    }

    private fun intervalField(ctx: Context, label: String): Pair<LinearLayout, EditText> {
        val container = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                rightMargin = 24
            }
            addView(TextView(ctx).apply { text = label; textSize = 12f; setPadding(0, 0, 0, 8) })
        }
        val et = EditText(ctx).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setTextSize(16f)
            setPadding(24, 24, 24, 24)
            setBackgroundResource(android.R.drawable.edit_text)
        }
        container.addView(et)
        return Pair(container, et)
    }
}
