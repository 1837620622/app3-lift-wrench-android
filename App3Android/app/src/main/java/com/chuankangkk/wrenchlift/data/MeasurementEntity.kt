package com.chuankangkk.wrenchlift.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "measurement_sessions")
data class MeasurementEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val projectName: String,
    val lineName: String,
    val sectionName: String,
    val slabNo: String,
    val pointNo: String,
    val operatorName: String,
    val deviceSn: String,
    val startTime: Long,
    val endTime: Long?,
    val connectionMode: String,
    val resultStatus: String,
    val finalTorqueNm: Double?,
    val finalAngleDeg: Double?,
    val finalForceKn: Double?,
    val finalPressureMpa: Double?,
    val finalDisplacementMm: Double?,
    val wrenchResultType: String?,
    val wrenchResultCode: Int?,
    val wrenchResultLabel: String?,
    val wrenchEmployeeId: String?,
    val wrenchBoltNo: Int?,
    val wrenchMode: Int?,
    val targetTorqueNm: Double?,
    val actualTorqueNm: Double?,
    val targetAngleDeg: Double?,
    val actualAngleDeg: Double?,
    val torqueUpperNm: Double?,
    val torqueLowerNm: Double?,
    val angleUpperDeg: Double?,
    val angleLowerDeg: Double?,
    val wrenchResultRawHex: String?,
)
