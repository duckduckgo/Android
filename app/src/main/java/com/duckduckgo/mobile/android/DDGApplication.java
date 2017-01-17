package com.duckduckgo.mobile.android;

import android.app.Application;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDatabase.CursorFactory;
import android.preference.PreferenceManager;
import android.util.Log;
import android.webkit.CookieSyncManager;

import com.duckduckgo.mobile.android.download.FileCache;
import com.duckduckgo.mobile.android.download.ImageCache;
import com.duckduckgo.mobile.android.network.DDGNetworkConstants;
import com.duckduckgo.mobile.android.util.DDGConstants;
import com.duckduckgo.mobile.android.util.DDGControlVar;
import com.duckduckgo.mobile.android.util.PreferencesManager;
import org.acra.ACRA;
import org.acra.ReportField;
import org.acra.ReportingInteractionMode;
import org.acra.annotation.ReportsCrashes;

import java.io.File;

@ReportsCrashes(formKey="",
formUri = "https://collect.duckduckgo.com/collect.js?type=crash",
mode = ReportingInteractionMode.DIALOG,
customReportContent = {
        ReportField.APP_VERSION_CODE, ReportField.APP_VERSION_NAME, ReportField.ANDROID_VERSION, ReportField.STACK_TRACE,
        ReportField.AVAILABLE_MEM_SIZE, ReportField.USER_COMMENT, ReportField.LOGCAT,
        ReportField.PRODUCT, ReportField.PHONE_MODEL
},
resToastText = R.string.crash_toast_text, // optional, displayed as soon as the crash occurs, before collecting data which can take a few seconds
resNotifTickerText = R.string.crash_notif_ticker_text,
resNotifTitle = R.string.crash_notif_title,
resNotifText = R.string.crash_notif_text,
resDialogText = R.string.crash_dialog_text,
resDialogCommentPrompt = R.string.crash_dialog_comment_prompt, // optional. when defined, adds a user text field input with this text resource as a label
resDialogOkToast = R.string.crash_dialog_ok_toast // optional. displays a Toast message when the user accepts to send a report.
)
public class DDGApplication extends Application {

	private static final ImageCache imageCache = new ImageCache(null);
	private static FileCache fileCache = null;
	private static SharedPreferences sharedPreferences = null;

	private static String DB_FOLDER_NAME = "database";
	
	/**
	 * Changes after application upgrade
	 * Also records current app version code to "appVersionCode" sharedPreferences 
	 * 
	 * @param appVersionCode
	 */
	private void onUpgrade(int appVersionCode) {
		// clear old sharedPreferences values, types can conflict (int -> float)
		PreferencesManager.clearValues();
		//PreferencesManager.migrateAllowedSources();
		PreferencesManager.saveAppVersionCode(appVersionCode);
		fileCache.removeThrashOnMigration();
	}
	
	@Override
	public void onCreate() {
		super.onCreate();
        ACRA.init(this);
		sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
		fileCache = new FileCache(this.getApplicationContext());
		imageCache.setFileCache(fileCache);
        CookieSyncManager.createInstance(this);

		try {
			PackageInfo pInfo;
			pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
			String appVersion = pInfo.versionName;
			int appVersionCode = pInfo.versionCode;
			int oldVersionCode = PreferencesManager.getAppVersionCode();

			Log.v("APP", "oldversion: " + oldVersionCode + " new: " + appVersionCode);
			if(oldVersionCode == 0 || oldVersionCode != appVersionCode) {				
				// upgrade
				onUpgrade(appVersionCode);
			}
			
			DDGConstants.USER_AGENT = DDGConstants.USER_AGENT.replace("%version", appVersion);
		} catch (NameNotFoundException e) {
			// at least specify new Android version
			DDGConstants.USER_AGENT = DDGConstants.USER_AGENT.replace("%version", "2+");
		}
		DDGNetworkConstants.initialize(this);


		DDGControlVar.START_SCREEN = PreferencesManager.getActiveStartScreen();
		DDGControlVar.regionString = PreferencesManager.getRegion();
        DDGControlVar.useExternalBrowser = PreferencesManager.getUseExternalBrowser();

	}


    public static ImageCache getImageCache() {
		return imageCache;
	}
	
	public static SharedPreferences getSharedPreferences() {
		return sharedPreferences;
	}

	public static FileCache getFileCache() {
		return fileCache;
	}
	
	// method overridden to put DB in database folder cleanable upon uninstall
	@Override
	public SQLiteDatabase openOrCreateDatabase(String name, int mode,
	CursorFactory factory) {
	    File externalFilesDir = getDir(DB_FOLDER_NAME, MODE_PRIVATE);
	    File dbFile = new File(externalFilesDir, name);
	    return SQLiteDatabase.openDatabase(dbFile.getAbsolutePath(), null, SQLiteDatabase.CREATE_IF_NECESSARY);
	}
	
	@Override
	public void onLowMemory() {
		DDGApplication.getImageCache().purge();
		super.onLowMemory();
	}
}
