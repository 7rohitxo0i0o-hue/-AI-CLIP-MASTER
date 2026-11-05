
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

import com.google.firebase.auth.FirebaseAuth;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Locale;

@UnstableApi
public class MainActivity extends Activity {

    private FirebaseAuth auth;

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

        auth = FirebaseAuth.getInstance();

        if (auth.getCurrentUser() == null) {
            showAuthScreen();
        } else {
            startEditor();
        }
    }

    // LOGIN AND SIGN UP SCREEN
    private void showAuthScreen() {

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(24), dp(40), dp(24), dp(24));
        layout.setBackgroundColor(Color.rgb(18, 18, 18));

        TextView title = makeText("AI CLIP MASTER");
        title.setTextSize(28);
        title.setGravity(17);
        layout.addView(title);

        TextView subtitle = makeText("Login or Create Account");
        subtitle.setGravity(17);
        layout.addView(subtitle);

        EditText emailInput = new EditText(this);
        emailInput.setHint("Email address");
        emailInput.setSingleLine(true);
        emailInput.setTextColor(Color.WHITE);
        emailInput.setHintTextColor(Color.LTGRAY);
        emailInput.setInputType(33);
        layout.addView(emailInput);

        EditText passwordInput = new EditText(this);
        passwordInput.setHint("Password");
        passwordInput.setSingleLine(true);
        passwordInput.setTextColor(Color.WHITE);
        passwordInput.setHintTextColor(Color.LTGRAY);
        passwordInput.setInputType(129);
        layout.addView(passwordInput);

        Button loginButton = makeButton("LOGIN");
        layout.addView(loginButton);

        Button signupButton = makeButton("CREATE ACCOUNT");
        layout.addView(signupButton);

        TextView message = makeText(
                "Use your email and password to continue.");
        layout.addView(message);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(layout);
        setContentView(scroll);

        loginButton.setOnClickListener(v -> {

            String email = emailInput.getText().toString().trim();
            String password = passwordInput.getText().toString();

            if (email.isEmpty() || password.isEmpty()) {
                message.setText("Enter email and password.");
                return;
            }

            loginButton.setEnabled(false);
            message.setText("Logging in...");

            auth.signInWithEmailAndPassword(email, password)
                    .addOnCompleteListener(this, task -> {

                        loginButton.setEnabled(true);

                        if (task.isSuccessful()) {
                            startEditor();
                        } else {
                            message.setText(
                                    "Login failed: " +
                                    (task.getException() != null
                                    ? task.getException().getMessage()
                                    : "Please try again."));
                        }
                    });
        });

        signupButton.setOnClickListener(v -> {

            String email = emailInput.getText().toString().trim();
            String password = passwordInput.getText().toString();

            if (email.isEmpty() || password.isEmpty()) {
                message.setText("Enter email and password.");
                return;
            }

            if (password.length() < 6) {
                message.setText(
                        "Password must be at least 6 characters.");
                return;
            }

            signupButton.setEnabled(false);
            message.setText("Creating account...");

            auth.createUserWithEmailAndPassword(email, password)
                    .addOnCompleteListener(this, task -> {

                        signupButton.setEnabled(true);

                        if (task.isSuccessful()) {
                            startEditor();
                        } else {
                            message.setText(
                                    "Signup failed: " +
                                    (task.getException() != null
                                    ? task.getException().getMessage()
                                    : "Please try again."));
                        }
                    });
        });
    }

    // OPEN VIDEO EDITOR AFTER LOGIN
    private void startEditor() {

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

    // VIDEO EDITOR INTERFACE
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

        Button logoutButton = makeButton("LOGOUT");
        root.addView(logoutButton);

        logoutButton.setOnClickListener(v -> {
            if (player != null) {
                player.release();
                player = null;
            }

            auth.signOut();
            showAuthScreen();
        });

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
    
private void selectVideo() {
    Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
    intent.setType("video/*");
    intent.addCategory(Intent.CATEGORY_OPENABLE);
    startActivityForResult(intent, 100);
}

@Override
protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);

    if (requestCode == 100 && resultCode == RESULT_OK
            && data != null && data.getData() != null) {
        videoUri = data.getData();

        try {
            getContentResolver().takePersistableUriPermission(
                    videoUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
            );
        } catch (Exception ignored) {}

        try {
            MediaMetadataRetriever retriever =
                    new MediaMetadataRetriever();
            retriever.setDataSource(this, videoUri);

            String duration = retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_DURATION
            );

            durationMs = Long.parseLong(duration);
            clipStartMs = 0;
            clipEndMs = durationMs;

            startSeekBar.setMax((int) durationMs);
            endSeekBar.setMax((int) durationMs);
            startSeekBar.setProgress(0);
            endSeekBar.setProgress((int) durationMs);

            player.setMediaItem(
                    androidx.media3.common.MediaItem.fromUri(videoUri)
            );
            player.prepare();

            statusText.setText("Video loaded successfully!");
            retriever.release();

        } catch (Exception e) {
            showMessage("Video load failed: " + e.getMessage());
        }
    }
}

