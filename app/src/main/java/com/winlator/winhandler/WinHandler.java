package com.winlator.winhandler;

import static com.winlator.inputcontrols.ExternalController.isGameController;

import android.content.SharedPreferences;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.widget.Toast;

import androidx.preference.PreferenceManager;

import com.winlator.XServerDisplayActivity;
import com.winlator.core.StringUtils;
import com.winlator.inputcontrols.ControlsProfile;
import com.winlator.inputcontrols.ExternalController;
import com.winlator.inputcontrols.GamepadState;
import com.winlator.math.Mathf;
import com.winlator.xserver.XServer;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayDeque;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;

public class WinHandler {
    private static final short SERVER_PORT = 7947;
    private static final short CLIENT_PORT = 7946;
    public static final byte DINPUT_MAPPER_TYPE_STANDARD = 0;
    public static final byte DINPUT_MAPPER_TYPE_XINPUT = 1;
    private DatagramSocket socket;
    private final ByteBuffer sendData = ByteBuffer.allocate(64).order(ByteOrder.LITTLE_ENDIAN);
    private final ByteBuffer receiveData = ByteBuffer.allocate(64).order(ByteOrder.LITTLE_ENDIAN);
    private final DatagramPacket sendPacket = new DatagramPacket(sendData.array(), 64);
    private final DatagramPacket receivePacket = new DatagramPacket(receiveData.array(), 64);
    private final ArrayDeque<Runnable> actions = new ArrayDeque<>();
    private boolean initReceived = false;
    private boolean running = false;
    private OnGetProcessInfoListener onGetProcessInfoListener;
    private ExternalController currentController;
    private InetAddress localhost;
    private byte dinputMapperType = DINPUT_MAPPER_TYPE_XINPUT;
    private final XServerDisplayActivity activity;
    private final List<Integer> gamepadClients = new CopyOnWriteArrayList<>();
    private SharedPreferences preferences;
    private byte triggerMode;

    private boolean xinputDisabled; // Used for exclusive mouse control

    public WinHandler(XServerDisplayActivity activity) {
        this.activity = activity;
    }

    // Gyro related variables
    private float gyroX = 0;
    private float gyroY = 0;
    // Add fields for sensitivity, smoothing, and inversion
    private float gyroSensitivityX = 1.0f;
    private float gyroSensitivityY = 1.0f;
    private float smoothingFactor = 0.9f; // For exponential smoothing
    private boolean invertGyroX = true;
    private boolean invertGyroY = false;
    private float gyroDeadzone = 0.05f;

    // Implement exponential smoothing
    private float smoothGyroX = 0;
    private float smoothGyroY = 0;

    public void setGyroSensitivityX(float sensitivity) {
        this.gyroSensitivityX = sensitivity;
    }

    public void setGyroSensitivityY(float sensitivity) {
        this.gyroSensitivityY = sensitivity;
    }

    public void setSmoothingFactor(float factor) {
        this.smoothingFactor = factor;
    }

    public void setInvertGyroX(boolean invert) {
        this.invertGyroX = invert;
    }

    public void setInvertGyroY(boolean invert) {
        this.invertGyroY = invert;
    }

    public void setGyroDeadzone(float deadzone) {
        this.gyroDeadzone = deadzone;
    }


    public void updateGyroData(float rawGyroX, float rawGyroY) {
        // Apply deadzone
        if (Math.abs(rawGyroX) < gyroDeadzone) rawGyroX = 0;
        if (Math.abs(rawGyroY) < gyroDeadzone) rawGyroY = 0;

        // Apply inversion
        if (invertGyroX) rawGyroX = -rawGyroX;
        if (invertGyroY) rawGyroY = -rawGyroY;

        // Apply sensitivity
        rawGyroX *= gyroSensitivityX;
        rawGyroY *= gyroSensitivityY;

        // Apply smoothing
        smoothGyroX = smoothGyroX * smoothingFactor + rawGyroX * (1 - smoothingFactor);
        smoothGyroY = smoothGyroY * smoothingFactor + rawGyroY * (1 - smoothingFactor);

        // Update the gyro data
        this.gyroX = smoothGyroX;
        this.gyroY = smoothGyroY;

        // Send the updated gamepad state
        sendGamepadState();
    }

