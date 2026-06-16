package id.local.pencatatan

import java.util.Locale
import kotlin.math.roundToLong

object TransactionTextParser {
    private val amountRegex = Regex(
        pattern = """(?:rp\s*)?(\d+(?:[.,]\d+)*)\s*(ribu|rb|rbu|k|juta|jt|mio|miliar|milyar|rupiah|idr)?""",
        options = setOf(RegexOption.IGNORE_CASE)
    )
    private val placeRegex = Regex("""\b(?:di|ke|dari)\s+(.+)$""", RegexOption.IGNORE_CASE)

    fun parseAmount(text: String): Long? {
        return amountRegex.findAll(text)
            .mapNotNull { match -> parseCandidate(match.groupValues[1], match.groupValues.getOrNull(2)) }
            .firstOrNull()
    }

    fun extractPlace(text: String): String? {
        val rawPlace = placeRegex.find(text)?.groupValues?.getOrNull(1) ?: return null
        val withoutAmount = amountRegex.replace(rawPlace, " ")
        return withoutAmount
            .replace(Regex("""\s+"""), " ")
            .trim(' ', ',', '.', '-')
            .takeIf { it.isNotBlank() }
    }

    private fun parseCandidate(rawNumber: String, rawSuffix: String?): Long? {
        val suffix = rawSuffix.orEmpty().lowercase(Locale("id", "ID")).ifBlank { null }
        val number = parseLocalizedNumber(rawNumber, hasSuffix = suffix != null) ?: return null
        val multiplier = when (suffix) {
            "ribu", "rb", "rbu", "k" -> 1_000.0
            "juta", "jt", "mio" -> 1_000_000.0
            "miliar", "milyar" -> 1_000_000_000.0
            else -> 1.0
        }
        val amount = (number * multiplier).roundToLong()
        return amount.takeIf { suffix != null || it >= 1_000L }
    }

    private fun parseLocalizedNumber(raw: String, hasSuffix: Boolean): Double? {
        val normalized = when {
            raw.contains(".") && raw.contains(",") -> raw.replace(".", "").replace(",", ".")
            raw.contains(".") -> normalizeSingleSeparator(raw, ".", hasSuffix)
            raw.contains(",") -> normalizeSingleSeparator(raw, ",", hasSuffix)
            else -> raw
        }
        return normalized.toDoubleOrNull()
    }

    private fun normalizeSingleSeparator(raw: String, separator: String, hasSuffix: Boolean): String {
        val parts = raw.split(separator)
        val lastPart = parts.lastOrNull().orEmpty()
        val looksLikeThousands = parts.size > 2 || lastPart.length == 3
        return when {
            looksLikeThousands -> raw.replace(separator, "")
            separator == "," -> raw.replace(",", ".")
            hasSuffix -> raw
            else -> raw
        }
    }
}

