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
        recordId = 7, projectName = "钢弹簧浮置板顶升", lineName = "1号线", sectionName = "A-B",
        slabNo = "FZB-012", pointNo = "P03", operatorName = "张三", deviceSn = "20260101",
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
        System.getenv("WIFI_WRENCH_EVIDENCE_DIR")?.let { File(it, "sample-export.xlsx").writeBytes(bytes) }
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
            assertTrue(labels.contains("未标定：本表不含顶升力等正式力值。"))
        }
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

    @Test fun textIsEscapedAndControlCharactersDropped() {
        assertEquals("a&amp;b&lt;c&gt;&quot;d", Xml.escape("a&b<c>\"d\u0001"))
        assertEquals("'图表数据'!\$B\$2:\$B\$9", Xml.range("图表数据", 1, 2, 9))
        assertEquals("AA", Xml.column(26))
        assertEquals("12", Xml.number(12.0))
        assertEquals("0.5", Xml.number(0.5))
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
