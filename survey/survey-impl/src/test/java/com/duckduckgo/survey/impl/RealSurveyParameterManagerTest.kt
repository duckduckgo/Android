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
    fun whenRequestedParamsContainsUnsupportedParamThenBuildSurveyUrlStrictReturnsNull() = runTest {
        val result = surveyParameterManager.buildSurveyUrlStrict("http://example.com", listOf("atb", "unsupported"))

        assertNull(result)
    }

    @Test
    fun whenRequestedParamsContainsUnsupportedParamThenBuildSurveyUrlReturnsUrlWithEmptyValueForUnsupportedParam() = runTest {
        val result = surveyParameterManager.buildSurveyUrl("http://example.com", listOf("atb", "unsupported"))

        assertEquals("http://example.com?atb=test_atb&unsupported=", result)
    }

    @Test
    fun whenRequestedParamsAreAllSupportedThenBuildSurveyUrlReturnsResolvedUrl() = runTest {
        val result = surveyParameterManager.buildSurveyUrl("http://example.com", listOf("atb", "da"))

        assertEquals("http://example.com?atb=test_atb&da=test_da", result)
    }
}
