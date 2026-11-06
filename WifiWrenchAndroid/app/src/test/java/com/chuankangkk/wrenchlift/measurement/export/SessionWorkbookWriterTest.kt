package com.chuankangkk.wrenchlift.measurement.export

import kotlinx.coroutines.runBlocking
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

class SessionWorkbookWriterTest {
    private fun summary(target: Double? = 180.0, actual: Double? = 176.0) = WorkbookSummary(
        recordId = 7, deviceSn = "20260101",
        startTimeMillis = 1_762_300_000_000, endTimeMillis = 1_762_300_600_000, resultStatus = "合格",
        targetTorqueNm = target, actualTorqueNm = actual, targetAngleDeg = 90.0, actualAngleDeg = 88.0,
        torqueLowerNm = 150.0, torqueUpperNm = 220.0, angleLowerDeg = 1.0, angleUpperDeg = 360.0,
        appVersion = "0.3.0-test", exportedAtMillis = 1_762_300_700_000,
    )

    /** 两段运行：第二段转角回落到 0，用来检查段间断开。 */
    private fun rows(total: Int): List<ProcessRow> = List(total) { i ->
        val secondRun = i >= total / 2
        val local = if (secondRun) i - total / 2 else i
        ProcessRow(
            index = i + 1L, receivedAtMillis = 1_762_300_000_000 + (i / 20) * 1000L, boltNo = if (secondRun) 2 else 1,
            torqueNm = (local % 400).toDouble(), angleDeg = local * 0.05, angleRaw = (local * 0.05).toInt(),
            frameLabel = "扭矩转角过程", rawHex = "C5 C5 01 12 FF FF 10 <&>",
        )
    }

    private fun build(points: List<ProcessRow>, summary: WorkbookSummary = summary()): Pair<ByteArray, WorkbookStats> {
        val out = ByteArrayOutputStream()
        val stats = runBlocking {
            SessionWorkbookWriter.write(out, summary, points.size.toLong()) { emit -> points.forEach { emit(it) } }
        }
        return out.toByteArray() to stats
    }

    @Test fun fullDataIsNotTruncatedAndNativeChartsReferenceSheetRanges() {
        val points = rows(25_000)
        val (bytes, stats) = build(points)
        System.getenv("WIFI_WRENCH_EVIDENCE_DIR")?.let { File(it).apply { mkdirs() }.resolve("sample-export.xlsx").writeBytes(bytes) }
        assertEquals(25_000L, stats.processRows)
        assertEquals(5, stats.chartCount)
        assertTrue("图表点应被抽样", stats.chartRows <= SessionWorkbookWriter.MAX_CHART_ROWS + 2)

        XSSFWorkbook(ByteArrayInputStream(bytes)).use { book ->
            assertEquals(listOf("概要", "图表", "过程数据", "图表数据"), (0 until book.numberOfSheets).map { book.getSheetName(it) })
            val data = book.getSheet("过程数据")
            assertEquals(25_000, data.lastRowNum) // 表头 + 25000 行（0 基）
            assertEquals(25_000.0, data.getRow(25_000).getCell(0).numericCellValue, 0.0)
            assertEquals("C5 C5 01 12 FF FF 10 <&>", data.getRow(1).getCell(9).stringCellValue)
            val charts = book.getSheet("图表").createDrawingPatriarch().charts
            assertEquals(5, charts.size)
            val titles = charts.map { it.titleText?.string }
            assertTrue(titles.contains("扭矩–转角"))
            assertTrue(titles.contains("扭矩–序号"))
            val torqueAngle = charts.first { it.titleText?.string == "扭矩–转角" }
            val series = torqueAngle.chartSeries.single().getSeries(0)
            assertTrue(series.categoryData.dataRangeReference.startsWith("'图表数据'!\$B\$2"))
            assertTrue(series.valuesData.dataRangeReference.startsWith("'图表数据'!\$C\$2"))
            // 段间有空行：两段运行在图表数据里中间断开。
            val chartData = book.getSheet("图表数据")
            val blankRows = (1..chartData.lastRowNum).count { r -> chartData.getRow(r)?.getCell(0) == null }
            assertTrue("段间至少一个空行", blankRows >= 1)
        }
        assertAllXmlWellFormed(bytes)
    }

