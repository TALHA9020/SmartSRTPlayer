package com.smart.srtplayer

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
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
        loadLastSrt()

        setContent {
            val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            
            // سیٹنگز کے لیے سٹیٹس
            var textSize by remember { mutableFloatStateOf(prefs.getFloat("text_size", 1.0f)) }
            var timerSize by remember { mutableFloatStateOf(prefs.getFloat("timer_size", 0.8f)) }
            var opacity by remember { mutableFloatStateOf(prefs.getFloat("opacity", 0.8f)) }
            var textColor by remember { mutableIntStateOf(prefs.getInt("text_color", Color.White.toArgb())) }
            var bgColor by remember { mutableIntStateOf(prefs.getInt("bg_color", Color.Black.toArgb())) }

            Column(modifier = Modifier
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
            ) {
                Text("Smart SRT Player Settings", style = MaterialTheme.typography.headlineMedium)
                Spacer(modifier = Modifier.height(20.dp))

                // سروس بٹنز
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Button(onClick = {
                        if (!Settings.canDrawOverlays(this@MainActivity)) {
                            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                        } else {
                            startForegroundService(Intent(this@MainActivity, SubtitleService::class.java))
                        }
                    }) { Text("Start Player") }

                    Button(onClick = {
                        stopService(Intent(this@MainActivity, SubtitleService::class.java))
                    }, colors = ButtonDefaults.buttonColors(containerColor = Color.Red)) { 
                        Text("Stop Player", color = Color.White) 
                    }
                }

                Divider(modifier = Modifier.padding(vertical = 20.dp))

                // ٹیکسٹ سائز کنٹرول
                Text("Text Size: ${String.format("%.1f", textSize)}")
                Slider(value = textSize, onValueChange = { 
                    textSize = it
                    prefs.edit().putFloat("text_size", it).apply()
                    refreshService()
                }, valueRange = 0.5f..3.0f)

                // ٹائمر سائز کنٹرول
                Text("Timer Size: ${String.format("%.1f", timerSize)}")
                Slider(value = timerSize, onValueChange = { 
                    timerSize = it
                    prefs.edit().putFloat("timer_size", it).apply()
                    refreshService()
                }, valueRange = 0.5f..2.0f)

                // بیک گراؤنڈ اوپیسٹی
                Text("Background Opacity: ${String.format("%.1f", opacity)}")
                Slider(value = opacity, onValueChange = { 
                    opacity = it
                    prefs.edit().putFloat("opacity", it).apply()
                    refreshService()
                }, valueRange = 0.0f..1.0f)

                Spacer(modifier = Modifier.height(10.dp))

                // سادہ کلر سلیکٹر (Black/White Toggle)
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text("Text Color: ")
                    Button(onClick = { 
                        textColor = if(textColor == Color.White.toArgb()) Color.Yellow.toArgb() else Color.White.toArgb()
                        prefs.edit().putInt("text_color", textColor).apply()
                        refreshService()
                    }, colors = ButtonDefaults.buttonColors(containerColor = Color(textColor))) {
                        Text(if(textColor == Color.White.toArgb()) "White" else "Yellow", color = Color.Blue)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text("BG Color: ")
                    Button(onClick = { 
                        bgColor = if(bgColor == Color.Black.toArgb()) Color.DarkGray.toArgb() else Color.Black.toArgb()
                        prefs.edit().putInt("bg_color", bgColor).apply()
                        refreshService()
                    }, colors = ButtonDefaults.buttonColors(containerColor = Color(bgColor))) {
                        Text(if(bgColor == Color.Black.toArgb()) "Black" else "Gray", color = Color.White)
                    }
                }
                
                Spacer(modifier = Modifier.height(30.dp))
                Text("نئی فائل یا فونٹ کے لیے فلوٹنگ ونڈو میں '+' کا بٹن استعمال کریں۔", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
        }
    }

    private fun refreshService() {
        // اگر سروس چل رہی ہے تو اسے نئی سیٹنگز لوڈ کرنے کے لیے ری سٹارٹ کریں (بغیر وقت ضائع کیے)
        if (isServiceRunning()) {
            val intent = Intent(this, SubtitleService::class.java)
            startForegroundService(intent)
        }
    }

    private fun isServiceRunning(): Boolean {
        // یہ چیک کرنے کے لیے کہ سروس ایکٹو ہے یا نہیں
        return true // سادہ رکھنے کے لیے ہم ہمیشہ ریفریش کال کر سکتے ہیں
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
