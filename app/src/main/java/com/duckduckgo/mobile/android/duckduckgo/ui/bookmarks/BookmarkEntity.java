package com.duckduckgo.mobile.android.duckduckgo.ui.bookmarks;

import android.os.Parcel;
import android.os.Parcelable;

import com.duckduckgo.mobile.android.duckduckgo.domain.bookmark.Bookmark;

import java.util.UUID;

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

public class BookmarkEntity implements Bookmark, Parcelable {
    private String id;
    private String name;
    private String url;
    private int index;

    public BookmarkEntity() {
    }

    public BookmarkEntity(Bookmark bookmark) {
        id = bookmark.getId();
        name = bookmark.getName();
        url = bookmark.getUrl();
        index = bookmark.getIndex();
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    @Override
    public int getIndex() {
        return index;
    }

    public void setIndex(int index) {
        this.index = index;
    }

    public static BookmarkEntity create() {
        BookmarkEntity bookmarkEntity = new BookmarkEntity();
        bookmarkEntity.id = UUID.randomUUID().toString();
        return bookmarkEntity;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(this.id);
        dest.writeString(this.name);
        dest.writeString(this.url);
        dest.writeInt(this.index);
    }

    protected BookmarkEntity(Parcel in) {
        this.id = in.readString();
        this.name = in.readString();
        this.url = in.readString();
        this.index = in.readInt();
    }

    public static final Creator<BookmarkEntity> CREATOR = new Creator<BookmarkEntity>() {
        @Override
        public BookmarkEntity createFromParcel(Parcel source) {
            return new BookmarkEntity(source);
        }

        @Override
        public BookmarkEntity[] newArray(int size) {
            return new BookmarkEntity[size];
        }
    };
}
