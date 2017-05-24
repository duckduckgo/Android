package com.duckduckgo.mobile.android.duckduckgo.util;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.net.MalformedURLException;
import java.net.URL;

/**
 * Created by fgei on 5/23/17.
 */

public class UrlUtils {

    public static boolean isUrl(@Nullable String url) {
        if(url == null) return false;
        return url.contains(".") && !url.contains(" ");
    }

    public static String getUrlWithScheme(@NonNull String url) {
        if(url.startsWith("http://") || url.startsWith("https://")) {
            return url;
        }
        return "http://" + url;
    }
}
