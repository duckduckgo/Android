package com.duckduckgo.app.anr.internal.store

import androidx.lifecycle.LifecycleOwner
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.cash.turbine.test
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.utils.formatters.time.DatabaseDateFormatter
import java.time.LocalDateTime
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock

@RunWith(AndroidJUnit4::class)
class DatabaseCleanupObserverTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()
    private val db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, CrashANRsInternalDatabase::class.java)
        .allowMainThreadQueries()
        .build()

    private val mockLifecycleOwner = mock<LifecycleOwner>()

    private val testee = DatabaseCleanupObserver(db, coroutineTestRule.testScope, coroutineTestRule.testDispatcherProvider)

    @After
    fun after() {
        db.close()
    }

    @Test
    fun whenAppCreatedThenOldAnrsAreRemoved() = runTest {
        db.anrDao().insertAnr(aAnrEntity("1", LocalDateTime.now()))
        db.anrDao().insertAnr(aAnrEntity("2", LocalDateTime.now().minusDays(31)))

        testee.onCreate(mockLifecycleOwner)

        db.anrDao().getAnrs().test {
            assertEquals(1, awaitItem().size)
        }
    }

    @Test
    fun whenAppCreatedThenOldCrashesAreRemoved() = runTest {
        db.crashDao().insertCrash(aCrashEntity("1", LocalDateTime.now()))
        db.crashDao().insertCrash(aCrashEntity("2", LocalDateTime.now().minusDays(31)))

        testee.onCreate(mockLifecycleOwner)

        db.crashDao().getCrashes().test {
            assertEquals(1, awaitItem().size)
        }
    }

    private fun aCrashEntity(
        id: String,
        dateTime: LocalDateTime,
    ) = CrashInternalEntity(
        hash = id,
        message = "message",
        stackTrace = "stackTrace",
        timestamp = DatabaseDateFormatter.timestamp(dateTime),
        webView = "webView",
        customTab = false,
        shortName = "shortName",
        processName = "processName",
        version = "version",
    )

    private fun aAnrEntity(
        id: String,
        dateTime: LocalDateTime,
    ): AnrInternalEntity {
        return AnrInternalEntity(
            hash = id,
            timestamp = DatabaseDateFormatter.timestamp(dateTime),
            name = "name",
            file = "file",
            lineNumber = 1,
            message = "message",
            webView = "webView",
            customTab = false,
            stackTrace = "stackTrace",
        )
    }
}
