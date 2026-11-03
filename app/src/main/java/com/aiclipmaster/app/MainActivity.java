
package com.aiclipmaster.app;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.net.Uri;
import android.graphics.Color;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.*;
import android.database.Cursor;
import android.provider.OpenableColumns;
import android.media.MediaPlayer;
import android.widget.VideoView;

public class MainActivity extends Activity {

    LinearLayout mainLayout;
    TextView selectedText, durationText;
    VideoView videoView;

    static final int PICK_VIDEO = 100;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mainLayout = new LinearLayout(this);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setPadding(25, 25, 25, 25);
        mainLayout.setBackgroundColor(Color.rgb(18, 18, 28));

        ScrollView scroll = new ScrollView(this);
        scroll.addView(mainLayout);
        setContentView(scroll);

        TextView title = new TextView(this);
        title.setText("AI CLIP MASTER");
        title.setTextSize(27);
        title.setTextColor(Color.WHITE);
        title.setGravity(Gravity.CENTER);
        mainLayout.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Video Preview Studio");
        subtitle.setTextColor(Color.LTGRAY);
        subtitle.setGravity(Gravity.CENTER);
        mainLayout.addView(subtitle);

        Button selectButton = new Button(this);
        selectButton.setText("SELECT VIDEO");
        mainLayout.addView(selectButton);

        selectedText = new TextView(this);
        selectedText.setText("No video selected");
        selectedText.setTextColor(Color.WHITE);
        selectedText.setPadding(5, 15, 5, 15);
        mainLayout.addView(selectedText);

        videoView = new VideoView(this);
        videoView.setBackgroundColor(Color.BLACK);

        LinearLayout.LayoutParams videoParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        600
                );

        mainLayout.addView(videoView, videoParams);

        Button playButton = new Button(this);
        playButton.setText("PLAY");
        mainLayout.addView(playButton);

        Button pauseButton = new Button(this);
        pauseButton.setText("PAUSE");
        mainLayout.addView(pauseButton);

        durationText = new TextView(this);
        durationText.setText("Duration: 00:00");
        durationText.setTextColor(Color.WHITE);
        mainLayout.addView(durationText);

        selectButton.setOnClickListener(v -> selectVideo());

        playButton.setOnClickListener(v -> {
            if (videoView.isPlaying()) {
                videoView.start();
            } else {
                videoView.start();
            }
        });

        pauseButton.setOnClickListener(v -> videoView.pause());
    }

    void selectVideo() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("video/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        startActivityForResult(intent, PICK_VIDEO);
    }

    @Override
    protected void onActivityResult(int requestCode,
            int resultCode, Intent data) {

        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == PICK_VIDEO &&
                resultCode == RESULT_OK &&
                data != null && data.getData() != null) {

            Uri videoUri = data.getData();

            selectedText.setText("Selected: " + videoUri.getLastPathSegment());

            videoView.setVideoURI(videoUri);

            videoView.setOnPreparedListener(mp -> {
                int seconds = mp.getDuration() / 1000;
                durationText.setText(
                        "Duration: " + String.format(
                                "%02d:%02d",
                                seconds / 60,
                                seconds % 60
                        )
                );
                videoView.start();
            });

            videoView.setOnErrorListener((mp, what, extra) -> {
                Toast.makeText(this,
                        "Video Preview Error",
                        Toast.LENGTH_SHORT).show();
                return true;
            });
        }
    }
}
