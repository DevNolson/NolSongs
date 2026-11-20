package com.nolson.nolsongs;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Base64;
import androidx.core.app.NotificationCompat;
import androidx.media.app.NotificationCompat.MediaStyle;

public class MusicService extends Service {
    public static final String CHANNEL_ID = "nolsongs_playback";
    public static final String ACTION_UPDATE = "ACTION_UPDATE";
    public static final String ACTION_PREV = "ACTION_PREV";
    public static final String ACTION_TOGGLE = "ACTION_TOGGLE";
    public static final String ACTION_NEXT = "ACTION_NEXT";

    private MediaSessionCompat mediaSession;
    private PowerManager.WakeLock wakeLock;
    private String curTitle = "NolSongs";
    private String curArtist = "Nolson";
    private boolean isPlaying = false;
    private Bitmap curArt = null;

    @Override
    public void onCreate() {
        super.onCreate();
        createChannel();

        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        if (pm != null) {
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "NolSongs::AudioWakeLock");
        }

        mediaSession = new MediaSessionCompat(this, "NolSongsSession");
        mediaSession.setCallback(new MediaSessionCompat.Callback() {
            @Override public void onPlay() { sendControl("toggle"); }
            @Override public void onPause() { sendControl("toggle"); }
            @Override public void onSkipToNext() { sendControl("next"); }
            @Override public void onSkipToPrevious() { sendControl("prev"); }
        });
        mediaSession.setActive(true);
    }

    private void sendControl(String cmd) {
        Intent i = new Intent("NOLSONGS_MEDIA_CMD");
        i.setPackage(getPackageName());
        i.putExtra("cmd", cmd);
        sendBroadcast(i);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getAction() != null) {
            String act = intent.getAction();
            if (ACTION_PREV.equals(act)) { sendControl("prev"); return START_STICKY; }
            if (ACTION_TOGGLE.equals(act)) { sendControl("toggle"); return START_STICKY; }
            if (ACTION_NEXT.equals(act)) { sendControl("next"); return START_STICKY; }

            if (ACTION_UPDATE.equals(act)) {
                String t = intent.getStringExtra("title");
                String a = intent.getStringExtra("artist");
                String b64 = intent.getStringExtra("art");
                if (t != null && !t.isEmpty()) curTitle = t;
                if (a != null && !a.isEmpty()) curArtist = a;
                isPlaying = intent.getBooleanExtra("playing", false);

                if (b64 != null && b64.contains(",")) {
                    try {
                        byte[] bytes = Base64.decode(b64.split(",")[1], Base64.DEFAULT);
                        Bitmap bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                        if (bmp != null) curArt = bmp;
                    } catch (Exception ignored) {}
                }
            }
        }

        if (isPlaying) {
            if (wakeLock != null && !wakeLock.isHeld()) wakeLock.acquire(4 * 60 * 60 * 1000L);
        } else {
            if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        }

        updateSessionAndNotification();
        return START_STICKY;
    }

    private void updateSessionAndNotification() {
        Bitmap art = curArt != null ? curArt : BitmapFactory.decodeResource(getResources(), android.R.drawable.sym_def_app_icon);

        MediaMetadataCompat.Builder meta = new MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, curTitle)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, curArtist)
                .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, art);
        mediaSession.setMetadata(meta.build());

        long actions = PlaybackStateCompat.ACTION_PLAY | PlaybackStateCompat.ACTION_PAUSE
                | PlaybackStateCompat.ACTION_PLAY_PAUSE | PlaybackStateCompat.ACTION_SKIP_TO_NEXT
                | PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS;
        int state = isPlaying ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED;
        mediaSession.setPlaybackState(new PlaybackStateCompat.Builder()
                .setActions(actions)
                .setState(state, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1.0f)
                .build());

        Intent openApp = new Intent(this, MainActivity.class);
        PendingIntent contentPi = PendingIntent.getActivity(this, 0, openApp,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        PendingIntent prevPi = PendingIntent.getService(this, 1,
                new Intent(this, MusicService.class).setAction(ACTION_PREV), PendingIntent.FLAG_IMMUTABLE);
        PendingIntent togglePi = PendingIntent.getService(this, 2,
                new Intent(this, MusicService.class).setAction(ACTION_TOGGLE), PendingIntent.FLAG_IMMUTABLE);
        PendingIntent nextPi = PendingIntent.getService(this, 3,
                new Intent(this, MusicService.class).setAction(ACTION_NEXT), PendingIntent.FLAG_IMMUTABLE);

        int playIcon = isPlaying ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play;

        Notification notif = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setLargeIcon(art)
                .setContentTitle(curTitle)
                .setContentText(curArtist)
                .setContentIntent(contentPi)
                .setOnlyAlertOnce(true)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .addAction(android.R.drawable.ic_media_previous, "Sebelumnya", prevPi)
                .addAction(playIcon, isPlaying ? "Jeda" : "Putar", togglePi)
                .addAction(android.R.drawable.ic_media_next, "Berikutnya", nextPi)
                .setStyle(new MediaStyle()
                        .setMediaSession(mediaSession.getSessionToken())
                        .setShowActionsInCompactView(0, 1, 2))
                .build();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(101, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        } else {
            startForeground(101, notif);
        }
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID, "Pemutar Musik NolSongs", NotificationManager.IMPORTANCE_LOW);
            ch.setShowBadge(false);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(ch);
        }
    }

    @Override
    public void onDestroy() {
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        if (mediaSession != null) mediaSession.release();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
