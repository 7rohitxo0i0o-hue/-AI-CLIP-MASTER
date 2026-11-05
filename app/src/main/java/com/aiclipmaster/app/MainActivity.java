
package com.aiclipmaster.app;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Bundle;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;
import androidx.media3.transformer.Composition;
import androidx.media3.transformer.EditedMediaItem;
import androidx.media3.transformer.ExportException;
import androidx.media3.transformer.ExportResult;
import androidx.media3.transformer.Transformer;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;

public class MainActivity extends Activity {

    private static final int PICK_VIDEO = 100;

    private ExoPlayer player;
    private PlayerView playerView;

    private TextView fileName;
    private TextView startLabel;
    private TextView endLabel;
    private TextView sceneResults;
    private TextView bestMomentsResults;

    private SeekBar startSlider;
    private SeekBar endSlider;

    private Uri selectedVideoUri;

    private long durationMs = 0;
    private long startMs = 0;
    private long endMs = 0;

    private boolean previewActive = false;

    private Transformer transformer;
    private File exportFile;

    private final ArrayList<long[]> smartClipCandidates =
            new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(20, 20, 20, 20);

        TextView title = new TextView(this);
        title.setText("AI CLIP MASTER");
        title.setTextSize(26);
        layout.addView(title);

        Button selectButton = new Button(this);
        selectButton.setText("SELECT VIDEO");
        layout.addView(selectButton);

        fileName = new TextView(this);
        fileName.setText("No video selected");
        layout.addView(fileName);

