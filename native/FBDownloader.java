package com.fezzy.festersbrowser;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.media.MediaScannerConnection;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.PowerManager;
import android.provider.MediaStore;
import android.webkit.CookieManager;
import android.webkit.MimeTypeMap;
import android.webkit.URLUtil;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * V1.23.6: Festers' own download engine. One real downloader for every http(s) download.
 * - Streams into a private .part file, so a stopped download resumes from the byte where it stopped (HTTP Range).
 * - Follows redirects itself (http to https too), sends the page's cookies, user agent and referer, keeps Set-Cookie answers.
 * - Reports bytes, total, percentage and speed (event "dlprogress"), shows an Android progress notification, then "Download complete".
 * - Pause, resume, cancel, retry after a lost connection, up to three downloads at once, jobs survive an app restart.
 * - When finished, the file is saved into the phone's public Downloads/Festers folder (MediaStore on Android 10+, plain file below).
 */
public class FBDownloader {

    public interface Sink { void on(JSObject job, String kind); } // kind: start | progress | done | fail | state

    private static final class Stop extends Exception {
        final boolean cancel;
        Stop(boolean cancel) { super(cancel ? "cancelled" : "paused"); this.cancel = cancel; }
    }

    private static final class HttpEx extends IOException {
        final int code;
        HttpEx(int code, String msg) { super(msg); this.code = code; }
    }

    static final class Job {
        long id;
        String url = "", name = "", mime = "", ua = "", referer = "", state = "queued", error = "", uri = "", path = "";
        long bytes, total = -1, speed, time;
        boolean wifiOnly, nameFixed, netPaused, quiet;
        volatile boolean cancel, pause;
        long lastEmit, lastNote, lastSave, lastBytes, lastSample;
    }

    private static FBDownloader inst;

    public static synchronized FBDownloader get(Context c) {
        if (inst == null) inst = new FBDownloader(c.getApplicationContext());
        return inst;
    }

    private final Context ctx;
    private final SharedPreferences sp;
    private final ExecutorService pool = Executors.newFixedThreadPool(3);
    private final LinkedHashMap<Long, Job> jobs = new LinkedHashMap<>();
    private volatile Sink sink;
    private PowerManager.WakeLock wl;
    private int active = 0;
    private long nextId = 1;

