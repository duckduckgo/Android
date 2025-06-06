package com.duckduckgo.savedsites.impl.bookmarks

import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class HiddenIdsManagerTest {

    private lateinit var manager: HiddenIdsManager

    @Before
    fun setup() {
        manager = HiddenIdsManager.getInstance()
    }

    @After
    fun tearDown() {
        manager.getAll().forEach { manager.remove(it) }
    }

    @Test
    fun `add should include id in hiddenIds`() = runTest {
        manager.add("id1")
        val ids = manager.getAll()
        assertEquals(1, ids.size)
        assertTrue(ids.contains("id1"))
    }

    @Test
    fun `remove should exclude id from hiddenIds`() = runTest {
        manager.add("id1")
        manager.remove("id1")
        assertEquals(0, manager.getAll().size)
    }

    @Test
    fun `getAll returns all hidden ids`() = runTest {
        manager.add("id1")
        manager.add("id2")
        assertEquals(setOf("id1", "id2"), manager.getAll().toSet())
    }

    @Test
    fun `contains returns true only for added ids`() = runTest {
        manager.add("id1")
        assertTrue(manager.contains("id1"))
        assertFalse(manager.contains("id2"))
    }
}