        playerView = new PlayerView(this);
        layout.addView(playerView,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dp(220)));

        startLabel = new TextView(this);
        layout.addView(startLabel);

        startSlider = new SeekBar(this);
        startSlider.setMax(1000);
        layout.addView(startSlider);

        endLabel = new TextView(this);
        layout.addView(endLabel);

        endSlider = new SeekBar(this);
        endSlider.setMax(1000);
        layout.addView(endSlider);

        Button previewButton = new Button(this);
        previewButton.setText("PREVIEW SELECTED PART");
        layout.addView(previewButton);

        Button exportButton = new Button(this);
        exportButton.setText("EXPORT VIDEO");
        layout.addView(exportButton);

        Button sceneButton = new Button(this);
        sceneButton.setText("AI SCENE DETECTION");
        layout.addView(sceneButton);

        sceneResults = new TextView(this);
        sceneResults.setText("Detected scenes will appear here");
        layout.addView(sceneResults);

        Button bestMomentsButton = new Button(this);
        bestMomentsButton.setText("FIND BEST MOMENTS");
        layout.addView(bestMomentsButton);

        bestMomentsResults = new TextView(this);
        bestMomentsResults.setText("Best moments will appear here");
        layout.addView(bestMomentsResults);

        LinearLayout clipButtonsLayout = new LinearLayout(this);
        clipButtonsLayout.setOrientation(LinearLayout.VERTICAL);
        layout.addView(clipButtonsLayout);

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.addView(layout);
        setContentView(scrollView);

        player = new ExoPlayer.Builder(this).build();
        playerView.setPlayer(player);

        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_ENDED && previewActive) {
                    previewActive = false;
                    Toast.makeText(MainActivity.this,
                            "Preview complete",
                            Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onPlayerError(
                    androidx.media3.common.PlaybackException error) {
                previewActive = false;
                Toast.makeText(MainActivity.this,
                        "Playback error: " + error.getMessage(),
                        Toast.LENGTH_LONG).show();
            }
        });

        selectButton.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.setType("video/*");
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.addFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION |
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            startActivityForResult(intent, PICK_VIDEO);
        });

        startSlider.setOnSeekBarChangeListener(
                new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(
                    SeekBar bar, int progress, boolean fromUser) {

                if (fromUser && durationMs > 0) {
                    startMs = durationMs * progress / 1000;

                    if (startMs >= endMs) {
                        startMs = Math.max(0, endMs - 1000);
                    }

                    updateLabels();
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar bar) {}

            @Override
            public void onStopTrackingTouch(SeekBar bar) {}
        });

        endSlider.setOnSeekBarChangeListener(
                new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(
                    SeekBar bar, int progress, boolean fromUser) {

                if (fromUser && durationMs > 0) {
                    endMs = durationMs * progress / 1000;

                    if (endMs <= startMs) {
                        endMs = Math.min(durationMs, startMs + 1000);
                    }

                    updateLabels();
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar bar) {}

            @Override
            public void onStopTrackingTouch(SeekBar bar) {}
        });

        previewButton.setOnClickListener(v -> previewSelectedPart());

        exportButton.setOnClickListener(v -> exportVideo());

        sceneButton.setOnClickListener(v -> detectScenes());

        bestMomentsButton.setOnClickListener(v -> {

            if (selectedVideoUri == null || durationMs <= 0) {
                showMessage("Please select a video first");
                return;
            }

            String sceneText = sceneResults.getText().toString();

            if (!sceneText.contains("Detected Scene Changes:")) {
                bestMomentsResults.setText(
                        "Please run AI Scene Detection first.");
                return;
            }

            ArrayList<Long> sceneTimes = new ArrayList<>();

            for (String line : sceneText.split("\n")) {
                if (line.startsWith("• ")) {
                    try {
                        String[] parts =
                                line.substring(2).trim().split(":");

                        long seconds =
                                Long.parseLong(parts[0]) * 60 +
                                Long.parseLong(parts[1]);

                        sceneTimes.add(seconds);
                    } catch (Exception ignored) {}
                }
            }

            generateSmartClips(sceneTimes);
            clipButtonsLayout.removeAllViews();

            StringBuilder result =
                    new StringBuilder("Smart Clip Candidates:\n\n");

            for (int i = 0; i < smartClipCandidates.size(); i++) {

                long[] clip = smartClipCandidates.get(i);

                result.append("Clip ")
                        .append(i + 1)
                        .append(": ")
                        .append(formatTime(clip[0] * 1000))
                        .append(" - ")
                        .append(formatTime(clip[1] * 1000))
                        .append(" (")
                        .append(clip[1] - clip[0])
                        .append(" sec)\n");

                final long clipStart = clip[0] * 1000;
                final long clipEnd = clip[1] * 1000;

                Button clipButton = new Button(this);
                clipButton.setText("SELECT CLIP " + (i + 1));

                clipButton.setOnClickListener(view -> {
                    player.pause();
                    previewActive = false;

                    startMs = clipStart;
                    endMs = clipEnd;

                    updateLabels();

                    showMessage("Clip selected");
                });

                clipButtonsLayout.addView(clipButton);
            }

            if (smartClipCandidates.isEmpty()) {
                result.append("No suitable clips found.");
            }

            bestMomentsResults.setText(result.toString());
        });

        updateLabels();
    }

    private void previewSelectedPart() {

        if (selectedVideoUri == null || durationMs <= 0) {
            showMessage("Select a video first");
            return;
        }

        if (startMs < 0 || endMs > durationMs ||
                endMs <= startMs) {
            showMessage("Invalid clip duration");
            return;
        }

        try {
            previewActive = false;
            player.pause();

            MediaItem clippedItem = new MediaItem.Builder()
                    .setUri(selectedVideoUri)
                    .setClippingConfiguration(
                            new MediaItem.ClippingConfiguration.Builder()
                                    .setStartPositionMs(startMs)
                                    .setEndPositionMs(endMs)
                                    .build())
                    .build();

            previewActive = true;
            player.setMediaItem(clippedItem, true);
            player.prepare();
            player.play();

        } catch (Exception e) {
            previewActive = false;
            showMessage("Preview error: " + e.getMessage());
        }
    }

    private void updateLabels() {

        startLabel.setText("Start: " + formatTime(startMs));
        endLabel.setText("End: " + formatTime(endMs));

        if (durationMs > 0) {
            startSlider.setProgress(
                    (int) (startMs * 1000 / durationMs));

            endSlider.setProgress(
                    (int) (endMs * 1000 / durationMs));
        }
    }

    private String formatTime(long ms) {
        long seconds = ms / 1000;
        return String.format("%02d:%02d",
                seconds / 60, seconds % 60);
    }

    private void generateSmartClips(ArrayList<Long> sceneTimes) {

        smartClipCandidates.clear();

        long totalSeconds = durationMs / 1000;

        if (totalSeconds <= 0 || sceneTimes.isEmpty()) {
            return;
        }

        Collections.sort(sceneTimes);

        for (long eventTime : sceneTimes) {

            long start = Math.max(0, eventTime - 10);
            long end = Math.min(totalSeconds, eventTime + 20);

            if (end - start < 10) {
                continue;
            }

            boolean overlaps = false;

            for (long[] clip : smartClipCandidates) {
                if (start < clip[1] && end > clip[0]) {
                    overlaps = true;
                    break;
                }
            }

            if (!overlaps) {
                smartClipCandidates.add(new long[]{start, end});
            }
        }
    }

    private void exportVideo() {

        if (selectedVideoUri == null || durationMs <= 0) {
            showMessage("Please select a video first");
            return;
        }

        if (startMs < 0 || endMs > durationMs ||
                startMs >= endMs) {
            showMessage("Invalid Start or End time");
            return;
        }

        player.pause();
        previewActive = false;

        try {
            exportFile = File.createTempFile(
                    "trimmed_video_", ".mp4", getCacheDir());

            MediaItem mediaItem = new MediaItem.Builder()
                    .setUri(selectedVideoUri)
                    .setClippingConfiguration(
                            new MediaItem.ClippingConfiguration.Builder()
                                    .setStartPositionMs(startMs)
                                    .setEndPositionMs(endMs)
                                    .build())
                    .build();

            EditedMediaItem editedItem =
                    new EditedMediaItem.Builder(mediaItem).build();

            transformer = new Transformer.Builder(this)
                    .addListener(new Transformer.Listener() {

                        @Override
                        public void onCompleted(
                                Composition composition,
                                ExportResult result) {
                            runOnUiThread(() -> {
                                try {
                                    saveToGallery(exportFile);
                                } catch (Exception e) {
                                    showMessage("Gallery save failed: " +
                                            e.getMessage());
                                }
                            });
                        }

                        @Override
                        public void onError(
                                Composition composition,
                                ExportResult result,
                                ExportException exception) {
                            runOnUiThread(() ->
                                    showMessage("Export failed: " +
                                            exception.getMessage()));
                        }
                    })
                    .build();

            showMessage("Video Export Started");
            transformer.start(editedItem, exportFile.getAbsolutePath());

        } catch (Exception e) {
            showMessage("Export error: " + e.getMessage());
        }
    }

    private void saveToGallery(File sourceFile) throws Exception {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {

            ContentValues values = new ContentValues();

            values.put(MediaStore.Video.Media.DISPLAY_NAME,
                    "AI_CLIP_" + System.currentTimeMillis() + ".mp4");

            values.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4");

            values.put(MediaStore.Video.Media.RELATIVE_PATH,
                    Environment.DIRECTORY_MOVIES + "/AI CLIP MASTER");

            values.put(MediaStore.Video.Media.IS_PENDING, 1);

            Uri galleryUri = getContentResolver().insert(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    values);

            if (galleryUri == null) {
                throw new Exception("Gallery save failed");
            }

            try (InputStream input = new FileInputStream(sourceFile);
                 OutputStream output =
                         getContentResolver().openOutputStream(galleryUri)) {

                if (output == null) {
                    throw new Exception("Cannot open Gallery file");
                }

                byte[] buffer = new byte[8192];
                int length;

                while ((length = input.read(buffer)) != -1) {
                    output.write(buffer, 0, length);
                }
            }

            ContentValues completed = new ContentValues();
            completed.put(MediaStore.Video.Media.IS_PENDING, 0);

            getContentResolver().update(
                    galleryUri, completed, null, null);

            showMessage("Export Complete! Check Movies/AI CLIP MASTER");

        } else {
            showMessage("Exported to: " + sourceFile.getAbsolutePath());
        }
    }

    @Override
    protected void onActivityResult(
            int requestCode, int resultCode, Intent data) {

        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == PICK_VIDEO &&
                resultCode == RESULT_OK &&
                data != null &&
                data.getData() != null) {

            selectedVideoUri = data.getData();

            try {
                getContentResolver().takePersistableUriPermission(
                        selectedVideoUri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (SecurityException ignored) {}

            player.pause();
            previewActive = false;

            durationMs = 0;
            startMs = 0;
            endMs = 0;
            smartClipCandidates.clear();

            sceneResults.setText("Detected scenes will appear here");
            bestMomentsResults.setText("Best moments will appear here");

            fileName.setText("Video selected successfully");

            try {
                MediaMetadataRetriever retriever =
                        new MediaMetadataRetriever();

                retriever.setDataSource(this, selectedVideoUri);

                String durationText = retriever.extractMetadata(
                        MediaMetadataRetriever.METADATA_KEY_DURATION);

                if (durationText == null) {
                    throw new Exception("Could not read video duration");
                }

                durationMs = Long.parseLong(durationText);
                retriever.release();

                if (durationMs <= 0) {
                    throw new Exception("Invalid video duration");
                }

                startMs = 0;
                endMs = durationMs;

                MediaItem originalItem =
                        MediaItem.fromUri(selectedVideoUri);

                player.setMediaItem(originalItem);
                player.prepare();

                updateLabels();

            } catch (Exception e) {
                durationMs = 0;
                showMessage("Video error: " + e.getMessage());
            }
        }
    }

    private void detectScenes() {

        if (selectedVideoUri == null || durationMs <= 0) {
            showMessage("Please select a video first");
            return;
        }

        sceneResults.setText("Detecting scenes...");

        new Thread(() -> {

            MediaMetadataRetriever retriever =
                    new MediaMetadataRetriever();

            ArrayList<String> scenes = new ArrayList<>();

            try {
                retriever.setDataSource(this, selectedVideoUri);

                int[] previous = null;

                for (long time = 0;
                     time < durationMs;
                     time += 1000) {

                    Bitmap frame = retriever.getFrameAtTime(
                            time * 1000,
                            MediaMetadataRetriever.OPTION_CLOSEST);

                    if (frame == null) {
                   
                        continue;
                    }

                    Bitmap small = Bitmap.createScaledBitmap(
                            frame, 32, 32, true);

                    int[] pixels = new int[32 * 32];

                    small.getPixels(
                            pixels, 0, 32, 0, 0, 32, 32);

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
                            scenes.add(formatTime(time * 1000));
                        }
                    }

                    previous = pixels;

                    if (small != frame) {
                        small.recycle();
                    }

                    frame.recycle();
                }

            } catch (Exception e) {
                scenes.add("Detection error: " + e.getMessage());

            } finally {
                try {
                    retriever.release();
                } catch (Exception ignored) {}
            }

            runOnUiThread(() -> {
                if (scenes.isEmpty()) {
                    sceneResults.setText(
                            "No major scene changes detected.");
                } else {
                    StringBuilder result =
                            new StringBuilder(
                                    "Detected Scene Changes:\n");

                    for (String time : scenes) {
                        result.append("• ")
                                .append(time)
                                .append("\n");
                    }

                    sceneResults.setText(result.toString());
                }
            });

        }).start();
    }

    private void showMessage(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
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
