package com.winlator;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.collection.ArrayMap;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.preference.PreferenceManager;

import com.google.android.material.navigation.NavigationView;
import com.winlator.box86_64.Box86_64Preset;
import com.winlator.box86_64.Box86_64PresetManager;
import com.winlator.container.Container;
import com.winlator.container.ContainerManager;
import com.winlator.box86_64.Box86_64EditPresetDialog;
import com.winlator.core.AppUtils;
import com.winlator.core.Callback;
import com.winlator.core.FileUtils;
import com.winlator.core.GPUInformation;
import com.winlator.core.OBBImageInstaller;
import com.winlator.core.PreloaderDialog;
import com.winlator.core.StringUtils;
import com.winlator.core.WineInfo;
import com.winlator.core.WineUtils;
import com.winlator.contentdialog.ContentDialog;
import com.winlator.xenvironment.ImageFs;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executors;

public class SettingsFragment extends Fragment {
    public static final String DEFAULT_BOX86_VERSION = "0.3.0";
    public static final String DEFAULT_BOX64_VERSION = "0.2.5";
    private Callback<Uri> selectWineFileCallback;
    private PreloaderDialog preloaderDialog;
    private SharedPreferences preferences;

    public static String getDefaultTurnipVersion(Context context) {
        return GPUInformation.isAdreno6xx(context) ? "23.1.6" : "23.3.0";
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(false);
        preloaderDialog = new PreloaderDialog(getActivity());
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        ((AppCompatActivity)getActivity()).getSupportActionBar().setTitle(R.string.settings);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (requestCode == MainActivity.OPEN_FILE_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            try {
                if (selectWineFileCallback != null && data != null) selectWineFileCallback.call(data.getData());
            }
            catch (Exception e) {
                AppUtils.showToast(getContext(), R.string.unable_to_import_profile);
            }
            selectWineFileCallback = null;
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.settings_fragment, container, false);
        final Context context = getContext();
        preferences = PreferenceManager.getDefaultSharedPreferences(context);

        final Spinner sBox86Version = view.findViewById(R.id.SBox86Version);
        AppUtils.setSpinnerSelectionFromIdentifier(sBox86Version, preferences.getString("box86_version", DEFAULT_BOX86_VERSION));

        final Spinner sBox64Version = view.findViewById(R.id.SBox64Version);
        AppUtils.setSpinnerSelectionFromIdentifier(sBox64Version, preferences.getString("box64_version", DEFAULT_BOX64_VERSION));

        final Spinner sBox86Preset = view.findViewById(R.id.SBox86Preset);
        final Spinner sBox64Preset = view.findViewById(R.id.SBox64Preset);
        loadBox86_64PresetSpinners(view, sBox86Preset, sBox64Preset);

        final Spinner sTurnipVersion = view.findViewById(R.id.STurnipVersion);
        AppUtils.setSpinnerSelectionFromIdentifier(sTurnipVersion, preferences.getString("turnip_version", getDefaultTurnipVersion(context)));

        final CheckBox cbUseDRI3 = view.findViewById(R.id.CBUseDRI3);
        cbUseDRI3.setChecked(preferences.getBoolean("use_dri3", true));

        final TextView tvCursorSpeed = view.findViewById(R.id.TVCursorSpeed);
        final SeekBar sbCursorSpeed = view.findViewById(R.id.SBCursorSpeed);
        sbCursorSpeed.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                tvCursorSpeed.setText(progress+"%");
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        sbCursorSpeed.setProgress((int)(preferences.getFloat("cursor_speed", 1.0f) * 100));

        loadInstalledWineList(view);

        view.findViewById(R.id.BTSelectWineFile).setOnClickListener((v) -> {
            selectWineFileCallback = (uri) -> {
                preloaderDialog.show(R.string.preparing_installation);
                WineUtils.extractWineFileForInstallAsync(context, uri, (wineDir) -> {
                    if (wineDir != null) {
                        WineUtils.findWineVersionAsync(context, wineDir, (wineInfo) -> {
                            preloaderDialog.closeOnUiThread();
                            if (wineInfo == null) {
                                AppUtils.showToast(context, R.string.unable_to_install_wine);
                                return;
                            }

                            getActivity().runOnUiThread(() -> showWineInstallOptionsDialog(wineInfo));
                        });
                    }
                    else {
                        AppUtils.showToast(context, R.string.unable_to_install_wine);
                        preloaderDialog.closeOnUiThread();
                    }
                });
            };
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            getActivity().startActivityFromFragment(this, intent, MainActivity.OPEN_FILE_REQUEST_CODE);
        });

        final Runnable updateUI = () -> {
            String obbImageVersion = ImageFs.find(context).getFormattedVersion();
            ((TextView)view.findViewById(R.id.TVOBBImageVersion)).setText(context.getString(R.string.installed_version)+" "+obbImageVersion);
        };
        updateUI.run();

        view.findViewById(R.id.BTInstallOBBImage).setOnClickListener((v) -> {
            final MainActivity activity = (MainActivity)getActivity();
            PopupMenu popupMenu = new PopupMenu(context, v);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) popupMenu.setForceShowIcon(true);
            popupMenu.inflate(R.menu.open_file_popup_menu);
            popupMenu.setOnMenuItemClickListener((menuItem) -> {
                int itemId = menuItem.getItemId();
                if (itemId == R.id.open_file) {
                    OBBImageInstaller.openFileForInstall(activity, updateUI);
                }
                else if (itemId == R.id.download_file) {
                    OBBImageInstaller.downloadFileForInstall(activity, updateUI);
                }
                return true;
            });
            popupMenu.show();
        });

