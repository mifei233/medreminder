package com.example.medreminder.model

/**
 * 全局提醒配置
 *
 * 说明：
 * - morningStartHour / morningStartMinute：早上首次解锁触发的起始时间（默认 05:00）
 * - eveningStartHour / eveningStartMinute：晚上进入睡前提醒的起始时间（默认 22:00）
 * - morningIntervalMin：早上连续提醒间隔（默认 5 分钟）
 * - eveningIntervalMin：晚上连续提醒间隔（默认 60 分钟）
 * - items：需要提醒的清单内容（涂药、眼药水、补剂等，可自定义）
 */
data class ReminderConfig(
    val morningStartHour: Int = 5,
    val morningStartMinute: Int = 0,
    val eveningStartHour: Int = 22,
    val eveningStartMinute: Int = 0,
    val morningIntervalMin: Int = 5,
    val eveningIntervalMin: Int = 60,
    val items: List<String> = listOf(
        "生理盐水擦药",
        "左氧氟沙星滴眼液",
        "叶黄素补剂"
    )
)
