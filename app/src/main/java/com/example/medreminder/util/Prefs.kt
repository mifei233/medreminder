package com.example.medreminder.util

import android.content.Context
import android.content.SharedPreferences
import com.example.medreminder.model.ReminderConfig
import org.json.JSONArray

/**
 * 统一的 SharedPreferences 访问层
 * 所有 Receiver / Service / Activity 都通过这一个类读写配置，
 * 避免多处硬编码同一个 key 导致的数据源分裂问题。
 */
object Prefs {

    private const val PREFS_NAME = "med_reminder_prefs"

    // —— 配置键 ——
    private const val KEY_MORNING_HOUR = "morning_start_hour"
    private const val KEY_MORNING_MIN = "morning_start_min"
    private const val KEY_EVENING_HOUR = "evening_start_hour"
    private const val KEY_EVENING_MIN = "evening_start_min"
    private const val KEY_MORNING_INTERVAL = "morning_interval_min"
    private const val KEY_EVENING_INTERVAL = "evening_interval_min"
    private const val KEY_ITEMS = "reminder_items_json"

    // —— 运行时状态键（跨天自动失效） ——
    private const val KEY_LAST_MORNING_DATE = "last_morning_trigger_date"   // yyyy-MM-dd
    private const val KEY_MORNING_CONFIRMED_DATE = "morning_confirmed_date"
    private const val KEY_EVENING_CONFIRMED_DATE = "evening_confirmed_date"

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ===================== 配置读写 =====================

    fun getConfig(ctx: Context): ReminderConfig {
        val p = prefs(ctx)
        val items = runCatching {
            val arr = JSONArray(p.getString(KEY_ITEMS, "[]") ?: "[]")
            (0 until arr.length()).map { arr.getString(it) }
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
        cfg.items.forEach { arr.put(it) }
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

    // ===================== 运行时状态 =====================

    fun todayStr(): String {
        val cal = java.util.Calendar.getInstance()
        return "%04d-%02d-%02d".format(cal.get(java.util.Calendar.YEAR),
            cal.get(java.util.Calendar.MONTH) + 1,
            cal.get(java.util.Calendar.DAY_OF_MONTH))
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
}
