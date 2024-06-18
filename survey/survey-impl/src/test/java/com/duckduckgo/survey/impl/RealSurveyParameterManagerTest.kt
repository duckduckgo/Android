package com.duckduckgo.survey.impl

import com.duckduckgo.common.utils.plugins.PluginPoint
import com.duckduckgo.survey.api.SurveyParameterPlugin
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class RealSurveyParameterManagerTest {
    private lateinit var surveyParameterManager: RealSurveyParameterManager

    @Before
    fun setUp() {
        surveyParameterManager = RealSurveyParameterManager(
            surveyParameterPluginPoint = object : PluginPoint<SurveyParameterPlugin> {
                override fun getPlugins(): Collection<SurveyParameterPlugin> {
                    return listOf(
                        object : SurveyParameterPlugin {
                            override val surveyParamKey: String = "atb"

                            override suspend fun evaluate(): String = "test_atb"
                        },
                        object : SurveyParameterPlugin {
                            override val surveyParamKey: String = "av"
                            override suspend fun evaluate(): String = "test_av"
                        },
                        object : SurveyParameterPlugin {
                            override val surveyParamKey: String = "da"

                            override suspend fun evaluate(): String = "test_da"
                        },
                    )
                }
            },
        )
    }

    @Test
    fun whenRequestedParamsAreAllSupportedThenCanProvideAllParametersReturnsTrue() = runTest {
        val result = surveyParameterManager.canProvideAllParameters(listOf("atb", "da"))

        assertTrue(result)
    }

    @Test
    fun whenRequestedParamsIsEmptyThenCanProvideAllParametersReturnsTrue() = runTest {
        val result = surveyParameterManager.canProvideAllParameters(emptyList())

        assertTrue(result)
    }

    @Test
    fun whenRequestedParamsHasInvalidThenCanProvideAllParametersReturnsFalse() = runTest {
        val result = surveyParameterManager.canProvideAllParameters(listOf("atb", "scr", "da"))

        assertFalse(result)
    }

    @Test
    fun whenRequestedParamsAreAllInvalidThenCanProvideAllParametersReturnsFalse() = runTest {
        val result = surveyParameterManager.canProvideAllParameters(listOf("scr"))

        assertFalse(result)
    }
}
