package id.local.pencatatan

import android.content.Context
import org.json.JSONArray

class TransactionStore(context: Context) {
    private val preferences = context.getSharedPreferences("local_finance_records", Context.MODE_PRIVATE)

    fun all(): List<TransactionRecord> {
        val raw = preferences.getString(KEY_RECORDS, "[]") ?: "[]"
        val array = JSONArray(raw)
        return (0 until array.length()).map { index ->
            TransactionRecord.fromJson(array.getJSONObject(index))
        }
    }

    fun add(record: TransactionRecord) {
        save(all() + record)
    }

    fun clear() {
        preferences.edit().remove(KEY_RECORDS).apply()
    }

    private fun save(records: List<TransactionRecord>) {
        val array = JSONArray()
        records.forEach { array.put(it.toJson()) }
        preferences.edit().putString(KEY_RECORDS, array.toString()).apply()
    }

    companion object {
        private const val KEY_RECORDS = "records"
    }
}

