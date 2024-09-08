package com.winlator;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.Spinner;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.preference.PreferenceManager;

import com.google.android.material.navigation.NavigationView;
import com.winlator.box86_64.rc.RCFile;
import com.winlator.box86_64.rc.RCManager;
import com.winlator.container.Container;
import com.winlator.container.ContainerManager;
import com.winlator.container.Shortcut;
import com.winlator.contentdialog.ContentDialog;
import com.winlator.contentdialog.DXVKConfigDialog;
import com.winlator.contentdialog.DebugDialog;
import com.winlator.contentdialog.VKD3DConfigDialog;
import com.winlator.contents.ContentProfile;
import com.winlator.contents.ContentsManager;
import com.winlator.core.AppUtils;
import com.winlator.core.DefaultVersion;
import com.winlator.core.EnvVars;
import com.winlator.core.FileUtils;
import com.winlator.core.GPUInformation;
import com.winlator.core.KeyValueSet;
import com.winlator.core.OnExtractFileListener;
import com.winlator.core.PreloaderDialog;
import com.winlator.core.ProcessHelper;
import com.winlator.core.StringUtils;
import com.winlator.core.TarCompressorUtils;
import com.winlator.core.WineInfo;
import com.winlator.core.WineRegistryEditor;
import com.winlator.core.WineStartMenuCreator;
import com.winlator.core.WineThemeManager;
import com.winlator.core.WineUtils;
import com.winlator.inputcontrols.ControlsProfile;
import com.winlator.inputcontrols.ExternalController;
import com.winlator.inputcontrols.InputControlsManager;
import com.winlator.math.Mathf;
import com.winlator.math.XForm;
import com.winlator.renderer.GLRenderer;
import com.winlator.widget.FrameRating;
import com.winlator.widget.InputControlsView;
import com.winlator.widget.MagnifierView;
import com.winlator.widget.TouchpadView;
import com.winlator.widget.XServerView;
import com.winlator.winhandler.TaskManagerDialog;
import com.winlator.winhandler.WinHandler;
import com.winlator.xconnector.UnixSocketConfig;
import com.winlator.xenvironment.ImageFs;
import com.winlator.xenvironment.XEnvironment;
import com.winlator.xenvironment.components.ALSAServerComponent;
import com.winlator.xenvironment.components.GuestProgramLauncherComponent;
import com.winlator.xenvironment.components.LinuxCommandExplorer;
import com.winlator.xenvironment.components.NetworkInfoUpdateComponent;
import com.winlator.xenvironment.components.PulseAudioComponent;
import com.winlator.xenvironment.components.SysVSharedMemoryComponent;
import com.winlator.xenvironment.components.VirGLRendererComponent;
import com.winlator.xenvironment.components.XServerComponent;
import com.winlator.xserver.Pointer;
import com.winlator.xserver.Property;
import com.winlator.xserver.ScreenInfo;
import com.winlator.xserver.Window;
import com.winlator.xserver.WindowManager;
import com.winlator.xserver.XServer;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.concurrent.Executors;


public class XServerDisplayActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener {
    private XServerView xServerView;
    private InputControlsView inputControlsView;
    private TouchpadView touchpadView;
    private XEnvironment environment;
    private DrawerLayout drawerLayout;
    private ContainerManager containerManager;
    private Container container;
    private XServer xServer;
    private InputControlsManager inputControlsManager;
    private ImageFs imageFs;
    private FrameRating frameRating;
    private Runnable editInputControlsCallback;
    private Shortcut shortcut;
    private String graphicsDriver = Container.DEFAULT_GRAPHICS_DRIVER;
    private String audioDriver = Container.DEFAULT_AUDIO_DRIVER;
    private String dxwrapper = Container.DEFAULT_DXWRAPPER;
    private KeyValueSet dxwrapperConfig;
    private WineInfo wineInfo;
    private final EnvVars envVars = new EnvVars();
    private boolean firstTimeBoot = false;
    private SharedPreferences preferences;
    private OnExtractFileListener onExtractFileListener;
    private final WinHandler winHandler = new WinHandler(this);
    private float globalCursorSpeed = 1.0f;
    private MagnifierView magnifierView;
    private DebugDialog debugDialog;
    private short taskAffinityMask = 0;
    private short taskAffinityMaskWoW64 = 0;
    private int frameRatingWindowId = -1;
    private boolean pointerCaptureRequested = false; // Flag to track if pointer capture was requested

    private final float[] xform = XForm.getInstance();
    private ContentsManager contentsManager;
    private boolean navigationFocused = false;

    private Runnable configChangedCallback = null;


    // Inside the XServerDisplayActivity class
    private SensorManager sensorManager;
    private Sensor gyroSensor;
    private ExternalController controller;

    // Playtime stats tracking
    private long startTime;
    private SharedPreferences playtimePrefs;
    private String shortcutName;
    private Handler handler;
    private Runnable savePlaytimeRunnable;
    private static final long SAVE_INTERVAL_MS = 1000;


