package com.winlator.winhandler;

import android.app.ActivityManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.Build;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;

import com.winlator.R;
import com.winlator.XServerDisplayActivity;
import com.winlator.contentdialog.ContentDialog;
import com.winlator.core.CPUStatus;
import com.winlator.core.FileUtils;
import com.winlator.core.ProcessHelper;
import com.winlator.core.StringUtils;
import com.winlator.widget.CPUListView;
import com.winlator.xenvironment.ImageFs;
import com.winlator.xserver.Window;
import com.winlator.xserver.XLock;
import com.winlator.xserver.XServer;

import java.io.File;
import java.util.Timer;
import java.util.TimerTask;

public class TaskManagerDialog extends ContentDialog implements OnGetProcessInfoListener {
    private final XServerDisplayActivity activity;
    private final LayoutInflater inflater;
    private Timer timer;
    private final Object lock = new Object();

    private boolean isDarkMode;

    public TaskManagerDialog(XServerDisplayActivity activity) {
        super(activity, R.layout.task_manager_dialog);
        this.activity = activity;

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
        isDarkMode = sharedPreferences.getBoolean("dark_mode", false);

        setCancelable(false);
        setTitle(R.string.task_manager);
        setIcon(R.drawable.icon_task_manager);

        Button cancelButton = findViewById(R.id.BTCancel);
        cancelButton.setText(R.string.new_task);
        cancelButton.setOnClickListener((v) -> {
            dismiss();
            ContentDialog.prompt(activity, R.string.new_task, "taskmgr.exe", (command) -> activity.getWinHandler().exec(command));
        });


//        // Set the background color
//        findViewById(R.id.task_manager_container).setBackgroundColor(
//                ContextCompat.getColor(activity, isDarkMode ? R.color.window_background_color_dark : R.color.window_background_color)
//        );

        // Set Text Color
        int textColor = ContextCompat.getColor(activity, isDarkMode ? R.color.white : R.color.black);
        applyTextColorToViews(findViewById(android.R.id.content), textColor);

        // Apply dark theme bordered panel
//        LinearLayout llBorderedPanel = findViewById(R.id.LLBorderedPanel);
//        llBorderedPanel.setBackgroundResource(isDarkMode ? R.drawable.bordered_panel_dark : R.drawable.bordered_panel);

        int panelBackground = isDarkMode ? R.drawable.bordered_panel_dark : R.drawable.bordered_panel;
        findViewById(R.id.LLTableHead).setBackgroundResource(panelBackground);
        findViewById(R.id.LLCPUInfo).setBackgroundResource(panelBackground);
        findViewById(R.id.LLProcessList).setBackgroundResource(panelBackground);


        // Set styles for CPU and Memory info view
        updateCPUInfoViewStyle(isDarkMode);
        updateMemoryInfoViewStyle(isDarkMode);


        setOnDismissListener((dialog) -> {
            if (timer != null) {
                timer.cancel();
                timer = null;
            }

            activity.getWinHandler().setOnGetProcessInfoListener(null);
        });

        FileUtils.clear(getIconDir(activity));
        inflater = LayoutInflater.from(activity);
    }

    private void applyTextColorToViews(View rootView, int color) {
        if (rootView instanceof ViewGroup) {
            ViewGroup viewGroup = (ViewGroup) rootView;
            for (int i = 0; i < viewGroup.getChildCount(); i++) {
                applyTextColorToViews(viewGroup.getChildAt(i), color);
            }
        } else if (rootView instanceof TextView) {
            ((TextView) rootView).setTextColor(color);
        }
    }

    private void updateCPUInfoViewStyle(boolean isDarkMode) {
        LinearLayout llCPUInfo = findViewById(R.id.LLCPUInfo);
        TextView tvCPUTitle = findViewById(R.id.TVCPUTitle);
        tvCPUTitle.setTextColor(ContextCompat.getColor(activity, isDarkMode ? R.color.white : R.color.black));
    }

    private void updateMemoryInfoViewStyle(boolean isDarkMode) {
        TextView tvMemoryTitle = findViewById(R.id.TVMemoryTitle);
        tvMemoryTitle.setTextColor(ContextCompat.getColor(activity, isDarkMode ? R.color.white : R.color.black));
        TextView tvMemoryInfo = findViewById(R.id.TVMemoryInfo);
        tvMemoryInfo.setTextColor(ContextCompat.getColor(activity, isDarkMode ? R.color.white : R.color.black));
    }



    private void update() {
        synchronized (lock) {
            activity.getWinHandler().listProcesses();

            final LinearLayout container = findViewById(R.id.LLProcessList);
            if (container.getChildCount() == 0) findViewById(R.id.TVEmptyText).setVisibility(View.VISIBLE);
        }

        updateCPUInfoView();
        updateMemoryInfoView();
    }

    private void showListItemMenu(final View anchorView, final ProcessInfo processInfo) {
        PopupMenu listItemMenu = new PopupMenu(activity, anchorView);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) listItemMenu.setForceShowIcon(true);

