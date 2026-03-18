package com.smart.srtplayer

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import java.io.File
import java.io.FileOutputStream

class FilePickerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val type = intent.getStringExtra("type") ?: "srt"
        
        val picker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let { 
                saveFileLocally(it, type)
                // سروس کو دوبارہ شروع کریں تاکہ نئی فائل فوری لوڈ ہو جائے
                val serviceIntent = Intent(this, SubtitleService::class.java)
                stopService(serviceIntent)
                startForegroundService(serviceIntent)
            }
            finish()
        }

        // فائل ٹائپ کے مطابق پکر کھولیں
        if (type == "srt") picker.launch("*/*") else picker.launch("*/*")
    }

    private fun saveFileLocally(uri: Uri, type: String) {
        val inputStream = contentResolver.openInputStream(uri)
        val fileName = if (type == "srt") "current_sub.srt" else "current_font.ttf"
        val file = File(filesDir, fileName)
        
        inputStream?.use { input ->
            FileOutputStream(file).use { output ->
                input.copyTo(output)
            }
        }
        
        val prefs = getSharedPreferences(MainActivity.PREFS_NAME, MODE_PRIVATE)
        if (type == "srt") {
            prefs.edit().putString("last_srt_path", file.absolutePath).apply()
            // نئی فائل لوڈ ہونے پر ٹائمر زیرو کر دیں
            prefs.edit().putLong("last_time_ms", 0L).apply()
        } else {
            prefs.edit().putString("last_font_path", file.absolutePath).apply()
        }
    }
}
