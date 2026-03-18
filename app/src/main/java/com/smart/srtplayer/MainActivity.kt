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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
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
            
            var textSize by remember { mutableFloatStateOf(prefs.getFloat("text_size", 1.0f)) }
            var timerSize by remember { mutableFloatStateOf(prefs.getFloat("timer_size", 0.8f)) }
            var opacity by remember { mutableFloatStateOf(prefs.getFloat("opacity", 0.8f)) }
            var bgColor by remember { mutableIntStateOf(prefs.getInt("bg_color", Color.Black.toArgb())) }
            var bgWidth by remember { mutableFloatStateOf(prefs.getFloat("bg_width", 1.0f)) }
            var textColor by remember { mutableIntStateOf(prefs.getInt("text_color", Color.White.toArgb())) }

            Column(modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
            ) {
                Text("Smart SRT Player", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                
                Spacer(modifier = Modifier.height(10.dp))
                
                // لائیو پری ویو باکس
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .background(Color.DarkGray.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                        .border(1.dp, Color.Gray.copy(alpha = 0.5f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth(bgWidth * 0.9f)
                            .background(Color(bgColor).copy(alpha = opacity), RoundedCornerShape(8.dp))
                            .padding(8.dp)
                    ) {
                        Text("00:01:23", color = Color(textColor).copy(alpha = 0.7f), fontSize = (14 * timerSize).sp)
                        Text("یہ اردو سب ٹائٹل کا نمونہ ہے", color = Color(textColor), fontSize = (18 * textSize).sp, textAlign = TextAlign.Center)
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // فائل سلیکٹر بٹنز
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { openPicker("srt") }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))) {
                        Icon(Icons.Default.Subtitles, null)
                        Spacer(Modifier.width(4.dp))
                        Text("SRT File")
                    }
                    Button(onClick = { openPicker("font") }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3))) {
                        Icon(Icons.Default.FontDownload, null)
                        Spacer(Modifier.width(4.dp))
                        Text("Urdu Font")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // سٹارٹ اور سٹاپ بٹنز
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(modifier = Modifier.weight(1f), onClick = {
                        if (!Settings.canDrawOverlays(this@MainActivity)) {
                            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                        } else {
                            refreshService()
                        }
                    }) { Text("Start Player") }

                    Button(modifier = Modifier.weight(1f), onClick = {
                        stopService(Intent(this@MainActivity, SubtitleService::class.java))
                    }, colors = ButtonDefaults.buttonColors(containerColor = Color.Red)) { 
                        Text("Stop Player", color = Color.White) 
                    }
                }

                Divider(modifier = Modifier.padding(vertical = 15.dp))

                // سلائیڈرز مع آئیکنز
                SettingSlider("Text Size", textSize, Icons.Default.FormatSize, 0.5f..3.0f) {
                    textSize = it
                    prefs.edit().putFloat("text_size", it).apply()
                    refreshService()
                }

                SettingSlider("Timer Size", timerSize, Icons.Default.Timer, 0.5f..2.0f) {
                    timerSize = it
                    prefs.edit().putFloat("timer_size", it).apply()
                    refreshService()
                }

                SettingSlider("Background Width", bgWidth, Icons.Default.WidthFull, 0.4f..1.0f) {
                    bgWidth = it
                    prefs.edit().putFloat("bg_width", it).apply()
                    refreshService()
                }

                SettingSlider("Opacity", opacity, Icons.Default.Opacity, 0.0f..1.0f) {
                    opacity = it
                    prefs.edit().putFloat("opacity", it).apply()
                    refreshService()
                }

                Spacer(modifier = Modifier.height(20.dp))
                
                // رنگ بدلنے کے بٹنز
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    ColorBtn("Text Color", Color(textColor)) {
                        val next = if(textColor == Color.White.toArgb()) Color.Yellow.toArgb() else Color.White.toArgb()
                        textColor = next
                        prefs.edit().putInt("text_color", next).apply()
                        refreshService()
                    }
                    ColorBtn("BG Color", Color(bgColor)) {
                        val next = if(bgColor == Color.Black.toArgb()) Color.DarkGray.toArgb() else Color.Black.toArgb()
                        bgColor = next
                        prefs.edit().putInt("bg_color", next).apply()
                        refreshService()
                    }
                }
                Spacer(modifier = Modifier.height(50.dp))
            }
        }
    }

    private fun openPicker(type: String) {
        val intent = Intent(this, FilePickerActivity::class.java)
        intent.putExtra("type", type)
        startActivity(intent)
    }

    @Composable
    fun SettingSlider(label: String, value: Float, icon: ImageVector, range: ClosedFloatingPointRange<Float>, onValueChange: (Float) -> Unit) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("$label: ${String.format("%.1f", value)}", style = MaterialTheme.typography.bodyMedium)
            }
            Slider(value = value, onValueChange = onValueChange, valueRange = range)
        }
    }

    @Composable
    fun ColorBtn(label: String, col: Color, onClick: () -> Unit) {
        Button(onClick = onClick, colors = ButtonDefaults.buttonColors(containerColor = col), 
            modifier = Modifier.border(1.dp, Color.Gray, RoundedCornerShape(8.dp))) {
            Text(label, color = if(col == Color.White) Color.Black else Color.White)
        }
    }

    private fun refreshService() {
        startForegroundService(Intent(this, SubtitleService::class.java))
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
