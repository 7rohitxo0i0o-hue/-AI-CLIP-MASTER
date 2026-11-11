
package com.aiclipmaster.app;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.view.Gravity;
import android.webkit.WebView;
import android.webkit.WebResourceRequest;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.webkit.WebViewAssetLoader;
import androidx.webkit.WebViewClientCompat;

import org.json.JSONObject;

public class CaptionsActivity extends Activity {

    private WebView webView;
    private TextView statusText;
    private String videoUriString = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // MainActivity se selected video ka URI lena
        videoUriString = getIntent().getStringExtra("VIDEO_URI");

        if (videoUriString == null) {
            videoUriString = "";
        }

        // Main layout
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(20, 20, 20, 20);
        root.setBackgroundColor(Color.rgb(18, 18, 30));

        // Title
        TextView title = new TextView(this);
        title.setText("AI CLIP MASTER");
        title.setTextSize(23);
        title.setTextColor(Color.WHITE);
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        // Subtitle
        TextView subtitle = new TextView(this);
        subtitle.setText("AUTO CAPTIONS");
        subtitle.setTextSize(18);
        subtitle.setTextColor(Color.WHITE);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setPadding(0, 15, 0, 10);
        root.addView(subtitle);

        // Status
        statusText = new TextView(this);
        statusText.setText(
                videoUriString.isEmpty()
                        ? "Please select a video first."
                        : "Video received. Preparing Shruti..."
        );
        statusText.setTextColor(Color.WHITE);
        statusText.setTextSize(15);
        statusText.setPadding(0, 15, 0, 15);
        root.addView(statusText);

        // WebView
        webView = new WebView(this);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);

        // Load Android assets safely
        WebViewAssetLoader assetLoader =
                new WebViewAssetLoader.Builder()
                        .addPathHandler(
                                "/assets/",
                                new WebViewAssetLoader.AssetsPathHandler(this)
                        )
                        .build();

        webView.setWebViewClient(new WebViewClientCompat() {

            @Override
            public android.webkit.WebResourceResponse shouldInterceptRequest(
                    WebView view,
                    WebResourceRequest request
            ) {
                return assetLoader.shouldInterceptRequest(
                        request.getUrl()
                );
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);

                statusText.setText("Shruti test page loaded.");

                // Send selected video URI to JavaScript page
                String safeUri = JSONObject.quote(videoUriString);

                webView.evaluateJavascript(
                        "if (typeof window.setVideoUri === 'function') {" +
                                "window.setVideoUri(" + safeUri + ");" +
                                "}",
                        null
                );
            }
        });

        root.addView(
                webView,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        0,
                        1
                )
        );

        // Back button
        Button close = new Button(this);
        close.setText("BACK");
        root.addView(close);

        close.setOnClickListener(v -> finish());

        setContentView(root);

        // Open captions.html
        webView.loadUrl(
                "https://appassets.androidplatform.net/assets/captions.html"
        );
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.stopLoading();
            webView.destroy();
            webView = null;
        }

        super.onDestroy();
    }
}
