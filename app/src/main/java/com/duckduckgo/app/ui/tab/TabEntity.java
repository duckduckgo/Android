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

package com.duckduckgo.app.ui.tab;

import android.graphics.Bitmap;

import com.duckduckgo.app.domain.tab.Tab;

import java.util.UUID;

public class TabEntity implements Tab {

    private String id;
    private String title = "";
    private String currentUrl = "";
    private boolean canGoBack = false;
    private boolean canGoForward = false;
    private Bitmap favicon;

    private TabEntity() {

    }

    public TabEntity(Tab tab) {
        id = tab.getId();
        title = tab.getTitle();
        currentUrl = tab.getCurrentUrl();
        canGoBack = tab.canGoBack();
        canGoForward = tab.canGoForward();
    }

    public static TabEntity create() {
        TabEntity tab = new TabEntity();
        tab.id = UUID.randomUUID().toString();
        return tab;
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

    public Bitmap getFavicon() {
        return favicon;
    }

    public void setFavicon(Bitmap favicon) {
        this.favicon = favicon;
    }
}
