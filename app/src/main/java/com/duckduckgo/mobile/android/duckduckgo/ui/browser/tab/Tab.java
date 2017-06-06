package com.duckduckgo.mobile.android.duckduckgo.ui.browser.tab;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.UUID;

/**
 * Created by fgei on 5/30/17.
 */

public class Tab implements Parcelable {
    public String id;
    public String name = "";
    public String currentUrl = "";
    public int index;
    public boolean canGoBack = false;
    public boolean canGoForward = false;

    private Tab() {

    }

    public static Tab createNewTab() {
        Tab tab = new Tab();
        tab.id = UUID.randomUUID().toString();
        return tab;
    }

    @Override
    public String toString() {
        return "tab -> name: " + name + " currentUrl: " + currentUrl + " index: " + index + " id: " + id + " canGoBack: " + canGoBack + " canGoForward: " + canGoForward;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Tab tab = (Tab) o;

        if (index != tab.index) return false;
        if (canGoBack != tab.canGoBack) return false;
        if (canGoForward != tab.canGoForward) return false;
        if (id != null ? !id.equals(tab.id) : tab.id != null) return false;
        if (name != null ? !name.equals(tab.name) : tab.name != null) return false;
        return currentUrl != null ? currentUrl.equals(tab.currentUrl) : tab.currentUrl == null;

    }

    @Override
    public int hashCode() {
        int result = id != null ? id.hashCode() : 0;
        result = 31 * result + (name != null ? name.hashCode() : 0);
        result = 31 * result + (currentUrl != null ? currentUrl.hashCode() : 0);
        result = 31 * result + index;
        result = 31 * result + (canGoBack ? 1 : 0);
        result = 31 * result + (canGoForward ? 1 : 0);
        return result;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(this.id);
        dest.writeString(this.name);
        dest.writeString(this.currentUrl);
        dest.writeInt(this.index);
        dest.writeByte(this.canGoBack ? (byte) 1 : (byte) 0);
        dest.writeByte(this.canGoForward ? (byte) 1 : (byte) 0);
    }

    protected Tab(Parcel in) {
        this.id = in.readString();
        this.name = in.readString();
        this.currentUrl = in.readString();
        this.index = in.readInt();
        this.canGoBack = in.readByte() != 0;
        this.canGoForward = in.readByte() != 0;
    }

    public static final Creator<Tab> CREATOR = new Creator<Tab>() {
        @Override
        public Tab createFromParcel(Parcel source) {
            return new Tab(source);
        }

        @Override
        public Tab[] newArray(int size) {
            return new Tab[size];
        }
    };
}
