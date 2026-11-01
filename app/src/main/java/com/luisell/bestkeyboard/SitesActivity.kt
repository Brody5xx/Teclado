package com.luisell.bestkeyboard

import android.app.AlertDialog
import android.graphics.LinearGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.Locale

/** "Mis sitios": guarda los lugares donde trabajas para que «Llegué al sitio» sepa dónde estás. */
class SitesActivity : UiActivity() {

    private lateinit var list: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(android.R.style.Theme_Material_NoActionBar)
        super.onCreate(savedInstanceState)
        prefs = Prefs.get(this)
        loadLang()
        window.statusBarColor = BG
        window.navigationBarColor = BG
        val sv = ScrollView(this)
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
        title.text = tr("Mis sitios", "My sites")
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

        text(tr("Cuando tocas «📍 Llegué al sitio», el teclado usa el GPS para saber en cuál estás y lo pone en tu jornada y en el ticket (Location).",
            "When you tap «📍 Arrived on site», the keyboard uses GPS to know which one you're at and puts it in your workday and ticket (Location)."), 13f, faded = true)
        button(tr("📍 Guardar donde estoy ahora", "📍 Save where I am now"), primary = true) { saveHere() }
        button(tr("➕ Agregar por dirección", "➕ Add by address")) { addByAddress() }
        card(tr("Sitios guardados", "Saved sites"), Icons.GLOBE)
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(list, LinearLayout.LayoutParams(-1, -2))
        setContentView(sv)
        refresh()
        if (!WorkdayCenter.hasLocation(this)) {
            requestPermissions(arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION), 31)
        }
    }

    private fun refresh() {
        list.removeAllViews()
        val sites = SiteStore.all(prefs)
        if (sites.isEmpty()) {
            val t = TextView(this)
            t.text = tr("Todavía no tienes sitios. Cuando estés en un trabajo, toca «Guardar donde estoy ahora».",
                "No sites yet. When you're at a job, tap «Save where I am now».")
            t.textSize = 13f
            t.setTextColor(SUB)
            t.setPadding(0, dp(12), 0, dp(6))
            list.addView(t)
            return
        }
        for ((i, s) in sites.withIndex()) {
            if (i > 0) list.addView(View(this).apply { setBackgroundColor(LINE) }, LinearLayout.LayoutParams(-1, dp(1)))
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL
            row.setPadding(0, dp(8), 0, dp(8))
            val c = LinearLayout(this)
            c.orientation = LinearLayout.VERTICAL
            val n = TextView(this)
            n.text = "📍 " + s.name
            n.textSize = 16f
            n.setTextColor(TEXT)
            c.addView(n)
            val a = TextView(this)
            a.text = s.address.ifEmpty { tr("(sin dirección)", "(no address)") } +
                if (s.lat == 0.0 && s.lon == 0.0) tr("  ·  ⚠️ sin GPS", "  ·  ⚠️ no GPS") else ""
            a.textSize = 12f
            a.setTextColor(SUB)
            c.addView(a)
            row.addView(c, LinearLayout.LayoutParams(0, -2, 1f))
            val del = IconView(this, Icons.CLOSE, 6f)
            del.color = SUB
            del.setOnClickListener { SiteStore.remove(prefs, s.name); refresh() }
            row.addView(del, LinearLayout.LayoutParams(dp(40), dp(40)))
            list.addView(row)
        }
    }

    private fun field(hint: String, value: String = "") = EditText(this).apply {
        this.hint = hint
        setText(value)
        textSize = 15f
        setTextColor(TEXT)
        setHintTextColor(SUB)
        setPadding(dp(14), dp(12), dp(14), dp(12))
        background = GradientDrawable().apply { setColor(BG); cornerRadius = dpf(14f); setStroke(dp(1), LINE) }
    }

    private fun saveHere() {
        if (!WorkdayCenter.hasLocation(this)) {
            requestPermissions(arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION), 31)
            return
        }
        toast("Buscando dónde estás…", "Finding where you are…")
        WorkdayCenter.locate(this) { loc ->
            if (loc == null) { runOnUiThread { toast("No pude saber dónde estás. Prende la ubicación.", "Couldn't get your location. Turn on location.") }; return@locate }
            Thread {
                val addr = try {
                    @Suppress("DEPRECATION")
                    android.location.Geocoder(this, Locale.US).getFromLocation(loc.latitude, loc.longitude, 1)?.firstOrNull()?.getAddressLine(0)
                } catch (_: Exception) { null } ?: ""
                runOnUiThread { askName(addr, loc.latitude, loc.longitude) }
            }.start()
        }
    }

    private fun askName(address: String, lat: Double, lon: Double) {
        val name = field(tr("Nombre (ej. RUMC, Hospital, Tienda 12)", "Name (e.g. RUMC, Hospital, Store 12)"))
        val addr = field(tr("Dirección", "Address"), address)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), 0)
            addView(name, LinearLayout.LayoutParams(-1, -2))
            addView(addr, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
        }
        AlertDialog.Builder(this)
            .setTitle(tr("Guardar sitio", "Save site"))
            .setView(box)
            .setPositiveButton(tr("Guardar", "Save")) { _, _ ->
                val n = name.text.toString().trim()
                if (n.isEmpty()) { toast("Ponle un nombre.", "Give it a name."); return@setPositiveButton }
                SiteStore.add(prefs, SiteStore.Site(n, addr.text.toString().trim(), lat, lon))
                refresh()
                toast("Sitio guardado.", "Site saved.")
            }
            .setNegativeButton(tr("Cancelar", "Cancel"), null)
            .show()
    }

    private fun addByAddress() {
        val name = field(tr("Nombre (ej. RUMC)", "Name (e.g. RUMC)"))
        val addr = field(tr("Dirección completa", "Full address"))
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), 0)
            addView(name, LinearLayout.LayoutParams(-1, -2))
            addView(addr, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
        }
        AlertDialog.Builder(this)
            .setTitle(tr("Agregar sitio", "Add site"))
            .setView(box)
            .setPositiveButton(tr("Guardar", "Save")) { _, _ ->
                val n = name.text.toString().trim()
                val a = addr.text.toString().trim()
                if (n.isEmpty() || a.isEmpty()) { toast("Pon el nombre y la dirección.", "Add the name and address."); return@setPositiveButton }
                Thread {
                    // Busca en el mapa dónde queda esa dirección (para que el GPS lo reconozca)
                    val found = try {
                        @Suppress("DEPRECATION")
                        android.location.Geocoder(this, Locale.US).getFromLocationName(a, 1)?.firstOrNull()
                    } catch (_: Exception) { null }
                    runOnUiThread {
                        SiteStore.add(prefs, SiteStore.Site(n, a, found?.latitude ?: 0.0, found?.longitude ?: 0.0))
                        refresh()
                        if (found == null) toast("Guardado, pero no encontré la dirección en el mapa. Mejor usa «Guardar donde estoy ahora» cuando estés ahí.",
                            "Saved, but I couldn't find the address on the map. Better use «Save where I am now» when you're there.")
                        else toast("Sitio guardado.", "Site saved.")
                    }
                }.start()
            }
            .setNegativeButton(tr("Cancelar", "Cancel"), null)
            .show()
    }
}
