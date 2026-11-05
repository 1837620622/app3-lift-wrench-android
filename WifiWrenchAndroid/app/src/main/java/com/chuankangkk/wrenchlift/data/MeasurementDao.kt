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

    @Query(
        """
        UPDATE torque_points
        SET isEffectivePoint = 0
        WHERE id = (
            SELECT id FROM torque_points
            WHERE sessionId = :sessionId AND isEffectivePoint = 1
            ORDER BY timestampMillis DESC, id DESC
            LIMIT 1
        )
        """,
    )
    suspend fun unmarkLatestEffectivePoint(sessionId: Long)

    @Query("SELECT COUNT(*) FROM torque_points WHERE sessionId = :sessionId AND isEffectivePoint = 1")
    suspend fun countEffectivePoints(sessionId: Long): Int

    @Query("SELECT * FROM measurement_sessions WHERE actualTorqueNm IS NOT NULL OR EXISTS (SELECT 1 FROM torque_points WHERE sessionId = measurement_sessions.id) ORDER BY startTime DESC, id DESC LIMIT :limit")
    fun observeRecentSessions(limit: Int = 20): Flow<List<MeasurementEntity>>

    @Query("SELECT * FROM torque_points WHERE sessionId = :sessionId ORDER BY id ASC")
    fun observePoints(sessionId: Long): Flow<List<TorquePointEntity>>
    @Query("SELECT * FROM measurement_sessions WHERE id = :id")
    suspend fun getSession(id: Long): MeasurementEntity?

    @Query("UPDATE measurement_sessions SET deviceSn = :sn WHERE id = :id")
    suspend fun updateDeviceSn(id: Long, sn: String)

    @Query("SELECT MAX(id) FROM torque_points WHERE sessionId = :sessionId")
    suspend fun lastPointId(sessionId: Long): Long?

    @Query("SELECT COUNT(*) FROM torque_points WHERE sessionId = :sessionId AND id <= :throughId")
    suspend fun countPointsThrough(sessionId: Long, throughId: Long): Long

    @Query("SELECT * FROM torque_points WHERE sessionId = :sessionId AND id > :afterId AND id <= :throughId ORDER BY id LIMIT :limit")
    suspend fun pointsPage(sessionId: Long, afterId: Long, throughId: Long, limit: Int = 1000): List<TorquePointEntity>
}
