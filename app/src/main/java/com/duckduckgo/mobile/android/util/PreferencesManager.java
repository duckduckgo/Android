package com.duckduckgo.mobile.android.util;

import java.util.HashSet;
import java.util.Set;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.res.Resources.Theme;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;

import com.duckduckgo.mobile.android.DDGApplication;
import com.duckduckgo.mobile.android.R;
import com.duckduckgo.mobile.android.views.webview.DDGWebView;

public class PreferencesManager {
	
	/* Settings */
	
	/**
	 * Returns active SCREEN by considering the Duck Mode checkbox
	 * @return active SCREEN
	 */
	public static SCREEN getActiveStartScreen() {
        String startScreenCode = DDGApplication.getSharedPreferences().getString("startScreenPref", "0");
        return SCREEN.getByCode(Integer.valueOf(startScreenCode));
	}

    public static String getRegion() {
		return DDGApplication.getSharedPreferences().getString("regionPref", "wt-wt");
	}
	
	public static boolean getEnableTor(){
		return DDGApplication.getSharedPreferences().getBoolean("enableTor", false);
	}

    public static int getUseExternalBrowser() {
        int useExternalBrowser = Integer.valueOf(DDGApplication.getSharedPreferences().getString("useExternalBrowserPref", "0"));
        return useExternalBrowser<=1 ? useExternalBrowser : 0;
    }
	
	public static boolean getAutocomplete() {
		return DDGApplication.getSharedPreferences().getBoolean("autocompletePref", true);
	}
	
	public static boolean getRecordHistory() {
		return DDGApplication.getSharedPreferences().getBoolean("recordHistoryPref", true);
	}

    public static boolean getRecordCookies() {
        return DDGApplication.getSharedPreferences().getBoolean("recordCookiesPref", true);
    }

    public static boolean getEnableJavascript() {
        return DDGApplication.getSharedPreferences().getBoolean("enableJavascriptPref", true);
    }
	
	public static boolean getDirectQuery() {
		return DDGApplication.getSharedPreferences().getBoolean("directQueryPref", true);
	}

	public static int getAppVersionCode() {
		return DDGApplication.getSharedPreferences().getInt("appVersionCode", 0);
	}
	
	public static void saveAppVersionCode(int appVersionCode) {
		Editor editor = DDGApplication.getSharedPreferences().edit();
		editor.putInt("appVersionCode", appVersionCode);
		editor.commit();
	}
	
	public static void clearValues() {
		Editor editor = DDGApplication.getSharedPreferences().edit();
		editor.remove("mainFontSize");
		editor.remove("recentFontSize");
		editor.remove("webViewFontSize");
		editor.remove("ptrHeaderTextSize");
		editor.remove("ptrHeaderSubTextSize");
		editor.remove("leftTitleTextSize");
        //editor.remove("useExternalBrowserPref");
		editor.commit();
	}
	
	/* Events */
    public static void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
    	if(key.equals("startScreenPref")){
    		DDGControlVar.START_SCREEN = getActiveStartScreen();
        }
    	else if(key.equals("regionPref")){
            DDGControlVar.regionString = sharedPreferences.getString(key, "wt-wt");
        }
        else if(key.equals("useExternalBrowserPref")){
            DDGControlVar.useExternalBrowser = Integer.valueOf(sharedPreferences.getString(key, "0"));
        }
        else if(key.equals("autocompletePref")){
            DDGControlVar.isAutocompleteActive = sharedPreferences.getBoolean(key, true);
        }
        else if(key.equals("recordCookiesPref")) {
            DDGWebView.recordCookies(sharedPreferences.getBoolean(key, true));
        }
    }
}