    private boolean sendPacket(int port) {
        try {
            int size = sendData.position();
            if (size == 0) return false;
            sendPacket.setAddress(localhost);
            sendPacket.setPort(port);
            socket.send(sendPacket);
            return true;
        }
        catch (IOException e) {
            return false;
        }
    }

    public void exec(String command) {
        command = command.trim();
        if (command.isEmpty()) return;
        String[] cmdList = command.split(" ", 2);
        final String filename = cmdList[0];
        final String parameters = cmdList.length > 1 ? cmdList[1] : "";

        addAction(() -> {
            byte[] filenameBytes = filename.getBytes();
            byte[] parametersBytes = parameters.getBytes();

            sendData.rewind();
            sendData.put(RequestCodes.EXEC);
            sendData.putInt(filenameBytes.length + parametersBytes.length + 8);
            sendData.putInt(filenameBytes.length);
            sendData.putInt(parametersBytes.length);
            sendData.put(filenameBytes);
            sendData.put(parametersBytes);
            sendPacket(CLIENT_PORT);
        });
    }

    public void killProcess(final String processName) {
        addAction(() -> {
            sendData.rewind();
            sendData.put(RequestCodes.KILL_PROCESS);
            byte[] bytes = processName.getBytes();
            sendData.putInt(bytes.length);
            sendData.put(bytes);
            sendPacket(CLIENT_PORT);
        });
    }

    public void listProcesses() {
        addAction(() -> {
            sendData.rewind();
            sendData.put(RequestCodes.LIST_PROCESSES);
            sendData.putInt(0);

            if (!sendPacket(CLIENT_PORT) && onGetProcessInfoListener != null) {
                onGetProcessInfoListener.onGetProcessInfo(0, 0, null);
            }
        });
    }

    public void setProcessAffinity(final String processName, final int affinityMask) {
        addAction(() -> {
            byte[] bytes = processName.getBytes();
            sendData.rewind();
            sendData.put(RequestCodes.SET_PROCESS_AFFINITY);
            sendData.putInt(9 + bytes.length);
            sendData.putInt(0);
            sendData.putInt(affinityMask);
            sendData.put((byte)bytes.length);
            sendData.put(bytes);
            sendPacket(CLIENT_PORT);
        });
    }

    public void setProcessAffinity(final int pid, final int affinityMask) {
        addAction(() -> {
            sendData.rewind();
            sendData.put(RequestCodes.SET_PROCESS_AFFINITY);
            sendData.putInt(9);
            sendData.putInt(pid);
            sendData.putInt(affinityMask);
            sendData.put((byte)0);
            sendPacket(CLIENT_PORT);
        });
    }

    public void mouseEvent(int flags, int dx, int dy, int wheelDelta) {
        if (!initReceived) return;
        addAction(() -> {
            sendData.rewind();
            sendData.put(RequestCodes.MOUSE_EVENT);
            sendData.putInt(10);
            sendData.putInt(flags);
            sendData.putShort((short)dx);
            sendData.putShort((short)dy);
            sendData.putShort((short)wheelDelta);
            sendData.put((byte)((flags & MouseEventFlags.MOVE) != 0 ? 1 : 0)); // cursor pos feedback
            sendPacket(CLIENT_PORT);
        });
    }

    public void keyboardEvent(byte vkey, int flags) {
        if (!initReceived) return;
        addAction(() -> {
            sendData.rewind();
            sendData.put(RequestCodes.KEYBOARD_EVENT);
            sendData.put(vkey);
            sendData.putInt(flags);
            sendPacket(CLIENT_PORT);
        });
    }

    public void bringToFront(final String processName) {
        bringToFront(processName, 0);
    }

    public void bringToFront(final String processName, final long handle) {
        addAction(() -> {
            sendData.rewind();
            sendData.put(RequestCodes.BRING_TO_FRONT);
            byte[] bytes = processName.getBytes();
            sendData.putInt(bytes.length);
            sendData.put(bytes);
            sendData.putLong(handle);
            sendPacket(CLIENT_PORT);
        });
    }

    private void addAction(Runnable action) {
        synchronized (actions) {
            actions.add(action);
            actions.notify();
        }
    }

    public OnGetProcessInfoListener getOnGetProcessInfoListener() {
        return onGetProcessInfoListener;
    }

    public void setOnGetProcessInfoListener(OnGetProcessInfoListener onGetProcessInfoListener) {
        synchronized (actions) {
            this.onGetProcessInfoListener = onGetProcessInfoListener;
        }
    }

