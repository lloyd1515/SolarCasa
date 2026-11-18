package ro.solarcasa.data

import java.text.Normalizer
import kotlin.math.roundToInt

/**
 * Simulated AI ("Asistentul Solar"). It works offline on the demo data.
 * Design rule (Microsoft HAX G2, G9, G11): every answer shows why, with a confidence level,
 * and nothing runs without the user's confirmation.
 */
object SolarAssistant {
    const val CLOUDY_CONDITION = "Prognoza pentru mâine: zi înnorată (mai puțin de o treime din producția normală)"
    const val RAIN_CONDITION = "Prognoza pentru mâine: ploaie"
    const val COLD_CONDITION = "Temperatura exterioară sub 0 °C"
    const val HEAT_PUMP = "Pompa de căldură (split living)"

    data class Tip(val title: String, val text: String, val why: String)

    fun homeTip(h: House): Tip {
        val surplus = EnergyMath.surplusKw(h)
        val washer = h.appliances.first { it.id == "washer" }
        return when {
            h.gridDown -> Tip("Mod urgență", "Casa funcționează pe baterie.", "Rețeaua nu răspunde de la 20:15.")
            surplus >= washer.kw && washer.state == RunState.Off -> Tip(
                "Poți porni mașina de spălat",
                "Acum e momentul bun pentru aparatele mari. Surplusul de ${fmt(surplus)} kW mai durează ≈ 3 ore " +
                    "(prognoză: senin până la ${h.surplusUntil}).",
                "Prognoza meteo pentru Cluj-Napoca: senin până la ${h.surplusUntil} (încredere 86%). " +
                    "Consumul casei la această oră este ${fmt(EnergyMath.houseLoad(h))} kW, ca în ultimele 4 sâmbete.",
            )
            surplus > 0.3 -> Tip(
                "Ai surplus",
                "Produci cu ${fmt(surplus)} kW mai mult decât consumi. Bateria se încarcă.",
                "Măsurătorile invertorului din ultimul minut.",
            )
            else -> Tip(
                "Cel mai bun moment azi: 12:30 – 14:30",
                "Acum consumi mai mult decât produci. Între 12:30 și 14:30 energia pentru aparatele mari este gratuită.",
                "Prognoza meteo arată soare între 12:00 și 15:00 (încredere 71%).",
            )
        }
    }

    /** "Ce pot porni?": how an appliance fits in the current surplus. */
    fun fit(a: Appliance, surplusKw: Double): String = when {
        surplusKw >= a.kw -> "încape în surplus ✓"
        surplusKw > 0.3 -> "parțial din rețea (≈ ${fmt((a.kw - surplusKw) * a.runHours * EnergyMath.PRICE_LEI_PER_KWH)} lei)"
        else -> "acum ar folosi rețeaua (≈ ${fmt(a.kw * a.runHours * EnergyMath.PRICE_LEI_PER_KWH)} lei)"
    }

    sealed interface Reply {
        data class Draft(val draft: RuleDraft, val summary: String) : Reply
        data class Clarify(val question: String, val options: List<String>) : Reply
        data class Answer(val text: String) : Reply
    }

    /** Interprets a text or voice command. A rule is only proposed, never saved directly. */
    fun interpret(command: String, h: House): Reply {
        val c = plain(command)
        val temperature = Regex("""(\d{2})\s*(°|grade)""").find(c)?.groupValues?.get(1)?.toInt() ?: 20
        fun draft(condition: String) = RuleDraft(condition, HEAT_PUMP, temperature, byAssistant = true).let {
            Reply.Draft(it, "DACĂ ${it.condition} ATUNCI ${it.device} → mod economic (${it.temperature} °C), ${it.from} – ${it.to}")
        }
        return when {
            "vecin" in c -> Reply.Answer(
                "Nu pot folosi energia altei case. Pot crea o regulă care păstrează bateria ta pentru zilele înnorate.",
            )
            "urat" in c -> Reply.Clarify(
                "Te referi la cer înnorat, ploaie sau temperatură scăzută?",
                listOf("Cer înnorat", "Ploaie", "Temperatură scăzută"),
            )
            listOf("innorat", "nor").any { it in c } -> draft(CLOUDY_CONDITION)
            "ploaie" in c || "ploua" in c -> draft(RAIN_CONDITION)
            "frig" in c || "inghet" in c -> draft(COLD_CONDITION)
            "pornesc" in c || "pot porni" in c -> {
                val surplus = EnergyMath.surplusKw(h)
                Reply.Answer(h.appliances.joinToString("\n") { "${it.name}: ${fit(it, surplus)}" })
            }
            "baterie" in c || "bateria" in c -> {
                val hours = EnergyMath.autonomyHours(h.batteryPercent, h.reservePercent, EnergyMath.houseLoad(h))
                Reply.Answer("Bateria are ${h.batteryPercent}%. La consumul actual ajunge pentru ${hours(hours)}.")
            }
            else -> Reply.Answer(
                "Pot crea reguli (de exemplu: „Când mâine e înnorat, pune încălzirea pe economic”), " +
                    "îți pot spune ce aparate poți porni sau cât mai ține bateria.",
            )
        }
    }

    fun clarified(option: String, h: House): Reply = interpret(
        when (option) {
            "Ploaie" -> "ploaie"
            "Temperatură scăzută" -> "frig"
            else -> "innorat"
        },
        h,
    )

    data class Diagnosis(val cause: String, val confidence: Int, val steps: List<String>)

    fun diagnosis(alertId: String): Diagnosis = when (alertId) {
        "string2" -> Diagnosis(
            "Panourile din șirul 2 produc 0 W de la ora 10:12. Șirul 1 funcționează normal. " +
                "Posibil: siguranță declanșată sau conector defect.",
            82,
            listOf(
                "Verifică siguranța de curent continuu din tabloul invertorului (vezi schița).",
                "Dacă problema persistă, contactează service-ul.",
            ),
        )
        else -> Diagnosis(
            "Praf pe panouri. Nu a plouat de 36 de zile, iar producția scade la fel pe ambele șiruri.",
            74,
            listOf("Așteaptă ploaia prognozată peste 2 zile (pierdere estimată: 6 lei).", "Sau programează curățarea (≈ 250 lei)."),
        )
    }

    const val REPORT_EXPLANATION =
        "Principalele cauze: producția solară a fost cu 41% mai mică (zile mai scurte, 9 zile înnorate) " +
            "și pompa de căldură a funcționat cu 60% mai mult. Consumul celorlalte aparate a rămas constant."

    const val BOILER_FINDING =
        "Boilerul încălzește apa noaptea, între 01:00 și 05:00, deși nu folosiți apă caldă atunci. " +
            "Probabil termostatul a fost resetat după pana de curent din 28.10. Cost suplimentar: ≈ 58 lei/lună."

    const val BOILER_PROPOSAL = "Încălzire doar între 11:00 și 15:00 (din soare) și între 18:00 și 19:00."

    fun hours(h: Double): String {
        if (h.isInfinite()) return "mult timp"
        val minutes = (h * 6).roundToInt() * 10
        return when {
            minutes < 60 -> "≈ $minutes min"
            minutes < 240 -> "≈ ${minutes / 60} h" + if (minutes % 60 != 0) " ${minutes % 60} min" else ""
            else -> "≈ ${h.roundToInt()} ore"
        }
    }

    private fun fmt(x: Double) = "%.1f".format(java.util.Locale.forLanguageTag("ro-RO"), x)

    private fun plain(s: String) =
        Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
}
