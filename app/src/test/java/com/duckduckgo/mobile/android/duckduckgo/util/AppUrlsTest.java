package com.duckduckgo.mobile.android.duckduckgo.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
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

public class AppUrlsTest {
    @Test
    public void whenGetBaseThenReturnBaseUrl() {
        final String expected = "duckduckgo.com";
        final String actual = AppUrls.getBase();
        assertEquals(expected, actual);
    }

    @Test
    public void whenGetHomeThenReturnHomeUrl() {
        final String expected = "https://www.duckduckgo.com/?ko=-1&kl=wt-wt";
        final String actual = AppUrls.getHome();
        assertEquals(expected, actual);
    }

    @Test
    public void whenUrlIsDDGThenIsDuckDuckGoIsTrue() {
        final String url = "https://duckduckgo.com/?q=some+search&t=hy&ia=web";
        final boolean result = AppUrls.isDuckDuckGo(url);
        assertTrue(result);
    }

    @Test
    public void whenUrlIsDDGWithWWWThenIsDuckDuckGoIsTrue() {
        final String url = "https://www.duckduckgo.com/?q=some+search&t=hy&ia=web";
        final boolean result = AppUrls.isDuckDuckGo(url);
        assertTrue(result);
    }

    @Test
    public void whenUrlIsValidNotDDGThenIsDuckDuckGoIsFalse() {
        final String url = "https://www.test.com/?ko=-1&kl=wt-wt&q=some%20search";
        final boolean result = AppUrls.isDuckDuckGo(url);
        assertFalse(result);
    }

    @Test
    public void whenUrlIsValidNotDDGAndContainsDuckDuckGoUrlThenIsDuckDuckGoIsFalse() {
        final String url = "https://www.test.com/?ko=-1&kl=wt-wt&q=duckduckgo.com";
        final boolean result = AppUrls.isDuckDuckGo(url);
        assertFalse(result);
    }

    @Test
    public void whenUrlIsSimpleTextThenIsDuckDuckGoIsFalse() {
        final String url = "asd.oiasud";
        final boolean result = AppUrls.isDuckDuckGo(url);
        assertFalse(result);
    }

    @Test
    public void whenUrlIsSimpleTextThatContainsDuckDuckGoThenIsDuckDuckGoReturnFalse() {
        final String url = "as///d.duckduckgo.com";
        final boolean result = AppUrls.isDuckDuckGo(url);
        assertFalse(result);
    }

    @Test
    public void whenUrlIsDDGFromWebWithQueryThenGetQueryReturnCorrectQuery() {
        final String url = "https://duckduckgo.com/?q=some+search&t=hc&ia=web";
        final String expected = "some search";
        final String result = AppUrls.getQuery(url);
        assertEquals(expected, result);
    }

    @Test
    public void whenUrlIsDDGWithQueryThenGetQueryReturnCorrectQuery() {
        final String url = "https://www.duckduckgo.com/?ko=-1&kl=wt-wt&q=some+search";
        final String expected = "some search";
        final String result = AppUrls.getQuery(url);
        assertEquals(expected, result);
    }

    @Test
    public void whenUrlIsDDGWithoutQueryThenGetQueryReturnEmptyString() {
        final String url = "https://www.duckduckgo.com/?ko=-1&kl=wt-wt";
        final String expected = "";
        final String result = AppUrls.getQuery(url);
        assertEquals(expected, result);
    }

    @Test
    public void whenUrlIsNotDDGWithQueryThenGetQueryReturnNull() {
        final String url = "https://www.test.com/?ko=-1&kl=wt-wt&q=some+search";
        final String result = AppUrls.getQuery(url);
        assertNull(result);
    }

    @Test
    public void whenUrlIsRandomTextThenGetQueryReturnNull() {
        final String url = "asd''asd/////.com/?q=duckduckgo.com";
        final String result = AppUrls.getQuery(url);
        assertNull(result);
    }

    @Test
    public void whenUrlIsDDGWithQueryWithEncodedCharactersThenGetQueryReturnCorrectQuery() {
        assertEquals("1 + 1", AppUrls.getQuery("https://www.duckduckgo.com/?ko=-1&kl=wt-wt&q=1+%2B+1"));
        assertEquals("1+1", AppUrls.getQuery("https://www.duckduckgo.com/?ko=-1&kl=wt-wt&q=1%2B1"));
        assertEquals("1 @ % &  4", AppUrls.getQuery("https://www.duckduckgo.com/?ko=-1&kl=wt-wt&q=1+%40+%25+%26++4"));
    }

    @Test
    public void whenProvidedQueryThenGetSearchUrlReturnCorrectDuckDuckGoUrl() {
        final String expected = "https://www.duckduckgo.com/?ko=-1&kl=wt-wt&q=some+search";
        final String query = "some search";
        final String result = AppUrls.getSearchUrl(query);
        assertEquals(expected, result);
    }
}