private void previewSelectedPart() {
    if (videoUri == null) {
        showMessage("Please select a video first.");
        return;
    }

    clipStartMs = startSeekBar.getProgress();
    clipEndMs = endSeekBar.getProgress();

    if (clipEndMs <= clipStartMs) {
        showMessage("Please select a valid video range.");
        return;
    }

    player.seekTo(clipStartMs);
    player.play();
}

private Bitmap getSmallFrame(
        MediaMetadataRetriever retriever,
        long timeMs
) {
    Bitmap frame = retriever.getFrameAtTime(
            timeMs * 1000,
            MediaMetadataRetriever.OPTION_CLOSEST_SYNC
    );

    if (frame == null) return null;

    Bitmap small = Bitmap.createScaledBitmap(
            frame, 32, 32, true
    );

    if (small != frame) frame.recycle();

    return small;
}

private double frameDifference(Bitmap a, Bitmap b) {
    if (a == null || b == null) return 0;

    long difference = 0;
    int pixels = a.getWidth() * a.getHeight();

    for (int y = 0; y < a.getHeight(); y++) {
        for (int x = 0; x < a.getWidth(); x++) {
            int p1 = a.getPixel(x, y);
            int p2 = b.getPixel(x, y);

            difference += Math.abs(Color.red(p1) - Color.red(p2));
            difference += Math.abs(Color.green(p1) - Color.green(p2));
            difference += Math.abs(Color.blue(p1) - Color.blue(p2));
        }
    }

    return difference / (double) (pixels * 3);
}

private void detectScenes() {
    if (videoUri == null) {
        showMessage("Please select a video first.");
        return;
    }

    statusText.setText("Detecting scenes...");

    new Thread(() -> {
        ArrayList<Long> detected = new ArrayList<>();

        MediaMetadataRetriever retriever =
                new MediaMetadataRetriever();

        Bitmap previous = null;

        try {
            retriever.setDataSource(this, videoUri);

            for (long time = 1000; time < durationMs; time += 1000) {
                Bitmap current = getSmallFrame(retriever, time);

                if (current == null) continue;

                if (previous != null &&
                        frameDifference(previous, current) > 45) {
                    detected.add(time);
                }

                if (previous != null) previous.recycle();
                previous = current;
            }

            if (previous != null) previous.recycle();

        } catch (Exception e) {
            runOnUiThread(() ->
                    showMessage("Scene detection failed.")
            );
        } finally {
            try {
                retriever.release();
            } catch (Exception ignored) {}
        }

        runOnUiThread(() -> {
            sceneTimes.clear();
            sceneTimes.addAll(detected);

            sceneResults.setText(
                    "Scenes detected: " + sceneTimes.size()
            );

            statusText.setText("Scene detection complete!");
        });
    }).start();
}
    
private void findBestMoments() {
    if (videoUri == null) {
        showMessage("Please select a video first.");
        return;
    }

    bestClips.clear();

    for (long scene : sceneTimes) {
        long start = Math.max(0, scene - 10000);
        long end = Math.min(durationMs, scene + 20000);

        if (end - start < 3000) continue;

        boolean overlaps = false;

        for (long[] clip : bestClips) {
            if (start < clip[1] && end > clip[0]) {
                overlaps = true;
                break;
            }
        }

        if (!overlaps) {
            bestClips.add(new long[]{start, end});
        }
    }

    if (bestClips.isEmpty()) {
        for (long start = 0; start < durationMs; start += 20000) {
            long end = Math.min(durationMs, start + 20000);

            if (end - start >= 3000) {
                bestClips.add(new long[]{start, end});
            }
        }
    }

    bestResults.removeAllViews();

    for (int i = 0; i < bestClips.size(); i++) {
        final int index = i;
        long[] clip = bestClips.get(i);

        Button button = makeButton(
                "Clip " + (i + 1) + "  |  "
                        + formatTime(clip[0]) + " - "
                        + formatTime(clip[1])
        );

        button.setOnClickListener(v -> selectBestClip(index));
        bestResults.addView(button);
    }

    statusText.setText(
            "Suggested clips: " + bestClips.size()
    );
}

private void selectBestClip(int index) {
    if (index < 0 || index >= bestClips.size()) return;

    selectedClip = index;

    long[] clip = bestClips.get(index);

    clipStartMs = clip[0];
    clipEndMs = clip[1];

    startSeekBar.setProgress((int) clipStartMs);
    endSeekBar.setProgress((int) clipEndMs);

    previewSelectedPart();
}