    private void startSendThread() {
        Executors.newSingleThreadExecutor().execute(() -> {
            while (running) {
                synchronized (actions) {
                    while (initReceived && !actions.isEmpty()) actions.poll().run();
                    try {
                        actions.wait();
                    }
                    catch (InterruptedException e) {}
                }
            }
        });
    }

    public void stop() {
        running = false;

        if (socket != null) {
            socket.close();
            socket = null;
        }

        synchronized (actions) {
            actions.notify();
        }
    }

    private void handleRequest(byte requestCode, final int port) {
        switch (requestCode) {
            case RequestCodes.INIT: {
                initReceived = true;

                preferences = PreferenceManager.getDefaultSharedPreferences(activity.getBaseContext());

                // Load and apply trigger mode and xinput toggle settings
                triggerMode = (byte) preferences.getInt("trigger_mode", ExternalController.TRIGGER_AS_AXIS);
                xinputDisabled = preferences.getBoolean("xinput_toggle", false);

                // Load and apply gyro settings
                setGyroSensitivityX(preferences.getFloat("gyro_x_sensitivity", 1.0f));
                setGyroSensitivityY(preferences.getFloat("gyro_y_sensitivity", 1.0f));
                setSmoothingFactor(preferences.getFloat("gyro_smoothing", 0.9f));
                setInvertGyroX(preferences.getBoolean("invert_gyro_x", false));
                setInvertGyroY(preferences.getBoolean("invert_gyro_y", false));
                setGyroDeadzone(preferences.getFloat("gyro_deadzone", 0.05f));

                synchronized (actions) {
                    actions.notify();
                }
                break;
            }
            case RequestCodes.GET_PROCESS: {
                if (onGetProcessInfoListener == null) return;
                receiveData.position(receiveData.position() + 4);
                int numProcesses = receiveData.getShort();
                int index = receiveData.getShort();
                int pid = receiveData.getInt();
                long memoryUsage = receiveData.getLong();
                int affinityMask = receiveData.getInt();
                boolean wow64Process = receiveData.get() == 1;

                byte[] bytes = new byte[32];
                receiveData.get(bytes);
                String name = StringUtils.fromANSIString(bytes);

                onGetProcessInfoListener.onGetProcessInfo(index, numProcesses, new ProcessInfo(pid, name, memoryUsage, affinityMask, wow64Process));
                break;
            }
            case RequestCodes.GET_GAMEPAD: {
                if (xinputDisabled) return;
                boolean isXInput = receiveData.get() == 1;
                boolean notify = receiveData.get() == 1;
                final ControlsProfile profile = activity.getInputControlsView().getProfile();
                boolean useVirtualGamepad = profile != null && profile.isVirtualGamepad();

                if (!useVirtualGamepad && (currentController == null || !currentController.isConnected())) {
                    currentController = ExternalController.getController(0);
                    if (currentController != null)
                        currentController.setTriggerMode(triggerMode);
                }

                final boolean enabled = currentController != null || useVirtualGamepad;

                if (enabled && notify) {
                    if (!gamepadClients.contains(port)) gamepadClients.add(port);
                } else {
                    gamepadClients.remove(Integer.valueOf(port));
                }

                addAction(() -> {
                    sendData.rewind();
                    sendData.put(RequestCodes.GET_GAMEPAD);

                    if (enabled) {
                        sendData.putInt(!useVirtualGamepad ? currentController.getDeviceId() : profile.id);
                        sendData.put(dinputMapperType);
                        byte[] bytes = (useVirtualGamepad ? profile.getName() : currentController.getName()).getBytes();
                        sendData.putInt(bytes.length);
                        sendData.put(bytes);
                    } else {
                        sendData.putInt(0);
                    }

                    sendPacket(port);
                });
                break;
            }
            case RequestCodes.GET_GAMEPAD_STATE: {
                if (xinputDisabled) return;
                int gamepadId = receiveData.getInt();
                final ControlsProfile profile = activity.getInputControlsView().getProfile();
                boolean useVirtualGamepad = profile != null && profile.isVirtualGamepad();
                final boolean enabled = currentController != null || useVirtualGamepad;

                if (currentController != null && currentController.getDeviceId() != gamepadId) currentController = null;

                addAction(() -> {
                    sendData.rewind();
                    sendData.put(RequestCodes.GET_GAMEPAD_STATE);
                    sendData.put((byte)(enabled ? 1 : 0));

                    if (enabled) {
                        sendData.putInt(gamepadId);
                        if (useVirtualGamepad) {
                            profile.getGamepadState().writeTo(sendData);
                        } else {
                            currentController.state.writeTo(sendData);
                        }
                    }

                    sendPacket(port);
                });
                break;
            }
            case RequestCodes.RELEASE_GAMEPAD: {
                currentController = null;
                gamepadClients.clear();
                break;
            }
            case RequestCodes.CURSOR_POS_FEEDBACK: {
                short x = receiveData.getShort();
                short y = receiveData.getShort();
                XServer xServer = activity.getXServer();
                xServer.pointer.setX(x);
                xServer.pointer.setY(y);
                activity.getXServerView().requestRender();
                break;
            }
        }
    }