        listItemMenu.inflate(R.menu.process_popup_menu);
        listItemMenu.setOnMenuItemClickListener((menuItem) -> {
            int itemId = menuItem.getItemId();
            final WinHandler winHandler = activity.getWinHandler();
            if (itemId == R.id.process_affinity) {
                showProcessorAffinityDialog(processInfo);
            }
            else if (itemId == R.id.bring_to_front) {
                winHandler.bringToFront(processInfo.name);
                dismiss();
            }
            else if (itemId == R.id.process_end) {
                ContentDialog.confirm(activity, R.string.do_you_want_to_end_this_process, () -> {
                    winHandler.killProcess(processInfo.name);
                });
            }
            return true;
        });
        listItemMenu.show();
    }

    private void showProcessorAffinityDialog(final ProcessInfo processInfo) {
        ContentDialog dialog = new ContentDialog(activity, R.layout.cpu_list_dialog);
        dialog.setTitle(processInfo.name);
        dialog.setIcon(R.drawable.icon_cpu);
        final CPUListView cpuListView = dialog.findViewById(R.id.CPUListView);
        cpuListView.setCheckedCPUList(processInfo.getCPUList());
        dialog.setOnConfirmCallback(() -> {
            WinHandler winHandler = activity.getWinHandler();
            winHandler.setProcessAffinity(processInfo.pid, ProcessHelper.getAffinityMask(cpuListView.getCheckedCPUList()));
            update();
        });
        dialog.show();
    }

    public static File getIconDir(Context context) {
        File iconDir = new File(ImageFs.find(context).getRootDir(), "home/xuser/.local/share/icons/taskmgr");
        if (!iconDir.isDirectory()) iconDir.mkdirs();
        return iconDir;
    }

    @Override
    public void show() {
        update();
        activity.getWinHandler().setOnGetProcessInfoListener(this);

        timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                activity.runOnUiThread(TaskManagerDialog.this::update);
            }
        }, 0, 1000);
        super.show();
    }

    @Override
    public void onGetProcessInfo(int index, int numProcesses, ProcessInfo processInfo) {
        activity.runOnUiThread(() -> {
            synchronized (lock) {
                final LinearLayout container = findViewById(R.id.LLProcessList);
                setBottomBarText(activity.getString(R.string.processes)+": " + numProcesses);

                if (numProcesses == 0) {
                    container.removeAllViews();
                    findViewById(R.id.TVEmptyText).setVisibility(View.VISIBLE);
                    return;
                }

                findViewById(R.id.TVEmptyText).setVisibility(View.GONE);

                int childCount = container.getChildCount();
                View itemView = index < childCount ? container.getChildAt(index) : inflater.inflate(R.layout.process_info_list_item, container, false);
                ((TextView)itemView.findViewById(R.id.TVName)).setText(processInfo.name+(processInfo.wow64Process ? " *32" : ""));
                ((TextView)itemView.findViewById(R.id.TVPID)).setText(String.valueOf(processInfo.pid));
                ((TextView)itemView.findViewById(R.id.TVMemoryUsage)).setText(processInfo.getFormattedMemoryUsage());
                itemView.findViewById(R.id.BTMenu).setOnClickListener((v) -> showListItemMenu(v, processInfo));

                XServer xServer = activity.getXServer();
                Window window;

                try (XLock xlock = xServer.lock(XServer.Lockable.WINDOW_MANAGER)) {
                    window = xServer.windowManager.findWindowWithProcessId(processInfo.pid);
                }

                ImageView ivIcon = itemView.findViewById(R.id.IVIcon);
                ivIcon.setImageResource(R.drawable.taskmgr_process);
                if (window != null) {
                    Bitmap icon = xServer.pixmapManager.getWindowIcon(window);
                    if (icon != null) ivIcon.setImageBitmap(icon);
                }

                if (index >= childCount) container.addView(itemView);

                if (index == numProcesses-1 && childCount > numProcesses) {
                    for (int i = childCount-1; i >= numProcesses; i--) container.removeViewAt(i);
                }
            }
        });
    }

    private void updateCPUInfoView() {
        LinearLayout llCPUInfo = findViewById(R.id.LLCPUInfo);
        llCPUInfo.removeAllViews();
        short[] clockSpeeds = CPUStatus.getCurrentClockSpeeds();
        int totalClockSpeed = 0;
        short maxClockSpeed = 0;

        for (int i = 0; i < clockSpeeds.length; i++) {
            TextView textView = new TextView(activity);
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            short clockSpeed = CPUStatus.getMaxClockSpeed(i);
            textView.setText(clockSpeeds[i]+"/"+clockSpeed+" MHz");
            llCPUInfo.addView(textView);
            totalClockSpeed += clockSpeeds[i];
            maxClockSpeed = (short)Math.max(maxClockSpeed, clockSpeed);
        }

        int avgClockSpeed = totalClockSpeed / clockSpeeds.length;
        TextView tvCPUTitle = findViewById(R.id.TVCPUTitle);
        byte cpuUsagePercent = (byte)(((float)avgClockSpeed / maxClockSpeed) * 100.0f);
        tvCPUTitle.setText("CPU ("+cpuUsagePercent+"%)");
    }

    private void updateMemoryInfoView() {
        ActivityManager activityManager = (ActivityManager)activity.getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
        activityManager.getMemoryInfo(memoryInfo);
        long usedMem = memoryInfo.totalMem - memoryInfo.availMem;
        byte memUsagePercent = (byte)(((double)usedMem / memoryInfo.totalMem) * 100.0f);

        TextView tvMemoryTitle = findViewById(R.id.TVMemoryTitle);
        tvMemoryTitle.setText(activity.getString(R.string.memory)+" ("+memUsagePercent+"%)");

        TextView tvMemoryInfo = findViewById(R.id.TVMemoryInfo);
        tvMemoryInfo.setText(StringUtils.formatBytes(usedMem, false)+"/"+StringUtils.formatBytes(memoryInfo.totalMem));
    }
}
