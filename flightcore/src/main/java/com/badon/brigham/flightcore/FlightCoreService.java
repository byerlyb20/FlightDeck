package com.badon.brigham.flightcore;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.support.v4.app.NotificationCompat;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;

public class FlightCoreService extends Service {

    private static final int DRONE_PORT = 8080;

    private static final int ONGOING_NOTIFICATION_ID = 0;

    private static final String NOTIFICATION_CHANNEL_ID = "FLIGHTCORE_SERVICE";

    private static final int EVENT_ESTABLISH_CONNECTION = 0;
    private static final int EVENT_INITIATE_TEST = 1;

    private Looper mServiceLooper;
    private ServiceHandler mServiceHandler;
    private Messenger mMessenger;

    private Socket mSocket;

    public FlightCoreService() {
    }

    private class ServiceHandler extends Handler {
        public ServiceHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            Bundle bundle = msg.getData();
            int event = bundle.getInt("event");
            switch (event) {
                case EVENT_ESTABLISH_CONNECTION: {
                    if (mSocket == null) {
                        try {
                            String ipAddr = bundle.getString("ipAddr");
                            InetAddress addr = InetAddress.getByName(ipAddr);

                            mSocket = new Socket();
                            InetSocketAddress target = new InetSocketAddress(addr, DRONE_PORT);
                            mSocket.connect(target);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                    break;
                }
                case EVENT_INITIATE_TEST: {
                    try {
                        OutputStream os = mSocket.getOutputStream();
                        byte eventKey = '1';
                        byte[] payload = {eventKey};
                        os.write(payload);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    break;
                }
            }
        }
    }

    @Override
    public void onCreate() {
        createNotificationChannel();

        Notification notification = new NotificationCompat.Builder(this,
                NOTIFICATION_CHANNEL_ID)
                .setContentTitle(getText(R.string.service_notification_title))
                .setContentText(getText(R.string.service_notification_description))
                .setSmallIcon(R.drawable.ic_flight_black_24dp)
                .build();

        startForeground(ONGOING_NOTIFICATION_ID, notification);

        HandlerThread thread = new HandlerThread("FlightCoreHandlerThread");
        thread.start();

        mServiceLooper = thread.getLooper();
        mServiceHandler = new ServiceHandler(mServiceLooper);
        mMessenger = new Messenger(mServiceHandler);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mMessenger.getBinder();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = getString(R.string.service_channel_name);
            String description = getString(R.string.service_channel_description);
            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            NotificationChannel channel = new NotificationChannel(NOTIFICATION_CHANNEL_ID, name,
                    importance);
            channel.setDescription(description);
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }
}
