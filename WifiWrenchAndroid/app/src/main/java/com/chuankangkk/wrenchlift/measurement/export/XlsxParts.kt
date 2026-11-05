package com.chuankangkk.wrenchlift.measurement.export

import java.io.Writer
import java.math.BigDecimal

/** 手写 SpreadsheetML / DrawingML 片段的小工具：转义、单元格引用、数字格式。 */
internal object Xml {
    const val NS_MAIN = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
    const val NS_REL = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
    const val NS_PKG_REL = "http://schemas.openxmlformats.org/package/2006/relationships"
    const val NS_CHART = "http://schemas.openxmlformats.org/drawingml/2006/chart"
    const val NS_A = "http://schemas.openxmlformats.org/drawingml/2006/main"
    const val NS_XDR = "http://schemas.openxmlformats.org/drawingml/2006/spreadsheetDrawing"
    const val HEADER = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"

    /** XML 1.0 不允许的控制字符直接丢弃，其余做实体转义。 */
    fun escape(text: String): String {
        val builder = StringBuilder(text.length + 8)
        var index = 0
        while (index < text.length) {
            val char = text[index]
            when {
                char == '&' -> builder.append("&amp;")
                char == '<' -> builder.append("&lt;")
                char == '>' -> builder.append("&gt;")
                char == '"' -> builder.append("&quot;")
                char == '\t' || char == '\n' || char == '\r' -> builder.append(char)
                char < ' ' || char == '￾' || char == '￿' -> Unit
                Character.isHighSurrogate(char) -> {
                    val next = text.getOrNull(index + 1)
                    if (next != null && Character.isLowSurrogate(next)) {
                        builder.append(char).append(next)
                        index += 1
                    }
                }
                Character.isLowSurrogate(char) -> Unit
                else -> builder.append(char)
            }
            index += 1
        }
        return builder.toString()
    }

    fun column(index: Int): String {
        var value = index + 1
        val builder = StringBuilder()
        while (value > 0) {
            val remainder = (value - 1) % 26
            builder.insert(0, ('A' + remainder))
            value = (value - 1) / 26
        }
        return builder.toString()
    }

    fun cellRef(columnIndex: Int, rowNumber: Int): String = column(columnIndex) + rowNumber

    /** 绝对区域引用；中文表名必须加单引号。 */
    fun range(sheetName: String, columnIndex: Int, firstRow: Int, lastRow: Int): String {
        val col = column(columnIndex)
        return "'${sheetName.replace("'", "''")}'!\$$col\$$firstRow:\$$col\$$lastRow"
    }

    fun number(value: Double): String? {
        if (!value.isFinite()) return null
        if (value == Math.rint(value) && kotlin.math.abs(value) < 1e15) return value.toLong().toString()
        return BigDecimal(value).round(java.math.MathContext(12)).stripTrailingZeros().toPlainString()
    }
}

/** 单元格值：只区分数字与文本两类，避免公式注入（不写任何公式单元格）。 */
internal sealed interface Cell {
    data class Num(val value: Double) : Cell
    data class Text(val value: String) : Cell
    data object Blank : Cell
}

internal fun num(value: Number?): Cell = value?.toDouble()?.let { Cell.Num(it) } ?: Cell.Blank
internal fun text(value: String?): Cell = if (value.isNullOrEmpty()) Cell.Blank else Cell.Text(value)

internal object Styles {
    const val DEFAULT = 0
    const val HEADER = 1
    const val TITLE = 2
    const val NOTE = 3
    const val LABEL = 4

