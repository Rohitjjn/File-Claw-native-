package com.example.app

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.MainActivity
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Smoke test: the application launches and MainActivity is created without crashing.
 * Note: on a fresh emulator the MANAGE_EXTERNAL_STORAGE gate is visible, not bypassed —
 * the test only asserts the activity is alive.
 */
@RunWith(AndroidJUnit4::class)
class AppLaunchTest {

    @Test
    fun mainActivity_launches() {
        ActivityScenario.launch<MainActivity>(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertNotNull(activity)
            }
        }
    }
}
