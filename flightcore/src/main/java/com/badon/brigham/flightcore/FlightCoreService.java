package com.badon.brigham.flightcore;

import android.app.Service;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;

public class FlightCoreService extends Service {

    private static final int DRONE_PORT = 8080;

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
}
