package com.fezzy.festersbrowser;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;

/**
 * Optional keep-alive: a foreground service (media playback type) that holds a partial wake lock
 * while you play media, with a visible Stop action. Ends by itself after 4 hours.
 */
public class FBKeepAliveService extends Service {
    public static final String ACTION_START = "com.fezzy.festersbrowser.KEEPALIVE_START";
    public static final String ACTION_STOP = "com.fezzy.festersbrowser.KEEPALIVE_STOP";
    /** V1.14.1: started when you leave the app so Android keeps the process (and your open pages) alive. */
    public static final String ACTION_AUTO = "com.fezzy.festersbrowser.KEEPALIVE_AUTO";
    private static final long AUTO_MAX_MS = 12L * 60 * 60 * 1000;
    /** Set from the app setting "Keep running when you leave". */
    public static volatile boolean autoEnabled = true;
    /** True while the running service was started by ACTION_AUTO rather than the media wake lock button. */
    public static volatile boolean auto = false;

    public static void startAuto(Context c) {
        if (!autoEnabled) return;
        if (running && !auto) return; // the manual media keep-alive already covers it
        try {
            Intent i = new Intent(c, FBKeepAliveService.class);
            i.setAction(ACTION_AUTO);
            if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i); else c.startService(i);
        } catch (Throwable ignored) { }
    }

    /** Stops only a service that startAuto started; a manual media keep-alive is left running. */
    public static void stopAuto(Context c) {
        if (!(running && auto)) return;
        try { c.stopService(new Intent(c, FBKeepAliveService.class)); } catch (Throwable ignored) { }
    }
    private static final String CHANNEL = "fb_keepalive";
    private static final int NOTE_ID = 7322;
    private static final long MAX_MS = 4L * 60 * 60 * 1000;
    public static volatile boolean running = false;

    private PowerManager.WakeLock lock;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable timeout = () -> stopSelf();

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String a = intent == null ? null : intent.getAction();
        if (ACTION_STOP.equals(a)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        boolean isAuto = ACTION_AUTO.equals(a);
        if (!isAuto && running && auto) { auto = false; } // manual start takes over from the automatic one
        Notification n = buildNote(isAuto);
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTE_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        } else {
            startForeground(NOTE_ID, n);
        }
        if (!isAuto && (lock == null || !lock.isHeld())) {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm != null) {
                lock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "FestersBrowser:KeepAlive");
                lock.acquire(MAX_MS);
            }
        }
        handler.removeCallbacks(timeout);
        handler.postDelayed(timeout, isAuto ? AUTO_MAX_MS : MAX_MS);
        auto = isAuto;
        running = true;
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        running = false;
        auto = false;
        handler.removeCallbacks(timeout);
        try { if (lock != null && lock.isHeld()) lock.release(); } catch (Throwable ignored) { }
        lock = null;
        super.onDestroy();
    }

    private Notification buildNote(boolean isAuto) {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26 && nm != null && nm.getNotificationChannel(CHANNEL) == null) {
            NotificationChannel c = new NotificationChannel(CHANNEL, "Festers keep-alive", NotificationManager.IMPORTANCE_LOW);
            c.setDescription("Shown while Festers Browser keeps media playing in the background. Ends after 4 hours.");
            nm.createNotificationChannel(c);
        }
        int pf = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);
        Intent open = getPackageManager().getLaunchIntentForPackage(getPackageName());
        PendingIntent tap = open == null ? null : PendingIntent.getActivity(this, 0, open, pf);
        Intent stop = new Intent(this, FBKeepAliveService.class);
        stop.setAction(ACTION_STOP);
        PendingIntent stopPi = PendingIntent.getService(this, 1, stop, pf);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CHANNEL) : new Notification.Builder(this);
        b.setContentTitle(isAuto ? "Festers Browser is keeping your tabs" : "Festers Browser is staying awake")
         .setContentText(isAuto ? "Your tabs stay open while you are away. Tap Stop to let Android close the app." : "Tap Stop when you are done. Ends by itself after 4 hours.")
         .setSmallIcon(android.R.drawable.ic_media_play)
         .setOngoing(true);
        if (Build.VERSION.SDK_INT < 26) b.setPriority(Notification.PRIORITY_LOW);
        if (tap != null) b.setContentIntent(tap);
        b.addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPi);
        return b.build();
    }
}
