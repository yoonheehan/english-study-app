package com.grayman.englishstudy.speech

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.concurrent.thread

/**
 * Owns the microphone: writes 16 kHz mono PCM to a WAV file and, at the same
 * time, to a pipe that the speech recognizer reads. This is how one utterance
 * is both saved for playback and transcribed.
 */
class PcmRecorder(
    private val wavFile: File,
    pipe: ParcelFileDescriptor?,
    private val onFinished: (Long) -> Unit,
) {
    private val pipeOut: OutputStream? = pipe?.let { ParcelFileDescriptor.AutoCloseOutputStream(it) }

    @Volatile
    private var running = true
    private var worker: Thread? = null

    @SuppressLint("MissingPermission") // The caller only starts this after RECORD_AUDIO is granted.
    fun start() {
        val minBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, ENCODING)
        val record = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            ENCODING,
            minBuffer * 4,
        )
        worker = thread(name = "pcm-recorder") {
            var dataBytes = 0L
            var pipeOpen = pipeOut != null
            wavFile.parentFile?.mkdirs()
            try {
                RandomAccessFile(wavFile, "rw").use { file ->
                    file.setLength(0)
                    file.write(ByteArray(WAV_HEADER_SIZE))
                    val buffer = ByteArray(minBuffer)
                    record.startRecording()
                    while (running && dataBytes < MAX_DATA_BYTES) {
                        val read = record.read(buffer, 0, buffer.size)
                        if (read <= 0) continue
                        file.write(buffer, 0, read)
                        dataBytes += read
                        if (pipeOpen) {
                            try {
                                pipeOut?.write(buffer, 0, read)
                            } catch (_: IOException) {
                                // The recognizer stopped reading; keep saving the file.
                                pipeOpen = false
                            }
                        }
                    }
                    file.seek(0)
                    file.write(wavHeader(dataBytes))
                }
            } finally {
                runCatching { record.stop() }
                record.release()
                runCatching { pipeOut?.close() }
                onFinished(dataBytes)
            }
        }
    }

    fun stop() {
        running = false
    }

    companion object {
        const val SAMPLE_RATE = 16_000
        const val CHANNEL_COUNT = 1
        const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val WAV_HEADER_SIZE = 44
        private const val BYTES_PER_SAMPLE = 2
        private const val MAX_SECONDS = 15
        private const val MAX_DATA_BYTES = (SAMPLE_RATE * BYTES_PER_SAMPLE * MAX_SECONDS).toLong()

        fun wavHeader(dataBytes: Long): ByteArray {
            val byteRate = SAMPLE_RATE * CHANNEL_COUNT * BYTES_PER_SAMPLE
            return ByteBuffer.allocate(WAV_HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN).apply {
                put("RIFF".toByteArray())
                putInt((dataBytes + WAV_HEADER_SIZE - 8).toInt())
                put("WAVEfmt ".toByteArray())
                putInt(16)
                putShort(1)
                putShort(CHANNEL_COUNT.toShort())
                putInt(SAMPLE_RATE)
                putInt(byteRate)
                putShort((CHANNEL_COUNT * BYTES_PER_SAMPLE).toShort())
                putShort((BYTES_PER_SAMPLE * 8).toShort())
                put("data".toByteArray())
                putInt(dataBytes.toInt())
            }.array()
        }
    }
}