        view.findViewById(R.id.BTConfirm).setOnClickListener((v) -> {
            SharedPreferences.Editor editor = preferences.edit();
            editor.putString("box86_version", StringUtils.parseIdentifier(sBox86Version.getSelectedItem()));
            editor.putString("box64_version", StringUtils.parseIdentifier(sBox64Version.getSelectedItem()));
            editor.putString("box86_preset", Box86_64PresetManager.getSpinnerSelectedId(sBox86Preset));
            editor.putString("box64_preset", Box86_64PresetManager.getSpinnerSelectedId(sBox64Preset));
            editor.putString("turnip_version", StringUtils.parseIdentifier(sTurnipVersion.getSelectedItem()));
            editor.putBoolean("use_dri3", cbUseDRI3.isChecked());
            editor.putFloat("cursor_speed", sbCursorSpeed.getProgress() / 100.0f);

            if (editor.commit()) {
                NavigationView navigationView = getActivity().findViewById(R.id.NavigationView);
                navigationView.setCheckedItem(R.id.main_menu_containers);
                FragmentManager fragmentManager = getParentFragmentManager();
                fragmentManager.beginTransaction()
                    .replace(R.id.FLFragmentContainer, new ContainersFragment())
                    .commit();
            }
        });

        return view;
    }

    private void loadBox86_64PresetSpinners(View view, final Spinner sBox86Preset, final Spinner sBox64Preset) {
        final ArrayMap<String, Spinner> spinners = new ArrayMap<String, Spinner>() {{
            put("box86", sBox86Preset);
            put("box64", sBox64Preset);
        }};
        final Context context = getContext();

        Callback<String> updateSpinner = (prefix) -> {
            Box86_64PresetManager.loadSpinner(prefix, spinners.get(prefix), preferences.getString(prefix+"_preset", Box86_64Preset.COMPATIBILITY));
        };

        Callback<String> onAddPreset = (prefix) -> {
            Box86_64EditPresetDialog dialog = new Box86_64EditPresetDialog(context, prefix, null);
            dialog.setOnConfirmCallback(() -> updateSpinner.call(prefix));
            dialog.show();
        };

        Callback<String> onEditPreset = (prefix) -> {
            Box86_64EditPresetDialog dialog = new Box86_64EditPresetDialog(context, prefix, Box86_64PresetManager.getSpinnerSelectedId(spinners.get(prefix)));
            dialog.setOnConfirmCallback(() -> updateSpinner.call(prefix));
            dialog.show();
        };

        Callback<String> onDuplicatePreset = (prefix) -> ContentDialog.confirm(context, R.string.do_you_want_to_duplicate_this_preset, () -> {
            Spinner spinner = spinners.get(prefix);
            Box86_64PresetManager.duplicatePreset(prefix, context, Box86_64PresetManager.getSpinnerSelectedId(spinner));
            updateSpinner.call(prefix);
            spinner.setSelection(spinner.getCount()-1);
        });

        Callback<String> onRemovePreset = (prefix) -> {
            final String presetId = Box86_64PresetManager.getSpinnerSelectedId(spinners.get(prefix));
            if (!presetId.startsWith(Box86_64Preset.CUSTOM)) {
                AppUtils.showToast(context, R.string.you_cannot_remove_this_preset);
                return;
            }
            ContentDialog.confirm(context, R.string.do_you_want_to_remove_this_preset, () -> {
                Box86_64PresetManager.removePreset(prefix, context, presetId);
                updateSpinner.call(prefix);
            });
        };

        updateSpinner.call("box86");
        updateSpinner.call("box64");

        view.findViewById(R.id.BTAddBox86Preset).setOnClickListener((v) -> onAddPreset.call("box86"));
        view.findViewById(R.id.BTEditBox86Preset).setOnClickListener((v) -> onEditPreset.call("box86"));
        view.findViewById(R.id.BTDuplicateBox86Preset).setOnClickListener((v) -> onDuplicatePreset.call("box86"));
        view.findViewById(R.id.BTRemoveBox86Preset).setOnClickListener((v) -> onRemovePreset.call("box86"));

        view.findViewById(R.id.BTAddBox64Preset).setOnClickListener((v) -> onAddPreset.call("box64"));
        view.findViewById(R.id.BTEditBox64Preset).setOnClickListener((v) -> onEditPreset.call("box64"));
        view.findViewById(R.id.BTDuplicateBox64Preset).setOnClickListener((v) -> onDuplicatePreset.call("box64"));
        view.findViewById(R.id.BTRemoveBox64Preset).setOnClickListener((v) -> onRemovePreset.call("box64"));
    }

    private void removeInstalledWine(WineInfo wineInfo, Runnable onSuccess) {
        final Activity activity = getActivity();
        ContainerManager manager = new ContainerManager(activity);

        ArrayList<Container> containers = manager.getContainers();
        for (Container container : containers) {
            if (container.getWineVersion().equals(wineInfo.identifier())) {
                AppUtils.showToast(activity, R.string.unable_to_remove_this_wine_version);
                return;
            }
        }

        String suffix = wineInfo.fullVersion()+"-"+wineInfo.getArch();
        File installedWineDir = ImageFs.find(activity).getInstalledWineDir();
        File wineDir = new File(wineInfo.path);
        File containerPatternFile = new File(installedWineDir, "container-pattern-"+suffix+".tzst");

        if (!wineDir.isDirectory() || !containerPatternFile.isFile()) {
            AppUtils.showToast(activity, R.string.unable_to_remove_this_wine_version);
            return;
        }

        preloaderDialog.show(R.string.removing_wine);
        Executors.newSingleThreadExecutor().execute(() -> {
            FileUtils.delete(wineDir);
            FileUtils.delete(containerPatternFile);
            preloaderDialog.closeOnUiThread();
            if (onSuccess != null) activity.runOnUiThread(onSuccess);
        });
    }

    private void loadInstalledWineList(final View view) {
        Context context = getContext();
        LinearLayout container = view.findViewById(R.id.LLInstalledWineList);
        container.removeAllViews();
        ArrayList<WineInfo> wineInfos = WineUtils.getInstalledWineInfos(context);

        LayoutInflater inflater = LayoutInflater.from(context);
        for (final WineInfo wineInfo : wineInfos) {
            View itemView = inflater.inflate(R.layout.installed_wine_list_item, container, false);
            ((TextView)itemView.findViewById(R.id.TVTitle)).setText(wineInfo.toString());
            if (wineInfo != WineInfo.MAIN_WINE_VERSION) {
                View removeButton = itemView.findViewById(R.id.BTRemove);
                removeButton.setVisibility(View.VISIBLE);
                removeButton.setOnClickListener((v) -> {
                    ContentDialog.confirm(getContext(), R.string.do_you_want_to_remove_this_wine_version, () -> {
                        removeInstalledWine(wineInfo, () -> loadInstalledWineList(view));
                    });
                });
            }
            container.addView(itemView);
        }
    }

    private void installWine(final WineInfo wineInfo) {
        Context context = getContext();
        File installedWineDir = ImageFs.find(context).getInstalledWineDir();

        File wineDir = new File(installedWineDir, wineInfo.identifier());
        if (wineDir.isDirectory()) {
            AppUtils.showToast(context, R.string.unable_to_install_wine);
            return;
        }

        Intent intent = new Intent(context, XServerDisplayActivity.class);
        intent.putExtra("generate_wineprefix", true);
        intent.putExtra("wine_info", wineInfo);
        context.startActivity(intent);
    }

    private void showWineInstallOptionsDialog(final WineInfo wineInfo) {
        Context context = getContext();
        ContentDialog dialog = new ContentDialog(context, R.layout.wine_install_options_dialog);
        dialog.setCancelable(false);
        dialog.setCanceledOnTouchOutside(false);
        dialog.setTitle(R.string.install_wine);
        dialog.setIcon(R.drawable.icon_wine);

        EditText etVersion = dialog.findViewById(R.id.ETVersion);
        etVersion.setText("Wine "+wineInfo.version+(wineInfo.subversion != null ? " ("+wineInfo.subversion+")" : ""));

        Spinner sArch = dialog.findViewById(R.id.SArch);
        List<String> archList = wineInfo.isWin64() ? Arrays.asList("x86", "x86_64") : Arrays.asList("x86");
        sArch.setAdapter(new ArrayAdapter<>(context, android.R.layout.simple_spinner_dropdown_item, archList));
        sArch.setSelection(archList.size()-1);

        dialog.setOnConfirmCallback(() -> {
            wineInfo.setArch(sArch.getSelectedItem().toString());
            installWine(wineInfo);
        });
        dialog.show();
    }
}
