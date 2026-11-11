package com.aiclipmaster.app;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.net.Uri;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.media.MediaMetadataRetriever;
import android.view.Gravity;
import android.widget.*;

import android.content.ContentValues;
import android.provider.MediaStore;
import android.os.Build;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Locale;

import com.google.firebase.auth.FirebaseAuth;

import androidx.media3.common.MediaItem;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;
import androidx.media3.transformer.Transformer;
import androidx.media3.transformer.EditedMediaItem;
import androidx.media3.transformer.Composition;
import androidx.media3.transformer.ExportResult;
import androidx.media3.transformer.ExportException;

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
    private LinearLayout bestResults;
    private LinearLayout sceneResults;

    private TextView statusText;
    private SeekBar startSeekBar;
    private SeekBar endSeekBar;

    private final ArrayList<Long> sceneTimes = new ArrayList<>();
    private final ArrayList<long[]> bestClips = new ArrayList<>();

    private int selectedClip = -1;

    private static final int VIDEO_REQUEST_CODE = 100;
    private static final long MIN_CLIP_MS = 3000;
    private static final long MAX_CLIP_MS = 30000;

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

    private void showAuthScreen() {

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(35), dp(24), dp(24));
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(Color.rgb(18, 18, 30));

        TextView title = makeText("AI CLIP MASTER", 28);
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        TextView subtitle = makeText("Login or Create Account", 16);
        subtitle.setGravity(Gravity.CENTER);
        root.addView(subtitle);

        EditText email = new EditText(this);
        email.setHint("Enter Email");
        email.setSingleLine(true);
        email.setInputType(33);
        root.addView(email);

        EditText password = new EditText(this);
        password.setHint("Enter Password");
        password.setSingleLine(true);
        password.setInputType(129);
        root.addView(password);

        Button login = makeButton("LOGIN");
        root.addView(login);

        Button signup = makeButton("CREATE ACCOUNT");
        root.addView(signup);

        login.setOnClickListener(v -> {

            String e = email.getText().toString().trim();
            String p = password.getText().toString();

            if (e.isEmpty() || p.isEmpty()) {
                showMessage("Enter email and password");
                return;
            }

            login.setEnabled(false);

            auth.signInWithEmailAndPassword(e, p)
                    .addOnCompleteListener(task -> {
                        login.setEnabled(true);

                        if (task.isSuccessful()) {
                            startEditor();
                        } else {
                            showMessage("Login failed: "
                                    + task.getException().getMessage());
                        }
                    });
        });

        signup.setOnClickListener(v -> {

            String e = email.getText().toString().trim();
            String p = password.getText().toString();

            if (e.isEmpty() || p.length() < 6) {
                showMessage("Enter email and password (6+ characters)");
                return;
            }

            signup.setEnabled(false);

            auth.createUserWithEmailAndPassword(e, p)
                    .addOnCompleteListener(task -> {
                        signup.setEnabled(true);

                        if (task.isSuccessful()) {
                            startEditor();
                        } else {
                            showMessage("Signup failed: "
                                    + task.getException().getMessage());
                        }
                    });
        });

        setContentView(root);
    }

    private void startEditor() {

        buildInterface();

        player = new ExoPlayer.Builder(this).build();
        playerView.setPlayer(player);

        player.addListener(new androidx.media3.common.Player.Listener() {

            @Override
            public void onPlaybackStateChanged(int state) {
                if (state ==
                        androidx.media3.common.Player.STATE_ENDED) {
                    statusText.setText("Preview complete");
                }
            }
        });
    }

    private void buildInterface() {

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(16), dp(16), dp(16));
        root.setBackgroundColor(Color.rgb(18, 18, 30));

        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);

        TextView title = makeText("AI CLIP MASTER", 26);
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        TextView subtitle = makeText(
                "AI Video Editor & Shorts Maker", 14
        );
        subtitle.setGravity(Gravity.CENTER);
        root.addView(subtitle);

        Button logout = makeButton("LOGOUT");
        root.addView(logout);

        logout.setOnClickListener(v -> {

            releasePlayer();

            auth.signOut();
            videoUri = null;

            showAuthScreen();
        });

        Button select = makeButton("SELECT VIDEO");
        root.addView(select);
        select.setOnClickListener(v -> selectVideo());

        playerView = new PlayerView(this);
        playerView.setLayoutParams(
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dp(220)
                )
        );
        root.addView(playerView);

        statusText = makeText("Select a video to start", 16);
        root.addView(statusText);

        root.addView(makeText("TRIM VIDEO", 20));

        root.addView(makeText("Start Position", 14));
        startSeekBar = new SeekBar(this);
        root.addView(startSeekBar);

        root.addView(makeText("End Position", 14));
        endSeekBar = new SeekBar(this);
        root.addView(endSeekBar);

        Button preview = makeButton("PREVIEW SELECTED PART");
        root.addView(preview);
        preview.setOnClickListener(v -> previewSelectedPart());

        Button export = makeButton("EXPORT VIDEO");
        root.addView(export);
        export.setOnClickListener(v -> exportVideo());

        Button scenes = makeButton("AI SCENE DETECTION");
        root.addView(scenes);
        scenes.setOnClickListener(v -> detectScenes());

        sceneResults = new LinearLayout(this);
        sceneResults.setOrientation(LinearLayout.VERTICAL);
        root.addView(sceneResults);

        Button moments = makeButton("FIND BEST MOMENTS");
        root.addView(moments);
        moments.setOnClickListener(v -> findBestMoments());

        bestResults = new LinearLayout(this);
        bestResults.setOrientation(LinearLayout.VERTICAL);
        root.addView(bestResults);
