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
                
                // سروس کو اپ ڈیٹ کریں
                val serviceIntent = Intent(this, SubtitleService::class.java).apply {
                    if (type == "srt") {
                        putExtra("reset", true) // SRT بدلے تو ٹائمر ری سیٹ
                        MainActivity.loadSubtitleFromFile(this@FilePickerActivity)
                    }
                }
                startForegroundService(serviceIntent)
            }
            finish()
        }
        picker.launch("*/*")
    }

    private fun saveFileLocally(uri: Uri, type: String) {
        val fileName = if (type == "srt") "current_sub.srt" else "current_font.ttf"
        val file = File(filesDir, fileName)
        contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(file).use { output -> input.copyTo(output) }
        }
        val prefs = getSharedPreferences(MainActivity.PREFS_NAME, MODE_PRIVATE)
        if (type == "srt") prefs.edit().putString("last_srt_path", file.absolutePath).apply()
        else prefs.edit().putString("last_font_path", file.absolutePath).apply()
    }
}