    val xml: String = Xml.HEADER + """
<styleSheet xmlns="${Xml.NS_MAIN}">
<fonts count="4">
<font><sz val="11"/><name val="Calibri"/><family val="2"/></font>
<font><b/><sz val="11"/><color rgb="FF1B1F24"/><name val="Calibri"/><family val="2"/></font>
<font><b/><sz val="15"/><color rgb="FF1B1F24"/><name val="Calibri"/><family val="2"/></font>
<font><sz val="10"/><color rgb="FF5C6670"/><name val="Calibri"/><family val="2"/></font>
</fonts>
<fills count="3">
<fill><patternFill patternType="none"/></fill>
<fill><patternFill patternType="gray125"/></fill>
<fill><patternFill patternType="solid"><fgColor rgb="FFE8F4F2"/><bgColor indexed="64"/></patternFill></fill>
</fills>
<borders count="2">
<border><left/><right/><top/><bottom/><diagonal/></border>
<border><left/><right/><top/><bottom style="thin"><color rgb="FFD7DDE3"/></bottom><diagonal/></border>
</borders>
<cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
<cellXfs count="5">
<xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>
<xf numFmtId="0" fontId="1" fillId="2" borderId="1" xfId="0" applyFont="1" applyFill="1" applyBorder="1"/>
<xf numFmtId="0" fontId="2" fillId="0" borderId="0" xfId="0" applyFont="1"/>
<xf numFmtId="0" fontId="3" fillId="0" borderId="0" xfId="0" applyFont="1" applyAlignment="1"><alignment wrapText="0"/></xf>
<xf numFmtId="0" fontId="1" fillId="0" borderId="0" xfId="0" applyFont="1"/>
</cellXfs>
<cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles>
</styleSheet>
""".trimIndent()
}

/** 流式写一个 worksheet：先 [begin]，逐行 [row]，最后 [end]。 */
internal class SheetWriter(
    private val out: Writer,
    private val columnWidths: List<Double>,
    private val freezeHeader: Boolean,
) {
    private var rowNumber = 0
    val rowsWritten: Int get() = rowNumber

    fun begin() {
        out.write(Xml.HEADER)
        out.write("<worksheet xmlns=\"${Xml.NS_MAIN}\" xmlns:r=\"${Xml.NS_REL}\">")
        out.write("<sheetViews><sheetView workbookViewId=\"0\">")
        if (freezeHeader) {
            out.write("<pane ySplit=\"1\" topLeftCell=\"A2\" activePane=\"bottomLeft\" state=\"frozen\"/>")
        }
        out.write("</sheetView></sheetViews><sheetFormatPr defaultRowHeight=\"15\"/>")
        if (columnWidths.isNotEmpty()) {
            out.write("<cols>")
            columnWidths.forEachIndexed { index, width ->
                out.write("<col min=\"${index + 1}\" max=\"${index + 1}\" width=\"$width\" customWidth=\"1\"/>")
            }
            out.write("</cols>")
        }
        out.write("<sheetData>")
    }

    fun row(cells: List<Cell>, style: Int = Styles.DEFAULT) {
        rowNumber += 1
        out.write("<row r=\"$rowNumber\">")
        cells.forEachIndexed { index, cell ->
            val ref = Xml.cellRef(index, rowNumber)
            val styleAttr = if (style != Styles.DEFAULT) " s=\"$style\"" else ""
            when (cell) {
                is Cell.Num -> {
                    val value = Xml.number(cell.value)
                    if (value == null) out.write("<c r=\"$ref\"$styleAttr/>") else out.write("<c r=\"$ref\"$styleAttr><v>$value</v></c>")
                }
                is Cell.Text -> out.write("<c r=\"$ref\" t=\"inlineStr\"$styleAttr><is><t xml:space=\"preserve\">${Xml.escape(cell.value)}</t></is></c>")
                Cell.Blank -> if (style != Styles.DEFAULT) out.write("<c r=\"$ref\"$styleAttr/>")
            }
        }
        out.write("</row>")
    }

    /** 写一行空行（图表数据里用作段与段之间的断点）。 */
    fun blankRow() {
        rowNumber += 1
        out.write("<row r=\"$rowNumber\"/>")
    }

    fun end(drawingRelId: String? = null) {
        out.write("</sheetData>")
        out.write("<pageMargins left=\"0.7\" right=\"0.7\" top=\"0.75\" bottom=\"0.75\" header=\"0.3\" footer=\"0.3\"/>")
        if (drawingRelId != null) out.write("<drawing r:id=\"$drawingRelId\"/>")
        out.write("</worksheet>")
    }
}
