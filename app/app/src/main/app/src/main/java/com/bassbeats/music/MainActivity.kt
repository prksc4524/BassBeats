package com.bassbeats.music

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.MediaPlayer
import android.media.audiofx.BassBoost
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView

class MainActivity : Activity() {
    private var player: MediaPlayer? = null
    private var bass: BassBoost? = null
    private val songs = mutableListOf<Pair<String, String>>()
    private var current = 0
    private lateinit var songList: LinearLayout
    private lateinit var status: TextView

    private fun audioPermission() =
        if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
        else Manifest.permission.READ_EXTERNAL_STORAGE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(12, 12, 20)
        window.navigationBarColor = Color.rgb(12, 12, 20)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 24, 20, 20)
            setBackgroundColor(Color.rgb(12, 12, 20))
        }
        root.addView(TextView(this).apply {
            text = "🎧 BASS BEATS"; textSize = 28f
            setTextColor(Color.CYAN); gravity = 17
        })
        status = TextView(this).apply {
            text = "Your offline music"; textSize = 16f
            setTextColor(Color.WHITE); gravity = 17
            setPadding(0, 20, 0, 20)
        }
        root.addView(status)
        val controls = LinearLayout(this).apply { gravity = 17 }
        fun addControl(label: String, action: () -> Unit) {
            controls.addView(Button(this).apply {
                text = label; setOnClickListener { action() }
            })
        }
        addControl("⏮") { play(current - 1) }
        addControl("▶ / ⏸") {
            when {
                player?.isPlaying == true -> player?.pause()
                player != null -> player?.start()
                else -> play(current)
            }
        }
        addControl("⏭") { play(current + 1) }
        root.addView(controls)
        root.addView(TextView(this).apply {
            text = "BASS BOOST"; setTextColor(Color.CYAN)
        })
        val bassSlider = SeekBar(this).apply { max = 100 }
        root.addView(bassSlider)
        bassSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                try { bass?.setStrength((progress * 10).toShort()) } catch (_: Exception) {}
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        val scroll = ScrollView(this)
        songList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(songList)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)

        val permission = audioPermission()
        if (checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED) scanSongs()
        else requestPermissions(arrayOf(permission), 10)
    }

    private fun scanSongs() {
        songs.clear()
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media._ID)
        contentResolver.query(
            collection, projection, "${MediaStore.Audio.Media.IS_MUSIC} != 0",
            null, "${MediaStore.Audio.Media.TITLE} ASC"
        )?.use { cursor ->
            val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            while (cursor.moveToNext()) {
                val title = cursor.getString(titleColumn) ?: "Unknown"
                val id = cursor.getLong(idColumn)
                val uri = android.content.ContentUris.withAppendedId(collection, id).toString()
                songs.add(title to uri)
            }
        }
        songList.removeAllViews()
        if (songs.isEmpty()) { status.text = "No songs found"; return }
        status.text = "${songs.size} songs found"
        songs.forEachIndexed { index, song ->
            songList.addView(TextView(this).apply {
                text = "♫  ${song.first}"; textSize = 17f
                setTextColor(Color.WHITE); setPadding(12, 16, 12, 16)
                setOnClickListener { play(index) }
            })
        }
    }

    private fun play(index: Int) {
        if (songs.isEmpty()) { status.text = "No songs found"; return }
        current = ((index % songs.size) + songs.size) % songs.size
        try {
            bass?.release(); bass = null
            player?.release()
            player = MediaPlayer().apply {
                setDataSource(this@MainActivity, android.net.Uri.parse(songs[current].second))
                setOnCompletionListener { play(current + 1) }
                prepare(); start()
            }
            bass = BassBoost(0, player!!.audioSessionId).apply { enabled = true }
            status.text = "▶ ${songs[current].first}"
        } catch (_: Exception) { status.text = "Cannot play this song" }
    }

    @Deprecated("Deprecated in Android API")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 10 && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) scanSongs()
        else status.text = "Audio permission required"
    }

    override fun onDestroy() {
        player?.release(); bass?.release()
        super.onDestroy()
    }
}
