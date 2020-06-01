/*
 * Copyright (c) 2019 DuckDuckGo
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

@file:Suppress("RemoveExplicitTypeArguments")

package com.duckduckgo.app.notification

import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.impl.utils.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.app.notification.NotificationScheduler.ClearDataNotificationWorker
import com.duckduckgo.app.notification.NotificationScheduler.PrivacyNotificationWorker
import com.duckduckgo.app.notification.model.SchedulableNotification
import com.duckduckgo.app.notification.model.SearchNotification
import com.duckduckgo.app.statistics.Variant
import com.duckduckgo.app.statistics.VariantManager
import com.duckduckgo.app.statistics.VariantManager.VariantFeature.DripNotification
import com.duckduckgo.app.statistics.VariantManager.VariantFeature.Day1PrivacyNotification
import com.duckduckgo.app.statistics.VariantManager.VariantFeature.Day1ArticleNotification
import com.duckduckgo.app.statistics.VariantManager.VariantFeature.Day1BlogNotification
import com.duckduckgo.app.statistics.VariantManager.VariantFeature.Day1AppFeatureNotification
import com.duckduckgo.app.statistics.VariantManager.VariantFeature.Day3ClearDataNotification
import com.duckduckgo.app.statistics.VariantManager.Companion.DEFAULT_VARIANT
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.reflect.jvm.jvmName

class AndroidNotificationSchedulerTest {

    @ExperimentalCoroutinesApi
    @get:Rule
    var coroutinesTestRule = CoroutineTestRule()

    private val variantManager: VariantManager = mock()
    private val clearNotification: SchedulableNotification = mock()
    private val privacyNotification: SchedulableNotification = mock()
    private val searchPromptNotification: SearchNotification = mock()
    private val articleNotification: SchedulableNotification = mock()
    private val blogNotification: SchedulableNotification = mock()
    private val appFeatureNotification: SchedulableNotification = mock()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var workManager: WorkManager
    private lateinit var testee: NotificationScheduler

    @Before
    fun before() {
        initializeWorkManager()
        whenever(variantManager.getVariant(any())).thenReturn(DEFAULT_VARIANT)
        testee = NotificationScheduler(
            workManager,
            clearNotification,
            privacyNotification,
            searchPromptNotification,
            articleNotification,
            blogNotification,
            appFeatureNotification,
            variantManager
        )
    }

    // https://developer.android.com/topic/libraries/architecture/workmanager/how-to/integration-testing
    private fun initializeWorkManager() {
        val config = Configuration.Builder()
            .setMinimumLoggingLevel(Log.DEBUG)
            .setExecutor(SynchronousExecutor())
            .build()

        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)
        workManager = WorkManager.getInstance(context)
    }

    @Test
    fun whenPrivacyNotificationClearDataCanShowThenPrivacyNotificationIsScheduled() = runBlocking<Unit> {
        whenever(privacyNotification.canShow()).thenReturn(true)
        whenever(clearNotification.canShow()).thenReturn(true)
        testee.scheduleNextNotification()

        assertUnusedAppNotificationScheduled(PrivacyNotificationWorker::class.jvmName)
    }

    @Test
    fun whenPrivacyNotificationCanShowButClearDataCannotThenPrivacyNotificationIsScheduled() = runBlocking<Unit> {
        whenever(variantManager.getVariant(any())).thenReturn(DEFAULT_VARIANT)
        whenever(privacyNotification.canShow()).thenReturn(true)
        whenever(clearNotification.canShow()).thenReturn(false)
        testee.scheduleNextNotification()

        assertUnusedAppNotificationScheduled(PrivacyNotificationWorker::class.jvmName)
    }

    @Test
    fun whenPrivacyNotificationCannotShowAndClearNotificationCanShowThenClearNotificationIsScheduled() = runBlocking<Unit> {
    fun whenPrivacyNotificationCannotShowButSearchPromptAndClearNotificationCanShowThenBothAreScheduled() = runBlocking<Unit> {
        whenever(variantManager.getVariant(any())).thenReturn(DEFAULT_VARIANT)
        whenever(privacyNotification.canShow()).thenReturn(false)
        whenever(clearNotification.canShow()).thenReturn(true)
        testee.scheduleNextNotification()

        assertUnusedAppNotificationScheduled(ClearDataNotificationWorker::class.jvmName)
    }

    @Test
    fun whenPrivacyNotificationAndClearNotificationCannotShowThenNoNotificationScheduled() = runBlocking<Unit> {
        whenever(privacyNotification.canShow()).thenReturn(false)
        whenever(clearNotification.canShow()).thenReturn(false)
        testee.scheduleNextNotification()

        assertNoUnusedAppNotificationScheduled()
    }

    @Test
    fun whenArticleVariantAndNoNotificationCanShowThenNoNotificationScheduled() = runBlocking<Unit> {
        setArticleVariant()
        whenever(articleNotification.canShow()).thenReturn(false)
        whenever(clearNotification.canShow()).thenReturn(false)
        whenever(searchPromptNotification.canShow()).thenReturn(false)
        testee.scheduleNextNotification()

        assertNoNotificationScheduled()
    }

    @Test
    fun whenBlogVariantAndBlogNotificationClearDataAndSearchPromptCanShowThenBothAreScheduled() = runBlocking<Unit> {
        setBlogVariant()
        whenever(blogNotification.canShow()).thenReturn(true)
        whenever(clearNotification.canShow()).thenReturn(true)
        whenever(searchPromptNotification.canShow()).thenReturn(true)

        testee.scheduleNextNotification()

        assertUnusedAppNotificationScheduled(BlogNotificationWorker::class.jvmName)
        assertContinuousAppUseNotificationScheduled(SearchPromptNotificationWorker::class.jvmName)
    }

    @Test
    fun whenBlogVariantAndBlogNotificationClearDataCanShowButSearchPromptCannotThenBlogNotificationScheduled() = runBlocking<Unit> {
        setBlogVariant()
        whenever(blogNotification.canShow()).thenReturn(true)
        whenever(clearNotification.canShow()).thenReturn(true)
        whenever(searchPromptNotification.canShow()).thenReturn(false)

        testee.scheduleNextNotification()

        assertUnusedAppNotificationScheduled(BlogNotificationWorker::class.jvmName)
        assertNoContinuousAppNotificationScheduled()
    }

    @Test
    fun whenBlogVariantAndBlogNotificationAndSearchPromptCanShowButClearDataCannotShowThenBothAreScheduled() = runBlocking<Unit> {
        setBlogVariant()
        whenever(blogNotification.canShow()).thenReturn(true)
        whenever(clearNotification.canShow()).thenReturn(false)
        whenever(searchPromptNotification.canShow()).thenReturn(true)

        testee.scheduleNextNotification()

        assertUnusedAppNotificationScheduled(BlogNotificationWorker::class.jvmName)
        assertContinuousAppUseNotificationScheduled(SearchPromptNotificationWorker::class.jvmName)
    }

    @Test
    fun whenBlogVariantAndBlogNotificationCanShowButClearDataAndSearchPromptCannotThenBlogScheduled() = runBlocking<Unit> {
        setBlogVariant()
        whenever(blogNotification.canShow()).thenReturn(true)
        whenever(clearNotification.canShow()).thenReturn(false)
        whenever(searchPromptNotification.canShow()).thenReturn(false)

        testee.scheduleNextNotification()

        assertUnusedAppNotificationScheduled(BlogNotificationWorker::class.jvmName)
        assertNoContinuousAppNotificationScheduled()
    }

    @Test
    fun whenBlogVariantAndBlogNotificationCannotShowButSearchPromptAndClearNotificationCanThenBothAreScheduled() = runBlocking<Unit> {
        setBlogVariant()
        whenever(blogNotification.canShow()).thenReturn(false)
        whenever(clearNotification.canShow()).thenReturn(true)
        whenever(searchPromptNotification.canShow()).thenReturn(true)

        testee.scheduleNextNotification()

        assertUnusedAppNotificationScheduled(ClearDataNotificationWorker::class.jvmName)
        assertContinuousAppUseNotificationScheduled(SearchPromptNotificationWorker::class.jvmName)
    }

    @Test
    fun whenBlogVariantAndBlogNotificationAndClearNotificationCannotShowButSearchPromptCanThenNotificationScheduled() = runBlocking<Unit> {
        setBlogVariant()
        whenever(blogNotification.canShow()).thenReturn(false)
        whenever(clearNotification.canShow()).thenReturn(false)
        whenever(searchPromptNotification.canShow()).thenReturn(true)
        testee.scheduleNextNotification()

        assertContinuousAppUseNotificationScheduled(SearchPromptNotificationWorker::class.jvmName)
        assertNoUnusedAppNotificationScheduled()
    }

    @Test
    fun whenBlogVariantAndNoNotificationCanShowThenNoNotificationScheduled() = runBlocking<Unit> {
        setBlogVariant()
        whenever(blogNotification.canShow()).thenReturn(false)
        whenever(clearNotification.canShow()).thenReturn(false)
        whenever(searchPromptNotification.canShow()).thenReturn(false)
        testee.scheduleNextNotification()

        assertNoNotificationScheduled()
    }

    @Test
    fun whenAppFeatureVariantAndAppFeatureNotificationClearDataAndSearchPromptCanShowThenBothAreScheduled() = runBlocking<Unit> {
        setAppFeatureVariant()
        whenever(appFeatureNotification.canShow()).thenReturn(true)
        whenever(clearNotification.canShow()).thenReturn(true)
        whenever(searchPromptNotification.canShow()).thenReturn(true)

        testee.scheduleNextNotification()

        assertUnusedAppNotificationScheduled(AppFeatureNotificationWorker::class.jvmName)
        assertContinuousAppUseNotificationScheduled(SearchPromptNotificationWorker::class.jvmName)
    }

    @Test
    fun whenAppFeatureVariantAndAppFeatureNotificationClearDataCanShowButSearchPromptCannotThenAppFeatureNotificationScheduled() = runBlocking<Unit> {
        setAppFeatureVariant()
        whenever(appFeatureNotification.canShow()).thenReturn(true)
        whenever(clearNotification.canShow()).thenReturn(true)
        whenever(searchPromptNotification.canShow()).thenReturn(false)

        testee.scheduleNextNotification()

        assertUnusedAppNotificationScheduled(AppFeatureNotificationWorker::class.jvmName)
        assertNoContinuousAppNotificationScheduled()
    }

    @Test
    fun whenAppFeatureVariantAndAppFeatureNotificationAndSearchPromptCanShowButClearDataCannotShowThenBothAreScheduled() = runBlocking<Unit> {
        setAppFeatureVariant()
        whenever(appFeatureNotification.canShow()).thenReturn(true)
        whenever(clearNotification.canShow()).thenReturn(false)
        whenever(searchPromptNotification.canShow()).thenReturn(true)

        testee.scheduleNextNotification()

        assertUnusedAppNotificationScheduled(AppFeatureNotificationWorker::class.jvmName)
        assertContinuousAppUseNotificationScheduled(SearchPromptNotificationWorker::class.jvmName)
    }

    @Test
    fun whenAppFeatureVariantAndAppFeatureNotificationCanShowButClearDataAndSearchPromptCannotThenAppFeatureScheduled() = runBlocking<Unit> {
        setAppFeatureVariant()
        whenever(appFeatureNotification.canShow()).thenReturn(true)
        whenever(clearNotification.canShow()).thenReturn(false)
        whenever(searchPromptNotification.canShow()).thenReturn(false)

        testee.scheduleNextNotification()

        assertUnusedAppNotificationScheduled(AppFeatureNotificationWorker::class.jvmName)
        assertNoContinuousAppNotificationScheduled()
    }

    @Test
    fun whenAppFeatureVariantAndAppFeatureNotificationCannotShowButSearchPromptAndClearNotificationCanThenBothAreScheduled() = runBlocking<Unit> {
        setAppFeatureVariant()
        whenever(appFeatureNotification.canShow()).thenReturn(false)
        whenever(clearNotification.canShow()).thenReturn(true)
        whenever(searchPromptNotification.canShow()).thenReturn(true)

        testee.scheduleNextNotification()

        assertUnusedAppNotificationScheduled(ClearDataNotificationWorker::class.jvmName)
        assertContinuousAppUseNotificationScheduled(SearchPromptNotificationWorker::class.jvmName)
    }

    @Test
    fun whenAppFeatureVariantAndAppNotificationAndClearNotificationCannotShowButSearchPromptCanThenNotificationScheduled() = runBlocking<Unit> {
        setAppFeatureVariant()
        whenever(appFeatureNotification.canShow()).thenReturn(false)
        whenever(clearNotification.canShow()).thenReturn(false)
        whenever(searchPromptNotification.canShow()).thenReturn(true)
        testee.scheduleNextNotification()

        assertContinuousAppUseNotificationScheduled(SearchPromptNotificationWorker::class.jvmName)
        assertNoUnusedAppNotificationScheduled()
    }

    @Test
    fun whenAppFeatureVariantAndNoNotificationCanShowThenNoNotificationScheduled() = runBlocking<Unit> {
        setAppFeatureVariant()
        whenever(appFeatureNotification.canShow()).thenReturn(false)
        whenever(clearNotification.canShow()).thenReturn(false)
        whenever(searchPromptNotification.canShow()).thenReturn(false)
        testee.scheduleNextNotification()

        assertNoNotificationScheduled()
    }

    @Test
    fun whenControlVariantAndPrivacyNotificationClearDataAndSearchPromptCanShowThenBothAreScheduled() = runBlocking<Unit> {
        setNotificationControlVariant()
        whenever(privacyNotification.canShow()).thenReturn(true)
        whenever(clearNotification.canShow()).thenReturn(true)
        whenever(searchPromptNotification.canShow()).thenReturn(true)

        testee.scheduleNextNotification()

        assertUnusedAppNotificationScheduled(PrivacyNotificationWorker::class.jvmName)
        assertContinuousAppUseNotificationScheduled(SearchPromptNotificationWorker::class.jvmName)
    }

    @Test
    fun whenControlVariantAndPrivacyNotificationClearDataCanShowButSearchPromptCannotThenPrivacyNotificationScheduled() = runBlocking<Unit> {
        setNotificationControlVariant()
        whenever(privacyNotification.canShow()).thenReturn(true)
        whenever(clearNotification.canShow()).thenReturn(true)
        whenever(searchPromptNotification.canShow()).thenReturn(false)

        testee.scheduleNextNotification()

        assertUnusedAppNotificationScheduled(PrivacyNotificationWorker::class.jvmName)
        assertNoContinuousAppNotificationScheduled()
    }

    @Test
    fun whenControlVariantAndPrivacyNotificationAndSearchPromptCanShowButClearDataCannotShowThenBothAreScheduled() = runBlocking<Unit> {
        setNotificationControlVariant()
        whenever(privacyNotification.canShow()).thenReturn(true)
        whenever(clearNotification.canShow()).thenReturn(false)
        whenever(searchPromptNotification.canShow()).thenReturn(true)

        testee.scheduleNextNotification()

        assertUnusedAppNotificationScheduled(PrivacyNotificationWorker::class.jvmName)
        assertContinuousAppUseNotificationScheduled(SearchPromptNotificationWorker::class.jvmName)
    }

    @Test
    fun whenControlVariantAndPrivacyNotificationCanShowButClearDataAndSearchPromptCannotThenPrivacyNotificationScheduled() = runBlocking<Unit> {
        setNotificationControlVariant()
        whenever(privacyNotification.canShow()).thenReturn(true)
        whenever(clearNotification.canShow()).thenReturn(false)
        whenever(searchPromptNotification.canShow()).thenReturn(false)

        testee.scheduleNextNotification()

        assertUnusedAppNotificationScheduled(PrivacyNotificationWorker::class.jvmName)
        assertNoContinuousAppNotificationScheduled()
    }

    @Test
    fun whenControlVariantAndPrivacyNotificationCannotShowButSearchPromptAndClearNotificationCanThenBothAreScheduled() = runBlocking<Unit> {
        setNotificationControlVariant()
        whenever(privacyNotification.canShow()).thenReturn(false)
        whenever(clearNotification.canShow()).thenReturn(true)
        whenever(searchPromptNotification.canShow()).thenReturn(true)

        testee.scheduleNextNotification()

        assertUnusedAppNotificationScheduled(ClearDataNotificationWorker::class.jvmName)
        assertContinuousAppUseNotificationScheduled(SearchPromptNotificationWorker::class.jvmName)
    }

    @Test
    fun whenControlVariantAndPrivacyNotificationAndClearNotificationCannotShowButSearchPromptCanThenNotificationScheduled() = runBlocking<Unit> {
        setNotificationControlVariant()
        whenever(privacyNotification.canShow()).thenReturn(false)
        whenever(clearNotification.canShow()).thenReturn(false)
        whenever(searchPromptNotification.canShow()).thenReturn(true)
        testee.scheduleNextNotification()

        assertContinuousAppUseNotificationScheduled(SearchPromptNotificationWorker::class.jvmName)
        assertNoUnusedAppNotificationScheduled()
    }

    @Test
    fun whenControlVariantAndNoNotificationCanShowThenNoNotificationScheduled() = runBlocking<Unit> {
        setNotificationControlVariant()
        whenever(privacyNotification.canShow()).thenReturn(false)
        whenever(clearNotification.canShow()).thenReturn(false)
        whenever(searchPromptNotification.canShow()).thenReturn(false)
        testee.scheduleNextNotification()

        assertNoNotificationScheduled()
    }

    @Test
    fun whenNullVariantAndAllNotificationsCanShowThenSearchPromptNotificationScheduled() = runBlocking<Unit> {
        setNotificationNullVariant()
        whenever(privacyNotification.canShow()).thenReturn(true)
        whenever(articleNotification.canShow()).thenReturn(true)
        whenever(blogNotification.canShow()).thenReturn(true)
        whenever(appFeatureNotification.canShow()).thenReturn(true)
        whenever(clearNotification.canShow()).thenReturn(true)
        whenever(searchPromptNotification.canShow()).thenReturn(true)
        testee.scheduleNextNotification()

        assertNoUnusedAppNotificationScheduled()
    }

    @Test
    fun whenNullVariantAndOnlySearchPromptCannotShowThenNoNotificationsScheduled() = runBlocking<Unit> {
        setNotificationNullVariant()
        whenever(privacyNotification.canShow()).thenReturn(true)
        whenever(articleNotification.canShow()).thenReturn(true)
        whenever(blogNotification.canShow()).thenReturn(true)
        whenever(appFeatureNotification.canShow()).thenReturn(true)
        whenever(clearNotification.canShow()).thenReturn(true)
        whenever(searchPromptNotification.canShow()).thenReturn(false)
        testee.scheduleNextNotification()

        assertNoNotificationScheduled()
    }

    @Test
    fun whenNullVariantAndNoNotificationCanShowThenNoNotificationScheduled() = runBlocking<Unit> {
        setNotificationNullVariant()
        whenever(privacyNotification.canShow()).thenReturn(false)
        whenever(articleNotification.canShow()).thenReturn(false)
        whenever(blogNotification.canShow()).thenReturn(false)
        whenever(appFeatureNotification.canShow()).thenReturn(false)
        whenever(clearNotification.canShow()).thenReturn(false)
        whenever(searchPromptNotification.canShow()).thenReturn(false)
        testee.scheduleNextNotification()

        assertNoNotificationScheduled()
    }

    private fun setArticleVariant() {
        whenever(variantManager.getVariant()).thenReturn(
            Variant("test", features = listOf(DripNotification, Day1ArticleNotification, Day3ClearDataNotification), filterBy = { true })
        )
    }

    private fun setBlogVariant() {
        whenever(variantManager.getVariant()).thenReturn(
            Variant("test", features = listOf(DripNotification, Day1BlogNotification, Day3ClearDataNotification), filterBy = { true })
        )
    }

    private fun setAppFeatureVariant() {
        whenever(variantManager.getVariant()).thenReturn(
            Variant("test", features = listOf(DripNotification, Day1AppFeatureNotification, Day3ClearDataNotification), filterBy = { true })
        )
    }

    private fun setNotificationControlVariant() {
        whenever(variantManager.getVariant()).thenReturn(
            Variant("test", features = listOf(DripNotification, Day1PrivacyNotification, Day3ClearDataNotification), filterBy = { true })
        )
    }

    private fun setNotificationNullVariant() {
        whenever(variantManager.getVariant()).thenReturn(
            Variant("test", features = listOf(DripNotification), filterBy = { true })
        )
    }

    private fun assertUnusedAppNotificationScheduled(workerName: String) {
        assertTrue(getScheduledWorkers(NotificationScheduler.UNUSED_APP_WORK_REQUEST_TAG).any { it.tags.contains(workerName) })
    }

    private fun assertNoUnusedAppNotificationScheduled() {
        assertTrue(getScheduledWorkers(NotificationScheduler.UNUSED_APP_WORK_REQUEST_TAG).isEmpty())
    }

    private fun getScheduledWorkers(tag: String): List<WorkInfo> {
        return workManager
            .getWorkInfosByTag(tag)
            .get()
            .filter { it.state == WorkInfo.State.ENQUEUED }
    }
}
