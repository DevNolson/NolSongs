package com.nolson.nolsongs;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {
    private WebView webView;

    private final BroadcastReceiver cmdReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String cmd = intent.getStringExtra("cmd");
            if (webView == null || cmd == null) return;
            if ("toggle".equals(cmd)) webView.evaluateJavascript("document.getElementById('play')?.click()", null);
            else if ("next".equals(cmd)) webView.evaluateJavascript("document.getElementById('next')?.click()", null);
            else if ("prev".equals(cmd)) webView.evaluateJavascript("document.getElementById('prev')?.click()", null);
        }
    };

    @SuppressLint({"SetJavaScriptEnabled", "UnspecifiedRegisterReceiverFlag"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS}, 10);
            }
        }

        IntentFilter filter = new IntentFilter("NOLSONGS_MEDIA_CMD");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(cmdReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(cmdReceiver, filter);
        }

        webView = new WebView(this);
        setContentView(webView);

        WebSettings ws = webView.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setAllowFileAccess(true);
        ws.setAllowFileAccessFromFileURLs(true);
        ws.setAllowUniversalAccessFromFileURLs(true);
        ws.setMediaPlaybackRequiresUserGesture(false);

        webView.addJavascriptInterface(new MediaBridge(), "AndroidMedia");
        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                String bridgeJs = "(function(){"
                        + "function pushMeta(isPlay){"
                        + "  var t=document.getElementById('nowTitle')?.textContent||'NolSongs';"
                        + "  var s=document.getElementById('nowSub')?.textContent||'Nolson';"
                        + "  var art='';"
                        + "  try{var im=document.getElementById('pArt');if(im&&im.complete&&im.naturalWidth>0){"
                        + "    var c=document.createElement('canvas');c.width=c.height=192;"
                        + "    c.getContext('2d').drawImage(im,0,0,192,192);art=c.toDataURL('image/jpeg',0.8);}}catch(e){}"
                        + "  AndroidMedia.update(t,s,isPlay,art);"
                        + "}"
                        + "var a=document.getElementById('audio');"
                        + "if(a){"
                        + "  a.addEventListener('play',()=>pushMeta(true));"
                        + "  a.addEventListener('playing',()=>pushMeta(true));"
                        + "  a.addEventListener('pause',()=>pushMeta(false));"
                        + "  a.addEventListener('loadeddata',()=>pushMeta(!a.paused));"
                        + "}"
                        + "})();";
                view.evaluateJavascript(bridgeJs, null);
            }
        });

        webView.loadUrl("file:///android_asset/index.html");
    }

    public class MediaBridge {
        @JavascriptInterface
        public void update(String title, String artist, boolean playing, String artBase64) {
            Intent i = new Intent(MainActivity.this, MusicService.class);
            i.setAction(MusicService.ACTION_UPDATE);
            i.putExtra("title", title);
            i.putExtra("artist", artist);
            i.putExtra("playing", playing);
            i.putExtra("art", artBase64);
            ContextCompat.startForegroundService(MainActivity.this, i);
        }
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            moveTaskToBack(true);
        }
    }

    @Override
    protected void onDestroy() {
        try { unregisterReceiver(cmdReceiver); } catch (Exception ignored) {}
        super.onDestroy();
    }
}
