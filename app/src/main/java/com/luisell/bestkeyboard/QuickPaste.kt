package com.luisell.bestkeyboard

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri

/**
 * Sugerencias rápidas arriba del teclado (como Gboard):
 *  - el texto que acabas de copiar,
 *  - el código de verificación del último SMS.
 */
object QuickPaste {
    /** Cuánto tiempo se ofrece lo copiado o el código. */
    const val MAX_AGE_MS = 3 * 60 * 1000L
    private const val SMS_AGE_MS = 10 * 60 * 1000L

    /** [picker] = botón "Mis contraseñas" (abre la ventanita para elegir una). */
    class Item(val text: String, val label: String, val isCode: Boolean, val sig: String, val time: Long, val picker: Boolean = false)

    private val KEYWORDS = Regex(
        "(?i)(c[oó]digo|code|otp|verif|pin\\b|contrase|password|passcode|clave|token|seguridad|security|autentica|authenticat|login|inicio de sesi)",
    )
    private val CODE = Regex("(?<![\\d\\w])(\\d{4,8})(?![\\d])")
    private val SPLIT_CODE = Regex("(?<!\\d)(\\d{3})[- ](\\d{3})(?!\\d)")

    /** ¿El texto copiado es un código? (solo números, 4 a 8) */
    fun asCode(text: String): String? {
        val t = text.trim().replace(" ", "").replace("-", "")
        return if (t.matches(Regex("\\d{4,8}"))) t else null
    }

    /** Busca el código en un mensaje ("Tu código de verificación es 482913"). */
    fun findCode(body: String): String? {
        if (!KEYWORDS.containsMatchIn(body)) return null
        SPLIT_CODE.find(body)?.let { return it.groupValues[1] + it.groupValues[2] }
        // Si hay varios números, el más cercano a la palabra "código"
        val k = KEYWORDS.find(body)?.range?.first ?: 0
        return CODE.findAll(body)
            .map { it.groupValues[1] to it.range.first }
            .filter { !(it.first.length == 4 && it.first.startsWith("20") && body.contains("/" + it.first)) } // fechas
            .minByOrNull { kotlin.math.abs(it.second - k) }?.first
    }

    fun preview(text: String): String {
        val one = text.trim().replace(Regex("\\s+"), " ")
        return if (one.length > 24) one.take(23) + "…" else one
    }

    fun canReadSms(ctx: Context) =
        ctx.checkSelfPermission(android.Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED

    /** El código del SMS más nuevo de los últimos minutos. (Llamar en segundo plano) */
    fun recentSmsCode(ctx: Context): Item? {
        if (!canReadSms(ctx)) return null
        val since = System.currentTimeMillis() - SMS_AGE_MS
        return try {
            ctx.contentResolver.query(
                Uri.parse("content://sms/inbox"), arrayOf("_id", "body", "date"),
                "date > ?", arrayOf(since.toString()), "date DESC",
            )?.use { c ->
                var n = 0
                while (c.moveToNext() && n < 5) {
                    n++
                    val body = c.getString(1) ?: continue
                    val code = findCode(body) ?: continue
                    return@use Item(code, L.t("Código: ", "Code: ") + code, true, "sms:" + c.getLong(0), c.getLong(2))
                }
                null
            }
        } catch (_: Exception) { null }
    }
}
