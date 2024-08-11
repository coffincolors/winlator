package com.winlator.contentdialog;

import android.content.Context;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Spinner;

import com.winlator.R;
import com.winlator.core.AppUtils;
import com.winlator.core.DefaultVersion;
import com.winlator.core.EnvVars;
import com.winlator.core.KeyValueSet;
import com.winlator.core.StringUtils;
public class VKD3DConfigDialog extends ContentDialog {
    public static final String DEFAULT_CONFIG = DXVKConfigDialog.DEFAULT_CONFIG +
            ",vkd3dVersion=" + DefaultVersion.VKD3D + ",vkd3dLevel=12_1";
    public static final String[] VKD3D_FEATURE_LEVEL = {"12_0", "12_1", "12_2", "11_1", "11_0", "10_1", "10_0", "9_3", "9_2", "9_1"};

    public VKD3DConfigDialog(View anchor) {
        super(anchor.getContext(), R.layout.vkd3d_config_dialog);
        setIcon(R.drawable.icon_settings);
        setTitle("VKD3D "+anchor.getContext().getString(R.string.configuration));

        final Spinner sVersion = findViewById(R.id.SVersion);
        final Spinner sFeatureLevel = findViewById(R.id.SFeatureLevel);

        ArrayAdapter<String> adapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_spinner_item, VKD3D_FEATURE_LEVEL);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sFeatureLevel.setAdapter(adapter);

        KeyValueSet config = parseConfig(anchor.getTag());
        AppUtils.setSpinnerSelectionFromIdentifier(sVersion, config.get("vkd3dVersion"));
        AppUtils.setSpinnerSelectionFromIdentifier(sFeatureLevel, config.get("vkd3dLevel"));

        setOnConfirmCallback(() -> {
            config.put("vkd3dVersion", StringUtils.parseNumber(sVersion.getSelectedItem()));
            config.put("vkd3dLevel", sFeatureLevel.getSelectedItem().toString());
            anchor.setTag(config.toString());
        });
    }

    public static KeyValueSet parseConfig(Object config) {
        String data = config != null && !config.toString().isEmpty() ? config.toString() : DEFAULT_CONFIG;
        return new KeyValueSet(data);
    }

    public static void setEnvVars(Context context, KeyValueSet config, EnvVars envVars) {
        envVars.put("VKD3D_FEATURE_LEVEL", config.get("vkd3dLevel"));
    }
}
