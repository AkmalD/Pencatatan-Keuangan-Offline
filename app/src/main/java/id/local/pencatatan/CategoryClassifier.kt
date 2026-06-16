package id.local.pencatatan

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import kotlin.math.exp
import kotlin.math.ln

data class ClassificationResult(
    val category: String,
    val confidence: Double
)

class CategoryClassifier private constructor(
    private val categories: List<String>,
    private val classLogPriors: Map<String, Double>,
    private val unknownLogProbabilities: Map<String, Double>,
    private val tokenLogProbabilities: Map<String, Map<String, Double>>,
    private val aliases: Map<String, String>,
    private val stopWords: Set<String>
) {
    fun classify(text: String): ClassificationResult {
        val tokens = tokenize(text)
        if (tokens.isEmpty()) {
            return ClassificationResult(FinanceCategory.SHOPPING.label, 0.0)
        }

        val tokenCounts = tokens.groupingBy { it }.eachCount()
        val scores = categories.associateWith { category ->
            val prior = classLogPriors[category] ?: ln(1.0 / categories.size)
            tokenCounts.entries.fold(prior) { score, (token, count) ->
                val tokenScore = tokenLogProbabilities[category]?.get(token)
                    ?: unknownLogProbabilities[category]
                    ?: -20.0
                score + (tokenScore * count)
            }
        }

        val best = scores.maxByOrNull { it.value } ?: return ClassificationResult(FinanceCategory.SHOPPING.label, 0.0)
        return ClassificationResult(best.key, confidenceFor(scores, best.value))
    }

    private fun tokenize(text: String): List<String> {
        return text.lowercase(Locale("id", "ID"))
            .replace(Regex("""(?<=\d)(?=[a-z])|(?<=[a-z])(?=\d)"""), " ")
            .replace(Regex("[^a-z0-9]+"), " ")
            .split(Regex("""\s+"""))
            .mapNotNull { rawToken ->
                val token = aliases[rawToken] ?: rawToken
                token.takeIf {
                    it.length > 1 &&
                        it !in stopWords &&
                        it.any { char -> char.isLetter() }
                }
            }
    }

    private fun confidenceFor(scores: Map<String, Double>, winningScore: Double): Double {
        val denominator = scores.values.sumOf { exp(it - winningScore) }
        return if (denominator == 0.0) 0.0 else 1.0 / denominator
    }

    companion object {
        fun fromAssets(context: Context, assetName: String = "category_model.json"): CategoryClassifier {
            val rawModel = context.assets.open(assetName).bufferedReader().use { it.readText() }
            val json = JSONObject(rawModel)
            val categories = json.getJSONArray("categories").toStringList()
            val tokenRoot = json.getJSONObject("token_log_probs")
            val tokenScores = categories.associateWith { category ->
                tokenRoot.getJSONObject(category).toDoubleMap()
            }

            return CategoryClassifier(
                categories = categories,
                classLogPriors = json.getJSONObject("class_log_priors").toDoubleMap(),
                unknownLogProbabilities = json.getJSONObject("unknown_log_probs").toDoubleMap(),
                tokenLogProbabilities = tokenScores,
                aliases = json.optJSONObject("aliases")?.toStringMap() ?: emptyMap(),
                stopWords = json.optJSONArray("stop_words")?.toStringList()?.toSet() ?: emptySet()
            )
        }
    }
}

private fun JSONArray.toStringList(): List<String> {
    return (0 until length()).map { index -> getString(index) }
}

private fun JSONObject.toDoubleMap(): Map<String, Double> {
    val result = mutableMapOf<String, Double>()
    val iterator = keys()
    while (iterator.hasNext()) {
        val key = iterator.next()
        result[key] = getDouble(key)
    }
    return result
}

private fun JSONObject.toStringMap(): Map<String, String> {
    val result = mutableMapOf<String, String>()
    val iterator = keys()
    while (iterator.hasNext()) {
        val key = iterator.next()
        result[key] = getString(key)
    }
    return result
}
