package com.winlator.cmod;

import static androidx.core.content.ContextCompat.getSystemService;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.FileObserver;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.documentfile.provider.DocumentFile;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.winlator.cmod.R;
import com.winlator.cmod.container.Container;
import com.winlator.cmod.container.ContainerManager;
import com.winlator.cmod.container.Shortcut;
import com.winlator.cmod.contentdialog.ContentDialog;
import com.winlator.cmod.contentdialog.ShortcutSettingsDialog;
import com.winlator.cmod.core.FileUtils;
import com.winlator.cmod.core.MSLink;
import com.winlator.cmod.core.PreloaderDialog;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;

public class ShortcutsFragment extends Fragment {
    private static final String TAG = "ShortcutsFragment";

    private RecyclerView recyclerView;
    private TextView emptyTextView;
    private ContainerManager manager;
    private Shortcut currentShortcut;

    private ArrayList<FileObserver> fileObservers = new ArrayList<>();
    private PreloaderDialog preloaderDialog;

    private final ActivityResultLauncher<Intent> iconPickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null && currentShortcut != null) {
                        // This is where you will handle the selected .ico file
                        handleSelectedIcon(uri);
                    }
                }
            });

    private void openIconPicker(Shortcut shortcut) {
        this.currentShortcut = shortcut;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/x-icon"); // Set the primary MIME type for .ico files

        // Provide an array of possible MIME types to be more compatible
        String[] mimeTypes = {"image/x-icon", "image/vnd.microsoft.icon"};
        intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);

        iconPickerLauncher.launch(intent);
    }

    private void showIconPickerConfirmation(final Shortcut shortcut) {
        new AlertDialog.Builder(getContext())
                .setTitle("Custom Icon")
                .setMessage("You will be prompted to select an icon file. Please choose a valid .ico file.")
                .setPositiveButton("Continue", (dialog, which) -> {
                    // This will launch the file picker
                    openIconPicker(shortcut);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void handleSelectedIcon(Uri icoFileUri) {
        try {
            File iconsDir = new File(currentShortcut.container.getIconsDir(0).getParentFile(), "custom_icons");
            if (!iconsDir.exists()) {
                iconsDir.mkdirs();
            }

            // Use the shortcut's unique name to create a unique icon filename
            String newIconFileName = currentShortcut.name + "_" + System.currentTimeMillis() + ".ico";
            File newIconFile = new File(iconsDir, newIconFileName);

            // Use the correct copy method from your FileUtils class
            if (FileUtils.copy(getContext(), icoFileUri, newIconFile)) {
                // Update the shortcut to point to this new icon
                currentShortcut.setCustomIconPath(newIconFile.getAbsolutePath());

                // Reload the list to show the new icon
                loadShortcutsList();
                Toast.makeText(getContext(), "Icon updated successfully.", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(getContext(), "Failed to copy icon file.", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(getContext(), "Failed to update icon.", Toast.LENGTH_SHORT).show();
            Log.e("ShortcutsFragment", "Error handling selected icon", e);
        }
    }


    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(false);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        stopFileObservers(); // Stop watching to prevent memory leaks
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        manager = new ContainerManager(getContext());
        preloaderDialog = new PreloaderDialog(getActivity());
        loadShortcutsList();
        startFileObservers(); // Start watching for new file
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

    private Container findContainerForFile(File file) {
        for (Container container : manager.getContainers()) {
            if (file.getAbsolutePath().startsWith(container.getDesktopDir().getAbsolutePath())) {
                return container;
            }
        }
        return null;
    }
    private void startFileObservers() {
        stopFileObservers();
        ArrayList<Container> containers = manager.getContainers();
        ArrayList<File> orphanedLinks = new ArrayList<>();
        Log.d("ShortcutObserver", "Starting observers for " + containers.size() + " containers.");

        for (Container container : containers) {
            File desktopDir = container.getDesktopDir();
            Log.d("ShortcutObserver", "Checking container " + container.id + " at path: " + desktopDir.getAbsolutePath());

            if (desktopDir.exists() && desktopDir.isDirectory()) {
                File[] files = desktopDir.listFiles();
                if (files != null) {
                    for (File file : files) {
                        if (file.getName().toLowerCase().endsWith(".lnk")) {
                            String desktopFileName = FileUtils.getBasename(file.getName()) + ".desktop";
                            File desktopFile = new File(desktopDir, desktopFileName);
                            if (!desktopFile.exists()) {
                                Log.d("ShortcutObserver", "Found orphaned .lnk file: " + file.getName());
                                orphanedLinks.add(file);
                            }
                        }
                    }
                }

                FileObserver observer = new FileObserver(desktopDir, FileObserver.CREATE) {
                    @Override
                    public void onEvent(int event, @Nullable String path) {
                        if (path != null && path.toLowerCase().endsWith(".lnk")) {
                            Log.d("ShortcutObserver", "New .lnk file created: " + path);
                            final File newLnkFile = new File(desktopDir, path);
                            if (getActivity() != null) {
                                getActivity().runOnUiThread(() -> {
                                    preloaderDialog.show(R.string.creating_shortcut);
                                    processNewLinkFile(newLnkFile, container);
                                });
                            }
                        }
                    }
                };
                observer.startWatching();
                fileObservers.add(observer);
            } else {
                Log.w("ShortcutObserver", "Desktop directory does not exist for container " + container.id + ": " + desktopDir.getAbsolutePath());
            }
        }

        if (!orphanedLinks.isEmpty()) {
            Log.d("ShortcutObserver", "Processing " + orphanedLinks.size() + " orphaned links.");
            preloaderDialog.show(R.string.creating_shortcut);
            processOrphanedLinkFiles(orphanedLinks);
        } else {
            Log.d("ShortcutObserver", "No orphaned links found.");
        }
    }

    private void processOrphanedLinkFiles(ArrayList<File> lnkFiles) {
        Executors.newSingleThreadExecutor().execute(() -> {
            boolean shortcutsChanged = false;
            try {
                for (File lnkFile : lnkFiles) {
                    Container owner = findContainerForFile(lnkFile);
                    if (owner != null) {
                        if (createDesktopFileFromLnk(lnkFile, owner)) {
                            shortcutsChanged = true;
                        }
                    }
                }
            } finally {
                if (getActivity() != null) {
                    final boolean finalShortcutsChanged = shortcutsChanged;
                    getActivity().runOnUiThread(() -> {
                        if (preloaderDialog != null) preloaderDialog.close();
                        if (finalShortcutsChanged) {
                            loadShortcutsList();
                            Toast.makeText(getContext(), "Shortcuts updated.", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        });
    }

    private void processNewLinkFile(File lnkFile, Container container) {
        Executors.newSingleThreadExecutor().execute(() -> {
            boolean shortcutCreated = false;
            try {
                shortcutCreated = createDesktopFileFromLnk(lnkFile, container);
            } finally {
                if (getActivity() != null) {
                    final boolean finalShortcutCreated = shortcutCreated;
                    getActivity().runOnUiThread(() -> {
                        if (preloaderDialog != null) preloaderDialog.close();
                        if (finalShortcutCreated) {
                            loadShortcutsList();
                            Toast.makeText(getContext(), "New shortcut created!", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        });
    }




    private boolean createDesktopFileFromLnk(File lnkFile, Container container) {
        try {
            Log.d("ShortcutCreation", "Processing .lnk: " + lnkFile.getAbsolutePath());
            String targetPath = MSLink.parse(lnkFile); // e.g., "D:\\Games\\Example Folder\\Example.exe"

            if (targetPath == null || targetPath.isEmpty() || !targetPath.contains(":")) {
                Log.e("ShortcutCreation", "Failed to parse a valid target path from " + lnkFile.getName());
                return false;
            }
            Log.d("ShortcutCreation", "Parsed target path: " + targetPath);

            String lnkName = FileUtils.getBasename(lnkFile.getName());
            File desktopFile = new File(container.getDesktopDir(), lnkName + ".desktop");

            if (desktopFile.exists()) {
                Log.d("ShortcutCreation", "Skipping existing .desktop file: " + desktopFile.getName());
                return false;
            }

            // --- FINAL LOGIC TO MATCH YOUR EXACT EXAMPLE ---

            // 1. Get the app's root files directory to construct the generic path
            String filesDir = getContext().getFilesDir().getAbsolutePath(); // e.g., /data/user/0/com.winlator.cmod/files
            String genericHomePath = filesDir + "/imagefs/home/xuser";

            // 2. Construct the specific, complex WINEPREFIX path
            String winePrefix = genericHomePath + "/.wine/dosdevices/z:" + genericHomePath + "/.wine";

            // 3. To get "\\\\", we need to escape twice. Once for Java, once for the file.
            String escapedTargetPath = targetPath.replace("\\", "\\\\\\\\");

            // 4. Construct the full Exec command
            String execCommand = "env WINEPREFIX=\"" + winePrefix + "\" wine " + escapedTargetPath;

            // 5. Construct the working directory Path using the generic dosdevices path
            File genericDosdevicesDir = new File(genericHomePath, ".wine/dosdevices");
            String driveLetter = targetPath.substring(0, 1).toLowerCase();
            File driveSymlink = new File(genericDosdevicesDir, driveLetter + ":");
            String pathAfterDrive = targetPath.substring(targetPath.indexOf('\\') + 1);
            String windowsWorkingDir = FileUtils.getDirname(pathAfterDrive);
            String finalWorkingPath = new File(driveSymlink, windowsWorkingDir).getAbsolutePath();

            // 6. Get the executable name for StartupWMClass
            String wmClass = FileUtils.getName(targetPath);

            // 7. Construct the final .desktop file content
            String content =
                    "[Desktop Entry]\n" +
                            "Name=" + lnkName + "\n" +
                            "Exec=" + execCommand + "\n" +
                            "Type=Application\n" +
                            "StartupNotify=true\n" +
                            "Path=" + finalWorkingPath + "\n" +
                            "Icon=\n" +
                            "StartupWMClass=" + wmClass + "\n\n" +
                            "[Extra Data]\n" +
                            "container_id:" + container.id + "\n";

            FileUtils.writeString(desktopFile, content);
            Log.d("ShortcutCreation", "SUCCESS: Created .desktop file at " + desktopFile.getAbsolutePath());
            Log.d("ShortcutCreation", "Content:\n" + content);

            return true;
        } catch (IOException e) {
            Log.e("ShortcutCreation", "IOException creating .desktop file from .lnk", e);
            return false;
        }
    }



    private void stopFileObservers() {
        for (FileObserver observer : fileObservers) {
            observer.stopWatching();
        }
        fileObservers.clear();
    }
    public void loadShortcutsList() {

        ArrayList<Shortcut> shortcuts = new ArrayList<>();
        ArrayList<File> quarantined   = new ArrayList<>();

        // ContainerManager can still throw (e.g. I/O permission issues).
        // Keep the whole call in one try/catch so the UI never dies.
        try {
            for (Container c : manager.getContainers()) {
                for (File f : c.getDesktopDir().listFiles((dir, n) -> n.endsWith(".desktop"))) {
                    try {
                        Shortcut s = new Shortcut(c, f);   // may throw
                        // very cheap logical sanity check
                        if (s.name == null || s.name.trim().isEmpty()) {
                            throw new IllegalStateException("empty name");
                        }
                        shortcuts.add(s);

                    } catch (Throwable t) {               // <-- swallow & quarantine
                        Log.e("ShortcutsFragment", "Bad shortcut: " + f.getAbsolutePath(), t);
                        quarantined.add(f);
                    }
                }
            }

        } catch (Throwable fatal) {
            Log.e("ShortcutsFragment", "Fatal error while scanning shortcuts!", fatal);
            Toast.makeText(getContext(),
                    "Couldn’t load shortcuts (see log).", Toast.LENGTH_LONG).show();
        }

        // ---- UI update ----
        Collections.sort(shortcuts, (a, b) -> {
            if (a == null || b == null) return 0;
            String an = a.name == null ? "" : a.name;
            String bn = b.name == null ? "" : b.name;
            return an.compareToIgnoreCase(bn);
        });

        recyclerView.setAdapter(new ShortcutsAdapter(shortcuts));
        emptyTextView.setVisibility(shortcuts.isEmpty() ? View.VISIBLE : View.GONE);

        // ---- quarantine report ----
        if (!quarantined.isEmpty()) {
            Toast.makeText(getContext(),
                    quarantined.size() + " shortcut(s) ignored (corrupted):",
                    Toast.LENGTH_LONG).show();

            // Move them out of the way so the crash never happens again.
            for (File bad : quarantined) {
                File dst = new File(bad.getParent(), bad.getName() + ".bad");
                Toast.makeText(getContext(), bad.getName() + " renamed to " + dst.getName(), Toast.LENGTH_LONG).show();
                // ignore return value – it’s a best-effort quarantine
                //noinspection ResultOfMethodCallIgnored
                bad.renameTo(dst);
            }
        }
    }


    private class ShortcutsAdapter extends RecyclerView.Adapter<ShortcutsAdapter.ViewHolder> {
        private final List<Shortcut> data;

        private class ViewHolder extends RecyclerView.ViewHolder {
            private final ImageButton menuButton;
            private final ImageButton imageView;
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
            if (item.icon != null) {
                holder.imageView.setImageBitmap(item.icon);
            } else {
                // Set a default icon if none exists
                holder.imageView.setImageResource(R.mipmap.ic_launcher_foreground); // Create a default icon drawable
            }
            holder.imageView.setOnClickListener(v -> showIconPickerConfirmation(item));            holder.title.setText(item.name);
            holder.subtitle.setText(item.container.getName());
            holder.menuButton.setOnClickListener((v) -> showListItemMenu(v, item));
            holder.innerArea.setOnClickListener((v) -> runFromShortcut(item));

            // Get the context from the item view
            Context context = holder.itemView.getContext();

            // Check if dark mode is enabled
            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
            boolean isDarkMode = sharedPreferences.getBoolean("dark_mode", false);

            if (isDarkMode) {
                // Set the text color to something light for dark backgrounds
                holder.title.setTextColor(android.graphics.Color.WHITE);
            } else {
                // Set the text color to something dark for light backgrounds
                holder.title.setTextColor(android.graphics.Color.BLACK);
            }
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
                        boolean desktopDeleted  = safeDelete(shortcut.file);
                        boolean iconDeleted     = safeDelete(shortcut.iconFile);
                        boolean lnkDeleted      = deletePairedLnkForShortcut(shortcut);

                        if (desktopDeleted) {
                            disableShortcutOnScreen(requireContext(), shortcut);
                            loadShortcutsList();
                        }

                        String msg;
                        if (desktopDeleted) {
                            if (lnkDeleted) {
                                msg = "Shortcut and paired .lnk removed.";
                            } else {
                                msg = "Shortcut removed." + (shortcut.file != null
                                        ? " (No paired .lnk found or could not delete.)"
                                        : "");
                            }
                        } else {
                            msg = "Failed to remove the shortcut. Please try again.";
                        }

                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show();
                    });
                }
                else if (itemId == R.id.shortcut_clone_to_container) {
                    // Use the ContainerManager to get the list of containers
                    ContainerManager containerManager = new ContainerManager(context);
                    ArrayList<Container> containers = containerManager.getContainers();

                    // Show a container selection dialog
                    showContainerSelectionDialog(containers, new OnContainerSelectedListener() {
                        @Override
                        public void onContainerSelected(Container selectedContainer) {
                            // Use the selected container to clone the shortcut
                            if (shortcut.cloneToContainer(selectedContainer)) {
                                Toast.makeText(context, "Shortcut cloned successfully.", Toast.LENGTH_SHORT).show();
                                loadShortcutsList(); // Reload the shortcuts to show the cloned one
                            } else {
                                Toast.makeText(context, "Failed to clone shortcut.", Toast.LENGTH_SHORT).show();
                            }
                        }
                    });
                }
                else if (itemId == R.id.shortcut_add_to_home_screen) {
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
                else if (itemId == R.id.shortcut_set_favorite) {
                    if (shortcut.getExtra("uuid").equals("")) shortcut.genUUID();  // ensure UUID
                    PreferenceManager.getDefaultSharedPreferences(context)
                            .edit().putString("favorite_uuid", shortcut.getExtra("uuid")).apply();
                    Toast.makeText(context, "Favorite set to " + shortcut.name, Toast.LENGTH_SHORT).show();
                }
                return true;
            });
            listItemMenu.show();
        }

        // Define the listener interface for selecting a container
        public interface OnContainerSelectedListener {
            void onContainerSelected(Container container);
        }

        private void showContainerSelectionDialog(ArrayList<Container> containers, OnContainerSelectedListener listener) {
            // Create an AlertDialog to show the list of containers
            AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
            builder.setTitle("Select a container");

            // Create an array of container names to display
            String[] containerNames = new String[containers.size()];
            for (int i = 0; i < containers.size(); i++) {
                containerNames[i] = containers.get(i).getName();
            }

            // Set up the list in the dialog
            builder.setItems(containerNames, (dialog, which) -> {
                // Call the listener when a container is selected
                listener.onContainerSelected(containers.get(which));
            });

            // Show the dialog
            builder.show();
        }






        private void runFromShortcut(Shortcut shortcut) {
            Activity activity = getActivity();

            if (!XrActivity.isEnabled(getContext())) {
                Intent intent = new Intent(activity, XServerDisplayActivity.class);
                intent.putExtra("container_id", shortcut.container.id);
                intent.putExtra("shortcut_path", shortcut.file.getPath());
                intent.putExtra("shortcut_name", shortcut.name); // Add this line to pass the shortcut name
                // Check if the shortcut has the disableXinput value; if not, default to false.
                String disableXinputValue = shortcut.getExtra("disableXinput", "0"); // Get value from shortcut or use "0" (false) by default
                intent.putExtra("disableXinput", disableXinputValue); // Use the actual value from the shortcut
                activity.startActivity(intent);
            }
            else XrActivity.openIntent(activity, shortcut.container.id, shortcut.file.getPath());
        }

        private void exportShortcutToFrontend(Shortcut shortcut) {
            // Check for a custom frontend export path in shared preferences
            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
            String uriString = sharedPreferences.getString("frontend_export_uri", null);

            File frontendDir;

            if (uriString != null) {
                // If custom URI is set, use it
                Uri folderUri = Uri.parse(uriString);
                DocumentFile pickedDir = DocumentFile.fromTreeUri(getContext(), folderUri);

                if (pickedDir == null || !pickedDir.canWrite()) {
                    Toast.makeText(getContext(), "Cannot write to the selected folder", Toast.LENGTH_SHORT).show();
                    return;
                }

                // Convert DocumentFile to a File object for further processing
                frontendDir = new File(FileUtils.getFilePathFromUri(getContext(), folderUri));
            } else {
                // Default to Downloads\Winlator\Frontend if no custom URI is set
                frontendDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Winlator/Frontend");
                if (!frontendDir.exists() && !frontendDir.mkdirs()) {
                    Toast.makeText(getContext(), "Failed to create default directory", Toast.LENGTH_SHORT).show();
                    return;
                }
            }


            // Check for FRONTEND_INSTRUCTIONS.txt
            File instructionsFile = new File(frontendDir, "FRONTEND_INSTRUCTIONS.txt");
            if (true) {
                try (FileWriter writer = new FileWriter(instructionsFile, false)) {
                    writer.write("Instructions for adding Winlator shortcuts to Frontends:\n\n");
                    writer.write("Daijisho:\n\n");
                    writer.write("1. Open Daijisho\n");
                    writer.write("2. Navigate to the Settings tab.\n");
                    writer.write("3. Navigate to Settings\\Library\n");
                    writer.write("4. Select, Import from Pegasus\n");
                    writer.write("5. Add the metadata.pegasus.txt file located in this directory (Downloads\\Winlator\\Frontend)\n");
                    writer.write("6. Set the Sync path to Downloads\\Winlator\\Frontend\n");
                    writer.write("7. Start your game!\n\n");
                    writer.write("Beacon:\n\n");
                    writer.write("1. Navigate to Settings\n");
                    writer.write("2. Click the + Icon\n");
                    writer.write("3. Set the following values:\n\n");
                    writer.write("Platform Type: Custom\n");
                    writer.write("Name: Windows (or Winlator, whatever you prefer)\n");
                    writer.write("Short name: windows\n");
                    writer.write("Player app: Select Winlator Cmod (or whichever fork you are using that has adopted this code)\n");
                    writer.write("ROMs folder: Use Android FilePicker to select the Downloads\\Winlator\\Frontend directory\n");
                    writer.write("Expand Advanced:\n");
                    writer.write("File handling: Default\n");
                    writer.write("Use custom launch: True\n");
                    writer.write("am start command: am start -n " + "com.winlator.cmod/com.winlator.cmod.XServerDisplayActivity -e shortcut_path {file_path}\n\n");
                    writer.write("4. Click Save\n");
                    writer.write("5. Scan the folder for your game\n");
                    writer.write("6. Launch your game!\n");
                    writer.flush();
                    Log.d("ShortcutsFragment", "FRONTEND_INSTRUCTIONS.txt created successfully.");
                } catch (IOException e) {
                    Log.e("ShortcutsFragment", "Failed to create FRONTEND_INSTRUCTIONS.txt", e);
                }
            }

            // Check for metadata.pegasus.txt
            File metadataFile = new File(frontendDir, "metadata.pegasus.txt");
            try (FileWriter writer = new FileWriter(metadataFile, false)) {
                writer.write("collection: Windows\n");
                writer.write("shortname: windows\n");
                writer.write("extensions: desktop\n");
                writer.write("launch: am start\n");
                writer.write("  -n " + "com.winlator.cmod/com.winlator.cmod.XServerDisplayActivity\n");
                writer.write("  -e shortcut_path {file.path}\n");
                writer.write("  --activity-clear-task\n");
                writer.write("  --activity-clear-top\n");
                writer.write("  --activity-no-history\n");
                writer.flush();
                Log.d("ShortcutsFragment", "metadata.pegasus.txt created or updated successfully.");
            } catch (IOException e) {
                Log.e("ShortcutsFragment", "Failed to create or update metadata.pegasus.txt", e);
            }

            // Create the export file in the Frontend directory
            File exportFile = new File(frontendDir, shortcut.file.getName());

            boolean fileExists = exportFile.exists();
            boolean containerIdFound = false;

            try {
                List<String> lines = new ArrayList<>();

                // Read the original file or existing file if it exists
                try (BufferedReader reader = new BufferedReader(new FileReader(shortcut.file))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.startsWith("container_id=")) {
                            // Replace the existing container_id line
                            lines.add("container_id=" + shortcut.container.id);
                            containerIdFound = true;
                        } else {
                            lines.add(line);
                        }
                    }
                }

                // If no container_id was found, add it
                if (!containerIdFound) {
                    lines.add("container_id=" + shortcut.container.id);
                }

                // Write the contents to the export file
                try (FileWriter writer = new FileWriter(exportFile, false)) {
                    for (String line : lines) {
                        writer.write(line + "\n");
                    }
                    writer.flush();
                }

                Log.d("ShortcutsFragment", "Shortcut exported successfully to " + exportFile.getPath());

                // Determine the toast message
                String message;
                if (fileExists) {
                    message = "Frontend Shortcut Updated at " + exportFile.getPath();
                } else {
                    message = "Frontend Shortcut Exported to " + exportFile.getPath();
                }

                // Show a toast message to the user
                Toast.makeText(getContext(), message, Toast.LENGTH_LONG).show();

            } catch (IOException e) {
                Log.e("ShortcutsFragment", "Failed to export shortcut", e);
                Toast.makeText(getContext(), "Failed to export shortcut", Toast.LENGTH_LONG).show();
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

            Button resetPropertiesButton = dialog.findViewById(R.id.reset_properties);

            resetPropertiesButton.setOnClickListener(v -> {
                playtimePrefs.edit().remove(playtimeKey).remove(playCountKey).apply();
                Toast.makeText(getContext(), "Properties reset successfully.", Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            });

            dialog.show();
        }




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
        } catch (Exception e) { Log.e(TAG, "Failed to disable shortcut", e); }
    }

    public void updateShortcutOnScreen(String shortLabel,
                                       String longLabel,
                                       int    containerId,
                                       String shortcutPath,
                                       Icon   icon,
                                       String uuid) {

        ShortcutManager sm =
                androidx.core.content.ContextCompat.getSystemService(
                        requireContext(), ShortcutManager.class);

        if (sm == null) {                    // ⇦ grace-fully bail out on devices
            Log.w("ShortcutsFragment",       //    that don’t expose ShortcutManager
                    "ShortcutManager not available; cannot update pinned shortcut");
            return;
        }

        for (ShortcutInfo info : sm.getPinnedShortcuts()) {
            if (uuid.equals(info.getId())) {
                sm.updateShortcuts(Collections.singletonList(
                        buildScreenShortCut(shortLabel, longLabel,
                                containerId, shortcutPath, icon, uuid)));
                break;
            }
        }
    }

    private static boolean safeDelete(@Nullable File f) {
        try {
            return f != null && f.exists() && f.delete();
        } catch (Exception e) {
            Log.e("ShortcutsFragment", "Delete failed for: " + (f != null ? f.getAbsolutePath() : "null"), e);
            return false;
        }
    }

    /** Delete a sibling .lnk that matches the .desktop basename. */
    private boolean deletePairedLnkForShortcut(Shortcut shortcut) {
        if (shortcut == null || shortcut.file == null) return false;
        File dir = shortcut.file.getParentFile();
        if (dir == null) return false;

        String base = FileUtils.getBasename(shortcut.file.getName()); // strips extension
        File lnk = new File(dir, base + ".lnk");
        boolean deleted = safeDelete(lnk);
        if (deleted) {
            Log.d("ShortcutsFragment", "Paired .lnk removed: " + lnk.getAbsolutePath());
        } else if (lnk.exists()) {
            Log.w("ShortcutsFragment", "Paired .lnk exists but could not be removed: " + lnk.getAbsolutePath());
        } else {
            Log.d("ShortcutsFragment", "No paired .lnk found for " + base);
        }
        return deleted;
    }
}
