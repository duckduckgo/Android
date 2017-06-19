package com.duckduckgo.mobile.android.duckduckgo;

import android.app.Application;

import timber.log.Timber;

/**
 * Created by fgei on 5/15/17.
 */

public class App extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        Injector.init(this);

        if (BuildConfig.DEBUG) {
            Timber.plant(new Timber.DebugTree());
        }
    }
}
