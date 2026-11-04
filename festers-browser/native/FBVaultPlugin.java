package com.fezzy.festersbrowser;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyPermanentlyInvalidatedException;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;

/**
 * FBVault: unlocks the JS vault with a fingerprint and/or an optional 4-digit PIN.
 *
 * The vault master password is wrapped by an Android Keystore key:
 *  - fingerprint: AES-GCM key that needs BiometricPrompt (CryptoObject) for every use when the
 *    phone has a Class 3 sensor; on a Class 2 sensor the key is not auth-bound and the prompt is
 *    an app-level gate (reported to JS as mode "weak").
 *  - PIN: AES-GCM Keystore key released only after the PIN verifies (PBKDF2 hash kept in private
 *    prefs). Three wrong tries start a lockout, five wrong tries erase the PIN.
 * The master password stays the real key. Nothing here leaves the device.
 */
@CapacitorPlugin(name = "FBVault")
public class FBVaultPlugin extends Plugin {
    private static final String KS = "AndroidKeyStore";
    private static final String BIO_ALIAS = "fb_vault_bio";
    private static final String GATE_ALIAS = "fb_vault_bio_gate";
    private static final String PIN_ALIAS = "fb_vault_pin";
    private static final String PREFS = "fb_vault_unlock";
    private static final int MAX_FAILS = 5;
    private static final int PBKDF_ROUNDS = 120000;

    private interface PromptDone { void ok(Cipher c) throws Exception; }

    /* ---------- helpers ---------- */

