package com.fezzy.festersbrowser;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.pdf.PdfRenderer;
import android.os.ParcelFileDescriptor;
import android.util.Base64;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;

/** Renders PDF pages with Android's built-in PdfRenderer; JS shows them as images. */
@CapacitorPlugin(name = "FBPdf")
public class FBPdfPlugin extends Plugin {
    private PdfRenderer r;
    private ParcelFileDescriptor pfd;

    private synchronized void shut() {
        try { if (r != null) r.close(); } catch (Exception e) { }
        try { if (pfd != null) pfd.close(); } catch (Exception e) { }
        r = null; pfd = null;
    }

    @PluginMethod
    public synchronized void open(PluginCall c) {
        try {
            shut();
            byte[] b = Base64.decode(c.getString("data"), Base64.DEFAULT);
            File f = new File(getContext().getCacheDir(), "view.pdf");
            FileOutputStream o = new FileOutputStream(f);
            o.write(b); o.close();
            pfd = ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY);
            r = new PdfRenderer(pfd);
            JSObject j = new JSObject();
            j.put("pages", r.getPageCount());
            c.resolve(j);
        } catch (Exception e) { c.reject("Cannot open PDF: " + e.getMessage()); }
    }

    @PluginMethod
    public synchronized void page(PluginCall c) {
        try {
            if (r == null) { c.reject("No PDF open"); return; }
            int w = Math.min(2000, Math.max(200, c.getInt("width", 1080)));
            PdfRenderer.Page p = r.openPage(c.getInt("n", 0));
            int h = Math.max(1, (int) ((long) w * p.getHeight() / p.getWidth()));
            Bitmap bm = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            bm.eraseColor(Color.WHITE);
            p.render(bm, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
            p.close();
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            bm.compress(Bitmap.CompressFormat.JPEG, 88, bo);
            bm.recycle();
            JSObject j = new JSObject();
            j.put("img", Base64.encodeToString(bo.toByteArray(), Base64.NO_WRAP));
            c.resolve(j);
        } catch (Exception e) { c.reject("Render failed: " + e.getMessage()); }
    }

    @PluginMethod
    public void close(PluginCall c) { shut(); c.resolve(); }
}
