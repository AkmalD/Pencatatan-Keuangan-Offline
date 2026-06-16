package id.local.pencatatan

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Rect
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.inputmethod.EditorInfo
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

class MainActivity : Activity() {
    private lateinit var classifier: CategoryClassifier
    private lateinit var store: TransactionStore
    private lateinit var inputField: EditText
    private lateinit var categorySpinner: Spinner
    private lateinit var previewText: TextView
    private lateinit var pieChartView: ExpensePieChartView
    private lateinit var legendContainer: LinearLayout
    private lateinit var recordsContainer: LinearLayout
    private lateinit var contentContainer: LinearLayout
    private lateinit var composerPanel: View
    private val visibleWindowBounds = Rect()

    private val localeId = Locale("id", "ID")
    private val currencyFormatter: NumberFormat by lazy {
        NumberFormat.getCurrencyInstance(localeId).apply { maximumFractionDigits = 0 }
    }
    private val dateFormatter = SimpleDateFormat("dd MMM yyyy, HH:mm", localeId)
    private val categoryColors = mapOf(
        FinanceCategory.FOOD.label to Color.rgb(214, 75, 64),
        FinanceCategory.TRANSPORT.label to Color.rgb(32, 116, 170),
        FinanceCategory.SHOPPING.label to Color.rgb(183, 92, 28),
        FinanceCategory.BILLS.label to Color.rgb(111, 93, 173),
        FinanceCategory.ENTERTAINMENT.label to Color.rgb(200, 74, 132),
        FinanceCategory.HEALTH.label to Color.rgb(34, 145, 115),
        FinanceCategory.TRANSFER.label to Color.rgb(90, 102, 122)
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        classifier = CategoryClassifier.fromAssets(this)
        store = TransactionStore(this)

        buildUi()
        bindInput()
        refreshPreview()
        renderRecords()
    }

