package com.duckduckgo.mobile.android.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.os.Parcelable;

import com.duckduckgo.mobile.android.R;

/*
 * A class that, well, shares stuff :)
 */
public class Sharer {

	/**
	 * Initializes an intent for share web page action name and sends it to the context parameter to start of an activity
	 *
	 * @param context
	 * @param title
	 * @param url
     */
	public static void shareWebPage(Context context, String title, String url) {
		String actionName = (String) context.getResources().getText(R.string.SharePage);
		Intent shareIntent = createTargetedShareIntent(context, url, title, actionName);
		context.startActivity(shareIntent);
	}

	/**
	 * Initializes an intent for share story action name and sends it to the context parameter to start of an activity
	 *
	 * @param context
	 * @param title
	 * @param url
     */
	public static void shareStory(Context context, String title, String url) {
		String actionName = (String) context.getResources().getText(R.string.ShareStory);
		Intent shareIntent = createTargetedShareIntent(context, String.format("%s %s", title, url), title, actionName);
		context.startActivity(shareIntent);
	}

	/**
	 * Initializes an intent for share search action name and sends it to the context parameter to start of an activity
	 *
	 * @param context
	 * @param query
     */
	public static void shareSearch(Context context, String query) {
		String actionName = (String) context.getResources().getText(R.string.ShareSearch);
		String url = "https://duckduckgo.com/?q=" + query;
		Intent shareIntent = createTargetedShareIntent(context, String.format("%s %s", query, url),
				String.format("DuckDuckGo Search for \"%s\"", query), actionName);
		context.startActivity(shareIntent);
	}
	
	private static Intent createTargetedShareIntent(Context context, String text, String subject, String actionName) {
		List<Intent> targetedShareIntents = new ArrayList<Intent>();
		Intent shareIntent = createBasicShareIntent(context, text, subject);
		List<HashMap<String, String>> intentMetaInfo = new ArrayList<HashMap<String, String>>();
        List<ResolveInfo> resInfo = context.getPackageManager().queryIntentActivities(shareIntent, 0);
        
        if (!resInfo.isEmpty()){
            for (ResolveInfo resolveInfo : resInfo) {
            	if (resolveInfo.activityInfo != null) {
            		HashMap<String, String> info = new HashMap<String, String>();
            		info.put("packageName", resolveInfo.activityInfo.packageName);
            		info.put("className", resolveInfo.activityInfo.name);
            		info.put("simpleName", String.valueOf(resolveInfo.activityInfo.loadLabel(context.getPackageManager())));
            		intentMetaInfo.add(info);
        		}
            }
        		 
    		if (!intentMetaInfo.isEmpty()) {
    			Collections.sort(intentMetaInfo, new Comparator<HashMap<String, String>>() {
    				@Override
    				public int compare(HashMap<String, String> map, HashMap<String, String> map2) {
    					return map.get("simpleName").compareTo(map2.get("simpleName"));
    				}
				});
    				 
				for (HashMap<String, String> metaInfo : intentMetaInfo) {
					String packageName = metaInfo.get("packageName");
	                Intent targetedShareIntent = (Intent) shareIntent.clone();
	                
	                if (packageName.contains("twitter")) {
	                    targetedShareIntent.putExtra(Intent.EXTRA_TEXT, 
	                    		String.format(context.getResources().getString(R.string.TwitterShareFormat), text));
	                }
	                
    				targetedShareIntent.setPackage(packageName);
    				targetedShareIntent.setClassName(metaInfo.get("packageName"), metaInfo.get("className"));
    				targetedShareIntents.add(targetedShareIntent);
				}
            }
    		
            Intent chooserIntent = Intent.createChooser(targetedShareIntents.remove(targetedShareIntents.size()-1), actionName);
            chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, targetedShareIntents.toArray(new Parcelable[]{}));
            return chooserIntent;
        }
        
        return shareIntent;
	}
	
	private static Intent createBasicShareIntent(Context context, String text, String subject) {
		Intent shareIntent = new Intent();
		shareIntent.setAction(Intent.ACTION_SEND);
		shareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		shareIntent.setType("text/plain");
		shareIntent.putExtra(Intent.EXTRA_TEXT, String.format(context.getResources().getString(R.string.RegularShareFormat), text));
		shareIntent.putExtra(Intent.EXTRA_SUBJECT, subject);
		return shareIntent;
	}
}
