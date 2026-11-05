package com.chuankangkk.wrenchlift.measurement.export

import java.io.OutputStream
import java.io.OutputStreamWriter
import java.io.Writer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.ceil
import kotlin.math.max

/** 一个全量过程点（来自数据库分页，不经过界面显示窗口）。 */
data class ProcessRow(
    val index: Long,
    val receivedAtMillis: Long,
    val boltNo: Int,
    val torqueNm: Double,
    val angleDeg: Double,
    val angleRaw: Int,
    val frameLabel: String,
    val rawHex: String,
)

data class WorkbookSummary(
    val recordId: Long,
    val projectName: String,
    val lineName: String,
    val sectionName: String,
    val slabNo: String,
    val pointNo: String,
    val operatorName: String,
    val deviceSn: String,
    val startTimeMillis: Long,
    val endTimeMillis: Long?,
    val resultStatus: String,
    val targetTorqueNm: Double?,
    val actualTorqueNm: Double?,
    val targetAngleDeg: Double?,
    val actualAngleDeg: Double?,
    val torqueLowerNm: Double?,
    val torqueUpperNm: Double?,
    val angleLowerDeg: Double?,
    val angleUpperDeg: Double?,
    val appVersion: String,
    val exportedAtMillis: Long = System.currentTimeMillis(),
)

data class WorkbookStats(val processRows: Long, val chartRows: Int, val chartCount: Int)

/**
 * 生成真 `.xlsx`（Office Open XML）：
 * - 「过程数据」全量写入，不截断；
 * - 「图表数据」按序号分桶保留每桶最大/最小扭矩，段与段之间留空行（图上断开，不跨段连线）；
 * - 「图表」页内嵌 Excel 原生图表：扭矩–转角、扭矩–序号、转角–序号、扭矩–接收时间，若有结果帧再加目标 vs 实际。
 * 流式写 ZIP，不在内存里拼整份文件。
 */
object SessionWorkbookWriter {
    const val MAX_CHART_ROWS = 6_000
    const val EXCEL_MAX_DATA_ROWS = 1_048_575L
    private const val RESET_DROP_DEG = 45.0

    const val SHEET_SUMMARY = "概要"
    const val SHEET_CHARTS = "图表"
    const val SHEET_DATA = "过程数据"
    const val SHEET_CHART_DATA = "图表数据"

    private const val COLOR_TORQUE = "0D9B8A"
    private const val COLOR_ANGLE = "3A6EA5"
    private const val COLOR_TIME = "6B5FB5"
    private const val COLOR_BAR = "0D9B8A"

    private val dataHeaders = listOf(
        "序号", "接收时间", "接收时间戳(ms)", "相对首点(s)", "螺栓号", "扭矩 N·m", "转角 °", "原始转角值", "帧类型", "原始HEX",
    )
    private val chartHeaders = listOf("序号", "转角 °", "扭矩 N·m", "相对首点(s)")

