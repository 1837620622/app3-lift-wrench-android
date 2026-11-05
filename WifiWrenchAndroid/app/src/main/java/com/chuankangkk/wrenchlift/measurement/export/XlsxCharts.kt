package com.chuankangkk.wrenchlift.measurement.export

/** 原生 Excel 图表（DrawingML chart part）。数据通过单元格区域引用，另附缓存值便于手机预览器直接显示。 */
internal sealed interface ChartSpec {
    val title: String

    data class Scatter(
        override val title: String,
        val seriesName: String,
        val xTitle: String,
        val yTitle: String,
        val xRef: String,
        val yRef: String,
        /** 与区域逐行对应；null 表示空行（段间断点）。 */
        val xValues: List<Double?>,
        val yValues: List<Double?>,
        val color: String,
    ) : ChartSpec

    data class Bar(
        override val title: String,
        val seriesName: String,
        val yTitle: String,
        val categoryRef: String,
        val valueRef: String,
        val categories: List<String>,
        val values: List<Double?>,
        val color: String,
    ) : ChartSpec
}

internal object ChartXml {
    private const val X_AXIS_ID = 510001
    private const val Y_AXIS_ID = 510002

    fun render(spec: ChartSpec): String = buildString {
        append(Xml.HEADER)
        append("<c:chartSpace xmlns:c=\"${Xml.NS_CHART}\" xmlns:a=\"${Xml.NS_A}\" xmlns:r=\"${Xml.NS_REL}\">")
        append("<c:roundedCorners val=\"0\"/>")
        append("<c:chart>")
        append(title(spec.title, 1300))
        append("<c:autoTitleDeleted val=\"0\"/>")
        append("<c:plotArea><c:layout/>")
        when (spec) {
            is ChartSpec.Scatter -> scatter(spec)
            is ChartSpec.Bar -> bar(spec)
        }
        append("</c:plotArea>")
        append("<c:plotVisOnly val=\"1\"/><c:dispBlanksAs val=\"gap\"/>")
        append("</c:chart>")
        append("<c:spPr><a:solidFill><a:srgbClr val=\"FFFFFF\"/></a:solidFill><a:ln><a:solidFill><a:srgbClr val=\"D7DDE3\"/></a:solidFill></a:ln></c:spPr>")
        append("</c:chartSpace>")
    }

    private fun StringBuilder.scatter(spec: ChartSpec.Scatter) {
        append("<c:scatterChart><c:scatterStyle val=\"lineMarker\"/><c:varyColors val=\"0\"/>")
        append("<c:ser><c:idx val=\"0\"/><c:order val=\"0\"/>")
        append("<c:tx><c:v>${Xml.escape(spec.seriesName)}</c:v></c:tx>")
        append(lineProps(spec.color))
        append("<c:marker><c:symbol val=\"none\"/></c:marker>")
        append("<c:xVal>").append(numRef(spec.xRef, spec.xValues)).append("</c:xVal>")
        append("<c:yVal>").append(numRef(spec.yRef, spec.yValues)).append("</c:yVal>")
        append("<c:smooth val=\"0\"/></c:ser>")
        append("<c:axId val=\"$X_AXIS_ID\"/><c:axId val=\"$Y_AXIS_ID\"/></c:scatterChart>")
        append(valueAxis(X_AXIS_ID, Y_AXIS_ID, "b", spec.xTitle, gridlines = false))
        append(valueAxis(Y_AXIS_ID, X_AXIS_ID, "l", spec.yTitle, gridlines = true))
    }

    private fun StringBuilder.bar(spec: ChartSpec.Bar) {
        append("<c:barChart><c:barDir val=\"col\"/><c:grouping val=\"clustered\"/><c:varyColors val=\"0\"/>")
        append("<c:ser><c:idx val=\"0\"/><c:order val=\"0\"/>")
        append("<c:tx><c:v>${Xml.escape(spec.seriesName)}</c:v></c:tx>")
        append("<c:spPr><a:solidFill><a:srgbClr val=\"${spec.color}\"/></a:solidFill></c:spPr>")
        append("<c:invertIfNegative val=\"0\"/>")
        append("<c:cat><c:strRef><c:f>${Xml.escape(spec.categoryRef)}</c:f><c:strCache><c:ptCount val=\"${spec.categories.size}\"/>")
        spec.categories.forEachIndexed { index, value -> append("<c:pt idx=\"$index\"><c:v>${Xml.escape(value)}</c:v></c:pt>") }
        append("</c:strCache></c:strRef></c:cat>")
        append("<c:val>").append(numRef(spec.valueRef, spec.values)).append("</c:val>")
        append("</c:ser><c:gapWidth val=\"80\"/>")
        append("<c:axId val=\"$X_AXIS_ID\"/><c:axId val=\"$Y_AXIS_ID\"/></c:barChart>")
        append("<c:catAx><c:axId val=\"$X_AXIS_ID\"/><c:scaling><c:orientation val=\"minMax\"/></c:scaling><c:delete val=\"0\"/>")
        append("<c:axPos val=\"b\"/><c:numFmt formatCode=\"General\" sourceLinked=\"0\"/><c:majorTickMark val=\"out\"/><c:minorTickMark val=\"none\"/>")
        append("<c:tickLblPos val=\"nextTo\"/>").append(axisLine())
        append("<c:crossAx val=\"$Y_AXIS_ID\"/><c:crosses val=\"autoZero\"/><c:auto val=\"1\"/><c:lblAlgn val=\"ctr\"/><c:lblOffset val=\"100\"/><c:noMultiLvlLbl val=\"0\"/></c:catAx>")
        append(valueAxis(Y_AXIS_ID, X_AXIS_ID, "l", spec.yTitle, gridlines = true, crossBetween = "between"))
    }

