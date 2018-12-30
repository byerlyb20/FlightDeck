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
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NoRouteToHostException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Timer;
import java.util.TimerTask;

public class FlightCoreService extends Service {

    private static final String TAG = "FlightCoreService";

    private static final int ONGOING_NOTIFICATION_ID = 1;

    private static final String NOTIFICATION_CHANNEL_ID = "FLIGHTCORE_SERVICE";

    // Incoming Events
    public static final int EVENT_ESTABLISH_CONNECTION = 0;
    public static final int EVENT_INITIATE_TEST = 1;
    public static final int EVENT_CONTROL = 2;
    public static final int EVENT_REQUEST_DISCONNECT = 3;
    public static final int EVENT_TAKEOFF = 4;
    public static final int EVENT_END_TAKEOFF = 5;

    // Outgoing Events
    public static final int EVENT_CONNECTION_SUCCESS = 0;
    public static final int EVENT_CONNECTION_FAILURE = 1;
    public static final int EVENT_CONNECTION_DISCONNECT = 2;
    public static final int EVENT_TELEMETRY = 3;

    public static final int FAILURE_REASON_OTHER = 0;
    public static final int FAILURE_REASON_HOST_UNREACHABLE = 1;

    private float mLift;
    private float mRoll;
    private float mPitch;
    private float mYaw;

    private int mReportThreadAction = ReportTimer.ACTION_DO_NOTHING;

    private Messenger mMessenger;
    private Messenger mClient;
    private Timer mReportTimer;
    private Timer mTelemetryTimer;

    private DatagramSocket mSocket;
    private DatagramPacket mPacketIn = new DatagramPacket(new byte[8], 8);

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
                        byte eventKey = '1';
                        byte[] payload = {eventKey};
                        DatagramPacket packet = new DatagramPacket(payload, payload.length);
                        mSocket.send(packet);
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
                    // Signal to the reporting thread to send a takeoff request on the next loop
                    mReportThreadAction = ReportTimer.ACTION_BEGIN_TAKEOFF;
                    break;
                }
                case EVENT_END_TAKEOFF: {
                    // Signal to the reporting thread to send a takeoff-arrest request on the next
                    // loop
                    mReportThreadAction = ReportTimer.ACTION_END_TAKEOFF;
                    break;
                }
            }
        }
    }

    private void establishConnection(InetAddress addr, int port) {
        if (mSocket == null || !mSocket.isConnected()) {
            try {
                InetSocketAddress target = new InetSocketAddress(addr, port);
                mSocket = new DatagramSocket(target);

                beginRegularDataOut();
                beginTelemetryChecks();

                try {
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
        // End regular intervals
        stopRegularDataOut();

        // Stop checking for telemetry
        stopTelemetryChecks();

        // End socket communication
        mSocket.close();

        // Stop service
        stopSelf();
    }

    private class ReportTimer extends TimerTask {

        public static final int ACTION_DO_NOTHING = 0;
        public static final int ACTION_BEGIN_TAKEOFF = 1;
        public static final int ACTION_CONTROLLER_REPORT = 2;
        public static final int ACTION_END_TAKEOFF = 3;

        @Override
        public void run() {
            sendDataOut();
        }
    }

    private class TelemetryTimer extends TimerTask {

        @Override
        public void run() {
            checkTelemetry();
        }
    }

    private void sendDataOut() {
        switch (mReportThreadAction) {
            case ReportTimer.ACTION_BEGIN_TAKEOFF: {
                try {
                    byte eventKey = '2';
                    byte[] payload = {eventKey};
                    DatagramPacket packet = new DatagramPacket(payload, payload.length);
                    mSocket.send(packet);

                    // Now that we have requested a takeoff, we periodically send controller
                    // values
                    mReportThreadAction = ReportTimer.ACTION_CONTROLLER_REPORT;
                } catch (IOException e) {
                    e.printStackTrace();
                }
                break;
            }
            case ReportTimer.ACTION_CONTROLLER_REPORT: {
                try {
                    ByteBuffer buffer = ByteBuffer.allocate(17);

                    byte eventKey = '0';
                    buffer.put(eventKey);

                    buffer.putFloat(mLift);
                    buffer.putFloat(mRoll);
                    buffer.putFloat(mPitch);
                    buffer.putFloat(mYaw);

                    byte[] payload = buffer.array();
                    DatagramPacket packet = new DatagramPacket(payload, payload.length);
                    mSocket.send(packet);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                break;
            }
            case ReportTimer.ACTION_END_TAKEOFF: {
                try {
                    byte eventKey = '4';
                    byte[] payload = {eventKey};
                    DatagramPacket packet = new DatagramPacket(payload, payload.length);
                    mSocket.send(packet);

                    // Now that we have requested a takeoff-arrest, we stop sending controller
                    // values
                    mReportThreadAction = ReportTimer.ACTION_DO_NOTHING;
                } catch (IOException e) {
                    e.printStackTrace();
                }
                break;
            }
        }
    }

    private void checkTelemetry() {
        try {
            // Wait until we receive a packet (this call blocks)
            mSocket.receive(mPacketIn);

            byte[] voltageRaw = Arrays.copyOfRange(mPacketIn.getData(), 0, 4);
            float voltage = ByteBuffer.wrap(voltageRaw).order(ByteOrder.LITTLE_ENDIAN)
                    .getFloat();

            byte[] ssRaw = Arrays.copyOfRange(mPacketIn.getData(), 4, 8);
            int signalStrength = ByteBuffer.wrap(ssRaw).order(ByteOrder.LITTLE_ENDIAN)
                    .getInt();

            Message telemetry = Message.obtain();
            telemetry.what = EVENT_TELEMETRY;

            Bundle payload = new Bundle();
            payload.putFloat("voltage", voltage);
            payload.putInt("signalStrength", signalStrength);

            telemetry.setData(payload);

            try {
                mClient.send(telemetry);
            } catch (RemoteException f) {
                f.printStackTrace();
            }
        } catch (IOException e) {
            e.printStackTrace();
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

        Looper serviceLooper = thread.getLooper();
        ServiceHandler serviceHandler = new ServiceHandler(serviceLooper);
        mMessenger = new Messenger(serviceHandler);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mMessenger.getBinder();
    }

    private void beginRegularDataOut() {
        if (mReportTimer != null) {
            mReportTimer.cancel();
        }
        ReportTimer reportTimer = new ReportTimer();
        this.mReportTimer = new Timer();
        this.mReportTimer.schedule(reportTimer, 0, 40);
    }

    private void beginTelemetryChecks() {
        if (mTelemetryTimer != null) {
            mTelemetryTimer.cancel();
        }
        ReportTimer telemetryTimer = new ReportTimer();
        this.mTelemetryTimer = new Timer();
        this.mTelemetryTimer.schedule(telemetryTimer, 0, 40);
    }

    private void stopRegularDataOut() {
        mReportTimer.cancel();
    }

    private void stopTelemetryChecks() {
        mTelemetryTimer.cancel();
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
