package com.duckduckgo.subscriptions.impl.settings.views

import app.cash.turbine.test
import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.subscriptions.impl.settings.views.ProSettingBuyViewModel.Command.OpenBuyScreen
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@ExperimentalCoroutinesApi
class ProSettingBuyViewModelTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private lateinit var viewModel: ProSettingBuyViewModel

    @Before
    fun before() {
        viewModel = ProSettingBuyViewModel()
    }

    @Test
    fun whenOnBuyThenCommandSent() = runTest {
        viewModel.commands().test {
            viewModel.onBuyClicked()
            assertTrue(awaitItem() is OpenBuyScreen)
            cancelAndConsumeRemainingEvents()
        }
    }
}
