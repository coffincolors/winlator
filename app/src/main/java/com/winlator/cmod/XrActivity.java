package com.winlator.cmod;

import android.app.Activity;
import android.app.ActivityOptions;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Display;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.View;
import android.widget.EditText;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;

import com.winlator.cmod.container.Container;
import com.winlator.cmod.contentdialog.ContentDialog;
import com.winlator.cmod.contentdialog.NavigationDialog;
import com.winlator.cmod.core.AppUtils;
import com.winlator.cmod.xserver.Keyboard;
import com.winlator.cmod.xserver.Pointer;
import com.winlator.cmod.xserver.XKeycode;
import com.winlator.cmod.xserver.XLock;
import com.winlator.cmod.xserver.XServer;

/*
    WinlatorXR implementation by lvonasek (https://github.com/lvonasek)
 */

public class XrActivity extends XServerDisplayActivity implements TextWatcher {
    // Order of the enum has to be the as in xr/main.cpp
    public enum ControllerAxis {
        L_PITCH, L_YAW, L_ROLL, L_QX, L_QY, L_QZ, L_QW, L_THUMBSTICK_X, L_THUMBSTICK_Y, L_X, L_Y, L_Z,
        R_PITCH, R_YAW, R_ROLL, R_QX, R_QY, R_QZ, R_QW, R_THUMBSTICK_X, R_THUMBSTICK_Y, R_X, R_Y, R_Z,
        HMD_PITCH, HMD_YAW, HMD_ROLL, HMD_QX, HMD_QY, HMD_QZ, HMD_QW, HMD_X, HMD_Y, HMD_Z,
        HMD_IPD, HMD_FOVX, HMD_FOVY, HMD_SYNC
    }

    // Order of the enum has to be the as in xr/main.cpp
    public enum ControllerButton {
        L_GRIP,  L_MENU, L_THUMBSTICK_PRESS, L_THUMBSTICK_LEFT, L_THUMBSTICK_RIGHT, L_THUMBSTICK_UP, L_THUMBSTICK_DOWN, L_TRIGGER, L_X, L_Y,
        R_A, R_B, R_GRIP, R_THUMBSTICK_PRESS, R_THUMBSTICK_LEFT, R_THUMBSTICK_RIGHT, R_THUMBSTICK_UP, R_THUMBSTICK_DOWN, R_TRIGGER,
    }

