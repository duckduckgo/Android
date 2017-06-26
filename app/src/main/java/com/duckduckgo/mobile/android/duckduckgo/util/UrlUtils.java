package com.duckduckgo.mobile.android.duckduckgo.util;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.util.regex.Pattern;

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

public class UrlUtils {

    private static final String WEB_URL_REGEX = "^(https?:\\/\\/)?([\\da-z\\.-]+\\.[a-z\\.]{2,6}|(([\\d]+[.]){3}[\\d]+))([\\/]?[\\/:?=&#]{1}[\\%\\da-zA-Z_\\.-]+)*[\\/\\?]?$";

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
