package com.winlator.contentdialog;

import android.content.Context;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Spinner;

import androidx.annotation.Nullable;

import com.winlator.R;
import com.winlator.container.Container;
import com.winlator.container.ContainerManager;
import com.winlator.core.AppUtils;
import com.winlator.core.DefaultVersion;

public class GraphicsDriverConfigDialog extends ContentDialog {
    private final Spinner sVersion;
    private final ContainerManager containerManager;
    private String selectedVersion;

    public interface OnGraphicsDriverVersionSelectedListener {
        void onGraphicsDriverVersionSelected(String version);
    }

    public GraphicsDriverConfigDialog(View anchor, ContainerManager containerManager, @Nullable Container container, @Nullable String initialVersion, OnGraphicsDriverVersionSelectedListener listener) {
        super(anchor.getContext(), R.layout.graphics_driver_config_dialog);
        this.containerManager = containerManager;

        setIcon(R.drawable.icon_settings);
        setTitle(anchor.getContext().getString(R.string.graphics_driver_configuration));

        sVersion = findViewById(R.id.SGraphicsDriverVersion);

        // Initialize the spinner with the provided initial version or a safe default
        selectedVersion = (initialVersion != null) ? initialVersion : DefaultVersion.TURNIP;

        AppUtils.setSpinnerSelectionFromIdentifier(sVersion, selectedVersion);

        // Update the selectedVersion whenever the user selects a different version
        sVersion.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedVersion = sVersion.getSelectedItem().toString();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // Do nothing
            }
        });

        setOnConfirmCallback(() -> {
            anchor.setTag(selectedVersion);

            if (container != null) {
                // Apply the selected version to the existing container
                container.setGraphicsDriverVersion(selectedVersion);
                containerManager.extractGraphicsDriverFiles(selectedVersion, container.getRootDir(), null);
            }

            // Pass the selected version back to ContainerDetailFragment
            if (listener != null) {
                listener.onGraphicsDriverVersionSelected(selectedVersion);
            }
        });
    }

    public String getSelectedVersion() {
        return selectedVersion;
    }
}


