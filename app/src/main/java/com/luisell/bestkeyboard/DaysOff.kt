package com.luisell.bestkeyboard

import android.content.SharedPreferences
import java.util.Calendar

/**
 * 🗓️ Días libres y días festivos federales de Estados Unidos.
 *  - Puedes marcar cualquier día como «No trabajo» (o «Sí trabajo», aunque sea fin de semana o festivo).
 *  - Los festivos federales salen en el calendario; por defecto cuentan como día libre (se puede cambiar).
 * En un día libre Mi jornada no anota nada solo, no te avisa ni te pregunta.
 */
object DaysOff {
    const val OFF = "off"
    const val WORK = "work"
    const val PREF_HOLIDAYS_OFF = "holidays_off"

    fun key(t: Long) = Plan.key(t)

    /** Lo que tú marcaste para ese día: [OFF], [WORK] o null. */
    fun mark(p: SharedPreferences, t: Long): String? = p.getString("dayset_" + key(t), null)

    fun setMark(p: SharedPreferences, t: Long, v: String?) {
        val e = p.edit()
        if (v == null) e.remove("dayset_" + key(t)) else e.putString("dayset_" + key(t), v)
        e.apply()
    }

    fun holidaysOff(p: SharedPreferences) = p.getBoolean(PREF_HOLIDAYS_OFF, true)

    /** ¿Ese día no trabajas? (lo marcaste, o es festivo). null = no se sabe (depende de tus días de trabajo). */
    fun isOff(p: SharedPreferences, t: Long): Boolean? = when (mark(p, t)) {
        OFF -> true
        WORK -> false
        else -> if (holidaysOff(p) && holiday(t) != null) true else null
    }

    // ---------------------------------------------------------------- 🇺🇸 Festivos federales

    class Holiday(val es: String, val en: String, val observed: Boolean = false) {
        fun name(): String = L.t(es, en) + if (observed) L.t(" (se observa)", " (observed)") else ""
    }

    private val cache = HashMap<Int, Map<String, Holiday>>()

    /** El festivo de ese día, o null. */
    fun holiday(t: Long): Holiday? {
        val y = Calendar.getInstance().apply { timeInMillis = t }.get(Calendar.YEAR)
        return forYear(y)[key(t)] ?: forYear(y + 1)[key(t)] // el 31 de dic. puede ser el Año Nuevo observado
    }

    fun forYear(y: Int): Map<String, Holiday> = synchronized(cache) {
        cache.getOrPut(y) {
            val m = LinkedHashMap<String, Holiday>()
            fun fixed(month: Int, d: Int, h: Holiday) {
                val c = cal(y, month, d)
                m[key(c.timeInMillis)] = h
                // Cae sábado: se descansa el viernes; domingo: el lunes
                when (c.get(Calendar.DAY_OF_WEEK)) {
                    Calendar.SATURDAY -> { c.add(Calendar.DAY_OF_YEAR, -1); m[key(c.timeInMillis)] = Holiday(h.es, h.en, true) }
                    Calendar.SUNDAY -> { c.add(Calendar.DAY_OF_YEAR, 1); m[key(c.timeInMillis)] = Holiday(h.es, h.en, true) }
                }
            }
            fun nth(month: Int, dow: Int, n: Int, h: Holiday) {
                val c = cal(y, month, 1)
                while (c.get(Calendar.DAY_OF_WEEK) != dow) c.add(Calendar.DAY_OF_YEAR, 1)
                c.add(Calendar.DAY_OF_YEAR, 7 * (n - 1))
                m[key(c.timeInMillis)] = h
            }
            fun last(month: Int, dow: Int, h: Holiday) {
                val c = cal(y, month, 1)
                c.set(Calendar.DAY_OF_MONTH, c.getActualMaximum(Calendar.DAY_OF_MONTH))
                while (c.get(Calendar.DAY_OF_WEEK) != dow) c.add(Calendar.DAY_OF_YEAR, -1)
                m[key(c.timeInMillis)] = h
            }
            fixed(Calendar.JANUARY, 1, Holiday("Año Nuevo", "New Year's Day"))
            nth(Calendar.JANUARY, Calendar.MONDAY, 3, Holiday("Día de Martin Luther King Jr.", "Martin Luther King Jr. Day"))
            nth(Calendar.FEBRUARY, Calendar.MONDAY, 3, Holiday("Día de los Presidentes", "Presidents' Day"))
            last(Calendar.MAY, Calendar.MONDAY, Holiday("Día de los Caídos (Memorial Day)", "Memorial Day"))
            fixed(Calendar.JUNE, 19, Holiday("Juneteenth", "Juneteenth"))
            fixed(Calendar.JULY, 4, Holiday("Día de la Independencia", "Independence Day"))
            nth(Calendar.SEPTEMBER, Calendar.MONDAY, 1, Holiday("Día del Trabajo (Labor Day)", "Labor Day"))
            nth(Calendar.OCTOBER, Calendar.MONDAY, 2, Holiday("Día de Colón", "Columbus Day"))
            fixed(Calendar.NOVEMBER, 11, Holiday("Día de los Veteranos", "Veterans Day"))
            nth(Calendar.NOVEMBER, Calendar.THURSDAY, 4, Holiday("Día de Acción de Gracias", "Thanksgiving Day"))
            fixed(Calendar.DECEMBER, 25, Holiday("Navidad", "Christmas Day"))
            m
        }
    }

    private fun cal(y: Int, month: Int, d: Int) = Calendar.getInstance().apply {
        clear(); set(y, month, d, 12, 0, 0)
    }
}
