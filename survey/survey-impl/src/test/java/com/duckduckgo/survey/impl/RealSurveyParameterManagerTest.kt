package com.duckduckgo.survey.impl

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.common.utils.plugins.PluginPoint
import com.duckduckgo.survey.api.SurveyParameterPlugin
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RealSurveyParameterManagerTest {
    private lateinit var surveyParameterManager: RealSurveyParameterManager

    @Before
    fun setUp() {
        surveyParameterManager = RealSurveyParameterManager(
            surveyParameterPluginPoint = object : PluginPoint<SurveyParameterPlugin> {
                override fun getPlugins(): Collection<SurveyParameterPlugin> {
                    return listOf(
                        object : SurveyParameterPlugin {
                            override fun matches(paramKey: String): Boolean = paramKey == "atb"

                            override suspend fun evaluate(paramKey: String): String = "test_atb"
                        },
                        object : SurveyParameterPlugin {
                            override fun matches(paramKey: String): Boolean = paramKey == "av"
                            override suspend fun evaluate(paramKey: String): String = "test_av"
                        },
                        object : SurveyParameterPlugin {
                            override fun matches(paramKey: String): Boolean = paramKey == "da"

                            override suspend fun evaluate(paramKey: String): String = "test_da"
                        },
                    )
                }
            },
        )
    }

    @Test
    fun whenRequestedParamsAreAllSupportedThenBuildSurveyUrlStrictReturnsResolvedUrl() = runTest {
        val result = surveyParameterManager.buildSurveyUrlStrict("http://example.com", listOf("atb", "da"))

        assertNotNull(result)
        result?.let {
            assertEquals("http://example.com?atb=test_atb&da=test_da", result)
        }
    }

    @Test
    fun whenSomeRequestedParamsAreNotSupportedThenBuildSurveyUrlStrictReturnsNull() = runTest {
        val result = surveyParameterManager.buildSurveyUrlStrict("http://example.com", listOf("atb", "ppro"))

        assertNull(result)
    }

    @Test
    fun whenAllRequestedParamsAreNotSupportedThenBuildSurveyUrlStrictReturnsNull() = runTest {
        val result = surveyParameterManager.buildSurveyUrlStrict("http://example.com", listOf("hello", "ppro"))

        assertNull(result)
    }

    @Test
    fun whenNoRequestedParamsThenBuildSurveyUrlStrictReturnsResolvedUrl() = runTest {
        val result = surveyParameterManager.buildSurveyUrlStrict("http://example.com", emptyList())

        assertEquals("http://example.com", result)
    }

    @Test
    fun whenRequestedParamsAreAllSupportedThenBuildSurveyUrlReturnsResolvedUrl() = runTest {
        val result = surveyParameterManager.buildSurveyUrl("http://example.com", listOf("atb", "da"))

        assertEquals("http://example.com?atb=test_atb&da=test_da", result)
    }

    @Test
    fun whenSomeRequestedParamsAreNotSupportedThenBuildSurveyUrlReturnsUrl() = runTest {
        val result = surveyParameterManager.buildSurveyUrl("http://example.com", listOf("atb", "ppro"))

        assertEquals("http://example.com?atb=test_atb&ppro=", result)
    }

    @Test
    fun whenAllRequestedParamsAreNotSupportedThenBuildSurveyUrlReturnsUrl() = runTest {
        val result = surveyParameterManager.buildSurveyUrl("http://example.com", listOf("hello", "ppro"))

        assertEquals("http://example.com?hello=&ppro=", result)
    }

    @Test
    fun whenNoRequestedParamsThenBuildSurveyUrlReturnsUrl() = runTest {
        val result = surveyParameterManager.buildSurveyUrl("http://example.com", emptyList())

        assertEquals("http://example.com", result)
    }
}
