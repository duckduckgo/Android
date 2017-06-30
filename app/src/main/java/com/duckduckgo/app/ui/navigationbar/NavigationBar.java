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

package com.duckduckgo.app.ui.navigationbar;

import android.content.Context;
import android.graphics.PorterDuff;
import android.support.annotation.ColorInt;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.ActionMenuView;
import android.util.AttributeSet;
import android.view.MenuInflater;
import android.view.MenuItem;

import com.duckduckgo.app.R;

public class NavigationBar extends ActionMenuView implements NavigationBarView {
    public NavigationBar(Context context) {
        super(context);
    }

    public NavigationBar(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public void setBackEnabled(boolean enabled) {
        MenuItem back = getMenu().findItem(R.id.action_back);
        back.setEnabled(enabled);
        back.getIcon().setColorFilter(getColorForState(enabled), PorterDuff.Mode.SRC_ATOP);
    }

    @Override
    public void setForwardEnabled(boolean enabled) {
        MenuItem forward = getMenu().findItem(R.id.action_forward);
        forward.setEnabled(enabled);
        forward.getIcon().setColorFilter(getColorForState(enabled), PorterDuff.Mode.SRC_ATOP);
    }

    public void inflateMenu(int resId, MenuInflater menuInflater) {
        menuInflater.inflate(resId, getMenu());

    }

    @ColorInt
    private int getColorForState(boolean enabled) {
        return ContextCompat.getColor(getContext(), enabled ? R.color.navigation_bar_enabled : R.color.navigation_bar_disabled);
    }
}
