package com.duckduckgo.mobile.android.duckduckgo.data.bookmark;

import com.duckduckgo.mobile.android.duckduckgo.data.base.JsonEntity;
import com.duckduckgo.mobile.android.duckduckgo.domain.bookmark.Bookmark;

import org.json.JSONException;
import org.json.JSONObject;

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

public class BookmarkJsonEntity implements Bookmark, JsonEntity {
    private String id;
    private String name;
    private String url;
    private int index;

    public BookmarkJsonEntity() {
    }

    public BookmarkJsonEntity(Bookmark bookmark) {
        id = bookmark.getId();
        name = bookmark.getName();
        url = bookmark.getUrl();
        index = bookmark.getIndex();
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getUrl() {
        return url;
    }

    @Override
    public int getIndex() {
        return index;
    }

    private static final String KEY_ID = "id";
    private static final String KEY_INDEX = "index";
    private static final String KEY_NAME = "name";
    private static final String KEY_URL = "url";


    @Override
    public String toJson() {
        String json = "";
        try {
            JSONObject jsonObject = new JSONObject();
            jsonObject.put(KEY_ID, id);
            jsonObject.put(KEY_INDEX, index);
            jsonObject.put(KEY_NAME, name);
            jsonObject.put(KEY_URL, url);
            json = jsonObject.toString();
        } catch (JSONException e) {
            Timber.e(e, "toJson");
        }
        return json;
    }

    @Override
    public void fromJson(String json) {
        try {
            JSONObject jsonObject = new JSONObject(json);
            id = jsonObject.getString(KEY_ID);
            index = jsonObject.getInt(KEY_INDEX);
            name = jsonObject.getString(KEY_NAME);
            url = jsonObject.getString(KEY_URL);
        } catch (JSONException e) {
            Timber.e(e, "fromJson, json: %s", json);
        }
    }

    @Override
    public String getKey() {
        return id;
    }
}
