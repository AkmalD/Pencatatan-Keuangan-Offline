package id.local.pencatatan

enum class FinanceCategory(val label: String) {
    FOOD("Makan & Minum"),
    TRANSPORT("Transport"),
    SHOPPING("Belanja"),
    BILLS("Tagihan"),
    ENTERTAINMENT("Hiburan"),
    INCOME("Gaji/Pemasukan"),
    HEALTH("Kesehatan"),
    TRANSFER("Transfer");

    companion object {
        val labels: List<String> = values().map { it.label }

        fun fromLabel(label: String): FinanceCategory {
            return values().firstOrNull { it.label.equals(label, ignoreCase = true) } ?: SHOPPING
        }

        fun isIncome(label: String): Boolean = fromLabel(label) == INCOME
    }
}

