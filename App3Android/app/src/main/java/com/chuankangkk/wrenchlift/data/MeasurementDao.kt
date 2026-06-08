package com.chuankangkk.wrenchlift.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MeasurementDao {
    @Insert
    suspend fun insertSession(entity: MeasurementEntity): Long

    @Insert
    suspend fun insertTorquePoint(entity: TorquePointEntity): Long

    @Query(
        """
        UPDATE measurement_sessions
        SET endTime = :endTime,
            resultStatus = :resultStatus,
            finalTorqueNm = :finalTorqueNm,
            finalAngleDeg = :finalAngleDeg,
            finalForceKn = :finalForceKn,
            finalPressureMpa = :finalPressureMpa,
            finalDisplacementMm = :finalDisplacementMm,
            wrenchResultType = :wrenchResultType,
            wrenchResultCode = :wrenchResultCode,
            wrenchResultLabel = :wrenchResultLabel,
            wrenchEmployeeId = :wrenchEmployeeId,
            wrenchBoltNo = :wrenchBoltNo,
            wrenchMode = :wrenchMode,
            targetTorqueNm = :targetTorqueNm,
            actualTorqueNm = :actualTorqueNm,
            targetAngleDeg = :targetAngleDeg,
            actualAngleDeg = :actualAngleDeg,
            torqueUpperNm = :torqueUpperNm,
            torqueLowerNm = :torqueLowerNm,
            angleUpperDeg = :angleUpperDeg,
            angleLowerDeg = :angleLowerDeg,
            wrenchResultRawHex = :wrenchResultRawHex
        WHERE id = :sessionId
        """,
    )
    suspend fun updateSessionSummary(
        sessionId: Long,
        endTime: Long,
        resultStatus: String,
        finalTorqueNm: Double?,
        finalAngleDeg: Double?,
        finalForceKn: Double?,
        finalPressureMpa: Double?,
        finalDisplacementMm: Double?,
        wrenchResultType: String?,
        wrenchResultCode: Int?,
        wrenchResultLabel: String?,
        wrenchEmployeeId: String?,
        wrenchBoltNo: Int?,
        wrenchMode: Int?,
        targetTorqueNm: Double?,
        actualTorqueNm: Double?,
        targetAngleDeg: Double?,
        actualAngleDeg: Double?,
        torqueUpperNm: Double?,
        torqueLowerNm: Double?,
        angleUpperDeg: Double?,
        angleLowerDeg: Double?,
        wrenchResultRawHex: String?,
    )

    @Query(
        """
        UPDATE torque_points
        SET isEffectivePoint = 1
        WHERE id = (
            SELECT id FROM torque_points
            WHERE sessionId = :sessionId
            ORDER BY timestampMillis DESC, id DESC
            LIMIT 1
        )
        """,
    )
    suspend fun markLatestPointEffective(sessionId: Long)

    @Query("SELECT * FROM measurement_sessions ORDER BY startTime DESC LIMIT :limit")
    fun observeRecentSessions(limit: Int = 20): Flow<List<MeasurementEntity>>

    @Query("SELECT * FROM torque_points WHERE sessionId = :sessionId ORDER BY timestampMillis ASC")
    fun observePoints(sessionId: Long): Flow<List<TorquePointEntity>>
}
