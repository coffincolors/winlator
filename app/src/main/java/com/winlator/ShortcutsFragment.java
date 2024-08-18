package com.winlator;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.winlator.container.ContainerManager;
import com.winlator.container.Shortcut;
import com.winlator.contentdialog.ContentDialog;
import com.winlator.contentdialog.ShortcutSettingsDialog;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class ShortcutsFragment extends Fragment {
    private RecyclerView recyclerView;
    private TextView emptyTextView;
    private ContainerManager manager;
    private static final int REQUEST_CREATE_SHORTCUT = 1001;

    private ShortcutBroadcastReceiver shortcutReceiver;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(false);

        // Register the ShortcutBroadcastReceiver dynamically
        IntentFilter filter = new IntentFilter("com.winlator.SHORTCUT_ADDED");
        shortcutReceiver = new ShortcutBroadcastReceiver();
        LocalBroadcastManager.getInstance(getContext()).registerReceiver(shortcutReceiver, filter);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // Unregister the receiver to avoid memory leaks
        if (shortcutReceiver != null) {
            LocalBroadcastManager.getInstance(getContext()).unregisterReceiver(shortcutReceiver);
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        manager = new ContainerManager(getContext());
        loadShortcutsList();
        ((AppCompatActivity)getActivity()).getSupportActionBar().setTitle(R.string.shortcuts);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        FrameLayout frameLayout = (FrameLayout)inflater.inflate(R.layout.shortcuts_fragment, container, false);
        recyclerView = frameLayout.findViewById(R.id.RecyclerView);
        emptyTextView = frameLayout.findViewById(R.id.TVEmptyText);
        recyclerView.setLayoutManager(new LinearLayoutManager(recyclerView.getContext()));
        recyclerView.addItemDecoration(new DividerItemDecoration(recyclerView.getContext(), DividerItemDecoration.VERTICAL));
        return frameLayout;
    }

    public void loadShortcutsList() {
        ArrayList<Shortcut> shortcuts = manager.loadShortcuts();
        recyclerView.setAdapter(new ShortcutsAdapter(shortcuts));
        if (shortcuts.isEmpty()) emptyTextView.setVisibility(View.VISIBLE);
    }

    private class ShortcutsAdapter extends RecyclerView.Adapter<ShortcutsAdapter.ViewHolder> {
        private final List<Shortcut> data;

        private class ViewHolder extends RecyclerView.ViewHolder {
            private final ImageButton menuButton;
            private final ImageView imageView;
            private final TextView title;
            private final TextView subtitle;
            private final View innerArea;

            private ViewHolder(View view) {
                super(view);
                this.imageView = view.findViewById(R.id.ImageView);
                this.title = view.findViewById(R.id.TVTitle);
                this.subtitle = view.findViewById(R.id.TVSubtitle);
                this.menuButton = view.findViewById(R.id.BTMenu);
                this.innerArea = view.findViewById(R.id.LLInnerArea);
            }
        }

        public ShortcutsAdapter(List<Shortcut> data) {
            this.data = data;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.shortcut_list_item, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            final Shortcut item = data.get(position);
            if (item.icon != null) holder.imageView.setImageBitmap(item.icon);
            holder.title.setText(item.name);
            holder.subtitle.setText(item.container.getName());
            holder.menuButton.setOnClickListener((v) -> showListItemMenu(v, item));
            holder.innerArea.setOnClickListener((v) -> runFromShortcut(item));
        }

        @Override
        public final int getItemCount() {
            return data.size();
        }

        private void showListItemMenu(View anchorView, final Shortcut shortcut) {
            final Context context = getContext();
            PopupMenu listItemMenu = new PopupMenu(context, anchorView);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) listItemMenu.setForceShowIcon(true);

            listItemMenu.inflate(R.menu.shortcut_popup_menu);
            listItemMenu.setOnMenuItemClickListener((menuItem) -> {
                int itemId = menuItem.getItemId();
                if (itemId == R.id.shortcut_settings) {
                    (new ShortcutSettingsDialog(ShortcutsFragment.this, shortcut)).show();
                } else if (itemId == R.id.shortcut_remove) {
                    ContentDialog.confirm(context, R.string.do_you_want_to_remove_this_shortcut, () -> {
                        if (shortcut.file.delete() && shortcut.iconFile != null) shortcut.iconFile.delete();
                        loadShortcutsList();
                    });
                } else if (itemId == R.id.shortcut_add_to_homescreen) {
                    addToHomescreen(context, shortcut);
                }
                return true;
            });
            listItemMenu.show();
        }

        private void addToHomescreen(Context context, Shortcut shortcut) {
            String LOG_TAG = "ShortcutHomeScreen";
            Log.d(LOG_TAG, "Attempting to add shortcut to homescreen: " + shortcut.name);

            // Create the intent that will be launched when the shortcut is clicked
            Intent shortcutIntent = new Intent(context, XServerDisplayActivity.class);
            shortcutIntent.putExtra("container_id", shortcut.container.id);
            shortcutIntent.putExtra("shortcut_path", shortcut.file.getAbsolutePath());
            shortcutIntent.setAction(Intent.ACTION_MAIN);

            // Retrieve the icon for the shortcut
            Bitmap icon = getShortcutIcon(context, shortcut);
            if (icon == null) {
                Log.e(LOG_TAG, "Failed to retrieve icon for shortcut.");
                return; // Exit if icon retrieval fails
            }
            Log.d(LOG_TAG, "Retrieved icon for shortcut.");

            boolean isShortcutAdded = false;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ShortcutManager shortcutManager = context.getSystemService(ShortcutManager.class);

                if (shortcutManager == null) {
                    Log.e(LOG_TAG, "ShortcutManager is null.");
                    return;
                }

                // Build the shortcut info
                ShortcutInfo shortcutInfo = new ShortcutInfo.Builder(context, shortcut.name)
                        .setShortLabel(shortcut.name)
                        .setIcon(Icon.createWithBitmap(icon))
                        .setIntent(shortcutIntent)
                        .setActivity(new ComponentName(context, MainActivity.class)) // Ensure the correct activity is set
                        .build();

                // Retrieve and manage the dynamic shortcuts list
                List<ShortcutInfo> dynamicShortcuts = new ArrayList<>(shortcutManager.getDynamicShortcuts());
                dynamicShortcuts.add(shortcutInfo);

                // Limit to 4 shortcuts
                if (dynamicShortcuts.size() > 4) {
                    dynamicShortcuts.remove(0);
                }

                // Set the dynamic shortcuts
                shortcutManager.setDynamicShortcuts(dynamicShortcuts);

                // Toast and log feedback
                Toast.makeText(context, "Shortcut added successfully (May not work on all devices)", Toast.LENGTH_SHORT).show();
                Log.d(LOG_TAG, "Shortcut added successfully.");
                isShortcutAdded = true;
            } else {
                // Fallback for older versions (API < 26)
                Intent addIntent = new Intent();
                addIntent.putExtra(Intent.EXTRA_SHORTCUT_INTENT, shortcutIntent);
                addIntent.putExtra(Intent.EXTRA_SHORTCUT_NAME, shortcut.name);
                addIntent.putExtra(Intent.EXTRA_SHORTCUT_ICON, icon);
                addIntent.setAction("com.android.launcher.action.INSTALL_SHORTCUT");

                try {
                    context.sendBroadcast(addIntent);
                    Log.d(LOG_TAG, "Sent broadcast to install shortcut.");
                    Toast.makeText(context, "Shortcut added successfully (Broadcast).", Toast.LENGTH_SHORT).show();
                    isShortcutAdded = true;
                } catch (Exception e) {
                    Log.e(LOG_TAG, "Error sending broadcast for installing shortcut: " + e.getMessage(), e);
                    Toast.makeText(context, "Failed to add shortcut via broadcast.", Toast.LENGTH_SHORT).show();
                }
            }

            if (isShortcutAdded) {
                Log.d(LOG_TAG, "Shortcut added successfully.");
            } else {
                Log.e(LOG_TAG, "Failed to add the shortcut, missing data.");
            }
        }

        // Helper method to retrieve the icon
        private Bitmap getShortcutIcon(Context context, Shortcut shortcut) {
            String LOG_TAG = "ShortcutHomeScreen";
            Bitmap icon = null;

            if (shortcut.icon != null) {
                Log.d(LOG_TAG, "Using the shortcut's existing icon.");
                icon = shortcut.icon;
            } else {
                Log.d(LOG_TAG, "Shortcut icon missing, attempting to load icon from the file.");
                File[] iconDirs = {shortcut.container.getIconsDir(64), shortcut.container.getIconsDir(48), shortcut.container.getIconsDir(32), shortcut.container.getIconsDir(16)};
                for (File iconDir : iconDirs) {
                    File iconFile = new File(iconDir, shortcut.name + ".png");
                    if (iconFile.isFile()) {
                        icon = BitmapFactory.decodeFile(iconFile.getPath());
                        if (icon != null) {
                            Log.d(LOG_TAG, "Successfully loaded icon from: " + iconFile.getPath());
                            break;
                        }
                    }
                }
            }

            if (icon == null) {
                Log.d(LOG_TAG, "Falling back to the app's default icon.");
                Drawable defaultIcon = context.getApplicationInfo().loadIcon(context.getPackageManager());
                icon = drawableToBitmap(defaultIcon);
            }

            return icon;
        }

        // Helper method to convert Drawable to Bitmap
        private Bitmap drawableToBitmap(Drawable drawable) {
            String LOG_TAG = "ShortcutHomeScreen";

            if (drawable instanceof BitmapDrawable) {
                Log.d(LOG_TAG, "Drawable is already a BitmapDrawable.");
                return ((BitmapDrawable) drawable).getBitmap();
            }

            Log.d(LOG_TAG, "Converting Drawable to Bitmap.");
            Bitmap bitmap = Bitmap.createBitmap(drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
            drawable.draw(canvas);
            Log.d(LOG_TAG, "Drawable successfully converted to Bitmap.");
            return bitmap;
        }



        private void runFromShortcut(Shortcut shortcut) {
            Activity activity = getActivity();

            if (!XrActivity.isSupported()) {
                Intent intent = new Intent(activity, XServerDisplayActivity.class);
                intent.putExtra("container_id", shortcut.container.id);
                intent.putExtra("shortcut_path", shortcut.file.getPath());
                activity.startActivity(intent);
            }
            else XrActivity.openIntent(activity, shortcut.container.id, shortcut.file.getPath());
        }

    }
}
