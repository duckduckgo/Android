package com.duckduckgo.app.anr.ndk

import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.data.store.api.FakeSharedPreferencesProvider
import com.duckduckgo.feature.toggles.api.Toggle
import com.squareup.moshi.Moshi
import kotlinx.coroutines.test.runTest
import org.junit.Assert
import org.junit.Rule
import org.junit.Test

class NativeCrashFeatureMultiProcessStoreTest {

    @get:Rule var coroutineRule = CoroutineTestRule()

    private val store = NativeCrashFeatureMultiProcessStore(
        coroutineRule.testScope,
        coroutineRule.testDispatcherProvider,
        FakeSharedPreferencesProvider(),
        Moshi.Builder().build(),
    )

    @Test
    fun `test set value`() = runTest {
        val expected = Toggle.State(enable = true)
        store.set("key", expected)

        Assert.assertEquals(expected, store.get("key"))
    }

    @Test
    fun `test get missing value`() = runTest {
        Assert.assertNull(store.get("key"))
    }

    @Test
    fun `test get when value is not present`() {
        val expected = Toggle.State(enable = true)
        store.set("key", expected)

        Assert.assertNull(store.get("wrong key"))
    }
}
