package com.duckduckgo.mobile.android.objects;

import org.json.JSONException;
import org.json.JSONObject;

import com.duckduckgo.mobile.android.util.SuggestType;

import android.content.Context;
import android.content.pm.PackageManager.NameNotFoundException;
import android.graphics.drawable.Drawable;

public class SuggestObject {
	private String phrase;
	private int score;
	private String snippet;
	private String imageUrl;
	private Drawable drawable;
	
	private SuggestType type; 
	
	public SuggestObject(JSONObject obj) throws JSONException {
		this.phrase = obj.getString("phrase");
		this.score = obj.optInt("score", 0); //Optional Field
		this.snippet = obj.optString("snippet", null); //Optional Field
		this.imageUrl = obj.optString("image", null); //Optional Field
		this.drawable = null;
		this.type = SuggestType.TEXT;
	}
	
	// used to suggest installed apps
	public SuggestObject(String phrase, String packageName, Context context) {
		this.phrase = phrase;
		this.snippet = packageName;
		this.type = SuggestType.APP;
		try {
			this.drawable = context.getPackageManager().getApplicationIcon(packageName);
		} catch (NameNotFoundException e) {
			this.drawable = null;
		}
	}
	
	public String getPhrase() {
		return phrase;
	}
	
	public int getScore() {
		return score;
	}
	
	public String getSnippet() {
		return snippet;
	}
	
	public String getImageUrl() {
		return imageUrl;
	}
	
	public Drawable getDrawable() {
		return drawable;
	}
	
	public SuggestType getType() {
		return type;
	}
	
	public boolean hasOnlyBangQuery(){
		// matches if the text starts with !, 
		// optionally directly followed by one or more repeats of an alphanumeric character
		return getPhrase().trim().matches("![a-zA-Z0-9]*");
	}
	
	@Override
	public String toString() {
		return this.phrase;
	}
}