    public void start() {
        try {
            localhost = InetAddress.getLocalHost();
        }
        catch (UnknownHostException e) {
            try {
                localhost = InetAddress.getByName("127.0.0.1");
            }
            catch (UnknownHostException ex) {}
        }

        running = true;
        startSendThread();
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                socket = new DatagramSocket(null);
                socket.setReuseAddress(true);
                socket.bind(new InetSocketAddress((InetAddress)null, SERVER_PORT));

                while (running) {
                    socket.receive(receivePacket);

                    synchronized (actions) {
                        receiveData.rewind();
                        byte requestCode = receiveData.get();
                        handleRequest(requestCode, receivePacket.getPort());
                    }
                }
            }
            catch (IOException e) {}
        });
    }

    public void sendGamepadState() {
        if (!initReceived || gamepadClients.isEmpty() || xinputDisabled) return; // Add this check
        final ControlsProfile profile = activity.getInputControlsView().getProfile();
        final boolean useVirtualGamepad = profile != null && profile.isVirtualGamepad();
        final boolean enabled = currentController != null || useVirtualGamepad;

        for (final int port : gamepadClients) {
            addAction(() -> {
                sendData.rewind();
                sendData.put(RequestCodes.GET_GAMEPAD_STATE);
                sendData.put((byte)(enabled ? 1 : 0));

                if (enabled) {
                    sendData.putInt(!useVirtualGamepad ? currentController.getDeviceId() : profile.id);
                    GamepadState state = useVirtualGamepad ? profile.getGamepadState() : currentController.state;

                    // Combine gyro input with thumbstick input
                    state.thumbRX = Mathf.clamp(state.thumbRX + gyroX, -1.0f, 1.0f); // Apply clamping
                    state.thumbRY = Mathf.clamp(state.thumbRY + gyroY, -1.0f, 1.0f); // Apply clamping

                    state.writeTo(sendData);
                }

                sendPacket(port);
            });
        }
    }



    public boolean onGenericMotionEvent(MotionEvent event) {
        boolean handled = false;

        if (isGameController(event.getDeviceId())) {
            if (currentController != null && currentController.getDeviceId() == event.getDeviceId()) {
                handled = currentController.updateStateFromMotionEvent(event);
                if (handled) sendGamepadState();
            }
        }

        return handled;
    }

    public boolean onKeyEvent(KeyEvent event) {
        boolean handled = false;

        if (isGameController(event.getDeviceId())) {
            if (currentController != null && currentController.getDeviceId() == event.getDeviceId() && event.getRepeatCount() == 0) {
                int action = event.getAction();

                if (action == KeyEvent.ACTION_DOWN) {
                    handled = currentController.updateStateFromKeyEvent(event);
                } else if (action == KeyEvent.ACTION_UP) {
                    handled = currentController.updateStateFromKeyEvent(event);
                }

                if (handled) sendGamepadState();
            }
        }

        return handled;
    }

    private boolean isGameController(int deviceId) {
        InputDevice device = InputDevice.getDevice(deviceId);

        // Get device name, vendor ID, and product ID
        String deviceName = device.getName();
        int vendorId = device.getVendorId();
        int productId = device.getProductId();


        // Check if the device is a gamepad
        int sources = device.getSources();
        boolean isGamepad = (sources & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD;

        return isGamepad;
    }


    public byte getDInputMapperType() {
        return dinputMapperType;
    }

    public void setDInputMapperType(byte dinputMapperType) {
        this.dinputMapperType = dinputMapperType;
    }

    public ExternalController getCurrentController() {
        return currentController;
    }
}
