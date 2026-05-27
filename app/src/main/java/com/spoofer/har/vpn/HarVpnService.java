package com.spoofer.har.vpn;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.net.VpnService;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import com.spoofer.har.MainActivity;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.net.InetAddress;
import java.nio.ByteBuffer;

public class HarVpnService extends VpnService implements Runnable {
    private static final String TAG = "HarVpnService";
    private static final String CHANNEL_ID = "vpn_service_channel";
    private static final int NOTIFICATION_ID = 1;
    private Thread vpnThread;
    private ParcelFileDescriptor vpnInterface;
    private boolean running = false;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForegroundNotification();
        startVpn();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        stopVpn();
        super.onDestroy();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "VPN Service", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("HAR VPN packet capture service");
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
                .setContentText("VPN packet capture active")
                .setSmallIcon(android.R.drawable.ic_menu_send)
                .setContentIntent(pi)
                .setOngoing(true)
                .build());
    }

    private void startVpn() {
        if (running) return;
        running = true;
        vpnThread = new Thread(this, "HarVpnThread");
        vpnThread.start();
    }

    private void stopVpn() {
        running = false;
        if (vpnThread != null) {
            vpnThread.interrupt();
            vpnThread = null;
        }
        if (vpnInterface != null) {
            try { vpnInterface.close(); } catch (Exception e) {}
            vpnInterface = null;
        }
        try { stopForeground(true); } catch (Exception e) {}
    }

    @Override
    public void run() {
        try {
            Builder builder = new Builder();
            builder.setSession("HAR Packet Sender");
            builder.addAddress("10.0.0.2", 32);
            builder.addRoute("0.0.0.0", 0);
            builder.addDnsServer("8.8.8.8");
            builder.setMtu(1500);
            vpnInterface = builder.establish();
            if (vpnInterface == null) {
                Log.e(TAG, "Failed to establish VPN interface");
                return;
            }
            processPackets();
        } catch (Exception e) {
            Log.e(TAG, "VPN error", e);
        }
    }

    private void processPackets() {
        FileInputStream in = new FileInputStream(vpnInterface.getFileDescriptor());
        FileOutputStream out = new FileOutputStream(vpnInterface.getFileDescriptor());
        byte[] data = new byte[32767];

        while (running) {
            try {
                int length = in.read(data);
                if (length <= 0) continue;

                int ipHeaderLen = (data[0] & 0x0F) * 4;
                int protocol = data[9] & 0xFF;

                if (protocol != 6) {
                    out.write(data, 0, length);
                    continue;
                }

                int tcpHeaderStart = ipHeaderLen;
                int dstPort = ((data[tcpHeaderStart + 2] & 0xFF) << 8) | (data[tcpHeaderStart + 3] & 0xFF);

                // Intercept HTTP (port 80) AND HTTPS (port 443) traffic
                if (dstPort == 80 || dstPort == 443) {
                    InetAddress proxyAddr = InetAddress.getByName("127.0.0.1");
                    int proxyPort = 18080;

                    byte[] proxyIp = proxyAddr.getAddress();
                    System.arraycopy(proxyIp, 0, data, 16, 4);

                    data[tcpHeaderStart + 2] = (byte) ((proxyPort >> 8) & 0xFF);
                    data[tcpHeaderStart + 3] = (byte) (proxyPort & 0xFF);

                    recalculateIpChecksum(data);
                    recalculateTcpChecksum(data, length, ipHeaderLen);
                }

                out.write(data, 0, length);
            } catch (Exception e) {
                if (running) {
                    Log.e(TAG, "Packet processing error", e);
                }
            }
        }
    }

    private void recalculateIpChecksum(byte[] data) {
        int ipHeaderLen = (data[0] & 0x0F) * 4;
        data[10] = 0;
        data[11] = 0;

        int sum = 0;
        for (int i = 0; i < ipHeaderLen; i += 2) {
            sum += ((data[i] & 0xFF) << 8) | (data[i + 1] & 0xFF);
        }
        while ((sum >> 16) != 0) {
            sum = (sum & 0xFFFF) + (sum >> 16);
        }
        sum = ~sum & 0xFFFF;
        data[10] = (byte) ((sum >> 8) & 0xFF);
        data[11] = (byte) (sum & 0xFF);
    }

    private void recalculateTcpChecksum(byte[] data, int totalLen, int ipHeaderLen) {
        int srcIp = ((data[12] & 0xFF) << 24) | ((data[13] & 0xFF) << 16) | ((data[14] & 0xFF) << 8) | (data[15] & 0xFF);
        int dstIp = ((data[16] & 0xFF) << 24) | ((data[17] & 0xFF) << 16) | ((data[18] & 0xFF) << 8) | (data[19] & 0xFF);

        data[ipHeaderLen + 16] = 0;
        data[ipHeaderLen + 17] = 0;

        int tcpLen = totalLen - ipHeaderLen;
        int sum = 0;

        sum += (srcIp >> 16) & 0xFFFF;
        sum += srcIp & 0xFFFF;
        sum += (dstIp >> 16) & 0xFFFF;
        sum += dstIp & 0xFFFF;
        sum += 6;
        sum += tcpLen;

        for (int i = ipHeaderLen; i < totalLen; i += 2) {
            if (i + 1 < totalLen) {
                sum += ((data[i] & 0xFF) << 8) | (data[i + 1] & 0xFF);
            } else {
                sum += (data[i] & 0xFF) << 8;
            }
        }
        while ((sum >> 16) != 0) {
            sum = (sum & 0xFFFF) + (sum >> 16);
        }
        sum = ~sum & 0xFFFF;
        data[ipHeaderLen + 16] = (byte) ((sum >> 8) & 0xFF);
        data[ipHeaderLen + 17] = (byte) (sum & 0xFF);
    }
}
