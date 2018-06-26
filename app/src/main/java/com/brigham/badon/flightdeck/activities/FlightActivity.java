package com.brigham.badon.flightdeck.activities;

import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;

import com.badon.brigham.flightcore.FlightCoreService;
import com.brigham.badon.flightdeck.R;

public class FlightActivity extends AppCompatActivity implements ServiceConnection {

    private static final String TAG = "FlightActivity";

    private Messenger mService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_flight);

        // Setup the toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        Intent intent = new Intent(this, FlightCoreService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }

    @Override
    public void onStart() {
        super.onStart();

        Intent intent = new Intent(this, FlightCoreService.class);
        bindService(intent, this, 0);
    }

    @Override
    public void onStop() {
        super.onStop();

        unbindService(this);
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        Log.v(TAG, "Service connected");

        mService = new Messenger(service);

        ClientHandler handler = new ClientHandler();
        Messenger client = new Messenger(handler);
        Message msg = Message.obtain();
        msg.replyTo = client;
        try {
            mService.send(msg);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        Log.v(TAG, "Service disconnected");
    }

    private class ClientHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {

        }
    }
}
