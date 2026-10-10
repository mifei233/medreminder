package com.example.medreminder.util

import android.content.Context
import android.content.SharedPreferences
import com.example.medreminder.model.ReminderConfig
import com.example.medreminder.model.ReminderItem
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * 统一 SharedPreferences 访问层
 *
 * JSON 存储格式（兼容升级）：
 *   老格式: ["擦药", "眼药水"]  — 读取时自动转成 intervalDays=1
 *   新格式: [{"text":"擦药","intervalDays":1,"lastDoneDate":"2026-10-10"}, ...]
 */
object Prefs {

    private const val PREFS_NAME = "med_reminder_prefs"

    private const val KEY_MORNING_HOUR = "morning_start_hour"
    private const val KEY_MORNING_MIN = "morning_start_min"
    private const val KEY_EVENING_HOUR = "evening_start_hour"
    private const val KEY_EVENING_MIN = "evening_start_min"
    private const val KEY_MORNING_INTERVAL = "morning_interval_min"
    private const val KEY_EVENING_INTERVAL = "evening_interval_min"
    private const val KEY_ITEMS = "reminder_items_json"

    private const val KEY_LAST_MORNING_DATE = "last_morning_trigger_date"
    private const val KEY_MORNING_CONFIRMED_DATE = "morning_confirmed_date"
    private const val KEY_EVENING_CONFIRMED_DATE = "evening_confirmed_date"

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ===================== 配置读写 =====================

    fun getConfig(ctx: Context): ReminderConfig {
        val p = prefs(ctx)
        val items = runCatching {
            val arr = JSONArray(p.getString(KEY_ITEMS, "[]") ?: "[]")
            (0 until arr.length()).map { i ->
                val elem = arr.get(i)
                when (elem) {
                    is String -> ReminderItem(text = elem.trim())
                    is JSONObject -> ReminderItem(
                        text = elem.getString("text"),
                        intervalDays = elem.optInt("intervalDays", 1).coerceAtLeast(1),
                        lastDoneDate = if (elem.has("lastDoneDate")) elem.optString("lastDoneDate") else null
                    )
                    else -> null
                }
            }.filterNotNull()
        }.getOrDefault(ReminderConfig().items)

        return ReminderConfig(
            morningStartHour = p.getInt(KEY_MORNING_HOUR, 5),
            morningStartMinute = p.getInt(KEY_MORNING_MIN, 0),
            eveningStartHour = p.getInt(KEY_EVENING_HOUR, 22),
            eveningStartMinute = p.getInt(KEY_EVENING_MIN, 0),
            morningIntervalMin = p.getInt(KEY_MORNING_INTERVAL, 5),
            eveningIntervalMin = p.getInt(KEY_EVENING_INTERVAL, 60),
            items = if (items.isEmpty()) ReminderConfig().items else items
        )
    }

    fun saveConfig(ctx: Context, cfg: ReminderConfig) {
        val arr = JSONArray()
        cfg.items.forEach { item ->
            arr.put(JSONObject().apply {
                put("text", item.text)
                put("intervalDays", item.intervalDays)
                item.lastDoneDate?.let { put("lastDoneDate", it) }
            })
        }
        prefs(ctx).edit()
            .putInt(KEY_MORNING_HOUR, cfg.morningStartHour)
            .putInt(KEY_MORNING_MIN, cfg.morningStartMinute)
            .putInt(KEY_EVENING_HOUR, cfg.eveningStartHour)
            .putInt(KEY_EVENING_MIN, cfg.eveningStartMinute)
            .putInt(KEY_MORNING_INTERVAL, cfg.morningIntervalMin)
            .putInt(KEY_EVENING_INTERVAL, cfg.eveningIntervalMin)
            .putString(KEY_ITEMS, arr.toString())
            .apply()
    }

    // ===================== 到期判断 =====================

    /** 距 lastDoneDate 过了 intervalDays 天以上 → 今天该提醒 */
    fun ReminderItem.isDueToday(): Boolean {
        val ld = lastDoneDate ?: return true  // 从未完成 → 每天都提醒
        val today = Calendar.getInstance()
        val lastCal = parseDate(ld) ?: return true
        val diffDays = TimeUnit.MILLISECONDS.toDays(
            today.timeInMillis - lastCal.timeInMillis
        ).toInt()
        return diffDays >= intervalDays
    }

    /** 返回今天到期的 items（供悬浮窗过滤显示） */
    fun getDueItems(ctx: Context): List<ReminderItem> =
        getConfig(ctx).items.filter { it.isDueToday() }

    // ===================== 运行时状态 =====================

    fun todayStr(): String {
        val cal = Calendar.getInstance()
        return "%04d-%02d-%02d".format(cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH))
    }

    /**
     * 本次提醒"已完成"：
     * 1. 把所有 due 的 items 的 lastDoneDate 设为今天
     * 2. 标记早/晚时段 confirm
     */
    fun markDueItemsDone(ctx: Context) {
        val cfg = getConfig(ctx)
        val updated = cfg.items.map { item ->
            if (item.isDueToday()) item.copy(lastDoneDate = todayStr()) else item
        }
        saveConfig(ctx, cfg.copy(items = updated))
    }

    fun recordMorningTriggered(ctx: Context) {
        prefs(ctx).edit().putString(KEY_LAST_MORNING_DATE, todayStr()).apply()
    }

    fun wasMorningTriggeredToday(ctx: Context): Boolean {
        return prefs(ctx).getString(KEY_LAST_MORNING_DATE, "") == todayStr()
    }

    fun confirmMorningDone(ctx: Context) {
        prefs(ctx).edit().putString(KEY_MORNING_CONFIRMED_DATE, todayStr()).apply()
    }

    fun isMorningDone(ctx: Context): Boolean {
        return prefs(ctx).getString(KEY_MORNING_CONFIRMED_DATE, "") == todayStr()
    }

    fun confirmEveningDone(ctx: Context) {
        prefs(ctx).edit().putString(KEY_EVENING_CONFIRMED_DATE, todayStr()).apply()
    }

    fun isEveningDone(ctx: Context): Boolean {
        return prefs(ctx).getString(KEY_EVENING_CONFIRMED_DATE, "") == todayStr()
    }

    // ===================== 工具 =====================

    private fun parseDate(s: String): Calendar? {
        return runCatching {
            val parts = s.split("-").map { it.toInt() }
            Calendar.getInstance().apply {
                set(parts[0], parts[1] - 1, parts[2], 0, 0, 0)
                set(Calendar.MILLISECOND, 0)
            }
        }.getOrNull()
    }
}
