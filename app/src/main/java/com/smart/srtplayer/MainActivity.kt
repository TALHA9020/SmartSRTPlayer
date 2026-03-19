package com.smart.srtplayer

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.nio.charset.Charset

class MainActivity : ComponentActivity() {

    companion object {
        const val PREFS_NAME = "SmartPrefs"
        var currentSubtitleList = mutableListOf<SubtitleItem>()
        var currentPlaylist = mutableListOf<PlaylistItem>()
        var currentSrtPath: String? = null

        // اردو اور UTF-8 سپورٹ کے ساتھ لوڈر
        fun loadSubtitle(context: Context, path: String) {
            val file = File(path)
            if (!file.exists()) return
            
            try {
                currentSubtitleList.clear()
                // فائل کو UTF-8 میں پڑھنا تاکہ اردو حروف ٹھیک رہیں
                val content = file.readText(Charsets.UTF_8)
                
                // بلاکس میں تقسیم کرنا (لائن بریکس کے مختلف انداز کو ہینڈل کرنا)
                val blocks = content.split(Regex("\\n\\s*\\n|\\r\\n\\s*\\r\\n"))
                
                for (block in blocks) {
                    val lines = block.trim().lines()
                    val timeLine = lines.find { it.contains(" --> ") }
                    
                    if (timeLine != null) {
                        val timeRange = timeLine.split(" --> ")
                        if (timeRange.size == 2) {
                            val startIndex = lines.indexOf(timeLine) + 1
                            val text = lines.drop(startIndex).joinToString("\n").trim()
                            
                            if (text.isNotEmpty()) {
                                currentSubtitleList.add(SubtitleItem(
                                    timeToMs(timeRange[0]), 
                                    timeToMs(timeRange[1]), 
                                    text
                                ))
                            }
                        }
                    }
                }
                currentSrtPath = path
                context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                    .edit().putString("last_srt_path", path).apply()
            } catch (e: Exception) { e.printStackTrace() }
        }

        private fun timeToMs(time: String): Long {
            return try {
                val cleanTime = time.trim().replace(",", ".")
                val parts = cleanTime.split(":")
                val secondsParts = parts[2].split(".")
                
                val h = parts[0].toLong() * 3600000
                val m = parts[1].toLong() * 60000
                val s = secondsParts[0].toLong() * 1000
                val ms = if (secondsParts.size > 1) secondsParts[1].padEnd(3, '0').take(3).toLong() else 0L
                h + m + s + ms
            } catch (e: Exception) { 0L }
        }
    }

    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
        }

        loadPlaylistData()
        prefs.getString("last_srt_path", null)?.let { loadSubtitle(this, it) }

        setContent { MaterialTheme { MainScreen() } }
    }

    private fun loadPlaylistData() {
        val json = prefs.getString("playlist", "[]")
        currentPlaylist = Gson().fromJson(json, object : TypeToken<MutableList<PlaylistItem>>() {}.type)
    }

    @Composable
    fun MainScreen() {
        var playlistState by remember { mutableStateOf(currentPlaylist.toList()) }
        var selectedPath by remember { mutableStateOf(currentSrtPath) }
        var textSize by remember { mutableStateOf(prefs.getFloat("text_size", 24f)) }
        var opacity by remember { mutableStateOf(prefs.getFloat("opacity", 0.8f)) }

        Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
            Text("Urdu SRT Player", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
            
            Spacer(modifier = Modifier.height(16.dp))

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Settings", style = MaterialTheme.typography.titleMedium)
                    Text("Text Size: ${textSize.toInt()}")
                    Slider(value = textSize, valueRange = 16f..60f, onValueChange = { 
                        textSize = it
                        prefs.edit().putFloat("text_size", it).apply()
                    })
                    Text("Opacity: ${(opacity * 100).toInt()}%")
                    Slider(value = opacity, valueRange = 0f..1f, onValueChange = { 
                        opacity = it
                        prefs.edit().putFloat("opacity", it).apply()
                    })
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(onClick = { openPicker() }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Add, null)
                Text(" Add Urdu SRT File")
            }

            playlistState.forEach { item ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        .background(if(selectedPath == item.path) Color(0xFFE3F2FD) else Color.Transparent)
                        .clickable { 
                            loadSubtitle(this@MainActivity, item.path)
                            selectedPath = item.path
                        }.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Description, null)
                    Text(item.name, modifier = Modifier.weight(1f).padding(start = 8.dp))
                    if(selectedPath == item.path) Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF4CAF50))
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = { startFloatingService() },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
            ) {
                Text("START PLAYER", fontSize = 18.sp)
            }
        }
    }

    private fun openPicker() {
        startActivity(Intent(this, FilePickerActivity::class.java).putExtra("type", "srt"))
    }

    private fun startFloatingService() {
        if (!Settings.canDrawOverlays(this)) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            return
        }
        val intent = Intent(this, SubtitleService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent)
        else startService(intent)
    }
}
