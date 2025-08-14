package com.winlator.cmod.contentdialog;

import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.util.SparseBooleanArray;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.BaseInputConnection;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;

import com.winlator.cmod.R;
import com.winlator.cmod.XrActivity;
import com.winlator.cmod.core.AppUtils;
import com.winlator.cmod.core.Callback;
import com.winlator.cmod.inputcontrols.ControllerManager;
import com.winlator.cmod.xserver.Drawable;

import java.util.ArrayList;

public class ContentDialog extends Dialog {
    public Runnable onConfirmCallback;
    private Runnable onCancelCallback;
    private final View contentView;

    //OpenXR compatible rendering
    private static int counter;
    private static int[] pixels;
    private static Bitmap bitmap;
    private static Canvas canvas;
    private static Drawable drawable;
    private static ArrayList<ContentDialog> instances = new ArrayList<>();

    private boolean isDarkMode;

    private Button gyroButton;

    public interface OnControllerInputListener {
        void onControllerInput(InputDevice device);
    }
    private OnControllerInputListener onControllerInputListener;

    public void setOnControllerInputListener(OnControllerInputListener listener) {
        this.onControllerInputListener = listener;
    }


    public ContentDialog(@NonNull Context context) {
        this(context, 0);
    }

    private View inflatedLayout;


    public ContentDialog(@NonNull Context context, int layoutResId) {
        super(context, R.style.ContentDialog);
        contentView = LayoutInflater.from(context).inflate(R.layout.content_dialog, null);


        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        isDarkMode = sharedPreferences.getBoolean("dark_mode", false);

//        contentView.setBackgroundResource(isDarkMode ? R.drawable.content_dialog_background_dark: R.drawable.content_dialog_background);

        if (isDarkMode) {
            this.getContext().setTheme(R.style.ContentDialog_Dark);
        }


        if (layoutResId > 0) {
            FrameLayout frameLayout = contentView.findViewById(R.id.FrameLayout);
            frameLayout.setVisibility(View.VISIBLE);
            View view = LayoutInflater.from(context).inflate(layoutResId, frameLayout, false);
            frameLayout.addView(view);
        }

        View confirmButton = contentView.findViewById(R.id.BTConfirm);
        confirmButton.setOnClickListener((v) -> {
            if (onConfirmCallback != null) onConfirmCallback.run();
            dismiss();
        });

        View cancelButton = contentView.findViewById(R.id.BTCancel);
        cancelButton.setOnClickListener((v) -> {
            if (onCancelCallback != null) onCancelCallback.run();
            dismiss();
        });

        setContentView(contentView);
    }

    public View getInflatedLayout() {
        return inflatedLayout;
    }

    public View getContentView() {
        return contentView;
    }

    public void setOnConfirmCallback(Runnable onConfirmCallback) {
        this.onConfirmCallback = onConfirmCallback;
    }

    public void setOnCancelCallback(Runnable onCancelCallback) {
        this.onCancelCallback = onCancelCallback;
    }

    @Override
    public void setTitle(int titleResId) {
        setTitle(getContext().getString(titleResId));
    }

    public void setIcon(int iconResId) {
        ImageView imageView = findViewById(R.id.IVIcon);
        imageView.setImageResource(iconResId);
        imageView.setVisibility(View.VISIBLE);
    }

    public void setTitle(String title) {
        LinearLayout titleBar = findViewById(R.id.LLTitleBar);
        TextView tvTitle = findViewById(R.id.TVTitle);

        if (title != null && !title.isEmpty()) {
            tvTitle.setText(title);
            titleBar.setVisibility(View.VISIBLE);
        }
        else {
            tvTitle.setText("");
            titleBar.setVisibility(View.GONE);
        }
    }

    public void setBottomBarText(String bottomBarText) {
        TextView tvBottomBarText = findViewById(R.id.TVBottomBarText);

        if (bottomBarText != null && !bottomBarText.isEmpty()) {
            tvBottomBarText.setText(bottomBarText);
            tvBottomBarText.setVisibility(View.VISIBLE);
        }
        else {
            tvBottomBarText.setText("");
            tvBottomBarText.setVisibility(View.GONE);
        }
    }

