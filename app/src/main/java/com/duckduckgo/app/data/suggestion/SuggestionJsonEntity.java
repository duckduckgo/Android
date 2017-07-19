/*
 * Copyright (c) 2017 DuckDuckGo
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

package com.duckduckgo.app.data.suggestion;

import com.duckduckgo.app.data.base.JsonEntity;
import com.duckduckgo.app.domain.suggestion.Suggestion;

import org.json.JSONException;
import org.json.JSONObject;

import timber.log.Timber;

public class SuggestionJsonEntity implements Suggestion, JsonEntity {
    private String type;
    private String suggestion;

    public SuggestionJsonEntity() {
    }

    public SuggestionJsonEntity(Suggestion suggestion) {
        this.type = suggestion.getType();
        this.suggestion = suggestion.getSuggestion();
    }

    @Override
    public String getType() {
        return type;
    }

    @Override
    public String getSuggestion() {
        return suggestion;
    }

    @Override
    public String toJson() {
        String json = "";
        try {
            JSONObject jsonObject = new JSONObject();
            jsonObject.put(type, suggestion);
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
            type = jsonObject.keys().next();
            suggestion = jsonObject.getString(type);
        } catch (JSONException e) {
            Timber.e(e, "fromJson, json: %s", json);
        }
    }

    @Override
    public String getKey() {
        return null;
    }
}
