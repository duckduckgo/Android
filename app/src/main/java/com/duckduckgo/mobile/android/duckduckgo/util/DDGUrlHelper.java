package com.duckduckgo.mobile.android.duckduckgo.util;

/**
 * Created by fgei on 5/15/17.
 */

public class DDGUrlHelper {
    private static final String BASE_URL = "https://duckduckgo.com/";

    private static final String PARAM_REMOVE_OMNIBOX = "ko=-1";
    private static final String PARAM_QUERY = "q=";

    private DDGUrlHelper() {
    }

    public static String getBaseUrl() {
        return BASE_URL + "?" + PARAM_REMOVE_OMNIBOX;
    }

    public static String getUrlForQuery(String query) {
        return getBaseUrl() + "&" + PARAM_QUERY + query;
    }
}
