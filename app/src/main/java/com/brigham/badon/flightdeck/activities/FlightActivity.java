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
import android.widget.TextView;

import com.badon.brigham.flightcore.FlightCoreService;
import com.brigham.badon.flightdeck.R;
import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.GridLabelRenderer;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;

import java.util.ArrayList;

public class FlightActivity extends AppCompatActivity implements ServiceConnection {

    private static final String TAG = "FlightActivity";

    private Messenger mService;

    private TextView mVoltageMeter;
    private TextView mSSMeter;
    private TextView mLiftStickMeter;

    private double mLastX = 0;
    private static final double MAX_Y = 12.8;
    private static final double MIN_Y = 11.0;
    private static final double MAX_X = 80.0;
    private static final double MIN_X = 0.0;
    private GraphView mVoltageGraph;
    private LineGraphSeries mSeries;

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

        mVoltageMeter = findViewById(R.id.voltageMeter);
        mSSMeter = findViewById(R.id.ssMeter);
        mLiftStickMeter = findViewById(R.id.liftStickMeter);

        mVoltageGraph = findViewById(R.id.voltageGraph);
        mSeries = new LineGraphSeries<>();
        mVoltageGraph.addSeries(mSeries);

        mVoltageGraph.getViewport().setYAxisBoundsManual(true);
        mVoltageGraph.getViewport().setMaxY(MAX_Y);
        mVoltageGraph.getViewport().setMinY(MIN_Y);
        mVoltageGraph.getViewport().setXAxisBoundsManual(true);
        mVoltageGraph.getViewport().setMaxX(MAX_X);
        mVoltageGraph.getViewport().setMinX(MIN_X);
        mVoltageGraph.getGridLabelRenderer().setHorizontalLabelsVisible(false);
        mVoltageGraph.getGridLabelRenderer().setGridStyle(GridLabelRenderer.GridStyle.HORIZONTAL);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.test:
                initiateTest();
                return true;
            case R.id.disconnect:
                disconnect();
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

    private void disconnect() {
        Message msg = Message.obtain();
        msg.what = FlightCoreService.EVENT_REQUEST_DISCONNECT;

        // Send the message off to the service
        try {
            // TODO: What if mService is null?
            mService.send(msg);
        } catch (RemoteException e) {
            e.printStackTrace();
        }

        Intent intent = new Intent(this, MainActivity.class);
        startActivity(intent);
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

            lift = Math.round(lift * 100.0f) / 100.0f;
            mLiftStickMeter.setText("LS: " + lift);
            return true;
        }
        return false;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent ev) {
        if (mService == null) {
            return false;
        }

        InputDevice device = ev.getDevice();

        if (isValidController(device.getSources()) && ev.getAction() == KeyEvent.ACTION_DOWN) {
            if (ev.getRepeatCount() == 0) {
                switch (ev.getKeyCode()) {
                    case KeyEvent.KEYCODE_BUTTON_A: {
                        // Begin takeoff
                        Log.v(TAG, "Begin takeoff");

                        Message takeoff = Message.obtain();
                        takeoff.what = FlightCoreService.EVENT_TAKEOFF;

                        try {
                            mService.send(takeoff);
                        } catch (RemoteException e) {
                            e.printStackTrace();
                        }
                        break;
                    }
                    case KeyEvent.KEYCODE_BUTTON_B: {
                        // Bring motors back to idle speed
                        Log.v(TAG, "Idle return");

                        Message endTakeoff = Message.obtain();
                        endTakeoff.what = FlightCoreService.EVENT_END_TAKEOFF;

                        try {
                            mService.send(endTakeoff);
                        } catch (RemoteException e) {
                            e.printStackTrace();
                        }
                        break;
                    }
                }
                return true;
            }
        }
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
        /*if (mControlBegun == state || mService == null) {
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
        }*/
    }

    private class ClientHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            Bundle bundle = msg.getData();
            switch (msg.what) {
                case FlightCoreService.EVENT_TELEMETRY: {
                    float voltage = Math.round(bundle.getFloat("voltage") * 100.0f) / 100.0f;

                    mLastX++;
                    mSeries.appendData(new DataPoint(mLastX, voltage), true, 80);

                    mVoltageMeter.setText(voltage + "v");
                    mSSMeter.setText("SS: " + bundle.getInt("signalStrength"));
                    break;
                }
            }
        }
    }
}
