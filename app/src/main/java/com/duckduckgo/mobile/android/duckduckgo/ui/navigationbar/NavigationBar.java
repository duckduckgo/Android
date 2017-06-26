package com.duckduckgo.mobile.android.duckduckgo.ui.navigationbar;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.PorterDuff;
import android.os.Build;
import android.support.annotation.AttrRes;
import android.support.annotation.ColorInt;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.StyleRes;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.ActionMenuView;
import android.util.AttributeSet;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.FrameLayout;

import com.duckduckgo.mobile.android.duckduckgo.R;

import butterknife.BindView;
import butterknife.ButterKnife;

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

public class NavigationBar extends FrameLayout implements NavigationBarView {

    @BindView(R.id.navigation_bar_action_menu_view)
    ActionMenuView actionMenuView;

    public NavigationBar(@NonNull Context context) {
        super(context);
        init();
    }

    public NavigationBar(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public NavigationBar(@NonNull Context context, @Nullable AttributeSet attrs, @AttrRes int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public NavigationBar(@NonNull Context context, @Nullable AttributeSet attrs, @AttrRes int defStyleAttr, @StyleRes int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init();
    }

    private void init() {
        inflate(getContext(), R.layout.view_navigation_bar, this);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        ButterKnife.bind(this);
    }

    @Override
    public void setBackEnabled(boolean enabled) {
        MenuItem back = actionMenuView.getMenu().findItem(R.id.action_back);
        back.setEnabled(enabled);
        back.getIcon().setColorFilter(getColorForState(enabled), PorterDuff.Mode.SRC_ATOP);
    }

    @Override
    public void setForwardEnabled(boolean enabled) {
        MenuItem forward = actionMenuView.getMenu().findItem(R.id.action_forward);
        forward.setEnabled(enabled);
        forward.getIcon().setColorFilter(getColorForState(enabled), PorterDuff.Mode.SRC_ATOP);
    }

    public void inflateMenu(int resId, MenuInflater menuInflater) {
        menuInflater.inflate(resId, actionMenuView.getMenu());

    }

    public void setOnMenuItemClickListener(ActionMenuView.OnMenuItemClickListener listener) {
        actionMenuView.setOnMenuItemClickListener(listener);
    }

    @ColorInt
    private int getColorForState(boolean enabled) {
        return ContextCompat.getColor(getContext(), enabled ? R.color.navigation_bar_enabled : R.color.navigation_bar_disabled);
    }
}