    private fun valueAxis(
        id: Int,
        crossId: Int,
        position: String,
        axisTitle: String,
        gridlines: Boolean,
        crossBetween: String = "midCat",
    ): String = buildString {
        append("<c:valAx><c:axId val=\"$id\"/><c:scaling><c:orientation val=\"minMax\"/></c:scaling><c:delete val=\"0\"/>")
        append("<c:axPos val=\"$position\"/>")
        if (gridlines) append("<c:majorGridlines><c:spPr><a:ln w=\"6350\"><a:solidFill><a:srgbClr val=\"E3E8EC\"/></a:solidFill></a:ln></c:spPr></c:majorGridlines>")
        append(title(axisTitle, 1000))
        append("<c:numFmt formatCode=\"General\" sourceLinked=\"1\"/><c:majorTickMark val=\"out\"/><c:minorTickMark val=\"none\"/><c:tickLblPos val=\"nextTo\"/>")
        append(axisLine())
        append("<c:crossAx val=\"$crossId\"/><c:crosses val=\"autoZero\"/><c:crossBetween val=\"$crossBetween\"/></c:valAx>")
    }

    private fun axisLine(): String =
        "<c:spPr><a:ln w=\"9525\"><a:solidFill><a:srgbClr val=\"8A96A3\"/></a:solidFill></a:ln></c:spPr>"

    private fun lineProps(color: String): String =
        "<c:spPr><a:ln w=\"19050\" cap=\"rnd\"><a:solidFill><a:srgbClr val=\"$color\"/></a:solidFill><a:round/></a:ln></c:spPr>"

    private fun title(text: String, size: Int): String =
        "<c:title><c:tx><c:rich><a:bodyPr/><a:lstStyle/><a:p><a:pPr><a:defRPr sz=\"$size\" b=\"1\"/></a:pPr>" +
            "<a:r><a:rPr lang=\"zh-CN\" sz=\"$size\" b=\"1\"/><a:t>${Xml.escape(text)}</a:t></a:r></a:p></c:rich></c:tx>" +
            "<c:overlay val=\"0\"/></c:title>"

    private fun numRef(ref: String, values: List<Double?>): String = buildString {
        append("<c:numRef><c:f>${Xml.escape(ref)}</c:f><c:numCache><c:formatCode>General</c:formatCode>")
        append("<c:ptCount val=\"${values.size}\"/>")
        values.forEachIndexed { index, value ->
            val formatted = value?.let(Xml::number) ?: return@forEachIndexed
            append("<c:pt idx=\"$index\"><c:v>$formatted</c:v></c:pt>")
        }
        append("</c:numCache></c:numRef>")
    }

    /** 图表页：两列网格摆放，每个图约 9 列 × 20 行。 */
    fun drawing(chartCount: Int): String = buildString {
        append(Xml.HEADER)
        append("<xdr:wsDr xmlns:xdr=\"${Xml.NS_XDR}\" xmlns:a=\"${Xml.NS_A}\">")
        for (index in 0 until chartCount) {
            val col = (index % 2) * 10
            val row = 2 + (index / 2) * 22
            append("<xdr:twoCellAnchor editAs=\"oneCell\">")
            append("<xdr:from><xdr:col>$col</xdr:col><xdr:colOff>0</xdr:colOff><xdr:row>$row</xdr:row><xdr:rowOff>0</xdr:rowOff></xdr:from>")
            append("<xdr:to><xdr:col>${col + 9}</xdr:col><xdr:colOff>0</xdr:colOff><xdr:row>${row + 20}</xdr:row><xdr:rowOff>0</xdr:rowOff></xdr:to>")
            append("<xdr:graphicFrame macro=\"\"><xdr:nvGraphicFramePr><xdr:cNvPr id=\"${index + 2}\" name=\"图表 ${index + 1}\"/><xdr:cNvGraphicFramePr/></xdr:nvGraphicFramePr>")
            append("<xdr:xfrm><a:off x=\"0\" y=\"0\"/><a:ext cx=\"0\" cy=\"0\"/></xdr:xfrm>")
            append("<a:graphic><a:graphicData uri=\"${Xml.NS_CHART}\"><c:chart xmlns:c=\"${Xml.NS_CHART}\" xmlns:r=\"${Xml.NS_REL}\" r:id=\"rId${index + 1}\"/></a:graphicData></a:graphic>")
            append("</xdr:graphicFrame><xdr:clientData/></xdr:twoCellAnchor>")
        }
        append("</xdr:wsDr>")
    }
}