    @Test fun everyProcessRowMatchesSourceValuesExactly() {
        val points = rows(3_000)
        val (bytes, _) = build(points)
        XSSFWorkbook(ByteArrayInputStream(bytes)).use { book ->
            val data = book.getSheet("过程数据")
            assertEquals("接收时间（手机）", data.getRow(0).getCell(1).stringCellValue)
            points.forEachIndexed { i, expected ->
                val row = data.getRow(i + 1)
                assertEquals("序号 $i", expected.index.toDouble(), row.getCell(0).numericCellValue, 0.0)
                assertEquals("时间戳 $i", expected.receivedAtMillis.toDouble(), row.getCell(2).numericCellValue, 0.0)
                assertEquals("螺栓号 $i", expected.boltNo.toDouble(), row.getCell(4).numericCellValue, 0.0)
                assertEquals("扭矩 $i", expected.torqueNm, row.getCell(5).numericCellValue, 1e-9)
                assertEquals("转角 $i", expected.angleDeg, row.getCell(6).numericCellValue, 1e-9)
                assertEquals("原始转角 $i", expected.angleRaw.toDouble(), row.getCell(7).numericCellValue, 0.0)
            }
            // 接收时间是 Excel 原生日期时间（可排序/筛选），显示为北京时间。
            val timeCell = data.getRow(1).getCell(1)
            assertTrue(org.apache.poi.ss.usermodel.DateUtil.isCellDateFormatted(timeCell))
            val shown = org.apache.poi.ss.usermodel.DataFormatter().formatCellValue(timeCell)
            assertEquals(SessionWorkbookWriter.formatTime(points.first().receivedAtMillis).substring(0, 19), shown.substring(0, 19))
            val summary = book.getSheet("概要")
            val labels = (0..summary.lastRowNum).mapNotNull { summary.getRow(it)?.getCell(0)?.stringCellValue }
            assertTrue(labels.contains("过程点数（全量）"))
            val countRow = (0..summary.lastRowNum).first { summary.getRow(it)?.getCell(0)?.stringCellValue == "过程点数（全量）" }
            assertEquals(3_000.0, summary.getRow(countRow).getCell(1).numericCellValue, 0.0)
            assertTrue(labels.contains("未标定：不含顶升力等正式力值。"))
            // 单台扳手：不出现板号/测点，也不在导出里署名。
            assertTrue(labels.none { it.contains("板号") || it.contains("测点") || it.contains("传康") || it.contains("免费") })
            // 统计值只取实测：两段 0..1499 每段扭矩 0..399 循环，峰值 399 出现在第 400 个点（转角 19.95°）。
            fun value(label: String) = summary.getRow((0..summary.lastRowNum).first { summary.getRow(it)?.getCell(0)?.stringCellValue == label }).getCell(1)
            assertEquals(399.0, value("峰值扭矩 N·m").numericCellValue, 0.0)
            assertEquals(19.95, value("峰值处转角 °").numericCellValue, 1e-9)
            assertEquals(0.0, value("最小转角 °").numericCellValue, 0.0)
            assertEquals(1_499 * 0.05, value("最大转角 °").numericCellValue, 1e-9)
            assertEquals(2.0, value("运行段数").numericCellValue, 0.0)
            assertEquals((2_999 / 20).toDouble(), value("首末点间隔 s").numericCellValue, 0.0)
        }
    }

    @Test fun recordFileNameUsesRecordIdAndStartTimeOnly() {
        // 2025-11-05 07:46:40 UTC = 15:46:40 北京时间
        assertEquals(
            "WifiWrench_R7_20251105_154640.xlsx",
            com.chuankangkk.wrenchlift.measurement.MeasurementCsvExporter.recordFileName(7, 1_762_328_800_000, "xlsx"),
        )
    }

    @Test fun resultOnlyRecordStillExportsTargetVersusActualChart() {
        val (bytes, stats) = build(emptyList())
        assertEquals(0L, stats.processRows)
        assertEquals(1, stats.chartCount)
        XSSFWorkbook(ByteArrayInputStream(bytes)).use { book ->
            assertEquals(0, book.getSheet("过程数据").lastRowNum)
            assertEquals(1, book.getSheet("图表").createDrawingPatriarch().charts.size)
        }
    }

    @Test fun smallRecordKeepsEveryPointInChartData() {
        val points = rows(300)
        val (bytes, stats) = build(points, summary(target = null, actual = null))
        assertEquals(4, stats.chartCount)
        assertEquals(300, stats.chartRows)
        XSSFWorkbook(ByteArrayInputStream(bytes)).use { book -> assertEquals(300, book.getSheet("过程数据").lastRowNum) }
    }

    /** 接收时间精确到毫秒：Excel 序列值不能被有效位数截掉毫秒。 */
    @Test fun receivedTimeKeepsMillisecondsInExcelDateCells() {
        val base = 1_762_300_000_000L
        val offsets = listOf(1L, 37L, 499L, 500L, 999L, 86_399_999L)
        val points = offsets.mapIndexed { i, ms ->
            ProcessRow(index = i + 1L, receivedAtMillis = base + ms, boltNo = 1, torqueNm = 10.0, angleDeg = i.toDouble(), angleRaw = i,
                frameLabel = "扭矩转角过程", rawHex = "C5")
        }
        val (bytes, _) = build(points)
        XSSFWorkbook(ByteArrayInputStream(bytes)).use { book ->
            val data = book.getSheet("过程数据")
            val formatter = org.apache.poi.ss.usermodel.DataFormatter()
            points.forEachIndexed { i, expected ->
                val cell = data.getRow(i + 1).getCell(1)
                val local = expected.receivedAtMillis + java.util.TimeZone.getTimeZone("Asia/Shanghai").getOffset(expected.receivedAtMillis)
                val millis = (cell.numericCellValue - 25_569.0) * 86_400_000.0
                assertEquals("序列值换回毫秒 $i", local.toDouble(), millis, 0.05)
                val expectedText = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", java.util.Locale.ROOT)
                    .apply { timeZone = java.util.TimeZone.getTimeZone("Asia/Shanghai") }.format(java.util.Date(expected.receivedAtMillis))
                assertEquals("显示到毫秒 $i", expectedText, formatter.formatCellValue(cell))
            }
        }
    }

    @Test fun textIsEscapedAndControlCharactersDropped() {
        assertEquals("a&amp;b&lt;c&gt;&quot;d", Xml.escape("a&b<c>\"d\u0001"))
        assertEquals("'图表数据'!\$B\$2:\$B\$9", Xml.range("图表数据", 1, 2, 9))
        assertEquals("AA", Xml.column(26))
        assertEquals("12", Xml.number(12.0))
        assertEquals("0.5", Xml.number(0.5))
        assertEquals("0.3", Xml.number(0.1 + 0.2))
        assertEquals("19.95", Xml.number(399 * 0.05))
        assertEquals(null, Xml.number(Double.NaN))
    }

    private fun assertAllXmlWellFormed(bytes: ByteArray) {
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        var entries = 0
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val content = zip.readBytes()
                factory.newDocumentBuilder().parse(ByteArrayInputStream(content))
                entries += 1
            }
        }
        assertTrue(entries >= 15)
    }
}
