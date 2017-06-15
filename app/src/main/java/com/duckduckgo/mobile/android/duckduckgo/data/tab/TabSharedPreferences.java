package com.duckduckgo.mobile.android.duckduckgo.data.tab;

import android.content.Context;

import com.duckduckgo.mobile.android.duckduckgo.data.base.BaseSharedPreferencesDataStore;

/**
 * Created by fgei on 6/14/17.
 */

public class TabSharedPreferences extends BaseSharedPreferencesDataStore<TabJsonEntity> {

    private static final String PREF_NAME = "tabs";

    public TabSharedPreferences(Context context) {
        super(context, TabJsonEntity.class);
    }

    @Override
    public String getFileName() {
        return PREF_NAME;
    }
}