    suspend fun write(
        output: OutputStream,
        summary: WorkbookSummary,
        totalPoints: Long,
        rows: suspend (emit: suspend (ProcessRow) -> Unit) -> Unit,
    ): WorkbookStats {
        require(totalPoints <= EXCEL_MAX_DATA_ROWS) { "过程点超过 Excel 单表行数上限，请改用 CSV 导出" }
        val zip = ZipOutputStream(output.buffered(64 * 1024))
        val writer = OutputStreamWriter(zip, Charsets.UTF_8)
        val hasPoints = totalPoints > 0
        val hasTargetActual = summary.targetTorqueNm != null && summary.actualTorqueNm != null
        val chartCount = (if (hasPoints) 4 else 0) + (if (hasTargetActual) 1 else 0)

        fun entry(name: String, body: (Writer) -> Unit) {
            zip.putNextEntry(ZipEntry(name))
            body(writer)
            writer.flush()
            zip.closeEntry()
        }

        entry("[Content_Types].xml") { it.write(contentTypes(chartCount)) }
        entry("_rels/.rels") { it.write(rootRels()) }
        entry("docProps/core.xml") { it.write(coreProps(summary)) }
        entry("docProps/app.xml") { it.write(appProps()) }
        entry("xl/styles.xml") { it.write(Styles.xml) }

        val summaryLayout = SummaryLayout(summary, totalPoints)
        entry("xl/worksheets/sheet1.xml") { summaryLayout.write(it) }

        // 过程数据：全量流式写出，同时按序号分桶抽出图表用的点。
        val sampler = ChartSampler(totalPoints)
        zip.putNextEntry(ZipEntry("xl/worksheets/sheet3.xml"))
        val dataSheet = SheetWriter(writer, listOf(8.0, 24.0, 16.0, 12.0, 8.0, 11.0, 10.0, 11.0, 14.0, 60.0), freezeHeader = true)
        dataSheet.begin()
        dataSheet.row(dataHeaders.map(::text), Styles.HEADER)
        var written = 0L
        var firstMillis: Long? = null
        rows { row ->
            val start = firstMillis ?: row.receivedAtMillis.also { firstMillis = it }
            val relativeSeconds = (row.receivedAtMillis - start) / 1000.0
            dataSheet.row(
                listOf(
                    num(row.index), text(formatTime(row.receivedAtMillis)), num(row.receivedAtMillis), num(relativeSeconds),
                    num(row.boltNo), num(row.torqueNm), num(row.angleDeg), num(row.angleRaw), text(row.frameLabel), text(row.rawHex),
                ),
            )
            sampler.accept(row, relativeSeconds)
            written += 1
        }
        dataSheet.end()
        writer.flush()
        zip.closeEntry()
        val samples = sampler.finish()

        entry("xl/worksheets/sheet4.xml") { out ->
            val sheet = SheetWriter(out, listOf(8.0, 10.0, 11.0, 12.0), freezeHeader = true)
            sheet.begin()
            sheet.row(chartHeaders.map(::text), Styles.HEADER)
            samples.forEach { sample ->
                if (sample == null) sheet.blankRow() else sheet.row(
                    listOf(num(sample.index), num(sample.angle), num(sample.torque), num(sample.seconds)),
                )
            }
            sheet.end()
        }

        val charts = buildList {
            if (hasPoints) {
                val last = samples.size + 1
                val index = samples.map { it?.index?.toDouble() }
                val angle = samples.map { it?.angle }
                val torque = samples.map { it?.torque }
                val seconds = samples.map { it?.seconds }
                add(ChartSpec.Scatter("扭矩–转角", "扭矩 N·m", "转角 °", "扭矩 N·m",
                    Xml.range(SHEET_CHART_DATA, 1, 2, last), Xml.range(SHEET_CHART_DATA, 2, 2, last), angle, torque, COLOR_TORQUE))
                add(ChartSpec.Scatter("扭矩–序号", "扭矩 N·m", "序号（接收顺序）", "扭矩 N·m",
                    Xml.range(SHEET_CHART_DATA, 0, 2, last), Xml.range(SHEET_CHART_DATA, 2, 2, last), index, torque, COLOR_TORQUE))
                add(ChartSpec.Scatter("转角–序号", "转角 °", "序号（接收顺序）", "转角 °",
                    Xml.range(SHEET_CHART_DATA, 0, 2, last), Xml.range(SHEET_CHART_DATA, 1, 2, last), index, angle, COLOR_ANGLE))
                add(ChartSpec.Scatter("扭矩–接收时间（手机接收时基）", "扭矩 N·m", "相对首点接收时间 s", "扭矩 N·m",
                    Xml.range(SHEET_CHART_DATA, 3, 2, last), Xml.range(SHEET_CHART_DATA, 2, 2, last), seconds, torque, COLOR_TIME))
            }
            if (hasTargetActual) {
                val firstRow = summaryLayout.targetActualFirstRow
                add(ChartSpec.Bar("目标 vs 实际扭矩（结果帧）", "扭矩 N·m", "扭矩 N·m",
                    Xml.range(SHEET_SUMMARY, 0, firstRow, firstRow + 1), Xml.range(SHEET_SUMMARY, 1, firstRow, firstRow + 1),
                    listOf(SummaryLayout.TARGET_LABEL, SummaryLayout.ACTUAL_LABEL),
                    listOf(summary.targetTorqueNm, summary.actualTorqueNm), COLOR_BAR))
            }
        }

        entry("xl/worksheets/sheet2.xml") { out ->
            val sheet = SheetWriter(out, emptyList(), freezeHeader = false)
            sheet.begin()
            sheet.row(listOf(text("WiFi 扳手 · 记录 R${summary.recordId} · ${summary.slabNo} / ${summary.pointNo}")), Styles.TITLE)
            sheet.row(listOf(text(chartNote(hasPoints, totalPoints, samples.count { it != null }))), Styles.NOTE)
            sheet.end(drawingRelId = if (charts.isNotEmpty()) "rId1" else null)
        }
        if (charts.isNotEmpty()) {
            entry("xl/worksheets/_rels/sheet2.xml.rels") { it.write(relationships(listOf(Rel("rId1", REL_DRAWING, "../drawings/drawing1.xml")))) }
            entry("xl/drawings/drawing1.xml") { it.write(ChartXml.drawing(charts.size)) }
            entry("xl/drawings/_rels/drawing1.xml.rels") { out ->
                out.write(relationships(charts.indices.map { Rel("rId${it + 1}", REL_CHART, "../charts/chart${it + 1}.xml") }))
            }
            charts.forEachIndexed { index, spec -> entry("xl/charts/chart${index + 1}.xml") { it.write(ChartXml.render(spec)) } }
        }

        entry("xl/workbook.xml") { it.write(workbook()) }
        entry("xl/_rels/workbook.xml.rels") { out ->
            out.write(
                relationships(
                    listOf(
                        Rel("rId1", REL_SHEET, "worksheets/sheet1.xml"),
                        Rel("rId2", REL_SHEET, "worksheets/sheet2.xml"),
                        Rel("rId3", REL_SHEET, "worksheets/sheet3.xml"),
                        Rel("rId4", REL_SHEET, "worksheets/sheet4.xml"),
                        Rel("rId5", REL_STYLES, "styles.xml"),
                    ),
                ),
            )
        }
        writer.flush()
        zip.finish()
        zip.flush()
        return WorkbookStats(processRows = written, chartRows = samples.count { it != null }, chartCount = charts.size)
    }

