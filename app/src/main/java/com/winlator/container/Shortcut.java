    package com.winlator.container;

    import android.graphics.Bitmap;
    import android.graphics.BitmapFactory;
    import android.util.Log;

    import com.winlator.core.FileUtils;
    import com.winlator.core.StringUtils;

    import org.json.JSONException;
    import org.json.JSONObject;

    import java.io.File;
    import java.util.ArrayList;
    import java.util.Iterator;
    import java.util.List;
    import java.util.UUID;

    public class Shortcut {
        public final Container container;
        public final String name;
        public final String path;
        public final Bitmap icon;
        public final File file;
        public final File iconFile;
        public final String wmClass;
        private final JSONObject extraData = new JSONObject();

        public Shortcut(Container container, File file) {
            this.container = container;
            this.file = file;

            String execArgs = "";
            Bitmap icon = null;
            File iconFile = null;
            String wmClass = "";

            File[] iconDirs = {container.getIconsDir(64), container.getIconsDir(48), container.getIconsDir(32), container.getIconsDir(16)};
            String section = "";

            int index;
            for (String line : FileUtils.readLines(file)) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue; // Skip empty lines and comments
                if (line.startsWith("[")) {
                    section = line.substring(1, line.indexOf("]"));
                }
                else {
                    index = line.indexOf("=");
                    if (index == -1) continue;
                    String key = line.substring(0, index);
                    String value = line.substring(index+1);

                    if (section.equals("Desktop Entry")) {
                        if (key.equals("Exec")) execArgs = value;
                        if (key.equals("Icon")) {
                            for (File iconDir : iconDirs) {
                                iconFile = new File(iconDir, value+".png");
                                if (iconFile.isFile()){
                                    icon = BitmapFactory.decodeFile(iconFile.getPath());
                                    break;
                                }
                            }
                        }
                        if (key.equals("StartupWMClass")) wmClass = value;
                    }
                    else if (section.equals("Extra Data")) {
                        try {
                            extraData.put(key, value);
                        }
                        catch (JSONException e) {}
                    }
                }
            }

            this.name = FileUtils.getBasename(file.getPath());
            this.icon = icon;
            this.iconFile = iconFile;
            this.path = StringUtils.unescape(execArgs.substring(execArgs.lastIndexOf("wine ") + 4));
            this.wmClass = wmClass;

            Container.checkObsoleteOrMissingProperties(extraData);
        }

        public String getExtra(String name) {
            return getExtra(name, "");
        }

        public String getExtra(String name, String fallback) {
            try {
                return extraData.has(name) ? extraData.getString(name) : fallback;
            }
            catch (JSONException e) {
                return fallback;
            }
        }

        public void putExtra(String name, String value) {
            try {
                if (value != null) {
                    extraData.put(name, value);
                }
                else extraData.remove(name);
            }
            catch (JSONException e) {}
        }

        public void saveData() {
            String content = "[Desktop Entry]\n";
            for (String line : FileUtils.readLines(file)) {
                if (line.contains("[Extra Data]")) break;
                if (!line.contains("[Desktop Entry]") && !line.isEmpty()) content += line + "\n";
            }

            if (extraData.length() > 0) {
                content += "\n[Extra Data]\n";
                Iterator<String> keys = extraData.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    try {
                        content += key + "=" + extraData.getString(key) + "\n";
                    } catch (JSONException e) {}
                }
            }

            // Verify that the file reference is correct
            if (!file.getName().endsWith(".desktop")) {
                Log.e("Shortcut", "Incorrect file reference before saving: " + file.getPath());
                return; // Prevent saving to an incorrect file
            }

            FileUtils.writeString(file, content);
        }


        public void genUUID() {
            if (getExtra("uuid").equals("")) {
                putExtra("uuid", UUID.randomUUID().toString());
                saveData();
            }
        }

        public boolean cloneToContainer(Container newContainer) {
            try {
                // Define the path for the new .desktop file in the new container
                File newShortcutFile = new File(newContainer.getDesktopDir(), this.file.getName());

                // Read the existing .desktop file
                ArrayList<String> lines = FileUtils.readLines(this.file);

                // Prepare the content for the new .desktop file with updated container_id
                StringBuilder updatedContent = new StringBuilder();
                boolean containerIdFound = false;

                for (String line : lines) {
                    if (line.startsWith("container_id:")) {
                        // Update the container_id to the new container
                        updatedContent.append("container_id:").append(newContainer.id).append("\n");
                        containerIdFound = true;
                    } else {
                        updatedContent.append(line).append("\n");
                    }
                }

                // If the container_id wasn't found in the original file, add it
                if (!containerIdFound) {
                    updatedContent.append("container_id:").append(newContainer.id).append("\n");
                }

                // Write the updated content to the new .desktop file
                FileUtils.writeString(newShortcutFile, updatedContent.toString());

                // Optionally copy the icon if it exists
                if (this.iconFile != null && this.iconFile.isFile()) {
                    File newIconFile = new File(newContainer.getIconsDir(64), this.iconFile.getName());
                    FileUtils.copy(this.iconFile, newIconFile);
                }

                return true;
            } catch (Exception e) {
                Log.e("Shortcut", "Failed to clone shortcut to new container", e);
                return false;
            }
        }



    }
