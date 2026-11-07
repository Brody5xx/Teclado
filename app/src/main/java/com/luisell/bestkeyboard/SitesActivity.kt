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

    companion object { const val EXTRA_NEW = "new_place" }

    private lateinit var list: LinearLayout

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
        head.addView(DiamondView(this), LinearLayout.LayoutParams(dp(26), dp(26)).apply { leftMargin = dp(10) }) // 💎 con su luz
        root.addView(head)

        text(tr("Cuando tocas «📍 Llegué al sitio», el teclado usa el GPS para saber en cuál estás y lo pone en tu jornada y en el ticket (Location).",
            "When you tap «📍 Arrived on site», the keyboard uses GPS to know which one you're at and puts it in your workday and ticket (Location)."), 13f, faded = true)
        // ---- Llegada y salida automáticas
        autoBtn = button("") { toggleAuto() }
        autoHelp = text("", 12f, faded = true)
        homeBtn = button("") { homeTap() }
        segmented(tr("En casa, esperar antes de cerrar el día", "At home, wait before closing the day"), Prefs.HOME_WAIT, "20",
            listOf("10" to "10 min", "20" to "20 min", "30" to "30 min", "45" to "45 min"))
        text(tr("Así, si solo paras en tu casa a buscar o dejar algo y te vas antes, tu día sigue abierto.",
            "That way, if you just stop at home to pick up or drop off something and leave before, your day stays open."), 12f, faded = true)
        segmented(tr("Esperar antes de anotar la salida", "Wait before logging the departure"), Prefs.AUTO_EXIT_WAIT, "10",
            listOf("5" to "5 min", "10" to "10 min", "15" to "15 min", "20" to "20 min"))
        text(tr("Adentro de edificios el GPS a veces brinca. Si vuelves a «entrar» antes de ese tiempo, no se anota ninguna salida.",
            "Inside buildings GPS sometimes jumps. If you «come back in» before that time, no departure is logged."), 12f, faded = true)
        // 📏🎯 Distancia y sensibilidad (como Samsung Rutinas)
        segmented(tr("📏 Distancia de detección (todos los lugares)", "📏 Detection distance (all places)"), Prefs.SITE_RADIUS, "250",
            listOf("100" to "100 m", "250" to "250 m", "400" to "400 m", "600" to "600 m", "1000" to "1 km")) { AutoSites.register(this); refresh() }
        text(tr("Qué tan lejos del punto guardado cuenta como «estoy aquí». Toca un lugar abajo para ponerle su propia distancia en el mapa.",
            "How far from the saved point counts as «I'm here». Tap a place below to give it its own distance on the map."), 12f, faded = true)
        segmented(tr("🎯 Sensibilidad", "🎯 Sensitivity"), Prefs.SITE_SENS, "normal",
            listOf("high" to tr("Alta", "High"), "normal" to "Normal", "low" to tr("Baja", "Low"))) { AutoSites.register(this); paintSens() }
        sensHelp = text("", 12f, faded = true)
        paintSens()
        paintAuto()
        button(tr("📍 Guardar donde estoy ahora", "📍 Save where I am now"), primary = true) { saveHere() }
        button(tr("🛻 Guardar el parqueo de la camioneta (estoy aquí)", "🛻 Save the truck parking lot (I'm here)")) { saveHere(start = true) }
        text(tr("El parqueo es donde recoges y dejas la camioneta: cuando sales de ahí empieza tu día, y cuando vuelves y te quedas, termina.",
            "The parking lot is where you pick up and drop off the truck: when you leave it your day starts, and when you come back and stay, it ends."), 12f, faded = true)
        button(tr("➕ Agregar por dirección", "➕ Add by address")) { addByAddress() }
        SiteStore.getNew(prefs)?.let { n ->
            button(tr("📍 Guardar el lugar nuevo de hoy: ", "📍 Save today's new place: ") + n.short) { askName(n.full.ifEmpty { n.short }, n.lat, n.lon, n.short) }
        }
        card(tr("Sitios guardados", "Saved sites"), Icons.GLOBE)
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(list, LinearLayout.LayoutParams(-1, -2))
        setContentView(sv)
        refresh()
        // Viene de «📍 Lugar nuevo»: abre de una vez para ponerle nombre
        SiteStore.getNew(prefs)?.let { n ->
            if (intent?.getBooleanExtra(EXTRA_NEW, false) == true) askName(n.full.ifEmpty { n.short }, n.lat, n.lon, n.short)
        }
        if (!WorkdayCenter.hasLocation(this)) {
            requestPermissions(arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION), 31)
        }
    }

    private lateinit var sensHelp: TextView

    private fun paintSens() {
        if (!::sensHelp.isInitialized) return
        sensHelp.text = when (prefs.getString(Prefs.SITE_SENS, "normal")) {
            "high" -> tr("Alta: anota la llegada después de 1 minuto adentro y el teléfono avisa al instante. Gasta un poquito más de batería.",
                "High: logs the arrival after 1 minute inside and the phone reacts right away. Uses a little more battery.")
            "low" -> tr("Baja: solo si te quedas 6 minutos adentro (si solo pasas cerca, no cuenta). Gasta menos batería.",
                "Low: only if you stay 6 minutes inside (just passing by doesn't count). Uses less battery.")
            else -> tr("Normal: anota la llegada después de 3 minutos adentro.", "Normal: logs the arrival after 3 minutes inside.")
        }
    }

    private lateinit var autoBtn: TextView
    private lateinit var autoHelp: TextView

    private lateinit var homeBtn: TextView

    private fun homeTap() {
        if (SiteStore.home(prefs) != null) {
            AlertDialog.Builder(this)
                .setTitle(tr("🏠 Tu casa", "🏠 Your home"))
                .setMessage(tr("¿Quitar tu casa? Ya no se anotará solo «Llegué a casa».", "Remove your home? «Home» won't be logged automatically anymore."))
                .setPositiveButton(tr("Quitar", "Remove")) { _, _ -> SiteStore.setHome(prefs, null, null); AutoSites.register(this); paintAuto() }
                .setNegativeButton(tr("Cancelar", "Cancel"), null)
                .show()
            return
        }
        if (!WorkdayCenter.hasLocation(this)) {
            requestPermissions(arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION), 31)
            return
        }
        AlertDialog.Builder(this)
            .setTitle(tr("🏠 Guardar tu casa", "🏠 Save your home"))
            .setMessage(tr("¿Estás en tu casa ahora? Se guarda solo el punto del GPS, solo en tu teléfono. En los tickets, el PDF y el resumen solo dirá «Casa».",
                "Are you home right now? Only the GPS point is saved, only on your phone. Tickets, the PDF and the summary will only say «Home»."))
            .setPositiveButton(tr("Sí, estoy en casa", "Yes, I'm home")) { _, _ ->
                toast("Buscando dónde estás…", "Finding where you are…")
                WorkdayCenter.locate(this) { loc ->
                    runOnUiThread {
                        if (loc == null) { toast("No pude saber dónde estás. Prende la ubicación.", "Couldn't get your location. Turn on location."); return@runOnUiThread }
                        SiteStore.setHome(prefs, loc.latitude, loc.longitude)
                        AutoSites.register(this)
                        paintAuto()
                        toast("🏠 Casa guardada.", "🏠 Home saved.")
                        if (!AutoSites.ready(this)) toast("Prende «Llegada y salida automáticas» para que se anote solo.", "Turn on «Automatic arrival and departure» so it's logged by itself.")
                    }
                }
            }
            .setNegativeButton(tr("Cancelar", "Cancel"), null)
            .show()
    }

    private fun paintAuto() {
        if (::homeBtn.isInitialized) homeBtn.text = if (SiteStore.home(prefs) != null) tr("🏠 Casa guardada ✓ (toca para quitarla)", "🏠 Home saved ✓ (tap to remove)")
            else tr("🏠 Guardar mi casa (para anotar solo «Llegué a casa»)", "🏠 Save my home (to log «Home» automatically)")
        val on = AutoSites.isOn(this)
        val ready = AutoSites.ready(this)
        autoBtn.text = when {
            !on -> tr("🤖 Llegada y salida automáticas: Apagado", "🤖 Automatic arrival and departure: Off")
            ready -> tr("🤖 Llegada y salida automáticas: ✓ Encendido", "🤖 Automatic arrival and departure: ✓ On")
            else -> tr("⚠️ Falta un permiso (toca para darlo)", "⚠️ A permission is missing (tap to give it)")
        }
        autoHelp.text = if (!on) tr("Si lo prendes, cuando llegues a uno de tus sitios y te quedes unos minutos se anota «Llegué» con la hora en que entraste, y cuando te vayas se anota «Salí». Siempre te llega un aviso con «Deshacer».",
            "If you turn it on, when you arrive at one of your sites and stay a few minutes, «Arrived» is logged with the time you got there, and when you leave, «Left» is logged. You always get an alert with «Undo».")
        else if (!ready) tr("Para que funcione con el teclado cerrado, en Ubicación elige «Permitir todo el tiempo». El teléfono usa geocercas, que gastan muy poca batería.",
            "To work with the keyboard closed, under Location choose «Allow all the time». The phone uses geofences, which use very little battery.")
        else tr("Funciona con los sitios guardados de abajo. Usa muy poca batería. Si un día no estás trabajando, toca «Deshacer» en el aviso.",
            "Works with the saved sites below. Uses very little battery. If you're not working one day, tap «Undo» on the alert.")
    }

    private fun toggleAuto() {
        val on = AutoSites.isOn(this)
        if (on && AutoSites.ready(this)) {
            prefs.edit().putBoolean(Prefs.AUTO_SITES, false).apply()
            AutoSites.register(this)
            paintAuto()
            return
        }
        prefs.edit().putBoolean(Prefs.AUTO_SITES, true).apply()
        askAutoPermissions()
    }

    private fun askAutoPermissions() {
        when {
            !WorkdayCenter.hasLocation(this) ->
                requestPermissions(arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION), 41)
            !AutoSites.hasBackground(this) -> AlertDialog.Builder(this)
                .setTitle(tr("Un permiso más", "One more permission"))
                .setMessage(tr("En la pantalla que se abre, toca «Permitir todo el tiempo». Así el teléfono sabe cuándo llegas o te vas aunque el teclado esté cerrado.",
                    "On the screen that opens, tap «Allow all the time». That way the phone knows when you arrive or leave even with the keyboard closed."))
                .setPositiveButton(tr("Continuar", "Continue")) { _, _ ->
                    requestPermissions(arrayOf("android.permission.ACCESS_BACKGROUND_LOCATION"), 42)
                }
                .setNegativeButton(tr("Ahora no", "Not now"), null)
                .show()
            else -> {
                AutoSites.register(this)
                toast("¡Listo! Ya se anota solo cuando llegas y te vas.", "Done! Arrivals and departures are now logged automatically.")
            }
        }
        paintAuto()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if ((requestCode == 41 || requestCode == 42) && AutoSites.isOn(this)) askAutoPermissions()
    }

    override fun onResume() {
        super.onResume()
        if (::autoBtn.isInitialized) paintAuto()
        if (AutoSites.isOn(this)) AutoSites.register(this)
    }

    private fun refresh() {
        AutoSites.register(this)
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
            n.text = (if (s.isStart) "🛻 " else "📍 ") + s.name + if (s.rate > 0) "   ⭐ " + Pay.money(s.rate) + tr("/hora", "/hour") else ""
            n.textSize = 16f
            n.setTextColor(TEXT)
            c.addView(n)
            val a = TextView(this)
            a.text = (if (s.isStart) tr("Comienzo y fin del día · ", "Start and end of day · ") else "") + s.address.ifEmpty { tr("(sin dirección)", "(no address)") } +
                "  ·  📏 " + SiteStore.radiusOf(prefs, s) + " m" + (if (s.radius > 0) "" else tr(" (la de todos)", " (default)")) +
                if (s.lat == 0.0 && s.lon == 0.0) tr("  ·  ⚠️ sin GPS", "  ·  ⚠️ no GPS") else ""
            a.textSize = 12f
            a.setTextColor(SUB)
            c.addView(a)
            c.setOnClickListener { siteOptions(s) }
            row.addView(c, LinearLayout.LayoutParams(0, -2, 1f))
            val del = IconView(this, Icons.CLOSE, 6f)
            del.color = SUB
            del.setOnClickListener { SiteStore.remove(prefs, s.name); refresh() }
            row.addView(del, LinearLayout.LayoutParams(dp(40), dp(40)))
            list.addView(row)
        }
    }

    /** Al tocar un sitio: su pago especial o cambiarlo a 🛻 parqueo (comienzo y fin del día). */
    private fun siteOptions(s: SiteStore.Site) {
        val opts = arrayOf(
            tr("📏 Distancia de detección (en el mapa)", "📏 Detection distance (on the map)"),
            tr("💵 Pago por hora en este sitio", "💵 Hourly pay at this site"),
            if (s.isStart) tr("📍 Cambiar a sitio de trabajo normal", "📍 Change to a normal work site")
            else tr("🛻 Usarlo como parqueo de la camioneta (comienzo y fin del día)", "🛻 Use it as the truck parking (start and end of day)"),
        )
        choiceDialog(s.name, opts.map { Emo.stripUi(it).trim() to null }) { w ->
                when (w) {
                    0 -> askRadius(s)
                    1 -> askRate(s)
                    else -> { SiteStore.setKind(prefs, s.name, if (s.isStart) "site" else "start"); refresh() }
                }
            }
    }

    /** 📏 La distancia de un lugar, viendo el círculo en el mapa (como Samsung Rutinas). */
    private fun askRadius(s: SiteStore.Site) {
        if (s.lat == 0.0 && s.lon == 0.0) { toast("Este lugar no tiene GPS. Guárdalo estando ahí.", "This place has no GPS. Save it while you're there."); return }
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(8), dp(20), 0) }
        val map = MapPreview(this, s.lat, s.lon)
        map.radius = SiteStore.radiusOf(prefs, s)
        map.clipToOutline = true
        map.background = GradientDrawable().apply { cornerRadius = dpf(18f); setColor(0xFF1B2230.toInt()) }
        box.addView(map, LinearLayout.LayoutParams(-1, dp(240)))
        val label = TextView(this).apply { textSize = 16f; setTextColor(TEXT); setPadding(0, dp(12), 0, dp(4)); gravity = Gravity.CENTER }
        box.addView(label)
        val steps = listOf(50, 75, 100, 125, 150, 200, 250, 300, 350, 400, 500, 600, 700, 800, 1000, 1200, 1500, 2000)
        fun show(r: Int) {
            map.radius = r
            label.text = "$r m  (" + Math.round(r * 3.281) + tr(" pies)", " ft)") +
                if (r < 100) tr("\n⚠️ Muy chico: el GPS a veces no lo detecta.", "\n⚠️ Very small: GPS may miss it.") else ""
        }
        val bar = android.widget.SeekBar(this).apply {
            max = steps.size - 1
            progress = steps.indexOfFirst { it >= map.radius }.let { if (it < 0) steps.size - 1 else it }
            setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: android.widget.SeekBar?, v: Int, user: Boolean) { show(steps[v]) }
                override fun onStartTrackingTouch(sb: android.widget.SeekBar?) {}
                override fun onStopTrackingTouch(sb: android.widget.SeekBar?) {}
            })
        }
        box.addView(bar, LinearLayout.LayoutParams(-1, -2))
        show(map.radius)
        AlertDialog.Builder(this)
            .setTitle("📏 " + s.name)
            .setMessage(tr("Mueve la barra: el círculo es el área donde cuenta que llegaste.", "Move the bar: the circle is the area where it counts as arrived."))
            .setView(box)
            .setPositiveButton(tr("Guardar", "Save")) { _, _ ->
                SiteStore.setRadius(prefs, s.name, steps[bar.progress]); AutoSites.register(this); refresh()
                toast("📏 Distancia guardada.", "📏 Distance saved.")
            }
            .setNeutralButton(tr("Usar la de todos", "Use default")) { _, _ -> SiteStore.setRadius(prefs, s.name, 0); AutoSites.register(this); refresh() }
            .setNegativeButton(tr("Cancelar", "Cancel"), null)
            .show()
    }

    /** Un sitio que paga más por hora (0 o vacío = el pago normal). */
    private fun askRate(s: SiteStore.Site) {
        val box = field(tr("Pago por hora en este sitio (vacío = el normal)", "Hourly pay at this site (empty = normal)"),
            if (s.rate > 0) s.rate.toString().removeSuffix(".0") else "")
        box.inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        val wrap = LinearLayout(this).apply { setPadding(dp(20), dp(8), dp(20), 0); addView(box, LinearLayout.LayoutParams(-1, -2)) }
        AlertDialog.Builder(this)
            .setTitle("📍 " + s.name)
            .setMessage(tr("¿Este sitio te paga diferente por hora? Escribe cuánto. Déjalo vacío para usar tu pago normal.",
                "Does this site pay a different hourly rate? Type it. Leave it empty to use your normal pay."))
            .setView(wrap)
            .setPositiveButton(tr("Guardar", "Save")) { _, _ ->
                SiteStore.setRate(prefs, s.name, box.text.toString().trim().replace(",", ".").toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0)
                refresh()
            }
            .setNegativeButton(tr("Cancelar", "Cancel"), null)
            .show()
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

    private fun saveHere(start: Boolean = false) {
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
                runOnUiThread { askName(addr, loc.latitude, loc.longitude, kind = if (start) "start" else "site") }
            }.start()
        }
    }

    private fun askName(address: String, lat: Double, lon: Double, fromNew: String = "", kind: String = "site") {
        val name = field(if (kind == "start") tr("Nombre (ej. Parqueo de la camioneta)", "Name (e.g. Truck parking)")
            else tr("Nombre (ej. Oficina central, Bodega, Tienda 12)", "Name (e.g. Main office, Warehouse, Store 12)"),
            if (kind == "start") tr("Parqueo de la camioneta", "Truck parking") else "")
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
                SiteStore.add(prefs, SiteStore.Site(n, addr.text.toString().trim(), lat, lon, kind = kind))
                if (kind == "start" && !AutoSites.ready(this))
                    toast("Prende «Llegada y salida automáticas» para que tu día empiece y termine solo.", "Turn on «Automatic arrival and departure» so your day starts and ends by itself.")
                if (fromNew.isNotEmpty()) {
                    // Lo de hoy también cambia: "Llegué a 123 Main St" → "Llegué a Oficina central"
                    WorkDay.renameSiteToday(prefs, fromNew, n)
                    SiteStore.setNew(prefs, null)
                    WorkdayCenter.refresh(this)
                }
                refresh()
                toast("Sitio guardado.", "Site saved.")
            }
            .setNegativeButton(tr("Cancelar", "Cancel"), null)
            .show()
    }

    private fun addByAddress() {
        val name = field(tr("Nombre (ej. Bodega norte)", "Name (e.g. North warehouse)"))
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
