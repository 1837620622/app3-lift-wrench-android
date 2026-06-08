package com.chuankangkk.wrenchlift.data

class MeasurementRepository(
    private val dao: MeasurementDao,
) {
    fun observeRecentSessions(limit: Int = 20) = dao.observeRecentSessions(limit)

    fun observePoints(sessionId: Long) = dao.observePoints(sessionId)

    suspend fun createSession(
        projectName: String,
        lineName: String,
        sectionName: String,
        slabNo: String,
        pointNo: String,
        operatorName: String,
        deviceSn: String,
        connectionMode: String,
    ): Long = dao.insertSession(
        MeasurementEntity(
            projectName = projectName,
            lineName = lineName,
            sectionName = sectionName,
            slabNo = slabNo,
            pointNo = pointNo,
            operatorName = operatorName,
            deviceSn = deviceSn,
            startTime = System.currentTimeMillis(),
            endTime = null,
            connectionMode = connectionMode,
            resultStatus = "采集中",
            finalTorqueNm = null,
            finalAngleDeg = null,
            finalForceKn = null,
            finalPressureMpa = null,
            finalDisplacementMm = null,
            wrenchResultType = null,
            wrenchResultCode = null,
            wrenchResultLabel = null,
            wrenchEmployeeId = null,
            wrenchBoltNo = null,
            wrenchMode = null,
            targetTorqueNm = null,
            actualTorqueNm = null,
            targetAngleDeg = null,
            actualAngleDeg = null,
            torqueUpperNm = null,
            torqueLowerNm = null,
            angleUpperDeg = null,
            angleLowerDeg = null,
            wrenchResultRawHex = null,
        ),
    )

    suspend fun savePoint(entity: TorquePointEntity): Long = dao.insertTorquePoint(entity)

    suspend fun updateSessionSummary(
        sessionId: Long,
        resultStatus: String,
        finalTorqueNm: Double?,
        finalAngleDeg: Double?,
        finalForceKn: Double?,
        finalPressureMpa: Double?,
        finalDisplacementMm: Double?,
        wrenchResultType: String? = null,
        wrenchResultCode: Int? = null,
        wrenchResultLabel: String? = null,
        wrenchEmployeeId: String? = null,
        wrenchBoltNo: Int? = null,
        wrenchMode: Int? = null,
        targetTorqueNm: Double? = null,
        actualTorqueNm: Double? = null,
        targetAngleDeg: Double? = null,
        actualAngleDeg: Double? = null,
        torqueUpperNm: Double? = null,
        torqueLowerNm: Double? = null,
        angleUpperDeg: Double? = null,
        angleLowerDeg: Double? = null,
        wrenchResultRawHex: String? = null,
    ) {
        dao.updateSessionSummary(
            sessionId = sessionId,
            endTime = System.currentTimeMillis(),
            resultStatus = resultStatus,
            finalTorqueNm = finalTorqueNm,
            finalAngleDeg = finalAngleDeg,
            finalForceKn = finalForceKn,
            finalPressureMpa = finalPressureMpa,
            finalDisplacementMm = finalDisplacementMm,
            wrenchResultType = wrenchResultType,
            wrenchResultCode = wrenchResultCode,
            wrenchResultLabel = wrenchResultLabel,
            wrenchEmployeeId = wrenchEmployeeId,
            wrenchBoltNo = wrenchBoltNo,
            wrenchMode = wrenchMode,
            targetTorqueNm = targetTorqueNm,
            actualTorqueNm = actualTorqueNm,
            targetAngleDeg = targetAngleDeg,
            actualAngleDeg = actualAngleDeg,
            torqueUpperNm = torqueUpperNm,
            torqueLowerNm = torqueLowerNm,
            angleUpperDeg = angleUpperDeg,
            angleLowerDeg = angleLowerDeg,
            wrenchResultRawHex = wrenchResultRawHex,
        )
    }

    suspend fun markLatestPointEffective(sessionId: Long) {
        dao.markLatestPointEffective(sessionId)
    }
}
