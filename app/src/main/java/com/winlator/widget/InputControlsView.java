package com.winlator.widget;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.winlator.inputcontrols.Binding;
import com.winlator.inputcontrols.ControlElement;
import com.winlator.inputcontrols.ControlsProfile;
import com.winlator.inputcontrols.ExternalController;
import com.winlator.inputcontrols.ExternalControllerBinding;
import com.winlator.math.Mathf;
import com.winlator.xserver.Pointer;
import com.winlator.xserver.XServer;

import java.io.IOException;
import java.io.InputStream;
import java.util.Timer;
import java.util.TimerTask;

public class InputControlsView extends View {
    public static final float DEFAULT_OVERLAY_OPACITY = 0.4f;
    public static final float JOYSTICK_DEAD_ZONE = 0.15f;
    public static final float DPAD_DEAD_ZONE = 0.3f;
    private boolean editMode = false;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final ColorFilter colorFilter = new PorterDuffColorFilter(0xffffffff, PorterDuff.Mode.SRC_IN);
    private final Point cursor = new Point();
    private boolean readyToDraw = false;
    private boolean moveCursor = false;
    private int snappingSize;
    private float offsetX;
    private float offsetY;
    private ControlElement selectedElement;
    private ControlsProfile profile;
    private float overlayOpacity = DEFAULT_OVERLAY_OPACITY;
    private TouchpadView touchpadView;
    private XServer xServer;
    private final Bitmap[] icons = new Bitmap[16];
    private Timer mouseMoveTimer;
    private final PointF mouseMoveOffset = new PointF();
    private boolean showTouchscreenControls = true;

