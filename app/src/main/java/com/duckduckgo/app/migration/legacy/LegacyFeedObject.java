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
