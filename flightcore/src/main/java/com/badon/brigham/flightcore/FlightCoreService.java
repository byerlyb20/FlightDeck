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
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.Timer;
import java.util.TimerTask;

public class FlightCoreService extends Service implements Runnable {

    private static final String TAG = "FlightCoreService";

    private static final int ONGOING_NOTIFICATION_ID = 1;

    private static final String NOTIFICATION_CHANNEL_ID = "FLIGHTCORE_SERVICE";

    // Incoming Events
    public static final int EVENT_ESTABLISH_CONNECTION = 0;
    public static final int EVENT_INITIATE_TEST = 1;
    public static final int EVENT_CONTROL = 2;
    public static final int EVENT_REQUEST_DISCONNECT = 3;
    public static final int EVENT_TAKEOFF = 4;
    public static final int EVENT_IDLE_RETURN = 5;

    // Outgoing Events
    public static final int EVENT_CONNECTION_SUCCESS = 0;
    public static final int EVENT_CONNECTION_FAILURE = 1;
    public static final int EVENT_CONNECTION_DISCONNECT = 2;

    public static final int FAILURE_REASON_OTHER = 0;
    public static final int FAILURE_REASON_HOST_UNREACHABLE = 1;

    private float mLift;
    private float mRoll;
    private float mPitch;
    private float mYaw;

    private ServiceHandler mServiceHandler;
    private Messenger mMessenger;
    private Messenger mClient;
    private ReportTimer mReportTimer;
    private Timer mTimer;

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
                    try {
                        String ipAddr = bundle.getString("ipAddr");
                        InetAddress addr = InetAddress.getByName(ipAddr);
                        int port = bundle.getInt("port");

                        establishConnection(addr, port);
                    } catch (UnknownHostException e) {
                        e.printStackTrace();

                        Message reply = Message.obtain();
                        reply.what = EVENT_CONNECTION_FAILURE;

                        try {
                            mClient.send(reply);
                        } catch (RemoteException f) {
                            f.printStackTrace();
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
                case EVENT_CONTROL: {
                    mLift = bundle.getFloat("lift");
                    mRoll = bundle.getFloat("roll");
                    mPitch = bundle.getFloat("pitch");
                    mYaw = bundle.getFloat("yaw");
                    break;
                }
                case EVENT_REQUEST_DISCONNECT: {
                    disconnect();
                    break;
                }
                case EVENT_TAKEOFF: {
                    mTimer.schedule(mReportTimer, 0, 20);
                    try {
                        OutputStream os = mSocket.getOutputStream();
                        byte eventKey = '2';
                        byte[] payload = {eventKey};
                        os.write(payload);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    break;
                }
                case EVENT_IDLE_RETURN: {
                    mTimer.cancel();
                    try {
                        OutputStream os = mSocket.getOutputStream();
                        byte eventKey = '3';
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

    private void establishConnection(InetAddress addr, int port) {
        if (mSocket == null || !mSocket.isConnected()) {
            try {
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
    }

    private void disconnect() {
        // End socket communication
        if (mSocket != null && !mSocket.isClosed()) {
            try {
                mSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        // End regular intervals
        mTimer.cancel();

        // Stop service
        stopSelf();
    }

    private class ReportTimer extends TimerTask {

        @Override
        public void run() {
            // Send the latest controller values, done 10 times a second
            if (mSocket != null) {
                try {
                    OutputStream os = mSocket.getOutputStream();
                    byte eventKey = '0';
                    byte[] payload = {eventKey};
                    os.write(payload);

                    byte[] lift = ByteBuffer.allocate(4).putFloat(mLift).array();
                    os.write(lift);
                    byte[] roll = ByteBuffer.allocate(4).putFloat(mRoll).array();
                    os.write(roll);
                    byte[] pitch = ByteBuffer.allocate(4).putFloat(mPitch).array();
                    os.write(pitch);
                    byte[] yaw = ByteBuffer.allocate(4).putFloat(mYaw).array();
                    os.write(yaw);
                } catch (IOException e) {
                    e.printStackTrace();
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
        mServiceHandler = new ServiceHandler(mServiceLooper);
        mServiceHandler.post(this);
        mMessenger = new Messenger(mServiceHandler);

        mReportTimer = new ReportTimer();
        mTimer = new Timer();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mMessenger.getBinder();
    }

    @Override
    public void run() {
        if (mSocket != null) {
            // TODO: Implement drone to FlightCore communication
            // (needs to be implemented in firmware as well)
            /*try {
                InputStream is = mSocket.getInputStream();
            } catch (IOException e) {
                e.printStackTrace();
            }*/
        }
        mServiceHandler.post(this);
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
