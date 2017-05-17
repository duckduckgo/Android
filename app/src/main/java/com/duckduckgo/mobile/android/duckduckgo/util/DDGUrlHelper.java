package com.duckduckgo.mobile.android.duckduckgo.util;

import android.net.Uri;
import android.util.Log;

import java.util.Set;

/**
 * Created by fgei on 5/15/17.
 */

public class DDGUrlHelper {
    private static final String BASE_URL = "https://duckduckgo.com/";

    private static final String PARAM_OMNIBOX = "ko";
    private static final String PARAM_QUERY = "q";
    private static final String VALUE_NO_OMNIBOX = "-1";

    private DDGUrlHelper() {
    }

    public static String getBaseUrl() {
        return BASE_URL + "?" + PARAM_OMNIBOX + "=" + VALUE_NO_OMNIBOX;
    }

    public static String getUrlForQuery(String query) {
        return getBaseUrl() + "&" + PARAM_QUERY + "=" + query;
    }

    public static boolean isUrlDDG(String url) {
        Uri uri = Uri.parse(url);
        String authority = uri.getAuthority();
        return BASE_URL.contains(authority);
    }

    public static String getQuery(String url) {
        if(!isUrlDDG(url)) return "";
        Uri uri = Uri.parse(url);
        return uri.getQueryParameter(PARAM_QUERY);
    }
}
