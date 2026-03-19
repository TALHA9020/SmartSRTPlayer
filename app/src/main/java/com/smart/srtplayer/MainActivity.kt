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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

class MainActivity : ComponentActivity() {

    companion object {
        const val PREFS_NAME = "SmartPrefs"
        var currentSubtitleList = mutableListOf<SubtitleItem>()
        var currentPlaylist = mutableListOf<PlaylistItem>()
        var currentSrtPath: String? = null

        // SRT فائل کو پڑھنے اور ڈیٹا میں تبدیل کرنے کا فنکشن
        fun loadSubtitle(context: Context, path: String) {
            val file = File(path)
            if (file.exists()) {
                currentSubtitleList.clear()
                val content = file.readText()
                val blocks = content.split(Regex("(\\n\\n)|(\\r\\n\\r\\n)"))
                for (block in blocks) {
                    val lines = block.trim().lines()
                    if (lines.size >= 3) {
                        val timeRange = lines[1].split(" --> ")
                        if (timeRange.size == 2) {
                            currentSubtitleList.add(SubtitleItem(
                                timeToMs(timeRange[0]), 
                                timeToMs(timeRange[1]), 
                                lines.drop(2).joinToString("\n")
                            ))
                        }
                    }
                }
                currentSrtPath = path
                context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                    .edit().putString("last_srt_path", path).apply()
            }
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

    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

        // نوٹیفیکیشن کی اجازت (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
        }

        loadPlaylistData()
        prefs.getString("last_srt_path", null)?.let { loadSubtitle(this, it) }

        setContent {
            MaterialTheme {
                MainScreen()
            }
        }
    }

    private fun loadPlaylistData() {
        val json = prefs.getString("playlist", "[]")
        val type = object : TypeToken<MutableList<PlaylistItem>>() {}.type
        currentPlaylist = Gson().fromJson(json, type)
    }

    @Composable
    fun MainScreen() {
        var playlistState by remember { mutableStateOf(currentPlaylist.toList()) }
        var selectedPath by remember { mutableStateOf(currentSrtPath) }
        
        // سیٹنگز اسٹیٹس
        var textSize by remember { mutableStateOf(prefs.getFloat("text_size", 20f)) }
        var opacity by remember { mutableStateOf(prefs.getFloat("opacity", 0.8f)) }

        Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
            Text("Smart SRT Player", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
            
            Spacer(modifier = Modifier.height(20.dp))

            // --- ڈیزائن کنٹرولز ---
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Design Controls", style = MaterialTheme.typography.titleMedium)
                    
                    Text("Font Size: ${textSize.toInt()}")
                    Slider(value = textSize, valueRange = 12f..50f, onValueChange = { 
                        textSize = it
                        prefs.edit().putFloat("text_size", it).apply()
                    })

                    Text("Background Opacity: ${(opacity * 100).toInt()}%")
                    Slider(value = opacity, valueRange = 0f..1f, onValueChange = { 
                        opacity = it
                        prefs.edit().putFloat("opacity", it).apply()
                    })

                    Button(onClick = { openPicker("font") }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.TextFields, null)
                        Text(" Select Custom Font (.ttf)")
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // --- پلے لسٹ ---
            Text("Subtitle Playlist", style = MaterialTheme.typography.titleLarge)
            Button(onClick = { openPicker("srt") }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Icon(Icons.Default.Add, null)
                Text(" Add New Subtitle")
            }

            playlistState.forEach { item ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        .background(if(selectedPath == item.path) Color.LightGray else Color.Transparent)
                        .clickable { 
                            loadSubtitle(this@MainActivity, item.path)
                            selectedPath = item.path
                        }.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Description, null)
                    Text(item.name, modifier = Modifier.weight(1f).padding(start = 8.dp))
                    if(selectedPath == item.path) Icon(Icons.Default.CheckCircle, null, tint = Color.Green)
                }
            }

            Spacer(modifier = Modifier.height(30.dp))

            // --- پلیئر شروع کریں ---
            Button(
                onClick = { startFloatingService() },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
            ) {
                Text("OPEN PLAYER WINDOW", fontSize = 18.sp)
            }
        }
    }

    private fun openPicker(type: String) {
        startActivity(Intent(this, FilePickerActivity::class.java).putExtra("type", type))
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
