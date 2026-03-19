package com.smart.srtplayer

data class SubtitleItem(
    val index: Int,
    val startTime: Long, // ملی سیکنڈز میں
    val endTime: Long,
    val text: String
)

data class PlayerSettings(
    val fontSize: Float = 24f,
    val timerSize: Float = 16f,
    val bgColor: Int = -0x1000000, // Black
    val textColor: Int = -0x1,      // White
    val opacity: Float = 0.7f,
    val srtUri: String? = null,
    val fontUri: String? = null
)
