package com.duckduckgo.mobile.android.duckduckgo.util;

import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.util.regex.Pattern;

/**
 * Created by fgei on 5/23/17.
 */

public class UrlUtils {

    private static final String WEB_URL_REGEX = "^(https?:\\/\\/)?([\\da-z\\.-]+\\.[a-z\\.]{2,6}|(([\\d]+[.]){3}[\\d]+))([\\/:?=&#]{1}[\\da-z\\.-]+)*[\\/\\?]?$";

    public static boolean isUrl(@Nullable String url) {
        if (url == null) return false;
        return Pattern.compile(WEB_URL_REGEX).matcher(url).matches();
    }

    public static String getUrlWithScheme(@NonNull String url) {
        if (url.startsWith("http://") || url.startsWith("https://")) {
            return url;
        }
        return "http://" + url;
    }
}
