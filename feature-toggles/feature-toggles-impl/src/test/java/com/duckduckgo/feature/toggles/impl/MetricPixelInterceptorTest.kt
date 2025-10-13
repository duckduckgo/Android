package com.duckduckgo.feature.toggles.impl

import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.test.api.FakeChain
import com.duckduckgo.common.utils.plugins.PluginPoint
import com.duckduckgo.feature.toggles.api.ConversionWindow
import com.duckduckgo.feature.toggles.api.FakeToggleStore
import com.duckduckgo.feature.toggles.api.FeatureToggles
import com.duckduckgo.feature.toggles.api.MetricsPixel
import com.duckduckgo.feature.toggles.api.MetricsPixelPlugin
import com.duckduckgo.feature.toggles.api.PixelDefinition
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.feature.toggles.api.Toggle.State
import com.duckduckgo.feature.toggles.codegen.TestTriggerFeature
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

class MetricPixelInterceptorTest {
    @get:Rule
    @Suppress("unused")
    val coroutineRule = CoroutineTestRule()

    private val store = FakeStore()
    private lateinit var testFeature: TestTriggerFeature
    private lateinit var pluginPoint: FakePluginPoint
    private lateinit var pixelInterceptor: MetricPixelInterceptor

    @Before
    fun setup() {
        testFeature = FeatureToggles.Builder(
            FakeToggleStore(),
            featureName = "testFeature",
        ).build().create(TestTriggerFeature::class.java)
        pluginPoint = FakePluginPoint(testFeature)
        pixelInterceptor = MetricPixelInterceptor(pluginPoint, store)
    }

    @Test
    fun `test metric exist but is not in conversion window drops pixel`() {
        val enrollmentDateET = ZonedDateTime.now(ZoneId.of("America/New_York")).toString()
        val enrollmentDateParsedET: String = ZonedDateTime.parse(enrollmentDateET).format(DateTimeFormatter.ISO_LOCAL_DATE).toString()

        testFeature.experimentFooFeature().setRawStoredState(
            State(
                remoteEnableState = true,
                enable = true,
                assignedCohort = Toggle.State.Cohort(name = "control", weight = 1, enrollmentDateET = enrollmentDateET),
            ),
        )
        val pixelUrl = setupPixelUrl(
            experimentName = "experimentFooFeature",
            cohortName = "control",
            metric = "refreshClicked",
            value = "2",
            conversionWindowDays = "1-2",
            enrollmentDateET = enrollmentDateParsedET,
        )

        val result = pixelInterceptor.intercept(FakeChain(pixelUrl))
        assertEquals("Dropped experiment metrics pixel", result.message)
        assertNotEquals(null, result.body)
    }

    @Test
    fun `test metric exist and is in conversion window sends pixel`() {
        val enrollmentDateET = ZonedDateTime.now(ZoneId.of("America/New_York")).toString()
        val enrollmentDateParsedET: String = ZonedDateTime.parse(enrollmentDateET).format(DateTimeFormatter.ISO_LOCAL_DATE).toString()

        testFeature.experimentFooFeature().setRawStoredState(
            State(
                remoteEnableState = true,
                enable = true,
                assignedCohort = Toggle.State.Cohort(name = "control", weight = 1, enrollmentDateET = enrollmentDateET),
            ),
        )
        val pixelUrl = setupPixelUrl(
            experimentName = "experimentFooFeature",
            cohortName = "control",
            metric = "refreshClicked",
            value = "1",
            conversionWindowDays = "0-1",
            enrollmentDateET = enrollmentDateParsedET,
        )

        val result = pixelInterceptor.intercept(FakeChain(pixelUrl))
        assertEquals("", result.message)
        assertEquals(null, result.body)
    }

    @Test
    fun `test metric allows conversion window of one day`() {
        val enrollmentDateET = ZonedDateTime.now(ZoneId.of("America/New_York")).toString()
        val enrollmentDateParsedET: String = ZonedDateTime.parse(enrollmentDateET).format(DateTimeFormatter.ISO_LOCAL_DATE).toString()

        testFeature.experimentFooFeature().setRawStoredState(
            State(
                remoteEnableState = true,
                enable = true,
                assignedCohort = Toggle.State.Cohort(name = "control", weight = 1, enrollmentDateET = enrollmentDateET),
            ),
        )
        val pixelUrl = setupPixelUrl(
            experimentName = "experimentFooFeature",
            cohortName = "control",
            metric = "refreshClicked",
            value = "1",
            conversionWindowDays = "0",
            enrollmentDateET = enrollmentDateParsedET,
        )

        val result = pixelInterceptor.intercept(FakeChain(pixelUrl))
        assertEquals("", result.message)
        assertEquals(null, result.body)
    }

