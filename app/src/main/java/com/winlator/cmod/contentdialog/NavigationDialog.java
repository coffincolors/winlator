package com.winlator.cmod.contentdialog;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.google.android.material.navigation.NavigationView;
import com.winlator.cmod.R;
import com.winlator.cmod.XServerDisplayActivity;

public class NavigationDialog extends ContentDialog {

    public NavigationDialog(@NonNull XServerDisplayActivity context) {
        super(context, R.layout.navigation_dialog);
        setIcon(R.drawable.icon_container);
        setTitle(context.getString(R.string.app_name));
        findViewById(R.id.BTCancel).setVisibility(View.GONE);

        GridLayout grid = findViewById(R.id.main_menu_grid);
        int orientation = context.getResources().getConfiguration().orientation;
        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            grid.setColumnCount(5);
        } else {
            grid.setColumnCount(2);
        }

        NavigationView navigation = context.findViewById(R.id.NavigationView);
        Menu menu = navigation.getMenu();
        for (int i = 0; i < menu.size(); i++) {
            MenuItem item = menu.getItem(i);
            if (!item.isVisible()) {
                continue;
            }

            int padding = dpToPx(5, context);
            LinearLayout layout = new LinearLayout(context);
            layout.setPadding(padding, padding, padding, padding);
            layout.setOrientation(LinearLayout.VERTICAL);
            layout.setOnClickListener(view -> {
                context.onNavigationItemSelected(item);
                dismiss();
            });

            layout.setOnFocusChangeListener((view, focused) -> {
                if (focused) {
                    layout.setBackgroundColor(Color.GRAY);
                } else {
                    layout.setBackgroundColor(Color.TRANSPARENT);
                }
            });

            int size = dpToPx(40, context);
            View icon = new View(context);
            item.getIcon().setTint(context.getColor(R.color.colorAccent));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
            lp.gravity = Gravity.CENTER_HORIZONTAL;
            icon.setLayoutParams(lp);
            icon.setBackground(item.getIcon());
            layout.addView(icon);

            int width = dpToPx(96, context);
            TextView text = new TextView(context);
            text.setLayoutParams(new ViewGroup.LayoutParams(width, ViewGroup.LayoutParams.WRAP_CONTENT));
            text.setText(item.getTitle());
            text.setGravity(Gravity.CENTER);
            text.setLines(2);
            layout.addView(text);

            grid.addView(layout);
        }
    }

    public int dpToPx(float dp, Context context){
        return (int) (dp * context.getResources().getDisplayMetrics().densityDpi / DisplayMetrics.DENSITY_DEFAULT);
    }
}