    private FBDownloader(Context c) {
        ctx = c;
        sp = c.getSharedPreferences("fb_dl2", Context.MODE_PRIVATE);
        nextId = sp.getLong("next", 1);
        try {
            JSONArray a = new JSONArray(sp.getString("jobs", "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                Job j = new Job();
                j.id = o.optLong("id");
                j.url = o.optString("url"); j.name = o.optString("name"); j.mime = o.optString("mime");
                j.ua = o.optString("ua"); j.referer = o.optString("referer");
                j.state = o.optString("state", "failed"); j.error = o.optString("error"); j.uri = o.optString("uri"); j.path = o.optString("path");
                j.bytes = o.optLong("bytes"); j.total = o.optLong("total", -1); j.time = o.optLong("time");
                j.wifiOnly = o.optBoolean("wifiOnly"); j.nameFixed = o.optBoolean("nameFixed");
                if ("downloading".equals(j.state) || "queued".equals(j.state) || "saving".equals(j.state)) {
                    j.state = "paused"; j.error = "Interrupted when the app closed. Tap resume."; j.netPaused = true;
                }
                if (j.id > 0 && j.url.length() > 0) jobs.put(j.id, j);
            }
        } catch (Throwable ignored) { }
    }

    public void setSink(Sink s) { sink = s; }

    /* ---------- public API ---------- */

    public Job start(String url, String name, String mime, String ua, String referer, boolean wifiOnly) {
        return start(url, name, mime, ua, referer, wifiOnly, false);
    }

    public synchronized Job start(String url, String name, String mime, String ua, String referer, boolean wifiOnly, boolean quiet) {
        for (Job x : jobs.values()) {
            // the same link tapped twice (or announced twice by the WebView) must not make two downloads
            if (x.url.equals(url) && ("queued".equals(x.state) || "downloading".equals(x.state) || "saving".equals(x.state))) return x;
        }
        Job j = new Job();
        j.id = nextId++;
        sp.edit().putLong("next", nextId).apply();
        j.url = url; j.name = cleanName(name); j.mime = mime == null ? "" : mime;
        j.ua = ua == null ? "" : ua; j.referer = referer == null ? "" : referer;
        j.quiet = quiet; j.wifiOnly = wifiOnly; j.nameFixed = j.name.length() > 0; j.time = System.currentTimeMillis();
        if (j.name.length() == 0) j.name = cleanName(URLUtil.guessFileName(url, null, j.mime.length() == 0 ? null : j.mime));
        jobs.put(j.id, j);
        persist();
        emit(j, "start");
        submit(j);
        return j;
    }

    public synchronized JSArray list() {
        JSArray out = new JSArray();
        List<Job> all = new ArrayList<>(jobs.values());
        for (int i = all.size() - 1; i >= 0; i--) out.put(toJson(all.get(i)));
        return out;
    }

    public synchronized boolean pause(long id) {
        Job j = jobs.get(id);
        if (j == null) return false;
        if ("downloading".equals(j.state) || "queued".equals(j.state)) {
            j.pause = true; j.netPaused = false;
            if ("queued".equals(j.state)) { setState(j, "paused", "Paused"); }
            return true;
        }
        return false;
    }

    public synchronized boolean resume(long id) {
        Job j = jobs.get(id);
        if (j == null) return false;
        if ("paused".equals(j.state) || "failed".equals(j.state)) {
            j.pause = false; j.cancel = false; j.error = ""; j.netPaused = false;
            setState(j, "queued", "");
            submit(j);
            return true;
        }
        return false;
    }

    /** Resumes every job that stopped because the connection dropped or the app closed. Called when the phone is online again. */
    public synchronized int resumeNet() {
        int n = 0;
        for (Job j : new ArrayList<>(jobs.values())) {
            if ("paused".equals(j.state) && j.netPaused) { resume(j.id); n++; }
        }
        return n;
    }

    public synchronized boolean cancel(long id, boolean deleteFile) {
        Job j = jobs.get(id);
        if (j == null) return false;
        if ("downloading".equals(j.state) || "queued".equals(j.state) || "saving".equals(j.state)) {
            j.cancel = true;
            if ("queued".equals(j.state)) { removeNow(j, true); }
            return true;
        }
        removeNow(j, true);
        if (deleteFile && j.uri.length() > 0) deletePublic(j);
        return true;
    }

    public synchronized int clearFinished() {
        int n = 0;
        for (Job j : new ArrayList<>(jobs.values())) {
            if ("completed".equals(j.state) || "failed".equals(j.state)) { jobs.remove(j.id); keepDelete(j); n++; }
        }
        persist();
        return n;
    }

    /** Returns null when a viewer was started, or a short reason. */
    public String open(long id) {
        Job j;
        synchronized (this) { j = jobs.get(id); }
        if (j == null) return "That download is no longer in the list";
        if (!"completed".equals(j.state)) return "The download is not finished yet";
        Uri target = null;
        try { if (j.uri.length() > 0) target = Uri.parse(j.uri); } catch (Throwable ignored) { }
        String mt0 = j.mime.length() == 0 ? mimeByName(j.path.length() > 0 ? j.path : j.name) : j.mime;
        { // V1.26.0: the browser's own copy first; it never goes missing when Android's Downloads entry does
            Uri kept = keepUri(j);
            if (kept != null && viewUri(kept, mt0)) return null;
        }
        if (target != null) { // V1.25.0: just try the saved link; do not pre-check it (some phones refuse the check on a file that is there)
            try {
                Intent it0 = new Intent(Intent.ACTION_VIEW);
                it0.setDataAndType(target, mt0);
                it0.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
                ctx.startActivity(it0);
                return null;
            } catch (android.content.ActivityNotFoundException e0) { return "No app can open this file";
            } catch (Throwable ignored) { }
        }
        target = resolveSaved(j);
        if (target == null) {
            // V1.24.0: could not match the file by any route. Do not just say "gone": open the Downloads app so the person can find it, and say what was looked for.
            String shown = j.path.length() > 0 ? j.path : j.name;
            try {
                Intent dl = new Intent(android.app.DownloadManager.ACTION_VIEW_DOWNLOADS);
                dl.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                ctx.startActivity(dl);
                return "Festers could not match \"" + shown + "\", so the Downloads app is open. It may have been moved or deleted.";
            } catch (Throwable ignored) { }
            return "Could not find \"" + shown + "\" in Downloads (it may have been moved or deleted)";
        }
        String mt = j.mime.length() == 0 ? mimeByName(j.path.length() > 0 ? j.path : j.name) : j.mime;
        try {
            Intent it = new Intent(Intent.ACTION_VIEW);
            it.setDataAndType(target, mt);
            it.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(it);
            return null;
        } catch (Throwable e) {
            try {
                Intent it = new Intent(android.app.DownloadManager.ACTION_VIEW_DOWNLOADS);
                it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                ctx.startActivity(it);
                return null;
            } catch (Throwable e2) { return "No app can open this file"; }
        }
    }

    /** V1.23.8: finds the saved file again however it is stored, so Open never says "gone" for a file that is really there. */
    private boolean canRead(Uri u) {
        try (android.os.ParcelFileDescriptor p = ctx.getContentResolver().openFileDescriptor(u, "r")) { return p != null; }
        catch (Throwable e) { return false; }
    }

    private static String mimeByName(String name) {
        try {
            int i = name == null ? -1 : name.lastIndexOf('.');
            String ex = i >= 0 ? name.substring(i + 1).toLowerCase(Locale.US) : "";
            String m = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ex);
            return m == null ? "*/*" : m;
        } catch (Throwable e) { return "*/*"; }
    }

