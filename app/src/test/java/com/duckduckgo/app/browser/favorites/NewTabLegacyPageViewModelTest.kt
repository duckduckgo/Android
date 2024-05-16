package com.duckduckgo.app.browser.favorites

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.duckduckgo.common.test.CoroutineTestRule
import org.junit.Rule

class NewTabLegacyPageViewModelTest {

    @get:Rule
    @Suppress("unused")
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    var coroutinesTestRule = CoroutineTestRule()
}
