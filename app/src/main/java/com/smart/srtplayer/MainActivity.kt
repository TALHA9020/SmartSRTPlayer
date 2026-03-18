package com.smart.srtplayer

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.io.File

data class SubtitleItem(val start: Long, val end: Long, val text: String)

class MainActivity : ComponentActivity() {
    companion object {
        val fullSubtitleList = mutableListOf<SubtitleItem>()
        const val PREFS_NAME = "SmartPrefs"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // اگر پہلے سے کوئی فائل لوڈ ہے تو اسے میموری میں لائیں
        loadLastSrt()

        setContent {
            Column(modifier = Modifier.padding(20.dp)) {
                Button(onClick = {
                    if (!Settings.canDrawOverlays(this@MainActivity)) {
                        startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                    } else {
                        startForegroundService(Intent(this@MainActivity, SubtitleService::class.java))
                    }
                }) { Text("Start Floating Subtitles") }
                
                Spacer(modifier = Modifier.height(10.dp))
                
                Button(onClick = {
                    stopService(Intent(this@MainActivity, SubtitleService::class.java))
                }) { Text("Stop Service") }
            }
        }
    }

    private fun loadLastSrt() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val path = prefs.getString("last_srt_path", null)
        if (path != null && File(path).exists()) {
            fullSubtitleList.clear()
            fullSubtitleList.addAll(parseSrt(File(path).readText()))
        }
    }

    private fun parseSrt(content: String): List<SubtitleItem> {
        val list = mutableListOf<SubtitleItem>()
        try {
            val blocks = content.split(Regex("(\\n\\n)|(\\r\\n\\r\\n)"))
            for (block in blocks) {
                val lines = block.trim().lines()
                if (lines.size >= 3) {
                    val timeRange = lines[1].split(" --> ")
                    if (timeRange.size == 2) {
                        list.add(SubtitleItem(
                            start = timeToMs(timeRange[0]),
                            end = timeToMs(timeRange[1]),
                            text = lines.drop(2).joinToString("\n")
                        ))
                    }
                }
            }
        } catch (e: Exception) { e.printStackTrace() }
        return list
    }

    private fun timeToMs(time: String): Long {
        val parts = time.replace(",", ".").split(":")
        val h = parts[0].trim().toLong() * 3600000
        val m = parts[1].trim().toLong() * 60000
        val s = (parts[2].trim().toDouble() * 1000).toLong()
        return h + m + s
    }
}
