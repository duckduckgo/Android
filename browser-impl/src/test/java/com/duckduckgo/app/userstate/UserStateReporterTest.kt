/*
 * Copyright (c) 2024 DuckDuckGo
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.duckduckgo.app.userstate

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.duckduckgo.app.tabs.model.TabDataRepository
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.common.test.CoroutineTestRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@ExperimentalCoroutinesApi
class UserStateReporterTest {
    @get:Rule
    @Suppress("unused")
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    var coroutinesTestRule = CoroutineTestRule()

    private val dispatcherProvider = coroutinesTestRule.testDispatcherProvider
    private val repository = mock<TabDataRepository>()
    private val appBuildConfig = mock<AppBuildConfig>()

    @Before
    fun setUp() {
    }

    @Test
    fun verifyUserIsNewWhenFirstInstallTimeEqualsLastInstallTime() = runTest {
        initializeSut(isNewInstall = true)

        verify(repository).setIsUserNew(true)
    }

    @Test
    fun verifyUserExistingWhenFirstInstallTimeDoesNotEqualLastInstallTime() = runTest {
        initializeSut(isNewInstall = false)

        verify(repository).setIsUserNew(false)
    }

    private fun initializeSut(isNewInstall: Boolean) {
        whenever(appBuildConfig.isNewInstall()).thenReturn(isNewInstall)

        val userStateReporter = UserStateReporter(dispatcherProvider, repository, appBuildConfig, TestScope())

        userStateReporter.onCreate(mock())
    }
}
