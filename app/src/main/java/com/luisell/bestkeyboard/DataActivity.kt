package com.luisell.bestkeyboard

import android.app.AlertDialog
import android.app.KeyguardManager
import android.content.Context
import android.graphics.LinearGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * "Datos" (como Samsung Pass): tus correos, contraseñas y textos guardados, cifrados en el teléfono.
 * Toca uno para cambiarlo (las contraseñas piden tu huella), ✕ para borrarlo, o «Agregar».
 * Desde el teclado: selecciona un texto y toca «Guardar en Datos» para llegar aquí con el texto listo.
 */
class DataActivity : UiActivity() {

    companion object {
        /** Texto que seleccionaste en el teclado para guardarlo. */
        const val EXTRA_TEXT = "texto"
        /** El texto venía de un campo de contraseña. */
        const val EXTRA_PASSWORD = "es_contrasena"
    }

    private lateinit var vaultList: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(android.R.style.Theme_Material_NoActionBar)
        super.onCreate(savedInstanceState)
        prefs = Prefs.get(this)
        loadLang()
        window.statusBarColor = BG
        window.navigationBarColor = BG

        val sv = ScrollView(this)

        PanelUi.glowScrollbar(sv)
        sv.setBackgroundColor(BG)
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(48))
        }
        col = root
        sv.addView(root)

        val head = LinearLayout(this)
        head.orientation = LinearLayout.HORIZONTAL
        head.gravity = Gravity.CENTER_VERTICAL
        val back = IconView(this, Icons.BACK, 9f)
        back.color = TEXT
        back.setOnClickListener { finish() }
        head.addView(back, LinearLayout.LayoutParams(dp(44), dp(44)))
        val title = TextView(this)
        title.text = tr("Datos", "Data")
        title.textSize = 28f
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD))
        title.setTextColor(TEXT)
        title.setPadding(dp(6), 0, 0, 0)
        title.post {
            title.paint.shader = LinearGradient(0f, 0f, title.width.toFloat(), 0f, CYAN, VIOLET, Shader.TileMode.CLAMP)
            title.invalidate()
        }
        head.addView(title)
        root.addView(head)

        text(tr(
            "Tus correos, contraseñas y textos. Al escribir 2–3 letras (o el atajo) el teclado te los sugiere. " +
                "Toca uno para cambiarlo. Todo se guarda cifrado y solo en tu teléfono.",
            "Your emails, passwords and texts. Type 2–3 letters (or the shortcut) and the keyboard suggests them. " +
                "Tap one to change it. Everything is encrypted and stays only on your phone.",
        ), 13f, faded = true)
        button(tr("+  Agregar", "+  Add"), primary = true) { editDialog(null, null) }

        card(tr("Guardados", "Saved"), Icons.LOCK)
        vaultList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(vaultList, LinearLayout.LayoutParams(-1, -2))

        card(tr("Opciones", "Options"), Icons.SETTINGS)
        switch(tr("Sugerir mis datos al escribir", "Suggest my data while typing"), Prefs.VAULT_SUGGEST, true)
        switch(tr("Guardar los correos que escribo", "Save the emails I type"), Prefs.VAULT_LEARN, true,
            tr("Cuando escribes tu correo en un campo de correo, se guarda solo.", "When you type your email in an email field, it's saved automatically."))
        text(tr("En el teclado: selecciona un texto (correo, contraseña o lo que sea) y arriba sale «Guardar en Datos».",
            "On the keyboard: select some text (email, password or anything) and «Save to Data» shows on top."), 12f, faded = true)
        setContentView(sv)

        refreshVault()
        // Llegaste desde el teclado con un texto seleccionado: se abre "Agregar" con el texto listo
        intent.getStringExtra(EXTRA_TEXT)?.takeIf { it.isNotBlank() }?.let {
            editDialog(null, it.trim(), intent.getBooleanExtra(EXTRA_PASSWORD, false))
        }
    }

    override fun onResume() {
        super.onResume()
        refreshVault()
    }

    /** Dibuja la lista (las contraseñas se ven con puntitos). Toca una fila para cambiarla. */
    private fun refreshVault() {
        if (!::vaultList.isInitialized) return
        try { VaultStore.load(this) } catch (_: Exception) { }
        vaultList.removeAllViews()
        if (VaultStore.items.isEmpty()) {
            val t = TextView(this)
            t.text = tr("Todavía no guardaste nada.", "Nothing saved yet.")
            t.textSize = 13f
            t.setTextColor(SUB)
            t.setPadding(0, dp(12), 0, dp(6))
            vaultList.addView(t)
            return
        }
        for (e in VaultStore.items.sortedBy { it.kind }) {
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL
            row.setPadding(0, dp(10), 0, dp(10))
            row.isClickable = true
            val iv = IconView(this, when (e.kind) { VaultStore.EMAIL -> Icons.MAIL; VaultStore.PASSWORD -> Icons.LOCK; else -> Icons.CLIP }, 8f)
            iv.isClickable = false
            iv.color = CYAN
            iv.bgColor = CARD
            row.addView(iv, LinearLayout.LayoutParams(dp(26), dp(26)))
            val texts = LinearLayout(this)
            texts.orientation = LinearLayout.VERTICAL
            texts.setPadding(dp(12), 0, dp(8), 0)
            val a = TextView(this)
            a.text = if (e.kind == VaultStore.PASSWORD) e.label.ifBlank { tr("Contraseña", "Password") } else e.value
            a.textSize = 15f
            a.setTextColor(TEXT)
            a.maxLines = 1
            a.ellipsize = android.text.TextUtils.TruncateAt.END
            texts.addView(a)
            val b = TextView(this)
            val extra = ArrayList<String>()
            if (e.kind == VaultStore.PASSWORD) extra += "••••••••" else if (e.label.isNotBlank() && e.kind == VaultStore.TEXT) extra += e.label
            if (e.shortcut.isNotBlank()) extra += tr("atajo: ", "shortcut: ") + e.shortcut
            b.text = extra.joinToString("  ·  ")
            b.textSize = 12f
            b.setTextColor(SUB)
            if (extra.isNotEmpty()) texts.addView(b)
            row.addView(texts, LinearLayout.LayoutParams(0, -2, 1f))
            val edit = IconView(this, Icons.PENCIL, 6.5f)
            edit.isClickable = false
            edit.color = SUB
            row.addView(edit, LinearLayout.LayoutParams(dp(32), dp(36)))
            val del = IconView(this, Icons.CLOSE, 6f)
            del.color = SUB
            del.setOnClickListener {
                AlertDialog.Builder(this)
                    .setTitle(tr("¿Borrar este dato?", "Delete this item?"))
                    .setMessage(tr("Se borrará de tu teléfono.", "It will be deleted from your phone."))
                    .setPositiveButton(tr("Borrar", "Delete")) { _, _ -> VaultStore.remove(this, e.id); refreshVault() }
                    .setNegativeButton(tr("Cancelar", "Cancel"), null)
                    .show()
            }
            row.addView(del, LinearLayout.LayoutParams(dp(36), dp(36)))
            // Tocar la fila = cambiarla (las contraseñas piden la huella primero)
            row.setOnClickListener {
                if (e.kind == VaultStore.PASSWORD) authenticate { editDialog(e, null) } else editDialog(e, null)
            }
            if (vaultList.childCount > 0) vaultList.addView(View(this).apply { setBackgroundColor(LINE) }, LinearLayout.LayoutParams(-1, dp(1)))
            vaultList.addView(row, LinearLayout.LayoutParams(-1, -2))
        }
    }

    /** Pide la huella o el PIN del teléfono (para ver o cambiar una contraseña). */
    private fun authenticate(onOk: () -> Unit) {
        val km = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        if (!km.isDeviceSecure || Build.VERSION.SDK_INT < 29) { onOk(); return }
        val cb = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) { onOk() }
        }
        try {
            val b = BiometricPrompt.Builder(this).setTitle(tr("Confirma que eres tú", "Confirm it's you"))
            if (Build.VERSION.SDK_INT >= 30) {
                b.setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
            } else {
                @Suppress("DEPRECATION")
                b.setDeviceCredentialAllowed(true)
            }
            b.build().authenticate(CancellationSignal(), mainExecutor, cb)
        } catch (_: Exception) { }
    }

    /**
     * Agregar ([old] = null) o cambiar un dato. [prefill] = texto que seleccionaste en el teclado
     * (si parece correo, se elige "Correo" solo).
     */
    private fun editDialog(old: VaultStore.Entry?, prefill: String?, asPassword: Boolean = false) {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(20), dp(8), dp(20), dp(4))
        val looksEmail = prefill != null && prefill.matches(Regex("[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}"))
        var kind = old?.kind ?: when {
            asPassword -> VaultStore.PASSWORD
            prefill != null && !looksEmail -> VaultStore.TEXT
            else -> VaultStore.EMAIL
        }
        val tabs = LinearLayout(this)
        tabs.orientation = LinearLayout.HORIZONTAL
        tabs.background = GradientDrawable().apply { setColor(BG); cornerRadius = dpf(22f) }
        tabs.setPadding(dp(4), dp(4), dp(4), dp(4))
        fun field(hint: String) = EditText(this).apply {
            this.hint = hint
            isSingleLine = true
            textSize = 15f
            setTextColor(TEXT)
            setHintTextColor(SUB)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = GradientDrawable().apply { setColor(BG); cornerRadius = dpf(14f); setStroke(dp(1), LINE) }
        }
        val name = field(tr("Nombre (ej.: Banco, Trabajo)", "Name (e.g.: Bank, Work)"))
        val value = field(tr("Correo", "Email"))
        val shortcut = field(tr("Atajo opcional (ej.: lp)", "Optional shortcut (e.g.: lp)"))
        name.setText(old?.label ?: "")
        value.setText(old?.value ?: prefill ?: "")
        shortcut.setText(old?.shortcut ?: "")
        val opts = listOf(
            VaultStore.EMAIL to tr("Correo", "Email"),
            VaultStore.PASSWORD to tr("Contraseña", "Password"),
            VaultStore.TEXT to tr("Texto / atajo", "Text / shortcut"),
        )
        val tabViews = ArrayList<TextView>()
        var eyeView: IconView? = null
        var shown = false
        fun paint() {
            for ((i, tv) in tabViews.withIndex()) {
                val on = opts[i].first == kind
                tv.setTextColor(if (on) 0xFF0B0D12.toInt() else SUB)
                tv.background = if (on) GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(CYAN, VIOLET)).apply { cornerRadius = dpf(18f) } else null
            }
            value.hint = when (kind) {
                VaultStore.EMAIL -> tr("Correo", "Email")
                VaultStore.PASSWORD -> tr("Contraseña", "Password")
                else -> tr("Texto (dirección, teléfono, firma…)", "Text (address, phone, signature…)")
            }
            value.inputType = android.text.InputType.TYPE_CLASS_TEXT or when (kind) {
                VaultStore.PASSWORD -> android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
                VaultStore.EMAIL -> android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
                else -> 0
            }
            shortcut.visibility = if (kind == VaultStore.PASSWORD) View.GONE else View.VISIBLE
            shown = false
            eyeView?.let { e ->
                e.visibility = if (kind == VaultStore.PASSWORD) View.VISIBLE else View.GONE
                e.kind = Icons.EYE_OFF
                e.color = SUB
            }
            value.setPadding(dp(14), dp(12), if (kind == VaultStore.PASSWORD) dp(50) else dp(14), dp(12))
        }
        for ((k, label) in opts) {
            val t = TextView(this)
            t.text = label
            t.textSize = 13f
            t.gravity = Gravity.CENTER
            t.setPadding(dp(4), dp(9), dp(4), dp(9))
            t.setOnClickListener { kind = k; paint() }
            tabViews.add(t)
            tabs.addView(t, LinearLayout.LayoutParams(0, -2, 1f))
        }
        box.addView(tabs, LinearLayout.LayoutParams(-1, -2))
        // La caja del valor con el ojito a la derecha (solo para contraseñas)
        val valueBox = android.widget.FrameLayout(this)
        valueBox.addView(value, android.widget.FrameLayout.LayoutParams(-1, -2))
        val eye = IconView(this, Icons.EYE_OFF, 8f)
        eye.color = SUB
        eye.bgColor = BG
        eye.setOnClickListener {
            shown = !shown
            val pos = value.selectionEnd
            value.inputType = android.text.InputType.TYPE_CLASS_TEXT or
                if (shown) android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD else android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            value.setSelection(pos.coerceIn(0, value.text.length))
            eye.kind = if (shown) Icons.EYE else Icons.EYE_OFF
            eye.color = if (shown) CYAN else SUB
        }
        valueBox.addView(eye, android.widget.FrameLayout.LayoutParams(dp(48), dp(48), Gravity.END or Gravity.CENTER_VERTICAL))
        box.addView(name, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
        box.addView(valueBox, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
        box.addView(shortcut, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
        eyeView = eye
        paint()
        AlertDialog.Builder(this)
            .setTitle(if (old == null) tr("Agregar a Datos", "Add to Data") else tr("Cambiar dato", "Edit item"))
            .setView(box)
            .setPositiveButton(tr("Guardar", "Save")) { _, _ ->
                val v = value.text.toString()
                if (v.isBlank()) return@setPositiveButton
                try {
                    val clean = if (kind == VaultStore.PASSWORD) v else v.trim()
                    val sc = if (kind == VaultStore.PASSWORD) "" else shortcut.text.toString()
                    if (old != null) VaultStore.update(this, old.id, kind, name.text.toString(), clean, sc)
                    else VaultStore.add(this, kind, name.text.toString(), clean, sc)
                    toast("Guardado.", "Saved.")
                } catch (_: Exception) {
                    toast("No se pudo guardar.", "Couldn't save.")
                }
                refreshVault()
            }
            .setNegativeButton(tr("Cancelar", "Cancel"), null)
            .show()
    }
}
