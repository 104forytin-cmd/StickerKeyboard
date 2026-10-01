package com.example.stickerkeyboard

import android.content.ClipDescription
import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.TextView
import androidx.core.content.FileProvider
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.core.view.inputmethod.InputConnectionCompat
import androidx.core.view.inputmethod.InputContentInfoCompat
import java.io.File

class StickerKeyboardService : InputMethodService() {

    private lateinit var tvMimeTypes: TextView
    private lateinit var tvCommitStatus: TextView
    private var acceptedMimeTypes: Array<String> = emptyArray()
    private var currentEditorInfo: EditorInfo? = null

    override fun onCreateInputView(): View {
        val view = layoutInflater.inflate(R.layout.keyboard_view, null)
        tvMimeTypes = view.findViewById(R.id.tvMimeTypes)
        tvCommitStatus = view.findViewById(R.id.tvCommitStatus)
        
        view.findViewById<Button>(R.id.btnSendSticker).setOnClickListener {
            sendStickerAndAudio()
        }
        
        view.findViewById<Button>(R.id.btnOpenApp).setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        }
        return view
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        currentEditorInfo = info
        if (info != null) {
            val mimeTypes = EditorInfoCompat.getContentMimeTypes(info)
            acceptedMimeTypes = mimeTypes
            tvMimeTypes.text = "Accepted MIME types: ${mimeTypes.joinToString(", ")}"
        } else {
            acceptedMimeTypes = emptyArray()
            tvMimeTypes.text = "Accepted MIME types: unknown"
        }
        tvCommitStatus.text = "Commit Status: Idle"
    }

    private fun sendStickerAndAudio() {
        val inputConnection = currentInputConnection
        val editorInfo = currentEditorInfo
        if (inputConnection == null || editorInfo == null) {
            tvCommitStatus.text = "Error: No active input connection"
            return
        }

        val stickersDir = File(filesDir, "stickers")
        val webpFile = File(stickersDir, "sticker.webp")
        val audioFile = File(stickersDir, "audio.m4a")

        if (!webpFile.exists() || !audioFile.exists()) {
            tvCommitStatus.text = "Error: Sticker/Audio not found. Open app to create."
            return
        }

        val webpUri = FileProvider.getUriForFile(this, "$packageName.fileprovider", webpFile)
        val audioUri = FileProvider.getUriForFile(this, "$packageName.fileprovider", audioFile)

        // Commit WebP
        val webpInfo = InputContentInfoCompat(
            webpUri,
            ClipDescription("Sticker", arrayOf("image/webp")),
            null
        )
        var webpSuccess = false
        try {
            webpSuccess = InputConnectionCompat.commitContent(
                inputConnection, editorInfo, webpInfo,
                InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION, null
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }

        tvCommitStatus.text = "Sticker commit: ${if (webpSuccess) "Success" else "Failed"}"

        if (webpSuccess) {
            // Delay 0.4s and commit audio
            Handler(Looper.getMainLooper()).postDelayed({
                val audioInfo = InputContentInfoCompat(
                    audioUri,
                    ClipDescription("Audio", arrayOf("audio/mp4", "audio/mpeg")),
                    null
                )
                var audioSuccess = false
                try {
                    audioSuccess = InputConnectionCompat.commitContent(
                        currentInputConnection!!, currentEditorInfo!!, audioInfo,
                        InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION, null
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                tvCommitStatus.text = "Sticker: Success | Audio: ${if (audioSuccess) "Success" else "Failed"}"
            }, 400)
        }
    }
}
