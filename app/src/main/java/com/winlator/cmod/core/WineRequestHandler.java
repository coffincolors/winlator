package com.winlator.cmod.core;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;

import com.winlator.cmod.box86_64.Box86_64PresetManager;
import com.winlator.cmod.container.Container;
import com.winlator.cmod.container.Shortcut;
import com.winlator.cmod.fexcore.FEXCoreManager;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class WineRequestHandler {
    private static final String TAG = "WineRequestHandler";

    abstract class RequestCodes {
        static final int OPEN_URL = 1;
        static final int GET_WINE_CLIPBOARD = 2;
        static final int SET_WINE_CLIPBAORD = 3;
        static final int GET_WINDOWS_PEB_DATA = 4;
    }

    private Context context;
    private ServerSocket serverSocket;
    private Container container;
    private EnvVars envVars;
    private Shortcut shortcut;
    private WineInfo wineInfo;

    public WineRequestHandler(Context context) {
        this.context = context;
    }

    public void start() {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            try {
                serverSocket = new ServerSocket(20000);
                while (true) {
                    Socket socket = serverSocket.accept();
                    DataInputStream inputStream = new DataInputStream(socket.getInputStream());
                    DataOutputStream outputStream = new DataOutputStream(socket.getOutputStream());
                    int requestCode = inputStream.readInt();
                    handleRequest(inputStream, outputStream, requestCode);
                }
            } catch (IOException e) {
                Log.e(TAG, "Server socket error", e);
            }
        });
    }

    public void stop() {
        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                Log.w(TAG, "Failed to close server socket", e);
            }
        }
    }

    public void handleRequest(DataInputStream inputStream, DataOutputStream outputStream, int requestCode) throws IOException {

        switch(requestCode) {
            case RequestCodes.OPEN_URL:
                openURL(inputStream, outputStream);
                break;
            case RequestCodes.GET_WINE_CLIPBOARD:
                getWineClipboard(inputStream, outputStream);
                break;
            case RequestCodes.SET_WINE_CLIPBAORD:
                setWineClipboard(inputStream, outputStream);
                break;
            case RequestCodes.GET_WINDOWS_PEB_DATA:
                getWindowsPebData(inputStream, outputStream);
                break;
        }
    }

    private void openURL(DataInputStream inputStream, DataOutputStream outputStream) throws IOException {
        int messageLength = inputStream.readInt();
        byte[] data = new byte[messageLength];
        inputStream.readFully(data);
        String url = new String(data, "UTF-8");
        Log.d("WineRequestHandler", "Received request code OPEN_URL with url " + url);
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        context.startActivity(intent);
    }

    private void getWineClipboard(DataInputStream inputStream, DataOutputStream outputStream) throws IOException {
        String clipboardData = "";
        int format = inputStream.readInt();
        int size = inputStream.readInt();
        byte[] data = new byte[size];
        inputStream.readFully(data);
        if (format == 13)  {
            clipboardData = new String(data, StandardCharsets.UTF_16LE);
            clipboardData = clipboardData.replace("\0", "");
            ClipboardManager clpm = (ClipboardManager)context.getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clipData = ClipData.newPlainText("", clipboardData);
            clpm.setPrimaryClip(clipData);
        }
        Log.d("WineRequestHandler", "Received request code GET_WINE_CLIPBOARD with format " + format + " and size " + size);
    }

    private void setWineClipboard(DataInputStream inputStream, DataOutputStream outputStream) throws IOException {
        int format = 13;
        ClipboardManager clipboardManager = (ClipboardManager)context.getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clipData = clipboardManager.getPrimaryClip();
        String clipText;
        if (clipData != null) {
            ClipData.Item item = clipData.getItemAt(0);
            clipText = item.getText().toString();
        }
        else {
            clipText = "";
        }
        Log.d("WineRequestHandler", "Received request code SET_WINE_CLIPBOARD for clipboard " + clipText);
        clipText = clipText + "\0";
        byte[] dataByte = clipText.getBytes(StandardCharsets.UTF_16LE);
        int size = dataByte.length;
        outputStream.writeInt(format);
        outputStream.writeInt(size);
        outputStream.write(dataByte);
    }

    private void getWindowsPebData(DataInputStream inputStream, DataOutputStream outputStream) throws IOException {
        int is_wow64 = inputStream.readInt();
        int is_arm64ec = inputStream.readInt();
        int execNameLength = inputStream.readInt();
        byte[] data = new byte[execNameLength];
        inputStream.readFully(data);
        String execName = new String(data, StandardCharsets.UTF_16LE);

        // List of common system processes to ignore for logging
        String[] ignoredProcesses = {
                "start.exe", "services.exe", "winedevice.exe", "wineboot.exe", "explorer.exe",
                "plugplay.exe", "svchost.exe", "rpcss.exe", "winhandler.exe", "wfm.exe",
                "tabtip.exe", "winemenubuilder.exe"
        };

        boolean shouldDump = true;
        for (String ignored : ignoredProcesses) {
            if (execName.equals(ignored)) {
                shouldDump = false;
                break;
            }
        }

        if (shouldDump && !execName.isEmpty()) {
            createSettingsDump(execName, is_wow64, is_arm64ec);
        }
    }

    private void createSettingsDump(String exec_name, int is_wow64, int is_arm64ec) throws IOException {
        File dumpDirectory = new File(android.os.Environment.getExternalStorageDirectory(), "Download/Winlator/settings_dump");
        if (!dumpDirectory.exists()) {
            dumpDirectory.mkdirs();
        }

        String screenSize, dxWrapper, dxwrapperConfig, graphicsDriverConfig, box64Preset, box64Version,
                startupSelection, winComponents, audioDriver, ddraWrapper, emulator,
                fexcoreVersion, fexcorePreset, cpuList;

        if (shortcut != null) {
            screenSize = shortcut.getExtra("screenSize", this.container.getScreenSize());
            dxWrapper = shortcut.getExtra("dxwrapper", this.container.getDXWrapper());
            dxwrapperConfig = shortcut.getExtra("dxwrapperConfig", this.container.getDXWrapperConfig());
            graphicsDriverConfig = shortcut.getExtra("graphicsDriverConfig", this.container.getGraphicsDriverConfig());
            box64Preset = shortcut.getExtra("box64Preset", this.container.getBox64Preset());
            box64Version = shortcut.getExtra("box64Version", this.container.getBox64Version());
            startupSelection = shortcut.getExtra("startupSelection", String.valueOf(this.container.getStartupSelection()));
            winComponents = shortcut.getExtra("wincomponents", this.container.getWinComponents());
            audioDriver = shortcut.getExtra("audioDriver", this.container.getAudioDriver());
            ddraWrapper = shortcut.getExtra("ddrawrapper", this.container.getDDrawWrapper());
            emulator = shortcut.getExtra("emulator", this.container.getEmulator());
            fexcoreVersion = shortcut.getExtra("fexcoreVersion", this.container.getFEXCoreVersion());
            fexcorePreset = FEXCoreManager.printFEXCoreSettings(context, this.shortcut);
            cpuList = shortcut.getExtra("cpuList", this.container.getCPUList());
        } else {
            screenSize = container.getScreenSize();
            dxWrapper = container.getDXWrapper();
            dxwrapperConfig = container.getDXWrapperConfig();
            graphicsDriverConfig = container.getGraphicsDriverConfig();
            box64Preset = container.getBox64Preset();
            box64Version = container.getBox64Version();
            startupSelection = String.valueOf(container.getStartupSelection());
            winComponents = container.getWinComponents();
            audioDriver = container.getAudioDriver();
            ddraWrapper = container.getDDrawWrapper();
            emulator = container.getEmulator();
            fexcoreVersion = container.getFEXCoreVersion();
            fexcorePreset = FEXCoreManager.printFEXCoreSettings(context, container);
            cpuList = container.getCPUList();
        }

        String wineVersion = container.getWineVersion();
        File dumpFile = new File(dumpDirectory, exec_name + "_settings_dump.txt");

        try (java.io.FileWriter writer = new java.io.FileWriter(dumpFile);
             java.io.BufferedWriter bw = new java.io.BufferedWriter(writer)) {
            bw.write(String.format("Executable: %s\n", exec_name));
            if (is_arm64ec == 1) {
                bw.write(String.format("Architecture: %s\n", "aarch64"));
            } else if (is_wow64 == 1) {
                bw.write(String.format("Architecture: %s\n", "x86"));
            } else {
                bw.write(String.format("Architecture: %s\n", "x86_64"));
            }
            bw.write(String.format("Device: %s\n", android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL));
            bw.write(String.format("GPU: %s\n", GPUInformation.getRenderer()));
            bw.write(String.format("Stock Driver Version: %s\n", GPUInformation.getVersion()));
            bw.write(String.format("graphicsDriverConfig: %s\n", graphicsDriverConfig));
            bw.write(String.format("wineVersion: %s\n", wineVersion));
            bw.write(String.format("emulator: %s\n", emulator));
            bw.write(String.format("emulator64: %s\n", wineInfo.isArm64EC() ? "fexcore" : "box64"));
            bw.write(String.format("box64Version: %s\n", box64Version));
            bw.write(String.format("box64Preset: [%s]\n", Box86_64PresetManager.getEnvVars("box64", context, box64Preset).print()));
            if (wineInfo.isArm64EC()) {
                bw.write(String.format("fexcoreVersion: %s\n", fexcoreVersion));
                bw.write(String.format("fexcorePreset: %s\n", fexcorePreset));
            }
            bw.write(String.format("screenSize: %s\n", screenSize));
            bw.write(String.format("dxwrapper: %s\n", dxWrapper.toUpperCase()));
            bw.write(String.format("dxwrapperConfig: %s\n", dxwrapperConfig));
            bw.write(String.format("startupSelection: %s\n", startupSelection));
            bw.write(String.format("wincomponents: %s\n", winComponents));
            bw.write(String.format("audioDriver: %s\n", audioDriver.toUpperCase()));
            bw.write(String.format("ddrawrapper: %s\n", ddraWrapper.toUpperCase()));
            bw.write(String.format("cpuList: %s\n", cpuList));
            bw.write(String.format("EnvVars: [%s]", envVars.print()));
        }
    }

    public void setContainer(Container container) {
        this.container = container;
    }

    public void setShortcut(Shortcut shortcut) {
        this.shortcut = shortcut;
    }

    public void setEnvVars(EnvVars envVars) {
        this.envVars = envVars;
    }

    public void setWineInfo(WineInfo wineInfo) {
        this.wineInfo = wineInfo;
    }
}