    public InputControlsView(Context context) {
        super(context);
        setClickable(true);
        setFocusable(true);
        setFocusableInTouchMode(true);
        setBackgroundColor(0x00000000);
        setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    public void setEditMode(boolean editMode) {
        this.editMode = editMode;
    }

    public void setOverlayOpacity(float overlayOpacity) {
        this.overlayOpacity = overlayOpacity;
    }

    public int getSnappingSize() {
        return snappingSize;
    }

    @Override
    protected synchronized void onDraw(Canvas canvas) {
        int width = getWidth();
        int height = getHeight();

        if (width == 0 || height == 0) {
            readyToDraw = false;
            return;
        }

        snappingSize = width / 100;
        readyToDraw = true;

        if (editMode) {
            drawGrid(canvas);
            drawCursor(canvas);
        }

        if (profile != null) {
            if (!profile.isElementsLoaded()) profile.loadElements(this);
            if (showTouchscreenControls) for (ControlElement element : profile.getElements()) element.draw(canvas);
        }

        super.onDraw(canvas);
    }

    private void drawGrid(Canvas canvas) {
        paint.setStyle(Paint.Style.FILL);
        paint.setStrokeWidth(snappingSize * 0.0625f);
        paint.setColor(0xff000000);
        canvas.drawColor(Color.BLACK);

        paint.setAntiAlias(false);
        paint.setColor(0xff303030);

        int width = getMaxWidth();
        int height = getMaxHeight();

        for (int i = 0; i < width; i += snappingSize) {
            canvas.drawLine(i, 0, i, height, paint);
            canvas.drawLine(0, i, width, i, paint);
        }

        float cx = Mathf.roundTo(width * 0.5f, snappingSize);
        float cy = Mathf.roundTo(height * 0.5f, snappingSize);
        paint.setColor(0xff424242);

        for (int i = 0; i < width; i += snappingSize * 2) {
            canvas.drawLine(cx, i, cx, i + snappingSize, paint);
            canvas.drawLine(i, cy, i + snappingSize, cy, paint);
        }

        paint.setAntiAlias(true);
    }

    private void drawCursor(Canvas canvas) {
        paint.setStyle(Paint.Style.FILL);
        paint.setStrokeWidth(snappingSize * 0.0625f);
        paint.setColor(0xffc62828);

        paint.setAntiAlias(false);
        canvas.drawLine(0, cursor.y, getMaxWidth(), cursor.y, paint);
        canvas.drawLine(cursor.x, 0, cursor.x, getMaxHeight(), paint);

        paint.setAntiAlias(true);
    }

    public synchronized boolean addElement() {
        if (editMode && profile != null) {
            ControlElement element = new ControlElement(this);
            element.setX(cursor.x);
            element.setY(cursor.y);
            profile.addElement(element);
            profile.save();
            selectElement(element);
            return true;
        }
        else return false;
    }

    public synchronized boolean removeElement() {
        if (editMode && selectedElement != null && profile != null) {
            profile.removeElement(selectedElement);
            selectedElement = null;
            profile.save();
            invalidate();
            return true;
        }
        else return false;
    }

    public ControlElement getSelectedElement() {
        return selectedElement;
    }

    private synchronized void deselectAllElements() {
        selectedElement = null;
        if (profile != null) {
            for (ControlElement element : profile.getElements()) element.setSelected(false);
        }
    }

    private void selectElement(ControlElement element) {
        deselectAllElements();
        if (element != null) {
            selectedElement = element;
            selectedElement.setSelected(true);
        }
        invalidate();
    }

    public synchronized ControlsProfile getProfile() {
        return profile;
    }

    public synchronized void setProfile(ControlsProfile profile) {
        if (profile != null) {
            this.profile = profile;
            deselectAllElements();
        }
        else this.profile = null;
    }

    public boolean isShowTouchscreenControls() {
        return showTouchscreenControls;
    }

    public void setShowTouchscreenControls(boolean showTouchscreenControls) {
        this.showTouchscreenControls = showTouchscreenControls;
    }

    public int getPrimaryColor() {
        return Color.argb((int)(overlayOpacity * 255), 255, 255, 255);
    }

    public int getSecondaryColor() {
        return Color.argb((int)(overlayOpacity * 255), 2, 119, 189);
    }

    private synchronized ControlElement intersectElement(float x, float y) {
        if (profile != null) {
            for (ControlElement element : profile.getElements()) {
                if (element.containsPoint(x, y)) return element;
            }
        }
        return null;
    }

    public Paint getPaint() {
        return paint;
    }

    public Path getPath() {
        return path;
    }

    public ColorFilter getColorFilter() {
        return colorFilter;
    }

    public void setTouchpadView(TouchpadView touchpadView) {
        this.touchpadView = touchpadView;
    }

    public void setXServer(XServer xServer) {
        this.xServer = xServer;
        createMouseMoveTimer();
    }

    public int getMaxWidth() {
        return (int)Mathf.roundTo(getWidth(), snappingSize);
    }

    public int getMaxHeight() {
        return (int)Mathf.roundTo(getHeight(), snappingSize);
    }

    private void createMouseMoveTimer() {
        if (profile != null && mouseMoveTimer == null) {
            final float cursorSpeed = profile.getCursorSpeed();
            mouseMoveTimer = new Timer();
            mouseMoveTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    xServer.injectPointerMoveDelta((int)(mouseMoveOffset.x * 10 * cursorSpeed), (int)(mouseMoveOffset.y * 10 * cursorSpeed));
                }
            }, 0, 1000 / 60);
        }
    }

    private void processJoystickInput(MotionEvent event, ExternalController controller, int historyPos) {
        ExternalControllerBinding controllerBinding;
        final int[] axes = {MotionEvent.AXIS_X, MotionEvent.AXIS_Y, MotionEvent.AXIS_Z, MotionEvent.AXIS_RZ, MotionEvent.AXIS_HAT_X, MotionEvent.AXIS_HAT_Y};
        for (int axis : axes) {
            float value = ExternalController.getCenteredAxis(event, axis, historyPos);
            if (Math.abs(value) > JOYSTICK_DEAD_ZONE) {
                controllerBinding = controller.getControllerBinding(ExternalControllerBinding.getKeyCodeForAxis(axis, Mathf.sign(value)));
                if (controllerBinding != null) handleInputEvent(controllerBinding.getBinding(), true, value);
            }
            else {
                controllerBinding = controller.getControllerBinding(ExternalControllerBinding.getKeyCodeForAxis(axis, (byte) 1));
                if (controllerBinding != null) handleInputEvent(controllerBinding.getBinding(), false, value);
                controllerBinding = controller.getControllerBinding(ExternalControllerBinding.getKeyCodeForAxis(axis, (byte)-1));
                if (controllerBinding != null) handleInputEvent(controllerBinding.getBinding(), false, value);
            }
        }
    }

    private void processTriggerButton(MotionEvent event, ExternalController controller) {
        ExternalControllerBinding controllerBinding;
        controllerBinding = controller.getControllerBinding(ExternalControllerBinding.AXIS_LTRIGGER);
        if (controllerBinding != null) {
            boolean pressed = event.getAxisValue(MotionEvent.AXIS_LTRIGGER) == 1 || event.getAxisValue(MotionEvent.AXIS_BRAKE) == 1;
            handleInputEvent(controllerBinding.getBinding(), pressed);
        }

        controllerBinding = controller.getControllerBinding(ExternalControllerBinding.AXIS_RTRIGGER);
        if (controllerBinding != null) {
            boolean pressed = event.getAxisValue(MotionEvent.AXIS_RTRIGGER) == 1 || event.getAxisValue(MotionEvent.AXIS_GAS) == 1;
            handleInputEvent(controllerBinding.getBinding(), pressed);
        }
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        if (!editMode && profile != null) {
            ExternalController controller = profile.getController(event.getDeviceId());
            if (controller != null) {
                if (ExternalController.isDPadDevice(event)) {
                    processTriggerButton(event, controller);
                    processJoystickInput(event, controller, -1);
                    return true;
                }
                else if (ExternalController.isJoystickDevice(event)) {
                    int historySize = event.getHistorySize();
                    for (int i = 0; i < historySize; i++) processJoystickInput(event, controller, i);
                    processJoystickInput(event, controller, -1);
                    return true;
                }
            }
        }

        return super.onGenericMotionEvent(event);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (editMode && readyToDraw) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN: {
                    float x = event.getX();
                    float y = event.getY();

                    ControlElement element = intersectElement(x, y);
                    moveCursor = true;
                    if (element != null) {
                        offsetX = x - element.getX();
                        offsetY = y - element.getY();
                        moveCursor = false;
                    }

                    selectElement(element);
                    break;
                }
                case MotionEvent.ACTION_MOVE: {
                    if (selectedElement != null) {
                        selectedElement.setX((int)Mathf.roundTo(event.getX() - offsetX, snappingSize));
                        selectedElement.setY((int)Mathf.roundTo(event.getY() - offsetY, snappingSize));
                        invalidate();
                    }
                    break;
                }
                case MotionEvent.ACTION_UP: {
                    if (selectedElement != null && profile != null) profile.save();
                    if (moveCursor) cursor.set((int)Mathf.roundTo(event.getX(), snappingSize), (int)Mathf.roundTo(event.getY(), snappingSize));
                    invalidate();
                    break;
                }
            }
        }

        if (!editMode && profile != null) {
            int actionIndex = event.getActionIndex();
            int pointerId = event.getPointerId(actionIndex);
            int actionMasked = event.getActionMasked();
            boolean handled = false;

            switch (actionMasked) {
                case MotionEvent.ACTION_DOWN:
                case MotionEvent.ACTION_POINTER_DOWN: {
                    float x = event.getX(actionIndex);
                    float y = event.getY(actionIndex);

                    touchpadView.setPointerButtonLeftEnabled(true);
                    for (ControlElement element : profile.getElements()) {
                        if (element.handleTouchDown(pointerId, x, y)) handled = true;
                        if (element.getBindingAt(0) == Binding.MOUSE_LEFT_BUTTON) {
                            touchpadView.setPointerButtonLeftEnabled(false);
                        }
                    }
                    if (!handled) touchpadView.onTouchEvent(event);
                    break;
                }
                case MotionEvent.ACTION_MOVE: {
                    for (byte i = 0, count = (byte)event.getPointerCount(); i < count; i++) {
                        float x = event.getX(i);
                        float y = event.getY(i);

                        handled = false;
                        for (ControlElement element : profile.getElements()) {
                            if (element.handleTouchMove(i, x, y)) handled = true;
                        }
                        if (!handled) touchpadView.onTouchEvent(event);
                    }
                    break;
                }
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_POINTER_UP:
                    for (ControlElement element : profile.getElements()) if (element.handleTouchUp(pointerId)) handled = true;
                    if (!handled) touchpadView.onTouchEvent(event);
                    break;
                case MotionEvent.ACTION_CANCEL:
                    for (ControlElement element : profile.getElements()) if (element.handleTouchUp(pointerId)) handled = true;
                    if (!handled) touchpadView.onTouchEvent(event);
                    break;
            }
        }
        return true;
    }

    public boolean onKeyEvent(KeyEvent event) {
        if (profile != null && event.getRepeatCount() == 0) {
            ExternalController controller = profile.getController(event.getDeviceId());
            if (controller != null) {
                ExternalControllerBinding controllerBinding = controller.getControllerBinding(event.getKeyCode());
                if (controllerBinding != null) {
                    int action = event.getAction();

                    if (action == KeyEvent.ACTION_DOWN) {
                        handleInputEvent(controllerBinding.getBinding(), true);
                    }
                    else if (action == KeyEvent.ACTION_UP) {
                        handleInputEvent(controllerBinding.getBinding(), false);
                    }
                    return true;
                }
            }
        }
        return false;
    }

    public void handleInputEvent(Binding binding, boolean isActionDown) {
        handleInputEvent(binding, isActionDown, 0);
    }

    public void handleInputEvent(Binding binding, boolean isActionDown, float offset) {
        if (isActionDown) {
            if (binding == Binding.MOUSE_MOVE_LEFT || binding == Binding.MOUSE_MOVE_RIGHT) {
                mouseMoveOffset.x = offset != 0 ? offset : (binding == Binding.MOUSE_MOVE_LEFT ? -1 : 1);
                createMouseMoveTimer();
            }
            else if (binding == Binding.MOUSE_MOVE_DOWN || binding == Binding.MOUSE_MOVE_UP) {
                mouseMoveOffset.y = offset != 0 ? offset : (binding == Binding.MOUSE_MOVE_UP ? -1 : 1);
                createMouseMoveTimer();
            }
            else {
                Pointer.Button pointerButton = binding.getPointerButton();
                if (pointerButton != null) {
                    xServer.injectPointerButtonPress(pointerButton);
                }
                else xServer.injectKeyPress(binding.keycode);
            }
        }
        else {
            if (binding == Binding.MOUSE_MOVE_LEFT || binding == Binding.MOUSE_MOVE_RIGHT) {
                if (Math.abs(offset) < InputControlsView.JOYSTICK_DEAD_ZONE) mouseMoveOffset.x = 0;
            }
            else if (binding == Binding.MOUSE_MOVE_UP || binding == Binding.MOUSE_MOVE_DOWN) {
                if (Math.abs(offset) < InputControlsView.JOYSTICK_DEAD_ZONE) mouseMoveOffset.y = 0;
            }
            else {
                Pointer.Button pointerButton = binding.getPointerButton();
                if (pointerButton != null) {
                    xServer.injectPointerButtonRelease(pointerButton);
                }
                else xServer.injectKeyRelease(binding.keycode);
            }
        }
    }

    public Bitmap getIcon(byte id) {
        if (icons[id] == null) {
            Context context = getContext();
            try (InputStream is = context.getAssets().open("inputcontrols/icons/"+id+".png")) {
                icons[id] = BitmapFactory.decodeStream(is);
            }
            catch (IOException e) {}
        }
        return icons[id];
    }
}