    public void setMessage(int msgResId) {
        setMessage(getContext().getString(msgResId));
    }

    public void setMessage(String message) {
        TextView tvMessage = findViewById(R.id.TVMessage);

        if (message != null && !message.isEmpty()) {
            tvMessage.setText(message);
            tvMessage.setVisibility(View.VISIBLE);
        }
        else {
            tvMessage.setText("");
            tvMessage.setVisibility(View.GONE);
        }
    }

    public static void alert(Context context, int msgResId, Runnable callback) {
        ContentDialog dialog = new ContentDialog(context);
        dialog.setMessage(msgResId);
        dialog.setOnConfirmCallback(callback);
        dialog.findViewById(R.id.BTCancel).setVisibility(View.GONE);
        dialog.show();
    }

    public static void alert(Context context, String msg, Runnable callback) {
        ContentDialog dialog = new ContentDialog(context);
        dialog.setMessage(msg);
        dialog.setOnConfirmCallback(callback);
        dialog.findViewById(R.id.BTCancel).setVisibility(View.GONE);
        dialog.show();
    }

    public static void confirm(Context context, int msgResId, Runnable callback) {
        ContentDialog dialog = new ContentDialog(context);
        dialog.setMessage(msgResId);
        dialog.setOnConfirmCallback(callback);
        dialog.show();
    }

    public static void confirm(Context context, String msg, Runnable callback) {
        ContentDialog dialog = new ContentDialog(context);
        dialog.setMessage(msg);
        dialog.setOnConfirmCallback(callback);
        dialog.show();
    }

    public static void prompt(Context context, int titleResId, String defaultText, Callback<String> callback) {
        ContentDialog dialog = new ContentDialog(context);

        final EditText editText = dialog.findViewById(R.id.EditText);

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        boolean isDarkMode = sharedPreferences.getBoolean("dark_mode", false);
        applyDarkThemeToEditText(editText, isDarkMode);

        editText.setHint(R.string.untitled);
        if (defaultText != null) editText.setText(defaultText);
        editText.setVisibility(View.VISIBLE);

        dialog.setTitle(titleResId);
        dialog.setOnConfirmCallback(() -> {
            String text = editText.getText().toString().trim();
            if (!text.isEmpty()) callback.call(text);
        });

        dialog.show();
    }

    private static void applyDarkThemeToEditText(EditText editText, boolean isDarkMode) {
        if (isDarkMode) {
            editText.setTextColor(Color.WHITE); // Set text color to white for dark theme
            editText.setHintTextColor(Color.GRAY); // Set hint color to gray
            editText.setBackgroundResource(R.drawable.edit_text_dark); // Custom dark background drawable
        } else {
            editText.setTextColor(Color.BLACK); // Default text color
            editText.setHintTextColor(Color.GRAY); // Default hint color
            editText.setBackgroundResource(R.drawable.edit_text); // Custom light background drawable
        }
    }

    public static void showMultipleChoiceList(Context context, int titleResId, final String[] items, Callback<ArrayList<Integer>> callback) {
        ContentDialog dialog = new ContentDialog(context);

        final ListView listView = dialog.findViewById(R.id.ListView);
        listView.getLayoutParams().width = AppUtils.getPreferredDialogWidth(context);
        listView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
        listView.setAdapter(new ArrayAdapter<>(context, android.R.layout.simple_list_item_multiple_choice, items));
        listView.setVisibility(View.VISIBLE);

        dialog.setTitle(titleResId);
        dialog.setOnConfirmCallback(() -> {
            ArrayList<Integer> result = new ArrayList<>();
            SparseBooleanArray checkedItemPositions = listView.getCheckedItemPositions();
            for (int i = 0; i < checkedItemPositions.size(); i++) {
                if (checkedItemPositions.valueAt(i)) result.add(checkedItemPositions.keyAt(i));
            }
            callback.call(result);
        });

        dialog.show();
    }

