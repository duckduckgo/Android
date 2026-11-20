package com.duckduckgo.malicioussiteprotection.impl

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class ProtectionUpdateScheduleTest {
    @Test
    fun storedFractionSetsDelayAndSurvivesRecreation() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences("protection_update_schedule", Context.MODE_PRIVATE)
        prefs.edit().putInt("minimum", 0).putInt("maximum", 50_000).commit()
        val schedule = ProtectionUpdateSchedule(context)

        for (minutes in listOf(20L, 480L, 720L)) {
            assertEquals(0, schedule.delayMillis("minimum", minutes))
            assertEquals(TimeUnit.MINUTES.toMillis(minutes) / 20, schedule.delayMillis("maximum", minutes))
        }
        val delay = schedule.delayMillis("random", 480)
        assertTrue(delay in 0..TimeUnit.MINUTES.toMillis(24))
        assertEquals(delay, ProtectionUpdateSchedule(context).delayMillis("random", 480))
    }

    @Test
    fun clearingOneVariantDrawsANewPhaseAndKeepsOtherVariants() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences("protection_update_schedule", Context.MODE_PRIVATE)
        prefs.edit().putInt("reenabled", -1).putInt("other", 25_000).commit()
        val schedule = ProtectionUpdateSchedule(context)
        val otherDelay = schedule.delayMillis("other", 480)

        schedule.clear("reenabled")
        val newDelay = schedule.delayMillis("reenabled", 480)

        assertTrue(newDelay in 0..TimeUnit.MINUTES.toMillis(24))
        assertEquals(newDelay, schedule.delayMillis("reenabled", 480))
        assertEquals(otherDelay, schedule.delayMillis("other", 480))
    }
}
