package com.chuankangkk.wrenchlift.measurement

import android.app.Application
import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ConnectionPreferencesTest {
    @Test fun replacementDeviceGuidanceSurvivesViewModelRecreation() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val app: Application = RuntimeEnvironment.getApplication()
        val prefs = app.getSharedPreferences("connection_preferences", 0)
        prefs.edit().clear().commit()
        val firstStore = ViewModelStore()
        val secondStore = ViewModelStore()
        try {
            val first = MeasurementViewModel(app)
            firstStore.put("measurement", first)
            first.updateTargetWifiName("Wrench-B")
            first.updateHost("192.168.8.1")
            first.updatePort("8899")
            val second = MeasurementViewModel(app)
            secondStore.put("measurement", second)
            assertEquals("Wrench-B", second.state.value.targetWifiName)
            assertEquals("192.168.8.1", second.state.value.host)
            assertEquals("8899", second.state.value.port)
        } finally {
            firstStore.clear()
            secondStore.clear()
            prefs.edit().clear().commit()
            Dispatchers.resetMain()
        }
    }
}
