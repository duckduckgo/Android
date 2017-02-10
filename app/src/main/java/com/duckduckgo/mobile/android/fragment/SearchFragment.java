package com.duckduckgo.mobile.android.fragment;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v7.view.menu.MenuBuilder;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListView;

import com.duckduckgo.mobile.android.R;
import com.duckduckgo.mobile.android.bus.BusProvider;
import com.duckduckgo.mobile.android.events.AutoCompleteResultClickEvent;
import com.duckduckgo.mobile.android.events.OverflowButtonClickEvent;
import com.duckduckgo.mobile.android.events.ShowAutoCompleteResultsEvent;
import com.duckduckgo.mobile.android.util.DDGControlVar;
import com.duckduckgo.mobile.android.views.DDGOverflowMenu;
import com.squareup.otto.Subscribe;

public class SearchFragment extends Fragment implements AdapterView.OnItemClickListener, AdapterView.OnItemLongClickListener {

    //public static final String TAG = "search_fragment";
    //public static final String TAG_HOME_PAGE = "search_fragment_home_page";

    private ListView autoCompleteResultListView;

    private View fragmentView = null;

    private Menu searchMenu = null;
    private DDGOverflowMenu overflowMenu = null;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        BusProvider.getInstance().register(this);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        BusProvider.getInstance().unregister(this);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        fragmentView = inflater.inflate(R.layout.fragment_search, container, false);
        return fragmentView;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        autoCompleteResultListView = (ListView) fragmentView.findViewById(R.id.autocomplete_list);
        autoCompleteResultListView.setDivider(null);

        autoCompleteResultListView.setOnItemClickListener(this);
        autoCompleteResultListView.setOnItemLongClickListener(this);
        autoCompleteResultListView.setVisibility(View.GONE);

        searchMenu = new MenuBuilder(getActivity());
        getActivity().getMenuInflater().inflate(R.menu.main, searchMenu);

    }

    @Override
    public void onResume() {
        super.onResume();
        if(DDGControlVar.isAutocompleteActive) {
            autoCompleteResultListView.setAdapter(DDGControlVar.mDuckDuckGoContainer.acAdapter);
        }
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if(!hidden) {
            showAutoCompleteResults(false);

            if(DDGControlVar.isAutocompleteActive) {
                autoCompleteResultListView.setAdapter(DDGControlVar.mDuckDuckGoContainer.acAdapter);
                DDGControlVar.mDuckDuckGoContainer.acAdapter.notifyDataSetChanged();
            }
        } else {
            if(overflowMenu!=null && overflowMenu.isShowing()) {
                overflowMenu.dismiss();
            }
        }
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.main, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        if(DDGControlVar.isAutocompleteActive) {
            BusProvider.getInstance().post(new AutoCompleteResultClickEvent(position));
        }
    }

    @Override
    public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {

        return false;
    }

    @Subscribe
    public void onShowAutoCompleteResultsEvent(ShowAutoCompleteResultsEvent event) {
        if(getActivity()!=null) {
            showAutoCompleteResults(event.isVisible);
        }

    }

    public void showAutoCompleteResults(boolean visible) {
        autoCompleteResultListView.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    @Subscribe
    public void onOverflowButtonClickEvent(OverflowButtonClickEvent event) {
        if(DDGControlVar.mDuckDuckGoContainer.currentFragmentTag.equals(getTag()) && searchMenu!=null) {
            if(overflowMenu!=null && overflowMenu.isShowing()) {
                return;
            }

            overflowMenu = new DDGOverflowMenu(getActivity());
            overflowMenu.setMenu(searchMenu);
            overflowMenu.show(event.anchor);
        }
    }


}
