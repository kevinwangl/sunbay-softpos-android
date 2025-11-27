package com.sunbay.softpos

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sunbay.softpos.data.DeviceManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DeviceRegistrationTest {

    @Test
    fun testDeviceRegistration() = runBlocking {
        // Context of the app under test.
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val deviceManager = DeviceManager(appContext)

        val result = deviceManager.registerDevice("http://10.0.2.2:8080/")
        
        // Note: This test assumes the backend is reachable. 
        // If running on emulator, ensure backend is at http://10.0.2.2:8080
        assertTrue("Registration should succeed: ${result.exceptionOrNull()?.message}", result.isSuccess)
    }
}
