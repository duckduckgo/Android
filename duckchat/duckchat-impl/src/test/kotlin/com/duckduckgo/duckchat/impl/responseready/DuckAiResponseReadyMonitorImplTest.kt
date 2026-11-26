/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.duckchat.impl.responseready

import android.database.sqlite.SQLiteException
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.app.tabs.model.TabEntity
import com.duckduckgo.app.tabs.model.TabRepository
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.duckchat.api.DuckChat
import com.duckduckgo.duckchat.impl.pendingresponse.PendingResponseStatus
import com.duckduckgo.duckchat.store.impl.DuckAiChatStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doSuspendableAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.annotation.Config
import retrofit2.Response
import java.io.IOException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class DuckAiResponseReadyMonitorImplTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule(StandardTestDispatcher())

    private val chatStore: DuckAiChatStore = mock()
    private val tabRepository: TabRepository = mock()
    private val storedResponseClient: DuckAiStoredResponseClient = mock()
    private val duckChat: DuckChat = mock()

    private val tabs = MutableStateFlow(listOf(TabEntity(tabId = "t1", url = chatUrl("A"))))
    private val chatContent = mutableMapOf<String, String>()
    private val syncCodes = ArrayDeque(listOf(404))
    private val foundChatIds = mutableListOf<String>()
    private val requestTimes = mutableListOf<Long>()
    private var settings = ResponseReadyNotificationsSettings(pollInterval = 4.seconds, timeout = 180.seconds, errorSettle = 5.seconds)

    private lateinit var testee: DuckAiResponseReadyMonitorImpl

    @Before
    fun setup() = runTest {
        whenever(tabRepository.flowTabs).thenReturn(tabs)
        whenever(chatStore.getChatContent(any())).thenAnswer { chatContent[it.getArgument(0)] }
        whenever(duckChat.isDuckChatUrl(any())).thenAnswer { (it.arguments[0] as Uri).toString().startsWith(DUCK_AI_URL) }
        whenever(storedResponseClient.getStoredResponse(any())) doSuspendableAnswer {
            requestTimes += coroutineRule.testScope.testScheduler.currentTime
            val code = if (syncCodes.size > 1) syncCodes.removeFirst() else syncCodes.first()
            if (code == NETWORK_ERROR) throw IOException("offline")
            syncResponse(code)
        }
        testee = DuckAiResponseReadyMonitorImpl(chatStore, tabRepository, storedResponseClient, duckChat)
    }

    @Test
    fun `when Sync returns 200 then Ready`() {
        saveChat("A", pendingUser("r1"))
        syncAnswers(200)

        val (_, result) = startAwait()

        assertEquals(WaitResult.Ready, result.getCompleted())
    }

    @Test
    fun `when Sync returns 410 then Stopped`() {
        assertStopped(410)
    }

    @Test
    fun `when tab never gets a chat id then TimedOut after 180 s`() {
        tabs.value = listOf(TabEntity(tabId = "t1", url = DUCK_AI_URL))

        val (_, result) = startAwait(chatId = null)
        advance(179.seconds)
        assertFalse(result.isCompleted)
        advance(1.seconds)

        assertEquals(WaitResult.TimedOut, result.getCompleted())
    }

    @Test
    fun `when reading the saved chat throws then Stopped`() = runTest {
        whenever(chatStore.getChatContent(any())).thenThrow(SQLiteException("disk I/O error"))

        val (_, result) = startAwait()

        assertEquals(WaitResult.Stopped, result.getCompleted())
    }

    @Test
    fun `when a page status arrives before the wait starts then it is applied once the wait runs`() {
        saveChat("A", pendingUser("r1"))
        syncAnswers(404)
        val wait = TestWait()
        wait.statuses.trySend(PendingResponseStatus.from("start_stream:auto_retry_last_prompt"))
        val result = launchAwait(wait)

        wait.statuses.trySend(PendingResponseStatus.from("ready"))
        advance(1.seconds)

        assertFalse(result.isCompleted)
    }

    @Test
    fun `when chat id known at start then id read from saved chat`() = runTest {
        saveChat("A", pendingUser("r1"))

        startAwait()

        verify(storedResponseClient).getStoredResponse("r1")
    }

    @Test
    fun `when chat id missing then it is taken from the tab url once it appears`() = runTest {
        tabs.value = listOf(TabEntity(tabId = "t1", url = DUCK_AI_URL))
        saveChat("B", pendingUser("r1"))
        startAwait(chatId = null)
        verify(storedResponseClient, never()).getStoredResponse(any())

        tabs.value = listOf(TabEntity(tabId = "t1", url = chatUrl("B")))
        runCurrent()

        verify(storedResponseClient).getStoredResponse("r1")
        assertEquals(listOf("B"), foundChatIds)
    }

    @Test
    fun `when save lands after start then polling starts with the new id`() = runTest {
        saveChat("A", pendingUser("r0"), ASSISTANT)
        startAwait()
        verify(storedResponseClient, never()).getStoredResponse(any())

        saveChat("A", pendingUser("r0"), ASSISTANT, pendingUser("r1"))
        advance(4.seconds)

        verify(storedResponseClient).getStoredResponse("r1")
        verify(storedResponseClient, never()).getStoredResponse("r0")
    }

    @Test
    fun `when chat switches format between reads then id still found`() = runTest {
        saveChat("A", """{"role":"user","content":"hi","meta":{"recovery":{}}}""")
        startAwait()

        saveChat("A", pendingUser("r1"))
        advance(4.seconds)

        verify(storedResponseClient).getStoredResponse("r1")
    }

    @Test
    fun `when retry rewrites the id after it was found then the first id is kept`() = runTest {
        saveChat("A", pendingUser("r1"))
        startAwait()

        saveChat("A", """{"role":"user","content":"hi","meta":{"recovery":{"messageId":"r2"}}}""")

        verify(storedResponseClient).getStoredResponse("r1")
        verify(storedResponseClient, never()).getStoredResponse("r2")
    }

    @Test
    fun `when saved chat never has a pending id then TimedOut`() {
        saveChat("A", pendingUser("r0"), ASSISTANT)
        val (_, result) = startAwait()

        advance(180.seconds)

        assertEquals(WaitResult.TimedOut, result.getCompleted())
    }

    @Test
    fun `when remote timeout is 60 s then TimedOut after 60 s`() {
        settings = settings.copy(timeout = 60.seconds)
        val (_, result) = startAwait()

        advance(59.seconds)
        assertFalse(result.isCompleted)
        advance(1.seconds)

        assertEquals(WaitResult.TimedOut, result.getCompleted())
    }

    @Test
    fun `when Sync returns 404 then it polls again after 4 s`() {
        assertPollsAgainAfterInterval(404)
    }

    @Test
    fun `when Sync returns 418 then next request waits between 30 and 60 s`() {
        assertRateLimitWait(418)
    }

    @Test
    fun `when Sync returns 429 then next request waits between 30 and 60 s`() {
        assertRateLimitWait(429)
    }

    @Test
    fun `when rate limited twice then the second wait is between 60 and 120 s`() {
        settings = settings.copy(timeout = 600.seconds)
        syncAnswers(429, 418, 404)
        saveChat("A", pendingUser("r1"))

        startAwait()
        advance(200.seconds)

        val secondWait = requestTimes[2] - requestTimes[1]
        assertTrue("second wait $secondWait ms", secondWait in 60_000 until 120_000)
    }

    @Test
    fun `when Sync returns 500 then it polls again after 4 s`() {
        assertPollsAgainAfterInterval(500)
    }

    @Test
    fun `when Sync throws IOException then it polls again after 4 s`() {
        assertPollsAgainAfterInterval(NETWORK_ERROR)
    }

    @Test
    fun `when Sync returns 408 then it polls again after 4 s`() {
        assertPollsAgainAfterInterval(408)
    }

    @Test
    fun `when Sync returns 400 then Stopped`() {
        assertStopped(400)
    }

    @Test
    fun `when Sync returns 401 then Stopped`() {
        assertStopped(401)
    }

    @Test
    fun `when Sync returns 403 then Stopped`() {
        assertStopped(403)
    }

    @Test
    fun `when rate limited then another answer resets the backoff`() {
        settings = settings.copy(timeout = 600.seconds)
        syncAnswers(429, 404, 429, 404)
        saveChat("A", pendingUser("r1"))

        startAwait()
        advance(200.seconds)

        val waitAfterSecondRateLimit = requestTimes[3] - requestTimes[2]
        assertTrue("wait $waitAfterSecondRateLimit ms", waitAfterSecondRateLimit in 30_000 until 60_000)
    }

    @Test
    fun `when page ready while away and tab open then Ready without Sync 200`() {
        saveChat("A", pendingUser("r1"))
        val (wait, result) = startAwait()

        pageStatus(wait, "ready")

        assertEquals(WaitResult.Ready, result.getCompleted())
        advance(60.seconds)
        assertEquals(1, requestTimes.size)
    }

    @Test
    fun `when tab closed then page ready is ignored and Sync 200 gives Ready`() {
        saveChat("A", pendingUser("r1"))
        val (wait, result) = startAwait()
        tabs.value = emptyList()
        runCurrent()

        pageStatus(wait, "ready")
        assertFalse(result.isCompleted)

        syncAnswers(200)
        advance(4.seconds)
        assertEquals(WaitResult.Ready, result.getCompleted())
    }

    @Test
    fun `when tab marked deletable then page ready is ignored`() {
        saveChat("A", pendingUser("r1"))
        val (wait, result) = startAwait()
        tabs.value = listOf(TabEntity(tabId = "t1", url = chatUrl("A"), deletable = true))
        runCurrent()

        pageStatus(wait, "ready")

        assertFalse(result.isCompleted)
    }

    @Test
    fun `when tab state not known yet then page ready is ignored`() {
        whenever(tabRepository.flowTabs).thenReturn(MutableSharedFlow())
        saveChat("A", pendingUser("r1"))
        val (wait, result) = startAwait()

        pageStatus(wait, "ready")

        assertFalse(result.isCompleted)
    }

    @Test
    fun `when page restarts then ready without streaming is ignored`() {
        saveChat("A", pendingUser("r1"))
        val (wait, result) = startAwait()

        pageStatus(wait, "start_stream:restart_stream")
        pageStatus(wait, "ready")

        assertFalse(result.isCompleted)
    }

    @Test
    fun `when page restarts then streams then ready then Ready`() {
        saveChat("A", pendingUser("r1"))
        val (wait, result) = startAwait()

        pageStatus(wait, "start_stream:new_prompt")
        pageStatus(wait, "streaming")
        pageStatus(wait, "ready")

        assertEquals(WaitResult.Ready, result.getCompleted())
    }

    @Test
    fun `when page errors and no restart within 5 s then Ready`() {
        val (wait, result) = startAwait()

        pageStatus(wait, "error")
        advance(4.seconds)
        assertFalse(result.isCompleted)
        advance(1.seconds)

        assertEquals(WaitResult.Ready, result.getCompleted())
    }

    @Test
    fun `when page is blocked and no restart within 5 s then Ready`() {
        val (wait, result) = startAwait()

        pageStatus(wait, "blocked")
        advance(5.seconds)

        assertEquals(WaitResult.Ready, result.getCompleted())
    }

    @Test
    fun `when page errors and restarts within 5 s then not Ready`() {
        val (wait, result) = startAwait()

        pageStatus(wait, "error")
        advance(2.seconds)
        pageStatus(wait, "start_stream:new_prompt")
        advance(10.seconds)

        assertFalse(result.isCompleted)
    }

    @Test
    fun `when page errors and tab closes before the settle time then not Ready`() {
        val (wait, result) = startAwait()

        pageStatus(wait, "error")
        advance(2.seconds)
        tabs.value = emptyList()
        advance(10.seconds)

        assertFalse(result.isCompleted)
    }

    @Test
    fun `when page errors while the tab is closed then not Ready`() {
        val (wait, result) = startAwait()
        tabs.value = emptyList()
        runCurrent()

        pageStatus(wait, "error")
        tabs.value = listOf(TabEntity(tabId = "t1", url = chatUrl("A")))
        advance(10.seconds)

        assertFalse(result.isCompleted)
    }

    @Test
    fun `when error settle is 8 s then Ready at 8 s`() {
        settings = settings.copy(errorSettle = 8.seconds)
        val (wait, result) = startAwait()

        pageStatus(wait, "error")
        advance(7.seconds)
        assertFalse(result.isCompleted)
        advance(1.seconds)

        assertEquals(WaitResult.Ready, result.getCompleted())
    }

    @Test
    fun `when page ready arrives before the response id is found then Ready`() = runTest {
        val (wait, result) = startAwait()

        pageStatus(wait, "ready")

        assertEquals(WaitResult.Ready, result.getCompleted())
        verify(storedResponseClient, never()).getStoredResponse(any())
    }

    @Test
    fun `when page ready and Sync 200 arrive together then Ready and no further request`() = runTest {
        val inFlight = CompletableDeferred<Response<Unit>>()
        whenever(storedResponseClient.getStoredResponse(any())) doSuspendableAnswer { inFlight.await() }
        saveChat("A", pendingUser("r1"))
        val (wait, result) = startAwait()

        inFlight.complete(syncResponse(200))
        pageStatus(wait, "ready")
        advance(60.seconds)

        assertEquals(WaitResult.Ready, result.getCompleted())
        verify(storedResponseClient, times(1)).getStoredResponse(any())
    }

    @Test
    fun `when user returns after the page error then the settle timer does not complete the wait`() {
        val (wait, result) = startAwait()
        pageStatus(wait, "error")

        wait.job.cancel()
        advance(5.seconds)

        assertFalse(result.isCompleted)
        assertEquals(0, requestTimes.size)
    }

    private class TestWait {
        val statuses = Channel<PendingResponseStatus>(capacity = Channel.UNLIMITED)
        lateinit var job: Job
    }

    private fun startAwait(tabId: String = "t1", chatId: String? = "A"): Pair<TestWait, CompletableDeferred<WaitResult>> {
        val wait = TestWait()
        return wait to launchAwait(wait, tabId, chatId)
    }

    private fun launchAwait(wait: TestWait, tabId: String = "t1", chatId: String? = "A"): CompletableDeferred<WaitResult> {
        val result = CompletableDeferred<WaitResult>()
        wait.job = coroutineRule.testScope.launch {
            result.complete(testee.await(tabId, chatId, wait.statuses, settings) { foundChatIds += it })
        }
        runCurrent()
        return result
    }

    private fun assertPollsAgainAfterInterval(code: Int) {
        syncAnswers(code)
        saveChat("A", pendingUser("r1"))

        startAwait()
        assertEquals(1, requestTimes.size)
        advance(3.seconds)
        assertEquals(1, requestTimes.size)
        advance(1.seconds)
        assertEquals(2, requestTimes.size)
    }

    private fun assertStopped(code: Int) {
        syncAnswers(code)
        saveChat("A", pendingUser("r1"))

        val (_, result) = startAwait()
        advance(180.seconds)

        assertEquals(WaitResult.Stopped, result.getCompleted())
        assertEquals(1, requestTimes.size)
    }

    private fun assertRateLimitWait(code: Int) {
        syncAnswers(code, 404)
        saveChat("A", pendingUser("r1"))

        startAwait()
        advance(29.seconds)
        assertEquals(1, requestTimes.size)
        advance(31.seconds)

        val wait = requestTimes[1] - requestTimes[0]
        assertTrue("wait $wait ms", wait in 30_000 until 60_000)
    }

    private fun pageStatus(wait: TestWait, status: String) {
        wait.statuses.trySend(PendingResponseStatus.from(status))
        runCurrent()
    }

    private fun syncAnswers(vararg codes: Int) {
        syncCodes.clear()
        syncCodes.addAll(codes.toList())
    }

    private fun syncResponse(code: Int): Response<Unit> =
        if (code == 200) Response.success(Unit) else Response.error(code, "".toResponseBody())

    private fun saveChat(chatId: String, vararg messages: String) {
        chatContent[chatId] = """{"chatId":"$chatId","messages":[${messages.joinToString(",")}]}"""
        runCurrent()
    }

    private fun pendingUser(id: String) = """{"role":"user","content":"hi","messageId":"$id"}"""

    private fun runCurrent() = coroutineRule.testScope.testScheduler.runCurrent()

    private fun advance(duration: Duration) {
        coroutineRule.testScope.testScheduler.advanceTimeBy(duration)
        runCurrent()
    }

    private companion object {
        const val DUCK_AI_URL = "https://duck.ai/"
        const val NETWORK_ERROR = -1
        const val ASSISTANT = """{"role":"assistant","content":"yo","messageId":"x"}"""

        fun chatUrl(chatId: String) = "$DUCK_AI_URL?chatID=$chatId"
    }
}
