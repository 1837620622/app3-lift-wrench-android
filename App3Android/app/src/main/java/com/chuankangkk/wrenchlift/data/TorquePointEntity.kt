package com.chuankangkk.wrenchlift.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "torque_points",
    foreignKeys = [
        ForeignKey(
            entity = MeasurementEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sessionId"), Index("timestampMillis")],
)
data class TorquePointEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val sessionId: Long,
    val timestampMillis: Long,
    val boltNo: Int,
    val torqueNm: Double,
    val angleRaw: Int,
    val angleDeg: Double,
    val forceKn: Double?,
    val pressureMpa: Double?,
    val displacementMm: Double,
    val isEffectivePoint: Boolean,
    val rawHex: String,
)
