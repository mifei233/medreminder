package com.example.medreminder.model

/**
 * 单项提醒：一个药物 / 眼药水 / 补剂
 *
 * @param text          提醒内容，如 "左氧氟沙星滴眼液"
 * @param intervalDays  每几天提醒一次。1 = 每天（默认），3 = 每 3 天一次
 * @param lastDoneDate  上次点击"我已完成"的日期 (yyyy-MM-dd)，null 表示从未完成
 */
data class ReminderItem(
    val text: String,
    val intervalDays: Int = 1,
    val lastDoneDate: String? = null
)

/**
 * 全局提醒配置
 */
data class ReminderConfig(
    val morningStartHour: Int = 5,
    val morningStartMinute: Int = 0,
    val eveningStartHour: Int = 22,
    val eveningStartMinute: Int = 0,
    val morningIntervalMin: Int = 5,
    val eveningIntervalMin: Int = 60,
    val items: List<ReminderItem> = listOf(
        ReminderItem("生理盐水擦药", intervalDays = 1),
        ReminderItem("左氧氟沙星滴眼液", intervalDays = 1),
        ReminderItem("叶黄素补剂", intervalDays = 3)
    )
)
