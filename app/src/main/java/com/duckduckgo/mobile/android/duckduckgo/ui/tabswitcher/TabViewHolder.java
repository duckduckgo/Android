package com.duckduckgo.mobile.android.duckduckgo.ui.tabswitcher;

import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.duckduckgo.mobile.android.duckduckgo.R;

import butterknife.BindView;
import butterknife.ButterKnife;

/**
 * Created by fgei on 5/29/17.
 */

public class TabViewHolder extends RecyclerView.ViewHolder {

    @BindView(R.id.tab_title_text_view)
    TextView tabTitleTextView;

    public TabViewHolder(View itemView) {
        super(itemView);
        ButterKnife.bind(this, itemView);
    }

    public void setTab(String title) {
        tabTitleTextView.setText(title);
    }

    public void setOnClickListener(View.OnClickListener onClickListener) {
        itemView.setOnClickListener(onClickListener);
    }

    public static TabViewHolder inflate(ViewGroup parent) {
        return new TabViewHolder(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.viewholder_tab, parent, false));
    }
}
