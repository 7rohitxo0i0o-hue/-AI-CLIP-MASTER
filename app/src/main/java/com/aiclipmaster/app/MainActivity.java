package com.aiclipmaster.app;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.net.Uri;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.*;
import android.view.View;

public class MainActivity extends Activity {

    LinearLayout layout;
    TextView selectedVideo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setStatusBarColor(Color.rgb(15,15,25));
        getWindow().setNavigationBarColor(Color.rgb(15,15,25));

        ScrollView scroll = new ScrollView(this);
        layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(22, 28, 22, 28);
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
        select.setText("＋  SELECT VIDEO");
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

        addHeading("VIDEO TOOLS");

        addButton("AI Auto Clips", "Create short clips from videos");
        addButton("AI Captions", "Generate subtitles");
        addButton("Viral Clip Detection", "Find interesting moments");
        addButton("Silence Removal", "Remove silent portions");
        addButton("AI Reframe", "Vertical and horizontal formats");
        addButton("Video Editor", "Edit your selected video");
        addButton("Export Video", "Save your edited video");

        TextView footer = new TextView(this);
        footer.setText("AI CLIP MASTER  •  Version 1.0");
        footer.setTextColor(Color.GRAY);
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(0, 30, 0, 15);
        layout.addView(footer);

        setContentView(scroll);
    }

    void addView(TextView view, int top) {
        LinearLayout.LayoutParams params =
            new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = top;
        layout.addView(view, params);
    }

    void addHeading(String text) {
        TextView heading = new TextView(this);
        heading.setText(text);
        heading.setTextColor(Color.WHITE);
        heading.setTextSize(20);
        heading.setTypeface(null, Typeface.BOLD);
        heading.setPadding(0, 25, 0, 12);
        layout.addView(heading);
    }

    void addButton(String name, String description) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(16, 12, 16, 12);
        card.setBackgroundColor(Color.rgb(35,35,55));

        LinearLayout.LayoutParams params =
            new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, 7, 0, 7);

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
    protected void onActivityResult(int requestCode, int resultCode,
                                    Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == 100 && resultCode == RESULT_OK
                && data != null) {
            Uri videoUri = data.getData();
            selectedVideo.setText("Selected: " + videoUri.getLastPathSegment());
        }
    }
}
