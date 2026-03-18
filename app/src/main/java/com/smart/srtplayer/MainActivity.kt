package com.smart.srtplayer

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
            
            // سٹیٹس (States)
            var textSize by remember { mutableFloatStateOf(prefs.getFloat("text_size", 1.0f)) }
            var timerSize by remember { mutableFloatStateOf(prefs.getFloat("timer_size", 0.8f)) }
            var opacity by remember { mutableFloatStateOf(prefs.getFloat("opacity", 0.8f)) }
            var textColor by remember { mutableIntStateOf(prefs.getInt("text_color", Color.White.toArgb())) }
            var bgColor by remember { mutableIntStateOf(prefs.getInt("bg_color", Color.Black.toArgb())) }
            var bgWidth by remember { mutableFloatStateOf(prefs.getFloat("bg_width", 1.0f)) }

            Column(modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
            ) {
                Text("Smart SRT Settings", style = MaterialTheme.typography.headlineMedium)
                
                // --- لائیو پری ویو سیکشن ---
                Spacer(modifier = Modifier.height(10.dp))
                Text("Preview:", style = MaterialTheme.typography.labelLarge)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .padding(vertical = 8.dp)
                        .background(Color.LightGray.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                        .border(1.dp, Color.Gray, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    // یہ وہ ڈبہ ہے جو بالکل فلوٹنگ ونڈو جیسا نظر آئے گا
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth(bgWidth * 0.9f)
                            .background(Color(bgColor).copy(alpha = opacity), RoundedCornerShape(8.dp))
                            .padding(8.dp)
                    ) {
                        Text("00:01:23", color = Color(textColor).copy(alpha = 0.7f), fontSize = (14 * timerSize).sp)
                        Text(
                            "یہ سب ٹائٹل کا پری ویو ہے", 
                            color = Color(textColor), 
                            fontSize = (18 * textSize).sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // کنٹرول بٹنز
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

                HorizontalDivider(modifier = Modifier.padding(vertical = 20.dp))

                // --- سلائیڈرز ---
                SettingSlider("Text Size", textSize, 0.5f..3.0f) {
                    textSize = it
                    prefs.edit().putFloat("text_size", it).apply()
                    refreshService()
                }

                SettingSlider("Timer Size", timerSize, 0.5f..2.0f) {
                    timerSize = it
                    prefs.edit().putFloat("timer_size", it).apply()
                    refreshService()
                }

                SettingSlider("Background Width", bgWidth, 0.5f..1.0f) {
                    bgWidth = it
                    prefs.edit().putFloat("bg_width", it).apply()
                    refreshService()
                }

                SettingSlider("Background Opacity", opacity, 0.0f..1.0f) {
                    opacity = it
                    prefs.edit().putFloat("opacity", it).apply()
                    refreshService()
                }

                // --- کلر ٹوگلز ---
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    ColorToggle("Text", Color(textColor)) {
                        val nextCol = when(textColor) {
                            Color.White.toArgb() -> Color.Yellow.toArgb()
                            Color.Yellow.toArgb() -> Color.Cyan.toArgb()
                            else -> Color.White.toArgb()
                        }
                        textColor = nextCol
                        prefs.edit().putInt("text_color", nextCol).apply()
                        refreshService()
                    }
                    ColorToggle("BG", Color(bgColor)) {
                        val nextCol = when(bgColor) {
                            Color.Black.toArgb() -> Color.DarkGray.toArgb()
                            Color.DarkGray.toArgb() -> Color(0xFF1A1A1A).toArgb() // Deep Gray
                            else -> Color.Black.toArgb()
                        }
                        bgColor = nextCol
                        prefs.edit().putInt("bg_color", nextCol).apply()
                        refreshService()
                    }
                }
            }
        }
    }

    @Composable
    fun SettingSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onValueChange: (Float) -> Unit) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            Text("$label: ${String.format("%.1f", value)}", style = MaterialTheme.typography.bodyMedium)
            Slider(value = value, onValueChange = onValueChange, valueRange = range)
        }
    }

    @Composable
    fun ColorToggle(label: String, currentColor: Color, onClick: () -> Unit) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("$label Color: ")
            IconButton(onClick = onClick) {
                Box(modifier = Modifier.size(24.dp).background(currentColor, RoundedCornerShape(4.dp)).border(1.dp, Color.Gray))
            }
        }
    }

    private fun refreshService() {
        if (isServiceRunning()) {
            val intent = Intent(this, SubtitleService::class.java)
            // ہم صرف سٹارٹ کال کرتے ہیں، سروس خود کو onStartCommand میں اپ ڈیٹ کر لے گی
            startForegroundService(intent)
        }
    }

    private fun isServiceRunning(): Boolean {
        val manager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        for (service in manager.getRunningServices(Int.MAX_VALUE)) {
            if (SubtitleService::class.java.name == service.service.className) {
                return true
            }
        }
        return false
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
