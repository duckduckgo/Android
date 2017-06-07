package com.duckduckgo.mobile.android.duckduckgo.ui.tabswitcher;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import com.duckduckgo.mobile.android.duckduckgo.R;
import com.duckduckgo.mobile.android.duckduckgo.ui.browser.tab.Tab;

import butterknife.BindView;
import butterknife.ButterKnife;

/**
 * Created by fgei on 5/29/17.
 */

public class TabViewHolder extends RecyclerView.ViewHolder {

    private static final String EMPTY_TITLE = "DuckDuckGo Home";
    private static final String EMPTY_SUBTITLE = "duckduckgo.com";

    @BindView(R.id.tab_title_text_view)
    TextView titleTabTextView;

    @BindView(R.id.tab_subtitle_text_view)
    TextView subtitleTextView;

    @BindView(R.id.tab_remove_image_button)
    ImageButton removeTabImageButton;

    public TabViewHolder(View itemView) {
        super(itemView);
        ButterKnife.bind(this, itemView);
    }

    public void setTab(@NonNull Tab tab) {
        titleTabTextView.setText(getTitle(tab.name));
        subtitleTextView.setText(getSubtitle(tab.currentUrl));
    }

    public void setOnClickListener(View.OnClickListener onClickListener) {
        itemView.setOnClickListener(onClickListener);
    }

    public void setOnRemoveClickListener(View.OnClickListener onRemoveClickListener) {
        removeTabImageButton.setOnClickListener(onRemoveClickListener);
    }

    private String getTitle(@Nullable String title) {
        if (title == null || title.isEmpty()) {
            return EMPTY_TITLE;
        }
        return title;
    }

    private String getSubtitle(@Nullable String subtitle) {
        if (subtitle == null || subtitle.isEmpty()) {
            return EMPTY_SUBTITLE;
        }
        return subtitle;
    }

    public static TabViewHolder inflate(ViewGroup parent) {
        return new TabViewHolder(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.viewholder_tab, parent, false));
    }
}
