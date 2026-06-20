package id.local.pencatatan

import java.io.OutputStream
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object FinanceReportExporter {
    private val dateFormatter = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("id", "ID"))

    fun writeXlsx(
        outputStream: OutputStream,
        title: String,
        periodLabel: String,
        records: List<TransactionRecord>,
        locale: Locale
    ) {
        ZipOutputStream(outputStream).use { zip ->
            zip.textEntry("[Content_Types].xml", contentTypesXml())
            zip.textEntry("_rels/.rels", rootRelationshipsXml())
            zip.textEntry("docProps/app.xml", appPropertiesXml())
            zip.textEntry("docProps/core.xml", corePropertiesXml())
            zip.textEntry("xl/workbook.xml", workbookXml())
            zip.textEntry("xl/_rels/workbook.xml.rels", workbookRelationshipsXml())
            zip.textEntry("xl/styles.xml", stylesXml())
            zip.textEntry("xl/worksheets/sheet1.xml", worksheetXml(title, periodLabel, records, locale))
        }
    }

    private fun worksheetXml(
        title: String,
        periodLabel: String,
        records: List<TransactionRecord>,
        locale: Locale
    ): String {
        val income = records.filter { FinanceCategory.isIncome(it.category) }.sumOf { it.amount }
        val expense = records.filterNot { FinanceCategory.isIncome(it.category) }.sumOf { it.amount }
        val balance = income - expense
        val currencyFormatter = NumberFormat.getCurrencyInstance(locale).apply { maximumFractionDigits = 0 }
        val rows = mutableListOf<String>()

        rows += row(1, textCell("A", 1, title, style = 1))
        rows += row(2, textCell("A", 2, "Periode", style = 2), textCell("B", 2, periodLabel))
        rows += row(4, textCell("A", 4, "Total Masuk", style = 2), textCell("B", 4, currencyFormatter.format(income)))
        rows += row(5, textCell("A", 5, "Total Keluar", style = 2), textCell("B", 5, currencyFormatter.format(expense)))
        rows += row(6, textCell("A", 6, "Saldo", style = 2), textCell("B", 6, currencyFormatter.format(balance)))
        rows += row(
            8,
            textCell("A", 8, "Tanggal", style = 2),
            textCell("B", 8, "Kategori", style = 2),
            textCell("C", 8, "Tipe", style = 2),
            textCell("D", 8, "Nominal", style = 2),
            textCell("E", 8, "Tempat", style = 2),
            textCell("F", 8, "Catatan", style = 2)
        )

        records.sortedBy { it.createdAt }.forEachIndexed { index, record ->
            val rowNumber = index + 9
            val isIncome = FinanceCategory.isIncome(record.category)
            rows += row(
                rowNumber,
                textCell("A", rowNumber, dateFormatter.format(Date(record.createdAt))),
                textCell("B", rowNumber, record.category),
                textCell("C", rowNumber, if (isIncome) "Masuk" else "Keluar"),
                numberCell("D", rowNumber, record.amount),
                textCell("E", rowNumber, record.place.orEmpty()),
                textCell("F", rowNumber, record.message)
            )
        }

        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <dimension ref="A1:F${(records.size + 8).coerceAtLeast(8)}"/>
  <sheetViews><sheetView workbookViewId="0"/></sheetViews>
  <sheetFormatPr defaultRowHeight="15"/>
  <cols>
    <col min="1" max="1" width="22" customWidth="1"/>
    <col min="2" max="2" width="20" customWidth="1"/>
    <col min="3" max="3" width="12" customWidth="1"/>
    <col min="4" max="4" width="16" customWidth="1"/>
    <col min="5" max="5" width="22" customWidth="1"/>
    <col min="6" max="6" width="44" customWidth="1"/>
  </cols>
  <sheetData>
${rows.joinToString("\n")}
  </sheetData>
</worksheet>"""
    }

    private fun row(number: Int, vararg cells: String): String {
        return """<row r="$number">${cells.joinToString("")}</row>"""
    }

    private fun textCell(column: String, row: Int, value: String, style: Int? = null): String {
        val styleAttribute = style?.let { " s=\"$it\"" }.orEmpty()
        return """<c r="$column$row"$styleAttribute t="inlineStr"><is><t xml:space="preserve">${escapeXml(value)}</t></is></c>"""
    }

    private fun numberCell(column: String, row: Int, value: Long): String {
        return """<c r="$column$row" s="3"><v>$value</v></c>"""
    }

    private fun escapeXml(value: String): String {
        return value
            .filter { char -> char == '\n' || char == '\r' || char == '\t' || char.code >= 0x20 }
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    private fun contentTypesXml(): String {
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
  <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
  <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
  <Override PartName="/docProps/core.xml" ContentType="application/vnd.openxmlformats-package.core-properties+xml"/>
  <Override PartName="/docProps/app.xml" ContentType="application/vnd.openxmlformats-officedocument.extended-properties+xml"/>
</Types>"""
    }

    private fun rootRelationshipsXml(): String {
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties" Target="docProps/core.xml"/>
  <Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/extended-properties" Target="docProps/app.xml"/>
</Relationships>"""
    }

    private fun appPropertiesXml(): String {
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Properties xmlns="http://schemas.openxmlformats.org/officeDocument/2006/extended-properties" xmlns:vt="http://schemas.openxmlformats.org/officeDocument/2006/docPropsVTypes">
  <Application>Pencatatan</Application>
  <DocSecurity>0</DocSecurity>
  <ScaleCrop>false</ScaleCrop>
  <HeadingPairs>
    <vt:vector size="2" baseType="variant">
      <vt:variant><vt:lpstr>Worksheets</vt:lpstr></vt:variant>
      <vt:variant><vt:i4>1</vt:i4></vt:variant>
    </vt:vector>
  </HeadingPairs>
  <TitlesOfParts>
    <vt:vector size="1" baseType="lpstr">
      <vt:lpstr>Laporan</vt:lpstr>
    </vt:vector>
  </TitlesOfParts>
</Properties>"""
    }

    private fun corePropertiesXml(): String {
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<cp:coreProperties xmlns:cp="http://schemas.openxmlformats.org/package/2006/metadata/core-properties" xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:dcterms="http://purl.org/dc/terms/" xmlns:dcmitype="http://purl.org/dc/dcmitype/" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
  <dc:creator>Pencatatan</dc:creator>
  <cp:lastModifiedBy>Pencatatan</cp:lastModifiedBy>
</cp:coreProperties>"""
    }

    private fun workbookXml(): String {
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
  <sheets>
    <sheet name="Laporan" sheetId="1" r:id="rId1"/>
  </sheets>
</workbook>"""
    }

    private fun workbookRelationshipsXml(): String {
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
</Relationships>"""
    }

    private fun stylesXml(): String {
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <numFmts count="1">
    <numFmt numFmtId="164" formatCode="#,##0"/>
  </numFmts>
  <fonts count="2">
    <font><sz val="11"/><name val="Calibri"/></font>
    <font><b/><sz val="12"/><name val="Calibri"/></font>
  </fonts>
  <fills count="2">
    <fill><patternFill patternType="none"/></fill>
    <fill><patternFill patternType="gray125"/></fill>
  </fills>
  <borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders>
  <cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
  <cellXfs count="4">
    <xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>
    <xf numFmtId="0" fontId="1" fillId="0" borderId="0" xfId="0"/>
    <xf numFmtId="0" fontId="1" fillId="0" borderId="0" xfId="0"/>
    <xf numFmtId="164" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/>
  </cellXfs>
  <cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles>
  <dxfs count="0"/>
  <tableStyles count="0" defaultTableStyle="TableStyleMedium2" defaultPivotStyle="PivotStyleLight16"/>
</styleSheet>"""
    }

    private fun ZipOutputStream.textEntry(name: String, text: String) {
        putNextEntry(ZipEntry(name))
        write(text.toByteArray(Charsets.UTF_8))
        closeEntry()
    }
}
