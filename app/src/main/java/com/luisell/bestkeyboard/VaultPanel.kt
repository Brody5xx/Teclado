package com.luisell.bestkeyboard

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * 🔐 «Datos» dentro del teclado: tus cuentas (con su flecha ›) y tus textos guardados. Tocas una cuenta,
 * ves sus datos (usuario, correo, contraseña…) y un toque escribe el dato donde estás.
 * Los secretos salen con puntitos y su ojito. Se abre solo después de tu huella o PIN, y se cierra
 * (bloqueado) apenas sales o escondes el teclado.
 */
@SuppressLint("ViewConstructor")
class VaultPanel(context: Context) : FrameLayout(context) {

    interface Actions {
        fun onVaultText(text: String)
        fun onVaultBack()
        fun onVaultFeedback()
    }

    var actions: Actions? = null
    private var palette = Palette.DARK
    private fun d(v: Float) = PanelUi.dp(context, v)

    private val back = IconView(context, Icons.BACK, 8f)
    private val title = TextView(context)
    private val sub = TextView(context)
    private val list = LinearLayout(context)
    private val sv = ScrollView(context)
    /** null = la lista de cuentas; si no, la cuenta abierta. */
    private var openId: Long? = null
    private var all = true

    init {
        val body = LinearLayout(context)
        body.orientation = LinearLayout.VERTICAL
        body.setPadding(d(8f), d(4f), d(8f), d(6f))
        val head = LinearLayout(context)
        head.orientation = LinearLayout.HORIZONTAL
        head.gravity = Gravity.CENTER_VERTICAL
        back.setOnClickListener {
            actions?.onVaultFeedback()
            if (openId != null) { openId = null; rebuild(-1) } else actions?.onVaultBack()
        }
        head.addView(back, LinearLayout.LayoutParams(d(44f), d(40f)))
        title.textSize = 18f
        title.maxLines = 1
        title.ellipsize = android.text.TextUtils.TruncateAt.END
        title.setTypeface(title.typeface, Typeface.BOLD)
        title.setPadding(d(12f), 0, 0, 0)
        head.addView(title, LinearLayout.LayoutParams(0, -2, 1f))
        head.addView(DiamondView(context), LinearLayout.LayoutParams(d(22f), d(22f)).apply { rightMargin = d(10f) })
        body.addView(head, LinearLayout.LayoutParams(-1, d(46f)))
        sub.textSize = 12.5f
        sub.setPadding(d(8f), 0, d(8f), d(4f))
        body.addView(sub)
        list.orientation = LinearLayout.VERTICAL
        PanelUi.glowScrollbar(sv)
        sv.addView(list)
        body.addView(sv, LinearLayout.LayoutParams(-1, 0, 1f))
        addView(body, LayoutParams(-1, -1))
        applyPalette(palette)
    }

    /** Se abre en la lista de cuentas. [everything] = también correos y textos (no solo contraseñas). */
    fun open(everything: Boolean) {
        all = everything
        openId = null
        VaultStore.load(context)
        rebuild(0)
    }

    /** Se cierra: no queda nada a la vista. */
    fun close() {
        openId = null
        list.removeAllViews()
    }

    /** [dir]: 1 = entrar (desliza desde la derecha), -1 = volver (desde la izquierda), 0 = abrir. */
    private fun rebuild(dir: Int) {
        list.removeAllViews()
        val id = openId
        if (id == null) showAccounts() else showAccount(id)
        sv.scrollTo(0, 0)
        // Animación: la lista entra deslizándose y aparece suave
        list.alpha = 0f
        list.translationX = d(28f).toFloat() * dir
        list.translationY = if (dir == 0) d(10f).toFloat() else 0f
        list.animate().alpha(1f).translationX(0f).translationY(0f).setDuration(220L)
            .setInterpolator(android.view.animation.DecelerateInterpolator()).start()
        var i = 0
        for (k in 0 until list.childCount) {
            val c = list.getChildAt(k)
            c.alpha = 0f
            c.animate().alpha(1f).setStartDelay(40L + 30L * i++).setDuration(200L).start()
        }
    }

    private fun showAccounts() {
        title.text = L.t("Datos", "Data")
        val accs = VaultStore.accounts()
        val loose = VaultStore.items.filter { it.kind != VaultStore.ACCOUNT && (all || it.kind == VaultStore.PASSWORD) }
        sub.text = if (accs.isEmpty() && loose.isEmpty()) L.t("Todavía no guardaste nada. Agrégalo en la app Best Keyboard → «Datos».", "Nothing saved yet. Add it in the Best Keyboard app → «Data».")
            else L.t("Toca una cuenta y luego el dato que quieres escribir.", "Tap an account and then the item you want to type.")
        for ((aid, a) in accs) {
            val r = row()
            r.addView(avatar(a.name), LinearLayout.LayoutParams(d(34f), d(34f)))
            val texts = texts()
            texts.addView(line(a.name, 15.5f, palette.text))
            val s = a.user.ifBlank { a.email }
            if (s.isNotBlank()) texts.addView(line(s, 12f, palette.hint))
            r.addView(texts, LinearLayout.LayoutParams(0, -2, 1f))
            r.addView(IconView(context, Icons.CHEVRON, 6.5f).apply { color = palette.hint; isClickable = false }, LinearLayout.LayoutParams(d(28f), d(28f)))
            r.setOnClickListener { actions?.onVaultFeedback(); openId = aid; rebuild(1) }
            list.addView(r, rowLp())
        }
        if (loose.isNotEmpty()) {
            list.addView(TextView(context).apply {
                text = L.t("Textos y atajos", "Texts and shortcuts"); textSize = 12f; setTextColor(palette.hint)
                setPadding(d(8f), d(12f), 0, d(2f))
            })
            for (e in loose) {
                val isPass = e.kind == VaultStore.PASSWORD
                val icon = when (e.kind) { VaultStore.EMAIL -> Icons.MAIL; VaultStore.PASSWORD -> Icons.LOCK; else -> Icons.CLIP }
                val label = if (isPass) e.label.ifBlank { L.t("Contraseña", "Password") } else e.label
                list.addView(itemRow(icon, label, e.value, isPass), rowLp())
            }
        }
    }

