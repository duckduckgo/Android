package com.duckduckgo.mobile.android.duckduckgo.util;

import java.net.MalformedURLException;
import java.net.URL;

/**
 * Created by fgei on 5/23/17.
 */

public class UrlUtils {

    public static boolean isUrl(String url) {
        return url.contains(".") && !url.contains(" ");
    }

    public static String getValidUrl(String url) {
        if(url.startsWith("http://") || url.startsWith("https://")) {
            return url;
        }
        return "http://" + url;
    }

    public static boolean isValidUrl(String url) {
        return true;
    }

    public static boolean isHttpUrl(String url) {
        return true;
    }

    public static boolean isHttpsUrl(String url) {
        return true;
    }
}
