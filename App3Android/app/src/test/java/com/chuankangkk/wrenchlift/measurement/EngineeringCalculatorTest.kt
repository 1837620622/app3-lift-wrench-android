package com.chuankangkk.wrenchlift.measurement

import org.junit.Assert.assertEquals
import org.junit.Test

class EngineeringCalculatorTest {
    @Test
    fun screwForceUsesTorqueEfficiencyRatioAndLead() {
        val forceKn = EngineeringCalculator.forceKnFromTorque(
            torqueNm = 100.0,
            screwLeadMm = 5.0,
            efficiency = 0.8,
            gearRatio = 1.0,
        )

        assertEquals(100.5309, forceKn, 0.0001)
    }

    @Test
    fun pressureAndDisplacementKeepEngineeringUnitsConsistent() {
        val pressureMpa = EngineeringCalculator.pressureMpa(
            forceKn = 120.0,
            effectiveAreaMm2 = 12000.0,
        )
        val displacementMm = EngineeringCalculator.displacementMm(
            angleDeg = 180.0,
            screwLeadMm = 5.0,
            gearRatio = 1.0,
        )

        assertEquals(10.0, pressureMpa ?: 0.0, 0.0001)
        assertEquals(2.5, displacementMm, 0.0001)
    }
}