    private fun chartNote(hasPoints: Boolean, total: Long, sampled: Int): String = when {
        !hasPoints -> "本记录没有过程点，仅有扳手结果帧。"
        sampled.toLong() < total -> "图表取自「图表数据」页：全量 $total 点按序号分桶保留每桶最大/最小扭矩，共 $sampled 点；完整数据见「过程数据」页，未截断。运行段之间断开，不跨段连线。"
        else -> "图表取自「图表数据」页（与「过程数据」同为全量 $total 点）。运行段之间断开，不跨段连线。"
    }

    private data class Sample(val index: Long, val angle: Double, val torque: Double, val seconds: Double)

    /** 分桶抽样：每桶留最小/最大扭矩点（按原顺序），段边界处插入 null 作为断点。 */
    private class ChartSampler(total: Long) {
        private val bucketSize = max(1L, ceil(total / (MAX_CHART_ROWS / 2.0)).toLong())
        private val output = ArrayList<Sample?>()
        private var bucket = ArrayList<Sample>()
        private var previous: ProcessRow? = null

        fun accept(row: ProcessRow, seconds: Double) {
            val prev = previous
            if (prev != null && (row.boltNo != prev.boltNo || row.angleDeg < prev.angleDeg - RESET_DROP_DEG)) {
                flush()
                if (output.isNotEmpty() && output.last() != null) output += null
            }
            bucket += Sample(row.index, row.angleDeg, row.torqueNm, seconds)
            if (bucket.size >= bucketSize) flush()
            previous = row
        }

