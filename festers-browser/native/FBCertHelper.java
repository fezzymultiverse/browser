package com.fezzy.festersbrowser;

import android.net.http.SslCertificate;
import android.net.http.SslError;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.net.http.X509TrustManagerExtensions;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

/**
 * V1.23.0: Chrome fetches a site's missing intermediate certificate from the "Authority Information Access" (AIA) field of its leaf
 * certificate. Android's WebView does not, so every server that leaves its intermediate out (many government sites do) fails with
 * "untrusted" here even though Chrome opens it. This helper does what Chrome does: read the AIA caIssuers URL, download the
 * intermediate, rebuild the chain and validate it against the phone's real trust store. Only a chain that validates is accepted, so
 * this is NOT a bypass: a forged or self-signed certificate still fails.
 */
final class FBCertHelper {
    private FBCertHelper() { }

    interface Done { void result(boolean ok); }

    private static final ExecutorService POOL = Executors.newFixedThreadPool(2);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    /** Downloaded intermediates by subject DN (kept for the life of the process). */
    private static final Map<String, X509Certificate> INTER = new ConcurrentHashMap<>();
    /** Verdict by leaf SHA-256 so a page with 40 sub-resources does not fetch 40 times. */
    private static final Map<String, Boolean> VERDICT = new ConcurrentHashMap<>();
    /** Last explanation, shown in Settings > Page check. */
    static volatile String lastNote = "no certificate chase yet";

    /** True when only the chain is untrusted (no expiry, name or date problem alongside it). */
    static boolean chainOnly(SslError e) {
        try {
            return e.getPrimaryError() == SslError.SSL_UNTRUSTED
                && !e.hasError(SslError.SSL_EXPIRED) && !e.hasError(SslError.SSL_IDMISMATCH)
                && !e.hasError(SslError.SSL_NOTYETVALID) && !e.hasError(SslError.SSL_DATE_INVALID);
        } catch (Throwable t) { return false; }
    }

    /** Runs the chase off the UI thread and reports back on the UI thread. */
    static void verifyAsync(final SslError e, final String host, final Done cb) {
        final X509Certificate leaf = leafOf(e);
        if (leaf == null || !chainOnly(e)) { lastNote = "no readable leaf certificate or not a chain-only error"; MAIN.post(() -> cb.result(false)); return; }
        final String fp = sha256(leaf);
        Boolean known = VERDICT.get(fp);
        if (known != null) { final boolean k = known; MAIN.post(() -> cb.result(k)); return; }
        POOL.execute(() -> {
            boolean ok = false;
            try { ok = chaseAndValidate(leaf, host); } catch (Throwable t) { lastNote = "chase failed: " + t.getClass().getSimpleName(); }
            VERDICT.put(fp, ok);
            final boolean r = ok;
            MAIN.post(() -> cb.result(r));
        });
    }

    private static boolean chaseAndValidate(X509Certificate leaf, String host) throws Exception {
        List<X509Certificate> chain = new ArrayList<>();
        chain.add(leaf);
        X509Certificate cur = leaf;
        for (int depth = 0; depth < 4; depth++) {
            if (cur.getSubjectX500Principal().equals(cur.getIssuerX500Principal())) break; // self-issued: a root
            X509Certificate parent = INTER.get(cur.getIssuerX500Principal().getName());
            if (parent == null) {
                for (String u : aiaUrls(cur)) {
                    for (X509Certificate c : fetch(u)) {
                        INTER.put(c.getSubjectX500Principal().getName(), c);
                        if (c.getSubjectX500Principal().equals(cur.getIssuerX500Principal())) parent = c;
                    }
                    if (parent != null) break;
                }
            }
            if (parent == null) break; // the rest (root) must already be in the phone's store
            chain.add(parent);
            cur = parent;
        }
        X509TrustManager tm = systemTm();
        if (tm == null) { lastNote = "no system trust manager"; return false; }
        String alg = leaf.getPublicKey().getAlgorithm();
        String auth = "EC".equalsIgnoreCase(alg) ? "ECDHE_ECDSA" : "RSA";
        try {
            new X509TrustManagerExtensions(tm).checkServerTrusted(chain.toArray(new X509Certificate[0]), auth, host);
            lastNote = "trusted after fetching " + (chain.size() - 1) + " intermediate certificate(s) for " + host;
            return true;
        } catch (Throwable t) {
            lastNote = "still untrusted for " + host + " after fetching " + (chain.size() - 1) + " intermediate(s): " + t.getClass().getSimpleName();
            return false;
        }
    }