    private fun showAccount(id: Long) {
        val a = VaultStore.account(id) ?: run { openId = null; showAccounts(); return }
        title.text = a.name
        sub.text = L.t("Toca un dato y se escribe donde estás.", "Tap an item and it's typed where you are.")
        fun add(icon: Int, label: String, value: String, secret: Boolean) {
            if (value.isNotBlank()) list.addView(itemRow(icon, label, value, secret), rowLp())
        }
        add(Icons.PEOPLE, L.t("Nombre de usuario", "Username"), a.user, false)
        add(Icons.MAIL, L.t("Correo", "Email"), a.email, false)
        add(Icons.LOCK, L.t("Contraseña", "Password"), a.pass, true)
        add(Icons.KEY, L.t("Clave de seguridad", "Security key"), a.secKey, true)
        add(Icons.CALC, "PIN", a.pin, true)
        for ((q, ans) in a.questions) add(Icons.BUBBLE, q.ifBlank { L.t("Pregunta", "Question") }, ans, true)
    }

    private fun itemRow(icon: Int, label: String, value: String, secret: Boolean): View {
        val r = row()
        r.addView(IconView(context, icon, 8f).apply { color = palette.accent; isClickable = false }, LinearLayout.LayoutParams(d(26f), d(26f)))
        val texts = texts()
        if (label.isNotBlank()) texts.addView(line(label, 12f, palette.hint))
        val v = line("", 15f, palette.text).apply { maxLines = 2 }
        var shown = !secret
        fun paint() { v.text = if (shown) value else "•".repeat(value.length.coerceIn(6, 14)) }
        paint()
        texts.addView(v)
        r.addView(texts, LinearLayout.LayoutParams(0, -2, 1f))
        if (secret) {
            val eye = IconView(context, Icons.EYE_OFF, 8f).apply { color = palette.hint }
            eye.setOnClickListener {
                actions?.onVaultFeedback()
                shown = !shown; paint()
                eye.kind = if (shown) Icons.EYE else Icons.EYE_OFF
                eye.color = if (shown) palette.accent else palette.hint
            }
            r.addView(eye, LinearLayout.LayoutParams(d(44f), d(40f)))
        }
        r.setOnClickListener {
            actions?.onVaultFeedback()
            // Un brillito al tocar y se escribe
            r.animate().scaleX(0.97f).scaleY(0.97f).setDuration(80L).withEndAction {
                r.animate().scaleX(1f).scaleY(1f).setDuration(110L).start()
                actions?.onVaultText(value)
            }.start()
        }
        return r
    }

    private fun row() = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(d(12f), d(9f), d(6f), d(9f))
        background = PanelUi.pressable(palette.key, palette.keyPressed, d(18f).toFloat())
    }

    private fun rowLp() = LinearLayout.LayoutParams(-1, -2).apply { setMargins(d(4f), d(4f), d(4f), d(4f)) }

    private fun texts() = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(d(12f), 0, d(6f), 0) }

    private fun line(s: String, size: Float, color: Int) = TextView(context).apply {
        text = s; textSize = size; setTextColor(color); maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
    }

    private fun avatar(name: String): TextView = TextView(context).apply {
        text = name.trim().take(1).uppercase().ifEmpty { "?" }
        textSize = 14f; gravity = Gravity.CENTER; setTextColor(0xFF0B0D12.toInt()); setTypeface(typeface, Typeface.BOLD)
        val h = (name.lowercase().hashCode() and 0x7fffffff) % 3
        val c = MainActivity.CYAN; val v = MainActivity.VIOLET
        val cols = listOf(intArrayOf(c, v), intArrayOf(v, 0xFFF0ABFC.toInt()), intArrayOf(0xFF67E8F9.toInt(), 0xFFA78BFA.toInt()))
        background = GradientDrawable(GradientDrawable.Orientation.TL_BR, cols[h]).apply { shape = GradientDrawable.OVAL }
    }

    fun applyPalette(p: Palette) {
        palette = p
        setBackgroundColor(p.bg)
        title.setTextColor(p.text)
        sub.setTextColor(p.hint)
        back.color = p.text
        back.background = PanelUi.pressable(p.special, p.keyPressed, d(20f).toFloat())
        if (list.childCount > 0) { val k = openId; list.removeAllViews(); if (k == null) showAccounts() else showAccount(k) }
    }
}
