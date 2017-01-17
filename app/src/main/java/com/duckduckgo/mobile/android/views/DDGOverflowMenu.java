package com.duckduckgo.mobile.android.views;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Rect;
import android.os.Build;
import android.transition.Fade;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Adapter;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupWindow;
import android.widget.TextView;

import com.duckduckgo.mobile.android.R;
import com.duckduckgo.mobile.android.activity.DuckDuckGo;
import com.duckduckgo.mobile.android.bus.BusProvider;
import com.duckduckgo.mobile.android.events.WebViewEvents.WebViewUpdateMenuNavigationEvent;
import com.duckduckgo.mobile.android.events.WebViewEvents.WebViewItemMenuClickEvent;
import com.squareup.otto.Subscribe;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class DDGOverflowMenu extends PopupWindow implements View.OnClickListener, AdapterView.OnItemClickListener, AdapterView.OnItemSelectedListener {

    private Context context;

    private ListView menuListView = null;
    private DDGOverflowAdapter overflowAdapter;

    private LinearLayout header = null;
    private HashMap<Integer, MenuItem> headerItems = null;
    private boolean isBusRegistered = false;

    public DDGOverflowMenu(Context context) {
        super(context, null, R.attr.popUp);
        this.context = context;
        init();
    }

    public DDGOverflowMenu(Context context, AttributeSet attrs) {
        super(context, attrs, R.attr.popUp);
        this.context = context;
        init();
    }

    public void init() {
        setFocusable(true);
        setOutsideTouchable(true);
        LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View container = inflater.inflate(R.layout.overflow_menu, null);
        setContentView(container);
        setHeight(WindowManager.LayoutParams.WRAP_CONTENT);
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            setTransitions();
        }

        menuListView = (ListView) container.findViewById(R.id.menu_listview);
        menuListView.setOnItemClickListener(this);
        menuListView.setOnItemSelectedListener(this);

        header = (LinearLayout) container.findViewById(R.id.header_container);
    }

    public void registerBus() {
        isBusRegistered = true;
        BusProvider.getInstance().register(this);
    }

    public void unregisterBus() {
        if(isBusRegistered) {
            isBusRegistered = false;
            BusProvider.getInstance().unregister(this);
        }
    }

    public void setMenu(Menu menu) {
        overflowAdapter = new DDGOverflowAdapter(context, R.layout.item_overflow_menu);
        menuListView.setAdapter(overflowAdapter);
        setMenu(menu, false);
    }

    public void setMenu(Menu menu, boolean newSection) {
        menuListView.setVisibility(View.VISIBLE);

        List<MenuItem> newMenuItems = new ArrayList<MenuItem>();

        for(int i=0; i<menu.size(); i++) {
            if(menu.getItem(i).isVisible()) {
                newMenuItems.add(menu.getItem(i));
            }
        }
        overflowAdapter.addItems(newMenuItems, newSection);
        overflowAdapter.notifyDataSetChanged();

    }

    public void setHeaderMenu(Menu menu) {
        registerBus();

        header.setVisibility(View.VISIBLE);
        headerItems = new HashMap<Integer, MenuItem>();
        LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        for(int i=0; i<menu.size(); i++) {
            ImageButton imageButton = (ImageButton) inflater.inflate(R.layout.web_navigation_button, header, false);
            final String title = ""+menu.getItem(i).getTitle();
            final int actionId = menu.getItem(i).getItemId();
            imageButton.setId(actionId);
            imageButton.setEnabled(menu.getItem(i).isEnabled());
            imageButton.setImageDrawable(menu.getItem(i).getIcon());
            if(menu.getItem(i).getIcon()==null) {
                imageButton.setEnabled(false);
            } else {
                imageButton.setOnClickListener(this);
            }

            headerItems.put(imageButton.getId(), menu.getItem(i));
            header.addView(imageButton);
        }

    }

    public void show(View anchor) {
        show(anchor, true, true);
    }

    private void show(View anchor, boolean withMarginOnAnchor, boolean coverAnchor) {
        DisplayMetrics displayMetrics = new DisplayMetrics();
        ((DuckDuckGo)context).getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);

        setWidth(getMaxWidth(context, overflowAdapter));

        int itemCount = overflowAdapter.getItemCount();
        int height = ((int) context.getResources().getDimension(R.dimen.listview_item_height)) * (overflowAdapter.getItemCount());
        int divider = (int) context.getResources().getDimension(R.dimen.simple_divider_height);
        height += divider;

        Rect rect = new Rect();
        anchor.getGlobalVisibleRect(rect);

        boolean reverseMenu = false;
        if(displayMetrics.heightPixels>height) {
            menuListView.getLayoutParams().height = height;
            if((displayMetrics.heightPixels-rect.top)<=height) {
                reverseMenu = true;
            }
        } else {
            setHeight(WindowManager.LayoutParams.MATCH_PARENT);
            menuListView.getLayoutParams().height = ViewGroup.LayoutParams.MATCH_PARENT;
        }

        int xOffset = 0;
        int yOffset = 0;

        if(coverAnchor) {
            xOffset = anchor.getMeasuredWidth() - getWidth();
            yOffset = reverseMenu ? height : anchor.getMeasuredHeight();

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                if (withMarginOnAnchor) {
                    int screenMargin = (int) context.getResources().getDimension(R.dimen.menu_outer_margin);
                    xOffset -= screenMargin;
                    yOffset -= screenMargin;
                }
            }
        }

        if(coverAnchor) {
            showAsDropDown(anchor, xOffset, yOffset * -1);
        }
    }

    @Override
    public void dismiss() {
        unregisterBus();
        super.dismiss();
    }

    @Override
    public void onClick(View view) {
        BusProvider.getInstance().post(new WebViewItemMenuClickEvent(headerItems.get(view.getId())));
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        BusProvider.getInstance().post(new WebViewItemMenuClickEvent(overflowAdapter.getMenuItem(position)));
        dismiss();
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        dismiss();
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {
        dismiss();
    }

    @TargetApi(Build.VERSION_CODES.M)
    private void setTransitions() {
        Fade fadeIn = new Fade(Fade.IN);
        fadeIn.setDuration(100);
        Fade fadeOut = new Fade(Fade.OUT);
        fadeOut.setDuration(100);
        setEnterTransition(fadeIn);
        setExitTransition(fadeOut);
    }

    public static int getMaxWidth(Context context, DDGOverflowAdapter adapter) {
        int maxLength = 0;
        for(int i=0; i<adapter.getCount(); i++) {
            int newLength = adapter.getMenuItemTitle(i).length();
            maxLength = newLength>maxLength ? newLength : maxLength;
        }
        int width = (int) context.getResources().getDimension(R.dimen.menu_letterspace) * (maxLength+2);
        int menuPadding = (int) context.getResources().getDimension(R.dimen.menu_padding) * 2;
        return width + menuPadding;

    }

    @Subscribe
    public void onWebViewDisableMenuNavigationButtonEvent(WebViewUpdateMenuNavigationEvent event) {
        for(HashMap.Entry<Integer, Boolean> entry : event.newStates.entrySet()) {
            ImageButton imageButton = (ImageButton) header.findViewById(entry.getKey());
            if(imageButton!=null) {
                imageButton.setEnabled(entry.getValue() );
            }
        }
    }

    public class DDGOverflowAdapter extends ArrayAdapter<MenuItem> {

        private static final int TYPE_ITEM = 0;
        private static final int TYPE_DIVIDER = 1;

        private Context context;
        private int layoutResId;
        private List<Item> items;

        public DDGOverflowAdapter(Context context, int layoutResId) {
            super(context, layoutResId);
            this.context = context;
            this.layoutResId = layoutResId;
            items = new ArrayList<Item>();
        }

        public void addItems(List<MenuItem> menuItems, boolean newSection) {
            if(newSection) {
                items.add(new Item(true));
            }
            for(MenuItem menuItem : menuItems) {
                items.add(new Item(menuItem));
            }
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View root = convertView;
            Holder holder = null;
            int itemType = getItemViewType(position);
            LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

            if(root==null) {
                if(itemType==TYPE_ITEM) {
                    root = inflater.inflate(layoutResId, parent, false);
                    holder = new Holder();
                    holder.text = (TextView) root.findViewById(R.id.text1);
                    root.setTag(holder);
                } else {
                    root = inflater.inflate(R.layout.overflowmenu_divier, parent, false);
                }
            } else {
                if(itemType==TYPE_ITEM) {
                    holder = (Holder) root.getTag();
                }
            }

            if(itemType==TYPE_ITEM) {
                MenuItem item = items.get(position).item;
                holder.text.setText(item.getTitle());
                holder.text.setEnabled(item.isEnabled());
            }

            return root;
        }

        public int getItemCount() {
            int out = 0;
            for(Item item : items) {
                if(!item.isDivider) {
                    out++;
                }
            }
            return out;
        }

        @Override
        public int getCount() {
            return items.size();
        }

        @Override
        public int getItemViewType(int position) {
            Item item = items.get(position);
            if(item.isDivider) {
                return TYPE_DIVIDER;
            }
            return TYPE_ITEM;
        }

        @Override
        public boolean isEnabled(int position) {
            return !items.get(position).isDivider;
        }

        public MenuItem getMenuItem(int position) {
            Item item = items.get(position);
            if(item.isDivider) {
                return null;
            }
            return item.item;
        }

        public String getMenuItemTitle(int position) {
            Item item = items.get(position);
            if(item.isDivider) {
                return "";
            }
            return item.item.getTitle().toString();
        }

        class Holder {
            TextView text;
        }

        class Item {
            public boolean isDivider = false;
            public MenuItem item;

            public Item(boolean isDivider) {
                this.isDivider = isDivider;
            }

            public Item(MenuItem item) {
                this.item = item;
            }
        }
    }
}
