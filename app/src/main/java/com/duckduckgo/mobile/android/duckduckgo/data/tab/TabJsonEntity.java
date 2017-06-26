package com.duckduckgo.mobile.android.duckduckgo.data.tab;

import com.duckduckgo.mobile.android.duckduckgo.data.base.JsonEntity;
import com.duckduckgo.mobile.android.duckduckgo.domain.tab.Tab;

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

public class TabJsonEntity implements Tab, JsonEntity {

    private String id;
    private String title;
    private String currentUrl;
    private boolean canGoBack;
    private boolean canGoForward;

    public TabJsonEntity() {
    }

    public TabJsonEntity(Tab tab) {
        id = tab.getId();
        title = tab.getTitle();
        currentUrl = tab.getCurrentUrl();
        canGoBack = tab.canGoBack();
        canGoForward = tab.canGoForward();
    }

    @Override
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    @Override
    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    @Override
    public String getCurrentUrl() {
        return currentUrl;
    }

    public void setCurrentUrl(String currentUrl) {
        this.currentUrl = currentUrl;
    }

    @Override
    public boolean canGoBack() {
        return canGoBack;
    }

    public void setCanGoBack(boolean canGoBack) {
        this.canGoBack = canGoBack;
    }

    @Override
    public boolean canGoForward() {
        return canGoForward;
    }

    public void setCanGoForward(boolean canGoForward) {
        this.canGoForward = canGoForward;
    }

    private static final String KEY_ID = "id";
    private static final String KEY_TITLE = "title";
    private static final String KEY_CURRENT_URL = "current_url";
    private static final String KEY_CAN_GO_BACK = "can_go_back";
    private static final String KEY_CAN_GO_FORWARD = "can_go_forward";

    @Override
    public String toJson() {
        String json = "";
        try {
            JSONObject jsonObject = new JSONObject();
            jsonObject.put(KEY_ID, id);
            jsonObject.put(KEY_TITLE, title);
            jsonObject.put(KEY_CURRENT_URL, currentUrl);
            jsonObject.put(KEY_CAN_GO_BACK, canGoBack);
            jsonObject.put(KEY_CAN_GO_FORWARD, canGoForward);
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
            title = jsonObject.getString(KEY_TITLE);
            currentUrl = jsonObject.getString(KEY_CURRENT_URL);
            canGoBack = jsonObject.getBoolean(KEY_CAN_GO_BACK);
            canGoForward = jsonObject.getBoolean(KEY_CAN_GO_FORWARD);
        } catch (JSONException e) {
            Timber.e(e, "fromJson, json: %s", json);
        }
    }

    @Override
    public String getKey() {
        return id;
    }
}
