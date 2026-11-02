package com.gsajith.sonosvolume;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.WindowManager;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Asks the Beam for its volume and mute state several times a second, and shows the
 * volume bar for a moment whenever either changes. Watching the speaker (not the remote)
 * means changes from the Shield remote, TV remote and Sonos app all show up.
 */
public class VolumeOverlayService extends Service {
    private static final String TAG = "SonosVolume";
    private static final long POLL_MS = 250;
    private static final long RETRY_MS = 5000;
    private static final long SHOW_MS = 2000;
    private static final int MAX_RESPONSE_BYTES = 16 * 1024;
    // Samsung TVs show a volume control for HDMI soundbars against the left edge, about
    // 29.5% of the screen wide, with its minus sign 69% of the way down. The bar sits
    // just below it at the same width.
    private static final float WIDTH_FRACTION = 0.295f;
    private static final float TOP_FRACTION = 0.73f;

    private final Handler main = new Handler(Looper.getMainLooper());
    private WindowManager windowManager;
    private VolumeBarView bar;
    private final Runnable hide = () -> bar.animate().alpha(0f).setDuration(300);
    private Thread poller;
    private volatile boolean running;
    private String ip;

    static void start(Context context) {
        context.startForegroundService(new Intent(context, VolumeOverlayService.class));
    }

    @Override
    public void onCreate() {
        super.onCreate();
        // Android TV hides this notification, but a foreground service needs one.
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(
                new NotificationChannel("overlay", "Volume overlay", NotificationManager.IMPORTANCE_MIN));
        Notification notification = new Notification.Builder(this, "overlay")
                .setContentTitle("Showing Sonos volume")
                .setSmallIcon(android.R.drawable.ic_lock_silent_mode_off)
                .build();
        startForeground(1, notification);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        ip = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE).getString(MainActivity.KEY_IP, null);
        if (ip == null) {
            Log.e(TAG, "No Sonos IP saved. Start MainActivity with --es ip <address>.");
            stopSelf();
            return START_NOT_STICKY;
        }
        if (!Settings.canDrawOverlays(this)) {
            Log.e(TAG, "Overlay permission missing. Run: adb shell appops set "
                    + getPackageName() + " SYSTEM_ALERT_WINDOW allow");
            stopSelf();
            return START_NOT_STICKY;
        }
        if (bar == null) addOverlay();
        if (poller == null) {
            running = true;
            poller = new Thread(this::poll, "sonos-poller");
            poller.start();
        }
        return START_STICKY;
    }

    private void addOverlay() {
        DisplayMetrics screen = getResources().getDisplayMetrics();
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                (int) (screen.widthPixels * WIDTH_FRACTION), (int) (40 * screen.density),
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.x = 0;
        lp.y = (int) (screen.heightPixels * TOP_FRACTION);
        bar = new VolumeBarView(this);
        bar.setAlpha(0f);
        windowManager = getSystemService(WindowManager.class);
        windowManager.addView(bar, lp);
    }

    private void poll() {
        int lastVolume = -1;
        boolean lastMuted = false;
        while (running) {
            long wait = POLL_MS;
            try {
                int volume = query("GetVolume", "CurrentVolume");
                boolean muted = query("GetMute", "CurrentMute") == 1;
                // The first reading is only a baseline, so the bar doesn't flash on startup.
                if (lastVolume != -1 && (volume != lastVolume || muted != lastMuted)) {
                    main.post(() -> show(volume, muted));
                }
                lastVolume = volume;
                lastMuted = muted;
            } catch (IOException e) {
                Log.w(TAG, "Couldn't reach Sonos at " + ip + ": " + e.getMessage());
                wait = RETRY_MS;
            }
            try {
                Thread.sleep(wait);
            } catch (InterruptedException e) {
                return;
            }
        }
    }

    private void show(int volume, boolean muted) {
        bar.setLevel(volume, muted);
        bar.animate().cancel();
        bar.setAlpha(1f);
        main.removeCallbacks(hide);
        main.postDelayed(hide, SHOW_MS);
    }

    /** Calls a RenderingControl action on the Beam's local UPnP API and returns one numeric field. */
    private int query(String action, String field) throws IOException {
        String body = "<?xml version=\"1.0\"?>"
                + "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\""
                + " s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\"><s:Body>"
                + "<u:" + action + " xmlns:u=\"urn:schemas-upnp-org:service:RenderingControl:1\">"
                + "<InstanceID>0</InstanceID><Channel>Master</Channel>"
                + "</u:" + action + "></s:Body></s:Envelope>";
        URL url = new URL("http", ip, 1400, "/MediaRenderer/RenderingControl/Control");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        try {
            conn.setConnectTimeout(1000);
            conn.setReadTimeout(1000);
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "text/xml; charset=\"utf-8\"");
            conn.setRequestProperty("SOAPACTION",
                    "\"urn:schemas-upnp-org:service:RenderingControl:1#" + action + "\"");
            try (OutputStream out = conn.getOutputStream()) {
                out.write(body.getBytes(StandardCharsets.UTF_8));
            }
            String response;
            try (InputStream in = conn.getInputStream()) {
                ByteArrayOutputStream buf = new ByteArrayOutputStream();
                byte[] chunk = new byte[1024];
                for (int n; (n = in.read(chunk)) != -1; ) {
                    buf.write(chunk, 0, n);
                    // Real replies are a few hundred bytes.
                    if (buf.size() > MAX_RESPONSE_BYTES) throw new IOException("Response too large");
                }
                response = buf.toString("UTF-8");
            }
            Matcher m = Pattern.compile("<" + field + ">(\\d+)</" + field + ">").matcher(response);
            if (!m.find()) throw new IOException("No " + field + " in response");
            return Integer.parseInt(m.group(1));
        } finally {
            conn.disconnect();
        }
    }

    @Override
    public void onDestroy() {
        running = false;
        if (poller != null) poller.interrupt();
        main.removeCallbacksAndMessages(null);
        if (bar != null) windowManager.removeView(bar);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
