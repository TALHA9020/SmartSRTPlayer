package com.smart.srtplayer

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class FilePickerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val type = intent.getStringExtra("type") ?: "srt"
        
        val picker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let { 
                if (type == "srt") {
                    addSrtToPlaylist(it)
                } else {
                    saveFontLocally(it)
                }
            }
            finish()
        }
        picker.launch("*/*")
    }

    private fun addSrtToPlaylist(uri: Uri) {
        val fileName = getFileName(uri) ?: "Unknown.srt"
        // فائل کو ایک منفرد نام دے کر ایپ کے فولڈر میں کاپی کرنا
        val destinationFile = File(filesDir, "${UUID.randomUUID()}.srt")
        
        contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(destinationFile).use { output -> input.copyTo(output) }
        }

        // پلے لسٹ (SharedPreferences) میں محفوظ کرنا
        val prefs = getSharedPreferences(MainActivity.PREFS_NAME, MODE_PRIVATE)
        val gson = Gson()
        val playlistJson = prefs.getString("playlist", "[]")
        val itemType = object : TypeToken<MutableList<PlaylistItem>>() {}.type
        val playlist: MutableList<PlaylistItem> = gson.fromJson(playlistJson, itemType)
        
        playlist.add(PlaylistItem(destinationFile.name, fileName, destinationFile.absolutePath))
        
        prefs.edit().putString("playlist", gson.toJson(playlist)).apply()
    }

    private fun saveFontLocally(uri: Uri) {
        val file = File(filesDir, "current_font.ttf")
        contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(file).use { output -> input.copyTo(output) }
        }
        getSharedPreferences(MainActivity.PREFS_NAME, MODE_PRIVATE)
            .edit().putString("last_font_path", file.absolutePath).apply()
    }

    private fun getFileName(uri: Uri): String? {
        var result: String? = null
        if (uri.scheme == "content") {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index != -1) result = cursor.getString(index)
                }
            }
        }
        return result ?: uri.path?.substringAfterLast('/')
    }
}
