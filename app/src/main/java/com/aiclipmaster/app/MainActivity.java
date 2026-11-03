
package com.aiclipmaster.app;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import java.util.ArrayList;
import android.os.Handler;
import android.os.Looper;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.SeekBar;
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
import java.io.OutputStream;
import java.io.InputStream;

public class MainActivity extends Activity {

    private static final int PICK_VIDEO = 100;

    private ExoPlayer player;
    private PlayerView playerView;
    private TextView fileName, startLabel, endLabel;
    private SeekBar startSlider, endSlider;

    private Uri selectedVideoUri;
    private TextView sceneResults;
    private long durationMs = 0;
    private long startMs = 0;
    private long endMs = 0;

    private Transformer transformer;
    private File exportFile;

    private final Handler handler = new Handler(Looper.getMainLooper());

    private final Runnable endMonitor = new Runnable() {
        @Override
        public void run() {
            if (player != null && player.isPlaying()
                    && endMs > 0 && player.getCurrentPosition() >= endMs) {
                player.pause();
                player.seekTo(endMs);
            }
            handler.postDelayed(this, 200);
        }
    };

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
        selectButton.setText("Select Video");
        layout.addView(selectButton);

        fileName = new TextView(this);
        fileName.setText("No video selected");
        layout.addView(fileName);

        playerView = new PlayerView(this);
        layout.addView(playerView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(220)));

        startLabel = new TextView(this);
        startLabel.setText("Start: 00:00");
        layout.addView(startLabel);

        startSlider = new SeekBar(this);
        startSlider.setMax(1000);
        layout.addView(startSlider);

        endLabel = new TextView(this);
        endLabel.setText("End: 00:00");
        layout.addView(endLabel);

        endSlider = new SeekBar(this);
        endSlider.setMax(1000);
        layout.addView(endSlider);

        Button previewButton = new Button(this);
        previewButton.setText("Preview Selected Part");
        layout.addView(previewButton);

        Button exportButton = new Button(this);
        exportButton.setText("Export Video");
        layout.addView(exportButton);
        Button sceneButton = new Button(this);
sceneButton.setText("AI Scene Detection");
layout.addView(sceneButton);

sceneResults = new TextView(this);
sceneResults.setText("Detected scenes will appear here");
layout.addView(sceneResults);

sceneButton.setOnClickListener(v -> detectScenes());

        setContentView(layout);

        player = new ExoPlayer.Builder(this).build();
        playerView.setPlayer(player);

        handler.post(endMonitor);

        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_READY) {
                    long duration = player.getDuration();

                    if (duration > 0) {
                        durationMs = duration;
                        startMs = 0;
                        endMs = durationMs;
                        updateLabels();
                    }
                }
            }
        });

        selectButton.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.setType("video/*");
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                    | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            startActivityForResult(intent, PICK_VIDEO);
        });

        startSlider.setOnSeekBarChangeListener(
                new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int progress,
                                         boolean fromUser) {
                if (fromUser && durationMs > 0) {
                    startMs = durationMs * progress / 1000;

                    if (startMs >= endMs) {
                        startMs = Math.max(0, endMs - 1);
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
            public void onProgressChanged(SeekBar bar, int progress,
                                         boolean fromUser) {
                if (fromUser && durationMs > 0) {
                    endMs = durationMs * progress / 1000;

                    if (endMs <= startMs) {
                        endMs = Math.min(durationMs, startMs + 1);
                    }

                    updateLabels();
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar bar) {}

            @Override
            public void onStopTrackingTouch(SeekBar bar) {}
        });

        previewButton.setOnClickListener(v -> {
            if (selectedVideoUri != null && durationMs > 0) {
                player.seekTo(startMs);
                player.play();
            } else {
                Toast.makeText(this, "Select a video first",
                        Toast.LENGTH_SHORT).show();
            }
        });

        exportButton.setOnClickListener(v -> exportVideo());
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
        return String.format("%02d:%02d", seconds / 60, seconds % 60);
    }

    private void exportVideo() {
        if (selectedVideoUri == null || durationMs <= 0) {
            Toast.makeText(this, "Please select a video first",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        if (startMs >= endMs) {
            Toast.makeText(this, "Invalid Start or End time",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        player.pause();

        try {
            File outputDirectory = getCacheDir();
            exportFile = File.createTempFile(
                    "trimmed_video_", ".mp4", outputDirectory);

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
                                    Toast.makeText(MainActivity.this,
                                            "Could not save video",
                                            Toast.LENGTH_LONG).show();
                                }
                            });
                        }

                        @Override
                        public void onError(
                                Composition composition,
                                ExportResult result,
                                ExportException exception) {
                            runOnUiThread(() ->
                                    Toast.makeText(MainActivity.this,
                                            "Export failed: "
                                                    + exception.getMessage(),
                                            Toast.LENGTH_LONG).show());
                        }
                    })
                    .build();

            Toast.makeText(this, "Video Export Started",
                    Toast.LENGTH_SHORT).show();

            transformer.start(editedItem, exportFile.getAbsolutePath());

        } catch (Exception e) {
            Toast.makeText(this, "Error: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
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
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values);

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

            Toast.makeText(this,
                    "Export Complete! Check Movies/AI CLIP MASTER",
                    Toast.LENGTH_LONG).show();

        } else {
            Toast.makeText(this,
                    "Exported to: " + sourceFile.getAbsolutePath(),
                    Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode,
                                    Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == PICK_VIDEO && resultCode == RESULT_OK
                && data != null && data.getData() != null) {

            selectedVideoUri = data.getData();

            try {
                getContentResolver().takePersistableUriPermission(
                        selectedVideoUri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (SecurityException ignored) {}

            fileName.setText("Video selected successfully");

            durationMs = 0;
            startMs = 0;
            endMs = 0;

            player.setMediaItem(MediaItem.fromUri(selectedVideoUri));
            player.prepare();
            player.play();
        }
    }

    private int dp(int value) {
        return (int) (value * getResources()
                .getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(endMonitor);

        if (transformer != null) {
            transformer.cancel();
        }

        if (player != null) {
            player.release();
            player = null;
        }

        super.onDestroy();
    }
    private void detectScenes() {
    if (selectedVideoUri == null) {
        Toast.makeText(this, "Please select a video first",
                Toast.LENGTH_SHORT).show();
        return;
    }

    sceneResults.setText("Detecting scenes...");

    new Thread(() -> {
        MediaMetadataRetriever retriever =
                new MediaMetadataRetriever();

        ArrayList<String> scenes = new ArrayList<>();

        try {
            retriever.setDataSource(this, selectedVideoUri);

            String durationText = retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_DURATION);

            long duration = Long.parseLong(durationText);

            int[] previous = null;

            for (long time = 0; time < duration; time += 1000) {
                Bitmap frame = retriever.getFrameAtTime(
                        time * 1000,
                        MediaMetadataRetriever.OPTION_CLOSEST);

                if (frame == null) continue;

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
                        scenes.add(formatTime(time));
                    }
                }

                previous = pixels;

                if (small != frame) small.recycle();
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
                StringBuilder result = new StringBuilder(
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
}
