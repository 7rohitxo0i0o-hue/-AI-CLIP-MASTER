
package com.aiclipmaster.app;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.net.Uri;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.view.View;
import android.widget.*;
import android.graphics.Color;

import androidx.annotation.Nullable;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;
import androidx.media3.transformer.Transformer;
import androidx.media3.transformer.EditedMediaItem;
import androidx.media3.transformer.Composition;
import androidx.media3.transformer.ExportException;
import androidx.media3.transformer.ExportResult;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Locale;

@UnstableApi
public class MainActivity extends Activity {

    private ExoPlayer player;
    private PlayerView playerView;
    private Transformer transformer;

    private Uri videoUri;

    private long durationMs = 0;
    private long clipStartMs = 0;
    private long clipEndMs = 0;

    private LinearLayout root;
    private TextView statusText;
    private TextView sceneResults;
    private TextView bestResults;

    private SeekBar startSeek;
    private SeekBar endSeek;

    private Button previewButton;
    private Button exportButton;
    private Button sceneButton;
    private Button bestButton;

    private final ArrayList<Long> sceneTimes =
            new ArrayList<>();

    private final ArrayList<long[]> bestClips =
            new ArrayList<>();

    private int selectedClip = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        buildInterface();

        player = new ExoPlayer.Builder(this).build();
        playerView.setPlayer(player);

        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_ENDED) {
                    statusText.setText("Preview complete");
                }
            }
        });
    }

    private void buildInterface() {

        ScrollView scroll = new ScrollView(this);

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(16), dp(16), dp(24));
        root.setBackgroundColor(Color.rgb(18, 18, 18));

        scroll.addView(root);
        setContentView(scroll);

        TextView title = new TextView(this);
        title.setText("AI CLIP MASTER");
        title.setTextSize(26);
        title.setTextColor(Color.WHITE);
        title.setGravity(17);
        title.setPadding(0, dp(12), 0, dp(18));
        root.addView(title);

        Button selectButton = makeButton("SELECT VIDEO");
        root.addView(selectButton);
        selectButton.setOnClickListener(v -> selectVideo());

        playerView = new PlayerView(this);
        playerView.setLayoutParams(new LinearLayout.LayoutParams(
                -1, dp(220)));
        root.addView(playerView);

        statusText = makeText("Select a video to start");
        root.addView(statusText);

        TextView trimTitle = makeText("TRIM VIDEO");
        root.addView(trimTitle);

        TextView startLabel = makeText("Start: 00:00");
        root.addView(startLabel);

        startSeek = new SeekBar(this);
        startSeek.setMax(1000);
        root.addView(startSeek);

        TextView endLabel = makeText("End: 00:00");
        root.addView(endLabel);

        endSeek = new SeekBar(this);
        endSeek.setMax(1000);
        root.addView(endSeek);

        startSeek.setOnSeekBarChangeListener(
                new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(
                    SeekBar seekBar, int progress,
                    boolean fromUser) {
                if (fromUser && durationMs > 0) {
                    clipStartMs = durationMs * progress / 1000;
                    if (clipStartMs >= clipEndMs) {
                        clipStartMs = Math.max(0, clipEndMs - 1000);
                    }
                    startLabel.setText(
                            "Start: " + formatTime(clipStartMs));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar s) {}

            @Override
            public void onStopTrackingTouch(SeekBar s) {}
        });

        endSeek.setOnSeekBarChangeListener(
                new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(
                    SeekBar seekBar, int progress,
                    boolean fromUser) {
                if (fromUser && durationMs > 0) {
                    clipEndMs = durationMs * progress / 1000;
                    if (clipEndMs <= clipStartMs) {
                        clipEndMs = Math.min(
                                durationMs, clipStartMs + 1000);
                    }
                    endLabel.setText(
                            "End: " + formatTime(clipEndMs));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar s) {}

            @Override
            public void onStopTrackingTouch(SeekBar s) {}
        });

        previewButton = makeButton("PREVIEW SELECTED PART");
        root.addView(previewButton);
        previewButton.setOnClickListener(v -> previewSelectedPart());

        exportButton = makeButton("EXPORT VIDEO");
        root.addView(exportButton);
        exportButton.setOnClickListener(v -> exportVideo());

        sceneButton = makeButton("AI SCENE DETECTION");
        root.addView(sceneButton);
        sceneButton.setOnClickListener(v -> detectScenes());

        sceneResults = makeText("Scene results will appear here");
        root.addView(sceneResults);

        bestButton = makeButton("FIND BEST MOMENTS");
        root.addView(bestButton);
        bestButton.setOnClickListener(v -> findBestMoments());

        bestResults = makeText("Best moments will appear here");
        root.addView(bestResults);
    }

    private Button makeButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextColor(Color.WHITE);
        button.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(
                        Color.rgb(90, 40, 180)));
        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(-1, dp(52));
        params.setMargins(0, dp(8), 0, dp(8));
        button.setLayoutParams(params);
        return button;
    }

    private TextView makeText(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(Color.WHITE);
        view.setTextSize(15);
        view.setPadding(0, dp(10), 0, dp(10));
        return view;
    }

    private void selectVideo() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("video/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(intent, 100);
    }

    @Override
    protected void onActivityResult(
            int requestCode, int resultCode,
            @Nullable Intent data) {

        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == 100 &&
                resultCode == Activity.RESULT_OK &&
                data != null && data.getData() != null) {

            videoUri = data.getData();

            try {
                MediaMetadataRetriever retriever =
                        new MediaMetadataRetriever();

                retriever.setDataSource(this, videoUri);

                String value = retriever.extractMetadata(
                        MediaMetadataRetriever.METADATA_KEY_DURATION);

                durationMs = Long.parseLong(value);

                retriever.release();

                clipStartMs = 0;
                clipEndMs = durationMs;

                startSeek.setProgress(0);
                endSeek.setProgress(1000);

                player.setMediaItem(MediaItem.fromUri(videoUri));
                player.prepare();

                statusText.setText(
                        "Video loaded: " + formatTime(durationMs));

                sceneTimes.clear();
                bestClips.clear();
                sceneResults.setText("Scene results will appear here");
                bestResults.setText("Best moments will appear here");

            } catch (Exception e) {
                showMessage("Video loading error: " + e.getMessage());
            }
        }
    }

    private void previewSelectedPart() {

        if (videoUri == null || durationMs <= 0) {
            showMessage("Please select a video first");
            return;
        }

        if (clipEndMs <= clipStartMs) {
            showMessage("Please select a valid clip range");
            return;
        }

        MediaItem clippedItem = new MediaItem.Builder()
                .setUri(videoUri)
                .setClippingConfiguration(
                        new MediaItem.ClippingConfiguration.Builder()
                                .setStartPositionMs(clipStartMs)
                                .setEndPositionMs(clipEndMs)
                                .build())
                .build();

        player.setMediaItem(clippedItem);
        player.prepare();
        player.play();

        statusText.setText(
                "Playing: " + formatTime(clipStartMs)
                        + " - " + formatTime(clipEndMs));
    }
    
    // SCENE DETECTION - timestamps are stored in milliseconds
    private void detectScenes() {

        if (videoUri == null || durationMs <= 0) {
            showMessage("Please select a video first");
            return;
        }

        sceneButton.setEnabled(false);
        sceneResults.setText("Detecting scenes...");

        new Thread(() -> {

            ArrayList<Long> detected = new ArrayList<>();
            MediaMetadataRetriever retriever =
                    new MediaMetadataRetriever();

            try {
                retriever.setDataSource(this, videoUri);

                int[] previous = null;

                // Sample once per second, using actual milliseconds
                for (long timeMs = 1000;
                     timeMs < durationMs;
                     timeMs += 1000) {

                    Bitmap frame = retriever.getFrameAtTime(
                            timeMs * 1000,
                            MediaMetadataRetriever.OPTION_CLOSEST);

                    if (frame == null) {
                        continue;
                    }

                    Bitmap small = Bitmap.createScaledBitmap(
                            frame, 32, 32, true);

                    int[] pixels = new int[32 * 32];
                    small.getPixels(pixels, 0, 32, 0, 0, 32, 32);

                    if (previous != null) {
                        long difference = 0;

                        for (int i = 0; i < pixels.length; i++) {
                            int r1 = (pixels[i] >> 16) & 255;
                            int g1 = (pixels[i] >> 8) & 255;
                            int b1 = pixels[i] & 255;

                            int r2 = (previous[i] >> 16) & 255;
                            int g2 = (previous[i] >> 8) & 255;
                            int b2 = previous[i] & 255;

                            difference += Math.abs(r1 - r2);
                            difference += Math.abs(g1 - g2);
                            difference += Math.abs(b1 - b2);
                        }

                        double average = difference /
                                (double) (pixels.length * 3);

                        if (average > 45) {
                            detected.add(timeMs);
                        }
                    }

                    previous = pixels;

                    if (small != frame) {
                        small.recycle();
                    }

                    frame.recycle();
                }

            } catch (Exception e) {
                runOnUiThread(() -> {
                    sceneButton.setEnabled(true);
                    sceneResults.setText(
                            "Detection error: " + e.getMessage());
                });
                return;

            } finally {
                try {
                    retriever.release();
                } catch (Exception ignored) {}
            }

            runOnUiThread(() -> {
                sceneTimes.clear();
                sceneTimes.addAll(detected);
                Collections.sort(sceneTimes);

                sceneButton.setEnabled(true);

                if (sceneTimes.isEmpty()) {
                    sceneResults.setText(
                            "No major scene changes detected.");
                    return;
                }

                StringBuilder result =
                        new StringBuilder("Detected Scene Changes:\n");

                for (long time : sceneTimes) {
                    result.append("• ")
                            .append(formatTime(time))
                            .append("\n");
                }

                sceneResults.setText(result.toString());
            });

        }).start();
    }

    // FIND BEST MOMENTS
    private void findBestMoments() {

        if (videoUri == null || durationMs <= 0) {
            showMessage("Please select a video first");
            return;
        }

        bestClips.clear();

        // Create clips around detected scene changes.
        // All calculations use milliseconds.
        for (long scene : sceneTimes) {

            long start = Math.max(0, scene - 10000);
            long end = Math.min(durationMs, scene + 20000);

            if (end - start < 5000) {
                continue;
            }

            boolean overlaps = false;

            for (long[] existing : bestClips) {
                if (start < existing[1] &&
                        end > existing[0]) {
                    overlaps = true;
                    break;
                }
            }

            if (!overlaps) {
                bestClips.add(new long[]{start, end});
            }

            if (bestClips.size() >= 10) {
                break;
            }
        }

        // If no scene changes were found, make simple
        // time-based suggestions instead.
        if (bestClips.isEmpty()) {

            long start = 0;

            while (start < durationMs &&
                    bestClips.size() < 10) {

                long end = Math.min(durationMs, start + 20000);

                if (end - start >= 5000) {
                    bestClips.add(new long[]{start, end});
                }

                start += 20000;
            }
        }

        if (bestClips.isEmpty()) {
            bestResults.setText("Video is too short for clip suggestions.");
            return;
        }

        StringBuilder result =
                new StringBuilder("Smart Clip Candidates:\n\n");

        for (int i = 0; i < bestClips.size(); i++) {
            long[] clip = bestClips.get(i);

            result.append("Clip ")
                    .append(i + 1)
                    .append(": ")
                    .append(formatTime(clip[0]))
                    .append(" - ")
                    .append(formatTime(clip[1]))
                    .append("\n");
        }

        bestResults.setText(result.toString());

        // Add one selection button for each candidate.
        for (int i = 0; i < bestClips.size(); i++) {

            final int index = i;

            Button selectClip = makeButton(
                    "SELECT CLIP " + (i + 1));

            root.addView(selectClip);

            selectClip.setOnClickListener(v ->
                    selectBestClip(index));
        }
    }

    // SELECT A SUGGESTED CLIP
    private void selectBestClip(int index) {

        if (index < 0 || index >= bestClips.size()) {
            showMessage("Invalid clip selection");
            return;
        }

        long[] clip = bestClips.get(index);

        clipStartMs = clip[0];
        clipEndMs = clip[1];
        selectedClip = index;

        startSeek.setProgress(
                (int) (clipStartMs * 1000 / durationMs));

        endSeek.setProgress(
                (int) (clipEndMs * 1000 / durationMs));

        statusText.setText(
                "Selected Clip " + (index + 1)
                        + ": " + formatTime(clipStartMs)
                        + " - " + formatTime(clipEndMs));

        previewSelectedPart();
    }

    // EXPORT VIDEO
    private void exportVideo() {

        if (videoUri == null || durationMs <= 0) {
            showMessage("Please select a video first");
            return;
        }

        if (clipEndMs <= clipStartMs) {
            showMessage("Invalid clip range");
            return;
        }

        try {
            exportButton.setEnabled(false);
            statusText.setText("Preparing export...");

            File outputDir = getExternalFilesDir(
                    android.os.Environment.DIRECTORY_MOVIES);

            if (outputDir == null) {
                throw new Exception("Storage directory unavailable");
            }

            if (!outputDir.exists()) {
                outputDir.mkdirs();
            }

            File outputFile = new File(
                    outputDir,
                    "AI_CLIP_" + System.currentTimeMillis() + ".mp4");

            MediaItem clippedItem = new MediaItem.Builder()
                    .setUri(videoUri)
                    .setClippingConfiguration(
                            new MediaItem.ClippingConfiguration.Builder()
                                    .setStartPositionMs(clipStartMs)
                                    .setEndPositionMs(clipEndMs)
                                    .build())
                    .build();

            EditedMediaItem editedMediaItem =
                    new EditedMediaItem.Builder(clippedItem).build();

            transformer = new Transformer.Builder(this)
                    .addListener(new Transformer.Listener() {

                        @Override
                        public void onCompleted(
                                Composition composition,
                                ExportResult exportResult) {

                            runOnUiThread(() -> {
                                saveToGallery(outputFile);
                            });
                        }

                        @Override
                        public void onError(
                                Composition composition,
                                ExportResult exportResult,
                                ExportException exception) {

                            runOnUiThread(() -> {
                                exportButton.setEnabled(true);
                                statusText.setText("Export failed");
                                showMessage(exception.getMessage());
                            });
                        }
                    })
                    .build();

            transformer.start(
                    editedMediaItem,
                    outputFile.getAbsolutePath());

            statusText.setText("Exporting video...");

        } catch (Exception e) {
            exportButton.setEnabled(true);
            showMessage("Export error: " + e.getMessage());
        }
    }

    // SAVE EXPORTED FILE TO PHONE GALLERY
    private void saveToGallery(File sourceFile) {

        try {
            if (!sourceFile.exists()) {
                throw new Exception("Exported file not found");
            }

            android.content.ContentValues values =
                    new android.content.ContentValues();

            values.put(
                    android.provider.MediaStore.Video.Media.DISPLAY_NAME,
                    sourceFile.getName());

            values.put(
                    android.provider.MediaStore.Video.Media.MIME_TYPE,
                    "video/mp4");

            if (android.os.Build.VERSION.SDK_INT >= 29) {
                values.put(
                        android.provider.MediaStore.Video.Media.RELATIVE_PATH,
                        "Movies/AI Clip Master");

                values.put(
                        android.provider.MediaStore.Video.Media.IS_PENDING,
                        1);
            }

            Uri collection =
                    android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI;

            Uri galleryUri = getContentResolver().insert(
                    collection, values);

            if (galleryUri == null) {
                throw new Exception("Could not create gallery file");
            }

            try (
                    java.io.InputStream input =
                            new java.io.FileInputStream(sourceFile);

                    java.io.OutputStream output =
                            getContentResolver().openOutputStream(galleryUri)
            ) {
                if (output == null) {
                    throw new Exception("Could not open gallery file");
                }

                byte[] buffer = new byte[8192];
                int length;

                while ((length = input.read(buffer)) != -1) {
                    output.write(buffer, 0, length);
                }

                output.flush();
            }

            if (android.os.Build.VERSION.SDK_INT >= 29) {
                android.content.ContentValues complete =
                        new android.content.ContentValues();

                complete.put(
                        android.provider.MediaStore.Video.Media.IS_PENDING,
                        0);

                getContentResolver().update(
                        galleryUri, complete, null, null);
            }

            exportButton.setEnabled(true);
            statusText.setText("Export completed!");
            showMessage("Video saved to Gallery!");

        } catch (Exception e) {
            exportButton.setEnabled(true);
            statusText.setText("Gallery save failed");
            showMessage("Save error: " + e.getMessage());
        }
    }

    // FORMAT TIME AS MM:SS
    private String formatTime(long milliseconds) {

        long totalSeconds = Math.max(0, milliseconds) / 1000;

        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;

        return String.format(
                Locale.getDefault(),
                "%02d:%02d",
                minutes,
                seconds);
    }

    private void showMessage(String message) {
        Toast.makeText(
                this,
                message,
                Toast.LENGTH_LONG).show();
    }

    private int dp(int value) {
        return (int) (value * getResources()
                .getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {

        if (transformer != null) {
            transformer.cancel();
        }

        if (player != null) {
            player.release();
            player = null;
        }

        super.onDestroy();
    }
}

    
