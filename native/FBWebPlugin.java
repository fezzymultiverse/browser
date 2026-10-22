package com.fezzy.festersbrowser;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.RectF;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.provider.MediaStore;
import android.util.Base64;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.MimeTypeMap;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * FBWeb: one native WebView per browser tab, drawn over the Capacitor WebView
 * in the rectangle the JS side reports (the #views area).
 */
@CapacitorPlugin(name = "FBWeb")
public class FBWebPlugin extends Plugin {
    private static final String PRIVATE_PROFILE = "fb_private";

    private FrameLayout holder;
    private final Map<Integer, WebView> views = new HashMap<>();
    private final Map<Integer, Boolean> priv = new HashMap<>();
    private final Map<Integer, Boolean> desktop = new HashMap<>();
    private final Map<Integer, Boolean> httpsOnly = new HashMap<>();
    private final Map<Integer, Boolean> shieldOn = new HashMap<>();
    private final Map<Integer, Integer> shieldCount = new HashMap<>();
    private final Map<Integer, Long> shieldLastEmit = new HashMap<>();
    private final Map<Integer, String> shieldLastHost = new HashMap<>();
    private final Map<Integer, Map<String, Integer>> shieldHosts = new HashMap<>();

    /* Shields: well-known tracker, ad and fingerprinting hosts. Matched on the host and every parent domain. */
    private static final java.util.Set<String> TRACKERS = new java.util.HashSet<>(java.util.Arrays.asList(
        "doubleclick.net","googlesyndication.com","googleadservices.com","google-analytics.com","googletagmanager.com",
        "googletagservices.com","adservice.google.com","pagead2.googlesyndication.com","app-measurement.com","analytics.google.com",
        "facebook.net","connect.facebook.net","graph.facebook.com","pixel.facebook.com","fbcdn-analytics.net",
        "scorecardresearch.com","quantserve.com","quantcount.com","hotjar.com","hotjar.io","fullstory.com","mouseflow.com",
        "crazyegg.com","clarity.ms","segment.io","segment.com","mixpanel.com","amplitude.com","heap.io","heapanalytics.com",
        "optimizely.com","newrelic.com","nr-data.net","bugsnag.com","sentry-cdn.com","branch.io","adjust.com","appsflyer.com",
        "kochava.com","singular.net","criteo.com","criteo.net","taboola.com","outbrain.com","adnxs.com","rubiconproject.com",
        "pubmatic.com","openx.net","casalemedia.com","indexww.com","smartadserver.com","adsrvr.org","bidswitch.net","contextweb.com",
        "moatads.com","adsafeprotected.com","doubleverify.com","amazon-adsystem.com","media.net","yieldmo.com","sharethrough.com",
        "3lift.com","teads.tv","advertising.com","adform.net","serving-sys.com","everesttech.net","demdex.net","omtrdc.net",
        "2o7.net","bluekai.com","krxd.net","exelator.com","rlcdn.com","liveramp.com","tapad.com","agkn.com","addthis.com",
        "sharethis.com","disqus.com","chartbeat.com","chartbeat.net","parsely.com","statcounter.com","histats.com","yandex.ru",
        "mc.yandex.ru","top.mail.ru","counter.yadro.ru","ads-twitter.com","analytics.twitter.com","static.ads-twitter.com",
        "px.ads.linkedin.com","snap.licdn.com","analytics.tiktok.com","ads.tiktok.com","business-api.tiktok.com","ct.pinterest.com","analytics.pinterest.com","bat.bing.com","clarity.ms","ads.yahoo.com","analytics.yahoo.com","gemini.yahoo.com",
        "adtech.com","adtechus.com","zedo.com","revcontent.com","mgid.com","popads.net","popcash.net","propellerads.com","exoclick.com",
        "juicyads.com","trafficjunky.net","adcash.com","hilltopads.net","clickadu.com","pushwoosh.com",
        "hsadspixel.net","mktoresp.com",
        "quantserve.com",
        "adroll.com","perfectaudience.com","rtbhouse.com","mathtag.com","turn.com","tremorhub.com","spotxchange.com","springserve.com",
        "lijit.com","sovrn.com","33across.com","gumgum.com","connectad.io","adscale.de","admixer.net","inmobi.com","mopub.com",
        "unityads.unity3d.com","applovin.com","vungle.com","chartboost.com","ironsrc.com","supersonicads.com","startapp.com","flurry.com"
    ));

    /* V1.18.1: browser-check and captcha hosts. Never blocked by Shields; a stuck "Just a moment" page needs them. */
    private static final java.util.Set<String> CHALLENGE = new java.util.HashSet<>(java.util.Arrays.asList(
        "challenges.cloudflare.com","cloudflare.com","cdnjs.cloudflare.com","cloudflareinsights.com","hcaptcha.com","recaptcha.net",
        "gstatic.com","arkoselabs.com","funcaptcha.com","geetest.com","captcha-delivery.com","datadome.co"
    ));

    /** V1.22.2: sites that must never be thinned out by Shields or Strict cookies (sign-in flows and app shells that load blank otherwise). */
    private static final String[] SITE_FIX = {
        "google.com", "google.co.za", "gstatic.com", "googleapis.com", "googleusercontent.com", "youtube.com", "ytimg.com",
        "gitlab.com", "gitlab-static.net", "github.com", "githubassets.com", "githubusercontent.com"
    };
    private static boolean siteFix(String host) {
        if (host == null) return false;
        String h = host.toLowerCase();
        for (String s : SITE_FIX) if (h.equals(s) || h.endsWith("." + s)) return true;
        return false;
    }
    private final Map<Integer, Integer> netRetry = new java.util.concurrent.ConcurrentHashMap<>();
    /** V1.22.6: own forward list per tab. WebView's canGoForward can read false after a page rebuild; this keeps Forward working. */
    private final Map<Integer, java.util.concurrent.ConcurrentLinkedDeque<String>> fwdStack = new java.util.concurrent.ConcurrentHashMap<>();
    /** V1.23.3: Back/Forward direction is reported by the native side with the page event. The old 2.5 s timer in the page could expire before a slow page
        committed, so the page took a Back for a brand new page and threw away the Forward list. Also covers the hardware Back fallback, which never told the page. */
    private final Map<Integer, String> navDir = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<Integer, Long> navDirAt = new java.util.concurrent.ConcurrentHashMap<>();
    private void markDir(int id, String d) { navDir.put(id, d); navDirAt.put(id, System.currentTimeMillis()); }
    private String takeDir(int id) {
        String d = navDir.remove(id); Long t = navDirAt.remove(id);
        return (d != null && t != null && System.currentTimeMillis() - t < 45000L) ? d : null;
    }
    private void fwdPush(int id, String u) {
        if (u == null || !u.startsWith("http")) return;
        java.util.concurrent.ConcurrentLinkedDeque<String> d = fwdStack.get(id);
        if (d == null) { d = new java.util.concurrent.ConcurrentLinkedDeque<>(); fwdStack.put(id, d); }
        d.push(u);
        while (d.size() > 40) d.removeLast();
    }
    /** V1.22.6: hosts where you chose Continue anyway after an untrusted-chain certificate error (never for expired, wrong-name or other errors). */
    /** V1.23.0: hosts that have no working HTTPS; HTTPS-only leaves them on http. Sent from the page on every open. */
    static final java.util.Set<String> httpHosts = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final Map<Integer, String[]> upgraded = new java.util.concurrent.ConcurrentHashMap<>();
    static final java.util.Set<String> sslAllow = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static boolean sslAllowed(String host) {
        if (host == null) return false;
        String h = host.toLowerCase();
        for (String a : sslAllow) if (h.equals(a) || h.endsWith("." + a)) return true;
        return false;
    }
    private static int popupSeq = 1000000;
    @PluginMethod
    public void sslAllow(final PluginCall call) {
        final String h = call.getString("host", "");
        final boolean on = call.getBoolean("allow", true);
        if (h != null && !h.isEmpty()) { if (on) sslAllow.add(h.toLowerCase()); else sslAllow.remove(h.toLowerCase()); }
        final int id = call.getInt("id", -1);
        ui(() -> { try { WebView w = views.get(id); if (on && w != null) w.loadUrl("https://" + h + "/"); } catch (Throwable ignored) { } call.resolve(); });
    }
    private final Map<Integer, Boolean> strictOf = new java.util.concurrent.ConcurrentHashMap<>();
    /** V1.23.1: Cookies setting from the page: "all", "third" (block third-party) or "none" (block all). Sent on every open(). */
    private static volatile String cookieMode = "all";

    private boolean thirdOk(boolean isPriv, boolean strict) {
        return "all".equals(cookieMode) && !(isPriv || strict);
    }

    private void applyCookieMode(String m) {
        if (m == null || !(m.equals("all") || m.equals("third") || m.equals("none"))) return;
        cookieMode = m;
        try { CookieManager.getInstance().setAcceptCookie(!"none".equals(m)); } catch (Throwable ignored) { }
    }

    /** V1.22.4: NoScript policy per tab, checked on every main-frame navigation. Before, JavaScript was switched once when a tab opened,
     *  so a tab that started on a blocked site stayed script-less on every later site (pages then served their no-JavaScript versions). */
    private final Map<Integer, Boolean> noscriptOf = new java.util.concurrent.ConcurrentHashMap<>();
    private volatile java.util.Set<String> scriptHosts = new java.util.HashSet<>();
    private void readPolicy(PluginCall call, int id) {
        try {
            if (call.getData().has("noscript")) noscriptOf.put(id, Boolean.valueOf(call.getBoolean("noscript", false)));
            org.json.JSONArray a = call.getArray("allow");
            if (a != null) {
                java.util.Set<String> n = new java.util.HashSet<>();
                for (int i = 0; i < a.length(); i++) { String h = a.optString(i, ""); if (!h.isEmpty()) n.add(h.toLowerCase()); }
                scriptHosts = n;
            }
        } catch (Throwable ignored) { }
    }
    private boolean scriptAllowedHost(String host) {
        if (host == null) return false;
        String h = host.toLowerCase();
        for (String a : scriptHosts) if (h.equals(a) || h.endsWith("." + a)) return true;
        return false;
    }
    private void applyJsPolicy(WebView v, int id, String host) {
        try {
            if (!noscriptOf.containsKey(id)) return;
            boolean want = !(Boolean.TRUE.equals(noscriptOf.get(id)) && !scriptAllowedHost(host));
            if (v.getSettings().getJavaScriptEnabled() != want) v.getSettings().setJavaScriptEnabled(want);
        } catch (Throwable ignored) { }
    }

    /** V1.22.2: net::ERR_NETWORK_CHANGED (Wi-Fi to 4G flip) and friends are transient. Retry up to 3 times instead of showing an error page. */
    private boolean tryRetry(final WebView v, final int id, final String url, String desc) {
        if (url == null || !(url.startsWith("http://") || url.startsWith("https://")) || desc == null) return false;
        boolean transientErr = desc.contains("ERR_NETWORK_CHANGED") || desc.contains("ERR_CONNECTION_RESET") || desc.contains("ERR_CONNECTION_CLOSED")
            || desc.contains("ERR_QUIC") || desc.contains("ERR_NETWORK_IO_SUSPENDED") || desc.contains("ERR_INTERNET_DISCONNECTED") || desc.contains("ERR_CONNECTION_ABORTED");
        if (!transientErr) return false;
        Integer c = netRetry.get(id);
        int n = c == null ? 0 : c.intValue();
        if (n >= 3) return false;
        netRetry.put(id, Integer.valueOf(n + 1));
        v.postDelayed(new Runnable() {
            @Override public void run() {
                try { if (views.get(id) == v) v.loadUrl(url); } catch (Throwable ignored) { }
            }
        }, 700L * (n + 1));
        return true;
    }

    /** V1.22.2: third-party cookies stay on for site-fix hosts even in Strict, and go back to the tab's own setting when you leave them. */
    private void siteCookies(WebView v, int id, String url) {
        try {
            if (Boolean.TRUE.equals(priv.get(id))) return;
            String h = Uri.parse(url).getHost();
            boolean fix = siteFix(h);
            boolean strict = Boolean.TRUE.equals(strictOf.get(id));
            CookieManager.getInstance().setAcceptThirdPartyCookies(v, "all".equals(cookieMode) && (fix || !strict));
        } catch (Throwable ignored) { }
    }

    private boolean isChallenge(String host) {
        if (host == null) return false;
        String h = host.toLowerCase();
        while (true) {
            if (CHALLENGE.contains(h)) return true;
            int i = h.indexOf('.');
            if (i < 0 || h.indexOf('.', i + 1) < 0) return false;
            h = h.substring(i + 1);
        }
    }

    /** V1.21.6: main-frame host per tab, written on the page's network thread, read by the shield. */
    private final Map<Integer, String> mainHost = new java.util.concurrent.ConcurrentHashMap<>();
    /** V1.22.7: last main-frame request URL per tab, so a certificate error on a sub-resource is not mistaken for the page itself. */
    private final Map<Integer, String> mainUrl = new java.util.concurrent.ConcurrentHashMap<>();
    private static String noFrag(String u) { if (u == null) return ""; int i = u.indexOf('#'); String s = i >= 0 ? u.substring(0, i) : u; return s.endsWith("/") ? s.substring(0, s.length() - 1) : s; }

    /** True when both hosts share their last two labels (or one is a subdomain of the other). Good enough to tell "the site itself" from a third party. */
    private static boolean sameSite(String a, String b) {
        if (a == null || b == null) return false;
        a = a.toLowerCase(); b = b.toLowerCase();
        if (a.equals(b) || a.endsWith("." + b) || b.endsWith("." + a)) return true;
        String[] pa = a.split("\\."), pb = b.split("\\.");
        if (pa.length < 2 || pb.length < 2) return false;
        return pa[pa.length - 1].equals(pb[pb.length - 1]) && pa[pa.length - 2].equals(pb[pb.length - 2]);
    }

    private boolean isTracker(String host) {
        if (host == null) return false;
        if (isChallenge(host)) return false;
        String h = host.toLowerCase();
        while (true) {
            if (TRACKERS.contains(h)) return true;
            int i = h.indexOf('.');
            if (i < 0 || h.indexOf('.', i + 1) < 0) return false;
            h = h.substring(i + 1);
        }
    }

    private void shieldHit(int id, String host) {
        int n;
        synchronized (shieldCount) {
            Integer c = shieldCount.get(id);
            n = (c == null ? 0 : c) + 1;
            shieldCount.put(id, n);
            shieldLastHost.put(id, host);
            Map<String, Integer> hm = shieldHosts.get(id);
            if (hm == null) { hm = new HashMap<>(); shieldHosts.put(id, hm); }
            String key = host == null ? "?" : host;
            Integer hc = hm.get(key);
            hm.put(key, (hc == null ? 0 : hc) + 1);
        }
        long now = android.os.SystemClock.uptimeMillis();
        Long last = shieldLastEmit.get(id);
        if (last == null || now - last > 200) {
            shieldLastEmit.put(id, now);
            shieldEmit(id);
        }
    }

    private void shieldEmit(int id) {
        try {
            JSObject o = new JSObject();
            Integer c = shieldCount.get(id);
            o.put("id", id);
            o.put("count", c == null ? 0 : c);
            o.put("host", shieldLastHost.get(id));
            JSObject hs = new JSObject();
            synchronized (shieldCount) {
                Map<String, Integer> hm = shieldHosts.get(id);
                if (hm != null) {
                    java.util.List<Map.Entry<String, Integer>> es = new java.util.ArrayList<>(hm.entrySet());
                    java.util.Collections.sort(es, (x, y) -> y.getValue() - x.getValue());
                    for (int i = 0; i < es.size() && i < 12; i++) hs.put(es.get(i).getKey(), es.get(i).getValue());
                }
            }
            o.put("hosts", hs);
            emit("shield", o);
        } catch (Throwable ignored) { }
    }
    private final Map<Integer, Boolean> isolatedMap = new HashMap<>();
    private int active = -1;
    private boolean profileInUse = false;

    private ActivityResultLauncher<Intent> chooserLauncher;
    private ValueCallback<Uri[]> chooserCallback;

    private ConnectivityManager.NetworkCallback vpnCb;
    private View customView;
    private WebChromeClient.CustomViewCallback customCb;

    /** V1.23.6: the page's "Wi-Fi only downloads" setting, pushed by dlConfig. */
    static volatile boolean S_WIFI_ONLY = false;

    @PluginMethod
    public void dlConfig(PluginCall call) {
        S_WIFI_ONLY = call.getBoolean("wifiOnly", false);
        call.resolve();
    }

    @Override
    public void load() {
        try {
            FBDownloader.get(getContext()).setSink((o, kind) -> {
                emit("dlprogress", o);
                if (o.optBoolean("quiet") && "start".equals(kind)) return;
                JSObject e = new JSObject();
                e.put("id", o.optLong("id")); e.put("url", o.optString("url")); e.put("name", o.optString("name"));
                if ("start".equals(kind)) { e.put("ok", true); emit("download", e); }
                else if ("done".equals(kind)) { e.put("ok", true); e.put("done", true); emit("download", e); }
                else if ("fail".equals(kind)) { e.put("ok", false); e.put("desc", "Download failed: " + o.optString("error")); emit("download", e); }
            });
        } catch (Throwable ignored) { }
        try {
            chooserLauncher = getActivity().registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(), result -> {
                    if (chooserCallback == null) return;
                    Uri[] r = WebChromeClient.FileChooserParams.parseResult(result.getResultCode(), result.getData());
                    chooserCallback.onReceiveValue(r);
                    chooserCallback = null;
                });
        } catch (Throwable ignored) { chooserLauncher = null; }
        try {
            ConnectivityManager cm = (ConnectivityManager) getContext().getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkRequest rq = new NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_VPN)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                .build();
            vpnCb = new ConnectivityManager.NetworkCallback() {
                @Override public void onAvailable(Network n) { pushVpn(); }
                @Override public void onLost(Network n) { pushVpn(); }
            };
            cm.registerNetworkCallback(rq, vpnCb);
        } catch (Throwable ignored) { vpnCb = null; }
    }

    /** true / false, or null when Android refuses (missing ACCESS_NETWORK_STATE). */
    private Boolean vpnNow() {
        try {
            ConnectivityManager cm = (ConnectivityManager) getContext().getSystemService(Context.CONNECTIVITY_SERVICE);
            for (Network n : cm.getAllNetworks()) {
                NetworkCapabilities nc = cm.getNetworkCapabilities(n);
                if (nc != null && nc.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return true;
            }
            return false;
        } catch (Throwable t) { return null; }
    }

    private void pushVpn() {
        // let Android finish updating its network list before reading it
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            Boolean a = vpnNow();
            JSObject o = new JSObject();
            o.put("supported", a != null);
            o.put("active", a != null && a);
            emit("vpn", o);
        }, 400);
    }

    @PluginMethod
    public void vpnStatus(final PluginCall call) {
        Boolean a = vpnNow();
        JSObject o = new JSObject();
        o.put("supported", a != null);
        o.put("active", a != null && a);
        call.resolve(o);
    }

    /* ---------- helpers ---------- */

    /* ---------- V1.14.4: site permission prompts ---------- */
    private boolean osHas(String p) { try { return getContext().checkSelfPermission(p) == android.content.pm.PackageManager.PERMISSION_GRANTED; } catch (Throwable t) { return false; } }

    private void sitePermission(final android.webkit.PermissionRequest req) {
        try {
            final java.util.ArrayList<String> grant = new java.util.ArrayList<>();
            final java.util.ArrayList<String> what = new java.util.ArrayList<>();
            final java.util.ArrayList<String> missing = new java.util.ArrayList<>();
            for (String r : req.getResources()) {
                if (android.webkit.PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(r)) { grant.add(r); what.add("camera"); if (!osHas(android.Manifest.permission.CAMERA)) missing.add("Camera"); }
                else if (android.webkit.PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(r)) { grant.add(r); what.add("microphone"); if (!osHas(android.Manifest.permission.RECORD_AUDIO)) missing.add("Microphone"); }
            }
            boolean drm = false;
            for (String r : req.getResources()) if (android.webkit.PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID.equals(r)) drm = true;
            if (grant.isEmpty()) { // V1.21.5: protected media (Widevine) lets Spotify / Netflix style players start; as in WebSheet it is granted, camera and mic still ask
                try { if (drm) req.grant(new String[]{android.webkit.PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID}); else req.deny(); } catch (Throwable ignored) { }
                return;
            }
            String host = (req.getOrigin() == null || req.getOrigin().getHost() == null) ? "This site" : req.getOrigin().getHost();
            if (!missing.isEmpty()) {
                req.deny();
                new android.app.AlertDialog.Builder(getActivity()).setTitle(android.text.TextUtils.join(" and ", missing) + " is off for Festers")
                    .setMessage(host + " asked for your " + android.text.TextUtils.join(" and ", what) + ". Open Settings > Android Permissions in Festers, allow it there, then try the site again.")
                    .setPositiveButton("OK", null).show();
                return;
            }
            final boolean[] answered = { false };
            new android.app.AlertDialog.Builder(getActivity()).setTitle("Allow " + host + "?")
                .setMessage("This site wants to use your " + android.text.TextUtils.join(" and ", what) + ".")
                .setPositiveButton("Allow", (d, w) -> { answered[0] = true; try { req.grant(grant.toArray(new String[0])); } catch (Throwable ignored) { } })
                .setNegativeButton("Block", (d, w) -> { answered[0] = true; try { req.deny(); } catch (Throwable ignored) { } })
                .setOnDismissListener(d -> { if (!answered[0]) { try { req.deny(); } catch (Throwable ignored) { } } })
                .show();
        } catch (Throwable t) { try { req.deny(); } catch (Throwable ignored) { } }
    }

    private void siteGeo(final String origin, final android.webkit.GeolocationPermissions.Callback cb) {
        try {
            if (!(osHas(android.Manifest.permission.ACCESS_FINE_LOCATION) || osHas(android.Manifest.permission.ACCESS_COARSE_LOCATION))) {
                cb.invoke(origin, false, false);
                new android.app.AlertDialog.Builder(getActivity()).setTitle("Location is off for Festers")
                    .setMessage("A site asked for your location. Open Settings > Android Permissions in Festers, allow Location there, then try the site again.")
                    .setPositiveButton("OK", null).show();
                return;
            }
            final boolean[] answered = { false };
            new android.app.AlertDialog.Builder(getActivity()).setTitle("Allow location?")
                .setMessage(origin + " wants to know where you are.")
                .setPositiveButton("Allow", (d, w) -> { answered[0] = true; cb.invoke(origin, true, false); })
                .setNegativeButton("Block", (d, w) -> { answered[0] = true; cb.invoke(origin, false, false); })
                .setOnDismissListener(d -> { if (!answered[0]) cb.invoke(origin, false, false); })
                .show();
        } catch (Throwable t) { try { cb.invoke(origin, false, false); } catch (Throwable ignored) { } }
    }

    private void ui(Runnable r) { getActivity().runOnUiThread(r); }

    private void ensureHolder() {
        if (holder != null) return;
        WebView bridgeWv = getBridge().getWebView();
        ViewGroup parent = (ViewGroup) bridgeWv.getParent();
        holder = new FrameLayout(getContext());
        holder.setVisibility(View.GONE);
        holder.setBackgroundColor(Color.WHITE); // V1.21.7: was #0c0809 (near-black); that dark holder flashed behind the page every
        // time the native view hid and reshowed (address-bar suggestions open/close on every keystroke, tab switches). White matches
        // the page canvas colour set below, so a hide/show cycle shows no colour change at all instead of a dark flash.
        parent.addView(holder, new ViewGroup.LayoutParams(1, 1));
    }

    /** V1.22.2: same string real Chrome for Android sends (reduced UA), exactly like Prodigy's WebSheet where Google, GitLab and the rest load.
     *  The old code only trimmed "; wv" and "Version/4.0", so sites still saw the device Build string and the full WebView version, which real Chrome never sends. */
    private static String chromeMajor(String ua) {
        try {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("Chrome/(\\d+)").matcher(ua == null ? "" : ua);
            if (m.find()) return m.group(1);
        } catch (Throwable ignored) { }
        return "126";
    }
    private String mobileUa(String ua) {
        return "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/" + chromeMajor(ua) + ".0.0.0 Mobile Safari/537.36";
    }
    private String desktopUa(String ua) {
        return "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/" + chromeMajor(ua) + ".0.0.0 Safari/537.36";
    }

    private boolean useProfile(WebView w) {
        try {
            Class<?> feat = Class.forName("androidx.webkit.WebViewFeature");
            boolean ok = (Boolean) feat.getMethod("isFeatureSupported", String.class).invoke(null, "MULTI_PROFILE");
            if (!ok) return false;
            Class<?> store = Class.forName("androidx.webkit.ProfileStore");
            Object inst = store.getMethod("getInstance").invoke(null);
            store.getMethod("getOrCreateProfile", String.class).invoke(inst, PRIVATE_PROFILE);
            Class<?> compat = Class.forName("androidx.webkit.WebViewCompat");
            compat.getMethod("setProfile", WebView.class, String.class).invoke(null, w, PRIVATE_PROFILE);
            return true;
        } catch (Throwable t) { return false; }
    }

    private void dropProfile() {
        try {
            Class<?> store = Class.forName("androidx.webkit.ProfileStore");
            Object inst = store.getMethod("getInstance").invoke(null);
            store.getMethod("deleteProfile", String.class).invoke(inst, PRIVATE_PROFILE);
        } catch (Throwable ignored) { }
        profileInUse = false;
    }

    private void applySettings(WebView w, int id, boolean js, double zoom, boolean desk) {
        WebSettings s = w.getSettings();
        s.setJavaScriptEnabled(js);
        s.setTextZoom((int) Math.round(zoom * 100));
        String base = (String) w.getTag();
        if (base == null) { base = s.getUserAgentString(); w.setTag(base); }
        s.setUserAgentString(desk ? desktopUa(base) : mobileUa(base));
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(desk);
    }

    private void emit(String name, JSObject o) { notifyListeners(name, o); }

    /** V1.23.5: the page view is a native layer above the shell, so the shell's own toast is hidden behind it. This one is drawn by Android itself. */
    @PluginMethod
    public void nativeToast(PluginCall call) {
        final String t = call.getString("text", "");
        if (!t.isEmpty() && getActivity() != null) getActivity().runOnUiThread(() -> { try { Toast.makeText(getContext(), t, Toast.LENGTH_SHORT).show(); } catch (Throwable ignored) { } });
        call.resolve();
    }

    private int idOf(WebView w) {
        for (Map.Entry<Integer, WebView> e : views.entrySet()) if (e.getValue() == w) return e.getKey();
        return -1;
    }

    private JSObject state(WebView w, int id) {
        JSObject o = new JSObject();
        o.put("id", id);
        o.put("url", w.getUrl());
        o.put("title", w.getTitle());
        o.put("progress", w.getProgress());
        o.put("canBack", w.canGoBack());
        java.util.concurrent.ConcurrentLinkedDeque<String> fs = fwdStack.get(id);
        o.put("canFwd", w.canGoForward() || (fs != null && !fs.isEmpty())); // V1.22.6
        return o;
    }

    private boolean isLocal(String url) {
        return url.matches("(?i)^http://(localhost|127\\.|10\\.|192\\.168\\.|172\\.(1[6-9]|2\\d|3[01])\\.).*");
    }

    /* ---------- media sniffing + saving ---------- */

    private final Map<Integer, LinkedHashMap<String, String>> sniffed = new HashMap<>();
    private static final Pattern MEDIA_EXT = Pattern.compile(
        "\\.(mp4|m4v|webm|mov|mkv|avi|3gp|flv|m3u8|mpd|mp3|m4a|aac|ogg|oga|opus|wav|flac|weba|pdf)$",
        Pattern.CASE_INSENSITIVE);

    private static final String SCAN_JS =
        "(function(){\n" +
        "var out=[],seen={},MAX=500;\n" +
        "var EXT={mp4:'video',m4v:'video',webm:'video',mov:'video',mkv:'video',avi:'video',flv:'video','3gp':'video',m3u8:'stream',mpd:'stream',mp3:'audio',m4a:'audio',aac:'audio',ogg:'audio',oga:'audio',opus:'audio',wav:'audio',flac:'audio',weba:'audio',jpg:'image',jpeg:'image',png:'image',gif:'image',webp:'image',avif:'image',bmp:'image',svg:'image',pdf:'doc',epub:'doc'};\n" +
        "function ext(u){var p=String(u).split('#')[0].split('?')[0],m=/\\.([a-z0-9]{2,5})$/i.exec(p);return m?m[1].toLowerCase():''}\n" +
        "function add(u,k,o){\n" +
        " try{\n" +
        "  if(out.length>=MAX||!u)return;u=String(u).trim();\n" +
        "  if(!u||/^(javascript|about|mailto|tel):/i.test(u))return;\n" +
        "  var a=/^(blob:|data:)/i.test(u)?u:new URL(u,document.baseURI).href;\n" +
        "  if(!/^(https?:|blob:|data:)/i.test(a))return;\n" +
        "  if(/^data:/i.test(a)&&(a.length>400000||/^data:image\\/svg/i.test(a)))return;\n" +
        "  if(seen[a])return;seen[a]=1;\n" +
        "  o=o||{};o.url=a;o.kind=k||EXT[ext(a)]||'file';\n" +
        "  out.push(o);\n" +
        " }catch(e){}\n" +
        "}\n" +
        "function best(ss){var b='',bw=-1;String(ss||'').split(/,\\s+/).forEach(function(p){var t=p.trim().split(/\\s+/);if(!t[0])return;var d=t[1]||'',w=parseFloat(d)||1;if(/x$/i.test(d))w=w*1000;if(w>bw){bw=w;b=t[0]}});return b}\n" +
        "var q=function(s){return [].slice.call(document.querySelectorAll(s))};\n" +
        "q('video').forEach(function(v){\n" +
        " var s=v.currentSrc||v.src||'';if(s)add(s,/^blob:/i.test(s)?'video':(EXT[ext(s)]==='stream'?'stream':'video'),{w:v.videoWidth||0,h:v.videoHeight||0,dur:isFinite(v.duration)?Math.round(v.duration):0,blob:/^blob:/i.test(s)?1:0});\n" +
        " q2(v,'source').forEach(function(c){var u=c.src||c.getAttribute('src');if(u)add(u,EXT[ext(u)]==='stream'?'stream':'video',{mime:c.type||''})});\n" +
        " if(v.poster)add(v.poster,'image',{w:0,h:0,poster:1});\n" +
        "});\n" +
        "function q2(root,s){return [].slice.call(root.querySelectorAll(s))}\n" +
        "q('audio').forEach(function(a){\n" +
        " var s=a.currentSrc||a.src||'';if(s)add(s,'audio',{blob:/^blob:/i.test(s)?1:0});\n" +
        " q2(a,'source').forEach(function(c){var u=c.src||c.getAttribute('src');if(u)add(u,'audio',{mime:c.type||''})});\n" +
        "});\n" +
        "q('picture source[srcset]').forEach(function(s){var u=best(s.getAttribute('srcset'));if(u)add(u,'image',{w:0,h:0})});\n" +
        "q('img').forEach(function(i){\n" +
        " var s=i.currentSrc||i.src||i.getAttribute('data-src')||i.getAttribute('data-original')||i.getAttribute('data-lazy-src')||'';\n" +
        " var big=best(i.getAttribute('srcset')||i.getAttribute('data-srcset')||'');\n" +
        " var same=!big||big===s;\n" +
        " add(big||s,'image',{w:same?(i.naturalWidth||0):0,h:same?(i.naturalHeight||0):0,alt:(i.alt||'').slice(0,80)});\n" +
        "});\n" +
        "q('a[href]').forEach(function(a){\n" +
        " var h=a.getAttribute('href')||'',k=EXT[ext(h.split('#')[0])];\n" +
        " if(k)add(a.href,k,{alt:(a.textContent||'').replace(/\\s+/g,' ').trim().slice(0,60)});\n" +
        "});\n" +
        "q('embed[src],object[data],iframe[src]').forEach(function(e){var u=e.getAttribute('src')||e.getAttribute('data'),k=EXT[ext(u||'')];if(k)add(u,k,{})});\n" +
        "q('meta[property],meta[name]').forEach(function(m){\n" +
        " var n=(m.getAttribute('property')||m.getAttribute('name')||'').toLowerCase(),c=m.getAttribute('content');\n" +
        " if(!c)return;\n" +
        " if(/^(og:image(:secure_url|:url)?|twitter:image(:src)?)$/.test(n))add(c,'image',{w:0,h:0,meta:1});\n" +
        " else if(/^(og:video(:secure_url|:url)?|twitter:player:stream)$/.test(n))add(c,EXT[ext(c)]==='stream'?'stream':'video',{meta:1});\n" +
        " else if(/^og:audio(:secure_url|:url)?$/.test(n))add(c,'audio',{meta:1});\n" +
        "});\n" +
        "q('link[rel=image_src]').forEach(function(l){add(l.href,'image',{w:0,h:0,meta:1})});\n" +
        "var els=q('div,section,span,a,li,figure,header,main,article'),n=0;\n" +
        "for(var i=0;i<els.length&&n<900;i++){n++;\n" +
        " var bg='';try{bg=getComputedStyle(els[i]).backgroundImage}catch(e){}\n" +
        " if(bg&&bg!=='none'&&bg.indexOf('url(')>=0){var re=/url\\((['\"]?)(.*?)\\1\\)/g,m;while((m=re.exec(bg)))add(m[2],'image',{w:0,h:0,bg:1})}\n" +
        "}\n" +
        "return JSON.stringify(out);\n" +
        "})()\n";

    private static String kindOfExt(String ext) {
        switch (ext) {
            case "m3u8": case "mpd": return "stream";
            case "mp3": case "m4a": case "aac": case "ogg": case "oga": case "opus": case "wav": case "flac": case "weba": return "audio";
            case "pdf": return "doc";
            default: return "video";
        }
    }

    /** Runs on WebView's network thread for every sub-request. Only records; never blocks or alters. */
    private void sniff(int id, String url) {
        if (url == null || !(url.startsWith("http://") || url.startsWith("https://"))) return;
        String path = url;
        int q = path.indexOf('?'); if (q >= 0) path = path.substring(0, q);
        int h = path.indexOf('#'); if (h >= 0) path = path.substring(0, h);
        Matcher m = MEDIA_EXT.matcher(path);
        if (!m.find()) return;
        String kind = kindOfExt(m.group(1).toLowerCase());
        synchronized (sniffed) {
            LinkedHashMap<String, String> mp = sniffed.get(id);
            if (mp == null) { mp = new LinkedHashMap<>(); sniffed.put(id, mp); }
            if (mp.size() < 200 && !mp.containsKey(url)) mp.put(url, kind);
        }
    }

    private void sniffClear(int id) {
        synchronized (sniffed) { sniffed.remove(id); }
    }

    private static String cleanName(String n) {
        if (n == null) return "";
        n = n.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").trim();
        while (n.startsWith(".")) n = n.substring(1);
        if (n.length() > 120) n = n.substring(n.length() - 120);
        return n;
    }

    /** V1.23.6: every http(s) download goes through Festers' own engine (FBDownloader): real progress, pause/resume, saved into Downloads/Festers. */
    private String enqueue(String url, String ua, String cd, String mime, String referer, String nameIn, boolean wifiOnly) {
        return enqueue(url, ua, cd, mime, referer, nameIn, wifiOnly, false);
    }

    private String enqueue(String url, String ua, String cd, String mime, String referer, String nameIn, boolean wifiOnly, boolean quiet) {
        FBDownloader.Job j = FBDownloader.get(getContext()).start(url, cleanName(nameIn), mime, ua, referer, wifiOnly, quiet);
        return j.name;
    }

    /** Kept for the callers that used the old in-app fallback; it is the same engine now. */
    private void fallbackDownload(final String url, final String ua, final String cd, final String mime, final String referer, final String nameIn) {
        enqueue(url, ua, cd, mime, referer, nameIn, false, false);
    }

    /** V1.23.1: streams into Downloads/Festers (MediaStore on Android 10+, plain file below). */
    private void saveStream(String name, String mime, java.io.InputStream in) throws Exception {
        byte[] buf = new byte[65536];
        int n;
        if (Build.VERSION.SDK_INT >= 29) {
            ContentResolver cr = getContext().getContentResolver();
            ContentValues cv = new ContentValues();
            cv.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
            cv.put(MediaStore.MediaColumns.MIME_TYPE, mime);
            cv.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
            cv.put(MediaStore.MediaColumns.IS_PENDING, 1);
            Uri u = cr.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
            if (u == null) throw new Exception("MediaStore refused the file");
            try (OutputStream os = cr.openOutputStream(u)) {
                if (os == null) throw new Exception("could not open the file");
                while ((n = in.read(buf)) > 0) os.write(buf, 0, n);
            } catch (Exception e) {
                try { cr.delete(u, null, null); } catch (Throwable ignored) { }
                throw e;
            }
            ContentValues done = new ContentValues();
            done.put(MediaStore.MediaColumns.IS_PENDING, 0);
            cr.update(u, done, null, null);
        } else {
            File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            if (!dir.exists() && !dir.mkdirs()) throw new Exception("could not create Downloads");
            try (FileOutputStream os = new FileOutputStream(new File(dir, name))) {
                while ((n = in.read(buf)) > 0) os.write(buf, 0, n);
            }
        }
    }

    /** Writes bytes (from a data: URL) into Downloads/Festers. */
    private void saveBytes(String name, String mime, byte[] bytes) throws Exception {
        if (Build.VERSION.SDK_INT >= 29) {
            ContentResolver cr = getContext().getContentResolver();
            ContentValues cv = new ContentValues();
            cv.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
            cv.put(MediaStore.MediaColumns.MIME_TYPE, mime);
            cv.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
            Uri u = cr.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
            if (u == null) throw new Exception("MediaStore refused the file");
            try (OutputStream os = cr.openOutputStream(u)) {
                if (os == null) throw new Exception("could not open the file");
                os.write(bytes);
            }
        } else {
            File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            if (!dir.exists() && !dir.mkdirs()) throw new Exception("could not create Downloads");
            try (FileOutputStream os = new FileOutputStream(new File(dir, name))) { os.write(bytes); }
        }
    }

    private void saveDataUrl(PluginCall call, String url, String nameIn) {
        JSObject o = new JSObject();
        try {
            int comma = url.indexOf(',');
            if (comma < 0) throw new Exception("bad data URL");
            String meta = url.substring(5, comma), payload = url.substring(comma + 1);
            boolean b64 = meta.endsWith(";base64");
            String mt = meta.replace(";base64", "");
            int semi = mt.indexOf(';'); if (semi >= 0) mt = mt.substring(0, semi);
            if (mt.isEmpty()) mt = "application/octet-stream";
            byte[] bytes = b64 ? Base64.decode(payload, Base64.DEFAULT) : Uri.decode(payload).getBytes(StandardCharsets.UTF_8);
            String name = cleanName(nameIn);
            if (name.isEmpty()) name = "festers-" + System.currentTimeMillis();
            if (name.indexOf('.') < 0) {
                String ex = MimeTypeMap.getSingleton().getExtensionFromMimeType(mt);
                if (ex != null) name = name + "." + ex;
            }
            saveBytes(name, mt, bytes);
            o.put("ok", true); o.put("name", name);
        } catch (Throwable e) {
            o.put("ok", false); o.put("desc", "Could not save: " + e.getMessage());
        }
        call.resolve(o);
    }


    /* ---------- V1.24.0: pull down to refresh the page ----------
     * A dark rounded chip (the same shape language as the tabs and chips) whose red edge traces itself as you pull, with an orange reload arrow
     * that turns with the pull. It is deliberately hard to trigger: nothing shows until a short dead zone is passed, the pull has resistance
     * (about 2.5 cm of finger travel to arm it), and it must be held for a moment, so flicking back to the top of a page or a short drag never
     * reloads. Past the line the chip locks to bright red and ticks once. Let go and the red edge runs around the chip while the page reloads;
     * it lifts away when the page finishes (or after 8 s). No extra library. */
    private PtrView ptr;
    private WebView ptrWeb;
    private boolean ptrSpin;
    private final Runnable ptrTimeout = () -> ptrDone();

    private class PtrView extends View {
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), edge = new Paint(Paint.ANTI_ALIAS_FLAG), trace = new Paint(Paint.ANTI_ALIAS_FLAG),
                glyph = new Paint(Paint.ANTI_ALIAS_FLAG), head = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path rr = new Path(), seg = new Path(), tri = new Path();
        private final PathMeasure pm = new PathMeasure();
        private final RectF box = new RectF(), ar = new RectF();
        private final float dn;
        float prog = 0f, rot = 0f;
        boolean armed = false, spinning = false;
        private android.animation.ValueAnimator anim;
        PtrView(Context c) {
            super(c);
            dn = c.getResources().getDisplayMetrics().density;
            fill.setStyle(Paint.Style.FILL); fill.setColor(0xF2120A0C);
            edge.setStyle(Paint.Style.STROKE); edge.setColor(0x66FF2D3D); edge.setStrokeWidth(1.2f * dn);
            trace.setStyle(Paint.Style.STROKE); trace.setStrokeCap(Paint.Cap.ROUND); trace.setStrokeWidth(2.6f * dn);
            glyph.setStyle(Paint.Style.STROKE); glyph.setStrokeCap(Paint.Cap.ROUND); glyph.setStrokeWidth(2f * dn);
            head.setStyle(Paint.Style.FILL);
            setElevation(6f * dn);
            setAlpha(0f);
        }
        @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
            super.onSizeChanged(w, h, ow, oh);
            float in = 2f * dn;
            box.set(in, in, w - in, h - in);
            rr.reset();
            rr.addRoundRect(box, 15f * dn, 15f * dn, Path.Direction.CW);
            pm.setPath(rr, false);
        }
        void spin(boolean on) {
            spinning = on;
            if (anim != null) { anim.cancel(); anim = null; }
            if (on) {
                anim = android.animation.ValueAnimator.ofFloat(0f, 360f);
                anim.setDuration(1100); anim.setRepeatCount(android.animation.ValueAnimator.INFINITE);
                anim.setInterpolator(new android.view.animation.LinearInterpolator());
                anim.addUpdateListener(a -> { rot = (Float) a.getAnimatedValue(); invalidate(); });
                anim.start();
            }
        }
        @Override protected void onDraw(Canvas cv) {
            float w = getWidth(), h = getHeight(), cx = w / 2f, cy = h / 2f, len = pm.getLength();
            cv.drawRoundRect(box, 15f * dn, 15f * dn, fill);
            cv.drawPath(rr, edge);
            boolean hot = armed || spinning;
            trace.setColor(hot ? 0xFFFF2D3D : 0xFFFF5A4A);
            seg.reset();
            if (len > 0f) {
                if (spinning) {
                    float st = (rot / 360f) * len, en = st + len * 0.30f;
                    if (en <= len) pm.getSegment(st, en, seg, true);
                    else { pm.getSegment(st, len, seg, true); pm.getSegment(0f, en - len, seg, true); }
                } else if (prog > 0.02f) {
                    pm.getSegment(0f, len * Math.min(1f, prog), seg, true);
                }
                cv.drawPath(seg, trace);
            }
            int gc = armed || spinning ? 0xFFFFE27A : 0xFFFF8A1F;
            glyph.setColor(gc); head.setColor(gc);
            float r = 7.5f * dn;
            float a0 = spinning ? rot * 1.5f : (prog * 270f - 60f);
            ar.set(cx - r, cy - r, cx + r, cy + r);
            cv.drawArc(ar, a0, 270f, false, glyph);
            double ea = Math.toRadians(a0 + 270f);
            float px = cx + r * (float) Math.cos(ea), py = cy + r * (float) Math.sin(ea);
            float nx = (float) Math.cos(ea), ny = (float) Math.sin(ea), tx = -ny, ty = nx;
            float hl = 4.2f * dn, hw = 3.3f * dn;
            tri.reset();
            tri.moveTo(px + tx * hl, py + ty * hl);
            tri.lineTo(px + nx * hw, py + ny * hw);
            tri.lineTo(px - nx * hw, py - ny * hw);
            tri.close();
            cv.drawPath(tri, head);
        }
    }

    /* V1.26.0: no spinner and nothing is drawn while pulling. Once the pull is accepted the page reloads and a wide dark panel
     * ("Your Page Is Being Restored" / "Bojack Security") drops in from the top edge, stays at least 1.3 s, and lifts away when the page finishes
     * (or after 5 s). It is a native view because the page view sits above the app's own UI. */
    private android.widget.LinearLayout ptrPanel;
    private long ptrShownAt;
    private final Runnable ptrHideNow = () -> {
        final android.widget.LinearLayout p = ptrPanel;
        if (p == null || p.getParent() == null) return;
        final float dn = getContext().getResources().getDisplayMetrics().density;
        p.animate().cancel();
        p.animate().alpha(0f).translationY(-30f * dn).setDuration(220).withEndAction(() -> {
            try { if (p.getParent() instanceof ViewGroup) ((ViewGroup) p.getParent()).removeView(p); } catch (Throwable ignored) { }
        }).start();
    };

    private void ptrShow() {
        try {
            if (holder == null) return;
            final Context c = getContext();
            final float dn = c.getResources().getDisplayMetrics().density;
            if (ptrPanel == null) {
                android.widget.LinearLayout p = new android.widget.LinearLayout(c);
                p.setOrientation(android.widget.LinearLayout.VERTICAL);
                p.setGravity(android.view.Gravity.CENTER);
                p.setPadding(Math.round(20 * dn), Math.round(11 * dn), Math.round(20 * dn), Math.round(11 * dn));
                android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
                bg.setColor(0xF2120A0C);
                bg.setCornerRadius(14f * dn);
                bg.setStroke(Math.round(1.5f * dn), 0xCCFF2D3D);
                p.setBackground(bg);
                android.widget.TextView a = new android.widget.TextView(c);
                a.setText("Your Page Is Being Restored");
                a.setTextColor(0xFFFFFFFF);
                a.setTextSize(15f);
                a.setTypeface(null, android.graphics.Typeface.BOLD);
                a.setGravity(android.view.Gravity.CENTER);
                android.widget.TextView b = new android.widget.TextView(c);
                b.setText("Bojack Security");
                b.setTextColor(0xFFFF8A1F);
                b.setTextSize(12f);
                b.setGravity(android.view.Gravity.CENTER);
                p.addView(a);
                p.addView(b);
                FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.gravity = android.view.Gravity.TOP;
                lp.setMargins(Math.round(12 * dn), Math.round(10 * dn), Math.round(12 * dn), 0);
                p.setLayoutParams(lp);
                p.setElevation(8f * dn);
                ptrPanel = p;
            }
            final android.widget.LinearLayout p = ptrPanel;
            p.removeCallbacks(ptrHideNow);
            p.removeCallbacks(ptrTimeout);
            p.animate().cancel();
            if (p.getParent() == null) holder.addView(p);
            p.bringToFront();
            p.setAlpha(0f);
            p.setTranslationY(-40f * dn);
            p.animate().alpha(1f).translationY(0f).setDuration(220).start();
            ptrShownAt = android.os.SystemClock.uptimeMillis();
            p.postDelayed(ptrTimeout, 5000);
        } catch (Throwable ignored) { }
    }

    private void ptrEnsure() { }
    private void ptrPull(float pull, float go) { }
    private void ptrCancel() { }
    private void ptrRun(WebView w) {
        ptrWeb = w;
        try { w.reload(); } catch (Throwable ignored) { }
        ptrShow();
    }
    private void ptrDone() {
        final android.widget.LinearLayout p = ptrPanel;
        if (p == null || p.getParent() == null) return;
        p.removeCallbacks(ptrTimeout);
        p.removeCallbacks(ptrHideNow);
        long left = 1300L - (android.os.SystemClock.uptimeMillis() - ptrShownAt);
        if (left > 0) p.postDelayed(ptrHideNow, left); else ptrHideNow.run();
    }

    private void attachPull(final WebView w) {
        final float dn = getContext().getResources().getDisplayMetrics().density;
        final float dead = Math.max(ViewConfiguration.get(getContext()).getScaledTouchSlop(), 18f * dn); // nothing happens inside this
        final float go = 74f * dn;      // resisted pull that arms the refresh
        final float drag = 0.5f;        // resistance: finger travel needed = dead + go / drag (about 2.5 cm)
        final long minHold = 220L;      // ms the pull must have been held; a quick flick never reloads
        final float[] st = new float[3]; // x0, y0, state: 0 idle, 1 armed (at top), 2 pulling
        final long[] t0 = new long[1];
        final boolean[] inner = new boolean[1]; // V1.26.0: the finger started inside a scrolled box on the page (chat, feed, map panel), so this is scrolling, not a refresh
        final long[] gen = new long[1];
        w.setOnTouchListener((v, e) -> {
            try {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        st[0] = e.getX(); st[1] = e.getY();
                        st[2] = (w.getScrollY() <= 0 && !ptrSpin) ? 1f : 0f;
                        inner[0] = false;
                        gen[0]++;
                        if (st[2] == 1f) {
                            final long g = gen[0];
                            final float cx = e.getX() / dn, cy = e.getY() / dn;
                            try {
                                w.evaluateJavascript("(function(){try{var e=document.elementFromPoint(" + cx + "," + cy + ");while(e&&e!==document.documentElement){if(e.scrollTop>0)return 1;e=e.parentElement}}catch(x){}return 0})()",
                                        val -> { if (g == gen[0] && "1".equals(val)) inner[0] = true; });
                            } catch (Throwable ignored) { }
                        }
                        break;
                    case MotionEvent.ACTION_MOVE: {
                        if (st[2] == 0f) break;
                        if (w.getScrollY() > 0 || e.getPointerCount() > 1) { if (st[2] == 2f) ptrCancel(); st[2] = 0f; break; }
                        float dy = e.getY() - st[1], dx = Math.abs(e.getX() - st[0]);
                        if (dy <= dead) { if (st[2] == 2f) { ptrCancel(); st[2] = 1f; } break; }
                        if (dx > dy * 0.6f && st[2] == 1f) { st[2] = 0f; break; } // mostly sideways: not a pull
                        if (st[2] == 1f) { st[2] = 2f; t0[0] = e.getEventTime(); }
                        ptrPull((dy - dead) * drag, go);
                        break;
                    }
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        if (st[2] == 2f) {
                            float pull = (e.getY() - st[1] - dead) * drag;
                            boolean held = e.getEventTime() - t0[0] >= minHold;
                            if (e.getActionMasked() == MotionEvent.ACTION_UP && pull >= go && held && !inner[0]) ptrRun(w);
                            else ptrCancel();
                        }
                        st[2] = 0f;
                        break;
                    default: break;
                }
            } catch (Throwable ignored) { }
            return false; // never consume: the page still gets every touch
        });
    }

    /* ---------- creation ---------- */

    private WebView make(final int id, boolean isPriv, boolean js, double zoom, boolean desk, boolean strict) {
        final WebView w = new WebView(getContext());
        boolean isolated = false;
        if (isPriv) { isolated = useProfile(w); if (isolated) profileInUse = true; }
        priv.put(id, isPriv);
        strictOf.put(id, Boolean.valueOf(strict)); // V1.22.2
        w.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        w.setBackgroundColor(Color.WHITE); // V1.14.7: pages that set no background must look white like in other browsers, not black
        // V1.14.2: let Android's autofill service (Google, Bitwarden, ...) fill logins in normal tabs, never in burner tabs.
        if (Build.VERSION.SDK_INT >= 26) {
            try { w.setImportantForAutofill(isPriv ? View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS : View.IMPORTANT_FOR_AUTOFILL_YES); } catch (Throwable ignored) { }
        }
        enablePasskeys(w);
        hideRequestedWith(w);
        stealthScript(w); // V1.21.5
        chromeHints(w); // V1.16.1
        hintState.put(w, Boolean.valueOf("chrome".equals(hintsPref)));
        if (!isPriv) w.addJavascriptInterface(new CredBridge(id, w), "FBCred"); // V1.15.0: save-password bar, never in burner tabs
        w.addJavascriptInterface(new BlobBridge(id), "FBBlob"); // V1.21.7: JS-generated (blob:) downloads, see blobCaptureScript
        blobCaptureScript(w); // V1.21.7
        w.addJavascriptInterface(new PkBridge(id, w), "FestersPasskey"); pkShimScript(w); // V1.23.2: Conduint passkey bridge
        try { // V1.23.8: thin glowing cyan scrollbar on every page
            w.setVerticalScrollBarEnabled(true);
            w.setHorizontalScrollBarEnabled(false);
            w.setScrollbarFadingEnabled(true);
            w.setScrollBarStyle(View.SCROLLBARS_INSIDE_OVERLAY);
            if (Build.VERSION.SDK_INT >= 29) {
                android.graphics.drawable.GradientDrawable th = new android.graphics.drawable.GradientDrawable(
                        android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0xFF00E5FF, 0xFF00B8D4});
                float dn = getContext().getResources().getDisplayMetrics().density;
                th.setCornerRadius(3f * dn);
                th.setSize(Math.max(2, Math.round(2.5f * dn)), Math.round(48 * dn));
                w.setVerticalScrollbarThumbDrawable(th);
            }
        } catch (Throwable ignored) { }
        WebSettings s = w.getSettings();
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true); // V1.21.4: Conduint's browser sets this and its pages load; Festers did not
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE); // V1.14.7: was NEVER_ALLOW, which left pages half loaded
        s.setSupportZoom(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setSupportMultipleWindows(true);
        s.setJavaScriptCanOpenWindowsAutomatically(true); // V1.22.5: Prodigy WebSheet parity; false dropped script-opened windows
        s.setMediaPlaybackRequiresUserGesture(true);
        if (isPriv) { s.setCacheMode(WebSettings.LOAD_NO_CACHE); s.setSaveFormData(false); }
        if (Build.VERSION.SDK_INT >= 26) s.setSafeBrowsingEnabled(true);
        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(!"none".equals(cookieMode));
        cm.setAcceptThirdPartyCookies(w, thirdOk(isPriv, strict));
        applySettings(w, id, js, zoom, desk);
        desktop.put(id, desk);

        w.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                String u = r.getUrl().toString();
                String sc = r.getUrl().getScheme() == null ? "" : r.getUrl().getScheme().toLowerCase();
                if (r.isForMainFrame() && !(Build.VERSION.SDK_INT >= 24 && r.isRedirect())) fwdStack.remove(id); // V1.22.6: a new page you opened ends the forward list
                if (r.isForMainFrame()) { syncHints(v, u); siteCookies(v, id, u); if (sc.equals("http") || sc.equals("https")) applyJsPolicy(v, id, r.getUrl().getHost()); enablePasskeys(v, r.getUrl().getHost()); } // V1.21.1, V1.22.2, V1.22.4, V1.23.0
                // Standard HTTP(S) navigations must remain native WebView navigations;
                // WebView itself follows 301/302 redirects and updates history correctly.
                if (sc.equals("https")) return false;
                if (sc.equals("http")) {
                    if (Boolean.TRUE.equals(httpsOnly.get(id)) && !isLocal(u) && r.isForMainFrame() && !httpHosts.contains(String.valueOf(r.getUrl().getHost()).toLowerCase())) {
                        // V1.23.0: remember what was upgraded so a site with no HTTPS at all can fall back to http (see onReceivedError)
                        upgraded.put(id, new String[] { "https:" + u.substring(5), u });
                        v.loadUrl("https:" + u.substring(5));
                        return true;
                    }
                    return false;
                }
                if (sc.equals("about") || sc.equals("blob") || sc.equals("data") || sc.equals("javascript")) return false;
                if (sc.equals("file") || sc.equals("content")) return true;
                try {
                    Intent it = u.startsWith("intent:") ? Intent.parseUri(u, Intent.URI_INTENT_SCHEME) : new Intent(Intent.ACTION_VIEW, r.getUrl());
                    String fb = u.startsWith("intent:") ? it.getStringExtra("browser_fallback_url") : null;
                    it.addCategory(Intent.CATEGORY_BROWSABLE);
                    it.setComponent(null);
                    it.setSelector(null);
                    try {
                        getActivity().startActivity(it);
                    } catch (Throwable e2) {
                        // V1.21.0: no app handles it, so open the site's own web fallback instead of a dead link
                        if (fb != null && (fb.startsWith("https://") || fb.startsWith("http://"))) v.loadUrl(fb);
                    }
                } catch (Throwable e) { /* nothing handles it */ }
                return true;
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest r) {
                try { sniff(id, r.getUrl().toString()); } catch (Throwable ignored) { }
                try {
                    if (r.isForMainFrame()) { mainHost.put(id, String.valueOf(r.getUrl().getHost()).toLowerCase()); mainUrl.put(id, r.getUrl().toString()); } // V1.21.6, V1.22.7
                    if (Boolean.TRUE.equals(shieldOn.get(id)) && !r.isForMainFrame() && !siteFix(mainHost.get(id))) { // V1.22.2: no blocking on site-fix hosts
                        String h = r.getUrl().getHost();
                        // V1.21.6: never block a site's own requests. Visiting a listed host (yandex.ru, disqus.com...) blocked every
                        // resource of the page itself and left it blank.
                        if (isTracker(h) && !sameSite(h, mainHost.get(id))) {
                            shieldHit(id, h); lastBlockedHost = h;
                            return new WebResourceResponse("text/plain", "UTF-8", new java.io.ByteArrayInputStream(new byte[0]));
                        }
                    }
                } catch (Throwable ignored) { }
                return null;
            }

            @Override
            public void onPageStarted(WebView v, String url, Bitmap fav) {
                sniffClear(id);
                synchronized (shieldCount) { shieldCount.put(id, 0); shieldLastHost.remove(id); shieldHosts.remove(id); }
                shieldEmit(id);
                JSObject o = state(v, id); o.put("url", url); o.put("loading", true); emit("page", o);
            }

            @Override
            public void onPageFinished(WebView v, String url) {
                shieldEmit(id);
                if (url != null && url.startsWith("http")) netRetry.remove(id); // V1.22.2
                try { CookieManager.getInstance().flush(); } catch (Throwable ignored) { } // V1.21.5
                if (!Boolean.TRUE.equals(priv.get(id)) && url != null && url.startsWith("https://")) {
                    try { v.evaluateJavascript(CAP_JS, null); } catch (Throwable ignored) { }
                }
                if (v == ptrWeb) ptrDone(); // V1.23.11, reworked V1.24.0
                JSObject o = state(v, id); o.put("loading", false); emit("page", o);
            }

            @Override
            public void doUpdateVisitedHistory(WebView v, String url, boolean isReload) {
                JSObject o = state(v, id); o.put("url", url); o.put("reload", isReload); o.put("nav", true); String dd = takeDir(id); if (dd != null) o.put("dir", dd); emit("page", o);
            }

            @Override
            public void onReceivedError(WebView v, WebResourceRequest r, WebResourceError e) {
                if (!r.isForMainFrame()) return;
                String failedUrl = r.getUrl() == null ? "" : r.getUrl().toString();
                String desc = Build.VERSION.SDK_INT >= 23 ? String.valueOf(e.getDescription()) : "Load error";
                if (desc.contains("ERR_ABORTED")) return; // V1.22.2
                // V1.23.0: HTTPS-only upgraded this address and the site has no working HTTPS (older government sites): open it over http, once, and remember the host.
                try {
                    String[] up = upgraded.get(id);
                    if (up != null && noFrag(up[0]).equalsIgnoreCase(noFrag(failedUrl)) && !desc.contains("ERR_CERT") && !desc.contains("ERR_SSL")) {
                        upgraded.remove(id);
                        String uh = Uri.parse(up[1]).getHost();
                        if (uh != null) httpHosts.add(uh.toLowerCase());
                        JSObject fo = new JSObject(); fo.put("id", id); fo.put("kind", "httpfallback"); fo.put("host", uh == null ? "" : uh); fo.put("url", up[1]); fo.put("desc", "No HTTPS on " + uh + ", opened over http");
                        emit("error", fo);
                        v.loadUrl(up[1]);
                        return;
                    }
                } catch (Throwable ignored) { } // a cancelled load (redirect, new navigation) is not a failure and must not replace the page
                if (tryRetry(v, id, failedUrl, desc)) return; // V1.22.2
                JSObject o = new JSObject();
                o.put("id", id); o.put("url", failedUrl); o.put("desc", desc);
                emit("error", o);
                // Main-frame failures get a visible, self-contained page rather than a blank canvas.
                // Escape all values because the error description can originate from the network.
                String safeUrl = android.text.TextUtils.htmlEncode(failedUrl);
                String safeDesc = android.text.TextUtils.htmlEncode(desc);
                String html = "<!doctype html><html><head><meta name=viewport content='width=device-width,initial-scale=1'><title>Page failed to load</title><style>body{font:16px sans-serif;background:#fff;color:#202124;margin:0;padding:28px;line-height:1.5}h2{font-size:22px}p{overflow-wrap:anywhere;color:#5f6368}.mark{font-size:34px}</style></head><body><div class=mark>⚠</div><h2>Page failed to load</h2><p>Festers Browser could not load this page.</p><p>" + safeDesc + "</p><p>" + safeUrl + "</p></body></html>";
                try { v.loadDataWithBaseURL("about:blank", html, "text/html", "UTF-8", null); } catch (Throwable ignored) { }
            }

            @Override
            public void onReceivedSslError(final WebView v, final android.webkit.SslErrorHandler h, final android.net.http.SslError e) {
                // V1.23.0: three layers, in this order.
                // 1. A site you allowed with Continue anyway proceeds for ANY certificate problem (you were shown the details first).
                // 2. An untrusted chain is repaired the way Chrome does it: the missing intermediate certificate is fetched from the
                //    certificate's own AIA field and the rebuilt chain is validated against the phone's trust store. Only a chain that
                //    validates is accepted, so a forged or self-signed certificate still fails.
                // 3. Everything else shows the certificate bar with the facts and the Continue anyway button.
                String eh = null;
                try { eh = Uri.parse(e.getUrl()).getHost(); } catch (Throwable ignored) { }
                if (sslAllowed(eh)) { h.proceed(); return; }
                if (e.getPrimaryError() == android.net.http.SslError.SSL_UNTRUSTED && FBCertHelper.chainOnly(e)) {
                    FBCertHelper.verifyAsync(e, eh, ok -> {
                        if (ok) { try { h.proceed(); } catch (Throwable ignored) { } }
                        else { try { h.cancel(); } catch (Throwable ignored) { } sslFail(v, e); }
                    });
                    return;
                }
                h.cancel();
                sslFail(v, e);
            }

            private void sslFail(WebView v, android.net.http.SslError e) {
                // V1.22.7: Prodigy's WebSheet never replaces the page for a certificate error: the stock client just refuses the bad request and the page
                // loads without it. Festers replaced the WHOLE page with "Secure connection failed" even when only a script, image or API call on
                // some other host (or another path) had a bad certificate. Only a failure of the page itself gets the error page now.
                try {
                    String mu = mainUrl.get(id);
                    if (mu != null && !noFrag(mu).equalsIgnoreCase(noFrag(e.getUrl()))) { lastBlockedHost = "cert: " + Uri.parse(e.getUrl()).getHost(); return; }
                } catch (Throwable ignored) { }
                String why;
                switch (e.getPrimaryError()) {
                    case android.net.http.SslError.SSL_UNTRUSTED: why = "untrusted"; break;
                    case android.net.http.SslError.SSL_EXPIRED: why = "expired"; break;
                    case android.net.http.SslError.SSL_IDMISMATCH: why = "idmismatch"; break;
                    case android.net.http.SslError.SSL_NOTYETVALID: why = "notyetvalid"; break;
                    case android.net.http.SslError.SSL_DATE_INVALID: why = "dateinvalid"; break;
                    default: why = "invalid";
                }
                String issuer = "";
                try { issuer = e.getCertificate().getIssuedBy().getCName(); } catch (Throwable ignored) { }
                JSObject o = new JSObject();
                o.put("id", id); o.put("url", e.getUrl()); o.put("desc", "Certificate problem. The connection was blocked.");
                String[] fx = FBCertHelper.facts(e); o.put("subject", fx[1]); o.put("from", fx[2]); o.put("to", fx[3]); o.put("sha", fx[4]); o.put("chase", FBCertHelper.lastNote);
                o.put("kind", "ssl"); o.put("why", why); try { o.put("host", Uri.parse(e.getUrl()).getHost()); } catch (Throwable ignored) { } o.put("issuer", issuer == null ? "" : issuer); // V1.19.2
                emit("error", o);
                String safeUrl = android.text.TextUtils.htmlEncode(e.getUrl() == null ? "" : e.getUrl());
                String safeWhy = android.text.TextUtils.htmlEncode(why);
                String html = "<!doctype html><html><head><meta name=viewport content='width=device-width,initial-scale=1'><title>Secure connection failed</title><style>body{font:16px sans-serif;background:#fff;color:#202124;margin:0;padding:28px;line-height:1.5}h2{font-size:22px}p{overflow-wrap:anywhere;color:#5f6368}</style></head><body><h2>Secure connection failed</h2><p>Festers Browser blocked this connection because the site's certificate could not be verified. Continuing could expose your passwords or data.</p><p>Certificate issue: " + safeWhy + "</p><p>" + safeUrl + "</p></body></html>";
                try { v.loadDataWithBaseURL("about:blank", html, "text/html", "UTF-8", null); } catch (Throwable ignored) { }
            }

            /* V1.14.7: when Android stops a page's renderer (low memory or a crash) the page turns blank or black and, by default, the whole app is killed. Keep the app alive and say what happened. */
            @Override
            public boolean onRenderProcessGone(WebView v, android.webkit.RenderProcessGoneDetail d) {
                boolean crashed = Build.VERSION.SDK_INT >= 26 && d != null && d.didCrash();
                lastRenderGone = crashed ? "the page crashed" : "Android stopped the page to free memory";
                // A renderer-dead WebView must never be reused. Replace it natively and restore
                // the last URL, rather than depending on the JS layer to notice and reopen the tab.
                String goneUrl = v != null && v.getUrl() != null ? v.getUrl() : "";
                boolean wasPrivate = Boolean.TRUE.equals(priv.get(id));
                try {
                    if (v != null && views.get(id) == v) {
                        views.remove(id);
                        if (holder != null) holder.removeView(v);
                        v.destroy();
                    }
                } catch (Throwable ignored) { }
                JSObject o = new JSObject();
                o.put("id", id); o.put("url", goneUrl); o.put("kind", "gone");
                o.put("desc", "This page stopped (" + lastRenderGone + "). Recreating the page.");
                emit("error", o);
                try {
                    if (holder != null && !goneUrl.isEmpty()) {
                        WebView replacement = make(id, wasPrivate, true, 1.0, Boolean.TRUE.equals(desktop.get(id)), false);
                        replacement.loadUrl(goneUrl);
                    }
                } catch (Throwable ignored) { }
                return true;
            }
        });

        w.setWebChromeClient(new WebChromeClient() {
            @Override public void onProgressChanged(WebView v, int p) {
                JSObject o = state(v, id); o.put("progress", p); o.put("loading", p < 100); emit("page", o);
            }
            @Override public void onReceivedTitle(WebView v, String t) { emit("page", state(v, id)); }
            @Override public void onReceivedIcon(WebView v, Bitmap icon) {
                try {
                    if (icon == null) return;
                    Bitmap b = Bitmap.createScaledBitmap(icon, 32, 32, true);
                    ByteArrayOutputStream bo = new ByteArrayOutputStream();
                    b.compress(Bitmap.CompressFormat.PNG, 100, bo);
                    JSObject o = new JSObject();
                    o.put("id", id);
                    o.put("data", "data:image/png;base64," + Base64.encodeToString(bo.toByteArray(), Base64.NO_WRAP));
                    emit("favicon", o);
                } catch (Throwable ignored) { }
            }
            @Override public boolean onCreateWindow(WebView v, boolean dialog, boolean userGesture, Message msg) {
                // V1.22.6: the popup is a real tab view handed to the page, so window.opener, postMessage and window.close() work
                // (Google sign-in popups, "Sign in with Google" on other sites). Before, the popup was thrown away and its URL
                // reopened as an unrelated tab, which left the opener waiting and the page refreshing in place.
                if (!userGesture && !siteFix(mainHost.get(id))) return false;
                try {
                    final int pid = ++popupSeq;
                    boolean pp = Boolean.TRUE.equals(priv.get(id));
                    WebView pop = make(pid, pp, true, 1.0, Boolean.TRUE.equals(desktop.get(id)), Boolean.TRUE.equals(strictOf.get(id)));
                    shieldOn.put(pid, Boolean.TRUE.equals(shieldOn.get(id)));
                    httpsOnly.put(pid, Boolean.TRUE.equals(httpsOnly.get(id)));
                    JSObject o = new JSObject(); o.put("id", id); o.put("popupId", pid); o.put("url", "");
                    emit("newWindow", o);
                    ((WebView.WebViewTransport) msg.obj).setWebView(pop);
                    msg.sendToTarget();
                    return true;
                } catch (Throwable e) { return false; }
            }
            @Override public void onCloseWindow(WebView v) {
                JSObject o = new JSObject(); o.put("id", idOf(v)); emit("closeWindow", o);
            }
            /** V1.14.4: camera/microphone requests from a page. Asks you every time; Android must already allow it (Settings > Android Permissions). */
            @Override public void onPermissionRequest(final android.webkit.PermissionRequest req) {
                ui(() -> sitePermission(req));
            }
            @Override public void onPermissionRequestCanceled(android.webkit.PermissionRequest req) { }
            @Override public void onGeolocationPermissionsShowPrompt(final String origin, final android.webkit.GeolocationPermissions.Callback cb) {
                ui(() -> siteGeo(origin, cb));
            }
            @Override public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb, FileChooserParams p) {
                if (chooserLauncher == null) { cb.onReceiveValue(null); return true; }
                if (chooserCallback != null) chooserCallback.onReceiveValue(null);
                chooserCallback = cb;
                try { chooserLauncher.launch(p.createIntent()); }
                catch (ActivityNotFoundException e) { chooserCallback = null; cb.onReceiveValue(null); }
                return true;
            }
            @Override public void onShowCustomView(View view, CustomViewCallback cb) {
                if (customView != null) { cb.onCustomViewHidden(); return; }
                customView = view; customCb = cb;
                view.setBackgroundColor(Color.BLACK);
                ViewGroup decor = (ViewGroup) getActivity().getWindow().getDecorView();
                decor.addView(view, new FrameLayout.LayoutParams(-1, -1));
                decor.setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
            }
            @Override public void onHideCustomView() { exitFullscreen(); }
        });

        w.setDownloadListener(new DownloadListener() {
            @Override
            public void onDownloadStart(String url, String ua, String cd, String mime, long len) {
                JSObject o = new JSObject();
                o.put("id", id); o.put("url", url); o.put("size", len > 0 ? len : 0);
                if (url.startsWith("blob:")) {
                    // V1.23.1: ask the page itself to read the blob and hand the bytes to FBBlob.save
                    String bjs = "(async function(){try{var r=await fetch(" + org.json.JSONObject.quote(url) + ");var b=await r.blob();var fr=new FileReader();"
                        + "fr.onload=function(){var s=String(fr.result||'');var i=s.indexOf(',');window.FBBlob.save('',b.type||'',s.slice(i+1))};fr.readAsDataURL(b)}catch(e){}})();";
                    try { w.evaluateJavascript(bjs, null); } catch (Throwable ignored) { }
                    return;
                }
                if (url.startsWith("data:")) {
                    final String durl = url;
                    new Thread(() -> {
                        JSObject r = new JSObject();
                        try {
                            int comma = durl.indexOf(',');
                            String meta = durl.substring(5, comma), payload = durl.substring(comma + 1);
                            boolean b64 = meta.endsWith(";base64");
                            String mt = meta.replace(";base64", "");
                            int semi = mt.indexOf(';'); if (semi >= 0) mt = mt.substring(0, semi);
                            if (mt.isEmpty()) mt = "application/octet-stream";
                            byte[] bytes = b64 ? Base64.decode(payload, Base64.DEFAULT) : Uri.decode(payload).getBytes(StandardCharsets.UTF_8);
                            String name = cleanName(URLUtil.guessFileName(durl.length() > 200 ? "data" : durl, cd, mt));
                            if (name.isEmpty()) name = "festers-" + System.currentTimeMillis();
                            saveBytes(name, mt, bytes);
                            r.put("id", id); r.put("ok", true); r.put("name", name);
                        } catch (Throwable e) {
                            r.put("id", id); r.put("ok", false); r.put("name", ""); r.put("desc", "Download could not start.");
                        }
                        emit("download", r);
                    }).start();
                    return;
                }
                try {
                    enqueue(url, ua, cd, mime, w.getUrl(), "", S_WIFI_ONLY); // V1.23.6: the engine announces start, progress and the end itself
                } catch (Throwable e) {
                    JSObject r = new JSObject();
                    r.put("id", id); r.put("ok", false); r.put("name", ""); r.put("desc", "Download could not start: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
                    emit("download", r);
                }
            }
        });

        w.setOnLongClickListener(v -> {
            WebView.HitTestResult hr = ((WebView) v).getHitTestResult();
            int type = hr.getType();
            String ex = hr.getExtra();
            if (ex == null) return false;
            if (type == WebView.HitTestResult.IMAGE_TYPE || type == WebView.HitTestResult.SRC_ANCHOR_TYPE) {
                JSObject o = new JSObject();
                o.put("id", id);
                o.put("img", type == WebView.HitTestResult.IMAGE_TYPE ? ex : "");
                o.put("link", type == WebView.HitTestResult.SRC_ANCHOR_TYPE ? ex : "");
                emit("longpress", o);
                return true;
            }
            if (type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE) {
                Handler hd = new Handler(Looper.getMainLooper()) {
                    @Override public void handleMessage(Message m) {
                        Bundle d = m.getData();
                        JSObject o = new JSObject();
                        o.put("id", id);
                        o.put("img", d == null || d.getString("src") == null ? "" : d.getString("src"));
                        o.put("link", d == null || d.getString("url") == null ? "" : d.getString("url"));
                        emit("longpress", o);
                    }
                };
                ((WebView) v).requestFocusNodeHref(hd.obtainMessage());
                return true;
            }
            return false;
        });

        w.setFindListener((active, count, done) -> {
            JSObject o = new JSObject();
            o.put("id", id); o.put("active", active); o.put("count", count); o.put("done", done);
            emit("find", o);
        });

        attachPull(w); // V1.23.11
        holder.addView(w);
        views.put(id, w);
        isolatedMap.put(id, !isPriv || isolated);
        return w;
    }

    /* ---------- plugin methods ---------- */

    /** V1.18.1: read-only report for one tab, shown in Settings > Page check. */
    @PluginMethod
    public void diag(final PluginCall call) {
        ui(() -> {
            JSObject r = new JSObject();
            try {
                int id = call.getInt("id", -1);
                WebView w = views.get(id);
                r.put("hasView", w != null);
                if (w != null) {
                    WebSettings st = w.getSettings();
                    CookieManager cm = CookieManager.getInstance();
                    r.put("js", st.getJavaScriptEnabled());
                    r.put("dom", st.getDomStorageEnabled());
                    r.put("cookies", cm.acceptCookie());
                    r.put("thirdParty", cm.acceptThirdPartyCookies(w));
                    r.put("ua", st.getUserAgentString());
                    r.put("burner", Boolean.TRUE.equals(priv.get(id)));
                    r.put("shield", Boolean.TRUE.equals(shieldOn.get(id)));
                }
                r.put("hints", hintsMode);
                r.put("lastBlocked", lastBlockedHost);
                r.put("webview", webViewVersion());
            } catch (Throwable e) { r.put("error", String.valueOf(e.getMessage())); }
            call.resolve(r);
        });
    }

    @PluginMethod
    public void open(final PluginCall call) {
        ui(() -> {
            try {
                ensureHolder();
                applyCookieMode(call.getString("cookies", "all")); // V1.23.1
                int id = call.getInt("id", -1);
                String url = call.getString("url", "about:blank");
                boolean js = call.getBoolean("js", true);
                double zoom = call.getDouble("zoom", 1.0);
                boolean desk = call.getBoolean("desktop", false);
                boolean strict = call.getBoolean("strict", false);
                boolean isPriv = call.getBoolean("priv", false);
                httpsOnly.put(id, call.getBoolean("httpsOnly", false));
                readPolicy(call, id); // V1.22.4
                shieldOn.put(id, call.getBoolean("shield", true));
                hintsPref = "chrome".equals(call.getString("hints", "default")) ? "chrome" : "default";
                passkeysPref = call.getBoolean("passkeys", true); // V1.22.5
                try { org.json.JSONArray pk = call.getArray("pkSkip"); if (pk != null) { pkSkip.clear(); for (int pi = 0; pi < pk.length(); pi++) { String ph = pk.optString(pi, ""); if (!ph.isEmpty()) pkSkip.add(ph.toLowerCase()); } } } catch (Throwable ignored) { }
                try { org.json.JSONArray hh = call.getArray("httpHosts"); if (hh != null) { httpHosts.clear(); for (int hi = 0; hi < hh.length(); hi++) { String hs = hh.optString(hi, ""); if (!hs.isEmpty()) httpHosts.add(hs.toLowerCase()); } } } catch (Throwable ignored) { }
                WebView w = views.get(id);
                boolean isolated = true;
                if (w == null) {
                    w = make(id, isPriv, js, zoom, desk, strict);
                    isolated = !Boolean.FALSE.equals(isolatedMap.get(id));
                } else {
                    applySettings(w, id, js, zoom, desk);
                    desktop.put(id, desk);
                    /* V1.18.1: turning Strict off now takes effect on tabs that are already open (before, only new tabs). */
                    strictOf.put(id, Boolean.valueOf(strict)); // V1.22.2
                    try { CookieManager.getInstance().setAcceptThirdPartyCookies(w, thirdOk(Boolean.TRUE.equals(priv.get(id)), strict)); } catch (Throwable ignored) { }
                }
                try {
                    org.json.JSONArray sa = call.getArray("sslHosts"); // V1.22.6
                    if (sa != null) { sslAllow.clear(); for (int si = 0; si < sa.length(); si++) { String sh = sa.optString(si, ""); if (!sh.isEmpty()) sslAllow.add(sh.toLowerCase()); } }
                } catch (Throwable ignored) { }
                fwdStack.remove(id);
                siteCookies(w, id, url); // V1.22.2
                syncHints(w, url); // V1.21.1
                w.loadUrl(url);
                JSObject r = new JSObject();
                r.put("isolated", isolated);
                call.resolve(r);
            } catch (Throwable e) { call.reject("open failed: " + e.getMessage()); }
        });
    }

    @PluginMethod
    public void setBounds(final PluginCall call) {
        ui(() -> {
            try {
                ensureHolder();
                WebView bw = getBridge().getWebView();
                double vw = call.getDouble("vw", 0.0);
                float sc = vw > 0 ? (float) (bw.getWidth() / vw) : getContext().getResources().getDisplayMetrics().density;
                int w = Math.max(1, Math.round((float) (call.getDouble("w", 1.0) * sc)));
                int h = Math.max(1, Math.round((float) (call.getDouble("h", 1.0) * sc)));
                ViewGroup.LayoutParams lp = holder.getLayoutParams();
                boolean resized = lp.width != w || lp.height != h;
                if (resized) { lp.width = w; lp.height = h; holder.setLayoutParams(lp); }
                holder.setTranslationX(bw.getLeft() + (float) (call.getDouble("x", 0.0) * sc));
                holder.setTranslationY(bw.getTop() + (float) (call.getDouble("y", 0.0) * sc));
                // V1.21.7: a page that finishes loading at a transient/odd size (keyboard up, rotation, the address-bar suggestions
                // panel resizing the view) could commit its last frame at that size and never repaint once the holder grew back to
                // its real size, which showed as a blank white page with no error. show() already forced one layout+invalidate right
                // when a tab becomes active; this does the same on every real size change, not only on activation.
                if (resized) {
                    WebView aw = views.get(active);
                    if (aw != null) aw.post(() -> { try { aw.requestLayout(); aw.invalidate(); } catch (Throwable ignored) { } });
                }
                call.resolve();
            } catch (Throwable e) { call.reject("setBounds failed: " + e.getMessage()); }
        });
    }

    @PluginMethod
    public void show(final PluginCall call) {
        ui(() -> {
            ensureHolder();
            int id = call.getInt("id", -1);
            for (Map.Entry<Integer, WebView> e : views.entrySet()) {
                boolean on = e.getKey() == id;
                e.getValue().setVisibility(on ? View.VISIBLE : View.GONE);
                if (on) e.getValue().onResume(); else e.getValue().onPause();
            }
            active = id;
            holder.setVisibility(views.containsKey(id) ? View.VISIBLE : View.GONE);
            // V1.21.6: a page that finished loading while its holder was hidden or 1 px wide can come back white until it is laid out again.
            final WebView aw = views.get(id);
            if (aw != null) {
                try { aw.resumeTimers(); } catch (Throwable ignored) { }
                aw.post(() -> { try { aw.requestLayout(); aw.invalidate(); } catch (Throwable ignored) { } });
            }
            call.resolve();
        });
    }

    @PluginMethod
    public void hide(final PluginCall call) {
        ui(() -> { if (holder != null) holder.setVisibility(View.GONE); call.resolve(); });
    }

    @PluginMethod
    public void close(final PluginCall call) {
        ui(() -> {
            int id = call.getInt("id", -1);
            WebView w = views.remove(id);
            boolean wasPriv = Boolean.TRUE.equals(priv.remove(id));
            mainHost.remove(id); netRetry.remove(id); strictOf.remove(id); desktop.remove(id); httpsOnly.remove(id); shieldOn.remove(id); shieldCount.remove(id); shieldLastEmit.remove(id); shieldLastHost.remove(id); shieldHosts.remove(id); isolatedMap.remove(id); sniffClear(id);
            if (w != null) {
                if (holder != null) holder.removeView(w);
                w.stopLoading(); w.destroy();
            }
            if (active == id) { active = -1; if (holder != null) holder.setVisibility(View.GONE); }
            if (wasPriv && profileInUse && !priv.containsValue(Boolean.TRUE)) dropProfile();
            call.resolve();
        });
    }

    /** V1.21.6: native fallback for the hardware Back button. Called on the UI thread by MainActivity when the page script has nothing left to close. */
    public boolean goBackActive() {
        try {
            if (holder == null || holder.getVisibility() != View.VISIBLE) return false;
            WebView w = views.get(active);
            if (w != null && w.canGoBack()) { fwdPush(active, w.getUrl()); markDir(active, "b"); w.goBack(); return true; }
        } catch (Throwable ignored) { }
        return false;
    }

    @PluginMethod
    public void back(final PluginCall call) {
        ui(() -> { int bid = call.getInt("id", -1); WebView w = views.get(bid); if (w != null && w.canGoBack()) { fwdPush(bid, w.getUrl()); markDir(bid, "b"); w.goBack(); } call.resolve(); });
    }

    @PluginMethod
    public void forward(final PluginCall call) {
        ui(() -> {
            int fid = call.getInt("id", -1);
            WebView w = views.get(fid);
            java.util.concurrent.ConcurrentLinkedDeque<String> fs = fwdStack.get(fid);
            if (w != null) {
                if (w.canGoForward()) { markDir(fid, "f"); w.goForward(); if (fs != null) fs.poll(); }
                else if (fs != null && !fs.isEmpty()) { String nu = fs.poll(); if (nu != null) { markDir(fid, "f"); w.loadUrl(nu); } } // V1.22.6
            }
            call.resolve();
        });
    }

    @PluginMethod
    public void reload(final PluginCall call) {
        ui(() -> { WebView w = views.get(call.getInt("id", -1)); if (w != null) w.reload(); call.resolve(); });
    }

    @PluginMethod
    public void stop(final PluginCall call) {
        ui(() -> { WebView w = views.get(call.getInt("id", -1)); if (w != null) w.stopLoading(); call.resolve(); });
    }

    @PluginMethod
    public void setOptions(final PluginCall call) {
        ui(() -> {
            int id = call.getInt("id", -1);
            WebView w = views.get(id);
            if (w == null) { call.resolve(); return; }
            readPolicy(call, id); // V1.22.4
            boolean desk = call.getBoolean("desktop", false);
            boolean changed = desk != Boolean.TRUE.equals(desktop.get(id));
            applySettings(w, id, call.getBoolean("js", w.getSettings().getJavaScriptEnabled()), call.getDouble("zoom", 1.0), desk);
            desktop.put(id, desk);
            httpsOnly.put(id, call.getBoolean("httpsOnly", Boolean.TRUE.equals(httpsOnly.get(id))));
            shieldOn.put(id, call.getBoolean("shield", !Boolean.FALSE.equals(shieldOn.get(id))));
            if (changed) w.reload();
            call.resolve();
        });
    }

    @PluginMethod
    public void find(final PluginCall call) {
        ui(() -> {
            WebView w = views.get(call.getInt("id", -1));
            String q = call.getString("q", "");
            if (w != null) { if (q.isEmpty()) w.clearMatches(); else w.findAllAsync(q); }
            call.resolve();
        });
    }

    @PluginMethod
    public void findNext(final PluginCall call) {
        ui(() -> {
            WebView w = views.get(call.getInt("id", -1));
            if (w != null) w.findNext(call.getBoolean("forward", true));
            call.resolve();
        });
    }

    @PluginMethod
    public void clearFind(final PluginCall call) {
        ui(() -> { WebView w = views.get(call.getInt("id", -1)); if (w != null) w.clearMatches(); call.resolve(); });
    }

    /**
     * V1.23.0 ROOT CAUSE of "my stuff disappears": WebStorage.deleteAllData() wipes the storage of EVERY origin in the app, including the
     * Capacitor page that holds your bookmarks, collections, notes, settings and the encrypted vault (all in localStorage). Burn and "clear
     * cookies" therefore erased them. Now only websites' origins are deleted; the app's own origins (localhost) are never touched.
     */
    private void wipeSiteStorage() {
        final android.webkit.WebStorage ws = android.webkit.WebStorage.getInstance();
        try {
            ws.getOrigins(new ValueCallback<Map>() {
                @Override public void onReceiveValue(Map m) {
                    try {
                        if (m == null) return;
                        for (Object k : m.keySet()) {
                            String o = String.valueOf(k).toLowerCase();
                            if (o.contains("://localhost") || o.contains("://127.0.0.1") || o.startsWith("capacitor://") || o.startsWith("file://")) continue;
                            try { ws.deleteOrigin(String.valueOf(k)); } catch (Throwable ignored) { }
                        }
                    } catch (Throwable ignored) { }
                }
            });
        } catch (Throwable ignored) { }
    }

    /** V1.23.1: cookie names (never values) that this page can see, for the Cookies panel. */
    @PluginMethod
    public void cookieSite(final PluginCall call) {
        ui(() -> {
            JSObject o = new JSObject();
            try {
                String url = call.getString("url", "");
                CookieManager cm = CookieManager.getInstance();
                String ck = cm.getCookie(url);
                org.json.JSONArray names = new org.json.JSONArray();
                if (ck != null) for (String p : ck.split(";")) {
                    int e = p.indexOf('=');
                    String n = (e < 0 ? p : p.substring(0, e)).trim();
                    if (!n.isEmpty()) names.put(n);
                }
                o.put("names", names);
                o.put("count", names.length());
                o.put("mode", cookieMode);
                o.put("accept", cm.acceptCookie());
            } catch (Throwable e) { o.put("count", 0); }
            call.resolve(o);
        });
    }

    /** V1.23.1: deletes the cookies this site has (root path, host and parent domains). */
    @PluginMethod
    public void cookieClearSite(final PluginCall call) {
        ui(() -> {
            int n = 0;
            try {
                String url = call.getString("url", "");
                CookieManager cm = CookieManager.getInstance();
                String host = Uri.parse(url).getHost();
                String ck = cm.getCookie(url);
                if (host != null && ck != null) {
                    List<String> doms = new ArrayList<>();
                    String h = host;
                    while (true) {
                        doms.add(h);
                        int i = h.indexOf('.');
                        if (i < 0 || h.indexOf('.', i + 1) < 0) break;
                        h = h.substring(i + 1);
                    }
                    String base = (url.startsWith("https") ? "https://" : "http://") + host + "/";
                    String sec = url.startsWith("https") ? "; Secure" : "";
                    String gone = "=; Max-Age=0; Expires=Thu, 01 Jan 1970 00:00:00 GMT; Path=/";
                    for (String p : ck.split(";")) {
                        int e = p.indexOf('=');
                        String name = (e < 0 ? p : p.substring(0, e)).trim();
                        if (name.isEmpty()) continue;
                        n++;
                        cm.setCookie(base, name + gone + sec);
                        for (String d : doms) cm.setCookie(base, name + gone + "; Domain=" + d + sec);
                    }
                }
                cm.flush();
            } catch (Throwable ignored) { }
            JSObject o = new JSObject();
            o.put("cleared", n);
            call.resolve(o);
        });
    }

    @PluginMethod
    public void clearData(final PluginCall call) {
        ui(() -> {
            boolean cookies = call.getBoolean("cookies", true), cache = call.getBoolean("cache", true);
            if (cookies) { CookieManager.getInstance().removeAllCookies(null); CookieManager.getInstance().flush(); wipeSiteStorage(); }
            if (cache) for (WebView w : views.values()) { w.clearCache(true); w.clearFormData(); }
            call.resolve();
        });
    }

    /* ---------- media downloader ---------- */

    /** Lists media on the tab: DOM scan (images, video, audio, pdf links, meta tags) plus sniffed network requests. */
    @PluginMethod
    public void mediaScan(final PluginCall call) {
        ui(() -> {
            final int id = call.getInt("id", -1);
            final WebView w = views.get(id);
            final JSArray net = new JSArray();
            synchronized (sniffed) {
                LinkedHashMap<String, String> mp = sniffed.get(id);
                if (mp != null) for (Map.Entry<String, String> e : mp.entrySet()) {
                    JSObject it = new JSObject();
                    it.put("url", e.getKey()); it.put("kind", e.getValue()); it.put("net", true);
                    net.put(it);
                }
            }
            if (w == null) {
                JSObject r = new JSObject(); r.put("items", new JSArray()); r.put("net", net); call.resolve(r);
                return;
            }
            try {
                w.evaluateJavascript(SCAN_JS, value -> {
                    JSObject r = new JSObject();
                    JSArray items = new JSArray();
                    try {
                        if (value != null && !"null".equals(value)) {
                            Object v = new org.json.JSONTokener(value).nextValue();
                            if (v instanceof String) items = new JSArray((String) v);
                        }
                    } catch (Throwable ignored) { }
                    r.put("items", items);
                    r.put("net", net);
                    r.put("url", w.getUrl());
                    r.put("title", w.getTitle());
                    call.resolve(r);
                });
            } catch (Throwable e) {
                JSObject r = new JSObject(); r.put("items", new JSArray()); r.put("net", net); call.resolve(r);
            }
        });
    }

    /** Save one media URL (http, https or data:) into Downloads/Festers. */
    @PluginMethod
    public void saveUrl(final PluginCall call) {
        final String url = call.getString("url", "");
        final String name = call.getString("name", "");
        final String mime = call.getString("mime", "");
        final String referer = call.getString("referer", "");
        if (url == null || url.isEmpty()) { call.reject("no url"); return; }
        if (url.startsWith("data:")) {
            new Thread(() -> saveDataUrl(call, url, name)).start();
            return;
        }
        JSObject o = new JSObject();
        try {
            if (!(url.startsWith("http://") || url.startsWith("https://"))) throw new Exception("Only http, https and data links can be saved");
            WebView w = views.get(call.getInt("id", -1));
            String ua = w != null ? w.getSettings().getUserAgentString() : null;
            String used = enqueue(url, ua, null, mime, referer, name, call.getBoolean("wifiOnly", false), true);
            o.put("ok", true); o.put("name", used);
        } catch (Throwable e) {
            o.put("ok", false); o.put("desc", e.getMessage() == null ? "Download could not start" : e.getMessage());
        }
        call.resolve(o);
    }

    /** V1.23.6: all downloads, newest first, with live bytes, percentage and speed. */
    @PluginMethod
    public void downloadJobs(PluginCall call) {
        JSObject result = new JSObject();
        result.put("jobs", FBDownloader.get(getContext()).list());
        call.resolve(result);
    }

    /** Starts a download from the page UI (a link the person typed, or retry). */
    @PluginMethod
    public void dlStart(PluginCall call) {
        String url = call.getString("url", "");
        JSObject r = new JSObject();
        if (url == null || !(url.startsWith("http://") || url.startsWith("https://"))) { r.put("ok", false); r.put("desc", "Only http and https links can be downloaded"); call.resolve(r); return; }
        WebView w = views.get(call.getInt("id", -1));
        String ua = w != null ? w.getSettings().getUserAgentString() : null;
        try {
            FBDownloader.Job j = FBDownloader.get(getContext()).start(url, cleanName(call.getString("name", "")), call.getString("mime", ""), ua, call.getString("referer", ""), call.getBoolean("wifiOnly", false), false);
            r.put("ok", true); r.put("id", j.id); r.put("name", j.name);
        } catch (Throwable e) { r.put("ok", false); r.put("desc", e.getMessage() == null ? "Download could not start" : e.getMessage()); }
        call.resolve(r);
    }

    @PluginMethod
    public void dlPause(PluginCall call) {
        JSObject r = new JSObject(); r.put("ok", FBDownloader.get(getContext()).pause(call.getLong("id", -1L))); call.resolve(r);
    }

    @PluginMethod
    public void dlResume(PluginCall call) {
        JSObject r = new JSObject(); r.put("ok", FBDownloader.get(getContext()).resume(call.getLong("id", -1L))); call.resolve(r);
    }

    /** Resumes every download that stopped because the connection dropped. The page calls this when the phone is online again. */
    @PluginMethod
    public void dlResumeNet(PluginCall call) {
        JSObject r = new JSObject(); r.put("n", FBDownloader.get(getContext()).resumeNet()); call.resolve(r);
    }

    @PluginMethod
    public void dlCancel(PluginCall call) {
        JSObject r = new JSObject(); r.put("ok", FBDownloader.get(getContext()).cancel(call.getLong("id", -1L), call.getBoolean("deleteFile", false))); call.resolve(r);
    }

    @PluginMethod
    public void dlClear(PluginCall call) {
        JSObject r = new JSObject(); r.put("n", FBDownloader.get(getContext()).clearFinished()); call.resolve(r);
    }

    /** Cancel/remove a download (old name, same engine). */
    @PluginMethod
    public void cancelDownload(PluginCall call) {
        long id = call.getLong("id", -1L);
        if (id < 0) { call.reject("Invalid download id"); return; }
        JSObject r = new JSObject(); r.put("ok", FBDownloader.get(getContext()).cancel(id, false)); call.resolve(r);
    }

    @PluginMethod
    public void openDownload(PluginCall call) {
        long id = call.getLong("id", -1L);
        String why = FBDownloader.get(getContext()).open(id);
        JSObject r = new JSObject();
        if (why == null) r.put("ok", true); else { r.put("ok", false); r.put("desc", why); }
        call.resolve(r);
    }

    /* ---------- lifecycle ---------- */

    /** V1.14.1: leaves a full-screen video. Returns true if one was showing (so Back is used up). */
    public boolean exitFullscreen() {
        if (customView == null) return false;
        try {
            ViewGroup decor = (ViewGroup) getActivity().getWindow().getDecorView();
            decor.removeView(customView);
            decor.setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
        } catch (Throwable ignored) { }
        customView = null;
        try { if (customCb != null) customCb.onCustomViewHidden(); } catch (Throwable ignored) { }
        customCb = null;
        return true;
    }

    /** V1.14.2: fills the username and password of the login form in the main frame. */
    private static final String FILL_JS =
        "(function(){try{" +
        "var U=__U__,P=__P__;" +
        "function vis(e){var r=e.getBoundingClientRect(),s=getComputedStyle(e);return r.width>0&&r.height>0&&s.visibility!=='hidden'&&s.display!=='none'&&!e.disabled&&!e.readOnly}" +
        "function set(e,v){var d=Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value');e.focus();d.set.call(e,v);e.dispatchEvent(new Event('input',{bubbles:true}));e.dispatchEvent(new Event('change',{bubbles:true}))}" +
        "var pw=[].slice.call(document.querySelectorAll('input[type=password]')).filter(vis)[0];" +
        "var cands=[].slice.call(document.querySelectorAll('input')).filter(function(e){return vis(e)&&/^(text|email|tel|)$/i.test(e.type||'')});" +
        "var un=null;" +
        "if(pw){for(var i=0;i<cands.length;i++){if(cands[i].compareDocumentPosition(pw)&4)un=cands[i]}}" +
        "else{un=cands.filter(function(e){return /user|email|login|id|name/i.test((e.name||'')+(e.id||'')+(e.autocomplete||'')+(e.placeholder||''))})[0]||cands[0]||null}" +
        "var res='none';" +
        "if(un&&U){set(un,U);res='user'}" +
        "if(pw&&P){set(pw,P);res=(un&&U)?'both':'pass'}" +
        "return res}catch(e){return 'error'}})()";

    /** Result of asking Android for web passkey support: shown in Settings > Autofill & Passkeys. */
    public static volatile String passkeyMode = "not asked yet";
    /** V1.14.7: result of asking the WebView not to send the X-Requested-With header (it names this app and makes some sites treat it as an embedded view, not a browser). */
    public static volatile String headerMode = "not asked yet";
    public static volatile String lastRenderGone = "none yet";

    private static String webViewVersion() {
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                android.content.pm.PackageInfo pi = WebView.getCurrentWebViewPackage();
                if (pi != null) return pi.packageName + " " + pi.versionName;
            }
        } catch (Throwable ignored) { }
        return "unknown WebView";
    }

    /** V1.21.5: from the syncing app's WebSheet. Runs before any page script, like real Chrome: window.chrome exists and navigator.webdriver is false. Sites that check these stop serving a blank or "unsupported browser" page. */
    private static final String STEALTH_JS = "(function(){try{if(!window.chrome){window.chrome={app:{isInstalled:false},runtime:{}}}}catch(e){}try{Object.defineProperty(navigator,'webdriver',{get:function(){return false}})}catch(e){}})();";

    private void stealthScript(WebView w) {
        try {
            Class<?> feat = Class.forName("androidx.webkit.WebViewFeature");
            boolean ok = (Boolean) feat.getMethod("isFeatureSupported", String.class).invoke(null, "DOCUMENT_START_SCRIPT");
            if (!ok) return;
            Class<?> wc = Class.forName("androidx.webkit.WebViewCompat");
            wc.getMethod("addDocumentStartJavaScript", WebView.class, String.class, java.util.Set.class)
                .invoke(null, w, STEALTH_JS, java.util.Collections.singleton("*"));
        } catch (Throwable ignored) { }
    }

    private void enablePasskeys(WebView w) { enablePasskeys(w, null); }

    /** V1.23.0: Web passkeys is ON by default and is switched per site: sites that cannot accept an app-mode passkey (pkSkip: Google, GitHub, GitLab by default) get it off so their sign-in does not break; every other site can ask the passkey app (Conduint). */
    private void enablePasskeys(WebView w, String host) {
        if (!passkeysPref) { passkeyMode = "off (default, same as Prodigy). Sites sign in with password or code. Turn on Web passkeys in Settings > Autofill & Passkeys to try app mode"; return; } // V1.22.5: advertising WebAuthn that cannot succeed (app mode, site lists no Festers assetlinks) made Google/GitHub/GitLab try a passkey and fail
        // V1.21.3 left WebAuthn off when CREDENTIAL_MANAGER_SET_ORIGIN was not granted, because browser mode needs that permission.
        // V1.21.6: WebAuthn is no longer left off. With the browser-origin permission Festers asks for browser mode; without it Festers asks for
        // app mode, which needs no special permission, so the passkey prompt (Conduint, Bitwarden, Google) at least appears. In app mode a site
        // only accepts the result if it lists Festers in its assetlinks.json, or if the passkey app trusts Festers (Conduint: CONDUINT_TRUST_FESTERS_SHA256).
        boolean origin = false;
        try { origin = getContext().checkSelfPermission("android.permission.CREDENTIAL_MANAGER_SET_ORIGIN") == android.content.pm.PackageManager.PERMISSION_GRANTED; } catch (Throwable ignored) { }
        try {
            Class<?> feat = Class.forName("androidx.webkit.WebViewFeature");
            boolean ok = (Boolean) feat.getMethod("isFeatureSupported", String.class).invoke(null, "WEB_AUTHENTICATION");
            if (!ok) { passkeyMode = "this WebView does not offer passkey support (" + webViewVersion() + ", Android " + Build.VERSION.SDK_INT + ")"; return; }
            Class<?> sc = Class.forName("androidx.webkit.WebSettingsCompat");
            boolean skip = false;
            if (host != null) { String hl = host.toLowerCase(); for (String a : pkSkip) if (hl.equals(a) || hl.endsWith("." + a)) skip = true; }
            int want = sc.getField(skip ? "WEB_AUTHENTICATION_SUPPORT_NONE" : (origin ? "WEB_AUTHENTICATION_SUPPORT_FOR_BROWSER" : "WEB_AUTHENTICATION_SUPPORT_FOR_APP")).getInt(null);
            sc.getMethod("setWebAuthenticationSupport", WebSettings.class, int.class).invoke(null, w.getSettings(), want);
            int got = want;
            try { got = (Integer) sc.getMethod("getWebAuthenticationSupport", WebSettings.class).invoke(null, w.getSettings()); } catch (Throwable ignored) { }
            String mode = skip ? "off for this site (" + host + ")" : origin ? "browser mode" : "app mode (Android has not given Festers the browser-origin permission)";
            passkeyMode = got == want
                ? "on, " + mode + ", " + webViewVersion() + ". The passkey app must also trust Festers; a site may still refuse it"
                : "asked for " + mode + " but the WebView kept it off (" + webViewVersion() + "). Use Outside (Chrome) for passkey sign-ins";
        } catch (Throwable e) {
            Throwable c = e.getCause() != null ? e.getCause() : e;
            passkeyMode = "not available: " + c.getClass().getSimpleName() + (c.getMessage() != null ? " " + c.getMessage() : "");
        }
    }

    /** V1.16.1: result of making the client hints (Sec-CH-UA, navigator.userAgentData) agree with the cleaned user agent. */
    static volatile String hintsMode = "not tried";
    /** V1.18.1: "default" keeps the WebView's own honest hints; "chrome" presents Chrome-style hints. Applied when a tab's view is created. */
    static volatile String hintsPref = "default";
    static volatile boolean passkeysPref = true; // V1.22.5, default flipped to ON in V1.23.0 (per-site skip list below)
    static final java.util.Set<String> pkSkip = java.util.concurrent.ConcurrentHashMap.newKeySet();
    static volatile String lastBlockedHost = "none yet";
    /** V1.21.1: Google refuses sign-in when the client hints say "Android WebView", so Google's sign-in hosts always get Chrome-style hints. */
    private final Map<WebView, Boolean> hintState = new java.util.WeakHashMap<>();

    private static boolean isGoogleSignIn(String url) {
        try {
            String h = Uri.parse(url).getHost();
            if (h == null) return false;
            h = h.toLowerCase();
            return h.equals("google.com") || h.endsWith(".google.com") || h.endsWith(".google.co.za") || h.equals("youtube.com") || h.endsWith(".youtube.com"); // V1.22.2: every Google host, not only accounts.google.com
        } catch (Throwable t) { return false; }
    }

    private void syncHints(WebView w, String url) {
        if (w == null || url == null) return;
        boolean want = "chrome".equals(hintsPref); // V1.22.5: was || isGoogleSignIn(url); Prodigy does not override hints
        Boolean cur = hintState.get(w);
        if (cur != null && cur.booleanValue() == want) return;
        hintState.put(w, Boolean.valueOf(want));
        if (want) applyChrome(w); else resetHints(w);
    }

    private void resetHints(WebView w) {
        try {
            Class<?> feat = Class.forName("androidx.webkit.WebViewFeature");
            boolean ok = (Boolean) feat.getMethod("isFeatureSupported", String.class).invoke(null, "USER_AGENT_METADATA");
            if (!ok) return;
            Class<?> umB = Class.forName("androidx.webkit.UserAgentMetadata$Builder");
            Object um = umB.getMethod("build").invoke(umB.getConstructor().newInstance());
            Class<?> sc = Class.forName("androidx.webkit.WebSettingsCompat");
            sc.getMethod("setUserAgentMetadata", WebSettings.class, Class.forName("androidx.webkit.UserAgentMetadata")).invoke(null, w.getSettings(), um);
            hintsMode = "WebView default (not overridden)";
        } catch (Throwable ignored) { }
    }

    private void chromeHints(WebView w) {
        if (!"chrome".equals(hintsPref)) { hintsMode = "WebView default (not overridden)"; return; }
        applyChrome(w);
    }

    private void applyChrome(WebView w) {
        try {
            Class<?> feat = Class.forName("androidx.webkit.WebViewFeature");
            boolean ok = (Boolean) feat.getMethod("isFeatureSupported", String.class).invoke(null, "USER_AGENT_METADATA");
            if (!ok) { hintsMode = "this WebView cannot change them"; return; }
            String ua = (w.getTag() instanceof String) ? (String) w.getTag() : w.getSettings().getUserAgentString(); // V1.22.2: base UA keeps the real full version
            java.util.regex.Matcher mt = java.util.regex.Pattern.compile("Chrome/(\\d+)[.\\d]*").matcher(ua == null ? "" : ua);
            if (!mt.find()) { hintsMode = "no Chrome version in the user agent"; return; }
            String major = mt.group(1), full = mt.group(0).substring(7);
            Class<?> bvB = Class.forName("androidx.webkit.UserAgentMetadata$BrandVersion$Builder");
            java.util.List<Object> brands = new java.util.ArrayList<>();
            String[][] bl = { {"Not.A/Brand", "99"}, {"Chromium", major}, {"Google Chrome", major} };
            for (String[] b : bl) {
                Object bb = bvB.getConstructor().newInstance();
                bvB.getMethod("setBrand", String.class).invoke(bb, b[0]);
                bvB.getMethod("setMajorVersion", String.class).invoke(bb, b[1]);
                bvB.getMethod("setFullVersion", String.class).invoke(bb, b[0].startsWith("Not") ? "99.0.0.0" : full);
                brands.add(bvB.getMethod("build").invoke(bb));
            }
            Class<?> umB = Class.forName("androidx.webkit.UserAgentMetadata$Builder");
            Object ub = umB.getConstructor().newInstance();
            umB.getMethod("setBrandVersionList", java.util.List.class).invoke(ub, brands);
            umB.getMethod("setFullVersion", String.class).invoke(ub, full);
            umB.getMethod("setPlatform", String.class).invoke(ub, "Android");
            String pv = Build.VERSION.RELEASE == null ? "14" : Build.VERSION.RELEASE;
            if (pv.indexOf('.') < 0) pv = pv + ".0.0"; else if (pv.indexOf('.', pv.indexOf('.') + 1) < 0) pv = pv + ".0";
            umB.getMethod("setPlatformVersion", String.class).invoke(ub, pv);
            umB.getMethod("setMobile", boolean.class).invoke(ub, true);
            try { umB.getMethod("setModel", String.class).invoke(ub, Build.MODEL == null ? "" : Build.MODEL); } catch (Throwable ignored) { }
            try { umB.getMethod("setArchitecture", String.class).invoke(ub, ""); } catch (Throwable ignored) { }
            try { umB.getMethod("setBitness", int.class).invoke(ub, 64); } catch (Throwable ignored) { }
            try { umB.getMethod("setWow64", boolean.class).invoke(ub, false); } catch (Throwable ignored) { }
            Object um = umB.getMethod("build").invoke(ub);
            Class<?> sc = Class.forName("androidx.webkit.WebSettingsCompat");
            sc.getMethod("setUserAgentMetadata", WebSettings.class, Class.forName("androidx.webkit.UserAgentMetadata")).invoke(null, w.getSettings(), um);
            hintsMode = "Chrome " + major;
        } catch (Throwable e) {
            Throwable c = e.getCause() != null ? e.getCause() : e;
            hintsMode = "not set: " + c.getClass().getSimpleName();
        }
    }

    private void hideRequestedWith(WebView w) {
        try {
            Class<?> feat = Class.forName("androidx.webkit.WebViewFeature");
            boolean ok = (Boolean) feat.getMethod("isFeatureSupported", String.class).invoke(null, "REQUESTED_WITH_HEADER_ALLOW_LIST");
            if (!ok) { headerMode = "this WebView cannot hide it"; return; }
            Class<?> sc = Class.forName("androidx.webkit.WebSettingsCompat");
            sc.getMethod("setRequestedWithHeaderOriginAllowList", WebSettings.class, java.util.Set.class)
                .invoke(null, w.getSettings(), new java.util.HashSet<String>());
            headerMode = "hidden from all sites";
        } catch (Throwable e) {
            Throwable c = e.getCause() != null ? e.getCause() : e;
            headerMode = "not hidden: " + c.getClass().getSimpleName();
        }
    }

    private static final String PROBE_JS =
        "(function(){try{" +
        "function vis(e){var r=e.getBoundingClientRect(),s=getComputedStyle(e);return r.width>0&&r.height>0&&s.visibility!=='hidden'&&s.display!=='none'&&!e.disabled&&!e.readOnly}" +
        "function set(e,v){var d=Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value');e.focus();d.set.call(e,v);e.dispatchEvent(new Event('input',{bubbles:true}));e.dispatchEvent(new Event('change',{bubbles:true}))}" +
        "function info(e){return (e.autocomplete||'')+' '+(e.name||'')+' '+(e.id||'')+' '+(e.placeholder||'')+' '+(e.getAttribute('aria-label')||'')}" +
        "var OTP=/one-time-code|otp|totp|2fa|mfa|verification|security.?code|auth(entication)?.?code|passcode|token/i;" +
        "function otps(){return [].slice.call(document.querySelectorAll('input')).filter(function(e){return vis(e)&&/^(text|tel|number|)$/i.test(e.type||'')&&OTP.test(info(e))})}" +
        "var pw=[].slice.call(document.querySelectorAll('input[type=password]')).filter(vis).length>0;" +
        "var o=otps().length>0;" +
        "var u=[].slice.call(document.querySelectorAll('input')).filter(function(e){return vis(e)&&/user|email|login/i.test(info(e)+' '+(e.type||''))}).length>0;" +
        "var em=[].slice.call(document.querySelectorAll('input')).filter(function(e){return vis(e)&&(/^email$/i.test(e.type||'')||/e-?mail/i.test(info(e)))}).length>0;" +
        "var fp='',fh='';try{var f0=document.querySelector('input[type=password]')||document.querySelector('input[type=email]');var fm=f0&&f0.form;if(fm){var a=new URL(fm.getAttribute('action')||location.href,location.href);fp=a.protocol;fh=a.hostname}}catch(x){}" +
        "return {pw:pw,otp:o&&!pw,user:u,email:em,fproto:fp,fhost:fh,title:(document.title||'').slice(0,120)}}catch(e){return {}}})()";

    private static final String CODE_JS =
        "(function(){try{var C=__C__;" +
        "function vis(e){var r=e.getBoundingClientRect(),s=getComputedStyle(e);return r.width>0&&r.height>0&&s.visibility!=='hidden'&&s.display!=='none'&&!e.disabled&&!e.readOnly}" +
        "function set(e,v){var d=Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value');e.focus();d.set.call(e,v);e.dispatchEvent(new Event('input',{bubbles:true}));e.dispatchEvent(new Event('change',{bubbles:true}))}" +
        "function info(e){return (e.autocomplete||'')+' '+(e.name||'')+' '+(e.id||'')+' '+(e.placeholder||'')+' '+(e.getAttribute('aria-label')||'')}" +
        "var OTP=/one-time-code|otp|totp|2fa|mfa|verification|security.?code|auth(entication)?.?code|passcode|token/i;" +
        "function otps(){return [].slice.call(document.querySelectorAll('input')).filter(function(e){return vis(e)&&/^(text|tel|number|)$/i.test(e.type||'')&&OTP.test(info(e))})}" +
        "var c=otps();if(!c.length)return 'none';" +
        "var boxes=c.filter(function(e){return e.maxLength===1});" +
        "if(C.length>1&&boxes.length>=C.length){for(var i=0;i<C.length;i++)set(boxes[i],C.charAt(i));return 'ok'}" +
        "set(c[0],C);return 'ok'}catch(e){return 'error'}})()";

    /** V1.15.0: watches the main frame for a sign-in being sent (form submit, a button tap or Enter while a password box has text) and reports the pair once. Runs only on https pages in normal tabs. */
    private static final String CAP_JS =
        "(function(){try{if(window.__fbcap)return;window.__fbcap=1;var lastP='',lastT=0;" +
        "function vis(e){var r=e.getBoundingClientRect(),s=getComputedStyle(e);return r.width>0&&r.height>0&&s.visibility!=='hidden'&&s.display!=='none'}" +
        "function pick(){var all=[].slice.call(document.querySelectorAll('input[type=password]')).filter(function(e){return e.value&&e.value.length<=256&&vis(e)});if(!all.length)return null;" +
        "var c={},best=null,i;for(i=0;i<all.length;i++){c[all[i].value]=(c[all[i].value]||0)+1}" +
        "for(i=0;i<all.length;i++){if(c[all[i].value]>=2){best=all[i];break}}" +
        "if(!best){if(all.length===1)best=all[0];else return null}" +
        "var scope=best.form||document,ins=[].slice.call(scope.querySelectorAll('input')).filter(function(e){return e!==best&&vis(e)&&/^(text|email|tel|)$/i.test(e.type||'')&&e.value}),u='',k;" +
        "for(i=0;i<ins.length;i++){if(ins[i].compareDocumentPosition(best)&4){k=ins[i];if(/user|email|login|mail|name/i.test((ins[i].name||'')+' '+(ins[i].id||'')+' '+(ins[i].autocomplete||'')+' '+(ins[i].type||''))){u=ins[i].value;k=null;break}}}" +
        "if(!u&&k)u=k.value;" +
        "return {u:String(u).slice(0,200),p:best.value}}" +
        "function send(){try{var x=pick();if(!x||typeof FBCred==='undefined')return;var now=Date.now();if(x.p===lastP&&now-lastT<4000)return;lastP=x.p;lastT=now;FBCred.cap(x.u,x.p)}catch(e){}}" +
        "document.addEventListener('submit',send,true);" +
        "document.addEventListener('click',function(e){var t=e.target&&e.target.closest&&e.target.closest('button,input[type=submit],input[type=button],[role=button],a');if(t)send()},true);" +
        "document.addEventListener('keydown',function(e){if(e.key==='Enter'&&e.target&&e.target.tagName==='INPUT')send()},true);" +
        "}catch(e){}})()";

    /** V1.15.0: receives the pair from CAP_JS. The URL is read from the WebView itself, never from the page, and only https pages in normal tabs are reported. Android runs this on a background thread, so everything goes through ui(). */
    /* V1.21.7: DownloadListener (above, in make()) only fires for a real navigation to a download URL. Most sites today build the
       file in JavaScript with new Blob([...]) + URL.createObjectURL + a hidden <a download> click, which never navigates and so
       never reaches DownloadListener at all — that is the "nothing happens" bug. blobCaptureScript patches that pattern at
       document-start so the page's own click still works normally, but the bytes are also handed to Java and saved for real. */
    private class BlobBridge {
        private final int wid;
        private long lastAt = 0;

        BlobBridge(int wid) { this.wid = wid; }

        @android.webkit.JavascriptInterface
        public void save(final String nameIn, final String mimeIn, final String b64) {
            if (b64 == null || b64.isEmpty() || b64.length() > 180_000_000) return; // ~135MB decoded ceiling, keeps the bridge sane
            long now = System.currentTimeMillis();
            if (now - lastAt < 150) return; // one real save per rapid-fire burst (some pages click several hidden anchors at once)
            lastAt = now;
            new Thread(() -> {
                JSObject o = new JSObject();
                o.put("id", wid);
                try {
                    byte[] bytes = Base64.decode(b64, Base64.DEFAULT);
                    String mime = (mimeIn == null || mimeIn.isEmpty()) ? "application/octet-stream" : mimeIn;
                    String name = cleanName(nameIn);
                    if (name.isEmpty()) name = "festers-" + System.currentTimeMillis();
                    if (name.indexOf('.') < 0) {
                        String ex = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime);
                        if (ex != null) name = name + "." + ex;
                    }
                    saveBytes(name, mime, bytes);
                    o.put("ok", true); o.put("name", name);
                } catch (Throwable e) {
                    o.put("ok", false); o.put("name", ""); o.put("desc", "Could not save: " + (e.getMessage() == null ? "download failed" : e.getMessage()));
                }
                emit("download", o);
            }).start();
        }
    }

    private static final String BLOB_CAPTURE_JS =
        "(function(){try{\n" +
        "if(window.__fbBlobHook)return;window.__fbBlobHook=true;\n" +
        "var urlMap=new WeakMap();\n" +
        "var nativeCreate=URL.createObjectURL.bind(URL);\n" +
        "URL.createObjectURL=function(obj){var u=nativeCreate(obj);try{if(obj instanceof Blob&&obj.size<=52428800){urlMap.set(obj,u);window.__fbBlobs=window.__fbBlobs||{};var ord=window.__fbBlobOrd=window.__fbBlobOrd||[];window.__fbBlobs[u]=obj;ord.push(u);while(ord.length>12){delete window.__fbBlobs[ord.shift()]}}}catch(e){}return u};\n" +
        "function b64FromBlob(blob,cb){try{var r=new FileReader();r.onload=function(){try{var s=String(r.result||'');var i=s.indexOf(',');cb(i>=0?s.slice(i+1):'')}catch(e){cb('')}};r.onerror=function(){cb('')};r.readAsDataURL(blob)}catch(e){cb('')}}\n" +
        "function handleAnchor(a){\n" +
        " try{\n" +
        "  var href=a.href||'';\n" +
        "  if(!/^blob:/i.test(href))return false;\n" +
        "  var blob=(window.__fbBlobs||{})[href];\n" +
        "  if(!(blob instanceof Blob))return false;\n" +
        "  var name=a.download||'';\n" +
        "  b64FromBlob(blob,function(b64){try{if(window.FBBlob&&b64)window.FBBlob.save(name,blob.type||'',b64)}catch(e){}});\n" +
        "  return true;\n" +
        " }catch(e){return false}\n" +
        "}\n" +
        "document.addEventListener('click',function(e){\n" +
        " var a=e.target&&e.target.closest?e.target.closest('a[download],a[href^=\"blob:\"]'):null;\n" +
        " if(a)handleAnchor(a);\n" +
        "},true);\n" +
        "var nativeClick=HTMLAnchorElement.prototype.click;\n" +
        "HTMLAnchorElement.prototype.click=function(){try{handleAnchor(this)}catch(e){}return nativeClick.apply(this,arguments)};\n" +
        "}catch(e){}})();";

    private void blobCaptureScript(WebView w) {
        try {
            Class<?> feat = Class.forName("androidx.webkit.WebViewFeature");
            boolean ok = (Boolean) feat.getMethod("isFeatureSupported", String.class).invoke(null, "DOCUMENT_START_SCRIPT");
            if (!ok) return;
            Class<?> wc = Class.forName("androidx.webkit.WebViewCompat");
            wc.getMethod("addDocumentStartJavaScript", WebView.class, String.class, java.util.Set.class)
                .invoke(null, w, BLOB_CAPTURE_JS, java.util.Collections.singleton("*"));
        } catch (Throwable ignored) { }
    }

    /* V1.23.2: Conduint passkey bridge. Android never gives a sideloaded browser the browser-origin permission, so Credential Manager cannot tell
       Conduint which website is asking. Festers therefore starts Conduint's FestersBridgeActivity directly with the page origin (read here in Java from
       the main-frame URL, never from the page) and hands the answer back to the page as a normal WebAuthn credential. Conduint pins Festers' signing
       certificate the first time (one confirmation) and refuses any other caller. Where the bridge does not apply (burner tab, site on the passkey skip
       list, Web passkeys off, not https, Conduint missing) the page's own navigator.credentials runs as before. */
    static final int RC_CONDUINT = 7100;
    private static volatile WebView pkView;
    private static volatile String pkCb;
    private static volatile long pkAt = 0;

    private static final String PK_SHIM_JS =
        "(function () {\n" +
        "  try {\n" +
        "    if (window.__cndPkInstalled || window.top !== window || !window.FestersPasskey || !navigator.credentials) return;\n" +
        "    window.__cndPkInstalled = true;\n" +
        "    var pending = {}, n = 0;\n" +
        "    function bytes(x) { return x instanceof ArrayBuffer ? new Uint8Array(x) : new Uint8Array(x.buffer, x.byteOffset, x.byteLength); }\n" +
        "    function b64u(x) { var b = bytes(x), s = ''; for (var i = 0; i < b.length; i++) s += String.fromCharCode(b[i]); return btoa(s).replace(/\\+/g, '-').replace(/\\//g, '_').replace(/=+$/, ''); }\n" +
        "    function unb64u(s) { s = s.replace(/-/g, '+').replace(/_/g, '/'); while (s.length % 4) s += '='; var r = atob(s), b = new Uint8Array(r.length); for (var i = 0; i < r.length; i++) b[i] = r.charCodeAt(i); return b.buffer; }\n" +
        "    function idList(l) { return (l || []).map(function (c) { return { type: c.type, id: b64u(c.id), transports: c.transports }; }); }\n" +
        "    window.__cndPk = function (id, json, err) { var p = pending[id]; delete pending[id]; if (!p) return; if (err === 'FALLBACK') p.fb(); else if (err) p.rej(new DOMException(err, 'NotAllowedError')); else { try { p.res(JSON.parse(json)); } catch (e) { p.rej(new DOMException('Conduint answered with something unreadable.', 'NotAllowedError')); } } };\n" +
        "    function call(op, req, build, fallback) {\n" +
        "      return new Promise(function (res, rej) {\n" +
        "        var id = 'c' + (++n);\n" +
        "        pending[id] = { res: function (c) { try { res(build(c)); } catch (e) { rej(e); } }, rej: rej, fb: function () { fallback().then(res, rej); } };\n" +
        "        try { window.FestersPasskey.passkey(id, op, JSON.stringify(req)); } catch (e) { delete pending[id]; fallback().then(res, rej); }\n" +
        "      });\n" +
        "    }\n" +
        "    function cred(c, extra) {\n" +
        "      var r = c.response, resp = { clientDataJSON: unb64u(r.clientDataJSON) };\n" +
        "      for (var k in extra) resp[k] = extra[k](r);\n" +
        "      return { id: c.id, rawId: unb64u(c.rawId), type: 'public-key', authenticatorAttachment: 'platform', response: resp, getClientExtensionResults: function () { return {}; } };\n" +
        "    }\n" +
        "    var orig = navigator.credentials;\n" +
        "    var shim = {\n" +
        "      create: function (o) {\n" +
        "        if (!o || !o.publicKey) return orig.create.call(orig, o);\n" +
        "        var p = o.publicKey;\n" +
        "        var req = { challenge: b64u(p.challenge), rp: p.rp, user: { id: b64u(p.user.id), name: p.user.name, displayName: p.user.displayName }, pubKeyCredParams: p.pubKeyCredParams, excludeCredentials: idList(p.excludeCredentials) };\n" +
        "        return call('create', req, function (c) {\n" +
        "          var x = cred(c, { attestationObject: function (r) { return unb64u(r.attestationObject); } });\n" +
        "          x.response.getTransports = function () { return ['internal']; };\n" +
        "          x.response.getAuthenticatorData = function () { return unb64u(c.response.authenticatorData); };\n" +
        "          x.response.getPublicKey = function () { return unb64u(c.response.publicKey); };\n" +
        "          x.response.getPublicKeyAlgorithm = function () { return -7; };\n" +
        "          return x;\n" +
        "        }, function () { return orig.create.call(orig, o); });\n" +
        "      },\n" +
        "      get: function (o) {\n" +
        "        if (!o || !o.publicKey || o.mediation === 'conditional') return orig.get.call(orig, o);\n" +
        "        var p = o.publicKey;\n" +
        "        var req = { challenge: b64u(p.challenge), rpId: p.rpId || location.hostname, allowCredentials: idList(p.allowCredentials) };\n" +
        "        return call('get', req, function (c) {\n" +
        "          return cred(c, { authenticatorData: function (r) { return unb64u(r.authenticatorData); }, signature: function (r) { return unb64u(r.signature); }, userHandle: function (r) { return r.userHandle ? unb64u(r.userHandle) : null; } });\n" +
        "        }, function () { return orig.get.call(orig, o); });\n" +
        "      },\n" +
        "      store: function (c) { return orig.store(c); },\n" +
        "      preventSilentAccess: function () { return orig.preventSilentAccess ? orig.preventSilentAccess() : Promise.resolve(); }\n" +
        "    };\n" +
        "    try { Object.defineProperty(navigator, 'credentials', { value: shim, configurable: true }); } catch (e) { navigator.credentials = shim; }\n" +
        "  } catch (e) {}\n" +
        "})();\n";

    private void pkDeliver(WebView w, String id, String json, String err) {
        try {
            String js = "window.__cndPk&&window.__cndPk(" + org.json.JSONObject.quote(id) + "," + (json == null ? "null" : org.json.JSONObject.quote(json)) + "," + (err == null ? "null" : org.json.JSONObject.quote(err)) + ")";
            w.evaluateJavascript(js, null);
        } catch (Throwable ignored) { }
    }

    /** Called by MainActivity.onActivityResult for RC_CONDUINT. */
    public void pkResult(int result, android.content.Intent data) {
        final WebView w = pkView; final String id = pkCb;
        pkView = null; pkCb = null;
        if (w == null || id == null) return;
        String resp = data == null ? null : data.getStringExtra("responseJson");
        if (result == android.app.Activity.RESULT_OK && resp != null) pkDeliver(w, id, resp, null);
        else { String er = data == null ? null : data.getStringExtra("error"); pkDeliver(w, id, null, er == null ? "Cancelled." : er); }
    }

    private class PkBridge {
        private final int wid;
        private final WebView wv;
        PkBridge(int wid, WebView wv) { this.wid = wid; this.wv = wv; }

        @android.webkit.JavascriptInterface
        public void passkey(final String cbId, final String op, final String requestJson) {
            if (cbId == null || op == null || requestJson == null || requestJson.length() > 200000) return;
            ui(() -> {
                try {
                    if (!("get".equals(op) || "create".equals(op))) { pkDeliver(wv, cbId, null, "FALLBACK"); return; }
                    String url = wv.getUrl();
                    Uri u = url == null ? null : Uri.parse(url);
                    String host = u == null ? null : u.getHost();
                    boolean skip = Boolean.TRUE.equals(priv.get(wid)) || !passkeysPref || u == null || host == null || !"https".equals(u.getScheme());
                    if (!skip) { String hl = host.toLowerCase(); for (String a : pkSkip) if (hl.equals(a) || hl.endsWith("." + a)) skip = true; }
                    if (skip) { pkDeliver(wv, cbId, null, "FALLBACK"); return; }
                    if (pkCb != null && System.currentTimeMillis() - pkAt < 120000) { pkDeliver(wv, cbId, null, "Another passkey request is still open."); return; }
                    String origin = "https://" + host + (u.getPort() > 0 ? ":" + u.getPort() : "");
                    android.content.Intent i = new android.content.Intent();
                    i.setComponent(new android.content.ComponentName("com.fezzy.conduint", "com.fezzy.conduint.FestersBridgeActivity"));
                    i.putExtra("op", op);
                    i.putExtra("origin", origin);
                    i.putExtra("requestJson", requestJson);
                    pkView = wv; pkCb = cbId; pkAt = System.currentTimeMillis();
                    try {
                        getActivity().startActivityForResult(i, RC_CONDUINT); // no NEW_TASK: Conduint checks getCallingPackage()
                    } catch (Throwable t) {
                        pkView = null; pkCb = null;
                        pkDeliver(wv, cbId, null, "FALLBACK"); // Conduint missing or too old: use the normal path
                    }
                } catch (Throwable t) {
                    pkDeliver(wv, cbId, null, "FALLBACK");
                }
            });
        }
    }

    private void pkShimScript(WebView w) {
        try {
            Class<?> feat = Class.forName("androidx.webkit.WebViewFeature");
            boolean ok = (Boolean) feat.getMethod("isFeatureSupported", String.class).invoke(null, "DOCUMENT_START_SCRIPT");
            if (!ok) return;
            Class<?> wc = Class.forName("androidx.webkit.WebViewCompat");
            wc.getMethod("addDocumentStartJavaScript", WebView.class, String.class, java.util.Set.class)
                .invoke(null, w, PK_SHIM_JS, java.util.Collections.singleton("*"));
        } catch (Throwable ignored) { }
    }

    private class CredBridge {
        private final int wid;
        private final WebView wv;
        private long lastAt = 0;

        CredBridge(int wid, WebView wv) { this.wid = wid; this.wv = wv; }

        @android.webkit.JavascriptInterface
        public void cap(final String user, final String pass) {
            if (pass == null || pass.length() == 0 || pass.length() > 256) return;
            final String u = user == null ? "" : (user.length() > 200 ? user.substring(0, 200) : user);
            long now = System.currentTimeMillis();
            if (now - lastAt < 1200) return;
            lastAt = now;
            ui(() -> {
                try {
                    if (Boolean.TRUE.equals(priv.get(wid))) return;
                    String url = wv.getUrl();
                    if (url == null || !url.startsWith("https://")) return;
                    JSObject o = new JSObject();
                    o.put("id", wid); o.put("url", url); o.put("user", u); o.put("pass", pass);
                    emit("cred", o);
                } catch (Throwable ignored) { }
            });
        }
    }

    /** V1.14.3: is there a login form or a one-time-code box on this page (main frame, visible only). */
    @PluginMethod
    public void probeForm(final PluginCall call) {
        ui(() -> {
            final JSObject r = new JSObject();
            WebView w = views.get(call.getInt("id", -1));
            if (w == null) { call.resolve(r); return; }
            try {
                w.evaluateJavascript(PROBE_JS, value -> {
                    JSObject o = new JSObject();
                    try { if (value != null && value.startsWith("{")) o = new JSObject(value); } catch (Throwable ignored) { }
                    o.put("url", w.getUrl());
                    call.resolve(o);
                });
            } catch (Throwable e) { call.resolve(r); }
        });
    }

    /** Fills a one-time code (digits only) into the page's code box. Same https and host check as fillLogin. */
    @PluginMethod
    public void fillCode(final PluginCall call) {
        ui(() -> {
            JSObject r = new JSObject();
            WebView w = views.get(call.getInt("id", -1));
            String site = String.valueOf(call.getString("site", "")).toLowerCase();
            String code = String.valueOf(call.getString("code", ""));
            String url = w == null ? null : w.getUrl();
            String h = "";
            try { h = Uri.parse(url == null ? "" : url).getHost(); if (h == null) h = ""; } catch (Throwable ignored) { }
            h = h.toLowerCase();
            if (h.startsWith("www.")) h = h.substring(4);
            if (site.startsWith("www.")) site = site.substring(4);
            if (w == null || url == null || !url.startsWith("https://") || site.isEmpty() || !(h.equals(site) || h.endsWith("." + site))) {
                r.put("ok", false); r.put("result", "mismatch"); call.resolve(r); return;
            }
            if (!code.matches("[0-9]{4,10}")) { r.put("ok", false); r.put("result", "error"); call.resolve(r); return; }
            try {
                w.evaluateJavascript(CODE_JS.replace("__C__", org.json.JSONObject.quote(code)), value -> {
                    JSObject o = new JSObject();
                    o.put("ok", true);
                    o.put("result", value == null ? "" : value.replace("\"", ""));
                    call.resolve(o);
                });
            } catch (Throwable e) { r.put("ok", false); r.put("result", "error"); call.resolve(r); }
        });
    }

    /** Fills a saved login. Only runs on https pages whose host matches the site the login was saved for. */
    @PluginMethod
    public void fillLogin(final PluginCall call) {
        ui(() -> {
            JSObject r = new JSObject();
            WebView w = views.get(call.getInt("id", -1));
            String site = String.valueOf(call.getString("site", "")).toLowerCase();
            String url = w == null ? null : w.getUrl();
            String h = "";
            try { h = Uri.parse(url == null ? "" : url).getHost(); if (h == null) h = ""; } catch (Throwable ignored) { }
            h = h.toLowerCase();
            if (h.startsWith("www.")) h = h.substring(4);
            if (site.startsWith("www.")) site = site.substring(4);
            if (w == null || url == null || !url.startsWith("https://") || site.isEmpty() || !(h.equals(site) || h.endsWith("." + site))) {
                r.put("ok", false); r.put("result", "mismatch"); call.resolve(r); return;
            }
            String code = FILL_JS
                .replace("__U__", org.json.JSONObject.quote(call.getString("user", "")))
                .replace("__P__", org.json.JSONObject.quote(call.getString("pass", "")));
            try {
                w.evaluateJavascript(code, value -> {
                    JSObject o = new JSObject();
                    o.put("ok", true);
                    o.put("result", value == null ? "" : value.replace("\"", ""));
                    call.resolve(o);
                });
            } catch (Throwable e) { r.put("ok", false); r.put("result", "error"); call.resolve(r); }
        });
    }

    @Override
    protected void handleOnPause() {
        // V1.14.1: a foreground service keeps the process (and every open tab) alive while you are away.
        try { if (getActivity() != null && !getActivity().isFinishing()) FBKeepAliveService.startAuto(getContext()); } catch (Throwable ignored) { }
        for (WebView w : views.values()) w.onPause();
    }

    @Override
    protected void handleOnResume() {
        FBKeepAliveService.stopAuto(getContext());
        WebView w = views.get(active);
        if (w != null) w.onResume();
    }

    @Override
    protected void handleOnDestroy() {
        try {
            if (vpnCb != null) ((ConnectivityManager) getContext().getSystemService(Context.CONNECTIVITY_SERVICE)).unregisterNetworkCallback(vpnCb);
        } catch (Throwable ignored) { }
        for (WebView w : views.values()) { w.stopLoading(); w.destroy(); }
        views.clear();
        synchronized (sniffed) { sniffed.clear(); }
    }
}
