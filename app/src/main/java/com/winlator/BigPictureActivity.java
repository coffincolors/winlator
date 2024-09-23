package com.winlator;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.LinearSnapHelper;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SnapHelper;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.winlator.bigpicture.BigPictureAdapter;
import com.winlator.bigpicture.CarouselItemDecoration;
import com.winlator.bigpicture.steamgrid.SteamGridDBApi;
import com.winlator.bigpicture.steamgrid.SteamGridGridsResponse;
import com.winlator.bigpicture.steamgrid.SteamGridGridsResponseDeserializer;
import com.winlator.bigpicture.steamgrid.SteamGridSearchResponse;
import com.winlator.container.Container;
import com.winlator.container.ContainerManager;
import com.winlator.container.Shortcut;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.concurrent.Executors;

import okhttp3.OkHttpClient;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class BigPictureActivity extends AppCompatActivity {
    private ImageView coverArtView;
    private TextView gameTitleView, graphicsDriverView, graphicsDriverVersionView, dxWrapperView, dxWrapperConfigView, audioDriverView, box86PresetView, box64PresetView, playCountView, playtimeView;
    private RecyclerView recyclerView;
    private ContainerManager manager;
    private BigPictureAdapter adapter;
    private ImageButton playButton;

    private Shortcut currentShortcut;

    private static String API_KEY = "0324c52513634547a7b32d6d323635d0";
    private static final String BASE_URL = "https://www.steamgriddb.com/api/v2/";

    private static final int REQUEST_CODE_UPLOAD_CUSTOM_COVER = 1069;
    private TextView uploadText; // Class-level variable

    private TextView emptyStateTextView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getSupportActionBar().hide();  // Hide the action bar for full-screen mode
        setContentView(R.layout.big_picture_activity);

        // Override API key if custom key is set
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        boolean isCustomApiKeyEnabled = preferences.getBoolean("enable_custom_api_key", false);
        if (isCustomApiKeyEnabled) {
            String customApiKey = preferences.getString("custom_api_key", "");
            if (customApiKey != null && !customApiKey.isEmpty()) {
                API_KEY = customApiKey;
            }
        }

        // Set immersive mode
        enableImmersiveMode();

        coverArtView = findViewById(R.id.IVCoverArt);
        gameTitleView = findViewById(R.id.TVGameTitle);
        graphicsDriverView = findViewById(R.id.TVGraphicsDriver);
        graphicsDriverVersionView = findViewById(R.id.TVGraphicsDriverVersion);
        dxWrapperView = findViewById(R.id.TVDXWrapper);
        dxWrapperConfigView = findViewById(R.id.TVDXWrapperConfig);
        audioDriverView = findViewById(R.id.TVAudioDriver);
        box86PresetView = findViewById(R.id.TVBox86Preset);
        box64PresetView = findViewById(R.id.TVBox64Preset);
        playCountView = findViewById(R.id.TVPlayCount);
        playtimeView = findViewById(R.id.TVPlaytime);
        recyclerView = findViewById(R.id.RecyclerView);
        playButton = findViewById(R.id.playButton);

        // Tint the play button icon to white
        Drawable playIcon = playButton.getDrawable();
        if (playIcon != null) {
            playIcon.mutate();  // Ensure it doesn't affect other instances
            playIcon.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN);  // Apply the white color filter
        }

        // Add item decoration for reduced spacing
        recyclerView.addItemDecoration(new CarouselItemDecoration(15));  // Reduced space between items

        // Initialize ContainerManager
        manager = new ContainerManager(this);

        // Load the list of shortcuts
        loadShortcutsList();

        // Setup snapping for RecyclerView to center the items
        SnapHelper snapHelper = new LinearSnapHelper();
        snapHelper.attachToRecyclerView(recyclerView);


        // Set the click listener for the play button
        playButton.setOnClickListener(v -> {
            if (currentShortcut != null) {
                runFromShortcut(currentShortcut);  // Use the loaded shortcut
            }
        });

        coverArtView.setOnClickListener(v -> {
            if (currentShortcut != null) {
                if (currentShortcut.getCustomCoverArtPath() != null) {
                    // Custom cover art exists, show the dialog
                    showCoverArtOptionsDialog();
                } else {
                    // No custom cover art, directly prompt for uploading a new one
                    promptForCustomCoverArtUpload();
                }
            }
        });



    }

    private void showCoverArtOptionsDialog() {
        // Create an AlertDialog to show the options
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Cover Art Options")
                .setItems(new CharSequence[]{"Remove Custom Cover Art", "Upload New Cover Art"}, (dialog, which) -> {
                    switch (which) {
                        case 0: // Remove Custom Cover Art
                            removeCustomCoverArt();
                            break;
                        case 1: // Upload New Cover Art
                            promptForCustomCoverArtUpload();
                            break;
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void removeCustomCoverArt() {
        if (currentShortcut != null) {
            Log.d("BigPictureActivity", "Removing cover art for shortcut: " + currentShortcut.name);
            Log.d("BigPictureActivity", "Current custom cover art path: " + currentShortcut.getCustomCoverArtPath());

            // Remove custom cover art from the shortcut
            currentShortcut.removeCustomCoverArt();

            // Clear cached cover art if exists
            File cachedFile = new File(getCacheDir(), "coverArtCache/" + currentShortcut.name + ".png");
            if (cachedFile.exists() && cachedFile.delete()) {
                Log.d("BigPictureActivity", "Cached cover art deleted successfully.");
            } else {
                Log.e("BigPictureActivity", "Failed to delete cached cover art or it doesn't exist.");
            }

            // Update UI
            coverArtView.setImageResource(R.drawable.icon_action_bar_import); // Default placeholder image
            coverArtView.setBackgroundColor(Color.parseColor("#99000000")); // Semi-transparent background

            Log.d("BigPictureActivity", "Custom cover art removed and data saved.");

            // Reload the shortcut data to reflect the changes
            loadShortcutData(currentShortcut);
            Log.d("BigPictureActivity", "Shortcut data reloaded after removal.");
        }
    }



    // Prompt user to select a custom cover art image from gallery
    private void promptForCustomCoverArtUpload() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
//        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, false);
        startActivityForResult(intent, REQUEST_CODE_UPLOAD_CUSTOM_COVER);
    }

    private void enableImmersiveMode() {
        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
    }

    private Shortcut getSelectedShortcut() {
        int position = getCenterItemPosition();
        if (position != RecyclerView.NO_POSITION) {
            return adapter.getItem(position);
        }
        return null;
    }

    private int getCenterItemPosition() {
        LinearLayoutManager layoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();
        int firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition();
        int lastVisibleItemPosition = layoutManager.findLastVisibleItemPosition();

        int centerPosition = RecyclerView.NO_POSITION;
        float closestToCenter = Float.MAX_VALUE;
        int recyclerViewCenter = recyclerView.getWidth() / 2;

        for (int i = firstVisibleItemPosition; i <= lastVisibleItemPosition; i++) {
            if (i >= 0) {
                View itemView = layoutManager.findViewByPosition(i);
                if (itemView != null) {
                    int itemCenter = (itemView.getLeft() + itemView.getRight()) / 2;
                    float distanceFromCenter = Math.abs(recyclerViewCenter - itemCenter);

                    if (distanceFromCenter < closestToCenter) {
                        closestToCenter = distanceFromCenter;
                        centerPosition = i;
                    }
                }
            }
        }

        return centerPosition;
    }

    private void loadShortcutsList() {
        List<Shortcut> shortcuts = manager.loadShortcuts();
        emptyStateTextView = findViewById(R.id.TVEmptyState);

        // Check if the shortcuts list is empty
        if (shortcuts.isEmpty()) {
            // Show the empty state message
            recyclerView.setVisibility(View.GONE);
            playButton.setVisibility(View.GONE);
            emptyStateTextView.setVisibility(View.VISIBLE);
        } else {
            // Hide the empty state message and show the RecyclerView
            recyclerView.setVisibility(View.VISIBLE);
            emptyStateTextView.setVisibility(View.GONE);

            // Initialize and set the adapter
            adapter = new BigPictureAdapter(shortcuts, recyclerView); // Pass the RecyclerView reference
            recyclerView.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
            recyclerView.setAdapter(adapter);

            // Preload first shortcut details
            loadShortcutData(shortcuts.get(0));
        }
    }


    public void loadShortcutData(Shortcut shortcut) {
        currentShortcut = shortcut;

        // Log the current cover art path
        Log.d("BigPictureActivity", "Loaded cover art path: " + shortcut.getCustomCoverArtPath());

        // Set the game title
        gameTitleView.setText(shortcut.name);

        // Set play count and playtime (unchanged)
        SharedPreferences playtimePrefs = getSharedPreferences("playtime_stats", Context.MODE_PRIVATE);
        long totalPlaytime = playtimePrefs.getLong(shortcut.name + "_playtime", 0);
        int playCount = playtimePrefs.getInt(shortcut.name + "_play_count", 0);
        playCountView.setText("Times Played: " + playCount);
        playtimeView.setText("Playtime: " + formatPlaytime(totalPlaytime));

        // Get the associated container for this shortcut (unchanged)
        Container container = manager.getContainerForShortcut(shortcut);
        setTextOrPlaceholder(graphicsDriverView, shortcut.getExtra("graphicsDriver"), container.getGraphicsDriver());
        setTextOrPlaceholder(graphicsDriverVersionView, shortcut.getExtra("graphicsDriverVersion"), container.getGraphicsDriverVersion());
        setTextOrPlaceholder(dxWrapperView, shortcut.getExtra("dxwrapper"), container.getDXWrapper());
        setTextOrPlaceholder(dxWrapperConfigView, shortcut.getExtra("dxwrapperConfig"), container.getDXWrapperConfig());
        setTextOrPlaceholder(audioDriverView, shortcut.getExtra("audioDriver"), container.getAudioDriver());
        setTextOrPlaceholder(box86PresetView, shortcut.getExtra("box86Preset"), container.getBox86Preset());
        setTextOrPlaceholder(box64PresetView, shortcut.getExtra("box64Preset"), container.getBox64Preset());

        // Handle cover art loading
        Bitmap coverArt = null;
        if (shortcut.getCustomCoverArtPath() != null && !shortcut.getCustomCoverArtPath().isEmpty()) {
            coverArt = BitmapFactory.decodeFile(shortcut.getCustomCoverArtPath());
        }

        if (coverArt == null) {
            // Check for cached cover art if custom cover art is not found or removed
            coverArt = loadCachedCoverArt(shortcut.name);
        }

        if (coverArt != null) {
            coverArtView.setImageBitmap(coverArt); // Set cover art from custom or cache
        } else {
            coverArtView.setImageResource(R.drawable.cover_art_placeholder); // Default icon or placeholder
            fetchCoverArt(shortcut); // Fetch from remote if not available locally
        }

        // Update the click listener to reflect the current state of custom cover art
        coverArtView.setOnClickListener(v -> {
            if (currentShortcut.getCustomCoverArtPath() != null) {
                // Custom cover art exists, show the dialog
                showCoverArtOptionsDialog();
            } else {
                // No custom cover art, directly prompt for uploading a new one
                promptForCustomCoverArtUpload();
            }
        });
    }


    private void runFromShortcut(Shortcut shortcut) {
        // Launch XServerDisplayActivity with the necessary extras
        Intent intent = new Intent(this, XServerDisplayActivity.class);
        intent.putExtra("container_id", shortcut.container.id);
        intent.putExtra("shortcut_path", shortcut.file.getPath());
        intent.putExtra("shortcut_name", shortcut.name); // Pass the shortcut name for display
        startActivity(intent);
    }


    private void setTextOrPlaceholder(TextView textView, String shortcutValue, String containerValue) {
        if (!shortcutValue.isEmpty()) {
            textView.setText(shortcutValue); // Use the value from the shortcut if available
        } else if (!containerValue.isEmpty()) {
            textView.setText(containerValue); // Fallback to the container's value
        } else {
            textView.setText("Not Set"); // Fallback if neither are available
        }
    }


    private void setTextFromContainer(TextView textView, String label, String shortcutValue, String containerValue) {
        if (!shortcutValue.isEmpty()) {
            textView.setText(label + shortcutValue); // Use the value from the shortcut if available
        } else if (!containerValue.isEmpty()) {
            textView.setText(label + containerValue); // Fallback to the container's value
        } else {
            textView.setText(label + "Not Set"); // Fallback if neither are available
        }
    }


    private void fetchCoverArt(Shortcut shortcut) {
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(new OkHttpClient())
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        SteamGridDBApi api = retrofit.create(SteamGridDBApi.class);
        Call<SteamGridSearchResponse> call = api.searchGame("Bearer " + API_KEY, shortcut.name);

        call.enqueue(new Callback<SteamGridSearchResponse>() {
            @Override
            public void onResponse(Call<SteamGridSearchResponse> call, Response<SteamGridSearchResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<SteamGridSearchResponse.GameData> gameData = response.body().data;
                    if (gameData != null && !gameData.isEmpty()) {
                        fetchGridsForGame(gameData.get(0).id, shortcut);
                    } else {
                        showCustomCoverArtUploadOption(shortcut);
                    }
                } else {
                    showCustomCoverArtUploadOption(shortcut);
                }
            }

            @Override
            public void onFailure(Call<SteamGridSearchResponse> call, Throwable t) {
                Log.e("SteamGridDB", "Failed to fetch game ID", t);
                showCustomCoverArtUploadOption(shortcut);
            }
        });
    }

    // Display upload custom cover art option if no cover art found
    private void showCustomCoverArtUploadOption(Shortcut shortcut) {
        runOnUiThread(() -> {
            coverArtView.setImageResource(android.R.color.transparent); // Remove existing placeholder
            coverArtView.setBackgroundColor(Color.parseColor("#99000000")); // Semi-transparent gray background

            // Set the image resource to show upload icon and update the click listener
            coverArtView.setImageResource(R.drawable.cover_art_placeholder);
            coverArtView.setOnClickListener(v -> promptForCustomCoverArtUpload());

            // Display message indicating no cover art found
            if (uploadText != null) {
                ViewGroup parent = (ViewGroup) uploadText.getParent();
                if (parent != null) {
                    parent.removeView(uploadText);
                }
            }

            uploadText = new TextView(this); // Initialize the uploadText variable
            uploadText.setText("No suitable cover art found for " + shortcut.name + ". Click the image to upload custom cover art or rename the Shortcut to something SteamGrid can recognize.");
            uploadText.setTextColor(Color.WHITE);
            uploadText.setTextSize(18);
            uploadText.setPadding(20, 20, 20, 20);
            uploadText.setGravity(Gravity.CENTER);
            uploadText.setBackgroundColor(Color.parseColor("#99000000")); // Semi-transparent gray background

            // Add the text view as a sibling to cover art view
            ViewGroup parent = (ViewGroup) coverArtView.getParent();
            parent.addView(uploadText);
        });
    }




    private void fetchGridsForGame(int gameId, Shortcut shortcut) {
        Gson gson = new GsonBuilder()
                .registerTypeAdapter(SteamGridGridsResponse.class, new SteamGridGridsResponseDeserializer())
                .setPrettyPrinting()
                .create();

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(new OkHttpClient())
                .addConverterFactory(GsonConverterFactory.create(gson))
                .build();

        SteamGridDBApi api = retrofit.create(SteamGridDBApi.class);

        Call<SteamGridGridsResponse> gridsCall = api.getGridsByGameId("Bearer " + API_KEY, gameId, "alternate", "600x900", "static");

        gridsCall.enqueue(new Callback<SteamGridGridsResponse>() {
            @Override
            public void onResponse(Call<SteamGridGridsResponse> call, Response<SteamGridGridsResponse> response) {
                if (response.isSuccessful() && response.body() != null && !response.body().data.isEmpty()) {
                    downloadCoverArt(response.body().data.get(0).url, shortcut);
                }
            }

            @Override
            public void onFailure(Call<SteamGridGridsResponse> call, Throwable t) {
                Log.e("SteamGridDB", "Failed to fetch cover art", t);
            }
        });
    }

    private void downloadCoverArt(String url, Shortcut shortcut) {
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
                connection.connect();
                InputStream input = connection.getInputStream();
                Bitmap coverArt = BitmapFactory.decodeStream(input);

                // Cache the downloaded cover art
                cacheCoverArt(coverArt, shortcut.name);

                // Set cover art in the shortcut and update the UI
                shortcut.setCoverArt(coverArt);
                runOnUiThread(() -> coverArtView.setImageBitmap(coverArt));
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private void cacheCoverArt(Bitmap coverArt, String shortcutName) {
        try {
            File cacheDir = new File(getCacheDir(), "coverArtCache");
            if (!cacheDir.exists()) {
                cacheDir.mkdirs();
            }
            File coverFile = new File(cacheDir, shortcutName + ".png");
            FileOutputStream outputStream = new FileOutputStream(coverFile);
            coverArt.compress(Bitmap.CompressFormat.PNG, 100, outputStream);
            outputStream.flush();
            outputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private Bitmap loadCachedCoverArt(String shortcutName) {
        try {
            File cacheDir = new File(getCacheDir(), "coverArtCache");
            File coverFile = new File(cacheDir, shortcutName + ".png");
            if (coverFile.exists()) {
                return BitmapFactory.decodeFile(coverFile.getAbsolutePath());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    // Handle result from image picker for custom cover art
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == RESULT_OK && requestCode == REQUEST_CODE_UPLOAD_CUSTOM_COVER) {
            if (data != null && data.getData() != null) {
                Uri selectedImageUri = data.getData();
                try {
                    InputStream inputStream = getContentResolver().openInputStream(selectedImageUri);
                    Bitmap customCoverArt = BitmapFactory.decodeStream(inputStream);

                    // Save custom cover art in shortcut and cache it
                    if (currentShortcut != null) {
                        currentShortcut.saveCustomCoverArt(customCoverArt);  // Save custom cover art

                        // Cache the custom cover art
                        cacheCoverArt(customCoverArt, currentShortcut.name);

                        // Set the custom cover art in the view
                        coverArtView.setImageBitmap(customCoverArt);

                        // Hide the upload text once the cover art is uploaded
                        if (uploadText != null) {
                            uploadText.setVisibility(View.GONE);
                        }

                        // Update the click listener to reflect that custom cover art is now present
                        coverArtView.setOnClickListener(v -> {
                            if (currentShortcut.getCustomCoverArtPath() != null) {
                                // Custom cover art exists, show the dialog
                                showCoverArtOptionsDialog();
                            } else {
                                // No custom cover art, directly prompt for uploading a new one
                                promptForCustomCoverArtUpload();
                            }
                        });

                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }


//    private String saveCustomCoverArt(Bitmap coverArt, String shortcutName) {
//        try {
//            File coverArtDir = new File(currentShortcut.container.getRootDir(), "app_data/cover_arts"); // Match path with Shortcut class
//            if (!coverArtDir.exists()) {
//                coverArtDir.mkdirs(); // Create directory if not exist
//            }
//
//            File coverFile = new File(coverArtDir, shortcutName + ".png");
//            FileOutputStream outputStream = new FileOutputStream(coverFile);
//            coverArt.compress(Bitmap.CompressFormat.PNG, 100, outputStream);
//            outputStream.flush();
//            outputStream.close();
//            return coverFile.getAbsolutePath(); // Return the file path for storing in shortcut
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//        return null;
//    }


    private String formatPlaytime(long playtimeInMillis) {
        long seconds = (playtimeInMillis / 1000) % 60;
        long minutes = (playtimeInMillis / (1000 * 60)) % 60;
        long hours = (playtimeInMillis / (1000 * 60 * 60)) % 24;
        long days = (playtimeInMillis / (1000 * 60 * 60 * 24));

        return String.format("%dd %02dh %02dm %02ds", days, hours, minutes, seconds);
    }
}
