package com.winlator.saves;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.winlator.R;

import java.io.File;

public class CustomFilePickerActivity extends AppCompatActivity {

    private File currentDirectory;
    private RecyclerView recyclerView;
    private FileAdapter fileAdapter;
    private Button confirmButton;
    private Button upButton;  // New Up button

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_file_picker);

        recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        confirmButton = findViewById(R.id.confirmButton);
        upButton = findViewById(R.id.upButton);

        // Get the initial directory from the intent
        String initialDirectoryPath = getIntent().getStringExtra("initialDirectory");
        currentDirectory = new File(initialDirectoryPath);

        // Check if in editing mode
        boolean isEditing = getIntent().getBooleanExtra("isEditing", false);
        if (isEditing) {
            // Load the directory to the path of the file being edited
            String editingPath = getIntent().getStringExtra("editingPath");
            if (editingPath != null) {
                currentDirectory = new File(editingPath);
            }
        }

        loadFiles(currentDirectory);

        confirmButton.setOnClickListener(view -> {
            Intent resultIntent = new Intent();
            resultIntent.putExtra("selectedDirectory", currentDirectory.getAbsolutePath());
            setResult(Activity.RESULT_OK, resultIntent);
            finish();
        });

        upButton.setOnClickListener(view -> {
            File parentDirectory = currentDirectory.getParentFile();
            if (parentDirectory != null) {
                currentDirectory = parentDirectory;
                loadFiles(currentDirectory);
                confirmButton.setEnabled(false);
            }
        });
    }

    private void loadFiles(File directory) {
        File[] files = directory.listFiles();
        if (files != null) {
            fileAdapter = new FileAdapter(files, this::onFileClicked);
            recyclerView.setAdapter(fileAdapter);
        }
        upButton.setEnabled(directory.getParentFile() != null);
    }

    private void onFileClicked(File file) {
        if (file.isDirectory()) {
            currentDirectory = file;
            loadFiles(currentDirectory);
            confirmButton.setEnabled(true);
        }
    }
}
