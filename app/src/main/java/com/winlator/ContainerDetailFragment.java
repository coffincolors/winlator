package com.winlator;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;

import com.winlator.box86_64.Box86_64Preset;
import com.winlator.box86_64.Box86_64PresetManager;
import com.winlator.box86_64.rc.RCManager;
import com.winlator.container.Container;
import com.winlator.container.ContainerManager;
import com.winlator.contentdialog.AddEnvVarDialog;
import com.winlator.contentdialog.ContentDialog;
import com.winlator.contentdialog.DXVKConfigDialog;
import com.winlator.contentdialog.GraphicsDriverConfigDialog;
import com.winlator.contentdialog.VKD3DConfigDialog;
import com.winlator.contents.ContentProfile;
import com.winlator.contents.ContentsManager;
import com.winlator.core.AppUtils;
import com.winlator.core.Callback;
import com.winlator.core.DefaultVersion;
import com.winlator.core.EnvVars;
import com.winlator.core.FileUtils;
import com.winlator.core.KeyValueSet;
import com.winlator.core.PreloaderDialog;
import com.winlator.core.StringUtils;
import com.winlator.core.WineInfo;
import com.winlator.core.WineRegistryEditor;
import com.winlator.core.WineThemeManager;
import com.winlator.core.WineUtils;
import com.winlator.widget.CPUListView;
import com.winlator.widget.ColorPickerView;
import com.winlator.widget.EnvVarsView;
import com.winlator.widget.ImagePickerView;
import com.winlator.winhandler.WinHandler;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class ContainerDetailFragment extends Fragment {
    private ContainerManager manager;
    private ContentsManager contentsManager;
    private final int containerId;
    private static Container container;
    private PreloaderDialog preloaderDialog;
    private JSONArray gpuCards;
    private Callback<String> openDirectoryCallback;

    private String graphicsDriverVersion;

    private String tempGraphicsDriverVersion; // Temporary storage for the graphics driver version

    public ContainerDetailFragment() {
        this(0);
    }

    public ContainerDetailFragment(int containerId) {
        this.containerId = containerId;
    }


    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(false);
        preloaderDialog = new PreloaderDialog(getActivity());


        try {
            gpuCards = new JSONArray(FileUtils.readString(getContext(), "gpu_cards.json"));
        }
        catch (JSONException e) {}

//        // Initialize graphicsDriverVersion
//        if (isEditMode() && container != null) {
//            graphicsDriverVersion = container.getGraphicsDriverVersion();
//        } else {
//            graphicsDriverVersion = DefaultVersion.TURNIP;  // Set a default valid value
//        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (requestCode == MainActivity.OPEN_DIRECTORY_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            if (data != null) {
                Uri uri = data.getData();

                String path = FileUtils.getFilePathFromUri(getContext(), uri);

                if (path != null) {
                    if (openDirectoryCallback != null) {
                        openDirectoryCallback.call(path);
                    }
                } else {
                    Toast.makeText(getContext(), "Invalid directory selected", Toast.LENGTH_SHORT).show();
                }
            }
            openDirectoryCallback = null;
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        ((AppCompatActivity)getActivity()).getSupportActionBar().setTitle(isEditMode() ? R.string.edit_container : R.string.new_container);
    }

    public boolean isEditMode() {
        return container != null;
    }

    @SuppressLint("SetTextI18n")
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup root, @Nullable Bundle savedInstanceState) {
        final Context context = getContext();
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        final View view = inflater.inflate(R.layout.container_detail_fragment, root, false);
        manager = new ContainerManager(context);
        container = containerId > 0 ? manager.getContainerById(containerId) : null;
        contentsManager = new ContentsManager(context);
        contentsManager.syncContents();

        final EditText etName = view.findViewById(R.id.ETName);
        final Switch swContentProfile = view.findViewById(R.id.SWContentProfile);
        final Spinner sWineVersion = view.findViewById(R.id.SWineVersion);
        final TextView tvWineVersion = view.findViewById(R.id.TVWineVersion); // New TextView for displaying the selected version

        // Make sure the wine version layout is visible
        final LinearLayout llWineVersion = view.findViewById(R.id.LLWineVersion);
        llWineVersion.setVisibility(View.VISIBLE);

        if (isEditMode()) {
            etName.setText(container.getName());
            graphicsDriverVersion = container.getGraphicsDriverVersion();

            // Hide the toggle in edit mode
            swContentProfile.setVisibility(View.GONE);

            // Hide spinner and show TextView in edit mode
            sWineVersion.setVisibility(View.GONE);
            tvWineVersion.setVisibility(View.VISIBLE);

            // Retrieve and display the stored Wine version
            String storedWineVersion = preferences.getString("wine_version_" + container.getName(), "Default Version");
            tvWineVersion.setText(getString(R.string.wine_version_label, storedWineVersion));
        } else {
            etName.setText(getString(R.string.container) + "-" + manager.getNextContainerId());

            // Show the toggle in non-edit mode
            swContentProfile.setVisibility(View.VISIBLE);

            // Initialize Wine version spinner based on toggle state
            swContentProfile.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    loadContentProfileWineSpinner(view, sWineVersion);
                } else {
                    loadStandardWineSpinner(view, sWineVersion);
                }
            });

            // Initialize with standard Wine options by default
            loadStandardWineSpinner(view, sWineVersion);

            // Show spinner and hide TextView in non-edit mode
            sWineVersion.setVisibility(View.VISIBLE);
            tvWineVersion.setVisibility(View.GONE);
        }

        final ArrayList<WineInfo> wineInfos = WineUtils.getInstalledWineInfos(context);
        loadScreenSizeSpinner(view, isEditMode() ? container.getScreenSize() : Container.DEFAULT_SCREEN_SIZE);

        // Other UI elements setup...
        final Spinner sGraphicsDriver = view.findViewById(R.id.SGraphicsDriver);
        final Spinner sDXWrapper = view.findViewById(R.id.SDXWrapper);

        final View vDXWrapperConfig = view.findViewById(R.id.BTDXWrapperConfig);
        vDXWrapperConfig.setTag(isEditMode() ? container.getDXWrapperConfig() : "");

        final View vGraphicsDriverConfig = view.findViewById(R.id.BTGraphicsDriverConfig);

        setupDXWrapperSpinner(sDXWrapper, vDXWrapperConfig);
        loadGraphicsDriverSpinner(sGraphicsDriver, sDXWrapper, vGraphicsDriverConfig,
                isEditMode() ? container.getGraphicsDriver() : Container.DEFAULT_GRAPHICS_DRIVER,
                isEditMode() ? container.getDXWrapper() : Container.DEFAULT_DXWRAPPER);

        view.findViewById(R.id.BTHelpDXWrapper).setOnClickListener((v) -> AppUtils.showHelpBox(context, v, R.string.dxwrapper_help_content));

        Spinner sAudioDriver = view.findViewById(R.id.SAudioDriver);
        AppUtils.setSpinnerSelectionFromIdentifier(sAudioDriver, isEditMode() ? container.getAudioDriver() : Container.DEFAULT_AUDIO_DRIVER);

        final CheckBox cbShowFPS = view.findViewById(R.id.CBShowFPS);
        cbShowFPS.setChecked(isEditMode() && container.isShowFPS());

        final CheckBox cbFullscreenStretched = view.findViewById(R.id.CBFullscreenStretched);
        cbFullscreenStretched.setChecked(isEditMode() && container.isFullscreenStretched());

        final Runnable showInputWarning = () -> ContentDialog.alert(context, R.string.enable_xinput_and_dinput_same_time, null);
        final CheckBox cbEnableXInput = view.findViewById(R.id.CBEnableXInput);
        final CheckBox cbEnableDInput = view.findViewById(R.id.CBEnableDInput);
        final View llDInputType = view.findViewById(R.id.LLDinputMapperType);
        final View btHelpXInput = view.findViewById(R.id.BTXInputHelp);
        final View btHelpDInput = view.findViewById(R.id.BTDInputHelp);
        final Spinner SDInputType = view.findViewById(R.id.SDInputType);
        int inputType = isEditMode() ? container.getInputType() : WinHandler.DEFAULT_INPUT_TYPE;
        cbEnableXInput.setChecked((inputType & WinHandler.FLAG_INPUT_TYPE_XINPUT) == WinHandler.FLAG_INPUT_TYPE_XINPUT);
        cbEnableDInput.setChecked((inputType & WinHandler.FLAG_INPUT_TYPE_DINPUT) == WinHandler.FLAG_INPUT_TYPE_DINPUT);
        cbEnableDInput.setOnCheckedChangeListener((buttonView, isChecked) -> {
            llDInputType.setVisibility(isChecked?View.VISIBLE:View.GONE);
            if (isChecked && cbEnableXInput.isChecked())
                showInputWarning.run();
        });
        cbEnableXInput.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked && cbEnableDInput.isChecked())
                showInputWarning.run();
        });
        btHelpXInput.setOnClickListener(v -> AppUtils.showHelpBox(context, v, R.string.help_xinput));
        btHelpDInput.setOnClickListener(v -> AppUtils.showHelpBox(context, v, R.string.help_dinput));
        SDInputType.setSelection(((inputType & WinHandler.FLAG_DINPUT_MAPPER_STANDARD) == WinHandler.FLAG_DINPUT_MAPPER_STANDARD) ? 0 : 1);
        llDInputType.setVisibility(cbEnableDInput.isChecked()?View.VISIBLE:View.GONE);

        final EditText etLC_ALL = view.findViewById(R.id.ETlcall);
        Locale systemLocal = Locale.getDefault();
        etLC_ALL.setText(isEditMode() ? container.getLC_ALL() : systemLocal.getLanguage() + '_' + systemLocal.getCountry() + ".UTF-8");

        final View btShowLCALL = view.findViewById(R.id.BTShowLCALL);
        btShowLCALL.setOnClickListener(v -> {
            PopupMenu popupMenu = new PopupMenu(context, v);
            String[] lcs = getResources().getStringArray(R.array.some_lc_all);
            for (int i = 0; i < lcs.length; i++)
                popupMenu.getMenu().add(Menu.NONE, i, Menu.NONE, lcs[i]);
            popupMenu.setOnMenuItemClickListener(item -> {
                etLC_ALL.setText(item.toString() + ".UTF-8");
                return true;
            });
            popupMenu.show();
        });

        final CheckBox cbWoW64Mode = view.findViewById(R.id.CBWoW64Mode);
        cbWoW64Mode.setChecked(!isEditMode() || container.isWoW64Mode());

        final Spinner sStartupSelection = view.findViewById(R.id.SStartupSelection);
        byte previousStartupSelection = isEditMode() ? container.getStartupSelection() : -1;
        sStartupSelection.setSelection(previousStartupSelection != -1 ? previousStartupSelection : Container.STARTUP_SELECTION_ESSENTIAL);

        final Spinner sBox86Preset = view.findViewById(R.id.SBox86Preset);
        Box86_64PresetManager.loadSpinner("box86", sBox86Preset, isEditMode() ? container.getBox86Preset() : preferences.getString("box86_preset", Box86_64Preset.COMPATIBILITY));

        final Spinner sBox64Preset = view.findViewById(R.id.SBox64Preset);
        Box86_64PresetManager.loadSpinner("box64", sBox64Preset, isEditMode() ? container.getBox64Preset() : preferences.getString("box64_preset", Box86_64Preset.COMPATIBILITY));

        final Spinner sRCFile = view.findViewById(R.id.SRCFile);
        final int[] rcfileIds = {0};
        RCManager rcManager = new RCManager(context);
        RCManager.loadRCFileSpinner(rcManager, container == null ? 0 : container.getRCFileId(), sRCFile, id -> rcfileIds[0] = id);

        final CPUListView cpuListView = view.findViewById(R.id.CPUListView);
        final CPUListView cpuListViewWoW64 = view.findViewById(R.id.CPUListViewWoW64);

        cpuListView.setCheckedCPUList(isEditMode() ? container.getCPUList(true) : Container.getFallbackCPUList());
        cpuListViewWoW64.setCheckedCPUList(isEditMode() ? container.getCPUListWoW64(true) : Container.getFallbackCPUListWoW64());


        createWineConfigurationTab(view);
        final EnvVarsView envVarsView = createEnvVarsTab(view);
        createWinComponentsTab(view, isEditMode() ? container.getWinComponents() : Container.DEFAULT_WINCOMPONENTS);
        createDrivesTab(view);

        AppUtils.setupTabLayout(view, R.id.TabLayout, R.id.LLTabWineConfiguration, R.id.LLTabWinComponents, R.id.LLTabEnvVars, R.id.LLTabDrives, R.id.LLTabAdvanced);

        view.findViewById(R.id.BTConfirm).setOnClickListener((v) -> {
            try {
                String name = etName.getText().toString();
                String screenSize = getScreenSize(view);
                String envVars = envVarsView.getEnvVars();
                String graphicsDriver = StringUtils.parseIdentifier(sGraphicsDriver.getSelectedItem());
                String graphicsDriverVersion = (this.graphicsDriverVersion != null) ? this.graphicsDriverVersion : DefaultVersion.TURNIP; // Use the selected version or default
                String dxwrapper = StringUtils.parseIdentifier(sDXWrapper.getSelectedItem());
                String dxwrapperConfig = vDXWrapperConfig.getTag().toString();
                String audioDriver = StringUtils.parseIdentifier(sAudioDriver.getSelectedItem());
                String lc_all = etLC_ALL.getText().toString();
                String wincomponents = getWinComponents(view);
                String drives = getDrives(view);
                boolean showFPS = cbShowFPS.isChecked();
                boolean fullscreenStretched = cbFullscreenStretched.isChecked();
                String cpuList = cpuListView.getCheckedCPUListAsString();
                String cpuListWoW64 = cpuListViewWoW64.getCheckedCPUListAsString();
                boolean wow64Mode = cbWoW64Mode.isChecked();
                byte startupSelection = (byte) sStartupSelection.getSelectedItemPosition();
                String box86Preset = Box86_64PresetManager.getSpinnerSelectedId(sBox86Preset);
                String box64Preset = Box86_64PresetManager.getSpinnerSelectedId(sBox64Preset);
                String desktopTheme = getDesktopTheme(view);
                int rcfileId = rcfileIds[0];



                int finalInputType = 0;
                finalInputType |= cbEnableXInput.isChecked() ? WinHandler.FLAG_INPUT_TYPE_XINPUT : 0;
                finalInputType |= cbEnableDInput.isChecked() ? WinHandler.FLAG_INPUT_TYPE_DINPUT : 0;
                finalInputType |= SDInputType.getSelectedItemPosition() == 0 ?  WinHandler.FLAG_DINPUT_MAPPER_STANDARD : WinHandler.FLAG_DINPUT_MAPPER_XINPUT;

                if (isEditMode()) {
                    container.setName(name);
                    container.setScreenSize(screenSize);
                    container.setEnvVars(envVars);
                    container.setCPUList(cpuList);
                    container.setCPUListWoW64(cpuListWoW64);
                    container.setGraphicsDriver(graphicsDriver);
                    container.setGraphicsDriverVersion(graphicsDriverVersion); // Set the updated version here
                    container.setDXWrapper(dxwrapper);
                    container.setDXWrapperConfig(dxwrapperConfig);
                    container.setAudioDriver(audioDriver);
                    container.setWinComponents(wincomponents);
                    container.setDrives(drives);
                    container.setShowFPS(showFPS);
                    container.setFullscreenStretched(fullscreenStretched);
                    container.setInputType(finalInputType);
                    container.setWoW64Mode(wow64Mode);
                    container.setStartupSelection(startupSelection);
                    container.setBox86Preset(box86Preset);
                    container.setBox64Preset(box64Preset);
                    container.setDesktopTheme(desktopTheme);
                    container.setRcfileId(rcfileId);
                    container.setLC_ALL(lc_all);
                    container.saveData();
                    saveWineRegistryKeys(view);
                    getActivity().onBackPressed();
                } else {
                    JSONObject data = new JSONObject();
                    data.put("name", name);
                    data.put("screenSize", screenSize);
                    data.put("envVars", envVars);
                    data.put("cpuList", cpuList);
                    data.put("cpuListWoW64", cpuListWoW64);
                    data.put("graphicsDriver", graphicsDriver);
                    data.put("dxwrapper", dxwrapper);
                    data.put("dxwrapperConfig", dxwrapperConfig);
                    data.put("audioDriver", audioDriver);
                    data.put("wincomponents", wincomponents);
                    data.put("drives", drives);
                    data.put("showFPS", showFPS);
                    data.put("fullscreenStretched", fullscreenStretched);
                    data.put("inputType", finalInputType);
                    data.put("wow64Mode", wow64Mode);
                    data.put("startupSelection", startupSelection);
                    data.put("box86Preset", box86Preset);
                    data.put("box64Preset", box64Preset);
                    data.put("desktopTheme", desktopTheme);
                    data.put("rcfileId", rcfileId);
                    data.put("lc_all", lc_all);
                    String selectedWineVersion = sWineVersion.getSelectedItem().toString();
                    data.put("wineVersion", selectedWineVersion);

                    if (wineInfos.size() > 1) {
                        data.put("wineVersion", wineInfos.get(sWineVersion.getSelectedItemPosition()).identifier());
                    }

                    // Store the selected graphics driver version temporarily
                    tempGraphicsDriverVersion = (graphicsDriverVersion != null) ? graphicsDriverVersion : DefaultVersion.TURNIP;

                    preloaderDialog.show(R.string.creating_container);
                    manager.createContainerAsync(data, (newContainer) -> {
                        if (newContainer != null) {
                            this.container = newContainer;
                            newContainer.setGraphicsDriverVersion(tempGraphicsDriverVersion);
                            saveWineRegistryKeys(view);

                            // Save the selected Wine version to SharedPreferences with the container's name
                            SharedPreferences.Editor editor = preferences.edit();
                            editor.putString("wine_version_" + newContainer.getName(), selectedWineVersion);
                            editor.apply();
                        }
                        preloaderDialog.close();
                        getActivity().onBackPressed();
                    });
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        });
        return view;
    }



    private void saveWineRegistryKeys(View view) {
        File userRegFile = new File(container.getRootDir(), ".wine/user.reg");
        try (WineRegistryEditor registryEditor = new WineRegistryEditor(userRegFile)) {
            Spinner sCSMT = view.findViewById(R.id.SCSMT);
            registryEditor.setDwordValue("Software\\Wine\\Direct3D", "csmt", sCSMT.getSelectedItemPosition() != 0 ? 3 : 0);

            Spinner sGPUName = view.findViewById(R.id.SGPUName);
            try {
                JSONObject gpuName = gpuCards.getJSONObject(sGPUName.getSelectedItemPosition());
                registryEditor.setDwordValue("Software\\Wine\\Direct3D", "VideoPciDeviceID", gpuName.getInt("deviceID"));
                registryEditor.setDwordValue("Software\\Wine\\Direct3D", "VideoPciVendorID", gpuName.getInt("vendorID"));
            }
            catch (JSONException e) {}

            Spinner sOffscreenRenderingMode = view.findViewById(R.id.SOffscreenRenderingMode);
            registryEditor.setStringValue("Software\\Wine\\Direct3D", "OffScreenRenderingMode", sOffscreenRenderingMode.getSelectedItem().toString().toLowerCase(Locale.ENGLISH));

            Spinner sStrictShaderMath = view.findViewById(R.id.SStrictShaderMath);
            registryEditor.setDwordValue("Software\\Wine\\Direct3D", "strict_shader_math", sStrictShaderMath.getSelectedItemPosition());

            Spinner sVideoMemorySize = view.findViewById(R.id.SVideoMemorySize);
            registryEditor.setStringValue("Software\\Wine\\Direct3D", "VideoMemorySize", StringUtils.parseNumber(sVideoMemorySize.getSelectedItem()));

            Spinner sMouseWarpOverride = view.findViewById(R.id.SMouseWarpOverride);
            registryEditor.setStringValue("Software\\Wine\\DirectInput", "MouseWarpOverride", sMouseWarpOverride.getSelectedItem().toString().toLowerCase(Locale.ENGLISH));

            registryEditor.setStringValue("Software\\Wine\\Direct3D", "shader_backend", "glsl");
            registryEditor.setStringValue("Software\\Wine\\Direct3D", "UseGLSL", "enabled");
        }
    }

    private void createWineConfigurationTab(View view) {
        Context context = getContext();

        WineThemeManager.ThemeInfo desktopTheme = new WineThemeManager.ThemeInfo(isEditMode() ? container.getDesktopTheme() : WineThemeManager.DEFAULT_DESKTOP_THEME);
        Spinner sDesktopTheme = view.findViewById(R.id.SDesktopTheme);
        sDesktopTheme.setSelection(desktopTheme.theme.ordinal());
        final ImagePickerView ipvDesktopBackgroundImage = view.findViewById(R.id.IPVDesktopBackgroundImage);
        final ColorPickerView cpvDesktopBackgroundColor = view.findViewById(R.id.CPVDesktopBackgroundColor);
        cpvDesktopBackgroundColor.setColor(desktopTheme.backgroundColor);

        Spinner sDesktopBackgroundType = view.findViewById(R.id.SDesktopBackgroundType);
        sDesktopBackgroundType.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                WineThemeManager.BackgroundType type = WineThemeManager.BackgroundType.values()[position];
                ipvDesktopBackgroundImage.setVisibility(View.GONE);
                cpvDesktopBackgroundColor.setVisibility(View.GONE);

                if (type == WineThemeManager.BackgroundType.IMAGE) {
                    ipvDesktopBackgroundImage.setVisibility(View.VISIBLE);
                }
                else if (type == WineThemeManager.BackgroundType.COLOR) {
                    cpvDesktopBackgroundColor.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
        sDesktopBackgroundType.setSelection(desktopTheme.backgroundType.ordinal());

        File containerDir = isEditMode() ? container.getRootDir() : null;
        File userRegFile = new File(containerDir, ".wine/user.reg");

        try (WineRegistryEditor registryEditor = new WineRegistryEditor(userRegFile)) {
            List<String> stateList = Arrays.asList(context.getString(R.string.disable), context.getString(R.string.enable));
            Spinner sCSMT = view.findViewById(R.id.SCSMT);
            sCSMT.setAdapter(new ArrayAdapter<>(context, android.R.layout.simple_spinner_dropdown_item, stateList));
            sCSMT.setSelection(registryEditor.getDwordValue("Software\\Wine\\Direct3D", "csmt", 3) != 0 ? 1 : 0);

            Spinner sGPUName = view.findViewById(R.id.SGPUName);
            loadGPUNameSpinner(sGPUName, registryEditor.getDwordValue("Software\\Wine\\Direct3D", "VideoPciDeviceID", 1728));

            List<String> offscreenRenderingModeList = Arrays.asList("Backbuffer", "FBO");
            Spinner sOffscreenRenderingMode = view.findViewById(R.id.SOffscreenRenderingMode);
            sOffscreenRenderingMode.setAdapter(new ArrayAdapter<>(context, android.R.layout.simple_spinner_dropdown_item, offscreenRenderingModeList));
            AppUtils.setSpinnerSelectionFromValue(sOffscreenRenderingMode, registryEditor.getStringValue("Software\\Wine\\Direct3D", "OffScreenRenderingMode", "fbo"));

            Spinner sStrictShaderMath = view.findViewById(R.id.SStrictShaderMath);
            sStrictShaderMath.setAdapter(new ArrayAdapter<>(context, android.R.layout.simple_spinner_dropdown_item, stateList));
            sStrictShaderMath.setSelection(Math.min(registryEditor.getDwordValue("Software\\Wine\\Direct3D", "strict_shader_math", 1), 1));

            Spinner sVideoMemorySize = view.findViewById(R.id.SVideoMemorySize);
            String videoMemorySize = registryEditor.getStringValue("Software\\Wine\\Direct3D", "VideoMemorySize", "2048");
            AppUtils.setSpinnerSelectionFromNumber(sVideoMemorySize, videoMemorySize);

            List<String> mouseWarpOverrideList = Arrays.asList(context.getString(R.string.disable), context.getString(R.string.enable), context.getString(R.string.force));
            Spinner sMouseWarpOverride = view.findViewById(R.id.SMouseWarpOverride);
            sMouseWarpOverride.setAdapter(new ArrayAdapter<>(context, android.R.layout.simple_spinner_dropdown_item, mouseWarpOverrideList));
            AppUtils.setSpinnerSelectionFromValue(sMouseWarpOverride, registryEditor.getStringValue("Software\\Wine\\DirectInput", "MouseWarpOverride", "disable"));
        }
    }

    private void loadGPUNameSpinner(Spinner spinner, int selectedDeviceID) {
        List<String> values = new ArrayList<>();
        int selectedPosition = 0;

        try {
            for (int i = 0; i < gpuCards.length(); i++) {
                JSONObject item = gpuCards.getJSONObject(i);
                if (item.getInt("deviceID") == selectedDeviceID) selectedPosition = i;
                values.add(item.getString("name"));
            }
        }
        catch (JSONException e) {}

        spinner.setAdapter(new ArrayAdapter<>(getContext(), android.R.layout.simple_spinner_dropdown_item, values));
        spinner.setSelection(selectedPosition);
    }

    public static String getScreenSize(View view) {
        Spinner sScreenSize = view.findViewById(R.id.SScreenSize);
        String value = sScreenSize.getSelectedItem().toString();
        if (value.equalsIgnoreCase("custom")) {
            value = Container.DEFAULT_SCREEN_SIZE;
            String strWidth = ((EditText)view.findViewById(R.id.ETScreenWidth)).getText().toString().trim();
            String strHeight = ((EditText)view.findViewById(R.id.ETScreenHeight)).getText().toString().trim();
            if (strWidth.matches("[0-9]+") && strHeight.matches("[0-9]+")) {
                int width = Integer.parseInt(strWidth);
                int height = Integer.parseInt(strHeight);
                if ((width % 2) == 0 && (height % 2) == 0) return width+"x"+height;
            }
        }
        return StringUtils.parseIdentifier(value);
    }

    private String getDesktopTheme(View view) {
        Spinner sDesktopBackgroundType = view.findViewById(R.id.SDesktopBackgroundType);
        WineThemeManager.BackgroundType type = WineThemeManager.BackgroundType.values()[sDesktopBackgroundType.getSelectedItemPosition()];
        Spinner sDesktopTheme = view.findViewById(R.id.SDesktopTheme);
        ColorPickerView cpvDesktopBackground = view.findViewById(R.id.CPVDesktopBackgroundColor);
        WineThemeManager.Theme theme = WineThemeManager.Theme.values()[sDesktopTheme.getSelectedItemPosition()];

        String desktopTheme = theme+","+type+","+cpvDesktopBackground.getColorAsString();
        if (type == WineThemeManager.BackgroundType.IMAGE) {
            File userWallpaperFile = WineThemeManager.getUserWallpaperFile(getContext());
            desktopTheme += ","+(userWallpaperFile.isFile() ? userWallpaperFile.lastModified() : "0");
        }
        return desktopTheme;
    }

    public static void loadScreenSizeSpinner(View view, String selectedValue) {
        final Spinner sScreenSize = view.findViewById(R.id.SScreenSize);

        final LinearLayout llCustomScreenSize = view.findViewById(R.id.LLCustomScreenSize);
        sScreenSize.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String value = sScreenSize.getItemAtPosition(position).toString();
                llCustomScreenSize.setVisibility(value.equalsIgnoreCase("custom") ? View.VISIBLE : View.GONE);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        boolean found = AppUtils.setSpinnerSelectionFromIdentifier(sScreenSize, selectedValue);
        if (!found) {
            AppUtils.setSpinnerSelectionFromValue(sScreenSize, "custom");
            String[] screenSize = selectedValue.split("x");
            ((EditText)view.findViewById(R.id.ETScreenWidth)).setText(screenSize[0]);
            ((EditText)view.findViewById(R.id.ETScreenHeight)).setText(screenSize[1]);
        }
    }

    // This method shows the GraphicsDriverConfigDialog
    private void showGraphicsDriverConfigDialog(View anchor) {
        new GraphicsDriverConfigDialog(anchor, manager, container, graphicsDriverVersion, version -> {
            // Capture the selected version
            graphicsDriverVersion = version;
        }).show();
    }

    // Original method: Used for compatibility with ShortcutSettingsDialog
    public static void loadGraphicsDriverSpinner(final Spinner sGraphicsDriver, final Spinner sDXWrapper, String selectedGraphicsDriver, String selectedDXWrapper) {
        final Context context = sGraphicsDriver.getContext();
        final String[] dxwrapperEntries = context.getResources().getStringArray(R.array.dxwrapper_entries);

        Runnable update = () -> {
            String graphicsDriver = StringUtils.parseIdentifier(sGraphicsDriver.getSelectedItem());
            boolean addAll = graphicsDriver.equals("turnip");

            ArrayList<String> items = new ArrayList<>();
            for (String value : dxwrapperEntries) {
                if (addAll || (!value.equals("DXVK") && !value.equals("VKD3D"))) {
                    items.add(value);
                }
            }
            sDXWrapper.setAdapter(new ArrayAdapter<>(context, android.R.layout.simple_spinner_dropdown_item, items.toArray(new String[0])));
            AppUtils.setSpinnerSelectionFromIdentifier(sDXWrapper, selectedDXWrapper);
        };

        sGraphicsDriver.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                update.run();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        AppUtils.setSpinnerSelectionFromIdentifier(sGraphicsDriver, selectedGraphicsDriver);
        update.run();
    }

    // New method: Adds support for the GraphicsDriverConfigDialog
    public void loadGraphicsDriverSpinner(final Spinner sGraphicsDriver, final Spinner sDXWrapper, final View vGraphicsDriverConfig, String selectedGraphicsDriver, String selectedDXWrapper) {
        final Context context = sGraphicsDriver.getContext();

        // Update the spinner with the available graphics driver options
        updateGraphicsDriverSpinner(context, contentsManager, sGraphicsDriver);

        Runnable update = () -> {
            String graphicsDriver = StringUtils.parseIdentifier(sGraphicsDriver.getSelectedItem());
            boolean isTurnip = graphicsDriver.equals("turnip");

            // Update the DXWrapper spinner
            ArrayList<String> items = new ArrayList<>();
            for (String value : context.getResources().getStringArray(R.array.dxwrapper_entries)) {
                if (isTurnip || (!value.equals("DXVK") && !value.equals("VKD3D"))) {
                    items.add(value);
                }
            }
            sDXWrapper.setAdapter(new ArrayAdapter<>(context, android.R.layout.simple_spinner_dropdown_item, items.toArray(new String[0])));
            AppUtils.setSpinnerSelectionFromIdentifier(sDXWrapper, selectedDXWrapper);

            // Update the gear icon for the graphics driver
            if (isTurnip) {
                vGraphicsDriverConfig.setOnClickListener((v) -> {
                    if (container != null) {
                        showGraphicsDriverConfigDialog(vGraphicsDriverConfig);
                    } else {
                        Toast.makeText(context, "Please save this container before attempting to change the driver version", Toast.LENGTH_LONG).show();
                    }
                });
                vGraphicsDriverConfig.setVisibility(View.VISIBLE);
            } else {
                vGraphicsDriverConfig.setVisibility(View.GONE);  // VirGL doesn't allow version selection
            }
        };

        sGraphicsDriver.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                update.run();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        AppUtils.setSpinnerSelectionFromIdentifier(sGraphicsDriver, selectedGraphicsDriver);
        update.run();
    }




    public static void setupDXWrapperSpinner(final Spinner sDXWrapper, final View vDXWrapperConfig) {
        sDXWrapper.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String dxwrapper = StringUtils.parseIdentifier(sDXWrapper.getSelectedItem());
                if (dxwrapper.equals("dxvk")) {
                    vDXWrapperConfig.setOnClickListener((v) -> (new DXVKConfigDialog(vDXWrapperConfig)).show());
                    vDXWrapperConfig.setVisibility(View.VISIBLE);
                }
                else if (dxwrapper.equals("vkd3d")) {
                    vDXWrapperConfig.setOnClickListener((v) -> (new VKD3DConfigDialog(vDXWrapperConfig)).show());
                    vDXWrapperConfig.setVisibility(View.VISIBLE);
                } else vDXWrapperConfig.setVisibility(View.GONE);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    public static String getWinComponents(View view) {
        ViewGroup parent = view.findViewById(R.id.LLTabWinComponents);
        ArrayList<View> views = new ArrayList<>();
        AppUtils.findViewsWithClass(parent, Spinner.class, views);
        String[] wincomponents = new String[views.size()];

        for (int i = 0; i < views.size(); i++) {
            Spinner spinner = (Spinner)views.get(i);
            wincomponents[i] = spinner.getTag()+"="+spinner.getSelectedItemPosition();
        }
        return String.join(",", wincomponents);
    }

    public static void createWinComponentsTab(View view, String wincomponents) {
        Context context = view.getContext();
        LayoutInflater inflater = LayoutInflater.from(context);
        ViewGroup tabView = view.findViewById(R.id.LLTabWinComponents);
        ViewGroup directxSectionView = tabView.findViewById(R.id.LLWinComponentsDirectX);
        ViewGroup generalSectionView = tabView.findViewById(R.id.LLWinComponentsGeneral);

        for (String[] wincomponent : new KeyValueSet(wincomponents)) {
            ViewGroup parent = wincomponent[0].startsWith("direct") ? directxSectionView : generalSectionView;
            View itemView = inflater.inflate(R.layout.wincomponent_list_item, parent, false);
            ((TextView)itemView.findViewById(R.id.TextView)).setText(StringUtils.getString(context, wincomponent[0]));
            Spinner spinner = itemView.findViewById(R.id.Spinner);
            spinner.setSelection(Integer.parseInt(wincomponent[1]), false);
            spinner.setTag(wincomponent[0]);
            parent.addView(itemView);
        }
    }

    private EnvVarsView createEnvVarsTab(final View view) {
        final Context context = view.getContext();
        final EnvVarsView envVarsView = view.findViewById(R.id.EnvVarsView);
        envVarsView.setEnvVars(new EnvVars(isEditMode() ? container.getEnvVars() : Container.DEFAULT_ENV_VARS));
        view.findViewById(R.id.BTAddEnvVar).setOnClickListener((v) -> (new AddEnvVarDialog(context, envVarsView)).show());
        return envVarsView;
    }

    private String getDrives(View view) {
        LinearLayout parent = view.findViewById(R.id.LLDrives);
        String drives = "";

        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            Spinner spinner = child.findViewById(R.id.Spinner);
            EditText editText = child.findViewById(R.id.EditText);
            String path = editText.getText().toString().trim();
            if (!path.isEmpty()) drives += spinner.getSelectedItem()+path;
        }
        return drives;
    }

    private void createDrivesTab(View view) {
        final Context context = getContext();

        final LinearLayout parent = view.findViewById(R.id.LLDrives);
        final View emptyTextView = view.findViewById(R.id.TVDrivesEmptyText);
        LayoutInflater inflater = LayoutInflater.from(context);
        final String drives = isEditMode() ? container.getDrives() : Container.DEFAULT_DRIVES;
        final String[] driveLetters = new String[Container.MAX_DRIVE_LETTERS];
        for (int i = 0; i < driveLetters.length; i++) driveLetters[i] = ((char)(i + 68))+":";

        Callback<String[]> addItem = (drive) -> {
            final View itemView = inflater.inflate(R.layout.drive_list_item, parent, false);
            Spinner spinner = itemView.findViewById(R.id.Spinner);
            spinner.setAdapter(new ArrayAdapter<>(context, android.R.layout.simple_spinner_dropdown_item, driveLetters));
            AppUtils.setSpinnerSelectionFromValue(spinner, drive[0]+":");

            final EditText editText = itemView.findViewById(R.id.EditText);
            editText.setText(drive[1]);

            itemView.findViewById(R.id.BTSearch).setOnClickListener((v) -> {
                openDirectoryCallback = (path) -> {
                    drive[1] = path;
                    editText.setText(path);
                };
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, Uri.fromFile(Environment.getExternalStorageDirectory()));
                getActivity().startActivityFromFragment(this, intent, MainActivity.OPEN_DIRECTORY_REQUEST_CODE);
            });

            itemView.findViewById(R.id.BTRemove).setOnClickListener((v) -> {
                parent.removeView(itemView);
                if (parent.getChildCount() == 0) emptyTextView.setVisibility(View.VISIBLE);
            });
            parent.addView(itemView);
        };
        for (String[] drive : Container.drivesIterator(drives)) addItem.call(drive);

        view.findViewById(R.id.BTAddDrive).setOnClickListener((v) -> {
            if (parent.getChildCount() >= Container.MAX_DRIVE_LETTERS) return;
            final String nextDriveLetter = String.valueOf(driveLetters[parent.getChildCount()].charAt(0));
            addItem.call(new String[]{nextDriveLetter, ""});
        });

        if (drives.isEmpty()) emptyTextView.setVisibility(View.VISIBLE);
    }

    private void loadContentProfileWineSpinner(View view, Spinner sWineVersion) {
        final Context context = getContext();
        ArrayList<String> wineVersions = new ArrayList<>();
        for (ContentProfile profile : contentsManager.getProfiles(ContentProfile.ContentType.CONTENT_TYPE_WINE)) {
            wineVersions.add(ContentsManager.getEntryName(profile));
        }
        sWineVersion.setAdapter(new ArrayAdapter<>(context, android.R.layout.simple_spinner_dropdown_item, wineVersions));
        if (isEditMode()) {
            AppUtils.setSpinnerSelectionFromValue(sWineVersion, container.getWineVersion());
        }
    }

    private void loadStandardWineSpinner(View view, Spinner sWineVersion) {
        final Context context = getContext();
        ArrayList<WineInfo> wineInfos = WineUtils.getInstalledWineInfos(context);
        ArrayList<String> wineVersions = new ArrayList<>();
        for (WineInfo info : wineInfos) {
            wineVersions.add(info.toString());
        }
        sWineVersion.setAdapter(new ArrayAdapter<>(context, android.R.layout.simple_spinner_dropdown_item, wineVersions));
        if (isEditMode()) {
            AppUtils.setSpinnerSelectionFromValue(sWineVersion, container.getWineVersion());
        }
    }



    public static void updateGraphicsDriverSpinner(Context context, ContentsManager manager, Spinner spinner) {
        String[] originalItems = context.getResources().getStringArray(R.array.graphics_driver_entries);
        List<String> itemList = new ArrayList<>(Arrays.asList(originalItems));

        // Add Turnip graphics driver versions to the list
//        for (ContentProfile profile : manager.getProfiles(ContentProfile.ContentType.CONTENT_TYPE_TURNIP)) {
//            itemList.add(ContentsManager.getEntryName(profile));
//        }
        // Add VirGL graphics driver versions to the list
        for (ContentProfile profile : manager.getProfiles(ContentProfile.ContentType.CONTENT_TYPE_VIRGL)) {
            itemList.add(ContentsManager.getEntryName(profile));
        }

        // Set the adapter with the combined list
        spinner.setAdapter(new ArrayAdapter<>(context, android.R.layout.simple_spinner_dropdown_item, itemList));
    }


}
