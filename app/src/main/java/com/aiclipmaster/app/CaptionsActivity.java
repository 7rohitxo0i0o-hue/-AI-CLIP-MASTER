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

        videoUriString = getIntent().getStringExtra("VIDEO_URI");

        if (videoUriString == null) {
            videoUriString = "";
        }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(12, 12, 12, 12);
        root.setBackgroundColor(Color.rgb(18, 18, 30));

        TextView title = new TextView(this);
        title.setText("AI CLIP MASTER");
        title.setTextSize(22);
        title.setTextColor(Color.WHITE);
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        statusText = new TextView(this);
        statusText.setTextColor(Color.WHITE);
        statusText.setTextSize(14);
        statusText.setPadding(5, 12, 5, 12);

        statusText.setText(
                videoUriString.isEmpty()
                        ? "Please select a video first."
                        : "Loading Shruti test page..."
        );

        root.addView(statusText);

        webView = new WebView(this);

        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);

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

                String safeUri = JSONObject.quote(videoUriString);

                webView.evaluateJavascript(
                        "window.setVideoUri(" + safeUri + ");",
                        null
                );

                statusText.setText(
                        "Captions page loaded. Load Shruti Engine to continue."
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

        Button back = new Button(this);
        back.setText("BACK");
        root.addView(back);

        back.setOnClickListener(v -> finish());

        setContentView(root);

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