    private SharedPreferences prefs() {
        return getContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static String b64(byte[] b) { return Base64.encodeToString(b, Base64.NO_WRAP); }

    private static byte[] unb64(String s) { return Base64.decode(s, Base64.NO_WRAP); }

    private void res(PluginCall call, boolean ok, String err) {
        JSObject o = new JSObject();
        o.put("ok", ok);
        if (err != null) o.put("err", err);
        call.resolve(o);
    }

    private void fail(PluginCall call, String why) {
        JSObject o = new JSObject();
        o.put("ok", false);
        o.put("err", "error");
        o.put("desc", why == null ? "failed" : why);
        call.resolve(o);
    }

    private SecretKey getKey(String alias) throws Exception {
        KeyStore ks = KeyStore.getInstance(KS);
        ks.load(null);
        return (SecretKey) ks.getKey(alias, null);
    }

    private void deleteKey(String alias) {
        try {
            KeyStore ks = KeyStore.getInstance(KS);
            ks.load(null);
            if (ks.containsAlias(alias)) ks.deleteEntry(alias);
        } catch (Throwable ignored) { }
    }

    private SecretKey makeKey(String alias, boolean needAuth) throws Exception {
        KeyGenerator kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KS);
        KeyGenParameterSpec.Builder b = new KeyGenParameterSpec.Builder(
                alias, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256);
        if (needAuth) {
            b.setUserAuthenticationRequired(true);
            if (Build.VERSION.SDK_INT >= 30) {
                b.setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG);
            }
            if (Build.VERSION.SDK_INT >= 24) b.setInvalidatedByBiometricEnrollment(true);
        }
        kg.init(b.build());
        return kg.generateKey();
    }

    private Cipher encCipher(SecretKey k) throws Exception {
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, k);
        return c;
    }

    private Cipher decCipher(SecretKey k, byte[] iv) throws Exception {
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.DECRYPT_MODE, k, new GCMParameterSpec(128, iv));
        return c;
    }

    /** "strong" (Class 3), "weak" (Class 2) or "none". */
    private String bioLevel() {
        try {
            BiometricManager bm = BiometricManager.from(getContext());
            if (bm.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS) return "strong";
            if (bm.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS) return "weak";
        } catch (Throwable ignored) { }
        return "none";
    }

    private void bioClear() {
        prefs().edit().remove("bio_iv").remove("bio_ct").remove("bio_mode").apply();
        deleteKey(BIO_ALIAS);
        deleteKey(GATE_ALIAS);
    }

    private void pinClear() {
        prefs().edit().remove("pin_salt").remove("pin_hash").remove("pin_iv").remove("pin_ct")
                .remove("pin_fails").remove("pin_until").apply();
        deleteKey(PIN_ALIAS);
    }

    private byte[] pinHash(String pin, byte[] salt) throws Exception {
        SecretKeyFactory f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        return f.generateSecret(new PBEKeySpec(pin.toCharArray(), salt, PBKDF_ROUNDS, 256)).getEncoded();
    }

    private void prompt(final PluginCall call, final Cipher cipher, final boolean strong,
                        final String title, final PromptDone done) {
        final FragmentActivity act = (FragmentActivity) getActivity();
        act.runOnUiThread(() -> {
            try {
                BiometricPrompt.PromptInfo info = new BiometricPrompt.PromptInfo.Builder()
                        .setTitle(title)
                        .setSubtitle("Festers Browser")
                        .setNegativeButtonText("Use master password")
                        .setConfirmationRequired(false)
                        .setAllowedAuthenticators(strong
                                ? BiometricManager.Authenticators.BIOMETRIC_STRONG
                                : BiometricManager.Authenticators.BIOMETRIC_WEAK)
                        .build();
                BiometricPrompt bp = new BiometricPrompt(act, ContextCompat.getMainExecutor(act),
                        new BiometricPrompt.AuthenticationCallback() {
                            @Override
                            public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult r) {
                                try {
                                    done.ok(strong ? r.getCryptoObject().getCipher() : cipher);
                                } catch (Throwable e) {
                                    fail(call, "crypto: " + e.getMessage());
                                }
                            }

                            @Override
                            public void onAuthenticationError(int code, CharSequence msg) {
                                boolean cancel = code == BiometricPrompt.ERROR_USER_CANCELED
                                        || code == BiometricPrompt.ERROR_NEGATIVE_BUTTON
                                        || code == BiometricPrompt.ERROR_CANCELED;
                                JSObject o = new JSObject();
                                o.put("ok", false);
                                o.put("err", cancel ? "cancel" : "error");
                                o.put("desc", String.valueOf(msg));
                                call.resolve(o);
                            }
                        });
                if (strong) bp.authenticate(info, new BiometricPrompt.CryptoObject(cipher));
                else bp.authenticate(info);
            } catch (Throwable e) {
                fail(call, "prompt: " + e.getMessage());
            }
        });
    }

    /* ---------- status ---------- */

    @PluginMethod
    public void status(final PluginCall call) {
        SharedPreferences p = prefs();
        JSObject o = new JSObject();
        o.put("bio", bioLevel());
        o.put("bioSet", p.getString("bio_ct", null) != null);
        o.put("bioMode", p.getString("bio_mode", ""));
        o.put("pinSet", p.getString("pin_hash", null) != null);
        o.put("pinLeft", Math.max(0, MAX_FAILS - p.getInt("pin_fails", 0)));
        call.resolve(o);
    }

    /* ---------- device passkey: fingerprint or screen lock, no key material ---------- */

    @PluginMethod
    public void confirm(final PluginCall call) {
        final String title = call.getString("title", "Confirm it's you");
        final FragmentActivity act = (FragmentActivity) getActivity();
        if (act == null) { fail(call, "no activity"); return; }
        final int auth = BiometricManager.Authenticators.BIOMETRIC_WEAK | BiometricManager.Authenticators.DEVICE_CREDENTIAL;
        try {
            if (BiometricManager.from(getContext()).canAuthenticate(auth) != BiometricManager.BIOMETRIC_SUCCESS) {
                res(call, false, "unavailable");
                return;
            }
        } catch (Throwable e) { fail(call, e.getMessage()); return; }
        act.runOnUiThread(() -> {
            try {
                BiometricPrompt.PromptInfo info = new BiometricPrompt.PromptInfo.Builder()
                        .setTitle(title)
                        .setSubtitle("Festers Browser passkey")
                        .setAllowedAuthenticators(auth)
                        .build();
                BiometricPrompt bp = new BiometricPrompt(act, ContextCompat.getMainExecutor(act),
                        new BiometricPrompt.AuthenticationCallback() {
                            @Override
                            public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult r) {
                                res(call, true, null);
                            }
                            @Override
                            public void onAuthenticationError(int code, CharSequence msg) {
                                boolean cancel = code == BiometricPrompt.ERROR_USER_CANCELED
                                        || code == BiometricPrompt.ERROR_NEGATIVE_BUTTON
                                        || code == BiometricPrompt.ERROR_CANCELED;
                                JSObject o = new JSObject();
                                o.put("ok", false);
                                o.put("err", cancel ? "cancel" : "error");
                                o.put("desc", String.valueOf(msg));
                                call.resolve(o);
                            }
                        });
                bp.authenticate(info);
            } catch (Throwable e) { fail(call, "prompt: " + e.getMessage()); }
        });
    }

    /* ---------- V1.26.0: browser lock, fingerprint only ---------- */

    /** Fingerprint check for the browser lock. Fingerprint only (the screen-lock PIN is not accepted); the negative button hands over to the browser's own 4-digit PIN. */
    @PluginMethod
    public void lockAuth(final PluginCall call) {
        final String title = call.getString("title", "Unlock Festers Browser");
        final FragmentActivity act = (FragmentActivity) getActivity();
        if (act == null) { fail(call, "no activity"); return; }
        try {
            if (BiometricManager.from(getContext()).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) != BiometricManager.BIOMETRIC_SUCCESS) {
                res(call, false, "unavailable");
                return;
            }
        } catch (Throwable e) { fail(call, e.getMessage()); return; }
        act.runOnUiThread(() -> {
            try {
                BiometricPrompt.PromptInfo info = new BiometricPrompt.PromptInfo.Builder()
                        .setTitle(title)
                        .setSubtitle("Bojack Security")
                        .setNegativeButtonText("Use PIN")
                        .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
                        .build();
                BiometricPrompt bp = new BiometricPrompt(act, ContextCompat.getMainExecutor(act),
                        new BiometricPrompt.AuthenticationCallback() {
                            @Override
                            public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult r) {
                                res(call, true, null);
                            }
                            @Override
                            public void onAuthenticationError(int code, CharSequence msg) {
                                boolean cancel = code == BiometricPrompt.ERROR_USER_CANCELED
                                        || code == BiometricPrompt.ERROR_NEGATIVE_BUTTON
                                        || code == BiometricPrompt.ERROR_CANCELED;
                                JSObject o = new JSObject();
                                o.put("ok", false);
                                o.put("err", cancel ? "cancel" : "error");
                                o.put("desc", String.valueOf(msg));
                                call.resolve(o);
                            }
                        });
                bp.authenticate(info);
            } catch (Throwable e) { fail(call, "prompt: " + e.getMessage()); }
        });
    }

    /* ---------- fingerprint ---------- */

    @PluginMethod
    public void bioEnroll(final PluginCall call) {
        final String secret = call.getString("secret", "");
        if (secret == null || secret.isEmpty()) { fail(call, "no secret"); return; }
        final String level = bioLevel();
        if ("none".equals(level)) { res(call, false, "unavailable"); return; }
        final boolean strong = "strong".equals(level);
        try {
            bioClear();
            SecretKey k = makeKey(strong ? BIO_ALIAS : GATE_ALIAS, strong);
            Cipher c = encCipher(k);
            prompt(call, c, strong, "Turn on fingerprint unlock", cph -> {
                byte[] ct = cph.doFinal(secret.getBytes(StandardCharsets.UTF_8));
                prefs().edit()
                        .putString("bio_iv", b64(cph.getIV()))
                        .putString("bio_ct", b64(ct))
                        .putString("bio_mode", strong ? "strong" : "weak")
                        .apply();
                res(call, true, null);
            });
        } catch (Throwable e) {
            bioClear();
            fail(call, e.getMessage());
        }
    }

    @PluginMethod
    public void bioUnlock(final PluginCall call) {
        SharedPreferences p = prefs();
        String iv = p.getString("bio_iv", null), ct = p.getString("bio_ct", null), mode = p.getString("bio_mode", "strong");
        if (iv == null || ct == null) { res(call, false, "none"); return; }
        final boolean strong = "strong".equals(mode);
        final byte[] ctBytes = unb64(ct);
        try {
            SecretKey k = getKey(strong ? BIO_ALIAS : GATE_ALIAS);
            if (k == null) { bioClear(); res(call, false, "invalidated"); return; }
            Cipher c;
            try {
                c = decCipher(k, unb64(iv));
            } catch (KeyPermanentlyInvalidatedException e) {
                bioClear(); res(call, false, "invalidated"); return;
            }
            prompt(call, c, strong, "Unlock vault", cph -> {
                byte[] pt = cph.doFinal(ctBytes);
                JSObject o = new JSObject();
                o.put("ok", true);
                o.put("secret", new String(pt, StandardCharsets.UTF_8));
                call.resolve(o);
            });
        } catch (Throwable e) {
            bioClear();
            res(call, false, "invalidated");
        }
    }

    @PluginMethod
    public void bioRemove(final PluginCall call) {
        bioClear();
        res(call, true, null);
    }

    /* ---------- PIN ---------- */

    private void pinStoreSecret(String secret) throws Exception {
        deleteKey(PIN_ALIAS);
        SecretKey k = makeKey(PIN_ALIAS, false);
        Cipher c = encCipher(k);
        byte[] ct = c.doFinal(secret.getBytes(StandardCharsets.UTF_8));
        prefs().edit().putString("pin_iv", b64(c.getIV())).putString("pin_ct", b64(ct)).apply();
    }

    @PluginMethod
    public void pinEnroll(final PluginCall call) {
        String pin = call.getString("pin", "");
        String secret = call.getString("secret", "");
        if (pin == null || !pin.matches("\\d{4}")) { res(call, false, "format"); return; }
        if (secret == null || secret.isEmpty()) { fail(call, "no secret"); return; }
        try {
            pinClear();
            byte[] salt = new byte[16];
            new SecureRandom().nextBytes(salt);
            byte[] h = pinHash(pin, salt);
            pinStoreSecret(secret);
            prefs().edit().putString("pin_salt", b64(salt)).putString("pin_hash", b64(h))
                    .putInt("pin_fails", 0).putLong("pin_until", 0L).apply();
            res(call, true, null);
        } catch (Throwable e) {
            pinClear();
            fail(call, e.getMessage());
        }
    }

    @PluginMethod
    public void pinUnlock(final PluginCall call) {
        String pin = call.getString("pin", "");
        SharedPreferences p = prefs();
        String salt = p.getString("pin_salt", null), hash = p.getString("pin_hash", null);
        String iv = p.getString("pin_iv", null), ct = p.getString("pin_ct", null);
        if (salt == null || hash == null || iv == null || ct == null) { res(call, false, "none"); return; }
        long now = System.currentTimeMillis(), until = p.getLong("pin_until", 0L);
        if (until > now) {
            JSObject o = new JSObject();
            o.put("ok", false); o.put("err", "wait"); o.put("wait", (until - now + 999) / 1000);
            call.resolve(o);
            return;
        }
        try {
            if (pin == null || !pin.matches("\\d{4}")) { res(call, false, "format"); return; }
            boolean good = MessageDigest.isEqual(pinHash(pin, unb64(salt)), unb64(hash));
            if (!good) {
                int fails = p.getInt("pin_fails", 0) + 1;
                if (fails >= MAX_FAILS) {
                    pinClear();
                    res(call, false, "wiped");
                    return;
                }
                long lock = fails >= 3 ? now + 30000L * (fails - 2) : 0L;
                p.edit().putInt("pin_fails", fails).putLong("pin_until", lock).apply();
                JSObject o = new JSObject();
                o.put("ok", false); o.put("err", "bad"); o.put("left", MAX_FAILS - fails);
                call.resolve(o);
                return;
            }
            SecretKey k = getKey(PIN_ALIAS);
            if (k == null) { pinClear(); res(call, false, "invalid"); return; }
            byte[] pt = decCipher(k, unb64(iv)).doFinal(unb64(ct));
            p.edit().putInt("pin_fails", 0).putLong("pin_until", 0L).apply();
            JSObject o = new JSObject();
            o.put("ok", true);
            o.put("secret", new String(pt, StandardCharsets.UTF_8));
            call.resolve(o);
        } catch (Throwable e) {
            pinClear();
            res(call, false, "invalid");
        }
    }

    /** Called after the master password changed (caller already proved the new one). */
    @PluginMethod
    public void pinRewrap(final PluginCall call) {
        String secret = call.getString("secret", "");
        if (secret == null || secret.isEmpty() || prefs().getString("pin_hash", null) == null) { res(call, false, "none"); return; }
        try {
            pinStoreSecret(secret);
            res(call, true, null);
        } catch (Throwable e) {
            pinClear();
            fail(call, e.getMessage());
        }
    }

    @PluginMethod
    public void pinRemove(final PluginCall call) {
        pinClear();
        res(call, true, null);
    }
}