    public static void showSingleChoiceList(Context context, int titleResId, final String[] items, Callback<Integer> callback) {
        ContentDialog dialog = new ContentDialog(context);
        dialog.getContentView().findViewById(R.id.BTConfirm).setVisibility(View.GONE);

        final ListView listView = dialog.findViewById(R.id.ListView);
        listView.getLayoutParams().width = AppUtils.getPreferredDialogWidth(context);
        listView.setChoiceMode(ListView.CHOICE_MODE_NONE);
        listView.setAdapter(new ArrayAdapter<>(context, android.R.layout.simple_list_item_single_choice, items));
        listView.setVisibility(View.VISIBLE);
        listView.setOnItemClickListener((parent, view, position, id) -> {
            callback.call(position);
            dialog.dismiss();
        });

        dialog.setTitle(titleResId);
        dialog.show();
    }

    @Override
    public void show() {
        super.show();
        instances.add(this);
    }

    @Override
    public void dismiss() {
        instances.remove(this);
        super.dismiss();
    }

    public Drawable getDrawable() {
        if (counter++ > 10) {
            XrActivity.getInstance().runOnUiThread(this::redraw);
            counter = 0;
        }
        return drawable;
    }

    public void onKeyAction(int keyCode) {
        BaseInputConnection input = new BaseInputConnection(contentView, true);
        input.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, keyCode));
        input.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, keyCode));
    }

    public void redraw() {
        //Check if the view is ready
        View v = getContentView();
        if (v == null) {
            return;
        }
        int w = v.getMeasuredWidth();
        int h = v.getMeasuredHeight();
        if (w * h == 0) {
            return;
        }

        //Allocate render arrays
        if ((pixels == null) || (bitmap.getWidth() != w) || (bitmap.getHeight() != h)) {
            pixels = new int[w * h];
            bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            canvas = new Canvas(bitmap);
            drawable = Drawable.fromBitmap(bitmap);
        }

        //Apply background
        android.graphics.drawable.Drawable background = v.getBackground();
        if (background != null) {
            background.draw(canvas);
        } else {
            canvas.drawColor(Color.WHITE);
        }

        //Render window
        v.draw(canvas);

        //Double buffering
        if (bitmap != null) {
            drawable.drawBitmap(bitmap);
        }
    }

    public static ContentDialog getFrontInstance() {
        return instances.isEmpty() ? null : instances.get(instances.size() - 1);
    }

    @Override
    public boolean dispatchKeyEvent(@NonNull KeyEvent event) {
        // If we are actively listening for controller input...
        if (onControllerInputListener != null && event.getAction() == KeyEvent.ACTION_DOWN) {
            InputDevice device = event.getDevice();
            // And the event is from a real, physical game controller...
            if (device != null && !device.isVirtual() && ControllerManager.isGameController(device)) {
                // ...then trigger our callback and consume the event so it doesn't do anything else.
                onControllerInputListener.onControllerInput(device);
                return true;
            }
        }
        // Otherwise, process the key event normally (e.g., for keyboard input in an EditText).
        return super.dispatchKeyEvent(event);
    }

    public static class ConfirmationResult {
        public final boolean confirmed;
        public final boolean checkboxChecked;

        public ConfirmationResult(boolean confirmed, boolean checkboxChecked) {
            this.confirmed = confirmed;
            this.checkboxChecked = checkboxChecked;
        }
    }

    public static void confirmWithCheckbox(Context context, String message, String checkboxText, Callback<ConfirmationResult> callback) {
        ContentDialog dialog = new ContentDialog(context);
        dialog.setMessage(message);

        final CheckBox checkBox = dialog.findViewById(R.id.CBExtraOption);
        final View confirmButton = dialog.findViewById(R.id.BTConfirm);

        checkBox.setText(checkboxText);
        checkBox.setVisibility(View.VISIBLE);
        checkBox.setChecked(false);

        // Link the checkbox to the OK button's state
        checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            confirmButton.setEnabled(!isChecked);
            // Add this line to visually fade the button when disabled
            confirmButton.setAlpha(isChecked ? 0.5f : 1.0f);
        });

        dialog.setOnConfirmCallback(() -> {
            callback.call(new ConfirmationResult(true, false));
        });
        dialog.setOnCancelCallback(() -> {
            callback.call(new ConfirmationResult(false, checkBox.isChecked()));
        });

        dialog.show();
    }


}