    /** V1.24.0: a MediaStore/provider row that exists is enough; some phones refuse openFileDescriptor on a row that is really there. */
    private boolean rowExists(Uri u) {
        try (Cursor q = ctx.getContentResolver().query(u, new String[]{android.provider.OpenableColumns.DISPLAY_NAME}, null, null, null)) { return q != null && q.moveToFirst(); }
        catch (Throwable e) { return false; }
    }

    private boolean usable(Uri u) { return canRead(u) || rowExists(u); }

    /** First row of a MediaStore.Downloads query, or null. */
    private Uri firstDownload(String sel, String[] args) {
        try (Cursor q = ctx.getContentResolver().query(MediaStore.Downloads.EXTERNAL_CONTENT_URI, new String[]{MediaStore.MediaColumns._ID},
                sel, args, MediaStore.MediaColumns.DATE_ADDED + " DESC")) {
            if (q != null && q.moveToFirst()) return android.content.ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, q.getLong(0));
        } catch (Throwable ignored) { }
        return null;
    }

    private Uri resolveSaved(Job j) {
        // 1) the link saved when the file was written
        try { if (j.uri.length() > 0) { Uri u = Uri.parse(j.uri); if (usable(u)) return u; } } catch (Throwable ignored) { }
        String name = j.path.length() > 0 ? j.path.substring(j.path.lastIndexOf('/') + 1) : j.name;
        String alt = (j.name != null && j.name.length() > 0 && !j.name.equals(name)) ? j.name : "";
        if ((name == null || name.length() == 0) && alt.length() == 0) return null;
        if (name == null || name.length() == 0) { name = alt; alt = ""; }
        if (Build.VERSION.SDK_INT >= 29) {
            String dn = MediaStore.MediaColumns.DISPLAY_NAME, rp = MediaStore.MediaColumns.RELATIVE_PATH;
            Uri u;
            // 2) same name inside Downloads/Festers
            u = firstDownload(dn + "=? AND " + rp + " LIKE ?", new String[]{name, "Download/%"});
            // 3) same name anywhere in Downloads (the file may have been moved to another folder there)
            if (u == null) u = firstDownload(dn + "=?", new String[]{name});
            // 4) the other name Festers knew it by
            if (u == null && alt.length() > 0) u = firstDownload(dn + "=? AND " + rp + " LIKE ?", new String[]{alt, "Download/%"});
            // 5) Android renamed it "name (1).ext" because the name was taken
            if (u == null) {
                int dot = name.lastIndexOf('.');
                String stem = dot > 0 ? name.substring(0, dot) : name, ext = dot > 0 ? name.substring(dot) : "";
                u = firstDownload(dn + " LIKE ? AND " + rp + " LIKE ?", new String[]{stem + " (%)" + ext, "Download/%"});
            }
            if (u != null) { j.uri = u.toString(); synchronized (this) { persist(); } return u; }
        }
        // 6) the plain file in Downloads/Festers (older phones, or a file that is visible by path)
        try {
            File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            String[] tries = alt.length() > 0 ? new String[]{name, alt} : new String[]{name};
            for (String nm : tries) {
                File f = new File(dir, nm);
                if (!f.isFile()) continue;
                Uri u = null;
                try { u = androidx.core.content.FileProvider.getUriForFile(ctx, ctx.getPackageName() + ".fileprovider", f); } catch (Throwable ignored) { }
                if (u != null && usable(u)) { j.uri = u.toString(); synchronized (this) { persist(); } return u; }
            }
        } catch (Throwable ignored) { }
        return null;
    }

    /* ---------- scheduling ---------- */

    private void submit(final Job j) {
        pool.execute(() -> runJob(j));
    }

    private void runJob(Job j) {
        synchronized (this) {
            if (j.cancel) { removeNow(j, true); return; }
            if (j.pause) { setState(j, "paused", "Paused"); return; }
            if (!"queued".equals(j.state)) return; // already running or finished
            setState(j, "downloading", "");
        }
        acquire();
        try {
            if (j.wifiOnly && isMetered()) throw new IOException("Waiting for Wi-Fi (the Wi-Fi only setting is on)");
            File part = partFile(j);
            int attempt = 0;
            while (true) {
                try {
                    transfer(j, part);
                    break;
                } catch (Stop s) {
                    throw s;
                } catch (HttpEx h) {
                    boolean retry = h.code == 408 || h.code == 429 || h.code >= 500;
                    if (!retry || ++attempt > 4) throw h;
                    backoff(j, attempt);
                } catch (IOException io) {
                    if (++attempt > 5) throw io;
                    backoff(j, attempt);
                }
            }
            publish(j, part);
        } catch (Stop s) {
            synchronized (this) {
                if (s.cancel) removeNow(j, true);
                else { setState(j, "paused", "Paused"); }
            }
        } catch (HttpEx h) {
            fail(j, httpText(h.code), false);
        } catch (Throwable e) {
            String m = e.getMessage();
            boolean net = e instanceof java.net.UnknownHostException || e instanceof java.net.SocketException || e instanceof java.net.SocketTimeoutException
                || (e instanceof IOException && (m == null || !m.startsWith("Could not save")));
            if (m != null && m.startsWith("Waiting for Wi-Fi")) { synchronized (this) { j.netPaused = true; setState(j, "paused", m); } }
            else if (net) { synchronized (this) { j.netPaused = true; setState(j, "paused", "Connection lost. It continues when you are back online, or tap resume."); } }
            else fail(j, m == null ? e.getClass().getSimpleName() : m, false);
        } finally {
            release();
        }
    }

    private void backoff(Job j, int attempt) throws Stop {
        long end = System.currentTimeMillis() + 1200L * attempt;
        while (System.currentTimeMillis() < end) {
            if (j.cancel) throw new Stop(true);
            if (j.pause) throw new Stop(false);
            try { Thread.sleep(150); } catch (InterruptedException ignored) { }
        }
    }

    /* ---------- the transfer ---------- */

    private void transfer(Job j, File part) throws Exception {
        long have = part.exists() ? part.length() : 0;
        String cur = j.url;
        HttpURLConnection c = null;
        int code = 0;
        try {
            for (int hop = 0; hop < 10; hop++) {
                c = (HttpURLConnection) new URL(cur).openConnection();
                c.setInstanceFollowRedirects(false);
                c.setConnectTimeout(20000);
                c.setReadTimeout(30000);
                c.setRequestProperty("Accept", "*/*");
                c.setRequestProperty("Accept-Encoding", "identity");
                if (j.ua.length() > 0) c.setRequestProperty("User-Agent", j.ua);
                String ck = null;
                try { ck = CookieManager.getInstance().getCookie(cur); } catch (Throwable ignored) { }
                if (ck != null && ck.length() > 0) c.setRequestProperty("Cookie", ck);
                if (j.referer.length() > 0) c.setRequestProperty("Referer", j.referer);
                if (have > 0) c.setRequestProperty("Range", "bytes=" + have + "-");
                code = c.getResponseCode();
                try {
                    List<String> sc = c.getHeaderFields().get("Set-Cookie");
                    if (sc != null) for (String s : sc) CookieManager.getInstance().setCookie(cur, s);
                } catch (Throwable ignored) { }
                String loc = c.getHeaderField("Location");
                if ((code == 301 || code == 302 || code == 303 || code == 307 || code == 308) && loc != null) {
                    cur = new URL(new URL(cur), loc).toString();
                    c.disconnect(); c = null;
                    continue;
                }
                break;
            }
            if (c == null) throw new IOException("Too many redirects");
            if (code == 416) {
                // the range we asked for is past the end: the part file is either complete or wrong
                long tot = j.total;
                String cr = c.getHeaderField("Content-Range");
                if (cr != null && cr.indexOf('/') > 0) { try { tot = Long.parseLong(cr.substring(cr.indexOf('/') + 1).trim()); } catch (Throwable ignored) { } }
                if (tot > 0 && have == tot) { j.total = tot; j.bytes = have; return; }
                if (!part.delete()) throw new IOException("Could not restart the download");
                throw new IOException("Restarting from the beginning");
            }
            if (code < 200 || code >= 300) throw new HttpEx(code, "The site answered " + code);
            boolean partial = code == 206 && have > 0;
            if (!partial) have = 0; // 200 on a Range request: the server ignored it, start again from byte 0
            long len = -1;
            try { len = Long.parseLong(c.getHeaderField("Content-Length").trim()); } catch (Throwable ignored) { }
            if (partial) {
                long tot = -1;
                String cr = c.getHeaderField("Content-Range");
                if (cr != null && cr.indexOf('/') > 0) { try { tot = Long.parseLong(cr.substring(cr.indexOf('/') + 1).trim()); } catch (Throwable ignored) { } }
                j.total = tot > 0 ? tot : (len > 0 ? have + len : -1);
            } else {
                j.total = len > 0 ? len : -1;
            }
            String ct = c.getContentType();
            if (ct != null && ct.indexOf(';') >= 0) ct = ct.substring(0, ct.indexOf(';')).trim();
            if (j.mime.length() == 0 || "application/octet-stream".equals(j.mime) || "binary/octet-stream".equals(j.mime)) {
                if (ct != null && ct.length() > 0) j.mime = ct;
            }
            if (j.mime.length() == 0) j.mime = "application/octet-stream";
            if (!j.nameFixed) {
                String nm = nameFromDisposition(c.getHeaderField("Content-Disposition"));
                if (nm.length() == 0) nm = cleanName(URLUtil.guessFileName(cur, null, j.mime));
                if (nm.length() == 0) nm = "festers-" + System.currentTimeMillis();
                j.name = withExt(nm, j.mime);
                j.nameFixed = true;
            } else {
                j.name = withExt(j.name, j.mime);
            }
            j.bytes = have;
            j.lastBytes = have; j.lastSample = System.currentTimeMillis();
            emit(j, "state");
            long got = have;
            byte[] buf = new byte[65536];
            try (InputStream in = c.getInputStream(); FileOutputStream os = new FileOutputStream(part, partial)) {
                int n;
                while ((n = in.read(buf)) > 0) {
                    if (j.cancel) throw new Stop(true);
                    if (j.pause) throw new Stop(false);
                    os.write(buf, 0, n);
                    got += n;
                    j.bytes = got;
                    tick(j);
                }
                os.getFD().sync();
            }
            if (j.cancel) throw new Stop(true);
            if (j.pause) throw new Stop(false);
            if (j.total > 0 && got < j.total) throw new IOException("The connection closed early");
            if (j.total <= 0) j.total = got;
        } finally {
            if (c != null) try { c.disconnect(); } catch (Throwable ignored) { }
        }
    }

    private void tick(Job j) {
        long now = System.currentTimeMillis();
        if (now - j.lastSample >= 500) {
            long dt = now - j.lastSample;
            long inst = (j.bytes - j.lastBytes) * 1000L / Math.max(1, dt);
            j.speed = j.speed <= 0 ? inst : (j.speed * 6 + inst * 4) / 10;
            j.lastBytes = j.bytes; j.lastSample = now;
        }
        if (now - j.lastEmit >= 250) { j.lastEmit = now; emit(j, "progress"); }
        if (now - j.lastNote >= 800) { j.lastNote = now; note(j); }
        if (now - j.lastSave >= 3000) { j.lastSave = now; synchronized (this) { persist(); } }
    }

    /* ---------- saving into the public Downloads folder ---------- */

    private void publish(Job j, File part) throws Exception {
        synchronized (this) { setState(j, "saving", ""); }
        j.speed = 0;
        String name = j.name.length() == 0 ? "festers-" + System.currentTimeMillis() : j.name;
        String mime = j.mime.length() == 0 ? "application/octet-stream" : j.mime;
        if (Build.VERSION.SDK_INT >= 29) {
            ContentResolver cr = ctx.getContentResolver();
            ContentValues cv = new ContentValues();
            cv.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
            cv.put(MediaStore.MediaColumns.MIME_TYPE, mime);
            cv.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
            cv.put(MediaStore.MediaColumns.IS_PENDING, 1);
            Uri u = cr.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
            if (u == null) throw new Exception("Could not save: Android refused to create the file in Downloads");
            try (OutputStream os = cr.openOutputStream(u); InputStream in = new FileInputStream(part)) {
                if (os == null) throw new Exception("Could not save: the file could not be opened");
                byte[] buf = new byte[131072];
                int n;
                while ((n = in.read(buf)) > 0) os.write(buf, 0, n);
            } catch (Exception e) {
                try { cr.delete(u, null, null); } catch (Throwable ignored) { }
                throw new Exception("Could not save: " + (e.getMessage() == null ? "write failed" : e.getMessage()));
            }
            ContentValues done = new ContentValues();
            done.put(MediaStore.MediaColumns.IS_PENDING, 0);
            cr.update(u, done, null, null);
            try (Cursor q = cr.query(u, new String[]{MediaStore.MediaColumns.DISPLAY_NAME}, null, null, null)) {
                if (q != null && q.moveToFirst()) { String real = q.getString(0); if (real != null && real.length() > 0) name = real; }
            } catch (Throwable ignored) { }
            j.uri = u.toString();
            j.path = "Downloads/" + name;
        } else {
            File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            if (!dir.exists() && !dir.mkdirs()) throw new Exception("Could not save: cannot create Downloads");
            File dest = new File(dir, name);
            int k = 1;
            while (dest.exists()) {
                int dot = name.lastIndexOf('.');
                String base = dot > 0 ? name.substring(0, dot) : name, ext = dot > 0 ? name.substring(dot) : "";
                dest = new File(dir, base + " (" + (k++) + ")" + ext);
            }
            try (OutputStream os = new FileOutputStream(dest); InputStream in = new FileInputStream(part)) {
                byte[] buf = new byte[131072];
                int n;
                while ((n = in.read(buf)) > 0) os.write(buf, 0, n);
            }
            name = dest.getName();
            j.path = "Downloads/" + name;
            final Job fj = j;
            MediaScannerConnection.scanFile(ctx, new String[]{dest.getAbsolutePath()}, new String[]{mime}, (p, uri) -> { if (uri != null) { fj.uri = uri.toString(); synchronized (FBDownloader.this) { persist(); } } });
            if (j.uri.length() == 0) j.uri = Uri.fromFile(dest).toString();
        }
        j.name = name;
        keepCopy(j, part); // V1.26.0: in-app copy, kept until the download is removed from the list
        try { part.delete(); } catch (Throwable ignored) { }
        synchronized (this) {
            j.bytes = j.total > 0 ? j.total : j.bytes;
            j.error = "";
            setState(j, "completed", "");
        }
        emit(j, "done");
        note(j);
    }

    /* ---------- V1.26.0: the browser's own copy of every finished download ----------
     * The public copy lives in the phone's Downloads. A second copy is kept inside the app (app-private storage, no permission needed)
     * so Open still works if Android's Downloads entry is moved, renamed or refuses a viewer. The in-app copy is deleted when the
     * download is removed from the list (Delete, Clear finished, Kill Switch, Blaze). */
    private File keepDir(Job j) {
        File base = null;
        try { base = ctx.getExternalFilesDir("kept"); } catch (Throwable ignored) { }
        if (base == null) base = new File(ctx.getFilesDir(), "kept");
        return new File(base, String.valueOf(j.id));
    }

    private File keepFile(Job j) {
        String n = (j.name == null || j.name.length() == 0) ? "file" : j.name.replace('/', '_').replace('\\', '_');
        return new File(keepDir(j), n);
    }

    private void keepCopy(Job j, File part) {
        try {
            File d = keepDir(j);
            if (!d.exists() && !d.mkdirs()) return;
            File f = keepFile(j);
            try (InputStream in = new FileInputStream(part); OutputStream os = new FileOutputStream(f)) {
                byte[] buf = new byte[131072];
                int n;
                while ((n = in.read(buf)) > 0) os.write(buf, 0, n);
            }
        } catch (Throwable ignored) { }
    }

    private void keepDelete(Job j) {
        try {
            File d = keepDir(j);
            File[] fs = d.listFiles();
            if (fs != null) for (File f : fs) { try { f.delete(); } catch (Throwable ignored) { } }
            d.delete();
        } catch (Throwable ignored) { }
    }

    /** The in-app copy as a content link for viewer apps, or null when there is none. */
    private Uri keepUri(Job j) {
        try {
            File f = keepFile(j);
            if (!f.isFile() || f.length() == 0) return null;
            return androidx.core.content.FileProvider.getUriForFile(ctx, ctx.getPackageName() + ".fileprovider", f);
        } catch (Throwable e) { return null; }
    }

    private boolean viewUri(Uri u, String mime) {
        try {
            Intent it = new Intent(Intent.ACTION_VIEW);
            it.setDataAndType(u, mime);
            it.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(it);
            return true;
        } catch (Throwable e) { return false; }
    }

    private void deletePublic(Job j) {
        try {
            if (Build.VERSION.SDK_INT >= 29 && j.uri.startsWith("content:")) ctx.getContentResolver().delete(Uri.parse(j.uri), null, null);
            else if (j.uri.startsWith("file:")) new File(Uri.parse(j.uri).getPath()).delete();
        } catch (Throwable ignored) { }
    }

    /* ---------- state, events, notifications ---------- */

    private synchronized void removeNow(Job j, boolean deletePart) {
        jobs.remove(j.id);
        keepDelete(j); // V1.26.0
        if (deletePart) { try { partFile(j).delete(); } catch (Throwable ignored) { } }
        persist();
        cancelNote(j);
        JSObject o = toJson(j); o.put("status", "removed"); o.put("state", "removed");
        Sink s = sink;
        if (s != null) s.on(o, "state");
    }

    private synchronized void setState(Job j, String st, String err) {
        j.state = st;
        if (err != null) j.error = err;
        if (!"downloading".equals(st)) j.speed = 0;
        persist();
        emit(j, "state");
        if ("paused".equals(st) || "queued".equals(st)) note(j);
    }

    private void fail(Job j, String why, boolean net) {
        synchronized (this) { j.error = why == null ? "Download failed" : why; setState(j, "failed", j.error); }
        emit(j, "fail");
        note(j);
    }

    private void emit(Job j, String kind) {
        Sink s = sink;
        if (s != null) { try { s.on(toJson(j), kind); } catch (Throwable ignored) { } }
    }

    private JSObject toJson(Job j) {
        JSObject o = new JSObject();
        o.put("id", j.id); o.put("name", j.name); o.put("url", j.url); o.put("mime", j.mime); o.put("time", j.time);
        String st = j.state;
        String legacy = "completed".equals(st) ? "completed" : "failed".equals(st) ? "failed" : "paused".equals(st) ? "paused" : "queued".equals(st) ? "pending" : "downloading";
        o.put("status", legacy); o.put("state", st);
        o.put("bytes", j.bytes); o.put("total", j.total);
        o.put("progress", "completed".equals(st) ? 100 : (j.total > 0 ? (int) Math.min(100, j.bytes * 100 / j.total) : -1));
        o.put("speed", j.speed); o.put("desc", j.error); o.put("error", j.error);
        o.put("uri", j.uri); o.put("path", j.path); o.put("reason", 0); o.put("quiet", j.quiet);
        return o;
    }

    private synchronized void persist() {
        try {
            JSONArray a = new JSONArray();
            for (Job j : jobs.values()) {
                JSONObject o = new JSONObject();
                o.put("id", j.id); o.put("url", j.url); o.put("name", j.name); o.put("mime", j.mime); o.put("ua", j.ua); o.put("referer", j.referer);
                o.put("state", j.state); o.put("error", j.error); o.put("uri", j.uri); o.put("path", j.path);
                o.put("bytes", j.bytes); o.put("total", j.total); o.put("time", j.time);
                o.put("wifiOnly", j.wifiOnly); o.put("nameFixed", j.nameFixed);
                a.put(o);
            }
            while (a.length() > 200) a.remove(0);
            sp.edit().putString("jobs", a.toString()).apply();
        } catch (Throwable ignored) { }
    }

    private int noteId(Job j) { return 50000 + (int) (j.id % 20000); }

    private void cancelNote(Job j) {
        try { NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE); if (nm != null) nm.cancel(noteId(j)); } catch (Throwable ignored) { }
    }

    private void note(Job j) {
        try {
            NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;
            String ch = "fb_downloads";
            if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(ch) == null) {
                NotificationChannel c = new NotificationChannel(ch, "Festers downloads", NotificationManager.IMPORTANCE_LOW);
                c.setDescription("Progress of files Festers Browser is downloading");
                nm.createNotificationChannel(c);
            }
            Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(ctx, ch) : new Notification.Builder(ctx);
            int pf = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);
            Intent launch = ctx.getPackageManager().getLaunchIntentForPackage(ctx.getPackageName());
            PendingIntent tap = launch == null ? null : PendingIntent.getActivity(ctx, 0, launch, pf);
            String st = j.state;
            b.setContentTitle(j.name.length() == 0 ? "Download" : j.name);
            if ("completed".equals(st)) {
                b.setSmallIcon(android.R.drawable.stat_sys_download_done).setContentText("Download complete").setOngoing(false).setAutoCancel(true);
                try {
                    Intent v = new Intent(Intent.ACTION_VIEW);
                    v.setDataAndType(Uri.parse(j.uri), j.mime.length() == 0 ? "*/*" : j.mime);
                    v.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
                    b.setContentIntent(PendingIntent.getActivity(ctx, (int) (j.id % 10000) + 1, v, pf));
                } catch (Throwable e) { if (tap != null) b.setContentIntent(tap); }
            } else if ("failed".equals(st)) {
                b.setSmallIcon(android.R.drawable.stat_notify_error).setContentText("Download failed: " + j.error).setOngoing(false).setAutoCancel(true);
                if (tap != null) b.setContentIntent(tap);
            } else if ("paused".equals(st)) {
                b.setSmallIcon(android.R.drawable.stat_sys_download_done).setContentText("Paused " + (j.total > 0 ? (j.bytes * 100 / j.total) + "%" : "")).setOngoing(false);
                if (tap != null) b.setContentIntent(tap);
            } else {
                b.setSmallIcon(android.R.drawable.stat_sys_download).setOngoing(true).setOnlyAlertOnce(true);
                if (j.total > 0) {
                    int pct = (int) Math.min(100, j.bytes * 100 / j.total);
                    b.setProgress(100, pct, false).setContentText(pct + "%  " + human(j.bytes) + " of " + human(j.total) + (j.speed > 0 ? "  " + human(j.speed) + "/s" : ""));
                } else {
                    b.setProgress(0, 0, true).setContentText(human(j.bytes) + (j.speed > 0 ? "  " + human(j.speed) + "/s" : ""));
                }
                if (tap != null) b.setContentIntent(tap);
            }
            nm.notify(noteId(j), b.build());
        } catch (Throwable ignored) { }
    }

    /* ---------- helpers ---------- */

    static String human(long b) {
        if (b < 1024) return b + " B";
        double v = b / 1024.0;
        if (v < 1024) return String.format(Locale.US, "%.0f KB", v);
        v /= 1024;
        if (v < 1024) return String.format(Locale.US, "%.1f MB", v);
        return String.format(Locale.US, "%.2f GB", v / 1024);
    }

    private static String httpText(int code) {
        if (code == 401 || code == 403) return "The site refused the download (" + code + "). Open the page again, sign in if it asks, then retry.";
        if (code == 404 || code == 410) return "The file is gone from the site (" + code + ")";
        if (code >= 500) return "The site had a server problem (" + code + "). Try again later.";
        return "The site answered " + code;
    }

    static String cleanName(String n) {
        if (n == null) return "";
        n = n.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").trim();
        while (n.startsWith(".")) n = n.substring(1);
        if (n.length() > 120) {
            int dot = n.lastIndexOf('.');
            String ext = dot > 0 && n.length() - dot <= 8 ? n.substring(dot) : "";
            n = n.substring(0, 120 - ext.length()) + ext;
        }
        return n;
    }

    private static String imageExt(String m) {
        if (m.contains("jpeg") || m.contains("jpg")) return "jpg";
        if (m.contains("png")) return "png";
        if (m.contains("gif")) return "gif";
        if (m.contains("webp")) return "webp";
        if (m.contains("avif")) return "avif";
        if (m.contains("heic")) return "heic";
        if (m.contains("heif")) return "heif";
        if (m.contains("svg")) return "svg";
        if (m.contains("bmp")) return "bmp";
        if (m.contains("icon") || m.contains("ico")) return "ico";
        if (m.contains("tiff")) return "tiff";
        if (m.contains("jxl")) return "jxl";
        return "";
    }

    private static String withExt(String name, String mime) {
        String m = mime == null ? "" : mime.toLowerCase(Locale.US);
        int dot = name.lastIndexOf('.');
        String cur = (dot > 0 && dot < name.length() - 1) ? name.substring(dot + 1).toLowerCase(Locale.US) : "";
        if (m.startsWith("image/")) { // V1.23.8: every image type keeps a real image extension
            String want = imageExt(m);
            boolean known = cur.matches("jpe?g|png|gif|webp|avif|heic|heif|svg|bmp|ico|tiff?|jfif|apng|jxl");
            if (!known && want.length() > 0) return (cur.matches("php|aspx?|html?|jsp|cgi|image|img|bin|tmp") ? name.substring(0, dot) : name) + "." + want;
            return name;
        }
        if (cur.length() > 0) return name;
        try {
            String ex = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime);
            if (ex != null && ex.length() > 0) return name + "." + ex;
        } catch (Throwable ignored) { }
        return name;
    }

    private static String nameFromDisposition(String cd) {
        if (cd == null || cd.length() == 0) return "";
        try {
            String low = cd.toLowerCase(Locale.US);
            int i = low.indexOf("filename*=");
            if (i >= 0) {
                String v = cd.substring(i + 10).trim();
                int semi = v.indexOf(';'); if (semi >= 0) v = v.substring(0, semi).trim();
                int q = v.indexOf("''");
                if (q >= 0) v = v.substring(q + 2);
                return cleanName(Uri.decode(v.replace("\"", "")));
            }
            i = low.indexOf("filename=");
            if (i >= 0) {
                String v = cd.substring(i + 9).trim();
                if (v.startsWith("\"")) { int e = v.indexOf('"', 1); v = e > 0 ? v.substring(1, e) : v.substring(1); }
                else { int semi = v.indexOf(';'); if (semi >= 0) v = v.substring(0, semi).trim(); }
                return cleanName(Uri.decode(v));
            }
        } catch (Throwable ignored) { }
        return "";
    }

    private File partFile(Job j) {
        File d = ctx.getExternalFilesDir("dl");
        if (d == null) d = new File(ctx.getFilesDir(), "dl");
        if (!d.exists()) d.mkdirs();
        return new File(d, j.id + ".part");
    }

    private boolean isMetered() {
        try {
            ConnectivityManager cm = (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
            return cm != null && cm.isActiveNetworkMetered();
        } catch (Throwable e) { return false; }
    }

    private synchronized void acquire() {
        active++;
        try {
            if (wl == null) {
                PowerManager pm = (PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
                if (pm != null) wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "FestersBrowser:Download");
            }
            if (wl != null && !wl.isHeld()) wl.acquire(6L * 60 * 60 * 1000);
        } catch (Throwable ignored) { }
        try { FBKeepAliveService.startAuto(ctx); } catch (Throwable ignored) { }
    }

    private synchronized void release() {
        active = Math.max(0, active - 1);
        try { if (active == 0 && wl != null && wl.isHeld()) wl.release(); } catch (Throwable ignored) { }
    }
}
