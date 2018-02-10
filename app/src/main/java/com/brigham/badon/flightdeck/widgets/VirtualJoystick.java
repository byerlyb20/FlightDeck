package com.brigham.badon.flightdeck.widgets;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Build;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.view.View;

import com.brigham.badon.flightdeck.R;

public class VirtualJoystick extends View {

    private double mX;
    private double mY;

    // All paint styles
    private Paint mBackgroundCirclePaint;
    private Paint mThumbCirclePaint;

    // All positioning values
    private float mBackgroundCircleX;
    private float mBackgroundCircleY;
    private float mBackgroundCircleRadius;
    private float mThumbCircleX;
    private float mThumbCircleY;
    private float mThumbCircleRadius;

    public VirtualJoystick(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);

        init();
    }

    private void init() {
        Resources res = getResources();
        // TODO: Setup view to accept colors as attributes
        int gray;
        int white;
        if (Build.VERSION.SDK_INT >= 23) {
            gray = res.getColor(R.color.joystickBackground, null);
            white = res.getColor(R.color.joystickThumbBackground, null);
        } else {
            gray = res.getColor(R.color.joystickBackground);
            white = res.getColor(R.color.joystickThumbBackground);
        }
        mBackgroundCirclePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mBackgroundCirclePaint.setColor(gray);
        mBackgroundCirclePaint.setStyle(Paint.Style.FILL_AND_STROKE);

        mThumbCirclePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mThumbCirclePaint.setColor(white);
        mThumbCirclePaint.setStyle(Paint.Style.FILL);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawCircle(mBackgroundCircleX, mBackgroundCircleY, mBackgroundCircleRadius,
                mBackgroundCirclePaint);
        canvas.drawCircle(mThumbCircleX, mThumbCircleY, mThumbCircleRadius,
                mThumbCirclePaint);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        mBackgroundCircleX = w / 2f;
        mBackgroundCircleY = h / 2f;
        mBackgroundCircleRadius = Math.min(w, h) / 2f;

        mThumbCircleRadius = mBackgroundCircleRadius / 6;
        float range = mBackgroundCircleRadius - mThumbCircleRadius;
        float offsetX = range * (float) mX;
        float offsetY = range * (float) -mY;
        mThumbCircleX = mBackgroundCircleX + offsetX;
        mThumbCircleY = mBackgroundCircleY + offsetY;
    }

    /**
     * Used to scale the axes to compensate for the controller's circular range,
     * takes a percentage of the total extension from the origin of the
     */
    private void normalizeAxis(double x, double y) {
        // Store the angle and the distance of the values
        double angle = Math.atan(y / x);
        double distance = Math.sqrt(Math.pow(x, 2) + Math.pow(y, 2));

        // Scale the distance such that it does not exceed 1
        double extendFactor;
        if (x > y) {
            extendFactor = 1 / x;
        } else {
            extendFactor = 1 / y;
        }
        x = x * extendFactor;
        y = y * extendFactor;
        double maxDistance = Math.sqrt(Math.pow(x, 2) + Math.pow(y, 2));
        double scaledDistance = distance / maxDistance;

        // Find the final coordinates
        mX = scaledDistance * Math.cos(angle);
        mY = scaledDistance * Math.sin(angle);
    }
}