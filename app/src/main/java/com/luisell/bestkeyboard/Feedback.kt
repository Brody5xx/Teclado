package com.luisell.bestkeyboard

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Vibración y sonido al tocar una tecla.
 *  - Vibración "moderna": los efectos de clic del propio teléfono (secos y precisos, como en iPhone).
 *  - Sonido "moderno": 3 sonidos originales (letra, borrar, espacio/enter), con SoundPool para que suenen al instante.
 */
object Feedback {
    const val KIND_KEY = 0
    const val KIND_DELETE = 1
    const val KIND_SPACE = 2

    private var vibrator: Vibrator? = null
    private var pool: SoundPool? = null
    private val ids = IntArray(3)
    private var audio: AudioManager? = null

    fun init(ctx: Context) {
        val app = ctx.applicationContext
        if (vibrator == null) {
            vibrator = if (Build.VERSION.SDK_INT >= 31) {
                (app.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                app.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
        }
        if (audio == null) audio = app.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        if (pool == null) {
            val p = SoundPool.Builder()
                .setMaxStreams(4)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                ).build()
            ids[KIND_KEY] = p.load(app, R.raw.click_key, 1)
            ids[KIND_DELETE] = p.load(app, R.raw.click_delete, 1)
            ids[KIND_SPACE] = p.load(app, R.raw.click_space, 1)
            pool = p
        }
    }

    /**
     * Vibra un toque. [style] = system (la vibración de teclado del teléfono, como Gboard) /
     * modern (clic del motor) / classic (pulso fuerte). [strength] = soft / medium / strong.
     * [view] = la vista que se tocó (la usa el modo "system").
     */
    fun vibrate(ctx: Context, style: String?, strength: String?, view: android.view.View? = null) {
        init(ctx)
        if ((style == null || style == "system") && view != null) {
            val c = when (strength) {
                "soft" -> android.view.HapticFeedbackConstants.CLOCK_TICK
                "strong" -> android.view.HapticFeedbackConstants.LONG_PRESS
                else -> if (Build.VERSION.SDK_INT >= 27) android.view.HapticFeedbackConstants.KEYBOARD_TAP else android.view.HapticFeedbackConstants.VIRTUAL_KEY
            }
            @Suppress("DEPRECATION")
            if (view.performHapticFeedback(c, android.view.HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING)) return
        }
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        try {
            if (style == "modern" && Build.VERSION.SDK_INT >= 29) {
                val effect = when (strength) {
                    "soft" -> VibrationEffect.EFFECT_TICK
                    "strong" -> VibrationEffect.EFFECT_HEAVY_CLICK
                    else -> VibrationEffect.EFFECT_CLICK
                }
                v.vibrate(VibrationEffect.createPredefined(effect))
                return
            }
            // Clásica: un pulso corto, más fuerte que antes para que se sienta en todos los teléfonos
            val (ms, amp) = when (strength) {
                "soft" -> 14L to 120
                "strong" -> 35L to 255
                else -> 22L to 200
            }
            if (Build.VERSION.SDK_INT >= 26) {
                v.vibrate(VibrationEffect.createOneShot(ms, if (v.hasAmplitudeControl()) amp else VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(ms)
            }
        } catch (_: Exception) { }
    }

    /** Suena una tecla. No suena si el teléfono está en silencio o vibración (como en iPhone). */
    fun sound(ctx: Context, kind: Int, style: String?, volume: String?) {
        init(ctx)
        val a = audio
        if (a != null && a.ringerMode != AudioManager.RINGER_MODE_NORMAL) return
        if (style == "classic") {
            a?.playSoundEffect(
                when (kind) {
                    KIND_DELETE -> AudioManager.FX_KEYPRESS_DELETE
                    KIND_SPACE -> AudioManager.FX_KEYPRESS_SPACEBAR
                    else -> AudioManager.FX_KEYPRESS_STANDARD
                }, -1f,
            )
            return
        }
        // Suave a propósito: un toque sutil, no un "clac"
        val vol = when (volume) {
            "low" -> 0.18f
            "high" -> 0.6f
            else -> 0.34f
        }
        pool?.play(ids[kind.coerceIn(0, 2)], vol, vol, 1, 0, 1f)
    }
}