    private static X509TrustManager systemTm() throws Exception {
        TrustManagerFactory f = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        f.init((java.security.KeyStore) null);
        for (TrustManager t : f.getTrustManagers()) if (t instanceof X509TrustManager) return (X509TrustManager) t;
        return null;
    }

    static X509Certificate leafOf(SslError e) {
        try {
            SslCertificate sc = e.getCertificate();
            if (sc == null) return null;
            if (Build.VERSION.SDK_INT >= 29) { X509Certificate x = sc.getX509Certificate(); if (x != null) return x; }
            Bundle b = SslCertificate.saveState(sc);
            byte[] der = b == null ? null : b.getByteArray("x509-certificate");
            if (der == null) return null;
            return (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(new ByteArrayInputStream(der));
        } catch (Throwable t) { return null; }
    }

    static String sha256(X509Certificate c) {
        try {
            byte[] h = MessageDigest.getInstance("SHA-256").digest(c.getEncoded());
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < h.length; i++) { if (i > 0) sb.append(':'); sb.append(String.format("%02X", h[i] & 0xFF)); }
            return sb.toString();
        } catch (Throwable t) { return ""; }
    }

    /** Plain facts about the certificate for the warning shown before "Continue anyway". */
    static String[] facts(SslError e) {
        String issuer = "", subject = "", from = "", to = "", sha = "";
        try {
            SslCertificate sc = e.getCertificate();
            if (sc != null) {
                if (sc.getIssuedBy() != null) issuer = String.valueOf(sc.getIssuedBy().getCName());
                if (sc.getIssuedTo() != null) subject = String.valueOf(sc.getIssuedTo().getCName());
                from = String.valueOf(sc.getValidNotBefore());
                to = String.valueOf(sc.getValidNotAfter());
            }
            X509Certificate x = leafOf(e);
            if (x != null) sha = sha256(x);
        } catch (Throwable ignored) { }
        return new String[] { issuer, subject, from, to, sha };
    }

    /* ---------- minimal DER reader: just enough to find the caIssuers URL ---------- */
    private static int[] tlv(byte[] b, int p) {
        int tag = b[p] & 0xff, l = b[p + 1] & 0xff, hl = 2;
        if ((l & 0x80) != 0) { int n = l & 0x7f; l = 0; for (int i = 0; i < n; i++) l = (l << 8) | (b[p + 2 + i] & 0xff); hl = 2 + n; }
        return new int[] { tag, hl, l };
    }

    private static final byte[] CA_ISSUERS = { 0x2B, 0x06, 0x01, 0x05, 0x05, 0x07, 0x30, 0x02 };

    static List<String> aiaUrls(X509Certificate c) {
        List<String> out = new ArrayList<>();
        try {
            byte[] ext = c.getExtensionValue("1.3.6.1.5.5.7.1.1");
            if (ext == null) return out;
            int[] o = tlv(ext, 0);
            int p = o[1];
            int[] s = tlv(ext, p);
            int q = p + s[1], end = p + s[1] + s[2];
            while (q < end) {
                int[] a = tlv(ext, q);
                int ap = q + a[1];
                int[] oid = tlv(ext, ap);
                int op = ap + oid[1];
                boolean ca = oid[2] == CA_ISSUERS.length;
                if (ca) for (int i = 0; i < CA_ISSUERS.length; i++) if (ext[op + i] != CA_ISSUERS[i]) { ca = false; break; }
                int gp = op + oid[2];
                int[] g = tlv(ext, gp);
                if (ca && g[0] == 0x86) out.add(new String(ext, gp + g[1], g[2], "US-ASCII"));
                q += a[1] + a[2];
            }
        } catch (Throwable ignored) { }
        return out;
    }

    private static List<X509Certificate> fetch(String u) {
        List<X509Certificate> out = new ArrayList<>();
        try {
            if (!(u.startsWith("http://") || u.startsWith("https://"))) return out;
            HttpURLConnection c = (HttpURLConnection) new URL(u).openConnection();
            c.setConnectTimeout(6000); c.setReadTimeout(8000); c.setInstanceFollowRedirects(true);
            c.setRequestProperty("User-Agent", "FestersBrowser-AIA");
            InputStream in = c.getInputStream();
            java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[4096]; int n, tot = 0;
            while ((n = in.read(buf)) > 0) { tot += n; if (tot > 200000) break; bo.write(buf, 0, n); }
            in.close();
            Collection<? extends Certificate> cs = CertificateFactory.getInstance("X.509").generateCertificates(new ByteArrayInputStream(bo.toByteArray()));
            for (Certificate x : cs) if (x instanceof X509Certificate) out.add((X509Certificate) x);
        } catch (Throwable ignored) { }
        return out;
    }
}
