package com.smart.srtplayer

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FontDownload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File

data class SubtitleItem(val start: Long, val end: Long, val text: String)

class MainActivity : ComponentActivity() {
    
    companion object {
        val fullSubtitleList = mutableListOf<SubtitleItem>()
        const val PREFS_NAME = "SmartPrefs"

        fun loadSubtitleFromFile(context: Context) {
            val prefs = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            val path = prefs.getString("last_srt_path", null)
            path?.let { 
                val file = File(it)
                if (file.exists()) {
                    fullSubtitleList.clear()
                    fullSubtitleList.addAll(parseSrt(file.readText()))
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
            return try {
                val parts = time.replace(",", ".").split(":")
                (parts[0].trim().toLong() * 3600000) + 
                (parts[1].trim().toLong() * 60000) + 
                (parts[2].trim().toDouble() * 1000).toLong()
            } catch (e: Exception) { 0L }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        loadSubtitleFromFile(this)

        setContent {
            val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            
            var textSize by remember { mutableFloatStateOf(prefs.getFloat("text_size", 22f)) }
            var opacity by remember { mutableFloatStateOf(prefs.getFloat("opacity", 0.7f)) }
            var textColor by remember { mutableIntStateOf(prefs.getInt("text_color", Color.White.toArgb())) }
            var bgColor by remember { mutableIntStateOf(prefs.getInt("bg_color", Color.Black.toArgb())) }

            Column(
                modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(16.dp).verticalScroll(rememberScrollState())
            ) {
                Text(text = "Smart SRT Player", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)

                Row(modifier = Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = { openPicker("srt") }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Add, null)
                        Text(" SRT")
                    }
                    Button(onClick = { openPicker("font") }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.FontDownload, null)
                        Text(" TTF")
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
                
                // پری ویو
                Card(modifier = Modifier.fillMaxWidth().height(100.dp), colors = CardDefaults.cardColors(containerColor = Color.LightGray.copy(0.2f))) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Box(modifier = Modifier.background(Color(bgColor).copy(alpha = opacity), RoundedCornerShape(4.dp)).padding(8.dp)) {
                            Text("Preview Text", color = Color(textColor), fontSize = textSize.sp)
                        }
                    }
                }

                Slider(value = textSize, valueRange = 12f..60f, onValueChange = { textSize = it; prefs.edit().putFloat("text_size", it).apply(); updateService() })
                Slider(value = opacity, valueRange = 0f..1f, onValueChange = { opacity = it; prefs.edit().putFloat("opacity", it).apply(); updateService() })

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = { textColor = Color.Yellow.toArgb(); prefs.edit().putInt("text_color", textColor).apply(); updateService() }) { Text("Yellow") }
                    Button(onClick = { textColor = Color.White.toArgb(); prefs.edit().putInt("text_color", textColor).apply(); updateService() }) { Text("White") }
                }

                Button(
                    onClick = {
                        if (Settings.canDrawOverlays(this@MainActivity)) {
                            val intent = Intent(this@MainActivity, SubtitleService::class.java)
                            startForegroundService(intent)
                        } else {
                            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                            startActivity(intent)
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                ) { Text("START PLAYER") }
            }
        }
    }

    private fun openPicker(type: String) {
        startActivity(Intent(this, FilePickerActivity::class.java).putExtra("type", type))
    }

    private fun updateService() {
        val intent = Intent(this, SubtitleService::class.java)
        if (Settings.canDrawOverlays(this)) {
            try { startForegroundService(intent) } catch (e: Exception) {}
        }
    }
}
