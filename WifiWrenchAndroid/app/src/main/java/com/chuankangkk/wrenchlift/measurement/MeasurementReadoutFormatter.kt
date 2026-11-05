package com.chuankangkk.wrenchlift.measurement

import java.util.Locale

fun MeasurementState.torqueReadoutText(): String =
    if (hasWrenchMeasurementData()) "%.1f".format(Locale.US, currentTorqueNm) else "--"

fun MeasurementState.angleReadoutText(): String =
    if (hasWrenchMeasurementData()) "%.1f".format(Locale.US, currentAngleDeg) else "--"
