package com.brigham.badon.flightdeck.activities;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;

import com.badon.brigham.flightcore.FlightCoreService;
import com.brigham.badon.flightdeck.R;

public class SelectDroneActivity extends AppCompatActivity implements ServiceConnection {

    private static final String TAG = "SelectDroneActivity";
    private static int SHORT_ANIMATION_DURATION;

    private Messenger mService;

    private LinearLayout mContent;
    private EditText mDroneIpInput;
    private EditText mDronePortInput;
    private ProgressBar mProgressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_select_drone);

        // Setup the toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        Intent intent = new Intent(this, FlightCoreService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }

        mContent = findViewById(R.id.contentView);
        mDroneIpInput = findViewById(R.id.droneIpInput);
        mDronePortInput = findViewById(R.id.dronePortInput);
        mProgressBar = findViewById(R.id.progressBar);

        SHORT_ANIMATION_DURATION = getResources().getInteger(android.R.integer.config_shortAnimTime);
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
            switch (msg.what) {
                case FlightCoreService.EVENT_CONNECTION_SUCCESS: {
                    // Open the flight control panel in FlightActivity
                    Intent intent = new Intent(SelectDroneActivity.this,
                            FlightActivity.class);
                    startActivity(intent);
                    break;
                }
                case FlightCoreService.EVENT_CONNECTION_FAILURE: {
                    setLoading(false);

                    Bundle details = msg.getData();
                    int reason = details.getInt("reason",
                            FlightCoreService.FAILURE_REASON_OTHER);
                    AlertDialog.Builder builder =
                            new AlertDialog.Builder(SelectDroneActivity.this);
                    switch (reason) {
                        case FlightCoreService.FAILURE_REASON_OTHER:
                            builder.setTitle(R.string.error_other_dialog_title)
                                    .setMessage(R.string.error_other_dialog_msg);
                            break;
                        case FlightCoreService.FAILURE_REASON_HOST_UNREACHABLE:
                            builder.setTitle(R.string.error_host_unreachable_dialog_title)
                                    .setMessage(R.string.error_host_unreachable_dialog_msg);
                            break;
                    }
                    builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            dialog.dismiss();
                        }
                    });
                    builder.create().show();
                    break;
                }
            }
        }
    }

    /*
    This method is passed as the value of the onClick parameter to a button, so it isn't actually
    called anywhere in code
     */
    public void establishConnection(View view) {
        Message msg = Message.obtain();
        msg.what = FlightCoreService.EVENT_ESTABLISH_CONNECTION;

        // Get values from user input fields
        String ip = mDroneIpInput.getText().toString();
        int port = Integer.parseInt(mDronePortInput.getText().toString());

        // Yoss the values into a Bundle
        Bundle bundle = new Bundle();
        bundle.putString("ipAddr", ip);
        bundle.putInt("port", port);
        msg.setData(bundle);

        // Send the message off to the service
        try {
            // TODO: What if mService is null?
            mService.send(msg);
            setLoading(true);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    private void setLoading(boolean state) {
        if (state) {
            crossfade(mProgressBar, mContent);
        } else {
            crossfade(mContent, mProgressBar);
        }
    }

    private void crossfade(final View a, final View b) {
        a.animate().cancel();

        a.setAlpha(0f);
        a.setVisibility(View.VISIBLE);

        a.animate()
                .alpha(1f)
                .setDuration(SHORT_ANIMATION_DURATION)
                .setListener(null);

        b.animate()
                .alpha(0f)
                .setDuration(SHORT_ANIMATION_DURATION)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        b.setVisibility(View.GONE);
                    }
                });
    }
}