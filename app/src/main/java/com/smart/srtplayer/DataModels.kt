package com.smart.srtplayer

import java.io.Serializable

data class SubtitleItem(
    val startTime: Long,
    val endTime: Long,
    val text: String
) : Serializable
