package com.duckduckgo.app.browser.webshare

import android.app.Activity
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.js.messaging.api.JsCallbackData
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNull
import junit.framework.TestCase.assertTrue
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock

@RunWith(AndroidJUnit4::class)
class WebShareChooserTest {

    private val webShareChooser = WebShareChooser()

    @Test
    fun whenTitleAndUrlAndTextEmptyThenParseCorrectData() {
        val data = JsCallbackData(
            params = JSONObject("""{"title":"Sample title","url":"https://example.com/", "text":""}"""),
            featureName = "featureName",
            id = "id",
            method = "method",
        )

        val intent = webShareChooser.createIntent(mock(), data)

        val chooserIntent: Intent = intent.extras?.get(Intent.EXTRA_INTENT) as Intent
        val text = chooserIntent.extras?.getString(Intent.EXTRA_TEXT)
        val title = chooserIntent.extras?.getString(Intent.EXTRA_TITLE)

        assertEquals("https://example.com/", text)
        assertEquals("Sample title", title)
    }

    @Test
    fun whenTitleAndUrlAndTextNullThenParseCorrectData() {
        val data = JsCallbackData(
            params = JSONObject("""{"title":"Sample title","url":"https://example.com/"}"""),
            featureName = "featureName",
            id = "id",
            method = "method",
        )

        val intent = webShareChooser.createIntent(mock(), data)

        val chooserIntent: Intent = intent.extras?.get(Intent.EXTRA_INTENT) as Intent
        val text = chooserIntent.extras?.getString(Intent.EXTRA_TEXT)
        val title = chooserIntent.extras?.getString(Intent.EXTRA_TITLE)

        assertEquals("https://example.com/", text)
        assertEquals("Sample title", title)
    }

    @Test
    fun whenTitleEmptyAndUrlThenParseCorrectData() {
        val data = JsCallbackData(
            params = JSONObject("""{"title":"","url":"https://example.com/"}"""),
            featureName = "featureName",
            id = "id",
            method = "method",
        )

        val intent = webShareChooser.createIntent(mock(), data)

        val chooserIntent: Intent = intent.extras?.get(Intent.EXTRA_INTENT) as Intent
        val text = chooserIntent.extras?.getString(Intent.EXTRA_TEXT)
        val title = chooserIntent.extras?.getString(Intent.EXTRA_TITLE)

        assertEquals("https://example.com/", text)
        assertNull(title)
    }

    @Test
    fun whenTitleAndUrlAndTextThenParseCorrectData() {
        val data = JsCallbackData(
            params = JSONObject("""{"title":"Sample title","url":"https://example.com/", "text":"Test"}"""),
            featureName = "featureName",
            id = "id",
            method = "method",
        )

        val intent = webShareChooser.createIntent(mock(), data)

        val chooserIntent: Intent = intent.extras?.get(Intent.EXTRA_INTENT) as Intent
        val text = chooserIntent.extras?.getString(Intent.EXTRA_TEXT)
        val title = chooserIntent.extras?.getString(Intent.EXTRA_TITLE)

        assertEquals("https://example.com/", text)
        assertEquals("Sample title", title)
    }

    @Test
    fun whenTitleAndTextAndUrlEmptyThenParseCorrectData() {
        val data = JsCallbackData(
            params = JSONObject("""{"title":"Sample title","url":"", "text":"Test"}"""),
            featureName = "featureName",
            id = "id",
            method = "method",
        )

        val intent = webShareChooser.createIntent(mock(), data)

        val chooserIntent: Intent = intent.extras?.get(Intent.EXTRA_INTENT) as Intent
        val text = chooserIntent.extras?.getString(Intent.EXTRA_TEXT)
        val title = chooserIntent.extras?.getString(Intent.EXTRA_TITLE)

        assertEquals("Test", text)
        assertEquals("Sample title", title)
    }

    @Test
    fun whenTitleAndTextAndUrlNullThenParseCorrectData() {
        val data = JsCallbackData(
            params = JSONObject("""{"title":"Sample title", "text":"Test"}"""),
            featureName = "featureName",
            id = "id",
            method = "method",
        )

        val intent = webShareChooser.createIntent(mock(), data)

        val chooserIntent: Intent = intent.extras?.get(Intent.EXTRA_INTENT) as Intent
        val text = chooserIntent.extras?.getString(Intent.EXTRA_TEXT)
        val title = chooserIntent.extras?.getString(Intent.EXTRA_TITLE)

        assertEquals("Test", text)
        assertEquals("Sample title", title)
    }

    @Test
    fun whenParseResultIsOkAndDataNotInitializedThenReturnDataError() {
        val data = webShareChooser.parseResult(Activity.RESULT_OK, null)

        assertEquals(WebShareChooser.DATA_ERROR, data.params.toString())
        assertTrue(data.id.isEmpty())
        assertTrue(data.method.isEmpty())
        assertTrue(data.featureName.isEmpty())
    }

    @Test
    fun whenParseResultIsOkThenReturnSuccess() {
        val data = JsCallbackData(
            params = JSONObject("""{"title":"Sample title", "text":"Test"}"""),
            featureName = "featureName",
            id = "id",
            method = "method",
        )

        webShareChooser.createIntent(mock(), data)
        val result = webShareChooser.parseResult(Activity.RESULT_OK, null)

        assertEquals(WebShareChooser.EMPTY, result.params.toString())
        assertEquals("featureName", data.featureName)
        assertEquals("id", data.id)
        assertEquals("method", data.method)
    }

    @Test
    fun whenParseResultIsCanceledThenReturnAbort() {
        val data = JsCallbackData(
            params = JSONObject("""{"title":"Sample title", "text":"Test"}"""),
            featureName = "featureName",
            id = "id",
            method = "method",
        )

        webShareChooser.createIntent(mock(), data)
        val result = webShareChooser.parseResult(Activity.RESULT_CANCELED, null)

        assertEquals(WebShareChooser.ABORT_ERROR, result.params.toString())
        assertEquals("featureName", data.featureName)
        assertEquals("id", data.id)
        assertEquals("method", data.method)
    }

    @Test
    fun whenParseResultIsOtherThenReturnDataError() {
        val data = JsCallbackData(
            params = JSONObject("""{"title":"Sample title", "text":"Test"}"""),
            featureName = "featureName",
            id = "id",
            method = "method",
        )

        webShareChooser.createIntent(mock(), data)
        val result = webShareChooser.parseResult(Activity.RESULT_FIRST_USER, null)

        assertEquals(WebShareChooser.DATA_ERROR, result.params.toString())
        assertEquals("featureName", data.featureName)
        assertEquals("id", data.id)
        assertEquals("method", data.method)
    }
}