    @Test
    fun `test metric exist and is in conversion window from previous days sends pixel`() {
        val enrollmentDateET = ZonedDateTime.now(ZoneId.of("America/New_York")).minusDays(4).toString()
        val enrollmentDateParsedET: String = ZonedDateTime.parse(enrollmentDateET).format(DateTimeFormatter.ISO_LOCAL_DATE).toString()

        testFeature.experimentFooFeature().setRawStoredState(
            State(
                remoteEnableState = true,
                enable = true,
                assignedCohort = Toggle.State.Cohort(name = "control", weight = 1, enrollmentDateET = enrollmentDateET),
            ),
        )
        val pixelUrl = setupPixelUrl(
            experimentName = "experimentFooFeature",
            cohortName = "control",
            metric = "refreshClicked",
            value = "1",
            conversionWindowDays = "3-4",
            enrollmentDateET = enrollmentDateParsedET,
        )

        val result = pixelInterceptor.intercept(FakeChain(pixelUrl))
        assertEquals("", result.message)
        assertEquals(null, result.body)
    }

    @Test
    fun `test pixel metric cannot be sent twice`() {
        val enrollmentDateET = ZonedDateTime.now(ZoneId.of("America/New_York")).toString()
        val enrollmentDateParsedET: String = ZonedDateTime.parse(enrollmentDateET).format(DateTimeFormatter.ISO_LOCAL_DATE).toString()

        testFeature.experimentFooFeature().setRawStoredState(
            State(
                remoteEnableState = true,
                enable = true,
                assignedCohort = Toggle.State.Cohort(name = "control", weight = 1, enrollmentDateET = enrollmentDateET),
            ),
        )
        val pixelUrl = setupPixelUrl(
            experimentName = "experimentFooFeature",
            cohortName = "control",
            metric = "refreshClicked",
            value = "1",
            conversionWindowDays = "0-1",
            enrollmentDateET = enrollmentDateParsedET,
        )

        var result = pixelInterceptor.intercept(FakeChain(pixelUrl))
        assertEquals("", result.message)
        assertEquals(null, result.body)

        result = pixelInterceptor.intercept(FakeChain(pixelUrl))
        assertEquals("Dropped experiment metrics pixel", result.message)
        assertNotEquals(null, result.body)
    }

    @Test
    fun `test metric doesnt exist drops pixels`() {
        val enrollmentDateET = ZonedDateTime.now(ZoneId.of("America/New_York")).toString()
        val enrollmentDateParsedET: String = ZonedDateTime.parse(enrollmentDateET).format(DateTimeFormatter.ISO_LOCAL_DATE).toString()

        testFeature.experimentFooFeature().setRawStoredState(
            State(
                remoteEnableState = true,
                enable = true,
                assignedCohort = Toggle.State.Cohort(name = "control", weight = 1, enrollmentDateET = enrollmentDateET),
            ),
        )
        val pixelUrl = setupPixelUrl(
            experimentName = "experimentFooFeature",
            cohortName = "control",
            metric = "doesNotExist",
            value = "1",
            conversionWindowDays = "0-1",
            enrollmentDateET = enrollmentDateParsedET,
        )

        val result = pixelInterceptor.intercept(FakeChain(pixelUrl))
        assertEquals("Dropped experiment metrics pixel", result.message)
        assertNotEquals(null, result.body)
    }

    @Test
    fun `test value doesnt exist drops pixels`() {
        val enrollmentDateET = ZonedDateTime.now(ZoneId.of("America/New_York")).toString()
        val enrollmentDateParsedET: String = ZonedDateTime.parse(enrollmentDateET).format(DateTimeFormatter.ISO_LOCAL_DATE).toString()

        testFeature.experimentFooFeature().setRawStoredState(
            State(
                remoteEnableState = true,
                enable = true,
                assignedCohort = Toggle.State.Cohort(name = "control", weight = 1, enrollmentDateET = enrollmentDateET),
            ),
        )
        val pixelUrl = setupPixelUrl(
            experimentName = "experimentFooFeature",
            cohortName = "control",
            metric = "refreshClicked",
            value = "2",
            conversionWindowDays = "0-1",
            enrollmentDateET = enrollmentDateParsedET,
        )

        val result = pixelInterceptor.intercept(FakeChain(pixelUrl))
        assertEquals("Dropped experiment metrics pixel", result.message)
        assertNotEquals(null, result.body)
    }

