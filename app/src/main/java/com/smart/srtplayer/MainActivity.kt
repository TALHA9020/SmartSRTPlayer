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

// ڈیٹا کلاس جو پوری ایپ کے لیے ہے
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
                
                Spacer(modifier = Modifier.height(10.dp))
                Text("Preview (پری ویو):", style = MaterialTheme.typography.labelLarge)
                
                // لائیو پری ویو باکس
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .padding(vertical = 8.dp)
                        .background(Color.LightGray.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                        .border(1.dp, Color.Gray.copy(alpha = 0.3f), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth(bgWidth * 0.95f)
                            .background(Color(bgColor).copy(alpha = opacity), RoundedCornerShape(12.dp))
                            .padding(12.dp)
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

                Spacer(modifier = Modifier.height(16.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(modifier = Modifier.weight(1f), onClick = {
                        if (!Settings.canDrawOverlays(this@MainActivity)) {
                            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                        } else {
                            startForegroundService(Intent(this@MainActivity, SubtitleService::class.java))
                        }
                    }) { Text("Start Player") }

                    Button(modifier = Modifier.weight(1f), onClick = {
                        stopService(Intent(this@MainActivity, SubtitleService::class.java))
                    }, colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(alpha = 0.8f))) { 
                        Text("Stop Player", color = Color.White) 
                    }
                }

                Divider(modifier = Modifier.padding(vertical = 20.dp), thickness = 0.5.dp, color = Color.Gray)

                SettingControl("Text Size (ٹیکسٹ سائز)", textSize, 0.5f..3.0f) {
                    textSize = it
                    prefs.edit().putFloat("text_size", it).apply()
                    refreshService()
                }

                SettingControl("Timer Size (ٹائمر سائز)", timerSize, 0.5f..2.0f) {
                    timerSize = it
                    prefs.edit().putFloat("timer_size", it).apply()
                    refreshService()
                }

                SettingControl("BG Width (چوڑائی)", bgWidth, 0.4f..1.0f) {
                    bgWidth = it
                    prefs.edit().putFloat("bg_width", it).apply()
                    refreshService()
                }

                SettingControl("Opacity (شفافیت)", opacity, 0.0f..1.0f) {
                    opacity = it
                    prefs.edit().putFloat("opacity", it).apply()
                    refreshService()
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    ColorToggleBtn("Text Color", Color(textColor)) {
                        val nextCol = when(textColor) {
                            Color.White.toArgb() -> Color.Yellow.toArgb()
                            Color.Yellow.toArgb() -> Color.Cyan.toArgb()
                            else -> Color.White.toArgb()
                        }
                        textColor = nextCol
                        prefs.edit().putInt("text_color", nextCol).apply()
                        refreshService()
                    }
                    ColorToggleBtn("BG Color", Color(bgColor)) {
                        val nextCol = when(bgColor) {
                            Color.Black.toArgb() -> Color.DarkGray.toArgb()
                            else -> Color.Black.toArgb()
                        }
                        bgColor = nextCol
                        prefs.edit().putInt("bg_color", nextCol).apply()
                        refreshService()
                    }
                }
                Spacer(modifier = Modifier.height(40.dp))
            }
        }
    }

    @Composable
    fun SettingControl(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onValueChange: (Float) -> Unit) {
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            Text("$label: ${String.format("%.1f", value)}", style = MaterialTheme.typography.bodySmall)
            Slider(value = value, onValueChange = onValueChange, valueRange = range)
        }
    }

    @Composable
    fun ColorToggleBtn(label: String, currentColor: Color, onClick: () -> Unit) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = MaterialTheme.typography.labelSmall)
            Button(onClick = onClick, colors = ButtonDefaults.buttonColors(containerColor = currentColor),
                modifier = Modifier.size(width = 100.dp, height = 40.dp).border(1.dp, Color.Gray, RoundedCornerShape(8.dp))) {
                val contentCol = if(currentColor == Color.White || currentColor == Color.Yellow) Color.Black else Color.White
                Text("Change", color = contentCol, fontSize = 10.sp)
            }
        }
    }

    private fun refreshService() {
        if (isServiceRunning()) {
            startForegroundService(Intent(this, SubtitleService::class.java))
        }
    }

    private fun isServiceRunning(): Boolean {
        val manager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return manager.getRunningServices(Int.MAX_VALUE).any { it.service.className == SubtitleService::class.java.name }
    }

    private fun loadLastSrt() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val path = prefs.getString("last_srt_path", null)
        path?.let { file ->
            if (File(file).exists()) {
                fullSubtitleList.clear()
                fullSubtitleList.addAll(parseSrt(File(file).readText()))
            }
        }
    }

    private fun parseSrt(content: String): List<SubtitleItem> {
        val list = mutableListOf<SubtitleItem>()
        val blocks = content.split(Regex("(\\n\\n)|(\\r\\n\\r\\n)"))
        for (block in blocks) {
            val lines = block.trim().lines()
            if (lines.size >= 3) {
                val timeRange = lines[1].split(" --> ")
                if (timeRange.size == 2) {
                    list.add(SubtitleItem(timeToMs(timeRange[0]), timeToMs(timeRange[1]), lines.drop(2).joinToString("\n")))
                }
            }
        }
        return list
    }

    private fun timeToMs(time: String): Long {
        val parts = time.replace(",", ".").split(":")
        return (parts[0].trim().toLong() * 3600000) + (parts[1].trim().toLong() * 60000) + (parts[2].trim().toDouble() * 1000).toLong()
    }
}