    private static boolean isDeviceDetectionFinished = false;
    private static boolean isDeviceSupported = false;
    private static boolean isEnabled = false;
    private static boolean isImmersive = false;
    private static boolean isSBS = false;
    private static boolean isVR = false;
    private static boolean usePassthrough = false;
    private static boolean[] currentButtons = new boolean[ControllerButton.values().length];
    private static final KeyCharacterMap chars = KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD);
    private static final float[] lastAxes = new float[ControllerAxis.values().length];
    private static final boolean[] lastButtons = new boolean[ControllerButton.values().length];
    private static long lastDialogShown = 0;
    private static String lastText = "";
    private static float mouseSpeed = 1;
    private static final float[] smoothedMouse = new float[2];
    private static XrActivity instance;
    private static XrAPI xrAPI = null;

    public native void nativeSetUsePT(boolean enabled);
    public native void nativeSetUseVR(boolean enabled);
    public native void sendManufacturer(String manufacturer);

    @Override
    public synchronized void onPause() {
        EditText text = findViewById(R.id.XRTextInput);
        text.removeTextChangedListener(this);
        super.onPause();
    }

    @Override
    public synchronized void onResume() {
        super.onResume();
        instance = this;
        mouseSpeed = PreferenceManager.getDefaultSharedPreferences(this).getFloat("cursor_speed", 1.0f);

        EditText text = findViewById(R.id.XRTextInput);
        text.getEditableText().clear();
        text.addTextChangedListener(this);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        usePassthrough = prefs.getBoolean("use_pt", true);
        nativeSetUsePT(usePassthrough);

        String manufacturer = Build.MANUFACTURER.toUpperCase();
        sendManufacturer(manufacturer);
    }

    @Override
    public synchronized void onDestroy() {
        super.onDestroy();

        Intent intent = getBaseContext().getPackageManager()
                .getLaunchIntentForPackage(getBaseContext().getPackageName());
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        }

        android.os.Process.killProcess(android.os.Process.myPid());
        System.exit(0);
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {}

    @Override
    public synchronized void afterTextChanged(Editable e) {
        XServer server = instance.getXServer();
        EditText text = findViewById(R.id.XRTextInput);
        String s = text.getEditableText().toString();
        if (s.length() > lastText.length()) {
            lastText = s;
            KeyEvent[] events = chars.getEvents(new char[]{s.charAt(s.length() - 1)});
            if (events != null) {
                for (KeyEvent keyEvent : events) {
                    server.keyboard.onKeyEvent(keyEvent);
                    sleep(50);
                }
            }
        } else {
            lastText = s;
            server.keyboard.onKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL));
            sleep(50);
            server.keyboard.onKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL));
        }
        if (s.isEmpty()) {
            resetText();
        }
    }

    private synchronized void resetText() {
        EditText text = findViewById(R.id.XRTextInput);
        text.removeTextChangedListener(this);
        text.getEditableText().clear();
        text.getEditableText().append(" ");
        text.addTextChangedListener(this);
    }

    public static XrActivity getInstance() {
        return instance;
    }

    public static boolean getImmersive() {
        return isImmersive && ContentDialog.getFrontInstance() == null;
    }

    public static boolean getSBS() {
        return isSBS && ContentDialog.getFrontInstance() == null;
    }

    public static boolean getVR() {
        return isVR && ContentDialog.getFrontInstance() == null;
    }

    public static boolean isEnabled(Context context) {
        if (context != null) {
            isEnabled = PreferenceManager.getDefaultSharedPreferences(context).getBoolean("use_xr", true);
        }
        return isSupported() && isEnabled;
    }

    public static boolean isSupported() {
        if (!isDeviceDetectionFinished) {
            if (Build.MANUFACTURER.compareToIgnoreCase("META") == 0) {
                isDeviceSupported = true;
            }
            if (Build.MANUFACTURER.compareToIgnoreCase("OCULUS") == 0) {
                isDeviceSupported = true;
            }
            if (Build.MANUFACTURER.compareToIgnoreCase("PICO") == 0) {
                isDeviceSupported = true;
            }
            isDeviceDetectionFinished = true;
        }
        return isDeviceSupported;
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        instance.findViewById(R.id.XRTextInput).setVisibility(View.GONE);
    }

    public void callMenuAction(int item) {
        switch (item) {
            case R.id.main_menu_keyboard:
                new Thread(() -> {
                    sleep(250); //ensure onWindowFocusChanged was called
                    runOnUiThread(() -> {
                        View input = instance.findViewById(R.id.XRTextInput);
                        input.setVisibility(View.VISIBLE);
                        isVR = false;
                        isSBS = false;
                        isImmersive = false;
                        instance.resetText();
                        AppUtils.showKeyboard(instance);
                        input.requestFocus();
                    });
                }).start();
                break;
            case R.id.xr_passthrough:
                usePassthrough = !usePassthrough;
                nativeSetUsePT(usePassthrough);
                break;
        }
    }

    public static void openIntent(Activity context, int containerId, String path) {
        // 0. Create the launch intent
        boolean isPico = Build.MANUFACTURER.compareToIgnoreCase("PICO") == 0;
        Intent intent = new Intent(context, isPico ? XrActivityPico.class : XrActivityMeta.class);
        intent.putExtra("container_id", containerId);
        if (path != null) {
            intent.putExtra("shortcut_path", path);
        }

        // 1. Locate the main display ID and add that to the intent
        final int mainDisplayId = Display.DEFAULT_DISPLAY;
        ActivityOptions options = ActivityOptions.makeBasic().setLaunchDisplayId(mainDisplayId);

        // 2. Set the flags: start in a new task and replace any existing tasks in the app stack
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK |
                Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        // 3. Launch the activity.
        // Don't use the container's ContextWrapper, which is adding arguments
        context.getBaseContext().startActivity(intent, options.toBundle());

        // 4. Finish the previous activity: this avoids an audio bug
        context.finish();
    }

    public static void updateControllers() {
        // Get OpenXR data
        float[] axes = instance.getAxes();
        boolean[] buttons = instance.getButtons();
        int primaryController = instance.container.getPrimaryController();

        // Primary controller mapping
        ControllerAxis mouseAxisX = primaryController == 0 ? ControllerAxis.L_X : ControllerAxis.R_X;
        ControllerAxis mouseAxisY = primaryController == 0 ? ControllerAxis.L_Y : ControllerAxis.R_Y;
        ControllerButton primaryGrip = primaryController == 0 ? ControllerButton.L_GRIP : ControllerButton.R_GRIP;
        ControllerButton primaryTrigger = primaryController == 0 ? ControllerButton.L_TRIGGER : ControllerButton.R_TRIGGER;
        ControllerButton primaryUp = primaryController == 0 ? ControllerButton.L_THUMBSTICK_UP : ControllerButton.R_THUMBSTICK_UP;
        ControllerButton primaryDown = primaryController == 0 ? ControllerButton.L_THUMBSTICK_DOWN : ControllerButton.R_THUMBSTICK_DOWN;
        ControllerButton primaryLeft = primaryController == 0 ? ControllerButton.L_THUMBSTICK_LEFT : ControllerButton.R_THUMBSTICK_LEFT;
        ControllerButton primaryRight = primaryController == 0 ? ControllerButton.L_THUMBSTICK_RIGHT : ControllerButton.R_THUMBSTICK_RIGHT;
        ControllerButton primaryPress = primaryController == 0 ? ControllerButton.L_THUMBSTICK_PRESS : ControllerButton.R_THUMBSTICK_PRESS;
        ControllerButton secondaryGrip = primaryController == 1 ? ControllerButton.L_GRIP : ControllerButton.R_GRIP;
        ControllerButton secondaryTrigger = primaryController == 1 ? ControllerButton.L_TRIGGER : ControllerButton.R_TRIGGER;
        ControllerButton secondaryUp = primaryController == 1 ? ControllerButton.L_THUMBSTICK_UP : ControllerButton.R_THUMBSTICK_UP;
        ControllerButton secondaryDown = primaryController == 1 ? ControllerButton.L_THUMBSTICK_DOWN : ControllerButton.R_THUMBSTICK_DOWN;
        ControllerButton secondaryLeft = primaryController == 1 ? ControllerButton.L_THUMBSTICK_LEFT : ControllerButton.R_THUMBSTICK_LEFT;
        ControllerButton secondaryRight = primaryController == 1 ? ControllerButton.L_THUMBSTICK_RIGHT : ControllerButton.R_THUMBSTICK_RIGHT;
        ControllerButton secondaryPress = primaryController == 1 ? ControllerButton.L_THUMBSTICK_PRESS : ControllerButton.R_THUMBSTICK_PRESS;

        // Android UI input
        ContentDialog dialog = ContentDialog.getFrontInstance();
        if (dialog != null) {
            if (getButtonClicked(buttons, primaryPress)) instance.runOnUiThread(dialog::onBackPressed);
            if (getButtonClicked(buttons, primaryUp)) instance.runOnUiThread(() -> dialog.onKeyAction(KeyEvent.KEYCODE_DPAD_UP));
            if (getButtonClicked(buttons, primaryDown)) instance.runOnUiThread(() -> dialog.onKeyAction(KeyEvent.KEYCODE_DPAD_DOWN));
            if (getButtonClicked(buttons, primaryTrigger)) instance.runOnUiThread(() -> dialog.onKeyAction(KeyEvent.KEYCODE_ENTER));
            if (getButtonClicked(buttons, primaryLeft)) instance.runOnUiThread(() -> dialog.onKeyAction(KeyEvent.KEYCODE_DPAD_LEFT));
            if (getButtonClicked(buttons, primaryRight)) instance.runOnUiThread(() -> dialog.onKeyAction(KeyEvent.KEYCODE_DPAD_RIGHT));
            System.arraycopy(buttons, 0, lastButtons, 0, buttons.length);
            lastDialogShown = System.currentTimeMillis();
            return;
        } else if (getButtonClicked(buttons, primaryPress)) {
            instance.runOnUiThread(() -> new NavigationDialog(instance).show());
        }

        // Block input shortly after dialog closed
        if (System.currentTimeMillis() - lastDialogShown < 500) {
            System.arraycopy(buttons, 0, lastButtons, 0, buttons.length);
            return;
        }

        try (XLock lock = instance.getXServer().lock(XServer.Lockable.WINDOW_MANAGER, XServer.Lockable.INPUT_DEVICE)) {
            // Mouse control with hand
            float f = 0.75f;
            float meter2px = instance.getXServer().screenInfo.width * 10.0f;
            float dx = (axes[mouseAxisX.ordinal()] - lastAxes[mouseAxisX.ordinal()]) * meter2px;
            float dy = (axes[mouseAxisY.ordinal()] - lastAxes[mouseAxisY.ordinal()]) * meter2px;
            if ((Math.abs(dx) > 300) || (Math.abs(dy) > 300)) {
                dx = 0;
                dy = 0;
            }

            // Mouse control with head
            Pointer mouse = instance.getXServer().pointer;
            if (isImmersive) {
                float angle2px = instance.getXServer().screenInfo.width * 0.05f / f;
                dx = getAngleDiff(lastAxes[ControllerAxis.HMD_YAW.ordinal()], axes[ControllerAxis.HMD_YAW.ordinal()]) * angle2px;
                dy = getAngleDiff(lastAxes[ControllerAxis.HMD_PITCH.ordinal()], axes[ControllerAxis.HMD_PITCH.ordinal()]) * angle2px;
                if (Float.isNaN(dy)) {
                    dy = 0;
                }
                smoothedMouse[0] = mouse.getClampedX() + 0.5f;
                smoothedMouse[1] = mouse.getClampedY() + 0.5f;
            }

            // Mouse smoothing
            dx *= mouseSpeed;
            dy *= mouseSpeed;
            smoothedMouse[0] = smoothedMouse[0] * f + (mouse.getClampedX() + 0.5f + dx) * (1 - f);
            smoothedMouse[1] = smoothedMouse[1] * f + (mouse.getClampedY() + 0.5f - dy) * (1 - f);

            // Mouse "snap turn"
            int snapturn = isImmersive ? 125 : 25;
            if (getButtonClicked(buttons, primaryLeft)) {
                smoothedMouse[0] = mouse.getClampedX() - snapturn;
            }
            if (getButtonClicked(buttons, primaryRight)) {
                smoothedMouse[0] = mouse.getClampedX() + snapturn;
            }

            // Set mouse status
            if (!instance.getXServer().isRelativeMouseMovement()) {
                mouse.setPosition((int) smoothedMouse[0], (int) smoothedMouse[1]);
                mouse.setButton(Pointer.Button.BUTTON_LEFT, buttons[primaryTrigger.ordinal()]);
                mouse.setButton(Pointer.Button.BUTTON_RIGHT, buttons[primaryGrip.ordinal()]);
                mouse.setButton(Pointer.Button.BUTTON_SCROLL_UP, buttons[primaryUp.ordinal()]);
                mouse.setButton(Pointer.Button.BUTTON_SCROLL_DOWN, buttons[primaryDown.ordinal()]);
            }

            // Switch immersive/SBS mode
            if (getButtonClicked(buttons, secondaryPress)) {
                if (buttons[primaryGrip.ordinal()]) {
                    isSBS = !isSBS;
                }
                else {
                    isImmersive = !isImmersive;
                }
            }

            // Update keyboard
            currentButtons = buttons;
            mapKey(ControllerButton.L_MENU, XKeycode.KEY_ESC.id);
            mapKey(ControllerButton.R_A, instance.container.getControllerMapping(Container.XrControllerMapping.BUTTON_A));
            mapKey(ControllerButton.R_B, instance.container.getControllerMapping(Container.XrControllerMapping.BUTTON_B));
            mapKey(ControllerButton.L_X, instance.container.getControllerMapping(Container.XrControllerMapping.BUTTON_X));
            mapKey(ControllerButton.L_Y, instance.container.getControllerMapping(Container.XrControllerMapping.BUTTON_Y));
            mapKey(secondaryGrip, instance.container.getControllerMapping(Container.XrControllerMapping.BUTTON_GRIP));
            mapKey(secondaryTrigger, instance.container.getControllerMapping(Container.XrControllerMapping.BUTTON_TRIGGER));
            mapKey(secondaryUp, instance.container.getControllerMapping(Container.XrControllerMapping.THUMBSTICK_UP));
            mapKey(secondaryDown, instance.container.getControllerMapping(Container.XrControllerMapping.THUMBSTICK_DOWN));
            mapKey(secondaryLeft, instance.container.getControllerMapping(Container.XrControllerMapping.THUMBSTICK_LEFT));
            mapKey(secondaryRight, instance.container.getControllerMapping(Container.XrControllerMapping.THUMBSTICK_RIGHT));

            // Store the OpenXR data
            System.arraycopy(axes, 0, lastAxes, 0, axes.length);
            System.arraycopy(buttons, 0, lastButtons, 0, buttons.length);

            // Communication between XR and Win32 apps
            try {
                if (xrAPI == null) {
                    //xrAPI = new XrAPI("/sdcard/Download/xrtemp");
                    xrAPI = new XrAPI(XrAPI.DEFAULT_PATH);
                    xrAPI.writeFile(XrAPI.FLAG_VERSION, XrAPI.CURRENT_VERSION);
                }
                isVR = xrAPI.hasFlag(XrAPI.FLAG_VR);
                getInstance().nativeSetUseVR(isVR);
                if (isVR) {
                    isSBS = xrAPI.hasFlag(XrAPI.FLAG_SBS);
                    xrAPI.sendUDP(xrAPI.encodeAxes(lastAxes, 0), XrAPI.DEFAULT_PORT);
                    //xrAPI.sendFile(xrAPI.encodeAxes(lastAxes, 0), 0);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private static float getAngleDiff(float oldAngle, float newAngle) {
        float diff = oldAngle - newAngle;
        while (diff > 180) {
            diff -= 360;
        }
        while (diff < -180) {
            diff += 360;
        }
        return diff;
    }

    private static boolean getButtonClicked(boolean[] buttons, ControllerButton button) {
        return buttons[button.ordinal()] && !lastButtons[button.ordinal()];
    }

    private static void mapKey(ControllerButton xrButton, byte xKeycode) {
        Keyboard keyboard = instance.getXServer().keyboard;
        if (currentButtons[xrButton.ordinal()] != lastButtons[xrButton.ordinal()]) {
            if (currentButtons[xrButton.ordinal()]) {
                keyboard.setKeyPress(xKeycode, 0);
            } else {
                keyboard.setKeyRelease(xKeycode);
            }
        }
    }

    private static void sleep(int ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    // Rendering
    public native void init();
    public native void bindFramebuffer();
    public native int getWidth();
    public native int getHeight();
    public native boolean beginFrame(boolean immersive, boolean sbs);
    public native void endFrame();

    // Input
    public native float[] getAxes();
    public native boolean[] getButtons();
}
