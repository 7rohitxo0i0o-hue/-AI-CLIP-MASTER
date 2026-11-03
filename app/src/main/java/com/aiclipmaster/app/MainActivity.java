
package com.aiclipmaster.app;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;

public class MainActivity extends Activity {

    private static final int PICK_VIDEO = 100;

    private ExoPlayer player;
    private PlayerView playerView;
    private TextView fileName, startLabel, endLabel;
    private SeekBar startSlider, endSlider;

    private long durationMs = 0;
    private long startMs = 0;
    private long endMs = 0;

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
        endSlider.setProgress(1000);
        layout.addView(endSlider);

        Button previewButton = new Button(this);
        previewButton.setText("Preview Selected Part");
        layout.addView(previewButton);

        setContentView(layout);

        player = new ExoPlayer.Builder(this).build();
        playerView.setPlayer(player);

        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_READY) {
                    durationMs = player.getDuration();

                    if (durationMs > 0) {
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
                        updateSliderValues();
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
                        updateSliderValues();
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
            if (durationMs > 0 && player != null) {
                player.seekTo(startMs);
                player.play();
            }
        });
    }

    private void updateLabels() {
        startLabel.setText("Start: " + formatTime(startMs));
        endLabel.setText("End: " + formatTime(endMs));
        updateSliderValues();
    }

    private void updateSliderValues() {
        if (durationMs > 0) {
            startSlider.setProgress((int) (startMs * 1000 / durationMs));
            endSlider.setProgress((int) (endMs * 1000 / durationMs));
        }
    }

    private String formatTime(long ms) {
        long seconds = ms / 1000;
        return String.format("%02d:%02d", seconds / 60, seconds % 60);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode,
                                    Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == PICK_VIDEO && resultCode == RESULT_OK
                && data != null && data.getData() != null) {

            Uri videoUri = data.getData();

            try {
                getContentResolver().takePersistableUriPermission(
                        videoUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (SecurityException ignored) {}

            fileName.setText("Video selected successfully");

            durationMs = 0;
            startMs = 0;
            endMs = 0;

            MediaItem item = MediaItem.fromUri(videoUri);
            player.setMediaItem(item);
            player.prepare();
            player.play();
        }
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (player != null) {
            player.release();
            player = null;
        }
    }
}
