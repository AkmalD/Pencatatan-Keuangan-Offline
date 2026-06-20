package id.local.pencatatan

import android.app.Activity
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.content.Intent
import android.graphics.Rect
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
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
import android.widget.AdapterView
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import java.text.DateFormatSymbols
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
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
    private lateinit var dateFilterText: TextView
    private lateinit var monthlyPeriodText: TextView
    private lateinit var monthlyExpenseText: TextView
    private lateinit var historyCategorySpinner: Spinner
    private lateinit var historySortSpinner: Spinner
    private val visibleWindowBounds = Rect()
    private var selectedDateStart: Long? = null
    private var selectedDateEnd: Long? = null
    private var monthlyReferenceTime: Long = System.currentTimeMillis()
    private var pendingExportRequest: ExportRequest? = null
    private val historyCategoryOptions = listOf(HISTORY_ALL_CATEGORIES) + FinanceCategory.labels
    private val historySortOptions = listOf(SORT_NEWEST, SORT_AMOUNT_DESC)

    private val localeId = Locale("id", "ID")
    private val currencyFormatter: NumberFormat by lazy {
        NumberFormat.getCurrencyInstance(localeId).apply { maximumFractionDigits = 0 }
    }
    private val dateFormatter = SimpleDateFormat("dd MMM yyyy, HH:mm", localeId)
    private val dateOnlyFormatter = SimpleDateFormat("dd MMM yyyy", localeId)
    private val monthFormatter = SimpleDateFormat("MMMM yyyy", localeId)
    private val fileDayFormatter = SimpleDateFormat("yyyyMMdd", Locale.US)
    private val fileMonthFormatter = SimpleDateFormat("yyyyMM", Locale.US)
    private val categoryColors = mapOf(
        FinanceCategory.FOOD.label to Color.rgb(214, 75, 64),
        FinanceCategory.TRANSPORT.label to Color.rgb(32, 116, 170),
        FinanceCategory.SHOPPING.label to Color.rgb(183, 92, 28),
        FinanceCategory.BILLS.label to Color.rgb(111, 93, 173),
        FinanceCategory.ENTERTAINMENT.label to Color.rgb(200, 74, 132),
        FinanceCategory.HEALTH.label to Color.rgb(34, 145, 115),
        FinanceCategory.TRANSFER.label to Color.rgb(90, 102, 122)
    )

    private enum class ExportReportType {
        DAILY,
        MONTHLY
    }

    private data class ExportRequest(
        val type: ExportReportType,
        val title: String,
        val periodLabel: String,
        val fileName: String,
        val startMillis: Long,
        val endMillis: Long
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        classifier = CategoryClassifier.fromAssets(this)
        store = TransactionStore(this)
        setTodayDateFilter(updateUi = false)

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
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(22), dp(20), dp(16))
            setBackgroundColor(Color.rgb(21, 94, 87))
        }
        val headerCopy = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        headerCopy.addView(text("Pencatatan", 24f, Color.WHITE, Typeface.BOLD))
        headerCopy.addView(text("Keuangan lokal", 14f, Color.rgb(216, 239, 235), Typeface.NORMAL))
        header.addView(headerCopy, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(ImageButton(this).apply {
            contentDescription = "Export laporan"
            setImageResource(R.drawable.ic_upload)
            setColorFilter(Color.WHITE)
            background = null
            scaleType = ImageView.ScaleType.CENTER
            setPadding(dp(8), dp(8), dp(8), dp(8))
            setOnClickListener { showExportDialog() }
        }, LinearLayout.LayoutParams(dp(40), dp(40)).apply {
            marginStart = dp(12)
        })
        page.addView(header, matchWrap())

        val scrollView = ScrollView(this)
        contentContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(180))
        }

        contentContainer.addView(text("Pengeluaran", 18f, Color.rgb(32, 39, 48), Typeface.BOLD), block(bottom = 8))
        contentContainer.addView(dateFilterView(), block(bottom = 12))
        contentContainer.addView(monthlyExpenseView(), block(bottom = 12))

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
            text = "Hapus Semua"
            setAllCaps(false)
            minHeight = dp(40)
            setOnClickListener { confirmClear() }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(44)))
        contentContainer.addView(historyHeader, block(bottom = 8))
        contentContainer.addView(historyFiltersView(), block(bottom = 10))

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

    private fun dateFilterView(): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = roundedRect(Color.WHITE, Color.rgb(226, 231, 236))
            setPadding(dp(14), dp(8), dp(10), dp(8))
        }

        dateFilterText = text(dateFilterLabel(), 14f, Color.rgb(42, 50, 61), Typeface.BOLD)
        row.addView(dateFilterText, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        row.addView(Button(this).apply {
            text = "Pilih"
            setAllCaps(false)
            minHeight = dp(36)
            minimumHeight = dp(36)
            setPadding(dp(10), 0, dp(10), 0)
            setOnClickListener { showDateFilterPicker() }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(40)).apply {
            marginEnd = dp(6)
        })

        row.addView(Button(this).apply {
            text = "Semua"
            setAllCaps(false)
            minHeight = dp(36)
            minimumHeight = dp(36)
            setPadding(dp(10), 0, dp(10), 0)
            setOnClickListener { showAllDates() }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(40)))

        return row
    }

    private fun monthlyExpenseView(): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedRect(Color.WHITE, Color.rgb(226, 231, 236))
            setPadding(dp(14), dp(10), dp(14), dp(12))
        }

        monthlyPeriodText = text("", 13f, Color.rgb(87, 100, 117), Typeface.NORMAL)
        monthlyExpenseText = text("", 20f, Color.rgb(190, 61, 47), Typeface.BOLD)
        card.addView(monthlyPeriodText, matchWrap())
        card.addView(monthlyExpenseText, topSpace(2))
        return card
    }

    private fun historyFiltersView(): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = roundedRect(Color.WHITE, Color.rgb(226, 231, 236))
            setPadding(dp(10), dp(8), dp(10), dp(8))
        }

        historyCategorySpinner = Spinner(this).apply {
            adapter = spinnerAdapter(historyCategoryOptions)
            onItemSelectedListener = historyFilterListener()
        }
        card.addView(historyCategorySpinner, LinearLayout.LayoutParams(0, dp(46), 1f).apply {
            marginEnd = dp(6)
        })

        historySortSpinner = Spinner(this).apply {
            adapter = spinnerAdapter(historySortOptions)
            onItemSelectedListener = historyFilterListener()
        }
        card.addView(historySortSpinner, LinearLayout.LayoutParams(0, dp(46), 1f))
        return card
    }

    private fun spinnerAdapter(items: List<String>): ArrayAdapter<String> {
        return ArrayAdapter(this, android.R.layout.simple_spinner_item, items).also {
            it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
    }

    private fun historyFilterListener(): AdapterView.OnItemSelectedListener {
        return object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (this@MainActivity::recordsContainer.isInitialized) {
                    renderRecords()
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
    }

    private fun showExportDialog() {
        var reportType = ExportReportType.DAILY
        val selectedCalendar = Calendar.getInstance(localeId).apply {
            timeInMillis = selectedDateStart ?: System.currentTimeMillis()
        }
        var exportRequest = createExportRequest(reportType, selectedCalendar.timeInMillis)

        val dialogContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(4), dp(4), 0)
        }

        val typeSpinner = Spinner(this).apply {
            adapter = spinnerAdapter(listOf("Laporan harian", "Laporan bulanan"))
        }
        val periodText = text("", 15f, Color.rgb(42, 50, 61), Typeface.BOLD)
        val choosePeriodButton = Button(this).apply {
            setAllCaps(false)
            minHeight = dp(40)
        }

        fun refreshDialogState() {
            exportRequest = createExportRequest(reportType, selectedCalendar.timeInMillis)
            periodText.text = exportRequest.periodLabel
            choosePeriodButton.text = if (reportType == ExportReportType.DAILY) {
                "Pilih tanggal"
            } else {
                "Pilih bulan"
            }
        }

        typeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                reportType = if (position == 0) ExportReportType.DAILY else ExportReportType.MONTHLY
                refreshDialogState()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        choosePeriodButton.setOnClickListener {
            if (reportType == ExportReportType.DAILY) {
                DatePickerDialog(
                    this,
                    { _, year, month, dayOfMonth ->
                        selectedCalendar.set(year, month, dayOfMonth, 0, 0, 0)
                        selectedCalendar.set(Calendar.MILLISECOND, 0)
                        refreshDialogState()
                    },
                    selectedCalendar.get(Calendar.YEAR),
                    selectedCalendar.get(Calendar.MONTH),
                    selectedCalendar.get(Calendar.DAY_OF_MONTH)
                ).show()
            } else {
                showMonthPicker(selectedCalendar.timeInMillis) { pickedMonth ->
                    selectedCalendar.timeInMillis = pickedMonth
                    refreshDialogState()
                }
            }
        }

        dialogContent.addView(text("Jenis laporan", 13f, Color.rgb(87, 100, 117), Typeface.NORMAL), block(bottom = 4))
        dialogContent.addView(typeSpinner, block(bottom = 12))
        dialogContent.addView(text("Periode", 13f, Color.rgb(87, 100, 117), Typeface.NORMAL), block(bottom = 4))
        dialogContent.addView(periodText, block(bottom = 8))
        dialogContent.addView(choosePeriodButton, matchWrap())
        refreshDialogState()

        val dialog = AlertDialog.Builder(this)
            .setTitle("Export laporan")
            .setView(dialogContent)
            .setPositiveButton("Export", null)
            .setNegativeButton("Batal", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                startCreateReportDocument(exportRequest)
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun showMonthPicker(initialTime: Long, onPicked: (Long) -> Unit) {
        val initialCalendar = Calendar.getInstance(localeId).apply { timeInMillis = initialTime }
        val currentYear = Calendar.getInstance(localeId).get(Calendar.YEAR)
        val initialYear = initialCalendar.get(Calendar.YEAR)
        val monthNames = DateFormatSymbols(localeId).months.take(12).toTypedArray()

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(8), dp(8), dp(8), 0)
        }
        val monthPicker = NumberPicker(this).apply {
            minValue = 0
            maxValue = 11
            displayedValues = monthNames
            value = initialCalendar.get(Calendar.MONTH)
        }
        val yearPicker = NumberPicker(this).apply {
            minValue = minOf(currentYear - 10, initialYear)
            maxValue = maxOf(currentYear + 10, initialYear)
            value = initialYear
        }
        row.addView(monthPicker, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(yearPicker, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        AlertDialog.Builder(this)
            .setTitle("Pilih bulan")
            .setView(row)
            .setPositiveButton("Pilih") { _, _ ->
                val selected = Calendar.getInstance(localeId).apply {
                    set(yearPicker.value, monthPicker.value, 1, 0, 0, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                onPicked(selected.timeInMillis)
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun createExportRequest(type: ExportReportType, referenceTime: Long): ExportRequest {
        return when (type) {
            ExportReportType.DAILY -> {
                val reference = Calendar.getInstance(localeId).apply { timeInMillis = referenceTime }
                val (start, end) = dayBounds(
                    reference.get(Calendar.YEAR),
                    reference.get(Calendar.MONTH),
                    reference.get(Calendar.DAY_OF_MONTH)
                )
                ExportRequest(
                    type = type,
                    title = "Laporan Keuangan Harian",
                    periodLabel = dateOnlyFormatter.format(Date(start.timeInMillis)),
                    fileName = "laporan_harian_${fileDayFormatter.format(Date(start.timeInMillis))}.xlsx",
                    startMillis = start.timeInMillis,
                    endMillis = end.timeInMillis
                )
            }
            ExportReportType.MONTHLY -> {
                val (start, end) = monthBounds(referenceTime)
                ExportRequest(
                    type = type,
                    title = "Laporan Keuangan Bulanan",
                    periodLabel = monthFormatter.format(Date(start)),
                    fileName = "laporan_bulanan_${fileMonthFormatter.format(Date(start))}.xlsx",
                    startMillis = start,
                    endMillis = end
                )
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun startCreateReportDocument(request: ExportRequest) {
        pendingExportRequest = request
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = XLSX_MIME_TYPE
            putExtra(Intent.EXTRA_TITLE, request.fileName)
        }
        try {
            startActivityForResult(intent, REQUEST_CREATE_REPORT_DOCUMENT)
        } catch (error: Exception) {
            pendingExportRequest = null
            Toast.makeText(this, "Tidak bisa membuka pemilih file", Toast.LENGTH_LONG).show()
        }
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_CREATE_REPORT_DOCUMENT || resultCode != RESULT_OK) return

        val uri = data?.data
        val request = pendingExportRequest
        pendingExportRequest = null

        if (uri == null || request == null) {
            Toast.makeText(this, "Export dibatalkan", Toast.LENGTH_SHORT).show()
            return
        }
        writeReportToUri(uri, request)
    }

    private fun writeReportToUri(uri: Uri, request: ExportRequest) {
        try {
            val records = store.all()
                .filter { it.createdAt in request.startMillis until request.endMillis }
                .sortedBy { it.createdAt }
            contentResolver.openOutputStream(uri)?.use { output ->
                FinanceReportExporter.writeXlsx(
                    outputStream = output,
                    title = request.title,
                    periodLabel = request.periodLabel,
                    records = records,
                    locale = localeId
                )
            } ?: error("Tidak bisa membuka lokasi file")
            Toast.makeText(this, "Laporan berhasil diexport", Toast.LENGTH_LONG).show()
        } catch (error: Exception) {
            Toast.makeText(this, "Export gagal: ${error.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun showDateFilterPicker() {
        val calendar = Calendar.getInstance(localeId)
        selectedDateStart?.let { calendar.timeInMillis = it }

        DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                val (start, end) = dayBounds(year, month, dayOfMonth)
                selectedDateStart = start.timeInMillis
                selectedDateEnd = end.timeInMillis
                monthlyReferenceTime = start.timeInMillis
                updateDateFilterLabel()
                renderRecords()
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun showAllDates() {
        selectedDateStart = null
        selectedDateEnd = null
        updateDateFilterLabel()
        renderRecords()
    }

    private fun setTodayDateFilter(updateUi: Boolean = true) {
        val today = Calendar.getInstance(localeId)
        val (start, end) = dayBounds(
            today.get(Calendar.YEAR),
            today.get(Calendar.MONTH),
            today.get(Calendar.DAY_OF_MONTH)
        )
        selectedDateStart = start.timeInMillis
        selectedDateEnd = end.timeInMillis
        monthlyReferenceTime = start.timeInMillis
        if (updateUi && this::dateFilterText.isInitialized) {
            updateDateFilterLabel()
        }
    }

    private fun dayBounds(year: Int, month: Int, dayOfMonth: Int): Pair<Calendar, Calendar> {
        val start = Calendar.getInstance(localeId).apply {
            set(year, month, dayOfMonth, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val end = Calendar.getInstance(localeId).apply {
            timeInMillis = start.timeInMillis
            add(Calendar.DATE, 1)
        }
        return start to end
    }

    private fun monthBounds(referenceTime: Long): Pair<Long, Long> {
        val start = Calendar.getInstance(localeId).apply {
            timeInMillis = referenceTime
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val end = Calendar.getInstance(localeId).apply {
            timeInMillis = start.timeInMillis
            add(Calendar.MONTH, 1)
        }
        return start.timeInMillis to end.timeInMillis
    }

    private fun updateDateFilterLabel() {
        dateFilterText.text = dateFilterLabel()
    }

    private fun dateFilterLabel(): String {
        val start = selectedDateStart ?: return "Tanggal: Semua"
        val today = Calendar.getInstance(localeId)
        val selected = Calendar.getInstance(localeId).apply { timeInMillis = start }
        val formattedDate = dateOnlyFormatter.format(Date(start))
        val isToday = selected.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
            selected.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR)
        return if (isToday) "Hari ini: $formattedDate" else "Tanggal: $formattedDate"
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

    private fun confirmDelete(record: TransactionRecord) {
        AlertDialog.Builder(this)
            .setTitle("Hapus transaksi?")
            .setMessage("${record.category} - ${formatCurrency(record.amount)}")
            .setPositiveButton("Hapus") { _, _ ->
                store.delete(record.id)
                renderRecords()
                Toast.makeText(this, "Transaksi dihapus", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun renderRecords() {
        val allRecords = store.all().sortedByDescending { it.createdAt }
        renderMonthlyExpense(allRecords)
        val records = applyDateFilter(allRecords)
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
        val historyRecords = applyHistoryFilters(records)
        if (historyRecords.isEmpty()) {
            val emptyText = if (selectedDateStart == null) {
                "Belum ada transaksi sesuai filter"
            } else {
                "Tidak ada transaksi sesuai filter pada tanggal ini"
            }
            recordsContainer.addView(text(emptyText, 14f, Color.rgb(98, 109, 124), Typeface.NORMAL))
            return
        }

        historyRecords.forEach { record ->
            recordsContainer.addView(recordRow(record), block(bottom = 10))
        }
    }

    private fun renderMonthlyExpense(records: List<TransactionRecord>) {
        val (monthStart, monthEnd) = monthBounds(monthlyReferenceTime)
        val monthlyExpense = records
            .filterNot { FinanceCategory.isIncome(it.category) }
            .filter { it.createdAt in monthStart until monthEnd }
            .sumOf { it.amount }
        monthlyPeriodText.text = "Pengeluaran ${monthFormatter.format(Date(monthStart))}"
        monthlyExpenseText.text = formatCurrency(monthlyExpense)
    }

    private fun applyDateFilter(records: List<TransactionRecord>): List<TransactionRecord> {
        val start = selectedDateStart ?: return records
        val end = selectedDateEnd ?: return records
        return records.filter { it.createdAt in start until end }
    }

    private fun applyHistoryFilters(records: List<TransactionRecord>): List<TransactionRecord> {
        val selectedCategory = if (this::historyCategorySpinner.isInitialized) {
            historyCategorySpinner.selectedItem?.toString()
        } else {
            HISTORY_ALL_CATEGORIES
        }
        val selectedSort = if (this::historySortSpinner.isInitialized) {
            historySortSpinner.selectedItem?.toString()
        } else {
            SORT_NEWEST
        }

        val categoryFiltered = if (selectedCategory == HISTORY_ALL_CATEGORIES || selectedCategory.isNullOrBlank()) {
            records
        } else {
            records.filter { it.category == selectedCategory }
        }

        return when (selectedSort) {
            SORT_AMOUNT_DESC -> categoryFiltered.sortedWith(
                compareByDescending<TransactionRecord> { it.amount }.thenByDescending { it.createdAt }
            )
            else -> categoryFiltered.sortedByDescending { it.createdAt }
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

        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        bottom.addView(
            text(dateFormatter.format(Date(record.createdAt)), 12f, Color.rgb(116, 127, 142), Typeface.NORMAL),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )
        bottom.addView(Button(this).apply {
            text = "Hapus"
            setAllCaps(false)
            minHeight = dp(34)
            minimumHeight = dp(34)
            minWidth = dp(72)
            minimumWidth = dp(72)
            setPadding(dp(10), 0, dp(10), 0)
            setOnClickListener { confirmDelete(record) }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(38)))
        row.addView(bottom, topSpace(8))
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

    companion object {
        private const val HISTORY_ALL_CATEGORIES = "Semua Kategori"
        private const val SORT_NEWEST = "Terbaru"
        private const val SORT_AMOUNT_DESC = "Nominal terbesar"
        private const val REQUEST_CREATE_REPORT_DOCUMENT = 7201
        private const val XLSX_MIME_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    }
}
