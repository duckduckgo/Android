package com.duckduckgo.mobile.android.duckduckgo.util;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;

import timber.log.Timber;

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

public class AppUrls {

    private class Urls {
        static final String BASE = "duckduckgo.com";
        static final String HOME = "https://www.duckduckgo.com/?ko=-1&kl=wt-wt";
    }

    private class Params {
        static final String SEARCH = "q";
    }

    private class ParamValues {
        static final String OMNIBAR_OFF = "-1";
    }

    public static String getBase() {
        return Urls.BASE;
    }

    public static String getHome() {
        return Urls.HOME;
    }

    public static boolean isDuckDuckGo(@NonNull String url) {
        try {
            URI uri = new URI(url);
            String authority = uri.getAuthority();
            if (authority == null) return false;
            return authority.contains(Urls.BASE);
        } catch (URISyntaxException e) {
            Timber.e(e, "isDuckDuckGo, url: %s", url);
            return false;
        }
    }

    @Nullable
    public static String getQuery(@NonNull String url) {
        if (!isDuckDuckGo(url)) return null;
        try {
            URI uri = new URI(url);
            String query = uri.getRawQuery();
            if (query == null || query.length() == 0) return "";
            String[] paramsSplit = query.split("&");
            Map<String, String> params = new HashMap<>();
            for (String param : paramsSplit) {
                String[] p = param.split("=");
                params.put(p[0], p[1]);
            }
            if (params.containsKey(Params.SEARCH)) {
                String result = params.get(Params.SEARCH);
                try {
                    result = URLDecoder.decode(result, "UTF-8");
                } catch (UnsupportedEncodingException e) {
                    Timber.e(e, "getQuery, url: %s", url);
                    return "";
                }
                return result;
            }
        } catch (URISyntaxException e) {
            Timber.e(e, "getQuery, url: %s", url);
            return "";
        }
        return "";
    }

    @NonNull
    public static String getSearchUrl(@NonNull String query) {
        String queryEncoded = query;
        try {
            queryEncoded = URLEncoder.encode(query, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            Timber.e(e, "getSearchUrl, query: %s", query);
        }
        StringBuilder builder = new StringBuilder(Urls.HOME);
        builder.append("&").append(Params.SEARCH).append("=").append(queryEncoded);
        return builder.toString();
    }
}
