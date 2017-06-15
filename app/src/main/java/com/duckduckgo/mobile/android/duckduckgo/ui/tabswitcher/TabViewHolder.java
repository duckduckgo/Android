package com.duckduckgo.mobile.android.duckduckgo.ui.tabswitcher;

import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import com.duckduckgo.mobile.android.duckduckgo.R;
import com.duckduckgo.mobile.android.duckduckgo.ui.tab.TabEntity;

import butterknife.BindView;
import butterknife.ButterKnife;

/**
 * Created by fgei on 6/14/17.
 */

public class TabViewHolder extends RecyclerView.ViewHolder {

    interface OnTabListener {
        void onTabSelected(View v, int position);

        void onTabDeleted(View v, int position);
    }

    @BindView(R.id.tab_title_text_view)
    TextView titleTextView;

    @BindView(R.id.tab_url_text_view)
    TextView urlTextView;

    @BindView(R.id.tab_delete_image_button)
    ImageButton deleteImageButton;

    public TabViewHolder(View itemView, final OnTabListener onTabListener) {
        super(itemView);
        ButterKnife.bind(this, itemView);
        itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onTabListener.onTabSelected(v, getAdapterPosition());
            }
        });
        deleteImageButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onTabListener.onTabDeleted(v, getAdapterPosition());
            }
        });
    }

    public void setTab(TabEntity tabEntity) {
        titleTextView.setText(tabEntity.getTitle());
        urlTextView.setText(tabEntity.getCurrentUrl());
    }

    public static TabViewHolder inflate(ViewGroup parent, OnTabListener onTabListener) {
        return new TabViewHolder(
                LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.viewholder_tab, parent, false),
                onTabListener);
    }
}
