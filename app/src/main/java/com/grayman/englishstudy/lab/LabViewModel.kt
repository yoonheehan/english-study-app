package com.grayman.englishstudy.lab

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.grayman.englishstudy.ai.GemmaProbe
import com.grayman.englishstudy.speech.SttProbe
import com.grayman.englishstudy.speech.TtsProbe

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