private void exportVideo() {
    if (videoUri == null) {
        showMessage("Please select a video first.");
        return;
    }

    clipStartMs = startSeekBar.getProgress();
    clipEndMs = endSeekBar.getProgress();

    if (clipEndMs <= clipStartMs) {
        showMessage("Please select a valid video range.");
        return;
    }

    try {
        File outputDir = new File(
                getExternalFilesDir(null), "Movies"
        );

        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }

        File outputFile = new File(
                outputDir,
                "AI_Clip_" + System.currentTimeMillis() + ".mp4"
        );

        androidx.media3.common.MediaItem input =
                new androidx.media3.common.MediaItem.Builder()
                        .setUri(videoUri)
                        .setClippingConfiguration(
                                new androidx.media3.common.MediaItem
                                        .ClippingConfiguration.Builder()
                                        .setStartPositionMs(clipStartMs)
                                        .setEndPositionMs(clipEndMs)
                                        .build()
                        )
                        .build();

        EditedMediaItem editedItem =
                new EditedMediaItem.Builder(input).build();

        transformer = new Transformer.Builder(this)
                .addListener(new Transformer.Listener() {
                    @Override
                    public void onCompleted(
                            Composition composition,
                            ExportResult exportResult
                    ) {
                        saveToGallery(outputFile);
                    }

                    @Override
                    public void onError(
                            Composition composition,
                            ExportResult exportResult,
                            ExportException exception
                    ) {
                        runOnUiThread(() ->
                                showMessage("Export failed: "
                                        + exception.getMessage())
                        );
                    }
                })
                .build();

        statusText.setText("Exporting video...");

        transformer.start(editedItem, outputFile.getAbsolutePath());

    } catch (Exception e) {
        showMessage("Export error: " + e.getMessage());
    }
}

private void saveToGallery(File sourceFile) {
    try {
        android.content.ContentValues values =
                new android.content.ContentValues();

        values.put(
                android.provider.MediaStore.Video.Media.DISPLAY_NAME,
                sourceFile.getName()
        );

        values.put(
                android.provider.MediaStore.Video.Media.MIME_TYPE,
                "video/mp4"
        );

        if (android.os.Build.VERSION.SDK_INT >= 29) {
            values.put(
                    android.provider.MediaStore.Video.Media.RELATIVE_PATH,
                    "Movies/AI Clip Master"
            );

            values.put(
                    android.provider.MediaStore.Video.Media.IS_PENDING,
                    1
            );
        }

        android.net.Uri collection =
                android.provider.MediaStore.Video.Media
                        .getContentUri(
                                android.provider.MediaStore.VOLUME_EXTERNAL_PRIMARY
                        );

        android.net.Uri savedUri = getContentResolver().insert(
                collection, values
        );

        if (savedUri == null) {
            showMessage("Could not save video.");
            return;
        }

        try (
                java.io.InputStream input =
                        new java.io.FileInputStream(sourceFile);
                java.io.OutputStream output =
                        getContentResolver().openOutputStream(savedUri)
        ) {
            if (output == null) {
                throw new java.io.IOException("Output stream unavailable");
            }

            byte[] buffer = new byte[8192];
            int length;

            while ((length = input.read(buffer)) > 0) {
                output.write(buffer, 0, length);
            }
        }

        if (android.os.Build.VERSION.SDK_INT >= 29) {
            android.content.ContentValues completed =
                    new android.content.ContentValues();

            completed.put(
                    android.provider.MediaStore.Video.Media.IS_PENDING,
                    0
            );

            getContentResolver().update(
                    savedUri, completed, null, null
            );
        }

        runOnUiThread(() -> {
            statusText.setText("Video saved to Gallery!");
            showMessage("Export successful!");
        });

    } catch (Exception e) {
        showMessage("Gallery save failed: " + e.getMessage());
    }
}

private Button makeButton(String text) {
    Button button = new Button(this);
    button.setText(text);
    return button;
}

private TextView makeText(String text, int size) {
    TextView view = new TextView(this);
    view.setText(text);
    view.setTextSize(size);
    view.setTextColor(Color.WHITE);
    return view;
}

private String formatTime(long milliseconds) {
    long seconds = milliseconds / 1000;
    return String.format(
            Locale.getDefault(),
            "%02d:%02d",
            seconds / 60,
            seconds % 60
    );
}

private void showMessage(String message) {
    runOnUiThread(() ->
            android.widget.Toast.makeText(
                    this,
                    message,
                    android.widget.Toast.LENGTH_SHORT
            ).show()
    );
}

private int dp(int value) {
    return (int) (
            value * getResources().getDisplayMetrics().density
    );
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
    
