package com.duckduckgo.mobile.android.util;

import com.duckduckgo.mobile.android.events.Event;


public class Item {
    public final String text;
    //public final int icon;
    public final ItemType type;

    /**
     * Constructor used by subclasses.
     *
     * @param text Text to set on the menu item
     * @param icon unused
     * @param type ItemType, enum value like SAVE, UNSAVE, SHARE, EXTERNAL
     */
    public Item(String text, Integer icon, ItemType type) {
        this.text = text;
        //this.icon = icon;
        this.type = type;
    }
    @Override
    public String toString() {
        return text;
    }

    public static enum ItemType {
        SAVE, UNSAVE, SHARE, EXTERNAL
    }

    /**
     * Event to fire when the menu item is chosen. The particular event is to be set by subclasses.
     */
    public Event EventToFire;
}