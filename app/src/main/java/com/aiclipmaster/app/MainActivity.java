
package com.aiclipmaster.app;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.content.Intent;
import android.net.Uri;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.*;
import android.view.View;

public class MainActivity extends Activity {

    LinearLayout layout;
    TextView selectedVideo, durationText;
    VideoView videoView;
    SeekBar seekBar;
    Uri videoUri;

    Handler handler = new Handler();
    Runnable updateTimeline;

    int dp(int value) {
        return (int)(value * getResources()
            .getDisplayMetrics().density);
    }

    String formatTime(int ms) {
        int total = ms / 1000;
        int min = total / 60;
        int sec = total % 60;

        return String.format("%02d:%02d", min, sec);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setStatusBarColor(Color.rgb(15,15,25));
        getWindow().setNavigationBarColor(Color.rgb(15,15,25));

        ScrollView scroll = new ScrollView(this);

        layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(18), dp(35), dp(18), dp(25));
        layout.setBackgroundColor(Color.rgb(15,15,25));

        scroll.addView(layout);

        TextView title = new TextView(this);
        title.setText("AI CLIP MASTER");
        title.setTextSize(27);
        title.setTextColor(Color.WHITE);
        title.setTypeface(null, Typeface.BOLD);
        layout.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Your AI Video Editing Studio");
        subtitle.setTextColor(Color.LTGRAY);
        subtitle.setTextSize(14);
        addView(subtitle, 8);

        Button select = new Button(this);
        select.setText("＋ SELECT VIDEO");
        layout.addView(select);

        select.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.setType("video/*");
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(intent, 100);
        });

        selectedVideo = new TextView(this);
        selectedVideo.setText("No video selected");
        selectedVideo.setTextColor(Color.LTGRAY);
        selectedVideo.setTextSize(14);
        addView(selectedVideo, 12);

        addHeading("VIDEO PREVIEW");

        videoView = new VideoView(this);
        videoView.setBackgroundColor(Color.BLACK);

        LinearLayout.LayoutParams videoParams =
            new LinearLayout.LayoutParams(
                -1, dp(220)
            );

        videoParams.topMargin = dp(10);
        layout.addView(videoView, videoParams);

        durationText = new TextView(this);
        durationText.setText("00:00 / 00:00");
        durationText.setTextColor(Color.WHITE);
        durationText.setTextSize(14);
        addView(durationText, 8);

        seekBar = new SeekBar(this);
        layout.addView(seekBar);

        seekBar.setMax(1000);

        seekBar.setOnSeekBarChangeListener(
            new SeekBar.OnSeekBarChangeListener() {

                public void onProgressChanged(
                    SeekBar bar, int progress,
                    boolean fromUser) {

                    if (fromUser && videoView.isPlaying()) {
                        int duration = videoView.getDuration();

                        if (duration > 0) {
                            int position =
                                (duration * progress) / 1000;

                            videoView.seekTo(position);
                        }
                    } else if (fromUser) {
                        int duration = videoView.getDuration();

                        if (duration > 0) {
                            videoView.seekTo(
                                duration * progress / 1000
                            );
                        }
                    }
                }

                public void onStartTrackingTouch(SeekBar bar) {}

                public void onStopTrackingTouch(SeekBar bar) {}
            }
        );

        LinearLayout controls = new LinearLayout(this);
        controls.setGravity(Gravity.CENTER);

        Button play = new Button(this);
        play.setText("PLAY");
        controls.addView(play);

        Button pause = new Button(this);
        pause.setText("PAUSE");
        controls.addView(pause);

        layout.addView(controls);

        play.setOnClickListener(v -> {
            if (videoUri != null) {
                videoView.start();
            }
        });

        pause.setOnClickListener(v -> {
            if (videoUri != null) {
                videoView.pause();
            }
        });

        updateTimeline = new Runnable() {
            @Override
            public void run() {
                if (videoUri != null) {
                    int duration = videoView.getDuration();
                    int position = videoView.getCurrentPosition();

                    if (duration > 0) {
                        seekBar.setProgress(
                            position * 1000 / duration
                        );

                        durationText.setText(
                            formatTime(position) + " / " +
                            formatTime(duration)
                        );
                    }
                }

                handler.postDelayed(this, 500);
            }
        };

        handler.post(updateTimeline);

        addHeading("VIDEO TOOLS");

        addTool("AI Auto Clips", "Create short clips");
        addTool("AI Captions", "Generate subtitles");
        addTool("Viral Clip Detection", "Find interesting moments");
        addTool("Silence Removal", "Remove silent portions");
        addTool("AI Reframe", "Vertical and horizontal formats");
        addTool("Video Editor", "Edit your selected video");
        addTool("Export Video", "Save your edited video");

        TextView footer = new TextView(this);
        footer.setText("AI CLIP MASTER • Version 1.0");
        footer.setTextColor(Color.GRAY);
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(0, dp(25), 0, dp(15));
        layout.addView(footer);

        setContentView(scroll);
    }

    void addView(TextView view, int top) {
        LinearLayout.LayoutParams params =
            new LinearLayout.LayoutParams(-1, -2);

        params.topMargin = dp(top);
        layout.addView(view, params);
    }

    void addHeading(String text) {
        TextView heading = new TextView(this);
        heading.setText(text);
        heading.setTextColor(Color.WHITE);
        heading.setTextSize(20);
        heading.setTypeface(null, Typeface.BOLD);
        heading.setPadding(0, dp(22), 0, dp(10));
        layout.addView(heading);
    }

    void addTool(String name, String description) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        card.setBackgroundColor(Color.rgb(35,35,55));

        LinearLayout.LayoutParams params =
            new LinearLayout.LayoutParams(-1, -2);

        params.setMargins(0, dp(6), 0, dp(6));

        TextView heading = new TextView(this);
        heading.setText(name);
        heading.setTextColor(Color.WHITE);
        heading.setTextSize(17);
        heading.setTypeface(null, Typeface.BOLD);

        TextView detail = new TextView(this);
        detail.setText(description);
        detail.setTextColor(Color.LTGRAY);
        detail.setTextSize(13);

        card.addView(heading);
        card.addView(detail);
        layout.addView(card, params);
    }

    @Override
    protected void onActivityResult(
        int requestCode, int resultCode, Intent data) {

        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == 100 &&
            resultCode == RESULT_OK && data != null) {

            videoUri = data.getData();

            try {
                getContentResolver().takePersistableUriPermission(
                    videoUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                );
            } catch (Exception ignored) {}

            selectedVideo.setText("Video selected successfully!");

            videoView.setVideoURI(videoUri);

            videoView.setOnPreparedListener(mp -> {
                int duration = videoView.getDuration();

                seekBar.setProgress(0);

                durationText.setText(
                    "00:00 / " + formatTime(duration)
                );
            });
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (videoView != null && videoView.isPlaying()) {
            videoView.pause();
        }
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(updateTimeline);
        super.onDestroy();
    }
}
