package ro.solarcasa.ui

import java.text.NumberFormat
import java.util.Locale

private val ro: Locale = Locale.forLanguageTag("ro-RO")

/** Romanian number formats: decimal comma, dot for thousands ("6.800", "4,8"). */
fun Double.fmt(digits: Int = 1): String = "%.${digits}f".format(ro, this)

fun Number.grouped(): String = NumberFormat.getIntegerInstance(ro).format(this.toLong())

/** A no-break space keeps the number and its unit on the same line ("1.045 kWh"). */
private const val NBSP = "\u00A0"

fun Double.kw(): String = "${fmt()}${NBSP}kW"

fun Number.kwh(): String = "${grouped()}${NBSP}kWh"

fun Number.lei(): String = "${grouped()}${NBSP}lei"

fun Number.eur(): String = "${grouped()}${NBSP}€"

fun Int.signedPercent(): String = if (this > 0) "+$this%" else "$this%"
