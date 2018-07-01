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
import android.os.RemoteException;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NoRouteToHostException;
import java.net.Socket;

public class FlightCoreService extends Service {

    private static final String TAG = "FlightCoreService";

    private static final int ONGOING_NOTIFICATION_ID = 1;

    private static final String NOTIFICATION_CHANNEL_ID = "FLIGHTCORE_SERVICE";

    public static final int EVENT_ESTABLISH_CONNECTION = 0;
    public static final int EVENT_INITIATE_TEST = 1;
    public static final int EVENT_CONNECTION_SUCCESS = 2;
    public static final int EVENT_CONNECTION_FAILURE = 3;

    public static final int FAILURE_REASON_OTHER = 0;
    public static final int FAILURE_REASON_HOST_UNREACHABLE = 1;

    private Messenger mMessenger;
    private Messenger mClient;

    private Socket mSocket;

    public FlightCoreService() {
    }

    private class ServiceHandler extends Handler {
        ServiceHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            // Check to see if we should be taking a Messenger object
            if (msg.replyTo != null) {
                mClient = msg.replyTo;
                return;
            }

            // Perform traditional checks
            Bundle bundle = msg.getData();
            switch (msg.what) {
                case EVENT_ESTABLISH_CONNECTION: {
                    if (mSocket == null || !mSocket.isConnected()) {
                        try {
                            String ipAddr = bundle.getString("ipAddr");
                            InetAddress addr = InetAddress.getByName(ipAddr);
                            int port = bundle.getInt("port");

                            mSocket = new Socket();
                            InetSocketAddress target = new InetSocketAddress(addr, port);
                            mSocket.connect(target);

                            try {
                                Log.v(TAG, "Socket Connection Success");
                                Message reply = Message.obtain();
                                reply.what = EVENT_CONNECTION_SUCCESS;
                                mClient.send(reply);
                            } catch (RemoteException e) {
                                e.printStackTrace();
                            }
                        } catch (IOException e) {
                            e.printStackTrace();

                            try {
                                Message reply = Message.obtain();
                                reply.what = EVENT_CONNECTION_FAILURE;

                                Bundle details = new Bundle();
                                if (e instanceof NoRouteToHostException) {
                                    details.putInt("reason", FAILURE_REASON_HOST_UNREACHABLE);
                                }
                                reply.setData(details);

                                mClient.send(reply);
                            } catch (RemoteException f) {
                                f.printStackTrace();
                            }
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

        Looper mServiceLooper = thread.getLooper();
        ServiceHandler mServiceHandler = new ServiceHandler(mServiceLooper);
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
