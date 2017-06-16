package com.duckduckgo.mobile.android.duckduckgo.data.tab;

import android.content.Context;

import com.duckduckgo.mobile.android.duckduckgo.data.base.SharedPreferencesDataStore;

/**
 * Created by fgei on 6/14/17.
 */

public class TabSharedPreferences extends SharedPreferencesDataStore<TabJsonEntity> {

    private static final String PREF_NAME = "tabs";

    public TabSharedPreferences(Context context) {
        super(context, PREF_NAME, new EntityCreator<TabJsonEntity>() {
            @Override
            public TabJsonEntity create() {
                return new TabJsonEntity();
            }
        });
    }
}