    @Test
    fun `test cohort name doesnt exist drops pixels`() {
        val enrollmentDateET = ZonedDateTime.now(ZoneId.of("America/New_York")).toString()
        val enrollmentDateParsedET: String = ZonedDateTime.parse(enrollmentDateET).format(DateTimeFormatter.ISO_LOCAL_DATE).toString()

        testFeature.experimentFooFeature().setRawStoredState(
            State(
                remoteEnableState = true,
                enable = true,
                assignedCohort = Toggle.State.Cohort(name = "control", weight = 1, enrollmentDateET = enrollmentDateET),
            ),
        )
        val pixelUrl = setupPixelUrl(
            experimentName = "experimentFooFeature",
            cohortName = "treatment",
            metric = "refreshClicked",
            value = "1",
            conversionWindowDays = "0-1",
            enrollmentDateET = enrollmentDateParsedET,
        )

        val result = pixelInterceptor.intercept(FakeChain(pixelUrl))
        assertEquals("Dropped experiment metrics pixel", result.message)
        assertNotEquals(null, result.body)
    }

    @Test
    fun `test experiment name doesnt exist drops pixels`() {
        val enrollmentDateET = ZonedDateTime.now(ZoneId.of("America/New_York")).toString()
        val enrollmentDateParsedET: String = ZonedDateTime.parse(enrollmentDateET).format(DateTimeFormatter.ISO_LOCAL_DATE).toString()

        testFeature.experimentFooFeature().setRawStoredState(
            State(
                remoteEnableState = true,
                enable = true,
                assignedCohort = Toggle.State.Cohort(name = "control", weight = 1, enrollmentDateET = enrollmentDateET),
            ),
        )
        val pixelUrl = setupPixelUrl(
            experimentName = "myExperiment",
            cohortName = "control",
            metric = "refreshClicked",
            value = "1",
            conversionWindowDays = "0-1",
            enrollmentDateET = enrollmentDateParsedET,
        )

        val result = pixelInterceptor.intercept(FakeChain(pixelUrl))
        assertEquals("Dropped experiment metrics pixel", result.message)
        assertNotEquals(null, result.body)
    }

    private fun setupPixelUrl(
        experimentName: String,
        cohortName: String,
        metric: String,
        value: String,
        conversionWindowDays: String,
        enrollmentDateET: String,
    ): String {
        return String.format(
            PIXEL_TEMPLATE,
            "experiment_metrics_${experimentName}_${cohortName}_android_phone?metric=$metric&value=$value&conversionWindowDays=" +
                "$conversionWindowDays&enrollmentDate=$enrollmentDateET",
        )
    }

    companion object {
        private const val PIXEL_TEMPLATE = "https://improving.duckduckgo.com/t/%s"
    }
}

class FakeStore : MetricsPixelStore {

    private val list = mutableListOf<String>()
    val metrics = mutableMapOf<String, Int>()

    override suspend fun wasPixelFired(tag: String): Boolean {
        return list.contains(tag)
    }

    override fun storePixelTag(tag: String) {
        list.add(tag)
    }

    override suspend fun increaseMetricForPixelDefinition(definition: PixelDefinition, metric: RetentionMetric): Int {
        val tag = "${definition}_$metric"
        val count = metrics.getOrDefault(tag, 0)
        metrics[tag] = count + 1
        return metrics[tag]!!
    }

    override suspend fun getMetricForPixelDefinition(definition: PixelDefinition, metric: RetentionMetric): Int {
        val tag = "${definition}_$metric"
        return metrics.getOrDefault(tag, 0)
    }
}

class FakePluginPoint(testFeature: TestTriggerFeature) : PluginPoint<MetricsPixelPlugin> {
    private val plugin = FakeMetricsPixelPlugin(testFeature)
    override fun getPlugins(): Collection<MetricsPixelPlugin> {
        return listOf(plugin)
    }
}

class FakeMetricsPixelPlugin(private val testFeature: TestTriggerFeature) : MetricsPixelPlugin {

    override suspend fun getMetrics(): List<MetricsPixel> = listOf(
        MetricsPixel(
            metric = "refreshClicked",
            value = "1",
            toggle = testFeature.experimentFooFeature(),
            conversionWindow = listOf(
                ConversionWindow(0, 0),
                ConversionWindow(0, 1),
                ConversionWindow(3, 4),
            ),
        ),
        MetricsPixel(
            metric = "refreshClicked",
            value = "2",
            toggle = testFeature.experimentFooFeature(),
            conversionWindow = listOf(
                ConversionWindow(1, 2),
            ),
        ),
    )
}
