package app.detent.launcher

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/** Detent clicks and haptics. Both follow the user's toggles and the system settings. */
class Feedback(private val ctx: Context) {
    var sound = true
    var haptics = true

    private val vibrator: Vibrator? =
        if (Build.VERSION.SDK_INT >= 31) ctx.getSystemService(VibratorManager::class.java)?.defaultVibrator
        else @Suppress("DEPRECATION") ctx.getSystemService(Vibrator::class.java)
    private val audio = ctx.getSystemService(AudioManager::class.java)
    private val sonification = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()
    private val pool = SoundPool.Builder().setMaxStreams(4).setAudioAttributes(sonification).build()

    @Volatile private var weakId = 0
    @Volatile private var strongId = 0

    init {
        thread(name = "detent-sound", isDaemon = true) {
            weakId = pool.load(wav("detent-weak.wav", synth(false)).path, 1)
            strongId = pool.load(wav("detent-strong.wav", synth(true)).path, 1)
        }
    }

    /** A short click. [strong] is the lower, firmer click for letters, long presses and app opens. */
    fun click(strong: Boolean) {
        if (!sound || audio?.ringerMode != AudioManager.RINGER_MODE_NORMAL) return
        val id = if (strong) strongId else weakId
        if (id != 0) pool.play(id, 1f, 1f, 1, 0, 0.9f + Random.nextFloat() * 0.2f)
    }

    /** One app passes the notch. */
    fun tick() = predefined(VibrationEffect.EFFECT_TICK)

    /** A button press. */
    fun tap() = predefined(VibrationEffect.EFFECT_TICK)

    /** One letter passes the notch. */
    fun letter() = predefined(VibrationEffect.EFFECT_CLICK)

    /** A long press or an app open. */
    fun heavy() = predefined(VibrationEffect.EFFECT_HEAVY_CLICK)

    /** The inward pull is armed, distinct from the dial ticks. */
    fun arm() = predefined(VibrationEffect.EFFECT_DOUBLE_CLICK)

    /** An app goes on or off the dial. */
    fun double() = predefined(VibrationEffect.EFFECT_DOUBLE_CLICK)

    /** The action is not allowed. */
    fun refuse() = vibrate(VibrationEffect.createWaveform(longArrayOf(0, 10, 60, 10), -1))

    fun release() = pool.release()

    private fun predefined(id: Int) = vibrate(VibrationEffect.createPredefined(id))

    private fun vibrate(effect: VibrationEffect) {
        if (!haptics) return
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                v.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH))
            } else {
                @Suppress("DEPRECATION") v.vibrate(effect, sonification)
            }
        } catch (_: RuntimeException) {
        }
    }

    // The same click the design builds with Web Audio: a band-passed noise burst plus a falling sine chirp.
    private fun synth(strong: Boolean): FloatArray {
        val sr = RATE
        val n = (sr * 0.1).toInt()
        val out = FloatArray(n)
        val burst = (sr * 0.014).toInt()
        val f0 = if (strong) 1800.0 else 3400.0
        val q = 1.4
        val w0 = 2 * PI * f0 / sr
        val alpha = sin(w0) / (2 * q)
        val a0 = 1 + alpha
        val a1 = -2 * cos(w0)
        val a2 = 1 - alpha
        val gain = if (strong) 0.9 else 0.55
        val rnd = Random(7)
        var x1 = 0.0; var x2 = 0.0; var y1 = 0.0; var y2 = 0.0
        for (i in 0 until n) {
            val x = if (i < burst) (rnd.nextDouble() * 2 - 1) * (1 - i.toDouble() / burst).pow(7) else 0.0
            val y = (alpha * x - alpha * x2 - a1 * y1 - a2 * y2) / a0
            x2 = x1; x1 = x; y2 = y1; y1 = y
            out[i] += (y * gain).toFloat()
        }
        val fa = if (strong) 520.0 else 1500.0
        val fb = if (strong) 180.0 else 700.0
        val ga = if (strong) 0.18 else 0.06
        val decay = if (strong) 0.08 else 0.025
        var phase = 0.0
        for (i in 0 until n) {
            val t = i.toDouble() / sr
            val f = if (t < 0.02) fa * (fb / fa).pow(t / 0.02) else fb
            phase += 2 * PI * f / sr
            val g = if (t < decay) ga * (0.0001 / ga).pow(t / decay) else 0.0
            out[i] += (sin(phase) * g).toFloat()
        }
        return out
    }

    private fun wav(name: String, samples: FloatArray): File {
        val data = ByteBuffer.allocate(44 + samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        data.put("RIFF".toByteArray()).putInt(36 + samples.size * 2).put("WAVE".toByteArray())
        data.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(1).putInt(RATE).putInt(RATE * 2)
            .putShort(2).putShort(16)
        data.put("data".toByteArray()).putInt(samples.size * 2)
        for (s in samples) data.putShort((s.coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort())
        return File(ctx.cacheDir, name).apply { writeBytes(data.array()) }
    }

    private companion object {
        const val RATE = 44100
    }
}
