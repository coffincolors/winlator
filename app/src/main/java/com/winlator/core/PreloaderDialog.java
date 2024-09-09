package com.winlator.core;

import android.app.Activity;
import android.app.Dialog;
import android.util.Log;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;

import com.winlator.R;

public class PreloaderDialog {
    private final Activity activity;
    private Dialog dialog;

    public PreloaderDialog(Activity activity) {
        this.activity = activity;
    }

    private void create() {
        if (dialog != null) return;
        dialog = new Dialog(activity, android.R.style.Theme_Translucent_NoTitleBar_Fullscreen);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(false);
        dialog.setCanceledOnTouchOutside(false);
        dialog.setContentView(R.layout.preloader_dialog);

        Window window = dialog.getWindow();
        if (window != null) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
            window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
        }
    }

    public synchronized void show(int textResId) {
        Log.d("PreloaderDialog", "Attempting to show preloader dialog with textResId: " + textResId);
        if (isShowing()) {
            Log.d("PreloaderDialog", "PreloaderDialog is already showing.");
            return;
        }
        close();
        if (dialog == null) create();
        ((TextView)dialog.findViewById(R.id.TextView)).setText(textResId);
        dialog.show();
        Log.d("PreloaderDialog", "Preloader dialog is now shown.");
    }

    public void showOnUiThread(final int textResId) {
        activity.runOnUiThread(() -> show(textResId));
    }

    public synchronized void close() {
        Log.d("PreloaderDialog", "Attempting to close preloader dialog.");
        try {
            if (dialog != null) {
                dialog.dismiss();
                Log.d("PreloaderDialog", "Preloader dialog is dismissed.");
            }
        } catch (Exception e) {
            Log.e("PreloaderDialog", "Error while closing dialog: " + e.getMessage(), e);
        }
    }

    public void closeOnUiThread() {
        activity.runOnUiThread(this::close);
    }

    public boolean isShowing() {
        return dialog != null && dialog.isShowing();
    }
}
