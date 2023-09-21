/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.app.browser.autofill

import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.autofill.api.AutofillEventListener
import com.duckduckgo.autofill.api.EmailProtectionChooserDialog
import com.duckduckgo.autofill.api.EmailProtectionChooserDialog.UseEmailResultType.DoNotUseEmailProtection
import com.duckduckgo.autofill.api.EmailProtectionChooserDialog.UseEmailResultType.UsePersonalEmailAddress
import com.duckduckgo.autofill.api.EmailProtectionChooserDialog.UseEmailResultType.UsePrivateAliasAddress
import com.duckduckgo.autofill.api.email.EmailManager
import com.duckduckgo.autofill.impl.email.ResultHandlerEmailProtectionChooseEmail
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
class ResultHandlerEmailProtectionChooseEmailTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val callback: AutofillEventListener = mock()

    private val appBuildConfig: AppBuildConfig = mock()
    private val emailManager: EmailManager = mock()

    private val testee = ResultHandlerEmailProtectionChooseEmail(
        appBuildConfig = appBuildConfig,
        emailManager = emailManager,
        dispatchers = coroutineTestRule.testDispatcherProvider,
        appCoroutineScope = coroutineTestRule.testScope,
    )

    @Before
    fun before() {
        whenever(emailManager.getEmailAddress()).thenReturn("personal-example@duck.com")
        whenever(emailManager.getAlias()).thenReturn("private-example@duck.com")
    }

    @Test
    fun whenUserSelectedToUsePersonalAddressThenCorrectCallbackInvoked() = runTest {
        val bundle = bundle(result = UsePersonalEmailAddress)
        testee.processResult(bundle, context, "tab-id-123", Fragment(), callback)
        verify(callback).onUseEmailProtectionPersonalAddress(any(), any())
    }

    @Test
    fun whenUserSelectedToUsePrivateAliasAddressThenCorrectCallbackInvoked() = runTest {
        val bundle = bundle(result = UsePrivateAliasAddress)
        testee.processResult(bundle, context, "tab-id-123", Fragment(), callback)
        verify(callback).onUseEmailProtectionPrivateAlias(any(), any())
    }

    @Test
    fun whenUserRejectedUsingAnyDuckAddressThenCorrectCallbackInvoked() {
        val bundle = bundle(result = DoNotUseEmailProtection)
        testee.processResult(bundle, context, "tab-id-123", Fragment(), callback)
        verify(callback).onRejectToUseEmailProtection(any())
    }

    @Test
    fun whenUrlMissingFromBundleThenExceptionThrown() = runTest {
        val bundle = bundle(url = null, result = UsePersonalEmailAddress)
        testee.processResult(bundle, context, "tab-id-123", Fragment(), callback)
        verifyNoInteractions(callback)
    }

    @Test
    fun whenResultTypeMissingFromBundleThenExceptionThrown() = runTest {
        val bundle = bundle(result = null)
        testee.processResult(bundle, context, "tab-id-123", Fragment(), callback)
        verifyNoInteractions(callback)
    }

    @Test
    fun whenUserSelectedToUsePrivateAliasAddressThenSetNewLastUsedDateCalled() {
        val bundle = bundle(result = UsePrivateAliasAddress)
        testee.processResult(bundle, context, "tab-id-123", Fragment(), callback)
        verify(emailManager).setNewLastUsedDate()
    }

    @Test
    fun whenUserSelectedToUsePersonalDuckAddressThenSetNewLastUsedDateCalled() {
        val bundle = bundle(result = UsePersonalEmailAddress)
        testee.processResult(bundle, context, "tab-id-123", Fragment(), callback)
        verify(emailManager).setNewLastUsedDate()
    }

    private fun bundle(
        url: String? = "example.com",
        result: EmailProtectionChooserDialog.UseEmailResultType?,
    ): Bundle {
        return Bundle().also {
            it.putString(EmailProtectionChooserDialog.KEY_URL, url)
            it.putParcelable(EmailProtectionChooserDialog.KEY_RESULT, result)
        }
    }
}
