package com.duckduckgo.app.browser.mediaplayback.store

import com.duckduckgo.common.test.CoroutineTestRule
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.ArgumentMatchers.anyList
import org.mockito.kotlin.mock
import org.mockito.kotlin.reset
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class RealMediaPlaybackRepositoryTest {
    @get:Rule
    var coroutineRule = CoroutineTestRule()

    lateinit var testee: RealMediaPlaybackRepository

    private val mockMediaPlaybackDatabase: MediaPlaybackDatabase = mock()
    private val mockMediaPlaybackDao: MediaPlaybackDao = mock()

    @Before
    fun before() {
        whenever(mockMediaPlaybackDatabase.mediaPlaybackDao()).thenReturn(mockMediaPlaybackDao)
        initRepository()
    }

    @Test
    fun whenRepositoryIsCreatedThenValuesLoadedIntoMemory() {
        givenMediaPlaybackDaoContainsEntities()

        initRepository()

        assertEquals(mediaPlaybackExceptionEntity.toFeatureException(), testee.exceptions.first())
    }

    @Test
    fun whenUpdateAllThenUpdateAllCalled() = runTest {
        initRepository()

        testee.updateAll(listOf())

        verify(mockMediaPlaybackDao).updateAll(anyList())
    }

    @Test
    fun whenUpdateAllThenPreviousValuesAreClearedAndNewValuesUpdated() = runTest {
        givenMediaPlaybackDaoContainsEntities()

        initRepository()
        assertEquals(1, testee.exceptions.size)

        reset(mockMediaPlaybackDao)

        testee.updateAll(listOf())

        assertEquals(0, testee.exceptions.size)
    }

    private fun initRepository() {
        testee = RealMediaPlaybackRepository(
            mockMediaPlaybackDao,
            TestScope(),
            coroutineRule.testDispatcherProvider,
        )
    }

    private fun givenMediaPlaybackDaoContainsEntities() {
        whenever(mockMediaPlaybackDao.getAll()).thenReturn(listOf(mediaPlaybackExceptionEntity))
    }

    companion object {
        val mediaPlaybackExceptionEntity = MediaPlaybackExceptionEntity(
            domain = "example.com",
        )
    }
}
