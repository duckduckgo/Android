package com.duckduckgo.app.anr.internal.feature

import com.duckduckgo.app.anr.internal.setting.CrashANRsRepository
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.test.api.FakeChain
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock

class CrashANRsInterceptorTest {
    @get:Rule
    var coroutineRule = CoroutineTestRule()

    private val repository: CrashANRsRepository = mock()

    @Test
    fun interceptEndpointWhenEnabled() {
        val chain = FakeChain(ANR_PIXEL)
        val interceptor = CrashANRsInterceptor(coroutineRule.testScope, coroutineRule.testDispatcherProvider, repository)
        val response = interceptor.intercept(chain)
    }

    companion object {
        private const val ANR_PIXEL =
            "https://improving.duckduckgo.com/t/m_anr_exception_android_phone?atb=v433-2&appVersion=5.203.0&stackTrace=amF2YS5sYW5nLlRocmVhZC5zbGVlcChOYXRpdmUgTWV0aG9kKQpqYXZhLmxhbmcuVGhyZWFkLnNsZWVwKFRocmVhZC5qYXZhOjQ0MCkKamF2YS5sYW5nLlRocmVhZC5zbGVlcChUaHJlYWQuamF2YTozNTYpCmNvbS5kdWNrZHVja2dvLmFwcC5icm93c2VyLkJyb3dzZXJUYWJGcmFnbWVudCRjb25maWd1cmVXZWJWaWV3JDEuaW52b2tlU3VzcGVuZChCcm93c2VyVGFiRnJhZ21lbnQua3Q6MjI2MykKa290bGluLmNvcm91dGluZXMuanZtLmludGVybmFsLkJhc2VDb250aW51YXRpb25JbXBsLnJlc3VtZVdpdGgoQ29udGludWF0aW9uSW1wbC5rdDozMykKa290bGlueC5jb3JvdXRpbmVzLkRpc3BhdGNoZWRUYXNrLnJ1bihEaXNwYXRjaGVkVGFzay5rdDoxMDgpCmFuZHJvaWQub3MuSGFuZGxlci5oYW5kbGVDYWxsYmFjayhIYW5kbGVyLmphdmE6ODgzKQphbmRyb2lkLm9zLkhhbmRsZXIuZGlzcGF0Y2hNZXNzYWdlKEhhbmRsZXIuamF2YToxMDApCmFuZHJvaWQub3MuTG9vcGVyLmxvb3AoTG9vcGVyLmphdmE6MjE0KQphbmRyb2lkLmFwcC5BY3Rpdml0eVRocmVhZC5tYWluKEFjdGl2aXR5VGhyZWFkLmphdmE6NzM1NikKamF2YS5sYW5nLnJlZmxlY3QuTWV0aG9kLmludm9rZShOYXRpdmUgTWV0aG9kKQpjb20uYW5kcm9pZC5pbnRlcm5hbC5vcy5SdW50aW1lSW5pdCRNZXRob2RBbmRBcmdzQ2FsbGVyLnJ1bihSdW50aW1lSW5pdC5qYXZhOjQ5MikKY29tLmFuZHJvaWQuaW50ZXJuYWwub3MuWnlnb3RlSW5pdC5tYWluKFp5Z290ZUluaXQuamF2YTo5MzAp&webView=124.0.6367.179&customTab=false&test=1"
    }
}
