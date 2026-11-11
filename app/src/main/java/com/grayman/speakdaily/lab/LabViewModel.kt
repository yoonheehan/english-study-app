package com.grayman.speakdaily.lab

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.grayman.speakdaily.ai.GemmaProbe
import com.grayman.speakdaily.speech.SttProbe
import com.grayman.speakdaily.speech.TtsProbe

class LabViewModel(app: Application) : AndroidViewModel(app) {
    val report = LabReport()
    val tts = TtsProbe(app, report)
    val stt = SttProbe(app, report)
    val gemma = GemmaProbe(app, report)

    override fun onCleared() {
        tts.close()
        stt.close()
        gemma.close()
    }
}
