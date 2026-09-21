package com.local.exchangecalc;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.inputmethod.InputMethodManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.graphics.Color;
import android.util.Base64;

import java.io.ByteArrayInputStream;

public class MainActivity extends Activity {
    private WebView webView;
    private byte[] templateBytes;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        Window window = getWindow();
        window.setStatusBarColor(Color.rgb(8, 88, 255));
        window.setNavigationBarColor(Color.rgb(240, 243, 248));
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        }

        webView = new WebView(this);
        webView.setBackgroundColor(Color.rgb(240, 243, 248));
        webView.setOverScrollMode(View.OVER_SCROLL_NEVER);

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(false);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setTextZoom(100);
        s.setLoadWithOverviewMode(false);
        s.setUseWideViewPort(false);

        webView.addJavascriptInterface(new AndroidBridge(), "Android");
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                if ("app.local".equals(request.getUrl().getHost())
                        && "/template.webp".equals(request.getUrl().getPath())) {
                    return new WebResourceResponse(
                            "image/webp",
                            null,
                            new ByteArrayInputStream(getTemplateBytes())
                    );
                }
                return super.shouldInterceptRequest(view, request);
            }
        });

        setContentView(webView);
        webView.loadUrl("file:///android_asset/index.html");
    }

    private byte[] getTemplateBytes() {
        if (templateBytes == null) {
            String b64 = TemplatePart0.DATA + TemplatePart1.DATA + TemplatePart2.DATA
                    + TemplatePart3.DATA + TemplatePart4.DATA + TemplatePart5.DATA;
            templateBytes = Base64.decode(b64, Base64.DEFAULT);
        }
        return templateBytes;
    }

    private class AndroidBridge {
        @JavascriptInterface
        public String getTemplateBase64() {
            return TemplatePart0.DATA + TemplatePart1.DATA + TemplatePart2.DATA
                    + TemplatePart3.DATA + TemplatePart4.DATA + TemplatePart5.DATA;
        }

        @JavascriptInterface
        public void hideKeyboard() {
            runOnUiThread(() -> {
                View focus = getCurrentFocus();
                if (focus != null) {
                    focus.clearFocus();
                    InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    if (imm != null) imm.hideSoftInputFromWindow(focus.getWindowToken(), 0);
                }
            });
        }
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
