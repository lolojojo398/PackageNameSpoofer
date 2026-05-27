package com.spoofer.har.proxy;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import com.spoofer.har.MainActivity;

public class LocalProxyService extends Service {
    private static final String CHANNEL_ID = "proxy_service_channel";
    private static final int NOTIFICATION_ID = 2;
    private static final String TAG = "LocalProxyService";
    private HttpProxyServer proxyServer;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForegroundNotification();
        startProxy();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        stopProxy();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Proxy Service", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Local proxy service");
            NotificationManager mgr = getSystemService(NotificationManager.class);
            if (mgr != null) {
                mgr.createNotificationChannel(channel);
            }
        }
    }

    private void startForegroundNotification() {
        Intent ni = new Intent(this, MainActivity.class);
        ni.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi = PendingIntent.getActivity(this, 0, ni, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }
        startForeground(NOTIFICATION_ID, builder.setContentTitle("HAR Packet Sender")
                .setContentText("Local proxy running on port 18080")
                .setSmallIcon(android.R.drawable.ic_menu_send)
                .setContentIntent(pi)
                .setOngoing(true)
                .build());
    }

    private void startProxy() {
        try {
            this.proxyServer = new HttpProxyServer(this, 18080);
            this.proxyServer.start();
            Log.i(TAG, "Local proxy started on port 18080");
        } catch (Exception e) {
            Log.e(TAG, "Failed to start proxy", e);
        }
    }

    private void stopProxy() {
        if (this.proxyServer != null) {
            try {
                this.proxyServer.stop();
            } catch (Exception e) {
                Log.e(TAG, "Error stopping proxy", e);
            }
            this.proxyServer = null;
        }
        try {
            stopForeground(true);
        } catch (Exception e) {
            // ignore
        }
    }
}
