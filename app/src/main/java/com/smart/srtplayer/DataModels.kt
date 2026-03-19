package com.smart.srtplayer

// سبٹائٹل کے ایک جملے کا ڈیٹا
data class SubtitleItem(
    val start: Long, 
    val end: Long, 
    val text: String
)

// پلے لسٹ میں موجود ایک فائل کا ڈیٹا
data class PlaylistItem(
    val id: String, 
    val name: String, 
    val path: String
)
