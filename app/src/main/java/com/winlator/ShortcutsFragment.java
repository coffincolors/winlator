package com.winlator;

import static androidx.core.content.ContextCompat.getSystemService;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
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
import androidx.documentfile.provider.DocumentFile;
import androidx.fragment.app.Fragment;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.winlator.container.ContainerManager;
import com.winlator.container.Shortcut;
import com.winlator.contentdialog.ContentDialog;
import com.winlator.contentdialog.ShortcutSettingsDialog;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Collections;
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

    private ShortcutInfo buildScreenShortCut(String shortLabel, String longLabel, int containerId, String shortcutPath, Icon icon, String uuid) {
        Intent intent = new Intent(getActivity(), XServerDisplayActivity.class);
        intent.setAction(Intent.ACTION_VIEW);
        intent.putExtra("container_id", containerId);
        intent.putExtra("shortcut_path", shortcutPath);

        return new ShortcutInfo.Builder(getActivity(), uuid)
                .setShortLabel(shortLabel)
                .setLongLabel(longLabel)
                .setIcon(icon)
                .setIntent(intent)
                .build();
    }

    private void addShortcutToScreen(Shortcut shortcut) {
        ShortcutManager shortcutManager = getSystemService(requireContext(), ShortcutManager.class);
        if (shortcutManager != null && shortcutManager.isRequestPinShortcutSupported())
            shortcutManager.requestPinShortcut(buildScreenShortCut(shortcut.name, shortcut.name, shortcut.container.id,
                    shortcut.file.getPath(), Icon.createWithBitmap(shortcut.icon), shortcut.getExtra("uuid")), null);
    }

    public static void disableShortcutOnScreen(Context context, Shortcut shortcut) {
        ShortcutManager shortcutManager = getSystemService(context, ShortcutManager.class);
        try {
            shortcutManager.disableShortcuts(Collections.singletonList(shortcut.getExtra("uuid")),
                    context.getString(R.string.shortcut_not_available));
        } catch (Exception e) {}
    }



    public void updateShortcutOnScreen(String shortLabel, String longLabel, int containerId, String shortcutPath, Icon icon, String uuid) {
        ShortcutManager shortcutManager = getSystemService(requireContext(), ShortcutManager.class);
        try {
            for (ShortcutInfo shortcutInfo : shortcutManager.getPinnedShortcuts()) {
                if (shortcutInfo.getId().equals(uuid)) {
                    shortcutManager.updateShortcuts(Collections.singletonList(
                            buildScreenShortCut(shortLabel, longLabel, containerId, shortcutPath, icon, uuid)));
                    break;
                }
            }
        } catch (Exception e) {}
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
        public void onViewRecycled(@NonNull ViewHolder holder) {
            holder.menuButton.setOnClickListener(null);
            holder.innerArea.setOnClickListener(null);
            super.onViewRecycled(holder);
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
                }
                else if (itemId == R.id.shortcut_remove) {
                    ContentDialog.confirm(context, R.string.do_you_want_to_remove_this_shortcut, () -> {
                        if (shortcut.file.delete() && shortcut.iconFile != null) shortcut.iconFile.delete();
                        disableShortcutOnScreen(requireContext(), shortcut);
                        loadShortcutsList();
                    });
                }                 else if (itemId == R.id.shortcut_add_to_home_screen) {
                    if (shortcut.getExtra("uuid").equals(""))
                        shortcut.genUUID();
                    addShortcutToScreen(shortcut);
                }
                else if (itemId == R.id.shortcut_export_to_frontend) {
                    exportShortcutToFrontend(shortcut);
                }
                else if (itemId == R.id.shortcut_properties) {
                    showShortcutProperties(shortcut);
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
                intent.putExtra("shortcut_name", shortcut.name); // Add this line to pass the shortcut name
                activity.startActivity(intent);
            }
            else XrActivity.openIntent(activity, shortcut.container.id, shortcut.file.getPath());
        }



        private void exportShortcutToFrontend(Shortcut shortcut) {
            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
            String uriString = sharedPreferences.getString("frontend_export_uri", null);

            // Check if a custom URI is set, if not fall back to default path
            DocumentFile pickedDir;
            if (uriString != null) {
                // Convert URI string back to a Uri object for the custom directory
                Uri folderUri = Uri.parse(uriString);
                pickedDir = DocumentFile.fromTreeUri(getContext(), folderUri);

                if (pickedDir == null || !pickedDir.canWrite()) {
                    Toast.makeText(getContext(), "Cannot write to the selected folder", Toast.LENGTH_SHORT).show();
                    return;
                }
            } else {
                // Default to Downloads\Winlator\Frontend if no custom URI is set
                File defaultDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Winlator/Frontend");

                // Create the directory if it doesn't exist
                if (!defaultDir.exists() && !defaultDir.mkdirs()) {
                    Toast.makeText(getContext(), "Failed to create default directory", Toast.LENGTH_SHORT).show();
                    return;
                }

                // Convert the default directory to a DocumentFile
                Uri defaultDirUri = Uri.fromFile(defaultDir);
                pickedDir = DocumentFile.fromFile(defaultDir);
            }

            // Resolve package name dynamically
            String packageName = getContext().getPackageName();

            // Create FRONTEND_INSTRUCTIONS.txt if it doesn't exist
            DocumentFile instructionsFile = pickedDir.findFile("FRONTEND_INSTRUCTIONS.txt");
            if (instructionsFile == null) {
                instructionsFile = pickedDir.createFile("text/plain", "FRONTEND_INSTRUCTIONS.txt");
            }

            if (instructionsFile != null) {
                try (OutputStream outStream = getContext().getContentResolver().openOutputStream(instructionsFile.getUri());
                     BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(outStream))) {
                    writer.write("Instructions for adding Winlator shortcuts to Frontends (WIP):\n\n");
                    writer.write("Daijisho:\n\n");
                    writer.write("1. Open Daijisho\n");
                    writer.write("2. Navigate to the Settings tab.\n");
                    writer.write("3. Navigate to Settings\\Library\n");
                    writer.write("4. Select, Import from Pegasus\n");
                    writer.write("5. Add the metadata.pegasus.txt file located in this directory\n");
                    writer.write("6. Set the Sync path to the selected directory\n");
                    writer.write("7. Start your game!\n\n");
                    writer.write("Beacon:\n\n");
                    writer.write("1. Navigate to Settings\n");
                    writer.write("2. Click the + Icon\n");
                    writer.write("3. Set the following values:\n\n");
                    writer.write("Platform Type: Custom\n");
                    writer.write("Name: Windows (or Winlator, whatever you prefer)\n");
                    writer.write("Short name: windows\n");
                    writer.write("Player app: Select Winlator.glibcmod\n");
                    writer.write("ROMs folder: Use Android FilePicker to select the chosen directory\n");
                    writer.write("Expand Advanced:\n");
                    writer.write("File handling: Default\n");
                    writer.write("Use custom launch: True\n");
                    writer.write("am start command: am start -n " + packageName + "/com.winlator.XServerDisplayActivity -e shortcut_path {file_path}\n\n");
                    writer.write("4. Click Save\n");
                    writer.write("5. Scan the folder for your game\n");
                    writer.write("6. Launch your game!\n");
                    writer.flush();
                    Log.d("ShortcutsFragment", "FRONTEND_INSTRUCTIONS.txt created successfully.");
                } catch (IOException e) {
                    Log.e("ShortcutsFragment", "Failed to create FRONTEND_INSTRUCTIONS.txt", e);
                }
            }

            // Create or update metadata.pegasus.txt in the selected folder
            DocumentFile metadataFile = pickedDir.findFile("metadata.pegasus.txt");
            if (metadataFile == null) {
                metadataFile = pickedDir.createFile("text/plain", "metadata.pegasus.txt");
            }

            if (metadataFile != null) {
                try (OutputStream outStream = getContext().getContentResolver().openOutputStream(metadataFile.getUri());
                     BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(outStream))) {
                    writer.write("collection: Windows (Cmod Proot)\n");
                    writer.write("shortname: winproot\n");
                    writer.write("extensions: desktop\n");
                    writer.write("launch: am start\n");
                    writer.write("  -n " + packageName + "/com.winlator.XServerDisplayActivity\n");
                    writer.write("  -e shortcut_path {file.path}\n");
                    writer.write("  --activity-clear-task\n");
                    writer.write("  --activity-clear-top\n");
                    writer.write("  --activity-no-history\n");
                    writer.flush();
                    Log.d("ShortcutsFragment", "metadata.pegasus.txt created successfully.");
                } catch (IOException e) {
                    Log.e("ShortcutsFragment", "Failed to create metadata.pegasus.txt", e);
                }
            }

            // Export the shortcut file directly into the chosen or default directory
            DocumentFile exportFile = pickedDir.findFile(shortcut.file.getName());
            if (exportFile != null) {
                // Delete the existing file to allow overwriting
                exportFile.delete();
            }

            // Recreate the export file without specifying a MIME type to avoid the .bin extension
            exportFile = pickedDir.createFile(null, shortcut.file.getName());


            if (exportFile != null) {
                try (OutputStream outStream = getContext().getContentResolver().openOutputStream(exportFile.getUri());
                     BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(outStream))) {
                    List<String> lines = new ArrayList<>();
                    try (BufferedReader reader = new BufferedReader(new FileReader(shortcut.file))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            if (line.startsWith("container_id:")) {
                                // Replace container_id
                                lines.add("container_id:" + shortcut.container.id);
                            } else {
                                lines.add(line);
                            }
                        }
                    }

                    // If no container_id was found, add it
                    if (!lines.contains("container_id:" + shortcut.container.id)) {
                        lines.add("container_id:" + shortcut.container.id);
                    }

                    for (String line : lines) {
                        writer.write(line + "\n");
                    }
                    writer.flush();

                    Log.d("ShortcutsFragment", "Shortcut exported successfully to " + exportFile.getUri().getPath());
                    Toast.makeText(getContext(), "Frontend Shortcut Exported", Toast.LENGTH_LONG).show();

                } catch (IOException e) {
                    Log.e("ShortcutsFragment", "Failed to export shortcut", e);
                    Toast.makeText(getContext(), "Failed to export shortcut", Toast.LENGTH_LONG).show();
                }
            }
        }

        private void showShortcutProperties(Shortcut shortcut) {
            SharedPreferences playtimePrefs = getContext().getSharedPreferences("playtime_stats", Context.MODE_PRIVATE);

            String playtimeKey = shortcut.name + "_playtime";
            String playCountKey = shortcut.name + "_play_count";

            long totalPlaytime = playtimePrefs.getLong(playtimeKey, 0);
            int playCount = playtimePrefs.getInt(playCountKey, 0);

            // Convert playtime to human-readable format
            long seconds = (totalPlaytime / 1000) % 60;
            long minutes = (totalPlaytime / (1000 * 60)) % 60;
            long hours = (totalPlaytime / (1000 * 60 * 60)) % 24;
            long days = (totalPlaytime / (1000 * 60 * 60 * 24));

            String playtimeFormatted = String.format("%dd %02dh %02dm %02ds", days, hours, minutes, seconds);

            // Create the properties dialog
            ContentDialog dialog = new ContentDialog(getContext(), R.layout.shortcut_properties_dialog);
            dialog.setTitle("Properties");

            TextView playCountTextView = dialog.findViewById(R.id.play_count);
            TextView playtimeTextView = dialog.findViewById(R.id.playtime);

            playCountTextView.setText("Number of times played: " + playCount);
            playtimeTextView.setText("Playtime: " + playtimeFormatted);

            dialog.show();
        }




    }

}
