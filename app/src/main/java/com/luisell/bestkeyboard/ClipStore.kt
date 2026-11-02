package com.luisell.bestkeyboard

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.Executors

/** Una cosa copiada: un texto o una imagen. */
class ClipItem(
    val id: Long,
    val text: String?,
    val file: String?,   // nombre del archivo de la imagen (en files/clips)
    val mime: String?,
    var time: Long,
    var pinned: Boolean,
    val hash: String,
) {
    val isImage get() = file != null
}

/**
 * El portapapeles infinito: guarda TODO lo que copias (textos e imágenes) solo en tu teléfono.
 * Sin límite de cantidad; el único límite es el espacio del teléfono.
 */
object ClipStore {
    const val AUTHORITY = "com.luisell.bestkeyboard.clips"
    private const val MAX_TEXT = 200_000
    private const val MAX_IMAGE_BYTES = 30L * 1024 * 1024
    /** Lo que no está fijado se borra solo después de 2 días (48 horas). */
    const val KEEP_MS = 48L * 60 * 60 * 1000

    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    /** Más nuevo primero. */
    val items = ArrayList<ClipItem>()
    private var loaded = false
    private var loading = false
    private val waiting = ArrayList<() -> Unit>()
    private var dir: File? = null
    private var index: File? = null
    var onChange: (() -> Unit)? = null

    fun clipsDir(ctx: Context): File = File(ctx.applicationContext.filesDir, "clips").apply { mkdirs() }

