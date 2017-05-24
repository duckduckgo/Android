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

    public static String getUrlWithScheme(String url) {
        if(url.startsWith("http://") || url.startsWith("https://")) {
            return url;
        }
        return "http://" + url;
    }
}
