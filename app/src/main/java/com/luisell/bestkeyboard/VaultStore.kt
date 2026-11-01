package com.luisell.bestkeyboard

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * "Mis datos" (como Samsung Pass): tus correos, contraseñas y atajos de texto.
 * Se guardan CIFRADOS en el teléfono con una llave del Android Keystore (no se puede sacar del teléfono).
 * Nunca se envían por internet.
 */
object VaultStore {
    const val EMAIL = 0
    const val PASSWORD = 1
    const val TEXT = 2

    class Entry(val id: Long, val kind: Int, val label: String, val value: String, val shortcut: String)

    private const val ALIAS = "best_keyboard_vault"
    private const val FILE = "mis_datos.bin"
    private var loadedStamp = -1L

    @Volatile var items: List<Entry> = emptyList()
        private set

    /** Lo que eliges en la ventanita de contraseñas: el teclado lo escribe al volver. */
    @Volatile var pendingInsert: String? = null

    private fun file(ctx: Context) = File(ctx.applicationContext.filesDir, FILE)

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return gen.generateKey()
    }

    private fun encrypt(plain: String): ByteArray {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key())
        val iv = c.iv
        val ct = c.doFinal(plain.toByteArray(Charsets.UTF_8))
        return byteArrayOf(iv.size.toByte()) + iv + ct
    }

    private fun decrypt(data: ByteArray): String {
        val n = data[0].toInt()
        val iv = data.copyOfRange(1, 1 + n)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
        return String(c.doFinal(data.copyOfRange(1 + n, data.size)), Charsets.UTF_8)
    }

    /** Cifra un texto corto con la llave del teléfono (para guardar la contraseña del respaldo automático). */
    fun seal(plain: String): String = android.util.Base64.encodeToString(encrypt(plain), android.util.Base64.NO_WRAP)

    fun unseal(sealed: String): String? = try { decrypt(android.util.Base64.decode(sealed, android.util.Base64.NO_WRAP)) } catch (_: Exception) { null }

    /** Lee los datos (solo si el archivo cambió desde la última vez). */
    fun load(ctx: Context) {
        val f = file(ctx)
        val stamp = if (f.exists()) f.lastModified() + f.length() else 0L
        if (stamp == loadedStamp) return
        loadedStamp = stamp
        if (!f.exists()) { items = emptyList(); return }
        items = try {
            val arr = JSONArray(decrypt(Base64.decode(f.readText(), Base64.NO_WRAP)))
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                Entry(o.getLong("id"), o.getInt("k"), o.optString("l"), o.optString("v"), o.optString("s"))
            }
        } catch (_: Exception) { emptyList() }
    }

    private fun save(ctx: Context, list: List<Entry>) {
        val arr = JSONArray()
        for (e in list) arr.put(JSONObject().put("id", e.id).put("k", e.kind).put("l", e.label).put("v", e.value).put("s", e.shortcut))
        val f = file(ctx)
        val tmp = File(f.parentFile, "$FILE.tmp")
        tmp.writeText(Base64.encodeToString(encrypt(arr.toString()), Base64.NO_WRAP))
        tmp.renameTo(f)
        items = list
        loadedStamp = f.lastModified() + f.length()
    }

    fun add(ctx: Context, kind: Int, label: String, value: String, shortcut: String) {
        load(ctx)
        save(ctx, items + Entry(System.currentTimeMillis(), kind, label.trim(), value, shortcut.trim().lowercase()))
    }

    /** Cambia un dato que ya existe. */
    fun update(ctx: Context, id: Long, kind: Int, label: String, value: String, shortcut: String) {
        load(ctx)
        save(ctx, items.map { if (it.id == id) Entry(id, kind, label.trim(), value, shortcut.trim().lowercase()) else it })
    }

    /** Cambia todo por [list] (al restaurar una copia de seguridad). */
    fun replaceAll(ctx: Context, list: List<Entry>) {
        load(ctx)
        save(ctx, list)
    }

    fun remove(ctx: Context, id: Long) {
        load(ctx)
        save(ctx, items.filter { it.id != id })
    }

    /** Guarda solo un correo que escribiste (si todavía no está). */
    fun learnEmail(ctx: Context, email: String) {
        load(ctx)
        if (items.any { it.kind == EMAIL && it.value.equals(email, true) }) return
        save(ctx, items + Entry(System.currentTimeMillis(), EMAIL, email.substringAfter('@'), email, ""))
    }

    fun passwords() = items.filter { it.kind == PASSWORD }

    /**
     * Sugerencias para lo que estás escribiendo ([typed] = lo escrito desde el último espacio).
     * Atajo exacto (2+ letras) primero; luego lo que empieza igual (3+ letras, o 1+ en campos de correo).
     * Las contraseñas nunca se sugieren aquí.
     */
    fun match(typed: String, emailField: Boolean): List<Entry> {
        val t = typed.trim().lowercase()
        val pool = items.filter { it.kind != PASSWORD }
        if (t.isEmpty()) return if (emailField) pool.filter { it.kind == EMAIL }.take(3) else emptyList()
        val out = ArrayList<Entry>()
        if (t.length >= 2) out += pool.filter { it.shortcut.isNotEmpty() && it.shortcut == t }
        val min = if (emailField) 1 else 3
        if (t.length >= min) out += pool.filter {
            it !in out && it.value.length > t.length && it.value.lowercase().startsWith(t) && (!emailField || it.kind == EMAIL || it.value.contains('@'))
        }
        return out.take(3)
    }
}
