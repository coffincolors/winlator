package com.winlator.container;

import android.content.Context;
import android.os.Handler;
import android.util.Log;

import com.winlator.R;
import com.winlator.core.Callback;
import com.winlator.core.FileUtils;
import com.winlator.core.OnExtractFileListener;
import com.winlator.core.TarCompressorUtils;
import com.winlator.core.WineInfo;
import com.winlator.xenvironment.ImageFs;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.concurrent.Executors;

public class ContainerManager {
    private final ArrayList<Container> containers = new ArrayList<>();
    private int maxContainerId = 0;
    private final File homeDir;
    private final Context context;

    public ContainerManager(Context context) {
        this.context = context;
        File rootDir = ImageFs.find(context).getRootDir();
        homeDir = new File(rootDir, "home");
        loadContainers();
    }

    public ArrayList<Container> getContainers() {
        return containers;
    }

    private void loadContainers() {
        containers.clear();
        maxContainerId = 0;

        try {
            File[] files = homeDir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        if (file.getName().startsWith(ImageFs.USER+"-")) {
                            Container container = new Container(Integer.parseInt(file.getName().replace(ImageFs.USER+"-", "")), this);

                            container.setRootDir(new File(homeDir, ImageFs.USER+"-"+container.id));
                            JSONObject data = new JSONObject(FileUtils.readString(container.getConfigFile()));
                            container.loadData(data);
                            containers.add(container);
                            maxContainerId = Math.max(maxContainerId, container.id);
                        }
                    }
                }
            }
        }
        catch (JSONException e) {}
    }

    public void activateContainer(Container container) {
        container.setRootDir(new File(homeDir, ImageFs.USER+"-"+container.id));
        File file = new File(homeDir, ImageFs.USER);
        file.delete();
        FileUtils.symlink("./"+ImageFs.USER+"-"+container.id, file.getPath());
    }

    public void createContainerAsync(final JSONObject data, Callback<Container> callback) {
        final Handler handler = new Handler();
        Executors.newSingleThreadExecutor().execute(() -> {
            final Container container = createContainer(data);
            handler.post(() -> callback.call(container));
        });
    }

    public void duplicateContainerAsync(Container container, Runnable callback) {
        final Handler handler = new Handler();
        Executors.newSingleThreadExecutor().execute(() -> {
            duplicateContainer(container);
            handler.post(callback);
        });
    }

    public void removeContainerAsync(Container container, Runnable callback) {
        final Handler handler = new Handler();
        Executors.newSingleThreadExecutor().execute(() -> {
            removeContainer(container);
            handler.post(callback);
        });
    }

    private Container createContainer(JSONObject data) {
        try {
            int id = maxContainerId + 1;
            data.put("id", id);

            File containerDir = new File(homeDir, ImageFs.USER+"-"+id);
            if (!containerDir.mkdirs()) return null;

            Container container = new Container(id, this);
            container.setRootDir(containerDir);
            container.loadData(data);

            boolean isMainWineVersion = !data.has("wineVersion") || WineInfo.isMainWineVersion(data.getString("wineVersion"));
            if (!isMainWineVersion) container.setWineVersion(data.getString("wineVersion"));

            if (!extractContainerPatternFile(container.getWineVersion(), containerDir, null)) {
                FileUtils.delete(containerDir);
                return null;
            }

//            // Extract the selected graphics driver files
//            String driverVersion = container.getGraphicsDriverVersion();
//            if (!extractGraphicsDriverFiles(driverVersion, containerDir, null)) {
//                FileUtils.delete(containerDir);
//                return null;
//            }

            container.saveData();
            maxContainerId++;
            containers.add(container);
            return container;
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return null;
    }


    private void duplicateContainer(Container srcContainer) {
        int id = maxContainerId + 1;

        File dstDir = new File(homeDir, ImageFs.USER+"-"+id);
        if (!dstDir.mkdirs()) return;

        if (!FileUtils.copy(srcContainer.getRootDir(), dstDir, (file) -> FileUtils.chmod(file, 0771))) {
            FileUtils.delete(dstDir);
            return;
        }

        Container dstContainer = new Container(id, this);
        dstContainer.setRootDir(dstDir);
        dstContainer.setName(srcContainer.getName()+" ("+context.getString(R.string.copy)+")");
        dstContainer.setScreenSize(srcContainer.getScreenSize());
        dstContainer.setEnvVars(srcContainer.getEnvVars());
        dstContainer.setCPUList(srcContainer.getCPUList());
        dstContainer.setCPUListWoW64(srcContainer.getCPUListWoW64());
        dstContainer.setGraphicsDriver(srcContainer.getGraphicsDriver());
        dstContainer.setDXWrapper(srcContainer.getDXWrapper());
        dstContainer.setDXWrapperConfig(srcContainer.getDXWrapperConfig());
        dstContainer.setAudioDriver(srcContainer.getAudioDriver());
        dstContainer.setWinComponents(srcContainer.getWinComponents());
        dstContainer.setDrives(srcContainer.getDrives());
        dstContainer.setShowFPS(srcContainer.isShowFPS());
        dstContainer.setWoW64Mode(srcContainer.isWoW64Mode());
        dstContainer.setStartupSelection(srcContainer.getStartupSelection());
        dstContainer.setBox86Preset(srcContainer.getBox86Preset());
        dstContainer.setBox64Preset(srcContainer.getBox64Preset());
        dstContainer.setDesktopTheme(srcContainer.getDesktopTheme());
        dstContainer.saveData();

        maxContainerId++;
        containers.add(dstContainer);
    }

    private void removeContainer(Container container) {
        if (FileUtils.delete(container.getRootDir())) containers.remove(container);
    }

    public ArrayList<Shortcut> loadShortcuts() {
        ArrayList<Shortcut> shortcuts = new ArrayList<>();
        for (Container container : containers) {
            File desktopDir = container.getDesktopDir();
            File[] files = desktopDir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.getName().endsWith(".desktop")) shortcuts.add(new Shortcut(container, file));
                }
            }
        }

        shortcuts.sort(Comparator.comparing(a -> a.name));
        return shortcuts;
    }

    public int getNextContainerId() {
        return maxContainerId + 1;
    }

    public Container getContainerById(int id) {
        for (Container container : containers) if (container.id == id) return container;
        return null;
    }

    private void extractCommonDlls(String srcName, String dstName, JSONObject commonDlls, File containerDir, OnExtractFileListener onExtractFileListener) throws JSONException {
        File srcDir = new File(ImageFs.find(context).getRootDir(), "/opt/wine/lib/wine/"+srcName);
        JSONArray dlnames = commonDlls.getJSONArray(dstName);

        for (int i = 0; i < dlnames.length(); i++) {
            String dlname = dlnames.getString(i);
            File dstFile = new File(containerDir, ".wine/drive_c/windows/"+dstName+"/"+dlname);
            if (onExtractFileListener != null) {
                dstFile = onExtractFileListener.onExtractFile(dstFile, 0);
                if (dstFile == null) continue;
            }
            FileUtils.copy(new File(srcDir, dlname), dstFile);
        }
    }

    public boolean extractContainerPatternFile(String wineVersion, File containerDir, OnExtractFileListener onExtractFileListener) {
        if (WineInfo.isMainWineVersion(wineVersion)) {
            boolean result = TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, context, "container_pattern.tzst", containerDir, onExtractFileListener);

            if (result) {
                try {
                    JSONObject commonDlls = new JSONObject(FileUtils.readString(context, "common_dlls.json"));
                    extractCommonDlls("x86_64-windows", "system32", commonDlls, containerDir, onExtractFileListener);
                    extractCommonDlls("i386-windows", "syswow64", commonDlls, containerDir, onExtractFileListener);
                }
                catch (JSONException e) {
                    return false;
                }
            }

            return result;
        }
        else {
            File installedWineDir = ImageFs.find(context).getInstalledWineDir();
            WineInfo wineInfo = WineInfo.fromIdentifier(context, wineVersion);
            String suffix = wineInfo.fullVersion()+"-"+wineInfo.getArch();
            File file = new File(installedWineDir, "container-pattern-"+suffix+".tzst");
            return TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, file, containerDir, onExtractFileListener);
        }
    }

    public boolean extractGraphicsDriverFiles(String driverVersion, File containerDir, OnExtractFileListener onExtractFileListener) {
        // Instead of using containerDir, point directly to the root of imagefs
        File imageFsRootDir = ImageFs.find(context).getRootDir(); // Get the root directory of imagefs
        File installedDriverDir = ImageFs.find(context).getInstalledWineDir();
        String fileName = "turnip-" + driverVersion + ".tzst";
        File file = new File(installedDriverDir, fileName);

        // Log the correct intended paths
        Log.d("ContainerManager", "Extracting to imageFsRootDir: " + imageFsRootDir.getAbsolutePath());

        if (!file.exists()) {
            Log.e("ContainerManager", "Driver file does not exist: " + file.getAbsolutePath());
            return false;
        }

        // Perform the extraction to the correct root directory
        boolean result = TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, file, imageFsRootDir, onExtractFileListener);

        // Log the result of the extraction
        if (result) {
            Log.d("ContainerManager", "Extraction succeeded for version: " + driverVersion);
        } else {
            Log.e("ContainerManager", "Extraction failed for version: " + driverVersion);
        }

        // Log the contents of the root directory after extraction
        logDirectoryContents(imageFsRootDir);

        return result;
    }


    private void logDirectoryContents(File dir) {
        Log.d("ContainerManager", "Directory: " + dir.getAbsolutePath());
        if (dir.isDirectory()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File file : files) {
                    Log.d("ContainerManager", "File/Dir: " + file.getAbsolutePath() + " (" + (file.isDirectory() ? "Dir" : "File") + ")");
                }
            }
        }
    }

    public Container getContainerForShortcut(Shortcut shortcut) {
        // Search for the container by its ID
        for (Container container : containers) {
            if (container.id == shortcut.getContainerId()) {
                return container;
            }
        }
        return null;  // Return null if no matching container is found
    }



}
