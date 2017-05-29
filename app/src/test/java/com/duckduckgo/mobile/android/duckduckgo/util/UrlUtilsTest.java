package com.duckduckgo.mobile.android.duckduckgo.util;

import android.webkit.URLUtil;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Created by fgei on 5/23/17.
 */

public class UrlUtilsTest {

    @Test
    public void whenIsUrlWithValidUrlsThenSucceeds() {
        final String[] urls = {"http://test.com", "https://test.com", "www.test.com"};
        for(String url : urls) {
            final boolean result = UrlUtils.isUrl(url);
            assertTrue(result);
        }
    }

    @Test
    public void whenIsUrlWitInvalidUrlsThenFails() {
        final String[] urls = {"test", "test:test", "http://", "https://test", "http test com"};
        for(String url : urls) {
            final boolean result = UrlUtils.isUrl(url);
            assertFalse(result);
        }
    }

    @Test
    public void whenIsUrlWithNullThenFails() {
        final String url = null;
        final boolean result = UrlUtils.isUrl(url);
        assertFalse(result);
    }

    @Test
    public void whenGetUrlWithSchemeWithUrlWithoutSchemeThenReturnUrlWithScheme() {
        final String expected = "http://test.com";
        final String text = "test.com";
        final String result = UrlUtils.getUrlWithScheme(text);
        assertEquals(expected, result);
    }

    @Test
    public void whenGetUrlWithSchemewithUrlWithSchemeThenReturnSameUrl() {
        final String expected = "https://test.com";
        final String result = UrlUtils.getUrlWithScheme(expected);
        assertEquals(expected, result);
    }
}