    private LinuxCommandExplorer commandExplorer = null;

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (configChangedCallback != null) {
            configChangedCallback.run();
            configChangedCallback = null;
        }
    }


    private final SensorEventListener gyroListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
                float gyroX = event.values[0]; // Rotation around the X-axis
                float gyroY = event.values[1]; // Rotation around the Y-axis

                winHandler.updateGyroData(gyroX, gyroY); // Send gyro data to WinHandler
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
            // No action needed
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AppUtils.hideSystemUI(this);
        AppUtils.keepScreenOn(this);
        setContentView(R.layout.xserver_display_activity);

        final PreloaderDialog preloaderDialog = new PreloaderDialog(this);
        preferences = PreferenceManager.getDefaultSharedPreferences(this);

        // Initialize and SensorManager
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);

        // Get the gyroscope sensor
        gyroSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);

        boolean gyroEnabled = preferences.getBoolean("gyro_enabled", true);

        if (gyroEnabled) {
            // Register the sensor event listener
            sensorManager.registerListener(gyroListener, gyroSensor, SensorManager.SENSOR_DELAY_GAME);
        }

        // Initialize playtime tracking
        playtimePrefs = getSharedPreferences("playtime_stats", MODE_PRIVATE);
        shortcutName = getIntent().getStringExtra("shortcut_name");

        // Increment play count at the start of a session
        incrementPlayCount();


        // Record the start time
        startTime = System.currentTimeMillis();

        // Initialize handler for periodic saving
        handler = new Handler(Looper.getMainLooper());
        savePlaytimeRunnable = new Runnable() {
            @Override
            public void run() {
                savePlaytimeData();
                handler.postDelayed(this, SAVE_INTERVAL_MS);
            }
        };
        handler.postDelayed(savePlaytimeRunnable, SAVE_INTERVAL_MS);


        contentsManager = new ContentsManager(this);
        contentsManager.syncContents();

        drawerLayout = findViewById(R.id.DrawerLayout);

        drawerLayout.setOnApplyWindowInsetsListener((view, windowInsets) -> windowInsets.replaceSystemWindowInsets(0, 0, 0, 0));
        drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);

        NavigationView navigationView = findViewById(R.id.NavigationView);
        ProcessHelper.removeAllDebugCallbacks();
        boolean enableLogs = preferences.getBoolean("enable_wine_debug", false) || preferences.getBoolean("enable_box86_64_logs", false);
        if (enableLogs) ProcessHelper.addDebugCallback(debugDialog = new DebugDialog(this));
        Menu menu = navigationView.getMenu();
        menu.findItem(R.id.main_menu_logs).setVisible(enableLogs);
        if (XrActivity.isSupported()) menu.findItem(R.id.main_menu_magnifier).setVisible(false);
        navigationView.setNavigationItemSelectedListener(this);

        imageFs = ImageFs.find(this);

        String screenSize = Container.DEFAULT_SCREEN_SIZE;
        if (!isGenerateWineprefix()) {
            containerManager = new ContainerManager(this);

            // Log shortcut_path
            // Inside onCreate() method of XServerDisplayActivity

// Determine container ID
            int containerId = getIntent().getIntExtra("container_id", 0);

// If container_id is 0, read from the .desktop file
            String shortcutPath = getIntent().getStringExtra("shortcut_path");
            if (containerId == 0 && shortcutPath != null && !shortcutPath.isEmpty()) {
                File shortcutFile = new File(shortcutPath);
                containerId = parseContainerIdFromDesktopFile(shortcutFile);
                Log.d("XServerDisplayActivity", "Parsed Container ID from .desktop file: " + containerId);
            }

// Retrieve the container first
            container = containerManager.getContainerById(containerId);

            if (container == null) {
                Log.e("XServerDisplayActivity", "Failed to retrieve container with ID: " + containerId);
                finish();  // Gracefully exit the activity to avoid crashing
                return;
            }

// Now initialize the Shortcut after container is fully initialized
            if (shortcutPath != null && !shortcutPath.isEmpty()) {
                try {
                    shortcut = new Shortcut(container, new File(shortcutPath));
                } catch (Exception e) {
                    Log.e("XServerDisplayActivity", "Error initializing shortcut: ", e);
                    finish(); // Close the activity gracefully
                }
            }


            containerManager.activateContainer(container);

            taskAffinityMask = (short) ProcessHelper.getAffinityMask(container.getCPUList(true));
            taskAffinityMaskWoW64 = (short) ProcessHelper.getAffinityMask(container.getCPUListWoW64(true));
            firstTimeBoot = container.getExtra("appVersion").isEmpty();

            String wineVersion = container.getWineVersion();
            wineInfo = WineInfo.fromIdentifier(this, wineVersion);

            if (wineInfo != WineInfo.MAIN_WINE_VERSION) imageFs.setWinePath(wineInfo.path);



            graphicsDriver = container.getGraphicsDriver();
            audioDriver = container.getAudioDriver();
            dxwrapper = container.getDXWrapper();
            String dxwrapperConfig = container.getDXWrapperConfig();
            screenSize = container.getScreenSize();
            winHandler.setInputType((byte) container.getInputType());

            if (shortcut != null) {
                graphicsDriver = shortcut.getExtra("graphicsDriver", container.getGraphicsDriver());
                audioDriver = shortcut.getExtra("audioDriver", container.getAudioDriver());
                dxwrapper = shortcut.getExtra("dxwrapper", container.getDXWrapper());
                dxwrapperConfig = shortcut.getExtra("dxwrapperConfig", container.getDXWrapperConfig());
                screenSize = shortcut.getExtra("screenSize", container.getScreenSize());

                String inputType = shortcut.getExtra("inputType");
                if (!inputType.isEmpty()) winHandler.setInputType(Byte.parseByte(inputType));
            }

            if (dxwrapper.equals("dxvk")) this.dxwrapperConfig = DXVKConfigDialog.parseConfig(dxwrapperConfig);

            if (!wineInfo.isWin64()) {
                onExtractFileListener = (file, size) -> {
                    String path = file.getPath();
                    if (path.contains("system32/")) return null;
                    return new File(path.replace("syswow64/", "system32/"));
                };
            }
        }

        preloaderDialog.show(R.string.starting_up);

        inputControlsManager = new InputControlsManager(this);
        xServer = new XServer(new ScreenInfo(screenSize));
        xServer.setWinHandler(winHandler);
        boolean[] winStarted = {false};
        xServer.windowManager.addOnWindowModificationListener(new WindowManager.OnWindowModificationListener() {
            @Override
            public void onUpdateWindowContent(Window window) {
                if (!winStarted[0] && window.isApplicationWindow()) {
                    xServerView.getRenderer().setCursorVisible(true);

                    //setCustomCursor();

                    preloaderDialog.closeOnUiThread();
                    winStarted[0] = true;
                }

                if (window.id == frameRatingWindowId) frameRating.update();
            }

            @Override
            public void onModifyWindowProperty(Window window, Property property) {
                changeFrameRatingVisibility(window, property);
            }

            @Override
            public void onMapWindow(Window window) {
                assignTaskAffinity(window);
            }

            @Override
            public void onUnmapWindow(Window window) {
                changeFrameRatingVisibility(window, null);
            }
        });


        Runnable runnable = () -> {
            setupUI();

            Executors.newSingleThreadExecutor().execute(() -> {
                if (!isGenerateWineprefix()) {
                    setupWineSystemFiles();
                    extractGraphicsDriverFiles();
                    changeWineAudioDriver();
                }
                setupXEnvironment();
            });
        };

        if (xServer.screenInfo.height > xServer.screenInfo.width) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            configChangedCallback = runnable;
        } else
            runnable.run();


    }



    // Method to parse container_id from .desktop file
    private int parseContainerIdFromDesktopFile(File desktopFile) {
        int containerId = 0;
        if (desktopFile.exists()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(desktopFile))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("container_id:")) {
                        containerId = Integer.parseInt(line.split(":")[1].trim());
                        break;
                    }
                }
            } catch (IOException | NumberFormatException e) {
                Log.e("XServerDisplayActivity", "Error parsing container_id from .desktop file", e);
            }
        }
        return containerId;
    }

    // Inside XServerDisplayActivity class
    private void handleCapturedPointer(MotionEvent event) {

        boolean handled = false;

        // Update XServer pointer position
        float dx = event.getX();
        float dy = event.getY();

        xServer.injectPointerMoveDelta((int) dx, (int) dy);

        int actionButton = event.getActionButton();
        switch (event.getAction()) {
            case MotionEvent.ACTION_BUTTON_PRESS:
                if (actionButton == MotionEvent.BUTTON_PRIMARY) {
                    xServer.injectPointerButtonPress(Pointer.Button.BUTTON_LEFT);
                } else if (actionButton == MotionEvent.BUTTON_SECONDARY) {
                    xServer.injectPointerButtonPress(Pointer.Button.BUTTON_RIGHT);
                } else if (actionButton == MotionEvent.BUTTON_TERTIARY) {
                    xServer.injectPointerButtonPress(Pointer.Button.BUTTON_MIDDLE); // Handle middle mouse button press
                }
                handled = true;
                break;
            case MotionEvent.ACTION_BUTTON_RELEASE:
                if (actionButton == MotionEvent.BUTTON_PRIMARY) {
                    xServer.injectPointerButtonRelease(Pointer.Button.BUTTON_LEFT);
                } else if (actionButton == MotionEvent.BUTTON_SECONDARY) {
                    xServer.injectPointerButtonRelease(Pointer.Button.BUTTON_RIGHT);
                } else if (actionButton == MotionEvent.BUTTON_TERTIARY) {
                    xServer.injectPointerButtonRelease(Pointer.Button.BUTTON_MIDDLE); // Handle middle mouse button release
                }
                handled = true;
                break;
            case MotionEvent.ACTION_HOVER_MOVE:
                float[] transformedPoint = XForm.transformPoint(xform, event.getX(), event.getY());
                xServer.injectPointerMove((int)transformedPoint[0], (int)transformedPoint[1]);
                handled = true;
                break;
            case MotionEvent.ACTION_SCROLL:
                float scrollY = event.getAxisValue(MotionEvent.AXIS_VSCROLL);
                if (scrollY <= -1.0f) {
                    xServer.injectPointerButtonPress(Pointer.Button.BUTTON_SCROLL_DOWN);
                    xServer.injectPointerButtonRelease(Pointer.Button.BUTTON_SCROLL_DOWN);
                } else if (scrollY >= 1.0f) {
                    xServer.injectPointerButtonPress(Pointer.Button.BUTTON_SCROLL_UP);
                    xServer.injectPointerButtonRelease(Pointer.Button.BUTTON_SCROLL_UP);
                }
                handled = true;
                break;
        }
    }



