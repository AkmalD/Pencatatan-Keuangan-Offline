package id.local.pencatatan

import org.json.JSONObject

data class TransactionRecord(
    val id: Long,
    val message: String,
    val category: String,
    val amount: Long,
    val place: String?,
    val createdAt: Long
) {
    fun toJson(): JSONObject {
        return JSONObject()
            .put("id", id)
            .put("message", message)
            .put("category", category)
            .put("amount", amount)
            .put("place", place ?: "")
            .put("createdAt", createdAt)
    }

    companion object {
        fun fromJson(json: JSONObject): TransactionRecord {
            val rawPlace = json.optString("place", "")
            return TransactionRecord(
                id = json.getLong("id"),
                message = json.getString("message"),
                category = json.getString("category"),
                amount = json.getLong("amount"),
                place = rawPlace.takeIf { it.isNotBlank() },
                createdAt = json.getLong("createdAt")
            )
        }
    }
}

