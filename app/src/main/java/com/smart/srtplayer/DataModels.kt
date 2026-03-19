package com.smart.srtplayer

import android.net.Uri
import java.io.Serializable

data class SubtitleItem(
    val startTime: Long,
    val endTime: Long,
    val text: String
) : Serializable

data class PlayerSettings(
    val fontSize: Float,
    val timerSize: Float,
    val bgColor: Int,
    val textColor: Int,
    val opacity: Float,
    val srtUri: String?,
    val fontUri: String?
) : Serializable