//    private void setCustomCursor() {
//        View decorView = getWindow().getDecorView();
//        Bitmap transparentCursorBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.transparent_cursor);
//        PointerIcon transparentCursorIcon = PointerIcon.create(transparentCursorBitmap, 0, 0);
//        decorView.setPointerIcon(transparentCursorIcon);
//    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == MainActivity.EDIT_INPUT_CONTROLS_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            if (editInputControlsCallback != null) {
                editInputControlsCallback.run();
                editInputControlsCallback = null;
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        boolean gyroEnabled = preferences.getBoolean("gyro_enabled", true);

        if (gyroEnabled) {
            // Re-register the sensor listener when the activity is resumed
            sensorManager.registerListener(gyroListener, gyroSensor, SensorManager.SENSOR_DELAY_GAME);
        }

        if (environment != null) {
            xServerView.onResume();
            environment.onResume();
        }
        startTime = System.currentTimeMillis();
        handler.postDelayed(savePlaytimeRunnable, SAVE_INTERVAL_MS);
    }

    @Override
    public void onPause() {
        super.onPause();
        boolean gyroEnabled = preferences.getBoolean("gyro_enabled", true);

        if (gyroEnabled) {
            // Unregister the sensor listener when the activity is paused
            sensorManager.unregisterListener(gyroListener);
        }

        // Check if we are entering Picture-in-Picture mode
        if (!isInPictureInPictureMode()) {
            // Only pause environment and xServerView if not in PiP mode
            if (environment != null) {
                environment.onPause();
                xServerView.onPause();
            }
        }

        savePlaytimeData();
        handler.removeCallbacks(savePlaytimeRunnable);
    }


    private void savePlaytimeData() {
        long endTime = System.currentTimeMillis();
        long playtime = endTime - startTime;

        // Ensure that playtime is not negative
        if (playtime < 0) {
            playtime = 0;
        }

        SharedPreferences.Editor editor = playtimePrefs.edit();
        String playtimeKey = shortcutName + "_playtime";

        // Accumulate the playtime into totalPlaytime
        long totalPlaytime = playtimePrefs.getLong(playtimeKey, 0) + playtime;
        editor.putLong(playtimeKey, totalPlaytime);
        editor.apply();

        // Reset startTime to the current time for the next interval
        startTime = System.currentTimeMillis();
    }


    private void incrementPlayCount() {
        SharedPreferences.Editor editor = playtimePrefs.edit();
        String playCountKey = shortcutName + "_play_count";
        int playCount = playtimePrefs.getInt(playCountKey, 0) + 1;
        editor.putInt(playCountKey, playCount);
        editor.apply();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Unregister sensor listener to avoid memory leaks
        sensorManager.unregisterListener(gyroListener);
        if (environment != null) environment.stopEnvironmentComponents();
        winHandler.stop();

        savePlaytimeData(); // Save on destroy
        handler.removeCallbacks(savePlaytimeRunnable);
    }

    @Override
    protected void onStop() {
        super.onStop();
        savePlaytimeData();
        handler.removeCallbacks(savePlaytimeRunnable);
    }


    @Override
    public void onBackPressed() {
        if (environment != null) {
            if (!drawerLayout.isDrawerOpen(GravityCompat.START)) {
                drawerLayout.openDrawer(GravityCompat.START);
            } else drawerLayout.closeDrawers();
        }
    }

    @SuppressLint("SourceLockedOrientationActivity")
    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        final GLRenderer renderer = xServerView.getRenderer();
        switch (item.getItemId()) {
            case R.id.main_menu_keyboard:
                AppUtils.showKeyboard(this);
                drawerLayout.closeDrawers();
                break;
            case R.id.main_menu_input_controls:
                showInputControlsDialog();
                drawerLayout.closeDrawers();
                break;
            case R.id.main_menu_toggle_fullscreen:
                renderer.toggleFullscreen();
                drawerLayout.closeDrawers();
                touchpadView.toggleFullscreen();
                break;
            case R.id.main_menu_toggle_orientation:
                if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE)
                    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                else
                    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
                ControlsProfile profile = inputControlsView.getProfile();
                int id = profile == null ? -1 : profile.id;
                configChangedCallback = () -> {
                    if (profile != null) {
                        inputControlsManager = new InputControlsManager(this);
                        inputControlsManager.loadProfiles(true);
                        showInputControls(inputControlsManager.getProfile(id));
                    }
                };
                drawerLayout.closeDrawers();
                break;
            case R.id.main_menu_task_manager:
                (new TaskManagerDialog(this)).show();
                drawerLayout.closeDrawers();
                break;
            case R.id.main_menu_magnifier:
                if (magnifierView == null) {
                    final FrameLayout container = findViewById(R.id.FLXServerDisplay);
                    magnifierView = new MagnifierView(this);
                    magnifierView.setZoomButtonCallback((value) -> {
                        renderer.setMagnifierZoom(Mathf.clamp(renderer.getMagnifierZoom() + value, 1.0f, 3.0f));
                        magnifierView.setZoomValue(renderer.getMagnifierZoom());
                    });
                    magnifierView.setZoomValue(renderer.getMagnifierZoom());
                    magnifierView.setHideButtonCallback(() -> {
                        container.removeView(magnifierView);
                        magnifierView = null;
                    });
                    container.addView(magnifierView);
                }
                drawerLayout.closeDrawers();
                break;
            case R.id.main_menu_logs:
                debugDialog.show();
                drawerLayout.closeDrawers();
                break;
            case R.id.main_menu_pip:
                // Enter Picture-in-Picture mode
                enterPictureInPictureMode();
                drawerLayout.closeDrawers();
                break;
            case R.id.main_menu_touchpad_help:
                showTouchpadHelpDialog();
                commandExplorer = new LinuxCommandExplorer();
                commandExplorer.runCommandTests();
                break;
            case R.id.main_menu_exit:
                finish();
                break;
        }
        return true;
    }




    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        boolean cursorLock = preferences.getBoolean("cursor_lock", false);

        if (hasFocus && !pointerCaptureRequested && cursorLock) {
            // Ensure TouchpadView and other relevant views are focused
            touchpadView.setFocusable(View.FOCUSABLE);
            touchpadView.setFocusableInTouchMode(true);
            touchpadView.requestFocus();
            touchpadView.requestPointerCapture();

            touchpadView.setOnCapturedPointerListener(new View.OnCapturedPointerListener() {
                @Override
                public boolean onCapturedPointer(View view, MotionEvent event) {
                    handleCapturedPointer(event);
                    return true;
                }
            });

            pointerCaptureRequested = true; // Ensure this is only called once

        } else if (!hasFocus) {
            if (touchpadView != null) {
                touchpadView.releasePointerCapture();
                touchpadView.setOnCapturedPointerListener(null);
            }
        }
    }


    private void exit() {
        winHandler.stop();
        if (environment != null) environment.stopEnvironmentComponents();
        AppUtils.restartApplication(this);
    }

    private void setupWineSystemFiles() {
        String appVersion = String.valueOf(AppUtils.getVersionCode(this));
        String imgVersion = String.valueOf(imageFs.getVersion());
        boolean containerDataChanged = false;

        if (!container.getExtra("appVersion").equals(appVersion) || !container.getExtra("imgVersion").equals(imgVersion)) {
            applyGeneralPatches(container);
            container.putExtra("appVersion", appVersion);
            container.putExtra("imgVersion", imgVersion);
            containerDataChanged = true;
        }

        String dxwrapper = this.dxwrapper;
        if (dxwrapper.equals("dxvk"))
            dxwrapper = "dxvk-"+dxwrapperConfig.get("version");
        else if (dxwrapper.equals("vkd3d")) {
            if (dxwrapperConfig == null) {
                dxwrapperConfig = VKD3DConfigDialog.parseConfig(null); // Use default config if null
            }
            dxwrapper = "vkd3d-" + dxwrapperConfig.get("vkd3dVersion");
        }


        if (!dxwrapper.equals(container.getExtra("dxwrapper"))) {
            extractDXWrapperFiles(dxwrapper);
            container.putExtra("dxwrapper", dxwrapper);
            containerDataChanged = true;
        }

        if (dxwrapper.equals("cnc-ddraw"))
            envVars.put("CNC_DDRAW_CONFIG_FILE", "C:\\ProgramData\\cnc-ddraw\\ddraw.ini");

        String wincomponents = shortcut != null ? shortcut.getExtra("wincomponents", container.getWinComponents()) : container.getWinComponents();
        if (!wincomponents.equals(container.getExtra("wincomponents"))) {
            extractWinComponentFiles();
            container.putExtra("wincomponents", wincomponents);
            containerDataChanged = true;
        }

        String desktopTheme = container.getDesktopTheme();
        if (!(desktopTheme + "," + xServer.screenInfo).equals(container.getExtra("desktopTheme"))) {
            WineThemeManager.apply(this, new WineThemeManager.ThemeInfo(desktopTheme), xServer.screenInfo);
            container.putExtra("desktopTheme", desktopTheme + "," + xServer.screenInfo);
            containerDataChanged = true;
        }

        WineStartMenuCreator.create(this, container);
        WineUtils.createDosdevicesSymlinks(container);

        String startupSelection = String.valueOf(container.getStartupSelection());
        if (!startupSelection.equals(container.getExtra("startupSelection"))) {
            WineUtils.changeServicesStatus(container, container.getStartupSelection() != Container.STARTUP_SELECTION_NORMAL);
            container.putExtra("startupSelection", startupSelection);
            containerDataChanged = true;
        }

        if (containerDataChanged) container.saveData();
    }

    private void setupXEnvironment() {
        envVars.put("MESA_DEBUG", "silent");
        envVars.put("MESA_NO_ERROR", "1");
        envVars.put("WINEPREFIX", ImageFs.WINEPREFIX);

        boolean enableWineDebug = preferences.getBoolean("enable_wine_debug", false);
        String wineDebugChannels = preferences.getString("wine_debug_channels", SettingsFragment.DEFAULT_WINE_DEBUG_CHANNELS);
        envVars.put("WINEDEBUG", enableWineDebug && !wineDebugChannels.isEmpty() ? "+" + wineDebugChannels.replace(",", ",+") : "-all");

        String rootPath = imageFs.getRootDir().getPath();
        FileUtils.clear(imageFs.getTmpDir());

        GuestProgramLauncherComponent guestProgramLauncherComponent = new GuestProgramLauncherComponent();

        if (container != null) {
            if (container.getStartupSelection() == Container.STARTUP_SELECTION_AGGRESSIVE)
                winHandler.killProcess("services.exe");

            boolean wow64Mode = container.isWoW64Mode();
            String guestExecutable = wineInfo.getExecutable(this, wow64Mode) + " explorer /desktop=shell," + xServer.screenInfo + " " + getWineStartCommand();
            guestProgramLauncherComponent.setWoW64Mode(wow64Mode);
            guestProgramLauncherComponent.setGuestExecutable(guestExecutable);

            envVars.putAll(container.getEnvVars());
            if (shortcut != null) envVars.putAll(shortcut.getExtra("envVars"));
            if (!envVars.has("WINEESYNC")) envVars.put("WINEESYNC", "1");

            ArrayList<String> bindingPaths = new ArrayList<>();
            for (String[] drive : container.drivesIterator()) bindingPaths.add(drive[1]);
            guestProgramLauncherComponent.setBindingPaths(bindingPaths.toArray(new String[0]));
            guestProgramLauncherComponent.setBox86Preset(shortcut != null ? shortcut.getExtra("box86Preset", container.getBox86Preset()) : container.getBox86Preset());
            guestProgramLauncherComponent.setBox64Preset(shortcut != null ? shortcut.getExtra("box64Preset", container.getBox64Preset()) : container.getBox64Preset());
        }

        environment = new XEnvironment(this, imageFs);
        environment.addComponent(new SysVSharedMemoryComponent(xServer, UnixSocketConfig.createSocket(rootPath, UnixSocketConfig.SYSVSHM_SERVER_PATH)));
        environment.addComponent(new XServerComponent(xServer, UnixSocketConfig.createSocket(rootPath, UnixSocketConfig.XSERVER_PATH)));
        environment.addComponent(new NetworkInfoUpdateComponent());

        if (audioDriver.equals("alsa")) {
            envVars.put("ANDROID_ALSA_SERVER", UnixSocketConfig.ALSA_SERVER_PATH);
            envVars.put("ANDROID_ASERVER_USE_SHM", "true");
            environment.addComponent(new ALSAServerComponent(UnixSocketConfig.createSocket(rootPath, UnixSocketConfig.ALSA_SERVER_PATH)));
        } else if (audioDriver.equals("pulseaudio")) {
            envVars.put("PULSE_SERVER", UnixSocketConfig.PULSE_SERVER_PATH);
            environment.addComponent(new PulseAudioComponent(UnixSocketConfig.createSocket(rootPath, UnixSocketConfig.PULSE_SERVER_PATH)));
        }

        if (graphicsDriver.equals("virgl")) {
            environment.addComponent(new VirGLRendererComponent(xServer, UnixSocketConfig.createSocket(rootPath, UnixSocketConfig.VIRGL_SERVER_PATH)));
        }

        RCManager manager = new RCManager(this);
        manager.loadRCFiles();
        int rcfileId = shortcut == null ? container.getRCFileId() :
                Integer.parseInt(shortcut.getExtra("rcfileId", String.valueOf(container.getRCFileId())));
        RCFile rcfile = manager.getRcfile(rcfileId);
        File file = new File(container.getRootDir(), ".box64rc");
        String str = rcfile == null ? "" : rcfile.generateBox86_64rc();
        FileUtils.writeString(file, str);
        envVars.put("BOX64_RCFILE", file.getAbsolutePath());

        guestProgramLauncherComponent.setEnvVars(envVars);
        guestProgramLauncherComponent.setTerminationCallback((status) -> exit());
        environment.addComponent(guestProgramLauncherComponent);

        if (isGenerateWineprefix()) generateWineprefix();
        environment.startEnvironmentComponents();

        winHandler.start();
        envVars.clear();
        dxwrapperConfig = null;
    }

    private void setupUI() {
        FrameLayout rootView = findViewById(R.id.FLXServerDisplay);
        xServerView = new XServerView(this, xServer);
        final GLRenderer renderer = xServerView.getRenderer();
        renderer.setCursorVisible(false);

        if (shortcut != null) {
            if (shortcut.getExtra("forceFullscreen", "0").equals("1"))
                renderer.setForceFullscreenWMClass(shortcut.wmClass);
            renderer.setUnviewableWMClasses("explorer.exe");
        }

        xServer.setRenderer(renderer);
        rootView.addView(xServerView);

        globalCursorSpeed = preferences.getFloat("cursor_speed", 1.0f);
        touchpadView = new TouchpadView(this, xServer);
        touchpadView.setSensitivity(globalCursorSpeed);
        touchpadView.setFourFingersTapCallback(() -> {
            if (!drawerLayout.isDrawerOpen(GravityCompat.START)) {
                drawerLayout.openDrawer(GravityCompat.START);
            }
        });
        rootView.addView(touchpadView);

        inputControlsView = new InputControlsView(this);
        inputControlsView.setOverlayOpacity(preferences.getFloat("overlay_opacity", InputControlsView.DEFAULT_OVERLAY_OPACITY));
        inputControlsView.setTouchpadView(touchpadView);
        inputControlsView.setXServer(xServer);
        inputControlsView.setVisibility(View.GONE);
        rootView.addView(inputControlsView);

        if (container != null && container.isShowFPS()) {
            frameRating = new FrameRating(this);
            frameRating.setVisibility(View.GONE);
            rootView.addView(frameRating);
        }

        if (shortcut != null) {
            String controlsProfile = shortcut.getExtra("controlsProfile");
            if (!controlsProfile.isEmpty()) {
                ControlsProfile profile = inputControlsManager.getProfile(Integer.parseInt(controlsProfile));
                if (profile != null) showInputControls(profile);
            }
        }

        AppUtils.observeSoftKeyboardVisibility(drawerLayout, renderer::setScreenOffsetYRelativeToCursor);
    }

    private ActivityResultLauncher<Intent> controlsEitorActivityResultLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (editInputControlsCallback != null) {
                    editInputControlsCallback.run();
                    editInputControlsCallback = null;
                }
            }
    );

    private void showInputControlsDialog() {
        final ContentDialog dialog = new ContentDialog(this, R.layout.input_controls_dialog);
        dialog.setTitle(R.string.input_controls);
        dialog.setIcon(R.drawable.icon_input_controls);

        final Spinner sProfile = dialog.findViewById(R.id.SProfile);
        Runnable loadProfileSpinner = () -> {
            ArrayList<ControlsProfile> profiles = inputControlsManager.getProfiles(true);
            ArrayList<String> profileItems = new ArrayList<>();
            int selectedPosition = 0;
            profileItems.add("-- " + getString(R.string.disabled) + " --");
            for (int i = 0; i < profiles.size(); i++) {
                ControlsProfile profile = profiles.get(i);
                if (inputControlsView.getProfile() != null && profile.id == inputControlsView.getProfile().id)
                    selectedPosition = i + 1;
                profileItems.add(profile.getName());
            }

            sProfile.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, profileItems));
            sProfile.setSelection(selectedPosition);
        };
        loadProfileSpinner.run();

        final CheckBox cbRelativeMouseMovement = dialog.findViewById(R.id.CBRelativeMouseMovement);
        cbRelativeMouseMovement.setChecked(xServer.isRelativeMouseMovement());

        final CheckBox cbShowTouchscreenControls = dialog.findViewById(R.id.CBShowTouchscreenControls);
        cbShowTouchscreenControls.setChecked(inputControlsView.isShowTouchscreenControls());

        final Runnable updateProfile = () -> {
            int position = sProfile.getSelectedItemPosition();
            if (position > 0) {
                showInputControls(inputControlsManager.getProfiles().get(position - 1));
            }
            else hideInputControls();
        };

        dialog.findViewById(R.id.BTSettings).setOnClickListener((v) -> {
            int position = sProfile.getSelectedItemPosition();
            Intent intent = new Intent(this, MainActivity.class);
            intent.putExtra("edit_input_controls", true);
            intent.putExtra("selected_profile_id", position > 0 ? inputControlsManager.getProfiles().get(position - 1).id : 0);
            editInputControlsCallback = () -> {
                hideInputControls();
                inputControlsManager.loadProfiles(true);
                loadProfileSpinner.run();
                updateProfile.run();
            };
            startActivityForResult(intent, MainActivity.EDIT_INPUT_CONTROLS_REQUEST_CODE);
        });

        dialog.setOnConfirmCallback(() -> {
            xServer.setRelativeMouseMovement(cbRelativeMouseMovement.isChecked());
            inputControlsView.setShowTouchscreenControls(cbShowTouchscreenControls.isChecked());
            int position = sProfile.getSelectedItemPosition();
            if (position > 0) {
                showInputControls(inputControlsManager.getProfiles().get(position - 1));
            } else hideInputControls();
        });

        dialog.setOnCancelCallback(updateProfile::run);

        dialog.setCanceledOnTouchOutside(false);
        dialog.show();
    }

    private void showInputControls(ControlsProfile profile) {
        inputControlsView.setVisibility(View.VISIBLE);
        inputControlsView.requestFocus();
        inputControlsView.setProfile(profile);

        touchpadView.setSensitivity(profile.getCursorSpeed() * globalCursorSpeed);
        touchpadView.setPointerButtonRightEnabled(false);

        inputControlsView.invalidate();
    }

    private void hideInputControls() {
        inputControlsView.setShowTouchscreenControls(true);
        inputControlsView.setVisibility(View.GONE);
        inputControlsView.setProfile(null);

        touchpadView.setSensitivity(globalCursorSpeed);
        touchpadView.setPointerButtonLeftEnabled(true);
        touchpadView.setPointerButtonRightEnabled(true);

        inputControlsView.invalidate();
    }

    private void extractGraphicsDriverFiles() {
        String cacheId = graphicsDriver;
        String selectedDriverVersion = container.getGraphicsDriverVersion(); // Fetch the selected version

        // Adjust cacheId based on graphics driver
        if (graphicsDriver.equals("turnip")) {
            cacheId += "-" + DefaultVersion.TURNIP + "-" + DefaultVersion.ZINK;
        } else if (graphicsDriver.equals("virgl")) {
            cacheId += "-" + DefaultVersion.VIRGL;
        }

        boolean changed = !cacheId.equals(container.getExtra("graphicsDriver"));
        File rootDir = imageFs.getRootDir(); // Target the root directory of imagefs

        if (changed) {
            FileUtils.delete(new File(imageFs.getLib32Dir(), "libvulkan_freedreno.so"));
            FileUtils.delete(new File(imageFs.getLib64Dir(), "libvulkan_freedreno.so"));
            FileUtils.delete(new File(imageFs.getLib32Dir(), "libGL.so.1.7.0"));
            FileUtils.delete(new File(imageFs.getLib64Dir(), "libGL.so.1.7.0"));
            container.putExtra("graphicsDriver", cacheId);
            container.saveData();
        }

        if (graphicsDriver.equals("turnip")) {
            if (dxwrapper.equals("dxvk")) {
                DXVKConfigDialog.setEnvVars(this, dxwrapperConfig, envVars);
            } else if (dxwrapper.equals("vkd3d")) {
                VKD3DConfigDialog.setEnvVars(this, dxwrapperConfig, envVars);
            }

            envVars.put("GALLIUM_DRIVER", "zink");
            envVars.put("TU_OVERRIDE_HEAP_SIZE", "4096");
            if (!envVars.has("MESA_VK_WSI_PRESENT_MODE")) envVars.put("MESA_VK_WSI_PRESENT_MODE", "mailbox");
            envVars.put("vblank_mode", "0");

            if (!GPUInformation.isAdreno6xx(this)) {
                EnvVars userEnvVars = new EnvVars(container.getEnvVars());
                String tuDebug = userEnvVars.get("TU_DEBUG");
                if (!tuDebug.contains("sysmem"))
                    userEnvVars.put("TU_DEBUG", (!tuDebug.isEmpty() ? tuDebug + "," : "") + "sysmem");
                container.setEnvVars(userEnvVars.toString());
            }

            boolean useDRI3 = preferences.getBoolean("use_dri3", true);
            if (!useDRI3) {
                envVars.put("MESA_VK_WSI_PRESENT_MODE", "immediate");
                envVars.put("MESA_VK_WSI_DEBUG", "sw");
            }

            boolean extractionSucceeded = false;
            if (changed) {
                extractionSucceeded = TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, this, "graphics_driver/turnip-" + DefaultVersion.TURNIP + ".tzst", rootDir) &&
                        TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, this, "graphics_driver/zink-" + DefaultVersion.ZINK + ".tzst", rootDir);

                if (extractionSucceeded) {
                    Log.d("GraphicsDriverExtraction", "Extraction from .tzst files succeeded.");
                } else {
                    Log.e("GraphicsDriverExtraction", "Extraction from .tzst files failed, will attempt to use the contents directory.");
                }
            }

            if (!extractionSucceeded) {
                // Parse version string for the actual version number, removing "Turnip-"
                String normalizedVersion = selectedDriverVersion.replaceFirst("Turnip-", "");
                File contentsDir = new File(getFilesDir(), "contents");
                File turnipDir = new File(contentsDir, "Turnip/" + normalizedVersion + "/turnip");
                File zinkDir = new File(contentsDir, "Turnip/" + normalizedVersion + "/zink");

                Log.d("GraphicsDriverExtraction", "Checking for Turnip directory: " + turnipDir.getAbsolutePath());
                Log.d("GraphicsDriverExtraction", "Checking for Zink directory: " + zinkDir.getAbsolutePath());

                if (turnipDir.exists() && turnipDir.isDirectory()) {
                    Log.d("GraphicsDriverExtraction", "Driver directory found in contents: " + turnipDir.getAbsolutePath());
                    File libDir = new File(rootDir, "lib/aarch64-linux-gnu");
                    libDir.mkdirs(); // Ensure the target directory exists

                    File icdTargetDir = new File(rootDir, "usr/share/vulkan/icd.d"); // Define the target directory for the JSON file
                    icdTargetDir.mkdirs(); // Ensure the target directory exists

                    // Use FileUtils.copy to handle file and directory copying
                    for (File file : turnipDir.listFiles()) {
                        if (file.isFile()) {
                            if (file.getName().equals("freedreno_icd.aarch64.json")) {
                                File targetFile = new File(icdTargetDir, file.getName());
                                FileUtils.copy(file, targetFile);
                                Log.d("GraphicsDriverExtraction", "Moved " + file.getName() + " to " + icdTargetDir.getAbsolutePath());
                            } else {
                                File targetFile = new File(libDir, file.getName());
                                FileUtils.copy(file, targetFile);
                            }
                        } else if (file.isDirectory()) {
                            File targetDir = new File(libDir, file.getName());
                            FileUtils.copy(file, targetDir);
                        }
                    }

                    if (zinkDir.exists() && zinkDir.isDirectory()) {
                        FileUtils.copy(zinkDir, libDir); // Copy contents of 'zink' folder if exists
                    }
                    Log.d("GraphicsDriverExtraction", "Driver successfully installed from contents manager: " + selectedDriverVersion);
                    contentsManager.markGraphicsDriverInstalled(selectedDriverVersion); // Mark as installed
                } else {
                    Log.d("GraphicsDriverExtraction", "Driver directory not found in contents: " + turnipDir.getAbsolutePath());
                }
            }
        } else if (graphicsDriver.equals("virgl")) {
            envVars.put("GALLIUM_DRIVER", "virpipe");
            envVars.put("VIRGL_NO_READBACK", "true");
            envVars.put("VIRGL_SERVER_PATH", UnixSocketConfig.VIRGL_SERVER_PATH);
            envVars.put("MESA_EXTENSION_OVERRIDE", "-GL_EXT_vertex_array_bgra");
            envVars.put("MESA_GL_VERSION_OVERRIDE", "3.1");
            envVars.put("vblank_mode", "0");
            if (changed)
                TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, this, "graphics_driver/virgl-" + DefaultVersion.VIRGL + ".tzst", rootDir);
        }
    }




    private void showTouchpadHelpDialog() {
        ContentDialog dialog = new ContentDialog(this, R.layout.touchpad_help_dialog);
        dialog.setTitle(R.string.touchpad_help);
        dialog.setIcon(R.drawable.icon_help);
        dialog.findViewById(R.id.BTCancel).setVisibility(View.GONE);
        dialog.show();
    }

    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent event) {
        return winHandler.onGenericMotionEvent(event) || (!navigationFocused && touchpadView.onExternalMouseEvent(event)) || super.dispatchGenericMotionEvent(event);
    }




    private static final int RECAPTURE_DELAY_MS = 10000; // 10 seconds

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        // Check if the event is from a game controller
        boolean isFromGameController = (event.getSource() & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD ||
                (event.getSource() & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK;

        // Handle Back button press if not from a game controller
        if (event.getAction() == KeyEvent.ACTION_DOWN && event.getKeyCode() == KeyEvent.KEYCODE_BACK && !isFromGameController) {
            // Similar logic to onBackPressed, but only for non-controller events
            if (environment != null) {
                if (!drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    drawerLayout.openDrawer(GravityCompat.START);
                } else {
                    drawerLayout.closeDrawers();
                }
                return true; // Indicate that the event was handled
            }
        }

        // Existing handling logic
        if (event.getAction() == KeyEvent.ACTION_DOWN && event.getKeyCode() == KeyEvent.KEYCODE_VOLUME_DOWN) {
            // Release pointer capture when Volume Down key is pressed
            if (touchpadView != null && pointerCaptureRequested) {
                touchpadView.releasePointerCapture();
                touchpadView.setOnCapturedPointerListener(null);
                pointerCaptureRequested = false;

                // Show toast message for pointer release
                AppUtils.showToast(this, "Pointer capture released for 10 seconds");

                // Schedule recapture after 10 seconds
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    if (touchpadView != null) {
                        touchpadView.requestPointerCapture();
                        touchpadView.setOnCapturedPointerListener(new View.OnCapturedPointerListener() {
                            @Override
                            public boolean onCapturedPointer(View view, MotionEvent event) {
                                handleCapturedPointer(event);
                                return true;
                            }
                        });
                        pointerCaptureRequested = true;

                        // Show toast message for pointer recapture
                        AppUtils.showToast(this, "Pointer re-captured. If not working, press again to release and re-capture");
                    }
                }, RECAPTURE_DELAY_MS);

                return true; // Indicate that the event was handled
            }
        }

        return (!inputControlsView.onKeyEvent(event) && !winHandler.onKeyEvent(event) && xServer.keyboard.onKeyEvent(event)) ||
                (!ExternalController.isGameController(event.getDevice()) && super.dispatchKeyEvent(event));
    }



    public InputControlsView getInputControlsView() {
        return inputControlsView;
    }

    private void generateWineprefix() {
        Intent intent = getIntent();

        final File rootDir = imageFs.getRootDir();
        final File installedWineDir = imageFs.getInstalledWineDir();
        wineInfo = intent.getParcelableExtra("wine_info");
        envVars.put("WINEARCH", wineInfo.isWin64() ? "win64" : "win32");
        imageFs.setWinePath(wineInfo.path);

        final File containerPatternDir = new File(installedWineDir, "/preinstall/container-pattern");
        if (containerPatternDir.isDirectory()) FileUtils.delete(containerPatternDir);
        containerPatternDir.mkdirs();

        File linkFile = new File(rootDir, ImageFs.HOME_PATH);
        linkFile.delete();
        FileUtils.symlink(".." + FileUtils.toRelativePath(rootDir.getPath(), containerPatternDir.getPath()), linkFile.getPath());

        GuestProgramLauncherComponent guestProgramLauncherComponent = environment.getComponent(GuestProgramLauncherComponent.class);
        guestProgramLauncherComponent.setGuestExecutable(wineInfo.getExecutable(this, false) + " explorer /desktop=shell," + Container.DEFAULT_SCREEN_SIZE + " winecfg");

        final PreloaderDialog preloaderDialog = new PreloaderDialog(this);
        guestProgramLauncherComponent.setTerminationCallback((status) -> Executors.newSingleThreadExecutor().execute(() -> {
            if (status > 0) {
                AppUtils.showToast(this, R.string.unable_to_install_wine);
                FileUtils.delete(new File(installedWineDir, "/preinstall"));
                AppUtils.restartApplication(this);
                return;
            }

            preloaderDialog.showOnUiThread(R.string.finishing_installation);
            FileUtils.writeString(new File(rootDir, ImageFs.WINEPREFIX + "/.update-timestamp"), "disable\n");

            File userDir = new File(rootDir, ImageFs.WINEPREFIX + "/drive_c/users/xuser");
            File[] userFiles = userDir.listFiles();
            if (userFiles != null) {
                for (File userFile : userFiles) {
                    if (FileUtils.isSymlink(userFile)) {
                        String path = userFile.getPath();
                        userFile.delete();
                        (new File(path)).mkdirs();
                    }
                }
            }

            String suffix = wineInfo.fullVersion() + "-" + wineInfo.getArch();
            File containerPatternFile = new File(installedWineDir, "/preinstall/container-pattern-" + suffix + ".tzst");
            TarCompressorUtils.compress(TarCompressorUtils.Type.ZSTD, new File(rootDir, ImageFs.WINEPREFIX), containerPatternFile, MainActivity.CONTAINER_PATTERN_COMPRESSION_LEVEL);

            if (!containerPatternFile.renameTo(new File(installedWineDir, containerPatternFile.getName())) ||
                    !(new File(wineInfo.path)).renameTo(new File(installedWineDir, wineInfo.identifier()))) {
                containerPatternFile.delete();
            }

            FileUtils.delete(new File(installedWineDir, "/preinstall"));

            preloaderDialog.closeOnUiThread();
            AppUtils.restartApplication(this, R.id.main_menu_settings);
        }));
    }

    private String normalizeDXVKVersion(String versionString) {
        // Remove suffixes like (Async) or (Default)
        String cleanVersion = versionString.replaceAll("\\s*\\([^)]*\\)", "").trim();
        // Check if "dxvk-" is already present, if not, add it
        if (!cleanVersion.startsWith("dxvk-")) {
            cleanVersion = "dxvk-" + cleanVersion;
        }
        return cleanVersion;
    }



    private void extractDXWrapperFiles(String dxwrapper) {
        final String[] dlls = {"d3d10.dll", "d3d10_1.dll", "d3d10core.dll", "d3d11.dll", "d3d12.dll", "d3d12core.dll", "d3d8.dll", "d3d9.dll", "dxgi.dll", "ddraw.dll"};
        if (firstTimeBoot && !dxwrapper.equals("vkd3d")) cloneOriginalDllFiles(dlls);
        File rootDir = imageFs.getRootDir();
        File windowsDir = new File(rootDir, ImageFs.WINEPREFIX + "/drive_c/windows");

        switch (dxwrapper) {
            case "wined3d":
                restoreOriginalDllFiles(dlls);
                break;
            case "cnc-ddraw":
                restoreOriginalDllFiles(dlls);
                final String assetDir = "dxwrapper/cnc-ddraw-" + DefaultVersion.CNC_DDRAW;
                File configFile = new File(rootDir, ImageFs.WINEPREFIX + "/drive_c/ProgramData/cnc-ddraw/ddraw.ini");
                if (!configFile.isFile()) FileUtils.copy(this, assetDir + "/ddraw.ini", configFile);
                File shadersDir = new File(rootDir, ImageFs.WINEPREFIX + "/drive_c/ProgramData/cnc-ddraw/Shaders");
                FileUtils.delete(shadersDir);
                FileUtils.copy(this, assetDir + "/Shaders", shadersDir);
                TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, this, assetDir + "/ddraw.tzst", windowsDir, onExtractFileListener);
                break;
            case "vkd3d":
                String[] dxvkVersions = getResources().getStringArray(R.array.dxvk_version_entries);
                String latestVersion = normalizeDXVKVersion(dxvkVersions[dxvkVersions.length - 1]); // Use the new helper method
                TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, this, "dxwrapper/" + latestVersion + ".tzst", windowsDir, onExtractFileListener);
                TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, this, "dxwrapper/vkd3d-" + DefaultVersion.VKD3D + ".tzst", windowsDir, onExtractFileListener);
                break;
            default:
                if (dxwrapper.startsWith("dxvk")) {
                    restoreOriginalDllFiles("d3d12.dll", "d3d12core.dll", "ddraw.dll");
                    ContentProfile profile = contentsManager.getProfileByEntryName(dxwrapper);
                    if (profile != null) {
                        contentsManager.applyContent(profile);
                    } else {
                        String parsedDxwrapper = normalizeDXVKVersion(dxwrapper); // Use the new helper method
                        TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, this, "dxwrapper/" + parsedDxwrapper + ".tzst", windowsDir, onExtractFileListener);
                        // d8vk merged into dxvk since dxvk-2.4, so we don't need to extract d8vk after that
                        if (compareVersion(StringUtils.parseNumber(parsedDxwrapper), "2.4") < 0) {
                            TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, this, "dxwrapper/d8vk-" + DefaultVersion.D8VK + ".tzst", windowsDir, onExtractFileListener);
                        }
                    }
                } else if (dxwrapper.startsWith("vkd3d")) {
                    ContentProfile profile = contentsManager.getProfileByEntryName(dxwrapper);
                    if (profile != null) {
                        contentsManager.applyContent(profile);
                    } else {
                        TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, this, "dxwrapper/" + dxwrapper + ".tzst", windowsDir, onExtractFileListener);
                    }
                }
                break;
        }
    }


    private static int compareVersion(String varA, String varB) {
        final String[] levelsA = varA.split("\\.");
        final String[] levelsB = varB.split("\\.");
        int minLen = Math.min(levelsA.length, levelsB.length);
        int numA, numB;

        for (int i = 0; i < minLen; i++) {
            numA = Integer.parseInt(levelsA[i]);
            numB = Integer.parseInt(levelsB[i]);
            if (numA != numB)
                return numA - numB;
        }

        if (levelsA.length != levelsB.length)
            return levelsA.length - levelsB.length;

        return 0;
    }

    private void extractWinComponentFiles() {
        File rootDir = imageFs.getRootDir();
        File windowsDir = new File(rootDir, ImageFs.WINEPREFIX + "/drive_c/windows");
        File systemRegFile = new File(rootDir, ImageFs.WINEPREFIX + "/system.reg");

        try {
            JSONObject wincomponentsJSONObject = new JSONObject(FileUtils.readString(this, "wincomponents/wincomponents.json"));
            ArrayList<String> dlls = new ArrayList<>();
            String wincomponents = shortcut != null ? shortcut.getExtra("wincomponents", container.getWinComponents()) : container.getWinComponents();

            if (firstTimeBoot) {
                for (String[] wincomponent : new KeyValueSet(wincomponents)) {
                    JSONArray dlnames = wincomponentsJSONObject.getJSONArray(wincomponent[0]);
                    for (int i = 0; i < dlnames.length(); i++) {
                        String dlname = dlnames.getString(i);
                        dlls.add(!dlname.endsWith(".exe") ? dlname + ".dll" : dlname);
                    }
                }

                cloneOriginalDllFiles(dlls.toArray(new String[0]));
                dlls.clear();
            }

            Iterator<String[]> oldWinComponentsIter = new KeyValueSet(container.getExtra("wincomponents", Container.FALLBACK_WINCOMPONENTS)).iterator();

            for (String[] wincomponent : new KeyValueSet(wincomponents)) {
                if (wincomponent[1].equals(oldWinComponentsIter.next()[1])) continue;
                String identifier = wincomponent[0];
                boolean useNative = wincomponent[1].equals("1");

                if (useNative) {
                    TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, this, "wincomponents/" + identifier + ".tzst", windowsDir, onExtractFileListener);
                } else {
                    JSONArray dlnames = wincomponentsJSONObject.getJSONArray(identifier);
                    for (int i = 0; i < dlnames.length(); i++) {
                        String dlname = dlnames.getString(i);
                        dlls.add(!dlname.endsWith(".exe") ? dlname + ".dll" : dlname);
                    }
                }

                WineUtils.setWinComponentRegistryKeys(systemRegFile, identifier, useNative);
            }

            if (!dlls.isEmpty()) restoreOriginalDllFiles(dlls.toArray(new String[0]));
            WineUtils.overrideWinComponentDlls(this, container, wincomponents);
        } catch (JSONException e) {
        }
    }

    private void restoreOriginalDllFiles(final String... dlls) {
        File rootDir = imageFs.getRootDir();
        File cacheDir = new File(rootDir, ImageFs.CACHE_PATH + "/original_dlls");
        if (cacheDir.isDirectory()) {
            File windowsDir = new File(rootDir, ImageFs.WINEPREFIX + "/drive_c/windows");
            String[] dirnames = cacheDir.list();
            int filesCopied = 0;

            for (String dll : dlls) {
                boolean success = false;
                for (String dirname : dirnames) {
                    File srcFile = new File(cacheDir, dirname + "/" + dll);
                    File dstFile = new File(windowsDir, dirname + "/" + dll);
                    if (FileUtils.copy(srcFile, dstFile)) success = true;
                }
                if (success) filesCopied++;
            }

            if (filesCopied == dlls.length) return;
        }

        containerManager.extractContainerPatternFile(container.getWineVersion(), container.getRootDir(), (file, size) -> {
            String path = file.getPath();
            if (path.contains("system32/") || path.contains("syswow64/")) {
                for (String dll : dlls) {
                    if (path.endsWith("system32/" + dll) || path.endsWith("syswow64/" + dll)) return file;
                }
            }
            return null;
        });

        cloneOriginalDllFiles(dlls);
    }

    private void cloneOriginalDllFiles(final String... dlls) {
        File rootDir = imageFs.getRootDir();
        File cacheDir = new File(rootDir, ImageFs.CACHE_PATH + "/original_dlls");
        if (!cacheDir.isDirectory()) cacheDir.mkdirs();
        File windowsDir = new File(rootDir, ImageFs.WINEPREFIX + "/drive_c/windows");
        String[] dirnames = {"system32", "syswow64"};

        for (String dll : dlls) {
            for (String dirname : dirnames) {
                File dllFile = new File(windowsDir, dirname + "/" + dll);
                if (dllFile.isFile()) FileUtils.copy(dllFile, new File(cacheDir, dirname + "/" + dll));
            }
        }
    }

    private boolean isGenerateWineprefix() {
        return getIntent().getBooleanExtra("generate_wineprefix", false);
    }

    private String getWineStartCommand() {
        File tempDir = new File(container.getRootDir(), ".wine/drive_c/windows/temp");
        FileUtils.clear(tempDir);

        String args = "";
        if (shortcut != null) {
            String execArgs = shortcut.getExtra("execArgs");
            execArgs = !execArgs.isEmpty() ? " " + execArgs : "";

            if (shortcut.path.endsWith(".lnk")) {
                args += "\"" + shortcut.path + "\"" + execArgs;
            } else {
                String exeDir = FileUtils.getDirname(shortcut.path);
                String filename = FileUtils.getName(shortcut.path);
                int dotIndex, spaceIndex;
                if ((dotIndex = filename.lastIndexOf(".")) != -1 && (spaceIndex = filename.indexOf(" ", dotIndex)) != -1) {
                    execArgs = filename.substring(spaceIndex + 1) + execArgs;
                    filename = filename.substring(0, spaceIndex);
                }
                args += "/dir " + exeDir.replace(" ", "\\ ") + " \"" + filename + "\"" + execArgs;
            }
        } else args += "\"wfm.exe\"";

        return "winhandler.exe " + args;
    }

    public XServer getXServer() {
        return xServer;
    }

    public WinHandler getWinHandler() {
        return winHandler;
    }

    public XServerView getXServerView() {
        return xServerView;
    }

    public Container getContainer() {
        return container;
    }

    private void changeWineAudioDriver() {
        if (!audioDriver.equals(container.getExtra("audioDriver"))) {
            File rootDir = imageFs.getRootDir();
            File userRegFile = new File(rootDir, ImageFs.WINEPREFIX + "/user.reg");
            try (WineRegistryEditor registryEditor = new WineRegistryEditor(userRegFile)) {
                if (audioDriver.equals("alsa")) {
                    registryEditor.setStringValue("Software\\Wine\\Drivers", "Audio", "alsa");
                } else if (audioDriver.equals("pulseaudio")) {
                    registryEditor.setStringValue("Software\\Wine\\Drivers", "Audio", "pulse");
                }
            }
            container.putExtra("audioDriver", audioDriver);
            container.saveData();
        }
    }

    private void applyGeneralPatches(Container container) {
        File rootDir = imageFs.getRootDir();
        FileUtils.delete(new File(rootDir, "/opt/apps"));
        TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, this, "imagefs_patches.tzst", rootDir, onExtractFileListener);
        TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, this, "pulseaudio.tzst", new File(getFilesDir(), "pulseaudio"));
        WineUtils.applySystemTweaks(this, wineInfo);
        container.putExtra("graphicsDriver", null);
        container.putExtra("desktopTheme", null);
        SettingsFragment.resetBox86_64Version(this);
    }

    private void assignTaskAffinity(Window window) {
        if (taskAffinityMask == 0) return;
        int processId = window.getProcessId();
        String className = window.getClassName();
        int processAffinity = window.isWoW64() ? taskAffinityMaskWoW64 : taskAffinityMask;

        if (processId > 0) {
            winHandler.setProcessAffinity(processId, processAffinity);
        } else if (!className.isEmpty()) {
            winHandler.setProcessAffinity(window.getClassName(), processAffinity);
        }
    }

    private void changeFrameRatingVisibility(Window window, Property property) {
        if (frameRating == null) return;
        if (property != null) {
            if (frameRatingWindowId == -1 && window.attributes.isMapped() && property.nameAsString().equals("_MESA_DRV")) {
                frameRatingWindowId = window.id;
            }
        } else if (window.id == frameRatingWindowId) {
            frameRatingWindowId = -1;
            runOnUiThread(() -> frameRating.setVisibility(View.GONE));
        }
    }




















}
