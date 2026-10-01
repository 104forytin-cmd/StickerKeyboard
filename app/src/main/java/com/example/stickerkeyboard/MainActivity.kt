package com.example.stickerkeyboard

import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {

    private var selectedVideoUri: Uri? = null
    private lateinit var tvStatus: TextView
    private lateinit var btnProcess: Button

    private val pickVideoLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            selectedVideoUri = uri
            btnProcess.isEnabled = true
            tvStatus.text = "Status: Video selected"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val btnPickVideo = findViewById<Button>(R.id.btnPickVideo)
        btnProcess = findViewById<Button>(R.id.btnProcess)
        val etStartTime = findViewById<EditText>(R.id.etStartTime)
        val etDuration = findViewById<EditText>(R.id.etDuration)
        tvStatus = findViewById<TextView>(R.id.tvStatus)

        btnPickVideo.setOnClickListener {
            pickVideoLauncher.launch("video/*")
        }

        btnProcess.setOnClickListener {
            val uri = selectedVideoUri ?: return@setOnClickListener
            val start = etStartTime.text.toString()
            val duration = etDuration.text.toString()
            processVideo(uri, start, duration)
        }
    }

    private fun processVideo(uri: Uri, start: String, duration: String) {
        tvStatus.text = "Status: Processing..."
        btnProcess.isEnabled = false

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val inputTempFile = File(cacheDir, "input_temp.mp4")
                contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(inputTempFile).use { output ->
                        input.copyTo(output)
                    }
                }

                val stickersDir = File(filesDir, "stickers")
                if (!stickersDir.exists()) {
                    stickersDir.mkdirs()
                }

                val outWebp = File(stickersDir, "sticker.webp")
                val outAudio = File(stickersDir, "audio.m4a")

                if (outWebp.exists()) outWebp.delete()
                if (outAudio.exists()) outAudio.delete()

                val webpCmd = "-ss $start -t $duration -i \"${inputTempFile.absolutePath}\" -vf \"fps=15,scale=512:512:force_original_aspect_ratio=decrease,pad=512:512:(ow-iw)/2:(oh-ih)/2:color=transparent\" -c:v libwebp -lossless 0 -q:v 50 -loop 0 -an -y \"${outWebp.absolutePath}\""
                val audioCmd = "-ss $start -t $duration -i \"${inputTempFile.absolutePath}\" -vn -c:a aac -b:a 64k -y \"${outAudio.absolutePath}\""

                val webpSession = FFmpegKit.execute(webpCmd)
                val audioSession = FFmpegKit.execute(audioCmd)

                withContext(Dispatchers.Main) {
                    if (ReturnCode.isSuccess(webpSession.returnCode) && ReturnCode.isSuccess(audioSession.returnCode)) {
                        tvStatus.text = "Status: Success! WebP size: ${outWebp.length() / 1024}KB"
                    } else {
                        tvStatus.text = "Status: FFmpeg Error. Check logs."
                    }
                    btnProcess.isEnabled = true
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    tvStatus.text = "Status: Error - ${e.message}"
                    btnProcess.isEnabled = true
                }
            }
        }
    }
}
