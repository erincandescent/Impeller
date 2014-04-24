package eu.e43.impeller.uikit;

import android.content.Context;
import android.util.AttributeSet;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.support.v7.widget.PopupMenu;
import android.widget.ToggleButton;

import java.util.ArrayList;
import java.util.List;

import eu.e43.impeller.R;

/**
 * TODO: document your custom view class.
 */
public class ToolbarView extends LinearLayout {
    private Menu                m_menu;
    private PopupMenu           m_popupMenu;
    private PopupMenu           m_overflowMenu;
    private ImageButton         m_overflowButton;
    private List<View>          m_buttons = new ArrayList<View>();
    private OnClickListener     m_clickListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            buttonClicked(v);
        }
    };
    private PopupMenu.OnMenuItemClickListener m_itemClickListener = null;

    public ToolbarView(Context context) {
        super(context);
        initialize();
    }

    public ToolbarView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initialize();
    }

    private void initialize() {
        m_overflowButton = new ImageButton(getContext());
        m_overflowButton.setBackgroundResource(android.R.color.transparent);
        m_overflowButton.setImageResource(
                android.support.v7.appcompat.R.drawable.abc_ic_menu_moreoverflow_normal_holo_light);
        m_overflowButton.setOnClickListener(m_clickListener);
        m_popupMenu    = new PopupMenu(getContext(), this);
        m_overflowMenu = new PopupMenu(getContext(), m_overflowButton);
        m_overflowMenu.setOnMenuItemClickListener(m_internalOnClickListener);
    }

    public void inflate(int menuId) {
        m_popupMenu.inflate(menuId);
        m_menu = m_popupMenu.getMenu();
        onMenuUpdated();
    }

    public void onMenuUpdated() {
        Menu mnu = m_popupMenu.getMenu();

        m_overflowMenu.getMenu().clear();
        List<MenuItem> items = new ArrayList<MenuItem>();
        for(int i = 0; i < mnu.size(); i++) {
            MenuItem item = mnu.getItem(i);
            if(item.isVisible()) {
                items.add(item);
            } else {
                copyMenuItem(m_overflowMenu.getMenu(), item);
            }
        }

        int maxIcons = getContext().getResources().getInteger(R.integer.toolbar_max_icons);
        removeAllViews();
        m_buttons.clear();

        boolean mustShowOverflow = false;

        if(items.size() > maxIcons) {
            maxIcons--;
            mustShowOverflow = true;
        }

        int iconSize = (int) getResources().getDimension(android.R.dimen.app_icon_size);

        int i = 0;
        for(; i < Math.min(items.size(), maxIcons); i++) {
            MenuItem itm = items.get(i);
            if(itm.getIcon() == null) {
                if(!mustShowOverflow) {
                    mustShowOverflow = true;
                    maxIcons--;
                }
                copyMenuItem(m_overflowMenu.getMenu(), items.get(i));
                continue;
            }

            if(itm.isCheckable()) {
                ToggleButton btn = new ToggleButton(getContext());

                btn.setBackgroundResource(android.R.color.transparent);
                btn.setButtonDrawable(itm.getIcon());

                btn.setTag(itm);
                btn.setChecked(itm.isChecked());
                btn.setOnClickListener(m_clickListener);

                btn.setTextOn("");
                btn.setTextOff("");

                m_buttons.add(btn);
                addView(btn, new LayoutParams(iconSize, iconSize));

            } else {
                ImageButton btn = new ImageButton(getContext());

                btn.setBackgroundResource(android.R.color.transparent);
                btn.setImageDrawable(itm.getIcon());

                btn.setTag(itm);
                btn.setOnClickListener(m_clickListener);

                m_buttons.add(btn);
                addView(btn, new LayoutParams(iconSize, iconSize));
            }
        }

        for(; i < items.size(); i++) {
            copyMenuItem(m_overflowMenu.getMenu(), items.get(i));
        }

        if(mustShowOverflow) {
            this.addView(m_overflowButton);
        }
    }

    private void copyMenuItem(Menu dest, MenuItem itm) {
        if(itm.hasSubMenu()) {
            SubMenu nestDest = dest.addSubMenu(itm.getGroupId(), itm.getItemId(), itm.getOrder(), itm.getTitle());
            fillMenuFromSubMenu(nestDest, itm.getSubMenu());
        } else {
            dest.add(itm.getGroupId(), itm.getItemId(), itm.getOrder(), itm.getTitle());
        }
    }

    private void fillMenuFromSubMenu(Menu dest, SubMenu src) {
        for(int i = 0; i < src.size(); i++) {
            copyMenuItem(dest, src.getItem(i));
        }
    }

    public void setOnItemClickListener(PopupMenu.OnMenuItemClickListener listener) {
        m_itemClickListener = listener;
        m_popupMenu.setOnMenuItemClickListener(listener);
    }


    private void buttonClicked(View v) {
        if(v == m_overflowButton) {
            m_overflowMenu.show();
        } else {
            MenuItem itm = (MenuItem) v.getTag();

            if(itm.hasSubMenu()) {
                PopupMenu btnMenu = new PopupMenu(getContext(), v);
                fillMenuFromSubMenu(btnMenu.getMenu(), itm.getSubMenu());
                btnMenu.setOnMenuItemClickListener(m_internalOnClickListener);
                btnMenu.show();
            } else if(itm.isCheckable()) {
                itm.setChecked(((ToggleButton)v).isChecked());
                m_itemClickListener.onMenuItemClick(itm);
            } else {
                m_itemClickListener.onMenuItemClick(itm);
            }
        }
    }

    public Menu getMenu() {
        return m_menu;
    }

    private PopupMenu.OnMenuItemClickListener m_internalOnClickListener = new PopupMenu.OnMenuItemClickListener() {
        @Override
        public boolean onMenuItemClick(MenuItem menuItem) {
            MenuItem realItem = m_menu.findItem(menuItem.getItemId());
            if(realItem.isCheckable())
                realItem.setChecked(menuItem.isChecked());

            return m_itemClickListener.onMenuItemClick(realItem);
        }
    };
}