Button captions = makeButton("AUTO CAPTIONS");
root.addView(captions);

captions.setOnClickListener(v -> {

    if (videoUri == null) {
        showMessage("Please select a video first");
        return;
    }

    Intent intent = new Intent(
            MainActivity.this,
            CaptionsActivity.class
    );

    intent.putExtra("VIDEO_URI", videoUri.toString());

    startActivity(intent);
});
        setContentView(scroll);
    }
    private void selectVideo() {

        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("video/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);

        startActivityForResult(intent, VIDEO_REQUEST_CODE);
    }

    @Override
    protected void onActivityResult(
            int requestCode,
            int resultCode,
            Intent data
    ) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode != VIDEO_REQUEST_CODE
                || resultCode != RESULT_OK
                || data == null
                || data.getData() == null) {
            return;
        }

        videoUri = data.getData();

        try {
            getContentResolver().takePersistableUriPermission(
                    videoUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
            );
        } catch (Exception ignored) {
        }

        loadSelectedVideo();
    }

    private void loadSelectedVideo() {

        MediaMetadataRetriever retriever =
                new MediaMetadataRetriever();

        try {
            retriever.setDataSource(this, videoUri);

            String duration = retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_DURATION
            );

            if (duration == null) {
                throw new Exception("Video duration unavailable");
            }

            durationMs = Long.parseLong(duration);

            if (durationMs <= 0 || durationMs > Integer.MAX_VALUE) {
                throw new Exception("Unsupported video duration");
            }

            clipStartMs = 0;
            clipEndMs = durationMs;

            startSeekBar.setMax((int) durationMs);
            endSeekBar.setMax((int) durationMs);

            startSeekBar.setProgress(0);
            endSeekBar.setProgress((int) durationMs);

            sceneTimes.clear();
            bestClips.clear();
            selectedClip = -1;

            sceneResults.removeAllViews();
            bestResults.removeAllViews();

            player.setMediaItem(MediaItem.fromUri(videoUri));
            player.prepare();

            statusText.setText(
                    "Video loaded: " + formatTime(durationMs)
            );

        } catch (Exception e) {

            showMessage(
                    "Video loading failed: " + e.getMessage()
            );

        } finally {

            try {
                retriever.release();
            } catch (Exception ignored) {
            }
        }
    }

    private void previewSelectedPart() {

        if (videoUri == null) {
            showMessage("Please select a video first");
            return;
        }

        clipStartMs = startSeekBar.getProgress();
        clipEndMs = endSeekBar.getProgress();

        if (clipEndMs <= clipStartMs) {
            showMessage("Invalid video selection");
            return;
        }

        try {

            MediaItem.ClippingConfiguration clipping =
                    new MediaItem.ClippingConfiguration.Builder()
                            .setStartPositionMs(clipStartMs)
                            .setEndPositionMs(clipEndMs)
                            .build();

            MediaItem clippedItem = new MediaItem.Builder()
                    .setUri(videoUri)
                    .setClippingConfiguration(clipping)
                    .build();

            player.setMediaItem(clippedItem);
            player.prepare();
            player.play();

            statusText.setText("Playing selected clip...");

        } catch (Exception e) {

            showMessage(
                    "Preview failed: " + e.getMessage()
            );
        }
    }
    
    private Bitmap getSmallFrame(
            MediaMetadataRetriever retriever,
            long timeMs
    ) {
        Bitmap frame = retriever.getFrameAtTime(
                timeMs * 1000,
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC
        );

        if (frame == null) {
            return null;
        }

        Bitmap small = Bitmap.createScaledBitmap(
                frame, 32, 32, true
        );

        if (small != frame) {
            frame.recycle();
        }

        return small;
    }

    private double frameDifference(Bitmap a, Bitmap b) {

        if (a == null || b == null) {
            return 0;
        }

        long difference = 0;
        int pixels = a.getWidth() * a.getHeight();

        for (int y = 0; y < a.getHeight(); y++) {
            for (int x = 0; x < a.getWidth(); x++) {

                int p1 = a.getPixel(x, y);
                int p2 = b.getPixel(x, y);

                difference += Math.abs(
                        Color.red(p1) - Color.red(p2)
                );

                difference += Math.abs(
                        Color.green(p1) - Color.green(p2)
                );

                difference += Math.abs(
                        Color.blue(p1) - Color.blue(p2)
                );
            }
        }

        return difference / (double) (pixels * 3);
    }

    private void detectScenes() {

        if (videoUri == null) {
            showMessage("Please select a video first");
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

                for (long time = 1000;
                     time < durationMs;
                     time += 1000) {

                    Bitmap current = getSmallFrame(
                            retriever, time
                    );

                    if (current == null) {
                        continue;
                    }

                    if (previous != null
                            && frameDifference(previous, current) > 45) {

                        detected.add(time);
                    }

                    if (previous != null
                            && !previous.isRecycled()) {
                        previous.recycle();
                    }

                    previous = current;
                }

            } catch (Exception e) {

                runOnUiThread(() ->
                        showMessage("Scene detection failed: "
                                + e.getMessage())
                );

            } finally {

                if (previous != null
                        && !previous.isRecycled()) {
                    previous.recycle();
                }

                try {
                    retriever.release();
                } catch (Exception ignored) {
                }
            }

            runOnUiThread(() -> {

                sceneTimes.clear();
                sceneTimes.addAll(detected);

                sceneResults.removeAllViews();

                sceneResults.addView(
                        makeText(
                                "Scene changes detected: "
                                        + sceneTimes.size(),
                                16
                        )
                );

                statusText.setText(
                        "Scene detection complete!"
                );
            });

        }).start();
    }

    private void findBestMoments() {

        if (videoUri == null) {
            showMessage("Please select a video first");
            return;
        }

        bestClips.clear();
        bestResults.removeAllViews();

        if (durationMs < MIN_CLIP_MS) {
            showMessage("Video is too short");
            return;
        }

        ArrayList<Long> points =
                new ArrayList<>(sceneTimes);

        Collections.sort(points);

        if (points.isEmpty()) {
            points.add(durationMs / 2);
        }

        long previousEnd = -1;

        for (int i = 0; i < points.size(); i++) {

            long scene = points.get(i);

            long previousScene =
                    i == 0 ? 0 : points.get(i - 1);

            long nextScene =
                    i == points.size() - 1
                            ? durationMs
                            : points.get(i + 1);

            long start = Math.max(
                    0,
                    Math.max(previousScene, scene - 10000)
            );

            long end = Math.min(
                    durationMs,
                    Math.min(nextScene, scene + 10000)
            );

            if (end - start < MIN_CLIP_MS) {
                start = Math.max(
                        0,
                        scene - MIN_CLIP_MS / 2
                );

                end = Math.min(
                        durationMs,
                        start + MIN_CLIP_MS
                );
            }

            if (end - start > MAX_CLIP_MS) {
                end = start + MAX_CLIP_MS;
            }

            if (end - start < MIN_CLIP_MS) {
                continue;
            }

            // Skip overlapping or duplicate suggestions.
            if (previousEnd >= 0 && start < previousEnd) {
                continue;
            }

            bestClips.add(new long[]{start, end});
            previousEnd = end;
        }

        if (bestClips.isEmpty()) {

            long length = Math.min(
                    MAX_CLIP_MS,
                    durationMs
            );

            long start = Math.max(
                    0,
                    (durationMs - length) / 2
            );

            bestClips.add(new long[]{
                    start,
                    start + length
            });
        }

        bestResults.addView(
                makeText(
                        "Suggested clips: " + bestClips.size(),
                        18
                )
        );

        for (int i = 0; i < bestClips.size(); i++) {

            final int index = i;
            long[] clip = bestClips.get(i);

            Button clipButton = makeButton(
                    "CLIP " + (i + 1)
                            + " | " + formatTime(clip[0])
                            + " - " + formatTime(clip[1])
            );

            bestResults.addView(clipButton);

            clipButton.setOnClickListener(
                    v -> selectBestClip(index)
            );
        }

        statusText.setText(
                "Smart clip suggestions ready"
        );
    }

    private void selectBestClip(int index) {

        if (index < 0 || index >= bestClips.size()) {
            return;
        }

        selectedClip = index;

        long[] clip = bestClips.get(index);

        startSeekBar.setProgress((int) clip[0]);
        endSeekBar.setProgress((int) clip[1]);

        clipStartMs = clip[0];
        clipEndMs = clip[1];

        statusText.setText(
                "Clip " + (index + 1) + " selected"
        );
    }
    
    private void exportVideo() {

        if (videoUri == null) {
            showMessage("Please select a video first");
            return;
        }

        clipStartMs = startSeekBar.getProgress();
        clipEndMs = endSeekBar.getProgress();

        if (clipEndMs <= clipStartMs) {
            showMessage("Invalid video selection");
            return;
        }

        try {

            if (transformer != null) {
                transformer.cancel();
                transformer = null;
            }

            File outputFile = new File(
                    getCacheDir(),
                    "AI_CLIP_" + System.currentTimeMillis() + ".mp4"
            );

            MediaItem.ClippingConfiguration clipping =
                    new MediaItem.ClippingConfiguration.Builder()
                            .setStartPositionMs(clipStartMs)
                            .setEndPositionMs(clipEndMs)
                            .build();

            MediaItem clippedItem = new MediaItem.Builder()
                    .setUri(videoUri)
                    .setClippingConfiguration(clipping)
                    .build();

            EditedMediaItem editedMediaItem =
                    new EditedMediaItem.Builder(clippedItem).build();

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
                                    showMessage(
                                            "Export failed: "
                                                    + exception.getMessage()
                                    )
                            );
                        }
                    })
                    .build();

            statusText.setText("Exporting video...");

            transformer.start(
                    editedMediaItem,
                    outputFile.getAbsolutePath()
            );

        } catch (Exception e) {

            showMessage("Export error: " + e.getMessage());
        }
    }

    private void saveToGallery(File file) {

        Uri galleryUri = null;

        try {

            ContentValues values = new ContentValues();

            values.put(
                    MediaStore.Video.Media.DISPLAY_NAME,
                    "AI_CLIP_" + System.currentTimeMillis() + ".mp4"
            );

            values.put(
                    MediaStore.Video.Media.MIME_TYPE,
                    "video/mp4"
            );

            if (Build.VERSION.SDK_INT >= 29) {

                values.put(
                        MediaStore.Video.Media.RELATIVE_PATH,
                        "Movies/AI CLIP MASTER"
                );

                values.put(
                        MediaStore.Video.Media.IS_PENDING,
                        1
                );
            }

            galleryUri = getContentResolver().insert(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    values
            );

            if (galleryUri == null) {
                throw new Exception("Could not create Gallery file");
            }

            try (
                    InputStream input = new FileInputStream(file);
                    OutputStream output =
                            getContentResolver().openOutputStream(galleryUri)
            ) {

                if (output == null) {
                    throw new Exception("Could not open Gallery file");
                }

                byte[] buffer = new byte[8192];
                int length;

                while ((length = input.read(buffer)) > 0) {
                    output.write(buffer, 0, length);
                }
            }

            if (Build.VERSION.SDK_INT >= 29) {

                ContentValues updateValues = new ContentValues();

                updateValues.put(
                        MediaStore.Video.Media.IS_PENDING,
                        0
                );

                getContentResolver().update(
                        galleryUri,
                        updateValues,
                        null,
                        null
                );
            }

            file.delete();

            runOnUiThread(() -> {

                statusText.setText("Video saved to Gallery!");
                showMessage("Export successful!");
            });

        } catch (Exception e) {

            if (galleryUri != null) {
                getContentResolver().delete(
                        galleryUri,
                        null,
                        null
                );
            }

            runOnUiThread(() ->
                    showMessage(
                            "Gallery save failed: " + e.getMessage()
                    )
            );
        }
    }

    private Button makeButton(String text) {

        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);

        return button;
    }

    private TextView makeText(String text, int size) {

        TextView view = new TextView(this);

        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(Color.WHITE);
        view.setPadding(0, dp(8), 0, dp(8));

        return view;
    }

    private String formatTime(long timeMs) {

        long seconds = timeMs / 1000;
        long minutes = seconds / 60;
        seconds = seconds % 60;

        return String.format(
                Locale.getDefault(),
                "%02d:%02d",
                minutes,
                seconds
        );
    }

    private void showMessage(String message) {

        runOnUiThread(() ->
                Toast.makeText(
                        MainActivity.this,
                        message,
                        Toast.LENGTH_LONG
                ).show()
        );
    }

    private int dp(int value) {

        return (int) (
                value * getResources()
                        .getDisplayMetrics().density
        );
    }

    private void releasePlayer() {

        if (player != null) {
            player.release();
            player = null;
        }
    }

    @Override
    protected void onDestroy() {

        if (transformer != null) {
            transformer.cancel();
            transformer = null;
        }

        releasePlayer();

        super.onDestroy();
    }
}
