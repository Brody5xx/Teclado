package com.luisell.bestkeyboard

import android.app.Activity
import android.app.AlertDialog
import android.app.NotificationManager
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.WindowManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import java.util.Calendar

/**
 * 🕐 Escribir la hora tú mismo: «5:32pm», «532p», «17:32» o «5 32».
 * Sirve para cambiar la hora de algo que ya anotaste, o para anotar algo de hoy con la hora que tú digas.
 */
class TimeSetActivity : Activity() {

    companion object {
        const val EXTRA_EVENT = "event_time"   // cambiar la hora de este evento
        const val EXTRA_KIND = "kind"          // o anotar uno nuevo de hoy
        const val EXTRA_SITE = "site"

        fun open(ctx: Context, eventTime: Long = -1L, kind: String? = null) {
            val i = Intent(ctx, TimeSetActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            if (eventTime > 0) i.putExtra(EXTRA_EVENT, eventTime)
            if (kind != null) i.putExtra(EXTRA_KIND, kind)
            try { ctx.startActivity(i) } catch (_: Exception) { }
        }

        /** Entiende «5:32pm», «5:32 p.m.», «532p», «17:32», «5 32», «5». Sin am/pm elige la más cercana antes de [now]. */
        fun parse(text: String, now: Long = System.currentTimeMillis()): Pair<Int, Int>? {
            val s = text.lowercase().replace(".", "").replace(" ", "").trim()
            val m = Regex("^(\\d{1,2})(?::?(\\d{2}))?(am|pm|a|p)?$").find(s) ?: return null
            var h = m.groupValues[1].toIntOrNull() ?: return null
            val min = m.groupValues[2].ifEmpty { "0" }.toIntOrNull() ?: return null
            if (min > 59) return null
            val ap = m.groupValues[3]
            if (ap.isNotEmpty()) {
                if (h !in 1..12) return null
                h %= 12
                if (ap.startsWith("p")) h += 12
            } else {
                if (h > 23) return null
                if (h in 1..12) {
                    // «5:32» a las 9 PM = 5:32 PM (la más cercana que ya pasó)
                    val c = Calendar.getInstance().apply { timeInMillis = now }
                    val nowMin = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
                    val am = (h % 12) * 60 + min
                    val pm = am + 12 * 60
                    h = if (pm <= nowMin) h % 12 + 12 else h % 12
                    if (am > nowMin && pm > nowMin) h = h % 12 // las dos están en el futuro: la de la mañana
                }
            }
            return h to min
        }
    }

    private var done = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) setShowWhenLocked(true)
        val p = Prefs.get(this)
        L.set(p.getString(Prefs.LANG, "es") ?: "es")
        val evTime = intent.getLongExtra(EXTRA_EVENT, -1L)
        val kind = intent.getStringExtra(EXTRA_KIND)
        val ev = if (evTime > 0) WorkDay.day(p, evTime).firstOrNull { it.time == evTime } else null
        if (ev == null && kind == null) { finish(); return }
        val k = ev?.kind ?: kind!!
        val base = ev?.time ?: System.currentTimeMillis()
        (getSystemService(NOTIFICATION_SERVICE) as? NotificationManager)?.cancel(4722)

        val box = EditText(this).apply {
            hint = L.t("Ej. 5:32pm", "E.g. 5:32pm")
            if (ev != null) setText(WorkStore.time(ev.time).lowercase().replace(" ", ""))
            setSelection(text.length)
            isSingleLine = true
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            textSize = 22f
            setPadding(40, 28, 40, 28)
            background = GradientDrawable().apply { cornerRadius = 36f; setStroke(3, 0xFF22D3EE.toInt()) }
        }
        val wrap = LinearLayout(this).apply { setPadding(56, 20, 56, 0); addView(box, LinearLayout.LayoutParams(-1, -2)) }
        val dlg = AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle(WorkDay.label(k))
            .setMessage(L.t("¿A qué hora? Escríbela como quieras: 5:32pm, 532p o 17:32.", "What time? Type it any way: 5:32pm, 532p or 17:32."))
            .setView(wrap)
            .setPositiveButton(L.t("Guardar", "Save"), null)
            .setNeutralButton(L.t("🕐 Reloj", "🕐 Clock"), null)
            .setNegativeButton(L.t("Cancelar", "Cancel")) { _, _ -> finish() }
            .setOnCancelListener { finish() }
            .create()
        dlg.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
        dlg.setOnShowListener {
            dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val hm = parse(box.text.toString())
                if (hm == null) {
                    Toast.makeText(this, L.t("No entendí la hora. Prueba así: 5:32pm", "I didn't get the time. Try: 5:32pm"), Toast.LENGTH_SHORT).show()
                } else { save(p, ev, k, base, hm.first, hm.second); dlg.dismiss() }
            }
            dlg.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                val c = Calendar.getInstance().apply { timeInMillis = base }
                // Reloj redondo moderno (el de ruedas se cerraba en algunos teléfonos)
                try {
                    TimePickerDialog(this, android.R.style.Theme_Material_Dialog, { _, h, m -> save(p, ev, k, base, h, m); dlg.dismiss() },
                        c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), false).show()
                } catch (_: Exception) {
                    Toast.makeText(this, L.t("Escribe la hora en la caja (ej. 5:32pm).", "Type the time in the box (e.g. 5:32pm)."), Toast.LENGTH_SHORT).show()
                }
            }
        }
        dlg.show()
        box.requestFocus()
    }

    private fun save(p: android.content.SharedPreferences, ev: WorkDay.Event?, kind: String, base: Long, h: Int, m: Int) {
        if (done) return
        done = true
        val c = Calendar.getInstance().apply {
            timeInMillis = base
            set(Calendar.HOUR_OF_DAY, h); set(Calendar.MINUTE, m); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val t = c.timeInMillis
        if (ev != null) WorkDay.moveTo(p, ev.time, t)
        else WorkDay.addAt(p, kind, if (kind == WorkDay.LEAVE) WorkDay.day(p).lastOrNull { it.kind == WorkDay.ARRIVE }?.site ?: "" else "", t)
        WorkdayCenter.refresh(this)
        Toast.makeText(this, WorkDay.label(kind) + " · " + WorkStore.time(t), Toast.LENGTH_SHORT).show()
        finish()
    }
}
