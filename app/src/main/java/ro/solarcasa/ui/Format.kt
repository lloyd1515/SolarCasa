package ro.solarcasa.ui

import java.text.NumberFormat
import java.util.Locale

private val ro: Locale = Locale.forLanguageTag("ro-RO")

/** Romanian number formats: decimal comma, dot for thousands ("6.800", "4,8"). */
fun Double.dec(digits: Int = 1): String = "%.${digits}f".format(ro, this)

fun Number.grouped(): String = NumberFormat.getIntegerInstance(ro).format(this.toLong())

fun Double.kw(): String = "${dec()} kW"

fun Number.kwh(): String = "${grouped()} kWh"

fun Number.lei(): String = "${grouped()} lei"

fun Number.eur(): String = "${grouped()} €"

fun Int.signedPercent(): String = if (this > 0) "+$this%" else "$this%"
