
package com.aiclipmaster.app;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.content.Intent;
import android.net.Uri;
import android.graphics.Color;
import android.graphics.SurfaceTexture;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.Gravity;
import android.widget.*;
import android.media.MediaPlayer;

public class MainActivity extends Activity {

    LinearLayout layout;
    TextureView textureView;
    TextView selectedVideo, durationText, trimText;
    SeekBar seekBar;

    Uri videoUri;
    Surface videoSurface;
    MediaPlayer mediaPlayer;

    Handler handler = new Handler();

    int videoDuration = 0;
    int startTime = 0;
    int endTime = 0;

    boolean isPrepared = false;
    boolean isTrimPreview = false;
    boolean userSeeking = false;

    int dp(int value) {
        return (int) (value * getResources()
                .getDisplayMetrics().density);
    }

    String formatTime(int ms) {
        int total = Math.max(0, ms / 1000);
        return String.format("%02d:%02d",
                total / 60, total % 60);
    }

    void addText(String value, int size) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(size);
        text.setTextColor(Color.WHITE);
        text.setPadding(0, dp(10), 0, dp(10));
        layout.addView(text);
    }

    void addButton(String value, Runnable action) {
        Button button = new Button(this);
        button.setText(value);
        layout.addView(button);

        button.setOnClickListener(v -> action.run());
    }

    void updateTrimText() {
        if (trimText == null) return;

        trimText.setText(
                "Start: " + formatTime(startTime)
                + "\nEnd: " + formatTime(endTime)
                + "\nSelected Duration: "
                + formatTime(Math.max(0, endTime - startTime))
        );
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setStatusBarColor(Color.rgb(15, 15, 20));
        getWindow().setNavigationBarColor(Color.BLACK);

        ScrollView scroll = new ScrollView(this);

        layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(18), dp(25), dp(18), dp(30));
        layout.setBackgroundColor(Color.rgb(18, 18, 25));

        scroll.addView(layout);
        setContentView(scroll);

        addText("AI CLIP MASTER", 27);
        addText("Your AI Video Editing Studio", 14);

        addButton("+ SELECT VIDEO", () -> selectVideo());

        selectedVideo = new TextView(this);
        selectedVideo.setText("No video selected");
        selectedVideo.setTextColor(Color.LTGRAY);
        selectedVideo.setPadding(0, dp(10), 0, dp(10));
        layout.addView(selectedVideo);

        textureView = new TextureView(this);
        textureView.setBackgroundColor(Color.BLACK);
        textureView.setSurfaceTextureListener(
                new TextureView.SurfaceTextureListener() {

            @Override
            public void onSurfaceTextureAvailable(
                    SurfaceTexture surface, int width, int height) {

                videoSurface = new Surface(surface);
                preparePlayer();
            }

            @Override
            public void onSurfaceTextureSizeChanged(
                    SurfaceTexture surface, int width, int height) {
            }

            @Override
            public boolean onSurfaceTextureDestroyed(
                    SurfaceTexture surface) {

                if (mediaPlayer != null) {
                    mediaPlayer.setSurface(null);
                }

                if (videoSurface != null) {
                    videoSurface.release();
                    videoSurface = null;
                }

                return true;
            }

            @Override
            public void onSurfaceTextureUpdated(
                    SurfaceTexture surface) {
            }
        });

        LinearLayout.LayoutParams videoParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dp(230));

        videoParams.setMargins(0, dp(12), 0, dp(12));
        layout.addView(textureView, videoParams);

        addButton("PLAY / PAUSE", () -> togglePlayback());

        durationText = new TextView(this);
        durationText.setText("00:00 / 00:00");
        durationText.setTextColor(Color.WHITE);
        layout.addView(durationText);

        seekBar = new SeekBar(this);
        seekBar.setMax(1000);
        layout.addView(seekBar);

        seekBar.setOnSeekBarChangeListener(
                new SeekBar.OnSeekBarChangeListener() {

            @Override
            public void onProgressChanged(
                    SeekBar bar, int progress, boolean fromUser) {

                if (fromUser && isPrepared && mediaPlayer != null) {
                    int position = videoDuration * progress / 1000;
                    durationText.setText(
                            formatTime(position) + " / "
                            + formatTime(videoDuration));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar bar) {
                userSeeking = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar bar) {
                userSeeking = false;

                if (isPrepared && mediaPlayer != null) {
                    int position =
                            videoDuration * bar.getProgress() / 1000;
                    mediaPlayer.seekTo(position);
                }
            }
        });

        addText("VIDEO TRIM CONTROLS", 20);

        trimText = new TextView(this);
        trimText.setTextColor(Color.WHITE);
        trimText.setTextSize(15);
        layout.addView(trimText);

        addButton("SET START TIME", () -> {
            if (isPrepared && mediaPlayer != null) {
                startTime = mediaPlayer.getCurrentPosition();
                if (endTime < startTime) endTime = videoDuration;
                updateTrimText();
            }
        });

        addButton("SET END TIME", () -> {
            if (isPrepared && mediaPlayer != null) {
                endTime = mediaPlayer.getCurrentPosition();
                if (endTime < startTime) startTime = 0;
                updateTrimText();
            }
        });

        addButton("PREVIEW SELECTED PART", () -> {
            if (!isPrepared || mediaPlayer == null) {
                showMessage("Please select a video first");
                return;
            }

            if (endTime <= startTime) {
                showMessage("Please set valid start and end times");
                return;
            }

            isTrimPreview = true;
            mediaPlayer.seekTo(startTime);
            mediaPlayer.start();
        });

        addText("AI VIDEO TOOLS", 21);
        addText("AI Auto Clips  •  Coming Soon", 16);
        addText("AI Captions  •  Coming Soon", 16);
        addText("Viral Clip Detection  •  Coming Soon", 16);
        addText("Silence Removal  •  Coming Soon", 16);
        addText("AI Reframe  •  Coming Soon", 16);
        addText("Video Editor  •  In Development", 16);
        addText("Export Video  •  Coming Soon", 16);

        updateTrimText();

        handler.post(updateTimeline);
    }

    void selectVideo() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("video/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
        );

        startActivityForResult(intent, 100);
    }

    @Override
    protected void onActivityResult(
            int requestCode, int resultCode, Intent data) {

        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == 100 && resultCode == RESULT_OK
                && data != null && data.getData() != null) {

            videoUri = data.getData();

            try {
                getContentResolver().takePersistableUriPermission(
                        videoUri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                );
            } catch (Exception ignored) {
            }

            selectedVideo.setText("Selected: " + videoUri.getLastPathSegment());

            releasePlayer();
            isPrepared = false;
            videoDuration = 0;
            startTime = 0;
            endTime = 0;

            seekBar.setProgress(0);
            durationText.setText("00:00 / 00:00");
            updateTrimText();

            preparePlayer();
        }
    }

    void preparePlayer() {
        if (videoUri == null || videoSurface == null) return;

        releasePlayer();

        try {
            mediaPlayer = new MediaPlayer();
            isPrepared = false;

            mediaPlayer.setDataSource(this, videoUri);
            mediaPlayer.setSurface(videoSurface);

            mediaPlayer.setOnPreparedListener(mp -> {
                isPrepared = true;
                videoDuration = mp.getDuration();
                startTime = 0;
                endTime = videoDuration;

                seekBar.setProgress(0);
                durationText.setText(
                        "00:00 / " + formatTime(videoDuration));

                updateTrimText();
                showMessage("Video ready");
            });

            mediaPlayer.setOnCompletionListener(mp -> {
                isTrimPreview = false;
                mp.pause();
            });

            mediaPlayer.setOnErrorListener((mp, what, extra) -> {
                showMessage("Video playback error. Try another MP4.");
                return true;
            });

            mediaPlayer.prepareAsync();

        } catch (Exception e) {
            showMessage("Unable to load video: " + e.getMessage());
        }
    }

    void togglePlayback() {
        if (!isPrepared || mediaPlayer == null) {
            showMessage("Please select a video first");
            return;
        }

        if (mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
            isTrimPreview = false;
        } else {
            mediaPlayer.start();
        }
    }

    Runnable updateTimeline = new Runnable() {
        @Override
        public void run() {

            if (isPrepared && mediaPlayer != null) {
                try {
                    int position = mediaPlayer.getCurrentPosition();

                    if (isTrimPreview && position >= endTime) {
                        mediaPlayer.pause();
                        mediaPlayer.seekTo(startTime);
                        isTrimPreview = false;
                        position = startTime;
                    }

                    if (!userSeeking) {
                        seekBar.setProgress(
                                videoDuration > 0
                                ? position * 1000 / videoDuration : 0
                        );
                    }

                    durationText.setText(
                            formatTime(position) + " / "
                            + formatTime(videoDuration)
                    );

                } catch (Exception ignored) {
                }
            }

            handler.postDelayed(this, 300);
        }
    };

    void showMessage(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    void releasePlayer() {
        isPrepared = false;

        if (mediaPlayer != null) {
            try {
                mediaPlayer.stop();
            } catch (Exception ignored) {
            }

            mediaPlayer.release();
            mediaPlayer = null;
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
        }
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(updateTimeline);
        releasePlayer();

        if (videoSurface != null) {
            videoSurface.release();
            videoSurface = null;
        }

        super.onDestroy();
    }
        }
