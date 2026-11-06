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
            frameLabel = "扭矩转角过程", rawHex = "C5 C5 01 12 FF FF 5C <&>",
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
        assertEquals(SessionWorkbookWriter.PROCESS_CHART_COUNT + 1, stats.chartCount)
        assertTrue("图表点应被抽样", stats.chartRows <= SessionWorkbookWriter.MAX_CHART_ROWS + 2)

        XSSFWorkbook(ByteArrayInputStream(bytes)).use { book ->
            assertEquals(
                listOf("概要", "图表", "运行段", "拟合明细", "候选扫描", "过程数据", "图表数据"),
                (0 until book.numberOfSheets).map { book.getSheetName(it) },
            )
            val data = book.getSheet("过程数据")
            assertEquals(25_000, data.lastRowNum) // 表头 + 25000 行（0 基）
            assertEquals(25_000.0, data.getRow(25_000).getCell(0).numericCellValue, 0.0)
            assertEquals("C5 C5 01 12 FF FF 5C <&>", data.getRow(1).getCell(17).stringCellValue)
            val charts = book.getSheet("图表").createDrawingPatriarch().charts
            assertEquals(SessionWorkbookWriter.PROCESS_CHART_COUNT + 1, charts.size)
            val titles = charts.map { it.titleText?.string }
            assertTrue(titles.contains("扭矩–转角"))
            assertTrue(titles.toString(), titles.any { it!!.startsWith("斜率–转角") })
            assertTrue(titles.toString(), titles.any { it!!.startsWith("各段叠加") })
            assertTrue(titles.toString(), titles.any { it!!.startsWith("残差改善–候选位置") })
            assertTrue(titles.contains("扭矩–接收时间（手机接收时基）"))
            assertTrue(titles.contains("转角–接收时间（转速与停顿）"))
            assertTrue(titles.toString(), titles.any { it!!.startsWith("转速–接收时间") })
            assertTrue(titles.toString(), titles.any { it!!.startsWith("帧接收间隔–接收时间") })
            // 序号图已移除（主屏与导出都不再按接收顺序作图）。
            assertTrue(titles.none { it!!.contains("序号") })
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
                assertEquals("螺栓号 $i", expected.boltNo.toDouble(), row.getCell(10).numericCellValue, 0.0)
                assertEquals("扭矩 $i", expected.torqueNm, row.getCell(11).numericCellValue, 1e-9)
                assertEquals("转角 $i", expected.angleDeg, row.getCell(12).numericCellValue, 1e-9)
                assertEquals("原始转角 $i", expected.angleRaw.toDouble(), row.getCell(15).numericCellValue, 0.0)
                // 派生列：两段各 1500 点，每 20 点一帧（同一接收时间），每帧转角 +1° → 帧间转速 1 °/s。
                val local = if (i >= 1_500) i - 1_500 else i
                assertEquals("距上一点 $i", if (i == 0) null else if (i % 20 == 0) 1_000.0 else 0.0, row.getCell(4)?.numericCellValue)
                assertEquals("运行段 $i", if (i >= 1_500) 2.0 else 1.0, row.getCell(5).numericCellValue, 0.0)
                assertEquals("段内时间 $i", (local / 20).toDouble(), row.getCell(6).numericCellValue, 1e-9)
                assertEquals("帧号 $i", (i / 20 + 1).toDouble(), row.getCell(7).numericCellValue, 0.0)
                assertEquals("帧内序号 $i", (i % 20 + 1).toDouble(), row.getCell(8).numericCellValue, 0.0)
                assertEquals("段内转角增量 $i", local * 0.05, row.getCell(13).numericCellValue, 1e-9)
                assertEquals("帧间转速 $i", if (local < 20) null else 1.0, row.getCell(14)?.numericCellValue)
            }
            val header = data.getRow(0)
            assertEquals(
                listOf("序号", "接收时间（手机）", "接收时间戳(ms)", "相对首点(s)", "距上一点(ms)", "运行段", "段内时间(s)", "帧号", "帧内序号", "帧内点数",
                    "螺栓号", "扭矩 N·m", "转角 °", "段内转角增量 °", "帧间转速 °/s", "原始转角值", "帧类型", "原始HEX"),
                (0 until header.lastCellNum).map { header.getCell(it).stringCellValue },
            )
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

    /** 两段都在 30° 处由 10 N·m/° 变缓到 3 N·m/°：「运行段」逐段给出候选，扭矩–转角图叠加候选点；结果帧不进统计。 */
    @Test fun runsSheetListsCandidatePerRunAndChartOverlaysThem() {
        fun run(startIndex: Long, startMillis: Long): List<ProcessRow> = List(321) { i ->
            val angle = i / 4
            val exact = i / 4.0
            val torque = if (exact < 30) 10 * exact else 300 + 3 * (exact - 30)
            ProcessRow(startIndex + i, startMillis + (i / 10) * 1000L, 1, Math.rint(torque), angle.toDouble(), angle,
                "扭矩转角过程", "C5 C5 01 12 FF FF 34")
        }
        val first = run(1, 1_762_300_000_000)
        val result = ProcessRow(322, 1_762_300_033_000, 1, 999.0, 400.0, 400, "运行结果", "C5 C5 01 15 00 FF 24")
        val second = run(323, 1_762_300_060_000)
        val (bytes, _) = build(first + result + second)
        System.getenv("WIFI_WRENCH_EVIDENCE_DIR")?.let { File(it).apply { mkdirs() }.resolve("sample-export-candidates.xlsx").writeBytes(bytes) }
        XSSFWorkbook(ByteArrayInputStream(bytes)).use { book ->
            val runs = book.getSheet("运行段")
            val header = runs.getRow(0)
            fun col(name: String) = (0 until header.lastCellNum).first { header.getCell(it).stringCellValue == name }
            assertEquals(2.0, runs.getRow(2).getCell(col("运行段")).numericCellValue, 0.0)
            for (r in 1..2) {
                val row = runs.getRow(r)
                assertEquals(321.0, row.getCell(col("点数")).numericCellValue, 0.0)
                assertEquals(30.0, row.getCell(col("候选转角 °")).numericCellValue, 2.0)
                assertEquals(10.0, row.getCell(col("前斜率 N·m/°")).numericCellValue, 1.0)
                assertEquals(3.0, row.getCell(col("后斜率 N·m/°")).numericCellValue, 1.0)
                assertEquals("转折候选（未验证）", row.getCell(col("判定")).stringCellValue)
                assertEquals(30.0, row.getCell(col("最佳两段位置 °")).numericCellValue, 2.0)
                assertTrue(row.getCell(col("最佳残差改善 %")).numericCellValue > 80.0)
                assertEquals(80.0 / 32.0, row.getCell(col("平均转速 °/s")).numericCellValue, 1e-6)
                assertTrue(row.getCell(col("单直线斜率 N·m/°")).numericCellValue in 3.0..10.0)
            }
            // 拟合明细：两段都有分箱数据，每箱有两段拟合值；残差相对扭矩很小。
            val fit = book.getSheet("拟合明细")
            val fitHeader = fit.getRow(0)
            fun fitCol(name: String) = (0 until fitHeader.lastCellNum).first { fitHeader.getCell(it).stringCellValue == name }
            val fitRows = (1..fit.lastRowNum).mapNotNull { fit.getRow(it) }.filter { it.getCell(fitCol("运行段"))?.cellType == org.apache.poi.ss.usermodel.CellType.NUMERIC }
            assertEquals(setOf(1.0, 2.0), fitRows.map { it.getCell(fitCol("运行段")).numericCellValue }.toSet())
            assertTrue("每段约 80 箱", fitRows.size in 140..170)
            fitRows.forEach { assertTrue(kotlin.math.abs(it.getCell(fitCol("两段残差 N·m")).numericCellValue) < 6.0) }
            // 候选扫描：每段恰有一行「最佳」，位置约 30°。
            val scan = book.getSheet("候选扫描")
            val best = (1..scan.lastRowNum).mapNotNull { scan.getRow(it) }.filter { it.getCell(7)?.stringCellValue == "最佳" }
            assertEquals(2, best.size)
            best.forEach { assertEquals(30.0, it.getCell(1).numericCellValue, 2.0) }
            val charts = book.getSheet("图表").createDrawingPatriarch().charts
            val overlay = charts.first { it.titleText?.string!!.startsWith("各段叠加") }
            assertEquals("两段各一条线", 2, overlay.chartSeries.single().seriesCount)
            val scanChart = charts.first { it.titleText?.string!!.startsWith("残差改善–候选位置") }
            assertEquals("两段 + 阈值线", 3, scanChart.chartSeries.single().seriesCount)
            assertTrue(scanChart.ctChart.xmlText().contains("prstDash"))
            val summary = book.getSheet("概要")
            fun value(label: String) = summary.getRow((0..summary.lastRowNum).first { summary.getRow(it)?.getCell(0)?.stringCellValue == label }).getCell(1)
            assertEquals("2 / 2", value("有候选的运行段").stringCellValue)
            assertEquals("转折候选（未验证）", value("末段判定").stringCellValue)
            // 结果帧（999 N·m）只列在过程数据里，不算峰值、不算运行段。
            assertEquals(2.0, value("运行段数").numericCellValue, 0.0)
            assertEquals(300.0 + 3 * 50, value("峰值扭矩 N·m").numericCellValue, 0.0)
            assertEquals(643, book.getSheet("过程数据").lastRowNum)
            val labels = (0..summary.lastRowNum).mapNotNull { summary.getRow(it)?.getCell(0)?.stringCellValue }
            assertTrue(labels.none { it.contains("平衡点已") || it.contains("已确认") })
            val torqueAngle = book.getSheet("图表").createDrawingPatriarch().charts.first { it.titleText?.string == "扭矩–转角" }
            val series = torqueAngle.chartSeries.single()
            assertEquals(2, series.seriesCount)
            val xml = torqueAngle.ctChart.xmlText()
            assertTrue("候选系列名", xml.contains("转折候选（未验证）"))
            assertTrue("候选只画菱形标记", xml.contains("diamond"))
        }
        assertAllXmlWellFormed(bytes)
    }

    /** 一万帧（每帧 1 点、每秒一帧）：帧级时间序列流式降采样，图表行数有上界，时间跨度不丢。 */
    @Test fun frameSeriesIsDownsampledButKeepsTheWholeTimeSpan() {
        val base = 1_762_300_000_000L
        val points = List(10_000) { i ->
            // 每 2000 帧一段：转角回到 0（新运行段），转速 2 °/s。
            val local = i % 2_000
            ProcessRow(i + 1L, base + i * 1_000L, 1, (local / 4).toDouble(), local * 2.0, local * 2, "扭矩转角过程", "C5 C5 01 12 00 00 10 %02X".format(i % 256))
        }
        val (bytes, _) = build(points, summary(target = null, actual = null))
        XSSFWorkbook(ByteArrayInputStream(bytes)).use { book ->
            val chartData = book.getSheet("图表数据")
            val header = chartData.getRow(0)
            fun col(name: String) = (0 until header.lastCellNum).first { header.getCell(it).stringCellValue == name }
            val seconds = (1..chartData.lastRowNum).mapNotNull { chartData.getRow(it)?.getCell(col("帧 相对首点(s)"))?.numericCellValue }
            assertTrue("帧点数 ${seconds.size}", seconds.size in SessionWorkbookWriter.MAX_FRAME_ROWS..(2 * SessionWorkbookWriter.MAX_FRAME_ROWS))
            assertEquals(0.0, seconds.first(), 0.0)
            assertTrue("覆盖到末尾：${seconds.last()}", seconds.last() > 9_990.0)
            val speeds = (1..chartData.lastRowNum).mapNotNull { chartData.getRow(it)?.getCell(col("帧间转速 °/s"))?.numericCellValue }
            assertTrue(speeds.isNotEmpty())
            speeds.forEach { assertEquals(2.0, it, 1e-9) }
            // 过程数据里每段第一帧的转速为空，其余 2 °/s。
            val data = book.getSheet("过程数据")
            assertEquals(null, data.getRow(2_001).getCell(14)?.numericCellValue)
            assertEquals(2.0, data.getRow(2_002).getCell(14).numericCellValue, 1e-9)
            assertEquals(5.0, data.getRow(10_000).getCell(5).numericCellValue, 0.0)
            val charts = book.getSheet("图表").createDrawingPatriarch().charts
            assertEquals(SessionWorkbookWriter.PROCESS_CHART_COUNT, charts.size)
            val speedChart = charts.first { it.titleText?.string!!.startsWith("转速–接收时间") }
            assertTrue(speedChart.chartSeries.single().getSeries(0).valuesData.dataRangeReference.contains("\$N\$2"))
        }
        assertAllXmlWellFormed(bytes)
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
        assertEquals(SessionWorkbookWriter.PROCESS_CHART_COUNT, stats.chartCount)
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
