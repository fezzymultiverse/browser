package com.fezzy.festersbrowser;

import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.Settings;
import android.webkit.MimeTypeMap;

import androidx.core.content.FileProvider;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Comparator;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Files in Downloads/Festers: list, open, delete, install APKs and extract ZIPs.
 * Android makes the user grant "Install unknown apps" in system settings; the app cannot grant it itself.
 */
@CapacitorPlugin(name = "FBFiles")
public class FBFilesPlugin extends Plugin {
    private static final long MAX_ZIP_BYTES = 4L * 1024 * 1024 * 1024;
    private static final int MAX_ZIP_ENTRIES = 50000;
    private static final long MAX_READ_BYTES = 16L * 1024 * 1024;

    private File dlDir() {
        return new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Festers");
    }

    /** Keep only a plain file name, never a path. */
    private static String base(String n) {
        if (n == null) return "";
        n = n.replace('\\', '/');
        int i = n.lastIndexOf('/');
        if (i >= 0) n = n.substring(i + 1);
        n = n.replaceAll("[\\p{Cntrl}]", "_").trim();
        while (n.startsWith(".")) n = n.substring(1);
        return n;
    }

    private File fileFor(String name) throws Exception {
        String b = base(name);
        if (b.isEmpty()) throw new Exception("No file name");
        File f = new File(dlDir(), b);
        if (!f.exists() || !f.isFile()) throw new Exception("File not found in Downloads/Festers");
        return f;
    }

    /** V1.12.2: resolve a folder under Downloads/Festers from a relative path; refuses anything that escapes it. */
    private File dirFor(String sub) throws Exception {
        File root = dlDir();
        if (sub == null || sub.trim().isEmpty()) return root;
        File cur = root;
        for (String seg : sub.replace('\\', '/').split("/")) {
            if (seg.isEmpty()) continue;
            if (seg.equals(".") || seg.equals("..") || seg.indexOf('\u0000') >= 0) throw new Exception("Unsafe folder path");
            cur = new File(cur, seg);
        }
        String rc = root.getCanonicalPath(), cc = cur.getCanonicalPath();
        if (!(cc.equals(rc) || cc.startsWith(rc + File.separator))) throw new Exception("Unsafe folder path");
        if (!cur.isDirectory()) throw new Exception("Folder not found");
        return cur;
    }

    /** V1.12.2: like fileFor, but inside an optional sub folder of Downloads/Festers. */
    private File fileFor(String name, String sub) throws Exception {
        String b = base(name);
        if (b.isEmpty()) throw new Exception("No file name");
        File f = new File(dirFor(sub), b);
        if (!f.exists() || !f.isFile()) throw new Exception("File not found in Downloads/Festers");
        return f;
    }

