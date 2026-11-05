package com.luisell.bestkeyboard

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * 🔐 Una cuenta de «Datos» (como el administrador de contraseñas de Google):
 * nombre de la página o app, usuario, correo, contraseña, preguntas de seguridad, clave de seguridad y PIN.
 * Se abre después de tu huella. Con [EXTRA_EDIT] se abre para agregar o cambiar.
 */
class AccountActivity : UiActivity() {

    companion object {
        const val EXTRA_ID = "account_id"
        const val EXTRA_EDIT = "edit"
        /** Para agregar una cuenta con un dato ya puesto (lo que seleccionaste en el teclado). */
        const val EXTRA_PREFILL = "prefill"
        const val EXTRA_PREFILL_PASS = "prefill_pass"
    }

    private var id: Long = -1L
    private lateinit var body: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(android.R.style.Theme_Material_NoActionBar)
        super.onCreate(savedInstanceState)
        // Que no se puedan tomar capturas de tus contraseñas
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        prefs = Prefs.get(this)
        loadLang()
        window.statusBarColor = BG
        window.navigationBarColor = BG
        VaultStore.load(this)
        id = intent.getLongExtra(EXTRA_ID, -1L)
        val sv = ScrollView(this)
        PanelUi.glowScrollbar(sv)
        sv.setBackgroundColor(BG)
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(18), dp(18), dp(48)) }
        col = root
        sv.addView(root)
        body = root
        setContentView(sv)
        if (intent.getBooleanExtra(EXTRA_EDIT, false) || id < 0) showEdit() else showView()
    }

    private fun header(title: String, name: String) {
        val head = LinearLayout(this)
        head.orientation = LinearLayout.HORIZONTAL
        head.gravity = Gravity.CENTER_VERTICAL
        val back = IconView(this, Icons.BACK, 9f)
        back.color = TEXT
        back.setOnClickListener { finish() }
        head.addView(back, LinearLayout.LayoutParams(dp(44), dp(44)))
        head.addView(avatar(name, 40), LinearLayout.LayoutParams(dp(40), dp(40)).apply { leftMargin = dp(6) })
        val t = TextView(this)
        t.text = title
        t.textSize = 24f
        t.maxLines = 2
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD))
        t.setTextColor(TEXT)
        t.setPadding(dp(12), 0, 0, 0)
        head.addView(t, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(head)
    }

    /** El círculo de colores con la primera letra (como los íconos de Google). */
    fun avatar(name: String, sizeDp: Int): TextView = TextView(this).apply {
        text = name.trim().take(1).uppercase().ifEmpty { "?" }
        textSize = sizeDp * 0.42f
        gravity = Gravity.CENTER
        setTextColor(0xFF0B0D12.toInt())
        setTypeface(typeface, Typeface.BOLD)
        val h = (name.lowercase().hashCode() and 0x7fffffff) % 3
        val cols = listOf(intArrayOf(CYAN, VIOLET), intArrayOf(VIOLET, 0xFFF0ABFC.toInt()), intArrayOf(0xFF67E8F9.toInt(), 0xFFA78BFA.toInt()))
        background = GradientDrawable(GradientDrawable.Orientation.TL_BR, cols[h]).apply { shape = GradientDrawable.OVAL }
    }

    // ---------------------------------------------------------------- Ver

    private fun showView() {
        val a = VaultStore.account(id) ?: run { finish(); return }
        root.removeAllViews()
        col = root
        header(a.name, a.name)
        text(tr("Toca el ojito para ver y el botón de copiar para copiar. Lo copiado se borra solo del portapapeles en 1 minuto.",
            "Tap the eye to see and the copy button to copy. What you copy is cleared from the clipboard in 1 minute."), 12f, faded = true)
        card(tr("Cuenta", "Account"), Icons.LOCK)
        var any = false
        fun item(icon: Int, label: String, value: String, secret: Boolean) {
            if (value.isBlank()) return
            any = true
            col.addView(fieldRow(icon, label, value, secret), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        }
        item(Icons.PEOPLE, tr("Nombre de usuario", "Username"), a.user, false)
        item(Icons.MAIL, tr("Correo", "Email"), a.email, false)
        item(Icons.LOCK, tr("Contraseña", "Password"), a.pass, true)
        item(Icons.KEY, tr("Clave de seguridad", "Security key"), a.secKey, true)
        item(Icons.CALC, "PIN", a.pin, true)
        if (!any) text(tr("Todavía no tiene datos. Toca «Editar».", "No data yet. Tap «Edit»."), 13f, faded = true)
        if (a.questions.isNotEmpty()) {
            card(tr("Preguntas de seguridad", "Security questions"), Icons.BUBBLE)
            for ((q, ans) in a.questions) {
                if (q.isBlank() && ans.isBlank()) continue
                col.addView(fieldRow(Icons.BUBBLE, q.ifBlank { tr("Pregunta", "Question") }, ans, true), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
            }
        }
        if (a.note.isNotBlank()) {
            card(tr("Notas", "Notes"), Icons.NOTEBOOK)
            col.addView(fieldRow(Icons.NOTEBOOK, tr("Nota", "Note"), a.note, false), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        }
        button(tr("Editar", "Edit"), primary = true) { showEdit() }
        button(tr("Eliminar cuenta", "Delete account")) {
            AlertDialog.Builder(this)
                .setTitle(tr("¿Eliminar «${a.name}»?", "Delete «${a.name}»?"))
                .setMessage(tr("Se borra de tu teléfono con todos sus datos.", "It's deleted from your phone with all its data."))
                .setPositiveButton(tr("Eliminar", "Delete")) { _, _ -> VaultStore.remove(this, id); toast("Eliminada.", "Deleted."); finish() }
                .setNegativeButton(tr("Cancelar", "Cancel"), null).show()
        }
    }

    /** Una fila: ícono, nombre del dato, el dato (con puntitos si es secreto), ojito y copiar. */
    private fun fieldRow(icon: Int, label: String, value: String, secret: Boolean): View {
        val r = LinearLayout(this)
        r.orientation = LinearLayout.HORIZONTAL
        r.gravity = Gravity.CENTER_VERTICAL
        r.setPadding(dp(14), dp(12), dp(6), dp(12))
        r.background = GradientDrawable().apply { setColor(CARD2); cornerRadius = dpf(18f) }
        val ic = IconView(this, icon, 8f).apply { color = CYAN; bgColor = CARD2; isClickable = false }
        r.addView(ic, LinearLayout.LayoutParams(dp(26), dp(26)))
        val texts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), 0, dp(6), 0) }
        val l = TextView(this).apply { text = label; textSize = 12f; setTextColor(SUB) }
        val v = TextView(this).apply { textSize = 16f; setTextColor(TEXT); setTextIsSelectable(false) }
        var shown = !secret
        fun paint() { v.text = if (shown) value else "•".repeat(value.length.coerceIn(6, 14)) }
        paint()
        texts.addView(l); texts.addView(v)
        r.addView(texts, LinearLayout.LayoutParams(0, -2, 1f))
        if (secret) {
            val eye = IconView(this, Icons.EYE_OFF, 7.5f).apply { color = SUB; bgColor = CARD2 }
            eye.setOnClickListener { shown = !shown; paint(); eye.kind = if (shown) Icons.EYE else Icons.EYE_OFF; eye.color = if (shown) CYAN else SUB }
            r.addView(eye, LinearLayout.LayoutParams(dp(42), dp(42)))
        }
        val copy = IconView(this, Icons.CLIP, 7.5f).apply { color = SUB; bgColor = CARD2 }
        copy.setOnClickListener { copyValue(label, value, secret) }
        r.addView(copy, LinearLayout.LayoutParams(dp(42), dp(42)))
        return r
    }

    private fun copyValue(label: String, value: String, secret: Boolean) {
        try {
            val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText(label, value)
            if (secret && Build.VERSION.SDK_INT >= 24) clip.description.extras = android.os.PersistableBundle().apply {
                putBoolean(if (Build.VERSION.SDK_INT >= 33) ClipDescription.EXTRA_IS_SENSITIVE else "android.content.extra.IS_SENSITIVE", true)
            }
            cm.setPrimaryClip(clip)
            toast("Copiado: $label", "Copied: $label")
            // Se borra solo del portapapeles al minuto (por seguridad)
            if (secret) root.postDelayed({
                try {
                    val cur = cm.primaryClip?.getItemAt(0)?.text?.toString()
                    if (cur == value) { if (Build.VERSION.SDK_INT >= 28) cm.clearPrimaryClip() else cm.setPrimaryClip(ClipData.newPlainText("", "")) }
                } catch (_: Exception) { }
            }, 60_000L)
        } catch (_: Exception) { }
    }

    // ---------------------------------------------------------------- Agregar / editar

    private fun box(hint: String, value: String, type: Int = InputType.TYPE_CLASS_TEXT, multi: Boolean = false) = EditText(this).apply {
        this.hint = hint
        setText(value)
        textSize = 15f
        setTextColor(TEXT)
        setHintTextColor(SUB)
        inputType = type or (if (multi) InputType.TYPE_TEXT_FLAG_MULTI_LINE else 0)
        isSingleLine = !multi
        if (multi) minLines = 2
        setPadding(dp(14), dp(12), dp(14), dp(12))
        background = GradientDrawable().apply { setColor(CARD2); cornerRadius = dpf(14f); setStroke(dp(1), LINE) }
    }

    /** Caja con ojito (para contraseña, clave y PIN). */
    private fun secretBox(hint: String, value: String, numeric: Boolean = false): Pair<View, EditText> {
        val hidden = if (numeric) InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD else InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        val open = if (numeric) InputType.TYPE_CLASS_NUMBER else InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
        val e = box(hint, value)
        e.inputType = hidden
        e.setPadding(dp(14), dp(12), dp(50), dp(12))
        val f = FrameLayout(this)
        f.addView(e, FrameLayout.LayoutParams(-1, -2))
        val eye = IconView(this, Icons.EYE_OFF, 8f).apply { color = SUB; bgColor = CARD2 }
        var shown = false
        eye.setOnClickListener {
            shown = !shown
            val pos = e.selectionEnd
            e.inputType = if (shown) open else hidden
            e.setSelection(pos.coerceIn(0, e.text.length))
            eye.kind = if (shown) Icons.EYE else Icons.EYE_OFF
            eye.color = if (shown) CYAN else SUB
        }
        f.addView(eye, FrameLayout.LayoutParams(dp(48), dp(48), Gravity.END or Gravity.CENTER_VERTICAL))
        return f to e
    }

    private fun label(t: String) = text(t, 13f).apply { setPadding(dp(4), dp(14), 0, dp(6)) }

    private fun showEdit() {
        val old = if (id >= 0) VaultStore.account(id) else null
        root.removeAllViews()
        col = root
        header(if (old == null) tr("Agregar cuenta", "Add account") else tr("Editar", "Edit"), old?.name ?: "+")
        val pre = intent.getStringExtra(EXTRA_PREFILL)
        val preIsPass = intent.getBooleanExtra(EXTRA_PREFILL_PASS, false)
        val preEmail = pre != null && !preIsPass && pre.contains('@')

        label(tr("Página o app *", "Website or app *"))
        val name = box(tr("Ej.: Gmail, Banco, Netflix", "E.g.: Gmail, Bank, Netflix"), old?.name ?: "")
        col.addView(name, LinearLayout.LayoutParams(-1, -2))
        label(tr("Nombre de usuario", "Username"))
        val user = box(tr("Usuario", "Username"), old?.user ?: if (pre != null && !preIsPass && !preEmail) pre else "")
        col.addView(user, LinearLayout.LayoutParams(-1, -2))
        label(tr("Correo", "Email"))
        val email = box(tr("correo@ejemplo.com", "email@example.com"), old?.email ?: if (preEmail) pre!! else "",
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS)
        col.addView(email, LinearLayout.LayoutParams(-1, -2))
        label(tr("Contraseña", "Password"))
        val (passV, pass) = secretBox(tr("Contraseña", "Password"), old?.pass ?: if (preIsPass) pre ?: "" else "")
        col.addView(passV, LinearLayout.LayoutParams(-1, -2))
        val gen = TextView(this).apply {
            text = tr("Crear una contraseña segura", "Create a strong password")
            textSize = 13f; setTextColor(CYAN); setPadding(dp(6), dp(8), dp(6), dp(2))
            setOnClickListener {
                val chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#$%*?"
                val rnd = java.security.SecureRandom()
                pass.setText((1..16).map { chars[rnd.nextInt(chars.length)] }.joinToString(""))
                toast("Listo: contraseña nueva de 16 letras.", "Done: new 16-character password.")
            }
        }
        col.addView(gen)
        label(tr("Clave de seguridad", "Security key"))
        val (keyV, key) = secretBox(tr("Clave, código o token", "Key, code or token"), old?.secKey ?: "")
        col.addView(keyV, LinearLayout.LayoutParams(-1, -2))
        label("PIN")
        val (pinV, pin) = secretBox("PIN", old?.pin ?: "", numeric = true)
        col.addView(pinV, LinearLayout.LayoutParams(-1, -2))

        // Preguntas de seguridad (las que quieras)
        label(tr("Preguntas de seguridad", "Security questions"))
        val qBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(qBox, LinearLayout.LayoutParams(-1, -2))
        val qRows = ArrayList<Triple<View, EditText, EditText>>()
        fun addQ(q: String, ans: String) {
            val card = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(10), dp(10), dp(10), dp(10)) }
            card.background = GradientDrawable().apply { setColor(CARD); cornerRadius = dpf(16f); setStroke(dp(1), LINE) }
            val top = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
            val qe = box(tr("Pregunta (ej.: ¿Nombre de tu primera mascota?)", "Question (e.g.: First pet's name?)"), q)
            top.addView(qe, LinearLayout.LayoutParams(0, -2, 1f))
            val x = IconView(this, Icons.CLOSE, 6f).apply { color = SUB }
            top.addView(x, LinearLayout.LayoutParams(dp(40), dp(40)))
            card.addView(top)
            val (av, ae) = secretBox(tr("Respuesta", "Answer"), ans)
            card.addView(av, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
            val t = Triple<View, EditText, EditText>(card, qe, ae)
            x.setOnClickListener { qBox.removeView(card); qRows.remove(t) }
            qRows.add(t)
            qBox.addView(card, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        }
        for ((q, a) in old?.questions ?: emptyList()) addQ(q, a)
        button(tr("+  Agregar pregunta", "+  Add question")) { addQ("", "") }

        label(tr("Notas", "Notes"))
        val note = box(tr("Algo más que quieras recordar", "Anything else to remember"), old?.note ?: "", multi = true)
        col.addView(note, LinearLayout.LayoutParams(-1, -2))

        button(tr("Guardar", "Save"), primary = true) {
            val n = name.text.toString().trim()
            if (n.isEmpty()) { toast("Ponle el nombre de la página o app.", "Add the website or app name."); name.requestFocus(); return@button }
            val acc = VaultStore.Account(n, user.text.toString().trim(), email.text.toString().trim(), pass.text.toString(),
                qRows.map { it.second.text.toString().trim() to it.third.text.toString() }.filter { it.first.isNotBlank() || it.second.isNotBlank() },
                key.text.toString(), pin.text.toString(), note.text.toString().trim())
            try {
                id = VaultStore.saveAccount(this, if (id >= 0) id else null, acc)
                toast("Guardado.", "Saved.")
                showView()
            } catch (_: Exception) { toast("No se pudo guardar.", "Couldn't save.") }
        }
        button(tr("Cancelar", "Cancel")) { if (id >= 0) showView() else finish() }
    }
}
