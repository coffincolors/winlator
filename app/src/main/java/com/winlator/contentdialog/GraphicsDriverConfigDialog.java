package com.winlator.contentdialog;

import android.content.Context;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;

import androidx.annotation.Nullable;

import com.winlator.R;
import com.winlator.container.Container;
import com.winlator.container.ContainerManager;
import com.winlator.contents.ContentProfile;
import com.winlator.contents.ContentsManager;
import com.winlator.core.AppUtils;
import com.winlator.core.DefaultVersion;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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

        // Ensure ContentsManager syncContents is called
        ContentsManager contentsManager = new ContentsManager(anchor.getContext());
        contentsManager.syncContents();

        // Populate the spinner with available versions from ContentsManager
        populateGraphicsDriverVersions(anchor.getContext(), contentsManager, initialVersion);

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

    private void populateGraphicsDriverVersions(Context context, ContentsManager contentsManager, @Nullable String initialVersion) {
        List<String> versions = new ArrayList<>();

        // Load the default versions from arrays.xml
        String[] defaultVersions = context.getResources().getStringArray(R.array.graphics_driver_version_entries);
        versions.addAll(Arrays.asList(defaultVersions));

        // Add installed versions from ContentsManager
        List<ContentProfile> profiles = contentsManager.getProfiles(ContentProfile.ContentType.CONTENT_TYPE_TURNIP);
        if (profiles != null) {
            for (ContentProfile profile : profiles) {
                versions.add(ContentsManager.getEntryName(profile));
            }
        }

        // Set the adapter and select the initial version
        ArrayAdapter<String> adapter = new ArrayAdapter<>(context, android.R.layout.simple_spinner_dropdown_item, versions);
        sVersion.setAdapter(adapter);
        AppUtils.setSpinnerSelectionFromIdentifier(sVersion, initialVersion != null ? initialVersion : defaultVersions[0]);
    }


    public String getSelectedVersion() {
        return selectedVersion;
    }
}
