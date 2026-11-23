package com.nirvor.app.games

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/** Short sounds, made by the app itself (no audio files). Call [release] when the screen closes. */
class GameSound(context: Context, private val player: PlayerProfile) {
    enum class S { Hit, Wall, Pocket, Tap, Win, Lose }

    private val pool = SoundPool.Builder().setMaxStreams(8).setAudioAttributes(
        AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
    ).build()

    private val ids: Map<S, Int> = try {
        val dir = File(context.cacheDir, "gamesound").apply { mkdirs() }
        S.entries.associateWith { s ->
            val f = File(dir, "${s.name}1.wav")
            if (!f.exists()) f.writeBytes(wav(make(s)))
            pool.load(f.absolutePath, 1)
        }
    } catch (e: Exception) {
        emptyMap() // no space: play silently
    }

    fun play(s: S, volume: Float = 1f) {
        if (player.sound) ids[s]?.let { pool.play(it, volume, volume, 1, 0, 1f) }
    }

    fun release() = pool.release()

    private fun make(s: S): FloatArray = when (s) {
        S.Hit -> tone(0.07, 70.0) { t, n -> 0.6 * sin(2 * PI * 2300 * t) + 0.35 * sin(2 * PI * 3700 * t) + 0.25 * n }
        S.Wall -> tone(0.11, 45.0) { t, n -> 0.8 * sin(2 * PI * 420 * t) + 0.2 * n }
        S.Pocket -> tone(0.32, 14.0) { t, n -> 0.7 * sin(2 * PI * (210 - 220 * t) * t) + 0.18 * n }
        S.Tap -> tone(0.06, 80.0) { t, n -> 0.7 * sin(2 * PI * 900 * t) + 0.1 * n }
        S.Win -> melody(523.0, 659.0, 784.0, 1047.0)
        S.Lose -> melody(392.0, 330.0, 262.0)
    }

    private fun tone(sec: Double, fade: Double, f: (Double, Double) -> Double): FloatArray {
        val r = Random(7)
        return FloatArray((sec * RATE).toInt()) { i ->
            val t = i.toDouble() / RATE
            (f(t, r.nextDouble() * 2 - 1) * exp(-fade * t)).toFloat()
        }
    }

    private fun melody(vararg notes: Double): FloatArray =
        notes.map { n -> tone(0.16, 9.0) { t, _ -> 0.5 * sin(2 * PI * n * t) } }.reduce { a, b -> a + b }

    private fun wav(x: FloatArray): ByteArray {
        val b = ByteBuffer.allocate(44 + x.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        b.put("RIFF".toByteArray()).putInt(36 + x.size * 2).put("WAVEfmt ".toByteArray()).putInt(16)
        b.putShort(1).putShort(1).putInt(RATE).putInt(RATE * 2).putShort(2).putShort(16)
        b.put("data".toByteArray()).putInt(x.size * 2)
        x.forEach { b.putShort((it.coerceIn(-1f, 1f) * 32000).toInt().toShort()) }
        return b.array()
    }

    private companion object {
        const val RATE = 22050
    }
}
