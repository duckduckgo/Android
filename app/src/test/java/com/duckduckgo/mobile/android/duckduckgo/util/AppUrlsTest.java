package com.duckduckgo.mobile.android.duckduckgo.util;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Created by fgei on 5/18/17.
 */

public class AppUrlsTest {
    //Base URLS
    @Test
    public void getBase_isCorrect() throws Exception {
        final String expected = "duckduckgo.com";
        final String actual = AppUrls.getBase();
        assertEquals(expected, actual);
    }
    @Test
    public void getHome_isCorrect() throws Exception {
        final String expected = "https://www.duckduckgo.com/?ko=-1&kl=wt-wt";
        final String actual = AppUrls.getHome();
        assertEquals(expected, actual);
    }
    @Test
    public void isDuckDuckGoReturnTrueIfIsDDDG() throws Exception {
        final String url = "https://duckduckgo.com/?q=some+search&t=hy&ia=web";
        final boolean result = AppUrls.isDuckDuckGo(url);
        assertTrue(result);
    }
    @Test
    public void isDuckDuckGoReturnTrueIfIsDDDGwithWWW() throws Exception {
        final String url = "https://www.duckduckgo.com/?q=some+search&t=hy&ia=web";
        final boolean result = AppUrls.isDuckDuckGo(url);
        assertTrue(result);
    }

    //isDuckDuckGo
    @Test
    public void isDuckDuckGoReturnFalseIfNotDDGUsingCorrectUrl() throws Exception {
        final String url = "https://www.test.com/?ko=-1&kl=wt-wt&q=some%20search";
        final boolean result = AppUrls.isDuckDuckGo(url);
        assertFalse(result);
    }
    @Test
    public void isDuckDuckGoReturnFalseIfNotDDGUsingCorrectUrlTatContainsDDG() throws Exception {
        final String url = "https://www.test.com/?ko=-1&kl=wt-wt&q=duckduckgo.com";
        final boolean result = AppUrls.isDuckDuckGo(url);
        assertFalse(result);
    }
    @Test
    public void isDuckDuckGoReturnFalseIfNotDDGUsingRandomText() throws Exception {
        final String url ="asd.oiasud";
        final boolean result = AppUrls.isDuckDuckGo(url);
        assertFalse(result);
    }
    @Test
    public void isDuckDuckGoReturnFalseIfNotDDGUsingRandomTextThatContainsDDG() throws Exception {
        final String url = "as///d.duckduckgo.com";
        final boolean result = AppUrls.isDuckDuckGo(url);
        assertFalse(result);
    }

    //getQuery
    @Test
    public void getQueryReturnSearchParamsInDGUrlFromWeb() throws Exception {
        final String url = "https://duckduckgo.com/?q=some+search&t=hc&ia=web";
        final String expected = "some search";
        final String result = AppUrls.getQuery(url);
        assertEquals(expected, result);
    }
    @Test
    public void getQueryReturnSearchParamsInDGUrl() throws Exception {
        final String url = "https://www.duckduckgo.com/?ko=-1&kl=wt-wt&q=some+search";
        final String expected = "some search";
        final String result = AppUrls.getQuery(url);
        assertEquals(expected, result);
    }
    @Test
    public void getQueryReturnEmptyStringInDDGUrl() throws Exception {
        final String url = "https://www.duckduckgo.com/?ko=-1&kl=wt-wt";
        final String expected = "";
        final String result = AppUrls.getQuery(url);
        assertEquals(expected, result);
    }
    @Test
    public void getQueryReturnNullInNotDDGUrl() throws Exception {
        final String url = "https://www.test.com/?ko=-1&kl=wt-wt&q=some+search";
        final String result = AppUrls.getQuery(url);
        assertNull(result);
    }
    @Test
    public void getQueryReturnNullInRandomText() throws Exception {
        final String url = "asd''asd/////.com/?q=duckduckgo.com";
        final String result = AppUrls.getQuery(url);
        assertNull(result);
    }

    //getSearchUrl
    @Test
    public void getSearchUrlCreatesCorrectUrlWithParams() throws Exception {
        final String expected = "https://www.duckduckgo.com/?ko=-1&kl=wt-wt&q=some+search";
        final String query = "some search";
        final String result = AppUrls.getSearchUrl(query);
        assertEquals(expected, result);
    }
}
