package com.duckduckgo.mobile.android.duckduckgo.util;

import android.support.annotation.NonNull;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by fgei on 5/18/17.
 */

public class AppUrls {

    private class Url {
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
        return Url.BASE;
    }

    public static String getHome() {
        return Url.HOME;
    }

    public static boolean isDuckDuckGo(@NonNull String url) {
        try {
            URI uri = new URI(url);
            String authority = uri.getAuthority();
            if(authority == null) return false;
            return authority.contains(Url.BASE);
        } catch(URISyntaxException e) {
        }
        return false;
    }

    public static String getQuery(@NonNull String url) {
        if(!isDuckDuckGo(url)) return null;
        try {
            URI uri = new URI(url);
            String query = uri.getQuery();
            if(query == null || query.length() == 0) return "";
            String[] paramsSplit = query.split("&");
            Map<String, String> params = new HashMap<>();
            for(String param : paramsSplit) {
                String[] p = param.split("=");
                params.put(p[0], p[1]);
            }
            if(params.containsKey(Params.SEARCH)) {
                String result = params.get(Params.SEARCH);
                try {
                    result = URLDecoder.decode(result, "UTF-8");
                } catch(UnsupportedEncodingException e) {
                }
                return result;
            }
        } catch(URISyntaxException e) {
        }
        return "";
    }

    @NonNull
    public static String getSearchUrl(@NonNull String query) {
        String queryEncoded = query;
        try {
            queryEncoded = URLEncoder.encode(query, "UTF-8");
        } catch(UnsupportedEncodingException e) {
        }
        StringBuilder builder = new StringBuilder(Url.HOME);
        builder.append("&").append(Params.SEARCH).append("=").append(queryEncoded);
        return builder.toString();
    }
}
