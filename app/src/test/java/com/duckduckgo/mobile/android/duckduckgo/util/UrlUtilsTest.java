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
            //final boolean result = UrlUtils.isUrl(url);
            //final boolean result = URLUtil.isValidUrl(url);
            //assertEquals(expected, result);
        }
    }

    @Test
    public void shouldReturnFalseIfWrongUrlIsProvided() {
        //final boolean expected = false;
        //String url = "test";
        //boolean result = UrlUtils.isUrl(url);
        //assertEquals(expected, result);
    }
}
