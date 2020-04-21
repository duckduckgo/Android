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

import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.app.notification.NotificationScheduler.*
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
import org.junit.After
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
    private var workManager = WorkManager.getInstance(context)
    private lateinit var testee: NotificationScheduler

    @Before
    fun before() {
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

    @After
    fun resetWorkers() {
        workManager.cancelAllWorkByTag(NotificationScheduler.CONTINUOUS_APP_USE_REQUEST_TAG)
    }

    @Test
    fun whenPrivacyNotificationClearDataAndSearchPromptCanShowThenBothAreScheduled() = runBlocking<Unit> {
        whenever(variantManager.getVariant(any())).thenReturn(DEFAULT_VARIANT)
        whenever(privacyNotification.canShow()).thenReturn(true)
        whenever(clearNotification.canShow()).thenReturn(true)
        whenever(searchPromptNotification.canShow()).thenReturn(true)
        testee.scheduleNextNotification()

        assertUnusedAppNotificationScheduled(PrivacyNotificationWorker::class.jvmName)
        assertContinuousAppUseNotificationScheduled(SearchPromptNotificationWorker::class.jvmName)
    }

    @Test
    fun whenPrivacyNotificationAndClearDataCanShowButSearchPromptCannotThenPrivacyNotificationScheduled() = runBlocking<Unit> {
        whenever(variantManager.getVariant(any())).thenReturn(DEFAULT_VARIANT)
        whenever(privacyNotification.canShow()).thenReturn(true)
        whenever(clearNotification.canShow()).thenReturn(true)
        whenever(searchPromptNotification.canShow()).thenReturn(false)
        testee.scheduleNextNotification()

        assertUnusedAppNotificationScheduled(PrivacyNotificationWorker::class.jvmName)
        assertNoContinuousAppNotificationScheduled()
    }

    @Test
    fun whenPrivacyNotificationAndSearchPromptCanShowButClearDataCannotThenBothAreScheduled() = runBlocking<Unit> {
        whenever(variantManager.getVariant(any())).thenReturn(DEFAULT_VARIANT)
        whenever(privacyNotification.canShow()).thenReturn(true)
        whenever(clearNotification.canShow()).thenReturn(false)
        whenever(searchPromptNotification.canShow()).thenReturn(true)
        testee.scheduleNextNotification()

        assertUnusedAppNotificationScheduled(PrivacyNotificationWorker::class.jvmName)
        assertContinuousAppUseNotificationScheduled(SearchPromptNotificationWorker::class.jvmName)
    }

    @Test
    fun whenPrivacyNotificationCanShowButClearDataAndSearchPromptCannotThenPrivacyNotificationScheduled() = runBlocking<Unit> {
        whenever(variantManager.getVariant(any())).thenReturn(DEFAULT_VARIANT)
        whenever(privacyNotification.canShow()).thenReturn(true)
        whenever(clearNotification.canShow()).thenReturn(false)
        whenever(searchPromptNotification.canShow()).thenReturn(false)
        testee.scheduleNextNotification()

        assertUnusedAppNotificationScheduled(PrivacyNotificationWorker::class.jvmName)
        assertNoContinuousAppNotificationScheduled()
    }

    @Test
    fun whenPrivacyNotificationCannotShowButSearchPromptAndClearNotificationCanShowThenBothAreScheduled() = runBlocking<Unit> {
        whenever(variantManager.getVariant(any())).thenReturn(DEFAULT_VARIANT)
        whenever(privacyNotification.canShow()).thenReturn(false)
        whenever(clearNotification.canShow()).thenReturn(true)
        whenever(searchPromptNotification.canShow()).thenReturn(true)
        testee.scheduleNextNotification()

        assertUnusedAppNotificationScheduled(ClearDataNotificationWorker::class.jvmName)
        assertContinuousAppUseNotificationScheduled(SearchPromptNotificationWorker::class.jvmName)
    }

    @Test
    fun whenPrivacyNotificationAndClearNotificationCannotShowButSearchPromptCanShowThenNotificationScheduled() = runBlocking<Unit> {
        whenever(variantManager.getVariant(any())).thenReturn(DEFAULT_VARIANT)
        whenever(privacyNotification.canShow()).thenReturn(false)
        whenever(clearNotification.canShow()).thenReturn(false)
        whenever(searchPromptNotification.canShow()).thenReturn(true)
        testee.scheduleNextNotification()

        assertContinuousAppUseNotificationScheduled(SearchPromptNotificationWorker::class.jvmName)
        assertNoUnusedAppNotificationScheduled()
    }

    @Test
    fun whenNoNotificationCanShowThenNoNotificationScheduled() = runBlocking<Unit> {
        whenever(variantManager.getVariant(any())).thenReturn(DEFAULT_VARIANT)
        whenever(privacyNotification.canShow()).thenReturn(false)
        whenever(clearNotification.canShow()).thenReturn(false)
        whenever(searchPromptNotification.canShow()).thenReturn(false)
        testee.scheduleNextNotification()

        assertNoNotificationScheduled()
    }

    @Test
    fun whenArticleVariantAndArticleNotificationClearDataAndSearchPromptCanShowThenBothAreScheduled() = runBlocking<Unit> {
        setArticleVariant()
        whenever(articleNotification.canShow()).thenReturn(true)
        whenever(clearNotification.canShow()).thenReturn(true)
        whenever(searchPromptNotification.canShow()).thenReturn(true)

        testee.scheduleNextNotification()

        assertUnusedAppNotificationScheduled(ArticleNotificationWorker::class.jvmName)
        assertContinuousAppUseNotificationScheduled(SearchPromptNotificationWorker::class.jvmName)
    }

    @Test
    fun whenArticleVariantAndArticleNotificationClearDataCanShowButSearchPromptCannotThenArticleNotificationScheduled() = runBlocking<Unit> {
        setArticleVariant()
        whenever(articleNotification.canShow()).thenReturn(true)
        whenever(clearNotification.canShow()).thenReturn(true)
        whenever(searchPromptNotification.canShow()).thenReturn(false)

        testee.scheduleNextNotification()

        assertUnusedAppNotificationScheduled(ArticleNotificationWorker::class.jvmName)
        assertNoContinuousAppNotificationScheduled()
    }

    @Test
    fun whenArticleVariantAndArticleNotificationAndSearchPromptCanShowButClearDataCannotShowThenBothAreScheduled() = runBlocking<Unit> {
        setArticleVariant()
        whenever(articleNotification.canShow()).thenReturn(true)
        whenever(clearNotification.canShow()).thenReturn(false)
        whenever(searchPromptNotification.canShow()).thenReturn(true)

        testee.scheduleNextNotification()

        assertUnusedAppNotificationScheduled(ArticleNotificationWorker::class.jvmName)
        assertContinuousAppUseNotificationScheduled(SearchPromptNotificationWorker::class.jvmName)
    }

    @Test
    fun whenArticleVariantAndArticleNotificationCanShowButClearDataAndSearchPromptCannotThenArticleScheduled() = runBlocking<Unit> {
        setArticleVariant()
        whenever(articleNotification.canShow()).thenReturn(true)
        whenever(clearNotification.canShow()).thenReturn(false)
        whenever(searchPromptNotification.canShow()).thenReturn(false)

        testee.scheduleNextNotification()

        assertUnusedAppNotificationScheduled(ArticleNotificationWorker::class.jvmName)
        assertNoContinuousAppNotificationScheduled()
    }

    @Test
    fun whenArticleVariantAndArticleNotificationCannotShowButSearchPromptAndClearNotificationCanThenBothAreScheduled() = runBlocking<Unit> {
        setArticleVariant()
        whenever(articleNotification.canShow()).thenReturn(false)
        whenever(clearNotification.canShow()).thenReturn(true)
        whenever(searchPromptNotification.canShow()).thenReturn(true)

        testee.scheduleNextNotification()

        assertUnusedAppNotificationScheduled(ClearDataNotificationWorker::class.jvmName)
        assertContinuousAppUseNotificationScheduled(SearchPromptNotificationWorker::class.jvmName)
    }

    @Test
    fun whenArticleVariantAndArticleNotificationAndClearNotificationCannotShowButSearchPromptCanThenNotificationScheduled() = runBlocking<Unit> {
        setArticleVariant()
        whenever(articleNotification.canShow()).thenReturn(false)
        whenever(clearNotification.canShow()).thenReturn(false)
        whenever(searchPromptNotification.canShow()).thenReturn(true)
        testee.scheduleNextNotification()

        assertContinuousAppUseNotificationScheduled(SearchPromptNotificationWorker::class.jvmName)
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

        assertContinuousAppUseNotificationScheduled(SearchPromptNotificationWorker::class.jvmName)
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
        assertTrue(getUnusedAppScheduledWorkers().any { it.tags.contains(workerName) })
    }

    private fun assertContinuousAppUseNotificationScheduled(workerName: String) {
        assertTrue(getContinuousAppUseScheduledWorkers().any { it.tags.contains(workerName) })
    }

    private fun assertNoUnusedAppNotificationScheduled() {
        assertTrue(getUnusedAppScheduledWorkers().isEmpty())
    }

    private fun assertNoContinuousAppNotificationScheduled() {
        assertTrue(getContinuousAppUseScheduledWorkers().isEmpty())
    }

    private fun assertNoNotificationScheduled() {
        assertTrue(getUnusedAppScheduledWorkers().isEmpty())
        assertTrue(getContinuousAppUseScheduledWorkers().isEmpty())
    }

    private fun getUnusedAppScheduledWorkers(): List<WorkInfo> {
        return workManager
            .getWorkInfosByTag(NotificationScheduler.UNUSED_APP_WORK_REQUEST_TAG)
            .get()
            .filter { it.state == WorkInfo.State.ENQUEUED }
    }

    private fun getContinuousAppUseScheduledWorkers(): List<WorkInfo> {
        return workManager
            .getWorkInfosByTag(NotificationScheduler.CONTINUOUS_APP_USE_REQUEST_TAG)
            .get()
            .filter { it.state == WorkInfo.State.ENQUEUED }
    }
}