        private fun flush() {
            if (bucket.isEmpty()) return
            if (bucket.size <= 2) {
                output.addAll(bucket)
            } else {
                val minSample = bucket.minBy { it.torque }
                val maxSample = bucket.maxBy { it.torque }
                if (minSample === maxSample) output += minSample
                else output.addAll(listOf(minSample, maxSample).sortedBy { it.index })
            }
            bucket = ArrayList()
        }

        fun finish(): List<Sample?> {
            flush()
            while (output.isNotEmpty() && output.last() == null) output.removeAt(output.lastIndex)
            return output
        }
    }

    /** 概要页（含目标/实际两行，供柱状图引用）。 */
    private class SummaryLayout(private val summary: WorkbookSummary, private val totalPoints: Long) {
        var targetActualFirstRow: Int = 0
            private set

        fun write(out: Writer) {
            val sheet = SheetWriter(out, listOf(22.0, 46.0), freezeHeader = false)
            sheet.begin()
            sheet.row(listOf(text("WiFi 扳手 · 测量记录")), Styles.TITLE)
            fun line(label: String, value: Cell) = sheet.row(listOf(text(label), value))
            line("记录编号", text("R${summary.recordId}"))
            line("项目", text(summary.projectName))
            line("线路 / 区间", text("${summary.lineName} / ${summary.sectionName}"))
            line("板号", text(summary.slabNo))
            line("测点", text(summary.pointNo))
            line("操作员", text(summary.operatorName))
            line("扳手 SN", text(summary.deviceSn))
            line("开始时间", text(formatTime(summary.startTimeMillis)))
            line("结束时间", text(summary.endTimeMillis?.let(::formatTime) ?: "未结束"))
            line("过程点数（全量）", num(totalPoints))
            line("结果状态", text(summary.resultStatus))
            targetActualFirstRow = sheet.rowsWritten + 1
            line(TARGET_LABEL, num(summary.targetTorqueNm))
            line(ACTUAL_LABEL, num(summary.actualTorqueNm))
            line("目标转角 °", num(summary.targetAngleDeg))
            line("实际转角 °", num(summary.actualAngleDeg))
            line("扭矩下限 / 上限 N·m", text(pair(summary.torqueLowerNm, summary.torqueUpperNm)))
            line("转角下限 / 上限 °", text(pair(summary.angleLowerDeg, summary.angleUpperDeg)))
            sheet.row(emptyList())
            sheet.row(listOf(text("说明")), Styles.LABEL)
            NOTES.forEach { sheet.row(listOf(text(it)), Styles.NOTE) }
            sheet.row(emptyList())
            sheet.row(listOf(text("导出时间"), text(formatTime(summary.exportedAtMillis))))
            sheet.row(listOf(text("导出软件"), text("WiFi 扳手 ${summary.appVersion}")))
            sheet.row(listOf(text("著作权 传康Kk · 免费供应")), Styles.NOTE)
            sheet.end()
        }

        private fun pair(a: Double?, b: Double?): String =
            if (a == null && b == null) "" else "${a?.let { Xml.number(it) } ?: "--"} / ${b?.let { Xml.number(it) } ?: "--"}"

        companion object {
            const val TARGET_LABEL = "目标扭矩 N·m"
            const val ACTUAL_LABEL = "实际扭矩 N·m"
            val NOTES = listOf(
                "「过程数据」为本记录全部过程点，未截断；图表可能按序号抽样，抽样口径见「图表」页。",
                "接收时间为手机接收时间：同一帧的多个点共用接收时间，不是设备逐点时戳。",
                "未标定：本表不含顶升力等正式力值。",
                "分离点 / 平衡点：待分析，本软件未做确认。",
            )
        }
    }

    private data class Rel(val id: String, val type: String, val target: String)

    private const val REL_SHEET = "${Xml.NS_REL}/worksheet"
    private const val REL_STYLES = "${Xml.NS_REL}/styles"
    private const val REL_DRAWING = "${Xml.NS_REL}/drawing"
    private const val REL_CHART = "${Xml.NS_REL}/chart"