    private static String mimeOf(String name) {
        String ext = MimeTypeMap.getFileExtensionFromUrl(name.replace(" ", "_"));
        if (ext == null || ext.isEmpty()) {
            int i = name.lastIndexOf('.');
            ext = i >= 0 ? name.substring(i + 1) : "";
        }
        String m = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext.toLowerCase());
        return m == null ? "application/octet-stream" : m;
    }

    private boolean canInstall() {
        if (Build.VERSION.SDK_INT < 26) return true;
        return getContext().getPackageManager().canRequestPackageInstalls();
    }

    private Uri shareUri(File f) {
        return FileProvider.getUriForFile(getContext(), getContext().getPackageName() + ".fileprovider", f);
    }

    @PluginMethod
    public void status(PluginCall call) {
        JSObject o = new JSObject();
        o.put("canInstall", canInstall());
        o.put("sdk", Build.VERSION.SDK_INT);
        o.put("dir", dlDir().getAbsolutePath());
        call.resolve(o);
    }

    /** Opens the system page where the user allows this app to install other apps. */
    @PluginMethod
    public void openInstallSettings(PluginCall call) {
        try {
            Intent it;
            if (Build.VERSION.SDK_INT >= 26) {
                it = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + getContext().getPackageName()));
            } else {
                it = new Intent(Settings.ACTION_SECURITY_SETTINGS);
            }
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(it);
            call.resolve(new JSObject().put("ok", true));
        } catch (Throwable e) {
            call.resolve(new JSObject().put("ok", false).put("desc", String.valueOf(e.getMessage())));
        }
    }

    @PluginMethod
    public void list(PluginCall call) {
        JSObject o = new JSObject();
        JSArray arr = new JSArray();
        File[] fs = null;
        try { fs = dirFor(call.getString("sub", "")).listFiles(); } catch (Exception ex) { o.put("error", String.valueOf(ex.getMessage())); }
        if (fs != null) {
            Arrays.sort(fs, new Comparator<File>() {
                public int compare(File a, File b) { return Long.compare(b.lastModified(), a.lastModified()); }
            });
            for (File f : fs) {
                if (f.isHidden()) continue;
                JSObject j = new JSObject();
                j.put("name", f.getName());
                j.put("dir", f.isDirectory());
                j.put("size", f.isDirectory() ? 0 : f.length());
                j.put("time", f.lastModified());
                arr.put(j);
            }
        }
        o.put("files", arr);
        call.resolve(o);
    }

    @PluginMethod
    public void installApk(PluginCall call) {
        JSObject o = new JSObject();
        try {
            File f = fileFor(call.getString("name", ""), call.getString("sub", ""));
            if (!f.getName().toLowerCase().endsWith(".apk")) throw new Exception("That is not an .apk file");
            if (!canInstall()) {
                o.put("ok", false); o.put("needPermission", true);
                o.put("desc", "Allow Festers Browser to install apps first");
                call.resolve(o);
                return;
            }
            // Copy to the app cache so the installer can read it through the FileProvider.
            File dir = new File(getContext().getCacheDir(), "apk");
            if (!dir.exists()) dir.mkdirs();
            File tmp = new File(dir, "install.apk");
            try (InputStream in = new FileInputStream(f); OutputStream out = new FileOutputStream(tmp)) {
                byte[] buf = new byte[65536]; int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            }
            Intent it = new Intent(Intent.ACTION_VIEW);
            it.setDataAndType(shareUri(tmp), "application/vnd.android.package-archive");
            it.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(it);
            o.put("ok", true);
        } catch (Throwable e) {
            o.put("ok", false); o.put("desc", e.getMessage() == null ? "Could not start the installer" : e.getMessage());
        }
        call.resolve(o);
    }

    /** V1.12.2: read a file from Downloads/Festers (or a sub folder) as base64 for the built-in viewers (16 MB limit). */
    @PluginMethod
    public void readFile(PluginCall call) {
        JSObject o = new JSObject();
        try {
            File f = fileFor(call.getString("name", ""), call.getString("sub", ""));
            if (f.length() > MAX_READ_BYTES) throw new Exception("File is too large to view here (limit 16 MB). Use Open with.");
            java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream((int) Math.max(32, f.length()));
            try (InputStream in = new FileInputStream(f)) {
                byte[] buf = new byte[65536]; int n;
                while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            }
            o.put("ok", true);
            o.put("name", f.getName());
            o.put("size", f.length());
            o.put("mime", mimeOf(f.getName()));
            o.put("data", android.util.Base64.encodeToString(bo.toByteArray(), android.util.Base64.NO_WRAP));
        } catch (Throwable e) {
            o.put("ok", false); o.put("desc", e.getMessage() == null ? "Could not read the file" : e.getMessage());
        }
        call.resolve(o);
    }

    @PluginMethod
    public void openFile(PluginCall call) {
        JSObject o = new JSObject();
        try {
            File f = fileFor(call.getString("name", ""), call.getString("sub", ""));
            File dir = new File(getContext().getCacheDir(), "open");
            if (!dir.exists()) dir.mkdirs();
            File tmp = new File(dir, f.getName());
            try (InputStream in = new FileInputStream(f); OutputStream out = new FileOutputStream(tmp)) {
                byte[] buf = new byte[65536]; int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            }
            Intent it = new Intent(Intent.ACTION_VIEW);
            it.setDataAndType(shareUri(tmp), mimeOf(f.getName()));
            it.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(Intent.createChooser(it, "Open with").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            o.put("ok", true);
        } catch (ActivityNotFoundException e) {
            o.put("ok", false); o.put("desc", "No app can open this file type");
        } catch (Throwable e) {
            o.put("ok", false); o.put("desc", e.getMessage() == null ? "Could not open the file" : e.getMessage());
        }
        call.resolve(o);
    }

    @PluginMethod
    public void deleteFile(PluginCall call) {
        JSObject o = new JSObject();
        try {
            File f = fileFor(call.getString("name", ""), call.getString("sub", ""));
            o.put("ok", f.delete());
        } catch (Throwable e) {
            o.put("ok", false); o.put("desc", e.getMessage());
        }
        call.resolve(o);
    }

    /** Extract a .zip from Downloads/Festers into Downloads/Festers/<folder>. Blocks path tricks and zip bombs. */
    /** V1.14.1: saves base64 bytes as a file in Downloads/Festers (used by the full-setup export). */
    @PluginMethod
    public void saveBase64(final PluginCall call) {
        final String name = base(call.getString("name", ""));
        final String data = call.getString("data", "");
        new Thread(() -> {
            JSObject o = new JSObject();
            try {
                if (name.isEmpty()) throw new Exception("No file name");
                byte[] bytes = android.util.Base64.decode(data, android.util.Base64.DEFAULT);
                if (Build.VERSION.SDK_INT >= 29) {
                    ContentResolver cr = getContext().getContentResolver();
                    ContentValues cv = new ContentValues();
                    cv.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
                    String mt = mimeOf(name);
                    if (!"application/octet-stream".equals(mt)) cv.put(MediaStore.MediaColumns.MIME_TYPE, mt);
                    cv.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                    Uri u = cr.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
                    if (u == null) throw new Exception("Android refused to create " + name);
                    try (OutputStream os = cr.openOutputStream(u)) { os.write(bytes); }
                } else {
                    File dir = dlDir();
                    if (!dir.exists()) dir.mkdirs();
                    try (FileOutputStream os = new FileOutputStream(new File(dir, name))) { os.write(bytes); }
                }
                o.put("ok", true); o.put("folder", "Downloads/Festers"); o.put("name", name);
            } catch (Throwable e) {
                o.put("ok", false); o.put("desc", e.getMessage() == null ? "Could not save the file" : e.getMessage());
            }
            call.resolve(o);
        }).start();
    }

    @PluginMethod
    public void unzip(final PluginCall call) {
        final String name = call.getString("name", "");
        final boolean own = call.getBoolean("ownFolder", true);
        final boolean del = call.getBoolean("deleteZip", false);
        new Thread(() -> {
            JSObject o = new JSObject();
            try {
                File zip = fileFor(name);
                if (!zip.getName().toLowerCase().endsWith(".zip")) throw new Exception("That is not a .zip file");
                String stem = zip.getName().substring(0, zip.getName().length() - 4);
                String sub = own ? base(stem) : "";
                if (own && sub.isEmpty()) sub = "extracted";
                if (own) {
                    // pick a folder name that does not exist yet
                    String pick = sub; int k = 2;
                    while (new File(dlDir(), pick).exists()) pick = sub + " (" + (k++) + ")";
                    sub = pick;
                }
                long total = 0; int count = 0;
                try (ZipInputStream zin = new ZipInputStream(new FileInputStream(zip))) {
                    ZipEntry e;
                    byte[] buf = new byte[65536];
                    while ((e = zin.getNextEntry()) != null) {
                        if (++count > MAX_ZIP_ENTRIES) throw new Exception("Too many files in the zip");
                        String en = e.getName().replace('\\', '/');
                        if (en.startsWith("/") || en.contains("../") || en.equals("..") || en.contains("\u0000"))
                            throw new Exception("Blocked: the zip has an unsafe path (" + en + ")");
                        if (e.isDirectory()) continue;
                        String rel = (sub.isEmpty() ? "" : sub + "/") + en;
                        int slash = rel.lastIndexOf('/');
                        String relDir = slash >= 0 ? rel.substring(0, slash) : "";
                        String fname = slash >= 0 ? rel.substring(slash + 1) : rel;
                        if (fname.isEmpty()) continue;
                        if (Build.VERSION.SDK_INT >= 29) {
                            ContentResolver cr = getContext().getContentResolver();
                            ContentValues cv = new ContentValues();
                            cv.put(MediaStore.MediaColumns.DISPLAY_NAME, fname);
                            String zm = mimeOf(fname);
                            if (!"application/octet-stream".equals(zm)) cv.put(MediaStore.MediaColumns.MIME_TYPE, zm);
                            cv.put(MediaStore.MediaColumns.RELATIVE_PATH,
                                Environment.DIRECTORY_DOWNLOADS + "/Festers" + (relDir.isEmpty() ? "" : "/" + relDir));
                            Uri u = cr.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
                            if (u == null) throw new Exception("Android refused to create " + fname);
                            try (OutputStream os = cr.openOutputStream(u)) {
                                int n;
                                while ((n = zin.read(buf)) > 0) {
                                    total += n;
                                    if (total > MAX_ZIP_BYTES) throw new Exception("Archive is too large to extract");
                                    os.write(buf, 0, n);
                                }
                            }
                        } else {
                            File out = new File(dlDir(), rel);
                            String canon = out.getCanonicalPath();
                            if (!canon.startsWith(dlDir().getCanonicalPath() + File.separator))
                                throw new Exception("Blocked: the zip has an unsafe path");
                            File parent = out.getParentFile();
                            if (parent != null && !parent.exists()) parent.mkdirs();
                            try (FileOutputStream os = new FileOutputStream(out)) {
                                int n;
                                while ((n = zin.read(buf)) > 0) {
                                    total += n;
                                    if (total > MAX_ZIP_BYTES) throw new Exception("Archive is too large to extract");
                                    os.write(buf, 0, n);
                                }
                            }
                        }
                    }
                }
                if (del) zip.delete();
                o.put("ok", true); o.put("count", count); o.put("folder", sub.isEmpty() ? "Festers" : "Festers/" + sub);
            } catch (Throwable e) {
                o.put("ok", false); o.put("desc", e.getMessage() == null ? "Could not extract the zip" : e.getMessage());
            }
            call.resolve(o);
        }).start();
    }
}
