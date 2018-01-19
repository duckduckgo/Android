/*
 * Copyright (c) 2018 DuckDuckGo
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.duckduckgo.app.migration.legacy;

import java.util.LinkedList;
import android.content.SharedPreferences;
import android.net.Uri;

/**
 * This class contains utility static methods, such as loading preferences as an array or decoding bitmaps.
 */
public final class LegacyUtils {

    public static LinkedList<String> loadList(SharedPreferences prefs, String listName) {
        int size = prefs.getInt(listName + "_size", 0);
        LinkedList<String> list = new LinkedList<String>();
        for(int i=0;i<size;i++)  {
            list.add(prefs.getString(listName + "_" + i, null));
        }
        return list;
    }

    /**
     * Checks to see if URL is DuckDuckGo SERP
     * Returns the query if it's a SERP, otherwise null
     *
     * @param url
     * @return
     */
    static public String getQueryIfSerp(String url) {
        if(!isSerpUrl(url)) {
            return null;
        }

        Uri uri = Uri.parse(url);
        String query = uri.getQueryParameter("q");
        if(query != null)
            return query;

        String lastPath = uri.getLastPathSegment();
        if(lastPath == null)
            return null;

        if(!lastPath.contains(".html")) {
            return lastPath.replace("_", " ");
        }

        return null;
    }

    public static boolean isSerpUrl(String url) {
        return url.contains("duckduckgo.com");
    }

}
