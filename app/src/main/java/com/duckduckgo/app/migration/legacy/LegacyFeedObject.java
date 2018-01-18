/*
 * Copyright (c) 2018 DuckDuckGo
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

package com.duckduckgo.app.migration.legacy;

import org.json.JSONException;
import org.json.JSONObject;

import android.database.sqlite.SQLiteCursor;


public class LegacyFeedObject {
	private final String feed;
	private final String favicon;
	private final String description;
	private final String timestamp;
	private final String url;
	private final String title;
	private final String id;
	private final String category;
	private final String imageUrl;
	private final String type;
	private final String articleUrl;
	private final String html;
	
	private final String hidden;

	/**
	 * No argument constructor, to be called by ORM layer
	 */
	public LegacyFeedObject() {
		// no-arg constructor for ORMlite
		this.feed = "";
		this.favicon = "";
		this.description = "";
		this.timestamp = "";
		this.url = "";
		this.title = "";
		this.id = "";
		this.category = "";
		this.imageUrl = "";
		this.type = "";
		this.articleUrl = "";
		this.html = "";
		this.hidden = "T";
	}
	
	public LegacyFeedObject(String id){
		this.id = id;
		this.feed = "";
		this.favicon = "";
		this.description = "";
		this.timestamp = "";
		this.url = "";
		this.title = "";
		this.category = "";
		this.imageUrl = "";
		this.type = "";
		this.articleUrl = "";
		this.html = "";
		this.hidden = "T";
	}
	
	public LegacyFeedObject(String id, String title, String description, String feed, String url, String imageUrl,
							String favicon, String timestamp, String category, String type, String articleUrl, String html, String hidden) {
		this.id = id;
		this.title = title;
		this.description = description;
		this.feed = feed;
		this.url = url;
		this.imageUrl = imageUrl;
		this.favicon = favicon;
		this.timestamp = timestamp;
		this.category = category;
		this.type = type;
		this.articleUrl = articleUrl;
		this.html = html;
		this.hidden = hidden;
	}
	
	public LegacyFeedObject(String title, String url) {
		this.id = url;
		this.title = title;
		this.description = "";
		this.feed = "";
		this.url = url;
		this.imageUrl = "";
		this.favicon = "";
		this.timestamp = "";
		this.category = "";
		this.type = "";
		this.articleUrl = "";
		this.html = "";
		this.hidden = "T";
	}
	
	public LegacyFeedObject(String title, String url, String imageUrl) {
		this.id = url;
		this.title = title;
		this.description = "";
		this.feed = "";
		this.url = url;
		if(imageUrl != null) {
			this.imageUrl = imageUrl;
		}
		else {
			this.imageUrl = "";
		}
		this.favicon = "";
		this.timestamp = "";
		this.category = "";
		this.type = "";
		this.articleUrl = "";
		this.html = "";
		this.hidden = "T";
	}
	
	public LegacyFeedObject(JSONObject obj) throws JSONException {
		this.feed = obj.getString("feed");
		this.favicon = obj.getString("favicon");
		this.description = obj.getString("description");
		this.timestamp = obj.getString("timestamp");
		this.url = obj.getString("url");
		this.title = obj.getString("title");
		this.id = obj.getString("id");
		this.category = obj.getString("category");
		this.imageUrl = obj.optString("image");
		this.type = obj.getString("type");
		if(obj.has("article_url"))
			this.articleUrl = obj.getString("article_url");
		else
			this.articleUrl = "";
		if(obj.has("html"))
			this.html = obj.getString("html");
		else
			this.html = "";
		this.hidden = "T";
	}
	
	public LegacyFeedObject(SQLiteCursor cursor) {
		this.id = cursor.getString(cursor.getColumnIndex("_id"));
		this.title = cursor.getString(cursor.getColumnIndex("title"));
		this.description = cursor.getString(cursor.getColumnIndex("description"));
		this.feed = cursor.getString(cursor.getColumnIndex("feed"));
		this.url = cursor.getString(cursor.getColumnIndex("url"));
		this.imageUrl = cursor.getString(cursor.getColumnIndex("imageurl"));
		this.favicon = cursor.getString(cursor.getColumnIndex("favicon"));
		this.timestamp = cursor.getString(cursor.getColumnIndex("timestamp"));
		this.category = cursor.getString(cursor.getColumnIndex("category"));
		this.type = cursor.getString(cursor.getColumnIndex("type"));
		this.articleUrl = cursor.getString(cursor.getColumnIndex("articleurl"));
//		this.html = cursor.getString(cursor.getColumnIndex("html"));
		this.html = "";
		this.hidden = cursor.getString(cursor.getColumnIndex("hidden"));;
	}
	
	@Override
	public String toString() {
		String string = "{";
		
		string = string.concat("feed:" + this.feed + "\n");
		string = string.concat("favicon:" + this.favicon + "\n");
		string = string.concat("description:" + this.description + "\n");
		string = string.concat("timestamp:" + this.timestamp + "\n");
		string = string.concat("url:" + this.url + "\n");
		string = string.concat("title:" + this.title + "\n");
		string = string.concat("id:" + this.id + "\n");
		string = string.concat("category:" + this.category + "\n");
		string = string.concat("image: " + this.imageUrl + "\n");
		string = string.concat("type: " + this.type + "\n");
		string = string.concat("article_url:" + this.articleUrl + "\n");
		string = string.concat("html:" + this.html + "\n");
		string = string.concat("hidden: " + this.hidden + "}");
		
		return string;
	}
	
	public String getFeed() {
		return feed;
	}
	
	public String getFavicon() {
		return favicon;
	}
	
	public String getTimestamp() {
		return timestamp;
	}
	
	public String getDescription() {
		return description;
	}
	
	public String getUrl() {
		return url;
	}
	
	public String getTitle() {
		return title;
	}
	
	public String getId() {
		return id;
	}
	
	public String getCategory() {
		return category;
	}
	
	public String getImageUrl() {
		return imageUrl;
	}
	
	public String getType() {
		return type;
	}
	
  	public String getHtml() {
  		return html;
  	}
  	
  	public String getArticleUrl() {
  		return articleUrl;
  	}
	
	public String getHidden() {
		return hidden;
	}

	public boolean hasPossibleReadability() {
		return getArticleUrl().length() != 0;
	}
}
