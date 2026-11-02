package com.luisell.bestkeyboard

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Copia de seguridad: guarda en UN archivo, protegido con tu contraseña, todo lo tuyo:
 * ajustes y estilo, tus palabras aprendidas, tus Datos (correos, contraseñas, atajos) y tus frases.
 * Sin la contraseña nadie lo puede abrir (cifrado AES-256).
 */
object Backup {
    private val MAGIC = "BKB1".toByteArray()

    /** Lo que nunca se copia (cosas del momento). */
    private val SKIP = setOf(Prefs.LAST_FIELD, Prefs.CLIP_LAST_SIG, Prefs.QUICK_DONE, Prefs.SHOT_USED, Prefs.SHOT_ASKS,
        // Cosas de ESTE teléfono que no sirven en otro (la carpeta y la contraseña del respaldo automático van
        // cifradas con una llave que no sale del teléfono; se vuelven a poner en el nuevo)
        Prefs.AUTO_BACKUP_DIR, Prefs.AUTO_BACKUP_PW, Prefs.AUTO_BACKUP_LAST,
        Prefs.AUTO_ENTER, Prefs.OVERTIME_SENT, Prefs.SITE_NEW, Prefs.CHECK_DONE, Prefs.BG_KIND, Prefs.BG_STAMP)

    /** El video de fondo va en la copia si pesa hasta esto (si pesa más, en el teléfono nuevo lo eliges otra vez). */
    const val MAX_VIDEO = 15L * 1024 * 1024

    /** ¿El video de fondo cabe en la copia? */
    fun videoFits(ctx: Context): Boolean = BgMedia.videoFile(ctx).let { it.exists() && it.length() <= MAX_VIDEO }

    private fun key(password: String, salt: ByteArray): SecretKeySpec {
        val f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val k = f.generateSecret(PBEKeySpec(password.toCharArray(), salt, 120_000, 256)).encoded
        return SecretKeySpec(k, "AES")
    }

    /** Arma el archivo de respaldo cifrado. */
    fun export(ctx: Context, password: String): ByteArray {
        val prefs = Prefs.get(ctx)
        val p = JSONObject()
        for ((k, v) in prefs.all) {
            if (k in SKIP || v == null) continue
            val o = JSONObject()
            when (v) {
                is Boolean -> o.put("t", "b").put("v", v)
                is Int -> o.put("t", "i").put("v", v)
                is Long -> o.put("t", "l").put("v", v)
                is Float -> o.put("t", "f").put("v", v.toDouble())
                is String -> o.put("t", "s").put("v", v)
                else -> continue
            }
            p.put(k, o)
        }
        WordStore.saveNow()
        val words = WordStore.engine?.user?.snapshot()
            ?: File(ctx.filesDir, "aprendido.txt").takeIf { it.exists() }?.readText() ?: ""
        VaultStore.load(ctx)
        val vault = JSONArray()
        for (e in VaultStore.items) vault.put(JSONObject().put("k", e.kind).put("l", e.label).put("v", e.value).put("s", e.shortcut))
        val all = JSONObject().put("v", 2).put("prefs", p).put("words", words).put("vault", vault)
        // Portapapeles (textos y las imágenes fijadas)
        try { all.put("clips", ClipStore.exportForBackup(ctx)) } catch (_: Exception) { }
        // Fondo propio: la imagen, o el video si no es muy pesado
        try {
            when (BgMedia.kind(ctx)) {
                "image" -> all.put("bg", JSONObject().put("k", "image")
                    .put("d", android.util.Base64.encodeToString(BgMedia.imageFile(ctx).readBytes(), android.util.Base64.NO_WRAP)))
                "video" -> if (videoFits(ctx)) all.put("bg", JSONObject().put("k", "video")
                    .put("d", android.util.Base64.encodeToString(BgMedia.videoFile(ctx).readBytes(), android.util.Base64.NO_WRAP)))
            }
        } catch (_: Throwable) { }

        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key(password, salt))
        val ct = c.doFinal(all.toString().toByteArray(Charsets.UTF_8))
        return MAGIC + salt + c.iv + ct
    }

    /** Devuelve true si se pudo restaurar; false si la contraseña está mal o el archivo no es válido. */
    fun import(ctx: Context, data: ByteArray, password: String): Boolean {
        if (data.size < 4 + 16 + 12 + 16 || !data.copyOfRange(0, 4).contentEquals(MAGIC)) return false
        val salt = data.copyOfRange(4, 20)
        val iv = data.copyOfRange(20, 32)
        val json = try {
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE, key(password, salt), GCMParameterSpec(128, iv))
            JSONObject(String(c.doFinal(data.copyOfRange(32, data.size)), Charsets.UTF_8))
        } catch (_: Exception) { return false }

        // Ajustes
        val p = json.optJSONObject("prefs")
        if (p != null) {
            val e = Prefs.get(ctx).edit()
            for (k in p.keys()) {
                val o = p.optJSONObject(k) ?: continue
                when (o.optString("t")) {
                    "b" -> e.putBoolean(k, o.optBoolean("v"))
                    "i" -> e.putInt(k, o.optInt("v"))
                    "l" -> e.putLong(k, o.optLong("v"))
                    "f" -> e.putFloat(k, o.optDouble("v").toFloat())
                    "s" -> e.putString(k, o.optString("v"))
                }
            }
            e.apply()
        }
        // Palabras aprendidas
        val words = json.optString("words", "")
        val f = File(ctx.filesDir, "aprendido.txt")
        f.writeText(words)
        WordStore.engine?.user?.let { u ->
            u.clear()
            try { u.load(f) } catch (_: Exception) { }
            u.dirty = false
        }
        // Datos
        val arr = json.optJSONArray("vault")
        if (arr != null) {
            val list = (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                VaultStore.Entry(System.currentTimeMillis() + it, o.optInt("k"), o.optString("l"), o.optString("v"), o.optString("s"))
            }
            VaultStore.replaceAll(ctx, list)
        }
        // Portapapeles
        json.optJSONArray("clips")?.let { try { ClipStore.restoreFromBackup(ctx, it) } catch (_: Exception) { } }
        // Fondo propio
        json.optJSONObject("bg")?.let { bg ->
            try {
                val bytes = android.util.Base64.decode(bg.optString("d"), android.util.Base64.NO_WRAP)
                val kind = bg.optString("k")
                if (kind == "image") { BgMedia.imageFile(ctx).writeBytes(bytes); BgMedia.videoFile(ctx).delete() }
                else if (kind == "video") { BgMedia.videoFile(ctx).writeBytes(bytes); BgMedia.imageFile(ctx).delete() }
                Prefs.get(ctx).edit().putString(Prefs.BG_KIND, kind).putLong(Prefs.BG_STAMP, System.currentTimeMillis()).apply()
            } catch (_: Throwable) { }
        }
        // Emojis recientes y avisos: se vuelven a cargar y programar en este teléfono
        try {
            EmojiData.recent.clear()
            Prefs.get(ctx).getString("emoji_recent", null)?.split('\u0001')?.filter { it.isNotEmpty() }?.let { EmojiData.recent.addAll(it) }
        } catch (_: Exception) { }
        try { WorkDay.config(Prefs.get(ctx)) } catch (_: Exception) { }
        try { WorkdayCenter.refresh(ctx) } catch (_: Exception) { }
        try { TicketReminder.schedule(ctx) } catch (_: Exception) { }
        try { Todos.schedule(ctx) } catch (_: Exception) { }
        try { AutoSites.register(ctx) } catch (_: Exception) { }
        return true
    }
}
