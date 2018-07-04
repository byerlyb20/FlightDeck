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
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;

import com.badon.brigham.flightcore.FlightCoreService;
import com.brigham.badon.flightdeck.R;

import java.util.ArrayList;

public class FlightActivity extends AppCompatActivity implements ServiceConnection {

    private static final String TAG = "FlightActivity";

    private Messenger mService;
    private boolean mControlBegun = false;

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
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.test:
                initiateTest();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void initiateTest() {
        Message msg = Message.obtain();
        msg.what = FlightCoreService.EVENT_INITIATE_TEST;

        // Send the message off to the service
        try {
            // TODO: What if mService is null?
            mService.send(msg);
        } catch (RemoteException e) {
            e.printStackTrace();
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
    public void onResume() {
        super.onResume();

        setControlState(true);
    }

    @Override
    public void onPause() {
        super.onPause();

        setControlState(false);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.activity_flight, menu);
        return true;
    }

    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent ev) {
        if (mService == null) {
            return false;
        }

        InputDevice device = ev.getDevice();

        if (isValidController(device.getSources()) && ev.getAction() ==
                MotionEvent.ACTION_MOVE) {
            float lift = -ev.getAxisValue(MotionEvent.AXIS_Y);
            float roll = ev.getAxisValue(MotionEvent.AXIS_RX);
            float pitch = -ev.getAxisValue(MotionEvent.AXIS_RY);
            float yaw = ev.getAxisValue(MotionEvent.AXIS_X);

            Bundle payload = new Bundle();
            payload.putFloat("lift", lift);
            payload.putFloat("roll", roll);
            payload.putFloat("pitch", pitch);
            payload.putFloat("yaw", yaw);

            Message msg = Message.obtain();
            msg.what = FlightCoreService.EVENT_CONTROL;
            msg.setData(payload);
            try {
                mService.send(msg);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        return false;
    }

    /**
     * FlightDeck only supports controllers with analog control sticks, a d-pad, and ABXY buttons
     * @return true if the input device is valid, false otherwise
     */
    private boolean isValidController(int sources) {
        boolean gamepad = (sources & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD;
        boolean joystick = (sources & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK;
        boolean dpad = (sources & InputDevice.SOURCE_DPAD) == InputDevice.SOURCE_DPAD;
        return joystick;
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

        setControlState(true);
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        Log.v(TAG, "Service disconnected");
    }

    private void setControlState(boolean state) {
        if (mControlBegun == state || mService == null) {
            return;
        }
        Message beginControl = Message.obtain();
        beginControl.what = (state ? FlightCoreService.EVENT_INFORM_CONTROL_BEGIN :
                FlightCoreService.EVENT_INFORM_CONTROL_STOP);
        try {
            mService.send(beginControl);
            mControlBegun = state;
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    private class ClientHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {

        }
    }
}
