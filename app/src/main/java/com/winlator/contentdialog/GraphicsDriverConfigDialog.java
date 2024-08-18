package com.winlator.contentdialog;

import android.content.Context;
import android.util.Log;
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
    private static final String TAG = "GraphicsDriverConfigDialog"; // Tag for logging

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
        Log.d(TAG, "Initializing with version: " + selectedVersion); // Log the initial version

        AppUtils.setSpinnerSelectionFromIdentifier(sVersion, selectedVersion);

        // Update the selectedVersion whenever the user selects a different version
        sVersion.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedVersion = sVersion.getSelectedItem().toString();
                Log.d(TAG, "User selected version: " + selectedVersion); // Log the selected version
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
                Log.d(TAG, "Applying selected version to container: " + selectedVersion);
                container.setGraphicsDriverVersion(selectedVersion);

                // Attempt to extract the graphics driver files
                boolean extractionSuccess = containerManager.extractGraphicsDriverFiles(selectedVersion, container.getRootDir(), null);
                Log.d(TAG, "Graphics driver extraction " + (extractionSuccess ? "succeeded" : "failed") + " for version: " + selectedVersion);

                if (!extractionSuccess) {
                    // Handle extraction failure (optional: you might want to notify the user here)
                    Log.e(TAG, "Failed to extract graphics driver files for version: " + selectedVersion);
                }
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
