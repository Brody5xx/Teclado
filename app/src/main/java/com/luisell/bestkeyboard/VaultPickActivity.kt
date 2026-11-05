package com.luisell.bestkeyboard

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.Locale

/**
 * Ventanita de "Mis datos" desde el teclado.
 *  - Normal (botón 🔒 Mis datos): primero pide tu huella/PIN y luego muestra TODO (correos, contraseñas, textos).
 *  - En un campo de contraseña: muestra tus contraseñas y pide la huella antes de escribir la elegida.
 * Lo que eliges se escribe en la app donde estabas.
 */
class VaultPickActivity : Activity() {

    companion object {
        /** true = mostrar todo (pide la huella al abrir). */
        const val EXTRA_ALL = "todo"
    }

    private var chosen: String? = null
    private var all = false
    private val en get() = Locale.getDefault().language != "es"
    private fun tr(es: String, en: String) = if (this.en) en else es
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(android.R.style.Theme_Material_Dialog_NoActionBar)
        super.onCreate(savedInstanceState)
        window?.setBackgroundDrawableResource(android.R.color.transparent)
        VaultStore.load(this)
        try { VaultStore.migrate(this) } catch (_: Exception) { }
        all = intent.getBooleanExtra(EXTRA_ALL, false)
        // 🔐 Igual que «Datos» en los ajustes: la lista de cuentas con su flecha; al tocar una, tu huella y sus datos
        showAccounts()
    }

    private fun frame(titleText: String, subText: String, onBack: (() -> Unit)?): Pair<LinearLayout, LinearLayout> {
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(dp(20), dp(20), dp(20), dp(12))
        root.background = GradientDrawable().apply { setColor(MainActivity.CARD); cornerRadius = dp(24).toFloat() }
        val head = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        if (onBack != null) {
            val back = IconView(this, Icons.BACK, 8f).apply { color = MainActivity.TEXT; bgColor = MainActivity.CARD }
            back.setOnClickListener { onBack() }
            head.addView(back, LinearLayout.LayoutParams(dp(40), dp(40)))
        }
        val title = TextView(this)
        title.text = titleText
        title.textSize = 20f
        title.maxLines = 1
        title.ellipsize = android.text.TextUtils.TruncateAt.END
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD))
        title.setTextColor(MainActivity.TEXT)
        title.setPadding(if (onBack != null) dp(8) else 0, 0, 0, 0)
        head.addView(title, LinearLayout.LayoutParams(0, -2, 1f))
        head.addView(DiamondView(this), LinearLayout.LayoutParams(dp(24), dp(24)))
        root.addView(head)
        val sub = TextView(this)
        sub.text = subText
        sub.textSize = 13f
        sub.setTextColor(MainActivity.SUB)
        sub.setPadding(0, dp(4), 0, dp(10))
        root.addView(sub)
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        val sv = object : ScrollView(this) {
            override fun onMeasure(w: Int, h: Int) {
                super.onMeasure(w, MeasureSpec.makeMeasureSpec((resources.displayMetrics.heightPixels * 0.6f).toInt(), MeasureSpec.AT_MOST))
            }
        }
        PanelUi.glowScrollbar(sv)
        sv.addView(col)
        root.addView(sv, LinearLayout.LayoutParams(-1, -2))
        val cancel = TextView(this)
        cancel.text = tr("Cerrar", "Close")
        cancel.textSize = 15f
        cancel.setTextColor(MainActivity.CYAN)
        cancel.gravity = Gravity.CENTER
        cancel.setPadding(0, dp(14), 0, dp(6))
        cancel.setOnClickListener { finish() }
        root.addView(cancel, LinearLayout.LayoutParams(-1, -2))
        setContentView(root)
        return root to col
    }

    private fun avatar(name: String): TextView = TextView(this).apply {
        text = name.trim().take(1).uppercase().ifEmpty { "?" }
        textSize = 15f; gravity = Gravity.CENTER; setTextColor(0xFF0B0D12.toInt()); setTypeface(typeface, Typeface.BOLD)
        val h = (name.lowercase().hashCode() and 0x7fffffff) % 3
        val c = MainActivity.CYAN; val v = MainActivity.VIOLET
        val cols = listOf(intArrayOf(c, v), intArrayOf(v, 0xFFF0ABFC.toInt()), intArrayOf(0xFF67E8F9.toInt(), 0xFFA78BFA.toInt()))
        background = GradientDrawable(GradientDrawable.Orientation.TL_BR, cols[h]).apply { shape = GradientDrawable.OVAL }
    }

    /** La lista: tus cuentas (con la flecha ›) y abajo tus textos sueltos. */
    private fun showAccounts() {
        val accs = VaultStore.accounts()
        val loose = VaultStore.items.filter { it.kind != VaultStore.ACCOUNT && (all || it.kind == VaultStore.PASSWORD) }
        val (_, col) = frame(tr("Datos", "Data"),
            if (accs.isEmpty() && loose.isEmpty()) tr("Todavía no guardaste nada. Agrégalo en la app Best Keyboard → «Datos».", "Nothing saved yet. Add it in the Best Keyboard app → «Data».")
            else tr("Toca una cuenta (te pide tu huella) y luego el dato que quieres escribir.", "Tap an account (it asks for your fingerprint) and then the item you want to type."), null)
        for ((aid, a) in accs) {
            val r = LinearLayout(this)
            r.orientation = LinearLayout.HORIZONTAL
            r.gravity = Gravity.CENTER_VERTICAL
            r.setPadding(dp(12), dp(10), dp(6), dp(10))
            r.background = android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x334FD1FF),
                GradientDrawable().apply { setColor(MainActivity.CARD2); cornerRadius = dp(18).toFloat() }, null)
            r.addView(avatar(a.name), LinearLayout.LayoutParams(dp(36), dp(36)))
            val texts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), 0, dp(6), 0) }
            texts.addView(TextView(this).apply { text = a.name; textSize = 16f; setTextColor(MainActivity.TEXT); maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END })
            val sub = a.user.ifBlank { a.email }
            if (sub.isNotBlank()) texts.addView(TextView(this).apply { text = sub; textSize = 12f; setTextColor(MainActivity.SUB); maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END })
            r.addView(texts, LinearLayout.LayoutParams(0, -2, 1f))
            r.addView(IconView(this, Icons.CHEVRON, 6.5f).apply { color = MainActivity.SUB; bgColor = MainActivity.CARD2; isClickable = false }, LinearLayout.LayoutParams(dp(30), dp(30)))
            r.setOnClickListener {
                if (System.currentTimeMillis() < DataActivity.unlockedUntil) showAccount(aid)
                else authenticate(onOk = { DataActivity.unlockedUntil = System.currentTimeMillis() + 60_000L; showAccount(aid) }, onFail = { })
            }
            col.addView(r, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        }
        if (loose.isNotEmpty()) {
            col.addView(TextView(this).apply {
                text = tr("Textos y atajos", "Texts and shortcuts"); textSize = 12f; setTextColor(MainActivity.SUB)
                setPadding(dp(4), dp(16), 0, dp(2))
            })
            for (e in loose) col.addView(row(e, unlocked = false), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        }
    }

    /** Una cuenta: cada dato con su ícono; un toque lo escribe donde estabas. Los secretos con puntitos y ojito. */
    private fun showAccount(id: Long) {
        val a = VaultStore.account(id) ?: return
        window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        val (_, col) = frame(a.name, tr("Toca un dato y se escribe donde estabas.", "Tap an item and it's typed where you were.")) { showAccounts() }
        fun add(icon: Int, label: String, value: String, secret: Boolean) {
            if (value.isBlank()) return
            col.addView(itemRow(icon, label, value, secret), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        }
        add(Icons.PEOPLE, tr("Nombre de usuario", "Username"), a.user, false)
        add(Icons.MAIL, tr("Correo", "Email"), a.email, false)
        add(Icons.LOCK, tr("Contraseña", "Password"), a.pass, true)
        add(Icons.KEY, tr("Clave de seguridad", "Security key"), a.secKey, true)
        add(Icons.CALC, "PIN", a.pin, true)
        for ((q, ans) in a.questions) add(Icons.BUBBLE, q.ifBlank { tr("Pregunta", "Question") }, ans, true)
    }

    private fun itemRow(icon: Int, label: String, value: String, secret: Boolean): View {
        val r = LinearLayout(this)
        r.orientation = LinearLayout.HORIZONTAL
        r.gravity = Gravity.CENTER_VERTICAL
        r.setPadding(dp(14), dp(10), dp(6), dp(10))
        r.background = android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x334FD1FF),
            GradientDrawable().apply { setColor(MainActivity.CARD2); cornerRadius = dp(18).toFloat() }, null)
        r.addView(IconView(this, icon, 8f).apply { color = MainActivity.CYAN; bgColor = MainActivity.CARD2; isClickable = false }, LinearLayout.LayoutParams(dp(26), dp(26)))
        val texts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), 0, dp(6), 0) }
        texts.addView(TextView(this).apply { text = label; textSize = 12f; setTextColor(MainActivity.SUB) })
        val v = TextView(this).apply { textSize = 15f; setTextColor(MainActivity.TEXT); maxLines = 2 }
        var shown = !secret
        fun paint() { v.text = if (shown) value else "•".repeat(value.length.coerceIn(6, 14)) }
        paint()
        texts.addView(v)
        r.addView(texts, LinearLayout.LayoutParams(0, -2, 1f))
        if (secret) {
            val eye = IconView(this, Icons.EYE_OFF, 8f).apply { color = MainActivity.SUB; bgColor = MainActivity.CARD2 }
            eye.setOnClickListener { shown = !shown; paint(); eye.kind = if (shown) Icons.EYE else Icons.EYE_OFF; eye.color = if (shown) MainActivity.CYAN else MainActivity.SUB }
            r.addView(eye, LinearLayout.LayoutParams(dp(44), dp(44)))
        }
        r.setOnClickListener { chosen = value; done() }
        return r
    }

    private fun showList(list: List<VaultStore.Entry>, unlocked: Boolean) {
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(dp(20), dp(20), dp(20), dp(12))
        root.background = GradientDrawable().apply { setColor(MainActivity.CARD); cornerRadius = dp(24).toFloat() }
        val title = TextView(this)
        title.text = if (all) tr("Datos", "Data") else tr("Mis contraseñas", "My passwords")
        title.textSize = 20f
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD))
        title.setTextColor(MainActivity.TEXT)
        root.addView(title)
        val sub = TextView(this)
        sub.text = when {
            list.isEmpty() -> tr("Todavía no guardaste nada. Agrégalo en la app Best Keyboard → «Datos».",
                "Nothing saved yet. Add it in the Best Keyboard app → «Data».")
            unlocked -> tr("Toca uno y se escribe donde estabas.", "Tap one and it's typed where you were.")
            else -> tr("Elige una. Se pide tu huella o PIN antes de escribirla.", "Pick one. Your fingerprint or PIN is asked before typing it.")
        }
        sub.textSize = 13f
        sub.setTextColor(MainActivity.SUB)
        sub.setPadding(0, dp(4), 0, dp(10))
        root.addView(sub)

        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        for (e in list) col.addView(row(e, unlocked), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        val sv = object : ScrollView(this) {
            override fun onMeasure(w: Int, h: Int) {
                // Que la ventanita no pase de ~60% de la pantalla
                super.onMeasure(w, MeasureSpec.makeMeasureSpec((resources.displayMetrics.heightPixels * 0.6f).toInt(), MeasureSpec.AT_MOST))
            }
        }
        PanelUi.glowScrollbar(sv)
        sv.addView(col)
        root.addView(sv, LinearLayout.LayoutParams(-1, -2))

        val cancel = TextView(this)
        cancel.text = tr("Cerrar", "Close")
        cancel.textSize = 15f
        cancel.setTextColor(MainActivity.CYAN)
        cancel.gravity = Gravity.CENTER
        cancel.setPadding(0, dp(14), 0, dp(6))
        cancel.setOnClickListener { finish() }
        root.addView(cancel, LinearLayout.LayoutParams(-1, -2))
        setContentView(root)
    }

    /** Una fila: ícono, nombre/valor (contraseña con puntitos y ojito) y un toque la escribe. */
    private fun row(e: VaultStore.Entry, unlocked: Boolean): View {
        val r = LinearLayout(this)
        r.orientation = LinearLayout.HORIZONTAL
        r.gravity = Gravity.CENTER_VERTICAL
        r.setPadding(dp(14), dp(10), dp(6), dp(10))
        r.background = android.graphics.drawable.RippleDrawable(
            android.content.res.ColorStateList.valueOf(0x334FD1FF),
            GradientDrawable().apply { setColor(MainActivity.CARD2); cornerRadius = dp(18).toFloat() }, null,
        )
        val icon = IconView(this, when (e.kind) { VaultStore.EMAIL -> Icons.MAIL; VaultStore.PASSWORD -> Icons.LOCK; else -> Icons.CLIP }, 8f)
        icon.isClickable = false
        icon.color = MainActivity.CYAN
        icon.bgColor = MainActivity.CARD2
        r.addView(icon, LinearLayout.LayoutParams(dp(26), dp(26)))
        val texts = LinearLayout(this)
        texts.orientation = LinearLayout.VERTICAL
        texts.setPadding(dp(12), 0, dp(6), 0)
        val a = TextView(this)
        val b = TextView(this)
        val isPass = e.kind == VaultStore.PASSWORD
        a.text = if (isPass) e.label.ifBlank { tr("Contraseña", "Password") } else e.value
        a.textSize = 15f
        a.setTextColor(MainActivity.TEXT)
        a.maxLines = 2
        texts.addView(a)
        if (isPass && unlocked) {
            b.text = "••••••••"
            b.textSize = 13f
            b.setTextColor(MainActivity.SUB)
            texts.addView(b)
        } else if (!isPass && e.label.isNotBlank() && e.kind == VaultStore.TEXT) {
            b.text = e.label
            b.textSize = 12f
            b.setTextColor(MainActivity.SUB)
            texts.addView(b)
        }
        r.addView(texts, LinearLayout.LayoutParams(0, -2, 1f))
        if (isPass && unlocked) {
            // Ojito para ver la contraseña (ya pusiste tu huella)
            val eye = IconView(this, Icons.EYE_OFF, 8f)
            eye.color = MainActivity.SUB
            eye.bgColor = MainActivity.CARD2
            var shown = false
            eye.setOnClickListener {
                shown = !shown
                b.text = if (shown) e.value else "••••••••"
                eye.kind = if (shown) Icons.EYE else Icons.EYE_OFF
                eye.color = if (shown) MainActivity.CYAN else MainActivity.SUB
            }
            r.addView(eye, LinearLayout.LayoutParams(dp(44), dp(44)))
        }
        r.setOnClickListener {
            chosen = e.value
            if (unlocked) done() else authenticate(onOk = { done() }, onFail = { chosen = null })
        }
        return r
    }

    // ---------------------------------------------------------------- Huella / PIN

    private var onAuthOk: (() -> Unit)? = null
    private var onAuthFail: (() -> Unit)? = null

    /** Pide la huella o el PIN del teléfono. */
    private fun authenticate(onOk: () -> Unit, onFail: () -> Unit) {
        val km = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        if (!km.isDeviceSecure) { onOk(); return } // el teléfono no tiene bloqueo: no hay nada que pedir
        onAuthOk = onOk
        onAuthFail = onFail
        val title = tr("Confirma que eres tú", "Confirm it's you")
        val cb = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) { onOk() }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) { onFail() }
        }
        try {
            when {
                Build.VERSION.SDK_INT >= 30 -> BiometricPrompt.Builder(this)
                    .setTitle(title)
                    .setSubtitle(tr("Para ver «Datos»", "To see «Data»"))
                    .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                    .build().authenticate(CancellationSignal(), mainExecutor, cb)
                Build.VERSION.SDK_INT == 29 -> @Suppress("DEPRECATION") BiometricPrompt.Builder(this)
                    .setTitle(title)
                    .setDeviceCredentialAllowed(true)
                    .build().authenticate(CancellationSignal(), mainExecutor, cb)
                else -> @Suppress("DEPRECATION") startActivityForResult(km.createConfirmDeviceCredentialIntent(title, null), 1)
            }
        } catch (_: Exception) {
            @Suppress("DEPRECATION")
            startActivityForResult(km.createConfirmDeviceCredentialIntent(title, null), 1)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != 1) return
        if (resultCode == RESULT_OK) onAuthOk?.invoke() else onAuthFail?.invoke()
    }

    private fun done() {
        VaultStore.pendingInsert = chosen
        finish()
    }
}