    fun load(ctx: Context, then: (() -> Unit)? = null) {
        if (loaded) { then?.invoke(); return }
        if (then != null) waiting.add(then)
        if (loading) return
        loading = true
        val d = clipsDir(ctx)
        dir = d
        val idx = File(d, "historial.json")
        index = idx
        io.execute {
            val list = ArrayList<ClipItem>()
            try {
                if (idx.exists()) {
                    val arr = JSONArray(idx.readText(Charsets.UTF_8))
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        val file = if (o.has("file")) o.getString("file") else null
                        if (file != null && !File(d, file).exists()) continue
                        list.add(ClipItem(
                            o.getLong("id"),
                            if (o.has("text")) o.getString("text") else null,
                            file,
                            if (o.has("mime")) o.getString("mime") else null,
                            o.getLong("time"),
                            o.optBoolean("pinned", false),
                            o.optString("hash", ""),
                        ))
                    }
                }
            } catch (_: Exception) { }
            main.post {
                // Lo que se copió mientras se cargaba queda arriba
                val pending = ArrayList(items)
                items.clear()
                items.addAll(list)
                for (p in pending.asReversed()) insert(p)
                loaded = true
                loading = false
                val w = ArrayList(waiting)
                waiting.clear()
                w.forEach { it() }
                if (!purgeOld()) changed()
                if (pending.isNotEmpty()) save()
            }
        }
    }

    private fun sha1(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-1").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun newId(): Long {
        var id = System.currentTimeMillis()
        while (items.any { it.id == id }) id++
        return id
    }

    /** Pone el elemento arriba; si ya existía igual, lo mueve arriba (conserva si estaba fijado). */
    private fun insert(item: ClipItem) {
        val old = items.indexOfFirst { it.hash == item.hash }
        if (old >= 0) {
            val o = items.removeAt(old)
            o.time = item.time
            items.add(0, o)
            if (item.file != null && item.file != o.file) dir?.let { File(it, item.file).delete() }
            return
        }
        items.add(0, item)
    }

    fun addText(text: String) {
        if (text.isBlank()) return
        val t = if (text.length > MAX_TEXT) text.substring(0, MAX_TEXT) else text
        insert(ClipItem(newId(), t, null, null, System.currentTimeMillis(), false, "t:" + sha1(t.toByteArray())))
        changed()
        save()
    }

    /** Borra lo que tiene más de 48 horas (menos lo fijado 📌). Devuelve true si borró algo. */
    fun purgeOld(): Boolean {
        val limit = System.currentTimeMillis() - KEEP_MS
        val old = items.filter { !it.pinned && it.time < limit }
        if (old.isEmpty()) return false
        items.removeAll(old.toSet())
        val d = dir
        if (d != null) io.execute { for (o in old) o.file?.let { File(d, it).delete() } }
        changed()
        save()
        return true
    }

    /** Copia la imagen a la memoria del teclado (en segundo plano). [done] recibe el elemento guardado. */
    fun addImage(ctx: Context, uri: Uri, mimeIn: String?, done: ((ClipItem?) -> Unit)? = null) {
        val app = ctx.applicationContext
        val d = clipsDir(app)
        dir = d
        val id = newId()
        io.execute {
            try {
                val mime = mimeIn ?: app.contentResolver.getType(uri) ?: "image/png"
                val ext = when {
                    mime.contains("jpeg") || mime.contains("jpg") -> "jpg"
                    mime.contains("gif") -> "gif"
                    mime.contains("webp") -> "webp"
                    else -> "png"
                }
                val bytes = app.contentResolver.openInputStream(uri)?.use { input ->
                    val out = java.io.ByteArrayOutputStream()
                    val buf = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        total += n
                        if (total > MAX_IMAGE_BYTES) return@use null
                        out.write(buf, 0, n)
                    }
                    out.toByteArray()
                }
                if (bytes == null || bytes.isEmpty()) { main.post { done?.invoke(null) }; return@execute }
                val name = "img_$id.$ext"
                File(d, name).writeBytes(bytes)
                val item = ClipItem(id, null, name, mime, System.currentTimeMillis(), false, "i:" + sha1(bytes))
                main.post {
                    insert(item)
                    changed()
                    save()
                    // Si ya existía igual, se usa el que quedó arriba
                    done?.invoke(items.firstOrNull { it.hash == item.hash } ?: item)
                }
            } catch (_: Exception) { main.post { done?.invoke(null) } }
        }
    }

    /** Cambia el texto de algo copiado (lo editaste en el portapapeles). */
    fun editText(item: ClipItem, newText: String) {
        val t = newText.take(MAX_TEXT)
        if (t.isBlank()) { remove(item); return }
        val i = items.indexOfFirst { it.id == item.id }
        if (i < 0) return
        items[i] = ClipItem(item.id, t, null, null, item.time, item.pinned, "t:" + sha1(t.toByteArray()))
        changed()
        save()
    }

    /** Agregas algo a mano con el lápiz (se queda fijado para que no se borre). */
    fun addManual(text: String) {
        if (text.isBlank()) return
        val t = text.take(MAX_TEXT)
        val hash = "t:" + sha1(t.toByteArray())
        items.removeAll { it.hash == hash }
        items.add(0, ClipItem(newId(), t, null, null, System.currentTimeMillis(), true, hash))
        changed()
        save()
    }

    /** Fija o quita de fijados varios a la vez. */
    fun setPinned(list: Collection<ClipItem>, pinned: Boolean) {
        for (it in list) it.pinned = pinned
        changed()
        save()
    }

    fun removeAll(list: Collection<ClipItem>) {
        for (it in list.toList()) {
            items.remove(it)
            it.file?.let { f -> dir?.let { d -> io.execute { File(d, f).delete() } } }
        }
        changed()
        save()
    }

    fun togglePin(item: ClipItem) {
        item.pinned = !item.pinned
        changed()
        save()
    }

    fun remove(item: ClipItem) {
        items.remove(item)
        item.file?.let { f -> dir?.let { io.execute { File(it, f).delete() } } }
        changed()
        save()
    }

    /** Borra todo menos lo fijado. */
    fun clearUnpinned() {
        val gone = items.filter { !it.pinned }
        items.removeAll(gone.toSet())
        val d = dir
        if (d != null) io.execute { for (g in gone) g.file?.let { File(d, it).delete() } }
        changed()
        save()
    }

    /** Lo fijado primero, después lo más nuevo. */
    fun ordered(): List<ClipItem> = items.filter { it.pinned } + items.filter { !it.pinned }

    fun fileOf(ctx: Context, item: ClipItem): File? = item.file?.let { File(clipsDir(ctx), it) }

    fun uriOf(item: ClipItem): Uri? = item.file?.let { Uri.parse("content://$AUTHORITY/$it") }

    private fun changed() { onChange?.invoke() }

    // ---------------------------------------------------------------- Copia de seguridad

    /** Lo del portapapeles para la copia: textos (todos) e imágenes fijadas 📌 (hasta [maxImageBytes] en total). */
    fun exportForBackup(ctx: Context, maxImageBytes: Long = 6L * 1024 * 1024): JSONArray {
        val d = clipsDir(ctx)
        val arr = JSONArray()
        var used = 0L
        val src: List<ClipItem> = if (loaded) items.toList() else try {
            val a = JSONArray(File(d, "historial.json").readText(Charsets.UTF_8))
            (0 until a.length()).map { val o = a.getJSONObject(it)
                ClipItem(o.getLong("id"), if (o.has("text")) o.getString("text") else null, if (o.has("file")) o.getString("file") else null,
                    if (o.has("mime")) o.getString("mime") else null, o.getLong("time"), o.optBoolean("pinned", false), o.optString("hash", "")) }
        } catch (_: Exception) { emptyList() }
        for (it in src) {
            val o = JSONObject().put("id", it.id).put("time", it.time).put("pinned", it.pinned).put("hash", it.hash)
            if (it.text != null) o.put("text", it.text)
            if (it.file != null) {
                if (!it.pinned) continue
                val f = File(d, it.file)
                if (!f.exists() || used + f.length() > maxImageBytes) continue
                used += f.length()
                o.put("file", it.file).put("mime", it.mime ?: "image/png")
                o.put("data", android.util.Base64.encodeToString(f.readBytes(), android.util.Base64.NO_WRAP))
            }
            arr.put(o)
        }
        return arr
    }

    /** Pone el portapapeles de la copia (reemplaza el actual). */
    fun restoreFromBackup(ctx: Context, arr: JSONArray) {
        val d = clipsDir(ctx)
        dir = d
        index = File(d, "historial.json")
        val list = ArrayList<ClipItem>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            var file: String? = null
            if (o.has("file") && o.has("data")) {
                val name = o.getString("file")
                if (name.matches(Regex("[A-Za-z0-9._-]+")) && !name.startsWith(".")) {
                    try { File(d, name).writeBytes(android.util.Base64.decode(o.getString("data"), android.util.Base64.NO_WRAP)); file = name } catch (_: Exception) { }
                }
                if (file == null) continue
            }
            val text = if (o.has("text")) o.getString("text") else null
            if (text == null && file == null) continue
            list.add(ClipItem(o.optLong("id", System.currentTimeMillis() + i), text, file, if (o.has("mime")) o.getString("mime") else null,
                o.optLong("time", System.currentTimeMillis()), o.optBoolean("pinned", false), o.optString("hash", "")))
        }
        main.post {
            items.clear()
            items.addAll(list)
            loaded = true
            loading = false
            save()
            changed()
        }
    }

    private fun save() {
        val idx = index ?: return
        if (!loaded) return
        val arr = JSONArray()
        for (it in items) {
            val o = JSONObject()
            o.put("id", it.id)
            it.text?.let { t -> o.put("text", t) }
            it.file?.let { f -> o.put("file", f) }
            it.mime?.let { m -> o.put("mime", m) }
            o.put("time", it.time)
            o.put("pinned", it.pinned)
            o.put("hash", it.hash)
            arr.put(o)
        }
        val text = arr.toString()
        io.execute {
            try {
                val tmp = File(idx.parentFile, idx.name + ".tmp")
                tmp.writeText(text, Charsets.UTF_8)
                tmp.renameTo(idx)
            } catch (_: Exception) { }
        }
    }
}
