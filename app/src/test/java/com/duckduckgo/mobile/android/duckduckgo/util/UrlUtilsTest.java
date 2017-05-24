package com.duckduckgo.mobile.android.duckduckgo.util;

import android.webkit.URLUtil;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Created by fgei on 5/23/17.
 */

public class UrlUtilsTest {

    @Test
    public void shouldReturnTrueIfCorrectUrlIsProvided() {
        final boolean expected = true;
        final String[] urls = {"http://test.com", "https://test.com", "www.test.com"};
        for(String url : urls) {
            final boolean result = UrlUtils.isUrl(url);
            assertEquals(expected, result);
        }
    }

    @Test
    public void shouldReturnFalseIfWrongUrlIsProvided() {
        final boolean expected = false;
        final String[] urls = {"test", "test:test", "http://", "https://test", "http test com"};
        for(String url : urls) {
            final boolean result = UrlUtils.isUrl(url);
            assertEquals(expected, result);
        }
    }

    @Test
    public void shouldReturnValidUrlWhenSchemeIsMissing() {
        final String expected = "http://test.com";
        final String text = "test.com";
        final String result = UrlUtils.getUrlWithScheme(text);
        assertEquals(expected, result);
    }

    @Test
    public void shouldReturnValidUrlWhenSchemeIsPresent() {
        final String expected = "https://test.com";
        final String test = "https://test.com";
        final String result = UrlUtils.getUrlWithScheme(expected);
        assertEquals(expected, result);
    }
}
