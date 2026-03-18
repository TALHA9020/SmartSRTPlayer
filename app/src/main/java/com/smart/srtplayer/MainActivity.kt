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

// ڈیٹا ماڈل
data class SubtitleItem(val start: Long, val end: Long, val text: String)

class MainActivity : ComponentActivity() {
    
    companion object {
        val fullSubtitleList = mutableListOf<SubtitleItem>()
        const val PREFS_NAME = "SmartPrefs"

        // فائل سے سب ٹائٹل لوڈ کرنے کا فنکشن
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
            
            // سٹیٹ مینجمنٹ
            var textSize by remember { mutableFloatStateOf(prefs.getFloat("text_size", 22f)) }
            var opacity by remember { mutableFloatStateOf(prefs.getFloat("opacity", 0.7f)) }
            var textColor by remember { mutableIntStateOf(prefs.getInt("text_color", Color.White.toArgb())) }
            var bgColor by remember { mutableIntStateOf(prefs.getInt("bg_color", Color.Black.toArgb())) }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Smart SRT Player",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 20.dp)
                )

                // 1 & 2: ایڈ ایس آر ٹی اور ایڈ ٹی ٹی ایف بٹنز (آئیکن کے ساتھ)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { openPicker("srt") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Add SRT")
                    }
                    Button(
                        onClick = { openPicker("font") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    ) {
                        Icon(Icons.Default.FontDownload, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Add TTF")
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // لائیو پری ویو سیکشن
                Text("Live Preview:", style = MaterialTheme.typography.titleMedium)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .padding(vertical = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.DarkGray.copy(0.1f))
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        // پری ویو باکس جو سیٹنگز کے مطابق بدلتا ہے
                        Box(
                            modifier = Modifier
                                .wrapContentSize()
                                .background(
                                    Color(bgColor).copy(alpha = opacity),
                                    RoundedCornerShape(4.dp)
                                )
                                .padding(12.dp)
                        ) {
                            Text(
                                text = "یہاں سب ٹائٹل نظر آئے گا",
                                color = Color(textColor),
                                fontSize = textSize.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // فونٹ سائز سلائیڈر
                Text("Text Size: ${textSize.toInt()} sp")
                Slider(
                    value = textSize,
                    valueRange = 12f..60f,
                    onValueChange = { 
                        textSize = it
                        prefs.edit().putFloat("text_size", it).apply()
                        updateService()
                    }
                )

                // اوپیسٹی سلائیڈر
                Text("Background Opacity: ${(opacity * 100).toInt()}%")
                Slider(
                    value = opacity,
                    valueRange = 0f..1f,
                    onValueChange = { 
                        opacity = it
                        prefs.edit().putFloat("opacity", it).apply()
                        updateService()
                    }
                )

                // کلر بٹنز
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = { 
                            // سادہ ٹوگل (سفید سے پیلا)
                            textColor = if (textColor == Color.White.toArgb()) Color.Yellow.toArgb() else Color.White.toArgb()
                            prefs.edit().putInt("text_color", textColor).apply()
                            updateService()
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("Text Color") }

                    OutlinedButton(
                        onClick = { 
                            // سادہ ٹوگل (کالا سے گہرا نیلا)
                            bgColor = if (bgColor == Color.Black.toArgb()) Color(0xFF1A1A1A).toArgb() else Color.Black.toArgb()
                            prefs.edit().putInt("bg_color", bgColor).apply()
                            updateService()
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("BG Color") }
                }

                Spacer(modifier = Modifier.height(32.dp))

                // سٹارٹ پلیئر بٹن
                Button(
                    onClick = {
                        if (!Settings.canDrawOverlays(this@MainActivity)) {
                            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                            startActivity(intent)
                        } else {
                            startForegroundService(Intent(this@MainActivity, SubtitleService::class.java))
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                ) {
                    Text("🚀 START PLAYER", fontSize = 18.sp)
                }
            }
        }
    }

    private fun openPicker(type: String) {
        val intent = Intent(this, FilePickerActivity::class.java).apply {
            putExtra("type", type)
        }
        startActivity(intent)
    }

    private fun updateService() {
        // سروس کو لائیو اپڈیٹ بھیجنا
        val intent = Intent(this, SubtitleService::class.java)
        startForegroundService(intent)
    }
}
