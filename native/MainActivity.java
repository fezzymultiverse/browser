package com.fezzy.festersbrowser;

import android.os.Bundle;
import androidx.activity.OnBackPressedCallback;
import com.getcapacitor.BridgeActivity;
import com.getcapacitor.PluginHandle;

public class MainActivity extends BridgeActivity {
    private long lastBack = 0;

    /** V1.23.7: the http(s) link another app asked Festers to open. The page reads it through FBSys.launchUrl, once. */
    private static String pendingUrl = null;
    static synchronized String takeLink() { String s = pendingUrl; pendingUrl = null; return s; }

    private void grabLink(android.content.Intent it) {
        try {
            if (it == null) return;
            if ((it.getFlags() & android.content.Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) != 0) return;
            if (!android.content.Intent.ACTION_VIEW.equals(it.getAction())) return;
            android.net.Uri d = it.getData();
            if (d == null) return;
            String s = d.toString();
            if (s.startsWith("http://") || s.startsWith("https://")) {
                synchronized (MainActivity.class) { pendingUrl = s; }
                it.setData(null);
                it.setAction(android.content.Intent.ACTION_MAIN);
            }
        } catch (Throwable ignored) { }
    }

    /** Tells the page a link is waiting. If the page is not loaded yet it pulls the link itself when it starts. */
    private void pushLink() {
        try {
            synchronized (MainActivity.class) { if (pendingUrl == null) return; }
            final android.webkit.WebView w = getBridge().getWebView();
            w.post(() -> {
                try { w.evaluateJavascript("(function(){try{if(window.__fbIntent){window.__fbIntent();return true}}catch(e){}return false})()", null); } catch (Throwable ignored) { }
            });
        } catch (Throwable ignored) { }
    }

    @Override
    public void onNewIntent(android.content.Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        grabLink(intent);
        pushLink();
    }

    @Override
    public void onResume() {
        super.onResume();
        pushLink();
    }

    /** V1.23.6: nothing is left to go back to. The first press only warns, the second sends the app to the background (stops accidental exits). */
    private void leaveOrWarn() {
        long now = android.os.SystemClock.elapsedRealtime();
        if (now - lastBack < 2200) { moveTaskToBack(true); return; }
        lastBack = now;
        android.widget.Toast.makeText(this, "Press Back again to leave Festers", android.widget.Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(FBWebPlugin.class);
        registerPlugin(FBCredentialPlugin.class);
        registerPlugin(FBVaultPlugin.class);
        registerPlugin(FBPdfPlugin.class);
        registerPlugin(FBFilesPlugin.class);
        registerPlugin(FBSysPlugin.class);
        super.onCreate(savedInstanceState);
        grabLink(getIntent()); // V1.23.7: a link that started the app
        // Added after Capacitor's own handler, so this one runs first.
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                // 1. A full-screen video: Back leaves full screen and nothing else.
                try {
                    PluginHandle h = getBridge().getPlugin("FBWeb");
                    if (h != null && h.getInstance() instanceof FBWebPlugin && ((FBWebPlugin) h.getInstance()).exitFullscreen()) return;
                } catch (Throwable ignored) { }
                // 2. Everything else is decided by the page: close the topmost thing, go back, or close a tab.
                //    If the page says nothing is left (or cannot answer), send the app to the background.
                //    It is never finished, so the tabs stay loaded and nothing reloads.
                //    V1.21.6: before backgrounding, the open page gets one last chance to go back on its own history (the page script's
                //    own copy of that history can be out of date, e.g. after a redirect or a page that rewrites its address).
                try {
                    getBridge().getWebView().evaluateJavascript(
                        "(function(){try{return window.__fbBack?window.__fbBack():false}catch(e){return false}})()",
                        value -> { if (!"true".equals(value) && !nativeBack()) leaveOrWarn(); });
                } catch (Throwable e) {
                    if (!nativeBack()) leaveOrWarn();
                }
            }
        });
    }

    /** V1.23.2: Conduint's passkey bridge answers here (request code FBWebPlugin.RC_CONDUINT). Everything else goes to Capacitor as before. */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, android.content.Intent data) {
        if (requestCode == FBWebPlugin.RC_CONDUINT) {
            try {
                PluginHandle h = getBridge().getPlugin("FBWeb");
                if (h != null && h.getInstance() instanceof FBWebPlugin) ((FBWebPlugin) h.getInstance()).pkResult(resultCode, data);
            } catch (Throwable ignored) { }
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private boolean nativeBack() {
        try {
            PluginHandle h = getBridge().getPlugin("FBWeb");
            return h != null && h.getInstance() instanceof FBWebPlugin && ((FBWebPlugin) h.getInstance()).goBackActive();
        } catch (Throwable ignored) {
            return false;
        }
    }
}
