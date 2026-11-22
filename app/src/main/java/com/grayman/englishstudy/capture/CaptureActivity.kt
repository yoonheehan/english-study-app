package com.grayman.englishstudy.capture

import com.grayman.englishstudy.MainActivity
import com.grayman.englishstudy.data.video.YouTubeLinks

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.grayman.englishstudy.domain.CaptureText
import com.grayman.englishstudy.ui.SpeakDailyTheme

/**
 * Opens over the browser when the learner picks "단어 찾기" in the text selection menu,
 * or shares text to this app. It only reads plain text from the intent, never acts on it.
 */
class CaptureActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val input = readInput(intent)
        // A YouTube link shared from the YouTube app opens the video with its subtitles.
        val video = YouTubeLinks.videoId(input.sourceUrl.orEmpty()) ?: YouTubeLinks.videoId(input.text).takeIf { input.text.contains("youtu") }
        if (video != null) {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .putExtra(MainActivity.EXTRA_VIDEO_ID, video)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            )
            finish()
            return
        }
        setContent {
            SpeakDailyTheme {
                CaptureSheet(input = input, onClose = ::finish)
            }
        }
    }

    private fun readInput(intent: Intent?): CaptureInput {
        val raw = when (intent?.action) {
            Intent.ACTION_PROCESS_TEXT -> intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
            else -> null
        }.orEmpty()
        val (text, link) = CaptureText.splitShared(raw)
        return CaptureInput(text = text, sourceUrl = link)
    }
}

data class CaptureInput(val text: String, val sourceUrl: String?)
