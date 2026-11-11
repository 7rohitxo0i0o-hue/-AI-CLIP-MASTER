package com.aiclipmaster.app;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.view.Gravity;
import android.net.Uri;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CaptionsActivity extends Activity {

    private TextView statusText;
    private Button extractButton;
    private ExecutorService executor;

    private String videoUriString = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        videoUriString = getIntent().getStringExtra("VIDEO_URI");

        if (videoUriString == null) {
            videoUriString = "";
        }

        executor = Executors.newSingleThreadExecutor();

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(20, 20, 20, 20);
        root.setBackgroundColor(Color.rgb(18, 18, 30));

        TextView title = new TextView(this);
        title.setText("AI CLIP MASTER");
        title.setTextSize(23);
        title.setTextColor(Color.WHITE);
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("AUTO CAPTIONS");
        subtitle.setTextSize(18);
        subtitle.setTextColor(Color.WHITE);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setPadding(0, 15, 0, 10);
        root.addView(subtitle);

        statusText = new TextView(this);
        statusText.setTextColor(Color.WHITE);
        statusText.setTextSize(15);
        statusText.setPadding(0, 20, 0, 20);

        statusText.setText(
                videoUriString.isEmpty()
                        ? "Please select a video first."
                        : "Video received. Ready to extract audio."
        );

        root.addView(statusText);

        extractButton = new Button(this);
        extractButton.setText("EXTRACT VIDEO AUDIO");
        root.addView(extractButton);

        extractButton.setOnClickListener(v -> extractAudio());

        Button close = new Button(this);
        close.setText("BACK");
        root.addView(close);

        close.setOnClickListener(v -> finish());

        setContentView(root);
    }

    private void extractAudio() {

        if (videoUriString.isEmpty()) {
            statusText.setText("Please select a video first.");
            return;
        }

        extractButton.setEnabled(false);
        statusText.setText("Extracting audio. Please wait...");

        executor.execute(() -> {
            try {
                Uri videoUri = Uri.parse(videoUriString);

                AudioExtractor.AudioData audio =
                        AudioExtractor.extractAudio(
                                this,
                                videoUri
                        );

                runOnUiThread(() -> {
                    extractButton.setEnabled(true);

                    statusText.setText(
                            "Audio extracted successfully!\n\n" +
                            "Sample Rate: " + audio.sampleRate + " Hz\n" +
                            "Channels: Mono\n" +
                            "Audio Samples: " + audio.samples.length + "\n" +
                            "Duration: " +
                            String.format(
                                    java.util.Locale.US,
                                    "%.2f seconds",
                                    audio.samples.length / 16000.0
                            )
                    );
                });

            } catch (Exception e) {
                runOnUiThread(() -> {
                    extractButton.setEnabled(true);
                    statusText.setText(
                            "Audio extraction failed:\n" +
                            e.getMessage()
                    );
                });
            }
        });
    }

    @Override
    protected void onDestroy() {
        if (executor != null) {
            executor.shutdownNow();
        }

        super.onDestroy();
    }
}