    private fun relationships(rels: List<Rel>): String = buildString {
        append(Xml.HEADER).append("<Relationships xmlns=\"${Xml.NS_PKG_REL}\">")
        rels.forEach { append("<Relationship Id=\"${it.id}\" Type=\"${it.type}\" Target=\"${it.target}\"/>") }
        append("</Relationships>")
    }

    private fun rootRels(): String = buildString {
        append(Xml.HEADER).append("<Relationships xmlns=\"${Xml.NS_PKG_REL}\">")
        append("<Relationship Id=\"rId1\" Type=\"${Xml.NS_REL}/officeDocument\" Target=\"xl/workbook.xml\"/>")
        append("<Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties\" Target=\"docProps/core.xml\"/>")
        append("<Relationship Id=\"rId3\" Type=\"${Xml.NS_REL}/extended-properties\" Target=\"docProps/app.xml\"/>")
        append("</Relationships>")
    }

    private fun workbook(): String = buildString {
        append(Xml.HEADER)
        append("<workbook xmlns=\"${Xml.NS_MAIN}\" xmlns:r=\"${Xml.NS_REL}\">")
        append("<bookViews><workbookView activeTab=\"1\"/></bookViews><sheets>")
        listOf(SHEET_SUMMARY, SHEET_CHARTS, SHEET_DATA, SHEET_CHART_DATA).forEachIndexed { index, name ->
            append("<sheet name=\"${Xml.escape(name)}\" sheetId=\"${index + 1}\" r:id=\"rId${index + 1}\"/>")
        }
        append("</sheets></workbook>")
    }

    private fun contentTypes(chartCount: Int): String = buildString {
        append(Xml.HEADER)
        append("<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">")
        append("<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>")
        append("<Default Extension=\"xml\" ContentType=\"application/xml\"/>")
        append("<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>")
        for (sheet in 1..4) {
            append("<Override PartName=\"/xl/worksheets/sheet$sheet.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>")
        }
        append("<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>")
        if (chartCount > 0) {
            append("<Override PartName=\"/xl/drawings/drawing1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.drawing+xml\"/>")
            for (chart in 1..chartCount) {
                append("<Override PartName=\"/xl/charts/chart$chart.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.drawingml.chart+xml\"/>")
            }
        }
        append("<Override PartName=\"/docProps/core.xml\" ContentType=\"application/vnd.openxmlformats-package.core-properties+xml\"/>")
        append("<Override PartName=\"/docProps/app.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.extended-properties+xml\"/>")
        append("</Types>")
    }

    private fun coreProps(summary: WorkbookSummary): String {
        val iso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
            .format(Date(summary.exportedAtMillis))
        return Xml.HEADER +
            "<cp:coreProperties xmlns:cp=\"http://schemas.openxmlformats.org/package/2006/metadata/core-properties\" " +
            "xmlns:dc=\"http://purl.org/dc/elements/1.1/\" xmlns:dcterms=\"http://purl.org/dc/terms/\" " +
            "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">" +
            "<dc:title>${Xml.escape("WiFi 扳手 R${summary.recordId} ${summary.slabNo} ${summary.pointNo}")}</dc:title>" +
            "<dc:creator>WiFi 扳手</dc:creator>" +
            "<dcterms:created xsi:type=\"dcterms:W3CDTF\">$iso</dcterms:created>" +
            "</cp:coreProperties>"
    }

    private fun appProps(): String = Xml.HEADER +
        "<Properties xmlns=\"http://schemas.openxmlformats.org/officeDocument/2006/extended-properties\">" +
        "<Application>WiFi 扳手</Application></Properties>"

    private val timeFormat = ThreadLocal.withInitial {
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.CHINA).apply { timeZone = TimeZone.getTimeZone("Asia/Shanghai") }
    }

    fun formatTime(millis: Long): String = timeFormat.get()!!.format(Date(millis))
}
