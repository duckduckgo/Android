package com.duckduckgo.mobile.android.duckduckgo.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 *    Copyright 2017 DuckDuckGo
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

public class UrlUtilsTest {

    @Test
    public void whenValidUrlThenisUrlIsTrue() {
        final String[] urls = {"http://test.com", "https://test.com", "www.test.com", "192.168.0.1", "120.0.20.0"};
        for (String url : urls) {
            final boolean result = UrlUtils.isUrl(url);
            assertTrue(result);
        }
    }

    @Test
    public void whenInvalidUrlThenIsUrlIsFalse() {
        final String[] urls = {"test", "test:test", "http://", "https://test", "http test com", "120.20", "20", ".", " "};
        for (String url : urls) {
            final boolean result = UrlUtils.isUrl(url);
            assertFalse(result);
        }
    }

    @Test
    public void whenNullThenIsUrlIsFalse() {
        final String url = null;
        final boolean result = UrlUtils.isUrl(url);
        assertFalse(result);
    }

    @Test
    public void whenHostIsValidThenIsUrlIsTrue() {
        assertTrue(UrlUtils.isUrl("test.com"));
        assertTrue(UrlUtils.isUrl("121.33.2.11"));
    }

    @Test
    public void whenHostIsInvalidThenIsUrlIsFalse() {
        assertFalse(UrlUtils.isUrl("t est.com"));
        assertFalse(UrlUtils.isUrl("test!com.com"));
        assertFalse(UrlUtils.isUrl("121.33.33."));
    }

    @Test
    public void whenSchemeIsValidThenIsUrlIsTrue() {
        assertTrue(UrlUtils.isUrl("http://test.com"));
        assertTrue(UrlUtils.isUrl("http://121.33.2.11"));
    }

    @Test
    public void whenSchemeIsInvalidThenIsUrlIsFalse() {
        assertFalse(UrlUtils.isUrl("asdas://test.com"));
        assertFalse(UrlUtils.isUrl("asdas://121.33.2.11"));
    }

    @Test
    public void whenPathIsValidThenIsUrlIsTrue() {
        assertTrue(UrlUtils.isUrl("http://test.com/path"));
        assertTrue(UrlUtils.isUrl("http://121.33.2.11/path"));
        assertTrue(UrlUtils.isUrl("test.com/path"));
        assertTrue(UrlUtils.isUrl("121.33.2.11/path"));
    }

    @Test
    public void whenPathIsInvalidThenIsUrlIsFalse() {
        assertFalse(UrlUtils.isUrl("http://test.com/pa th"));
        assertFalse(UrlUtils.isUrl("http://121.33.2.11/pa th"));
        assertFalse(UrlUtils.isUrl("test.com/pa th"));
        assertFalse(UrlUtils.isUrl("121.33.2.11/pa th"));
    }

    @Test
    public void whenParamsAreValidThenIsUrlIsTrue() {
        assertTrue(UrlUtils.isUrl("http://test.com?s=dafas&d=342"));
        assertTrue(UrlUtils.isUrl("http://121.33.2.11?s=dafas&d=342"));
        assertTrue(UrlUtils.isUrl("test.com?s=dafas&d=342"));
        assertTrue(UrlUtils.isUrl("121.33.2.11?s=dafas&d=342"));
    }

    @Test
    public void whenParamsAreInvalidThenIsUrlIsFalse() {
        assertFalse(UrlUtils.isUrl("http://test.com?s=!"));
        assertFalse(UrlUtils.isUrl("http://121.33.2.11?s=!"));
        assertFalse(UrlUtils.isUrl("test.com?s=!"));
        assertFalse(UrlUtils.isUrl("121.33.2.11?s=!"));
    }

    @Test
    public void whenGivenSimpleStringThenIsUrlIsFalse() {
        assertFalse(UrlUtils.isUrl("randomtext"));
    }

    @Test
    public void whenGivenStringWithDotPrefixThenIsUrlIsFalse() {
        assertFalse(UrlUtils.isUrl(".randomtext"));
    }

    @Test
    public void whenGivenStringWithDotSuffixThenIsUrlIsFalse() {
        assertFalse(UrlUtils.isUrl("randomtext."));
    }

    @Test
    public void whenGivenNumberThenIsUrlIsFalse() {
        assertFalse(UrlUtils.isUrl("33"));
    }

    @Test
    public void whenUrlIsWithoutSchemeTheneGetUrlWithSchemeReturnUrlWithScheme() {
        final String expected = "http://test.com";
        final String text = "test.com";
        final String result = UrlUtils.getUrlWithScheme(text);
        assertEquals(expected, result);
    }

    @Test
    public void whenUrlIsWithSchemeThenGetUrlWithSchemeReturnSameUrl() {
        final String expected = "https://test.com";
        final String result = UrlUtils.getUrlWithScheme(expected);
        assertEquals(expected, result);
    }
}
