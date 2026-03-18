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
            var textSize by remember { mutableFloatStateOf(prefs.getFloat("text_size", 20f)) }
            var opacity by remember { mutableFloatStateOf(prefs.getFloat("opacity", 0.8f)) }
            var bgColor by remember { mutableIntStateOf(prefs.getInt("bg_color", Color.Black.toArgb())) }
            var textColor by remember { mutableIntStateOf(prefs.getInt("text_color", Color.White.toArgb())) }

            Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
                Text("Smart SRT Settings", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // لائیو پری ویو باکس
                Card(
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().height(150.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.LightGray.copy(0.2f))
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        // یہ باکس بالکل فلوٹنگ ونڈو کی طرح دکھے گا
                        Box(modifier = Modifier
                            .wrapContentSize()
                            .background(Color(bgColor).copy(alpha = opacity), RoundedCornerShape(4.dp))
                            .padding(8.dp)
                        ) {
                            Text("اردو سب ٹائٹل نمونہ", color = Color(textColor), fontSize = textSize.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    onClick = {
                        if (!Settings.canDrawOverlays(this@MainActivity)) {
                            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                        } else {
                            startForegroundService(Intent(this@MainActivity, SubtitleService::class.java))
                        }
                    }
                ) {
                    Text("🚀 Start Floating Player")
                }

                Spacer(modifier = Modifier.height(20.dp))

                // سیٹنگز کنٹرولز
                Text("Text Size: ${textSize.toInt()}", style = MaterialTheme.typography.labelLarge)
                Slider(value = textSize, valueRange = 12f..50f, onValueChange = { 
                    textSize = it
                    prefs.edit().putFloat("text_size", it).apply()
                    updateService() 
                })

                Text("Background Opacity: ${(opacity * 100).toInt()}%", style = MaterialTheme.typography.labelLarge)
                Slider(value = opacity, valueRange = 0f..1f, onValueChange = { 
                    opacity = it
                    prefs.edit().putFloat("opacity", it).apply()
                    updateService() 
                })

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    OutlinedButton(onClick = { 
                        textColor = if(textColor == Color.White.toArgb()) Color.Yellow.toArgb() else Color.White.toArgb()
                        prefs.edit().putInt("text_color", textColor).apply()
                        updateService() 
                    }) { Text("Text Color") }

                    OutlinedButton(onClick = { 
                        bgColor = if(bgColor == Color.Black.toArgb()) Color(0xFF222222).toArgb() else Color.Black.toArgb()
                        prefs.edit().putInt("bg_color", bgColor).apply()
                        updateService() 
                    }) { Text("BG Color") }
                }
                
                Spacer(modifier = Modifier.height(30.dp))
                Text("نوٹ: پلیئر ونڈو میں ٹائم آف سیٹ اور بیک گراؤنڈ چھپانے کے بٹنز موجود ہیں۔", 
                    style = MaterialTheme.typography.bodySmall, 
                    color = Color.Gray)
            }
        }
    }

    private fun updateService() {
        // سروس کو صرف تب اپڈیٹ کریں اگر وہ چل رہی ہو
        startForegroundService(Intent(this, SubtitleService::class.java))
    }
}