    private fun buildUi() {
        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.rgb(245, 247, 250))
        }

        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(245, 247, 250))
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(22), dp(20), dp(16))
            setBackgroundColor(Color.rgb(21, 94, 87))
        }
        header.addView(text("Pencatatan", 24f, Color.WHITE, Typeface.BOLD))
        header.addView(text("Keuangan lokal", 14f, Color.rgb(216, 239, 235), Typeface.NORMAL))
        page.addView(header, matchWrap())

        val scrollView = ScrollView(this)
        contentContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(180))
        }

        contentContainer.addView(text("Pengeluaran", 18f, Color.rgb(32, 39, 48), Typeface.BOLD), block(bottom = 8))
        pieChartView = ExpensePieChartView(this).apply {
            background = roundedRect(Color.WHITE, Color.rgb(226, 231, 236))
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }
        contentContainer.addView(pieChartView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(260)).apply {
            bottomMargin = dp(10)
        })

        legendContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedRect(Color.WHITE, Color.rgb(226, 231, 236))
            setPadding(dp(14), dp(10), dp(14), dp(10))
        }
        contentContainer.addView(legendContainer, block(bottom = 16))

        val historyHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        historyHeader.addView(text("Riwayat", 18f, Color.rgb(32, 39, 48), Typeface.BOLD), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        historyHeader.addView(Button(this).apply {
            text = "Hapus"
            setAllCaps(false)
            minHeight = dp(40)
            setOnClickListener { confirmClear() }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(44)))
        contentContainer.addView(historyHeader, block(bottom = 8))

        recordsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        contentContainer.addView(recordsContainer, matchWrap())

        scrollView.addView(contentContainer)
        page.addView(scrollView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        root.addView(page, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        composerPanel = composerView()
        root.addView(composerPanel, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM
        ).apply {
            leftMargin = dp(12)
            rightMargin = dp(12)
            bottomMargin = dp(48)
        })
        setContentView(root)
        attachComposerPositioning(root)
    }

    private fun composerView(): View {
        val composer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(12))
            background = roundedRect(Color.WHITE, Color.rgb(226, 231, 236))
            elevation = dp(8).toFloat()
        }

        previewText = text("", 13f, Color.rgb(64, 73, 84), Typeface.NORMAL)
        composer.addView(previewText, block(bottom = 6))

        categorySpinner = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@MainActivity,
                android.R.layout.simple_spinner_item,
                FinanceCategory.labels
            ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        }
        composer.addView(categorySpinner, block(bottom = 6))

        val inputRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.BOTTOM
        }

        inputField = EditText(this).apply {
            hint = "beli makan 10 ribu di warteg"
            minLines = 1
            maxLines = 3
            imeOptions = EditorInfo.IME_ACTION_SEND or EditorInfo.IME_FLAG_NO_EXTRACT_UI
            inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or
                InputType.TYPE_TEXT_FLAG_MULTI_LINE
            background = roundedRect(Color.rgb(249, 250, 252), Color.rgb(205, 213, 222))
            setPadding(dp(14), dp(10), dp(14), dp(10))
            textSize = 16f
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_SEND) {
                    sendTransaction()
                    true
                } else {
                    false
                }
            }
        }
        inputRow.addView(inputField, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginEnd = dp(8)
        })

        inputRow.addView(Button(this).apply {
            text = "Kirim"
            setAllCaps(false)
            minHeight = dp(48)
            setOnClickListener { sendTransaction() }
        }, LinearLayout.LayoutParams(dp(88), ViewGroup.LayoutParams.WRAP_CONTENT))

        composer.addView(inputRow, matchWrap())
        return composer
    }

    private fun attachComposerPositioning(root: FrameLayout) {
        composerPanel.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            updateContentBottomPadding(currentComposerBottomMargin())
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            root.setOnApplyWindowInsetsListener { _, insets ->
                val imeInsets = insets.getInsets(WindowInsets.Type.ime()).bottom
                val navInsets = insets.getInsets(WindowInsets.Type.navigationBars()).bottom
                val imeVisible = insets.isVisible(WindowInsets.Type.ime())
                val rootWasResized = (window.decorView.height - root.height) > dp(80)
                val bottomOffset = when {
                    imeVisible && rootWasResized -> dp(8)
                    imeVisible -> imeInsets + dp(8)
                    else -> navInsets.coerceAtLeast(dp(40)) + dp(8)
                }
                updateComposerBottomOffset(bottomOffset)
                insets
            }
            root.post { root.requestApplyInsets() }
            return
        }

        root.viewTreeObserver.addOnGlobalLayoutListener {
            root.getWindowVisibleDisplayFrame(visibleWindowBounds)
            val hiddenBottom = (root.rootView.height - visibleWindowBounds.bottom).coerceAtLeast(0)
            val keyboardVisible = hiddenBottom > (root.rootView.height * 0.15f)
            val rootWasResized = (window.decorView.height - root.height) > dp(80)
            val bottomOffset = if (keyboardVisible) {
                if (rootWasResized) dp(8) else hiddenBottom + dp(8)
            } else {
                hiddenBottom.coerceAtLeast(dp(40)) + dp(8)
            }

            updateComposerBottomOffset(bottomOffset)
        }
    }

    private fun updateComposerBottomOffset(bottomOffset: Int) {
        val params = composerPanel.layoutParams as FrameLayout.LayoutParams
        if (params.bottomMargin != bottomOffset) {
            params.bottomMargin = bottomOffset
            composerPanel.layoutParams = params
        }
        updateContentBottomPadding(bottomOffset)
    }

    private fun updateContentBottomPadding(bottomOffset: Int) {
        val composerHeight = composerPanel.height.takeIf { it > 0 } ?: dp(150)
        val contentBottomPadding = composerHeight + bottomOffset + dp(16)
        if (contentContainer.paddingBottom != contentBottomPadding) {
            contentContainer.setPadding(
                contentContainer.paddingLeft,
                contentContainer.paddingTop,
                contentContainer.paddingRight,
                contentBottomPadding
            )
        }
    }

    private fun currentComposerBottomMargin(): Int {
        return (composerPanel.layoutParams as? FrameLayout.LayoutParams)?.bottomMargin ?: dp(48)
    }

    private fun bindInput() {
        inputField.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                refreshPreview()
            }
        })
    }

    private fun refreshPreview() {
        val message = inputField.text.toString().trim()
        if (message.isBlank()) {
            previewText.text = "Nominal: -"
            return
        }

        val classification = classifier.classify(message)
        selectCategory(classification.category)

        val amountText = TransactionTextParser.parseAmount(message)?.let { formatCurrency(it) } ?: "belum terbaca"
        val place = TransactionTextParser.extractPlace(message)
        val placeText = place?.let { " - $it" }.orEmpty()
        val percent = (classification.confidence * 100).roundToInt()
        previewText.text = "Nominal: $amountText$placeText - Deteksi $percent%"
    }

    private fun selectCategory(category: String) {
        val index = FinanceCategory.labels.indexOf(category)
        if (index >= 0 && categorySpinner.selectedItemPosition != index) {
            categorySpinner.setSelection(index)
        }
    }

    private fun sendTransaction() {
        val message = inputField.text.toString().trim()
        if (message.isBlank()) {
            Toast.makeText(this, "Transaksi masih kosong", Toast.LENGTH_SHORT).show()
            return
        }

        val amount = TransactionTextParser.parseAmount(message)
        if (amount == null) {
            Toast.makeText(this, "Nominal belum terbaca", Toast.LENGTH_SHORT).show()
            return
        }

        val now = System.currentTimeMillis()
        val category = categorySpinner.selectedItem?.toString() ?: classifier.classify(message).category
        store.add(
            TransactionRecord(
                id = now,
                message = message,
                category = category,
                amount = amount,
                place = TransactionTextParser.extractPlace(message),
                createdAt = now
            )
        )

        inputField.setText("")
        renderRecords()
        Toast.makeText(this, "Terkirim", Toast.LENGTH_SHORT).show()
    }

    private fun confirmClear() {
        if (store.all().isEmpty()) {
            Toast.makeText(this, "Belum ada catatan", Toast.LENGTH_SHORT).show()
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Hapus semua catatan?")
            .setMessage("Data lokal di perangkat ini akan dihapus.")
            .setPositiveButton("Hapus") { _, _ ->
                store.clear()
                renderRecords()
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun renderRecords() {
        val records = store.all().sortedByDescending { it.createdAt }
        val income = records.filter { FinanceCategory.isIncome(it.category) }.sumOf { it.amount }
        val expenseRecords = records.filterNot { FinanceCategory.isIncome(it.category) }
        val expense = expenseRecords.sumOf { it.amount }
        val slices = expenseRecords
            .groupBy { it.category }
            .mapValues { entry -> entry.value.sumOf { it.amount } }
            .filterValues { it > 0L }
            .entries
            .sortedByDescending { it.value }
            .map { entry ->
                ExpenseSlice(
                    label = entry.key,
                    value = entry.value,
                    color = categoryColors[entry.key] ?: Color.rgb(90, 102, 122)
                )
            }

        pieChartView.setChartData(
            slices = slices,
            centerLines = listOf(
                CenterTextLine("Masuk", Color.rgb(87, 100, 117), 11f, Typeface.NORMAL),
                CenterTextLine(compactCurrency(income), Color.rgb(22, 128, 82), 14f, Typeface.BOLD),
                CenterTextLine("Keluar", Color.rgb(87, 100, 117), 11f, Typeface.NORMAL),
                CenterTextLine(compactCurrency(expense), Color.rgb(190, 61, 47), 14f, Typeface.BOLD)
            )
        )
        renderLegend(slices, expense)

        recordsContainer.removeAllViews()
        if (records.isEmpty()) {
            recordsContainer.addView(text("Belum ada transaksi", 14f, Color.rgb(98, 109, 124), Typeface.NORMAL))
            return
        }

        records.forEach { record ->
            recordsContainer.addView(recordRow(record), block(bottom = 10))
        }
    }

    private fun renderLegend(slices: List<ExpenseSlice>, totalExpense: Long) {
        legendContainer.removeAllViews()
        if (slices.isEmpty()) {
            legendContainer.addView(text("Belum ada pengeluaran", 14f, Color.rgb(98, 109, 124), Typeface.NORMAL))
            return
        }

        slices.forEach { slice ->
            val percent = (slice.value.toDouble() / totalExpense * 100).roundToInt()
            legendContainer.addView(legendRow(slice, percent), block(bottom = 6))
        }
    }

    private fun legendRow(slice: ExpenseSlice, percent: Int): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        row.addView(View(this).apply {
            background = roundedRect(slice.color, slice.color)
        }, LinearLayout.LayoutParams(dp(12), dp(12)).apply {
            marginEnd = dp(8)
        })
        row.addView(text(slice.label, 14f, Color.rgb(42, 50, 61), Typeface.NORMAL), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(text("${formatCurrency(slice.value)} ($percent%)", 13f, Color.rgb(65, 77, 91), Typeface.BOLD))
        return row
    }

    private fun recordRow(record: TransactionRecord): View {
        val isIncome = FinanceCategory.isIncome(record.category)
        val amountColor = if (isIncome) Color.rgb(22, 128, 82) else Color.rgb(190, 61, 47)
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedRect(Color.WHITE, Color.rgb(226, 231, 236))
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }

        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        top.addView(text(record.category, 15f, Color.rgb(32, 39, 48), Typeface.BOLD), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        top.addView(text(formatCurrency(record.amount), 15f, amountColor, Typeface.BOLD))
        row.addView(top, matchWrap())

        if (!record.place.isNullOrBlank()) {
            row.addView(text(record.place, 13f, Color.rgb(65, 77, 91), Typeface.NORMAL), topSpace(4))
        }
        row.addView(text(record.message, 13f, Color.rgb(65, 77, 91), Typeface.NORMAL), topSpace(4))
        row.addView(text(dateFormatter.format(Date(record.createdAt)), 12f, Color.rgb(116, 127, 142), Typeface.NORMAL), topSpace(6))
        return row
    }

    private fun formatCurrency(value: Long): String = currencyFormatter.format(value)

    private fun compactCurrency(value: Long): String {
        val absolute = kotlin.math.abs(value)
        val prefix = if (value < 0) "-Rp" else "Rp"
        return when {
            absolute >= 1_000_000_000L -> "$prefix${compactNumber(absolute / 1_000_000_000.0)} M"
            absolute >= 1_000_000L -> "$prefix${compactNumber(absolute / 1_000_000.0)} jt"
            absolute >= 1_000L -> "$prefix${compactNumber(absolute / 1_000.0)} rb"
            else -> "$prefix$absolute"
        }
    }

    private fun compactNumber(value: Double): String {
        return if (value >= 10 || value % 1.0 == 0.0) {
            value.roundToInt().toString()
        } else {
            String.format(localeId, "%.1f", value)
        }
    }

    private fun text(value: String, size: Float, color: Int, style: Int): TextView {
        return TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            typeface = Typeface.create(Typeface.DEFAULT, style)
            includeFontPadding = true
        }
    }

    private fun roundedRect(fill: Int, stroke: Int): GradientDrawable {
        return GradientDrawable().apply {
            setColor(fill)
            cornerRadius = dp(8).toFloat()
            setStroke(dp(1), stroke)
        }
    }

    private fun matchWrap(): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    private fun block(bottom: Int = 0): LinearLayout.LayoutParams {
        return matchWrap().apply { bottomMargin = dp(bottom) }
    }

    private fun topSpace(top: Int): LinearLayout.LayoutParams {
        return matchWrap().apply { topMargin = dp(top) }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).roundToInt()
    }
}
