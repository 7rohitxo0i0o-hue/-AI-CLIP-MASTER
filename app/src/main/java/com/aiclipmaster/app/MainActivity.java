
package com.aiclipmaster.app;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.net.Uri;
import android.graphics.Color;
import android.view.Gravity;
import android.widget.*;
import android.database.Cursor;
import android.provider.OpenableColumns;

public class MainActivity extends Activity {

    LinearLayout mainLayout;
    TextView selectedText;
    static final int PICK_VIDEO = 100;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mainLayout = new LinearLayout(this);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setGravity(Gravity.CENTER);
        mainLayout.setPadding(30, 30, 30, 30);
        mainLayout.setBackgroundColor(Color.rgb(18, 18, 28));

        TextView title = new TextView(this);
        title.setText("AI CLIP MASTER");
        title.setTextSize(28);
        title.setTextColor(Color.WHITE);
        title.setGravity(Gravity.CENTER);
        mainLayout.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Your AI Video Editing Studio");
        subtitle.setTextSize(16);
        subtitle.setTextColor(Color.LTGRAY);
        subtitle.setGravity(Gravity.CENTER);
        mainLayout.addView(subtitle);

        Button selectButton = new Button(this);
        selectButton.setText("SELECT VIDEO");
        mainLayout.addView(selectButton);

        selectedText = new TextView(this);
        selectedText.setText("No video selected");
        selectedText.setTextColor(Color.WHITE);
        selectedText.setTextSize(16);
        selectedText.setGravity(Gravity.CENTER);
        selectedText.setPadding(10, 30, 10, 30);
        mainLayout.addView(selectedText);

        selectButton.setOnClickListener(v -> selectVideo());

        setContentView(mainLayout);
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

            String fileName = "Selected Video";

            Cursor cursor = getContentResolver().query(
                    videoUri,
                    null,
                    null,
                    null,
                    null
            );

            if (cursor != null) {
                try {
                    int index = cursor.getColumnIndex(
                            OpenableColumns.DISPLAY_NAME);

                    if (cursor.moveToFirst() && index >= 0) {
                        fileName = cursor.getString(index);
                    }
                } finally {
                    cursor.close();
                }
            }

            selectedText.setText(
                    "Selected Video:\n" + fileName
            );

            Toast.makeText(this,
                    "Video Selected Successfully",
                    Toast.LENGTH_SHORT).show();
        }
    }
}
