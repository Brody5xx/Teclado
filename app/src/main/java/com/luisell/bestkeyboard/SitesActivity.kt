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
        homeAddr = text("", 12f, faded = true)
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
            listOf("high" to tr("Alta", "High"), "normal" to tr("Normal", "Normal"), "low" to tr("Baja", "Low"))) { AutoSites.register(this); paintSens() }
        sensHelp = text("", 12f, faded = true)
        paintSens()
        paintAuto()
        button(tr("📍 Guardar donde estoy ahora", "📍 Save where I am now"), primary = true) { saveHere() }
        parkBtn = button("") { parkTap() }
        parkAddr = text("", 12f, faded = true)
        paintAuto()
        button(tr("➕ Agregar por dirección", "➕ Add by address")) { addByAddress() }
        SiteStore.getNew(prefs)?.let { n ->
            button(tr("📍 Guardar el lugar nuevo de hoy: ", "📍 Save today's new place: ") + n.short) { askName(n.full.ifEmpty { n.short }, n.lat, n.lon, n.short) }
        }
        // 🎯 Precisión del GPS: revisa los ajustes y deja probar la ubicación
        card(tr("Precisión del GPS", "GPS accuracy"), Icons.MAP_PIN)
        gpsBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(gpsBox, LinearLayout.LayoutParams(-1, -2))
        paintGps()
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
    private lateinit var gpsBox: LinearLayout
    private var googleOk: Boolean? = null
    private var googleFix: ((android.app.Activity) -> Unit)? = null
    private var testLine = ""


    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 77 && ::gpsBox.isInitialized) { googleOk = null; paintGps() }
    }

    /** La tarjeta «Precisión del GPS»: qué está listo, qué falta (con su botón) y la prueba. */
    private fun paintGps() {
        if (!::gpsBox.isInitialized) return
        if (googleOk == null) GpsCheck.checkGoogle(this) { ok, fix -> googleOk = ok; googleFix = fix; runOnUiThread { drawGps() } }
        drawGps()
    }

    private fun drawGps() {
        gpsBox.removeAllViews()
        val keep = col
        col = gpsBox
        val list = GpsCheck.checks(this, googleOk, googleFix)
        val missing = list.count { !it.ok }
        text(if (missing == 0) tr("Todo listo: tu teléfono está usando la ubicación más precisa.", "All set: your phone is using the most precise location.")
            else if (missing == 1) tr("Falta 1 ajuste. Tócalo para arreglarlo.", "1 setting missing. Tap it to fix it.")
            else tr("Faltan $missing ajustes. Toca cada uno para arreglarlo.", "$missing settings missing. Tap each one to fix it."), 13f, bold = true)
        for (c in list) {
            if (c.ok) text(tr("Listo", "Done") + "  ·  " + tr(c.es, c.en), 13f)
            else {
                val fx = c.fix
                button(tr("Arreglar", "Fix") + "  ·  " + tr(c.es, c.en)) { fx?.invoke(this) }
                text(tr(c.fixEs, c.fixEn), 12f, faded = true)
            }
        }
        switch(tr("GPS fino cerca del sitio", "Precise GPS near the site"), "gps_fine_near", true,
            tr("Al acercarte a 1 km de un sitio, prende el GPS fino unos minutos para anotar el minuto exacto en que entras o sales. Después se apaga solo.",
                "When you get within 1 km of a site, it turns on precise GPS for a few minutes to log the exact minute you go in or out. Then it turns off by itself."))
        switch(tr("Detectar cuando vas en vehículo", "Detect when you're in a vehicle"), "veh_detect", true,
            tr("Sabe cuándo te subes y te bajas de la camioneta (también de pasajero): el día empieza y termina a esa hora, y la salida de un sitio se anota al minuto.",
                "It knows when you get in and out of the truck (also as a passenger): the day starts and ends at that time, and leaving a site is logged to the minute."))
        text(tr("Además aprende solo: dónde llegas en cada sitio (y ajusta el punto), las redes Wi-Fi del sitio (para saber que estás adentro aunque el GPS falle), tus «Deshacer» y tus correcciones de hora.",
            "It also learns by itself: where you arrive at each site (and adjusts the point), the site's Wi-Fi networks (to know you're inside even if GPS fails), your «Undo» taps and your time corrections."), 12f, faded = true)
        button(tr("Probar el GPS ahora", "Test GPS now"), primary = true) { testGps() }
        if (testLine.isNotEmpty()) text(testLine, 13f)
        GpsCheck.ignoredText(this)?.let { text(it, 12f, faded = true) }
        SiteLearn.weekReport(prefs)?.let { text(it, 12f, faded = true) }
        text(tr("Consejo: pon el punto de cada sitio en la entrada o el parqueo donde llegas, y usa 150 m para sitios pequeños (no menos de 100 m).",
            "Tip: put each site's point at the entrance or parking lot where you arrive, and use 150 m for small sites (not less than 100 m)."), 12f, faded = true)
        col = keep
    }

    /** Busca tu ubicación con el GPS fino y dice qué tan precisa es y a qué sitio estás más cerca. */
    private fun testGps() {
        testLine = tr("Buscando tu ubicación con el GPS fino… (hasta 15 segundos)", "Finding your location with precise GPS… (up to 15 seconds)")
        drawGps()
        WorkdayCenter.preciseFix(this, 15_000L, 15f) { l ->
            testLine = if (l == null) tr("No se pudo leer la ubicación. Revisa que la ubicación del teléfono esté prendida.", "Couldn't read the location. Check that the phone's location is on.")
            else {
                val acc = l.accuracy.toInt()
                val quality = when {
                    acc <= 15 -> tr("excelente", "excellent")
                    acc <= 40 -> tr("buena", "good")
                    acc <= 100 -> tr("regular", "fair")
                    else -> tr("mala (sal a un lugar abierto o prende la precisión de Google)", "poor (go outside or turn on Google Location Accuracy)")
                }
                var line = tr("Precisión: $acc m, $quality.", "Accuracy: $acc m, $quality.")
                val near = SiteStore.all(prefs).filter { !(it.lat == 0.0 && it.lon == 0.0) }.map { s ->
                    val d = FloatArray(1); android.location.Location.distanceBetween(l.latitude, l.longitude, s.lat, s.lon, d); s to d[0]
                }.minByOrNull { it.second }
                if (near != null) {
                    val (s, d) = near
                    val r = SiteStore.radiusOf(prefs, s)
                    val dist = if (d >= 1000f) String.format(Locale.US, "%.1f km", d / 1000f) else "${d.toInt()} m"
                    line += "\n" + tr("El sitio más cerca: ${s.name}, a $dist", "Nearest site: ${s.name}, $dist away") +
                        (if (s.hasShape) (if (Geo.where(prefs, s, l.latitude, l.longitude, l.accuracy) == true) tr(" (estás adentro de su forma).", " (you're inside its shape).") else tr(" (estás afuera de su forma).", " (you're outside its shape)."))
                        else if (d <= r) tr(" (estás adentro de su círculo de $r m).", " (you're inside its $r m circle).") else tr(" (su círculo es de $r m).", " (its circle is $r m)."))
                }
                line
            }
            runOnUiThread { drawGps() }
        }
    }

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
    private lateinit var homeAddr: TextView
    private var lookingHome = false
    private lateinit var parkBtn: TextView
    private lateinit var parkAddr: TextView

    /** ✏️ Una ventanita para escribir una dirección; la busca en el mapa y devuelve dónde queda (o null). */
    private fun askAddress(title: String, current: String, onFound: (String, Double?, Double?) -> Unit) {
        val addr = field(tr("Dirección completa (calle, número, ciudad)", "Full address (street, number, city)"), current)
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(8), dp(20), 0); addView(addr, LinearLayout.LayoutParams(-1, -2)) }
        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(box)
            .setPositiveButton(tr("Guardar", "Save")) { _, _ ->
                val a = addr.text.toString().trim()
                if (a.isEmpty()) { toast("Escribe la dirección.", "Type the address."); return@setPositiveButton }
                toast("Buscando la dirección en el mapa…", "Finding the address on the map…")
                Thread {
                    val f = SiteStore.findAddress(this, a)
                    runOnUiThread { onFound(f?.getAddressLine(0)?.takeIf { it.isNotBlank() } ?: a, f?.latitude, f?.longitude) }
                }.start()
            }
            .setNegativeButton(tr("Cancelar", "Cancel"), null)
            .show()
    }

    /** El parqueo de la camioneta (el primero, si guardaste varios). */
    private fun parking(): SiteStore.Site? = SiteStore.all(prefs).firstOrNull { it.isStart }

    /** 🛻 El botón del parqueo: guardarlo, o cambiar su dirección, usar donde estás, agregar otro o quitarlo. */
    private fun parkTap() {
        val pk = parking()
        if (pk == null) {
            val opts = listOf(
                tr("Estoy en el parqueo ahora", "I'm at the parking lot now") to { saveHere(start = true) },
                tr("Escribir la dirección", "Type the address") to {
                    askAddress(tr("Dirección del parqueo", "Parking lot address"), "") { a, la, lo ->
                        if (la == null || lo == null) { toast("No encontré esa dirección en el mapa. Revísala o usa «Estoy en el parqueo ahora».", "I couldn't find that address on the map. Check it or use «I'm at the parking lot now»."); return@askAddress }
                        SiteStore.add(prefs, SiteStore.Site(tr("Parqueo de la camioneta", "Truck parking"), a, la, lo, kind = "start"))
                        AutoSites.register(this); refresh(); paintAuto()
                        toast("Parqueo guardado.", "Parking lot saved.")
                    }
                },
            )
            choiceDialog(tr("Guardar el parqueo de la camioneta", "Save the truck parking lot"), opts.map { it.first to null }) { w -> opts.getOrNull(w)?.second?.invoke() }
            return
        }
        val opts = listOf(
            tr("Cambiar la dirección", "Change the address") to { editSiteAddress(pk) },
            tr("Usar donde estoy ahora", "Use where I am now") to { moveSiteHere(pk) },
            tr("Agregar otro parqueo", "Add another parking lot") to { saveHere(start = true) },
            tr("Quitar el parqueo", "Remove the parking lot") to { SiteStore.remove(prefs, pk.name); AutoSites.register(this); refresh(); paintAuto() },
        )
        choiceDialog(pk.name, opts.map { it.first to null }) { w -> opts.getOrNull(w)?.second?.invoke() }
    }

    /** ✏️ Cambia la dirección de un sitio (y lo mueve a esa dirección en el mapa). */
    private fun editSiteAddress(s: SiteStore.Site) {
        askAddress(s.name, s.address) { a, la, lo ->
            SiteStore.setAddress(prefs, s.name, a, la, lo)
            AutoSites.register(this); refresh(); paintAuto()
            if (la == null) toast("Guardé la dirección, pero no la encontré en el mapa: el punto del GPS sigue igual.", "I saved the address, but couldn't find it on the map: the GPS point stays the same.")
            else toast("Dirección cambiada.", "Address changed.")
        }
    }

    /** 📍 Pone el sitio donde estás ahora (con su dirección). */
    private fun moveSiteHere(s: SiteStore.Site) {
        if (!WorkdayCenter.hasLocation(this)) { requestPermissions(arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION), 31); return }
        toast("Buscando dónde estás…", "Finding where you are…")
        WorkdayCenter.locate(this) { loc ->
            if (loc == null) { runOnUiThread { toast("No pude saber dónde estás. Prende la ubicación.", "Couldn't get your location. Turn on location.") }; return@locate }
            Thread {
                val a = SiteStore.addressOf(this, loc.latitude, loc.longitude).ifEmpty { s.address }
                runOnUiThread {
                    SiteStore.setAddress(prefs, s.name, a, loc.latitude, loc.longitude)
                    AutoSites.register(this); refresh(); paintAuto()
                    toast("Listo: ahora está donde estás.", "Done: it's now where you are.")
                }
            }.start()
        }
    }

    private fun homeTap() {
        if (SiteStore.home(prefs) != null) {
            val opts = listOf(
                tr("Cambiar la dirección", "Change the address") to {
                    askAddress(tr("Dirección de tu casa", "Your home address"), SiteStore.homeAddress(prefs)) { a, la, lo ->
                        if (la == null || lo == null) { SiteStore.setHomeAddress(prefs, a); paintAuto()
                            toast("Guardé la dirección, pero no la encontré en el mapa: el punto del GPS sigue igual.", "I saved the address, but couldn't find it on the map: the GPS point stays the same."); return@askAddress }
                        SiteStore.setHome(prefs, la, lo, a); AutoSites.register(this); paintAuto()
                        toast("Dirección de tu casa cambiada.", "Home address changed.")
                    }
                },
                tr("Usar donde estoy ahora", "Use where I am now") to { saveHomeHere() },
                tr("Quitar mi casa", "Remove my home") to {
                    AlertDialog.Builder(this)
                        .setMessage(tr("¿Quitar tu casa? Ya no se anotará solo «Llegué a casa».", "Remove your home? «Home» won't be logged automatically anymore."))
                        .setPositiveButton(tr("Quitar", "Remove")) { _, _ -> SiteStore.setHome(prefs, null, null); AutoSites.register(this); paintAuto() }
                        .setNegativeButton(tr("Cancelar", "Cancel"), null)
                        .show()
                },
            )
            choiceDialog(tr("Tu casa", "Your home"), opts.map { it.first to null }) { w -> opts.getOrNull(w)?.second?.invoke() }
            return
        }
        val opts = listOf(
            tr("Estoy en mi casa ahora", "I'm home now") to { saveHomeHere() },
            tr("Escribir la dirección", "Type the address") to {
                askAddress(tr("Dirección de tu casa", "Your home address"), "") { a, la, lo ->
                    if (la == null || lo == null) { toast("No encontré esa dirección en el mapa. Revísala o usa «Estoy en mi casa ahora».", "I couldn't find that address on the map. Check it or use «I'm home now»."); return@askAddress }
                    SiteStore.setHome(prefs, la, lo, a); AutoSites.register(this); paintAuto()
                    toast("Casa guardada.", "Home saved.")
                    if (!AutoSites.ready(this)) toast("Prende «Llegada y salida automáticas» para que se anote solo.", "Turn on «Automatic arrival and departure» so it's logged by itself.")
                }
            },
        )
        choiceDialog(tr("Guardar tu casa", "Save your home"), opts.map { it.first to null }) { w -> opts.getOrNull(w)?.second?.invoke() }
    }

    /** 🏠 Guarda tu casa donde estás ahora (con su dirección). Solo en tu teléfono; en los tickets solo dice «Casa». */
    private fun saveHomeHere() {
        if (!WorkdayCenter.hasLocation(this)) {
            requestPermissions(arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION), 31)
            return
        }
        toast("Buscando dónde estás…", "Finding where you are…")
        WorkdayCenter.locate(this) { loc ->
            if (loc == null) { runOnUiThread { toast("No pude saber dónde estás. Prende la ubicación.", "Couldn't get your location. Turn on location.") }; return@locate }
            Thread {
                val a = SiteStore.addressOf(this, loc.latitude, loc.longitude)
                runOnUiThread {
                    SiteStore.setHome(prefs, loc.latitude, loc.longitude, a)
                    HomeWifi.learn(this) // 📶 el Wi-Fi de tu casa (para la hora exacta en que sales)
                    AutoSites.register(this)
                    paintAuto()
                    toast("Casa guardada.", "Home saved.")
                    if (!AutoSites.ready(this)) toast("Prende «Llegada y salida automáticas» para que se anote solo.", "Turn on «Automatic arrival and departure» so it's logged by itself.")
                }
            }.start()
        }
    }

    private fun paintAuto() {
        val home = SiteStore.home(prefs)
        if (::homeBtn.isInitialized) homeBtn.text = if (home != null) tr("Casa guardada ✓ (toca para cambiarla)", "Home saved ✓ (tap to change it)")
            else tr("Guardar mi casa (para anotar solo «Llegué a casa»)", "Save my home (to log «Home» automatically)")
        if (::homeAddr.isInitialized) {
            val a = SiteStore.homeAddress(prefs)
            homeAddr.text = if (home == null) tr("Puedes guardarla estando en tu casa o escribiendo la dirección.", "You can save it while at home or by typing the address.")
                else tr("Dirección: ", "Address: ") + a.ifEmpty { tr("buscando…", "looking it up…") } + "\n" + tr("Solo se ve aquí, en tu teléfono. En los tickets solo dice «Casa».", "Only shown here, on your phone. Tickets only say «Home».")
            // Las casas guardadas antes no tenían dirección: se busca una vez
            if (home != null && a.isEmpty() && !lookingHome) {
                lookingHome = true
                Thread {
                    val found = SiteStore.addressOf(this, home.first, home.second)
                    runOnUiThread { if (found.isNotEmpty()) { SiteStore.setHomeAddress(prefs, found); paintAuto() } else homeAddr.text = tr("Dirección: (no se encontró; toca el botón para escribirla)", "Address: (not found; tap the button to type it)") }
                }.start()
            }
        }
        val pk = parking()
        if (::parkBtn.isInitialized) parkBtn.text = if (pk != null) tr("Parqueo guardado ✓ (toca para cambiarlo)", "Parking lot saved ✓ (tap to change it)")
            else tr("Guardar el parqueo de la camioneta", "Save the truck parking lot")
        if (::parkAddr.isInitialized) {
            val all = SiteStore.all(prefs).filter { it.isStart }
            parkAddr.text = if (all.isEmpty()) tr("El parqueo es donde recoges y dejas la camioneta: cuando sales de ahí empieza tu día, y cuando vuelves y te quedas, termina.",
                "The parking lot is where you pick up and drop off the truck: when you leave it your day starts, and when you come back and stay, it ends.")
                else all.joinToString("\n") { tr("Dirección: ", "Address: ") + it.address.ifEmpty { tr("(sin dirección)", "(no address)") } + if (all.size > 1) "  (" + it.name + ")" else "" }
        }
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
        if (::gpsBox.isInitialized) { googleOk = null; paintGps() } // 🎯 por si arreglaste algo en los ajustes
    }

    private fun refresh() {
        AutoSites.register(this)
        if (::autoBtn.isInitialized) paintAuto() // 🛻 la dirección del parqueo
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
                (if (s.hasShape) "  ·  " + tr("forma de ${s.shape.size} esquinas", "${s.shape.size}-corner shape") else "  ·  📏 " + SiteStore.radiusOf(prefs, s) + " m" + (if (s.radius > 0) "" else tr(" (la de todos)", " (default)"))) +
                (if (s.lat == 0.0 && s.lon == 0.0) tr("  ·  ⚠️ sin GPS", "  ·  ⚠️ no GPS") else "") +
                SiteLearn.summary(prefs, s.name).let { if (it.isEmpty()) "" else "\n" + it }
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
        val opts = ArrayList<Pair<String, () -> Unit>>()
        opts.add(tr("Distancia de detección (en el mapa)", "Detection distance (on the map)") to { askRadius(s) })
        opts.add((if (s.hasShape) tr("Cambiar la forma del sitio", "Change the site's shape") else tr("Dibujar la forma del sitio", "Draw the site's shape")) to { drawShape(s) })
        opts.add(tr("Cambiar la dirección", "Change the address") to { editSiteAddress(s) })
        opts.add(tr("Usar donde estoy ahora", "Use where I am now") to { moveSiteHere(s) })
        opts.add(tr("Pago por hora en este sitio", "Hourly pay at this site") to { askRate(s) })
        opts.add((if (s.isStart) tr("Cambiar a sitio de trabajo normal", "Change to a normal work site")
            else tr("Usarlo como parqueo de la camioneta (comienzo y fin del día)", "Use it as the truck parking (start and end of day)")) to {
            SiteStore.setKind(prefs, s.name, if (s.isStart) "site" else "start"); refresh()
        })
        if (SiteLearn.moved(prefs, s.name)) opts.add(tr("Volver al punto que guardé yo", "Go back to the point I saved") to { SiteLearn.restorePoint(this, s.name); refresh() })
        choiceDialog(s.name, opts.map { it.first to null }) { w -> opts.getOrNull(w)?.second?.invoke() }
    }

    /**
     * 🔷 Dibuja la forma real del sitio tocando sus esquinas en el mapa (por ejemplo, el edificio y su parqueo).
     * Así una calle o un parqueo de enfrente no cuentan como «llegué».
     */
    private fun drawShape(s: SiteStore.Site) {
        if (s.lat == 0.0 && s.lon == 0.0) { toast("Este lugar no tiene GPS. Guárdalo estando ahí.", "This place has no GPS. Save it while you're there."); return }
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(8), dp(20), 0) }
        val map = MapPreview(this, s.lat, s.lon)
        map.radius = SiteStore.radiusOf(prefs, s)
        map.shape = s.shape
        map.clipToOutline = true
        map.background = GradientDrawable().apply { cornerRadius = dpf(18f); setColor(0xFF1B2230.toInt()) }
        val info = TextView(this).apply { textSize = 13f; setTextColor(SUB); setPadding(0, dp(10), 0, dp(4)); gravity = Gravity.CENTER }
        fun paintInfo(n: Int) {
            info.text = when {
                n == 0 -> tr("Acerca con dos dedos y toca las esquinas del sitio en orden, dando la vuelta (mínimo 3).", "Zoom in with two fingers and tap the site's corners in order, going around (at least 3).")
                n < 3 -> tr("Esquinas: $n. Sigue tocando (mínimo 3).", "Corners: $n. Keep tapping (at least 3).")
                else -> tr("Esquinas: $n. Toca «Guardar» cuando la forma cubra todo el sitio.", "Corners: $n. Tap «Save» when the shape covers the whole site.")
            }
        }
        map.onCorner = { paintInfo(it.size) }
        box.addView(map, LinearLayout.LayoutParams(-1, dp(320)))
        // Acercar, alejar y deshacer la última esquina
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        fun small(label: String, act: () -> Unit) = TextView(this).apply {
            text = label; textSize = 14f; setTextColor(TEXT); gravity = Gravity.CENTER
            setPadding(dp(14), dp(8), dp(14), dp(8))
            background = GradientDrawable().apply { cornerRadius = dpf(16f); setColor(CARD); setStroke(dp(1), LINE) }
            setOnClickListener { act() }
        }
        row.addView(small("−") { map.zoomShift = map.zoomShift - 1 }, LinearLayout.LayoutParams(-2, -2).apply { rightMargin = dp(6) })
        row.addView(small("+") { map.zoomShift = map.zoomShift + 1 }, LinearLayout.LayoutParams(-2, -2).apply { rightMargin = dp(6) })
        row.addView(small(tr("Quitar última", "Remove last")) { map.shape = map.shape.dropLast(1); paintInfo(map.shape.size) }, LinearLayout.LayoutParams(-2, -2))
        box.addView(row, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        box.addView(info)
        paintInfo(map.shape.size)
        AlertDialog.Builder(this)
            .setTitle(s.name)
            .setView(box)
            .setPositiveButton(tr("Guardar", "Save")) { _, _ ->
                if (map.shape.size < 3) { toast("Faltan esquinas: toca al menos 3.", "Not enough corners: tap at least 3."); return@setPositiveButton }
                SiteStore.setShape(prefs, s.name, map.shape); AutoSites.register(this); refresh()
                toast("Forma guardada: ahora solo cuenta si estás adentro de ella.", "Shape saved: now it only counts if you're inside it.")
            }
            .setNeutralButton(tr("Borrar forma", "Delete shape")) { _, _ -> SiteStore.setShape(prefs, s.name, emptyList()); AutoSites.register(this); refresh() }
            .setNegativeButton(tr("Cancelar", "Cancel"), null)
            .show()
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
