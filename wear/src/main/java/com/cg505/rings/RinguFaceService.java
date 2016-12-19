package com.cg505.ringu;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

import android.os.BatteryManager;
import android.text.format.DateFormat;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.view.SurfaceHolder;

/**
 * Created by cooperc on 12/16/16.
 */

public class RinguFaceService extends CanvasWatchFaceService {

    private static final Typeface NORMAL_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);
    private static final Typeface BOLD_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD);

    // update twice/second in interactive mode (blinking colons)
    private static final long INTERACTIVE_UPDATE_RATE_MS = 500;

    @Override
    public Engine onCreateEngine() {

        /* provide your watch face implementation */
        return new Engine();
    }

    /* implement service callback methods */
    private class Engine extends CanvasWatchFaceService.Engine {

        static final int MSG_UPDATE_TIME = 1;
        static final float RING_WIDTH = 7f;
        static final String COLON_STRING = ":";

        // calendar/time and other logic
        Calendar mCalendar;
        Date mDate;
        SimpleDateFormat mDateFormat;
        boolean mShouldDrawColons;
        boolean mRegisteredTimeZoneReceiver = false;

        // battery shenanigans
        IntentFilter batteryIfilter;
        Intent batteryStatus;

        // device features
        boolean mLowBitAmbient;
        boolean mBurnInProtection;

        // graphic objects
        Bitmap mBackgroundBitmap;
        Bitmap mBackgroundScaledBitmap;
        Paint mAmbientBackgroundPaint;
        Paint mHourPaint;
        Paint mMinutePaint;
        Paint mSecondPaint;
        Paint mDatePaint;
        int mMainColor;
        int mSecondColor;
        Paint mRingPaint;

        private final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            }
        };

        // handler to update time once/second when in interactive mode
        final Handler mUpdateTimeHandler = new Handler() {
            @Override
            public void handleMessage(Message message) {
                switch (message.what) {
                    case MSG_UPDATE_TIME:
                        invalidate();
                        if (shouldTimerBeRunning()) {
                            long timeMs = System.currentTimeMillis();
                            long delayMs = INTERACTIVE_UPDATE_RATE_MS -
                                    (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                            mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
                        }
                        break;
                }
            }
        };

        // service methods
        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            // configure system ui
            setWatchFaceStyle(new WatchFaceStyle.Builder(RinguFaceService.this)
                    .setPeekOpacityMode(WatchFaceStyle.PEEK_OPACITY_MODE_TRANSLUCENT)
                    .setViewProtectionMode(WatchFaceStyle.PROTECT_WHOLE_SCREEN)
                    .build());

            // load bg image
            Resources resources = RinguFaceService.this.getResources();
            Drawable backgroundDrawable = resources.getDrawable(R.drawable.bg, null);
            mBackgroundBitmap = ((BitmapDrawable) backgroundDrawable).getBitmap();

            mAmbientBackgroundPaint = new Paint();
            mAmbientBackgroundPaint.setStyle(Paint.Style.FILL);
            mAmbientBackgroundPaint.setColor(Color.BLACK);

            // create graphic styles
            mMainColor = Color.argb(255, 255, 255, 255);
            mHourPaint = createTextPaint(mMainColor, 80f);
            mHourPaint.setTypeface(BOLD_TYPEFACE);
            mMinutePaint = createTextPaint(mMainColor, 80f);
            mSecondColor = Color.argb(200, 200, 200, 200);
            mSecondPaint = createTextPaint(mSecondColor, 50f);
            mDatePaint = createTextPaint(mMainColor, 30f);
            mDatePaint.setTypeface(BOLD_TYPEFACE);

            mRingPaint = new Paint();
            mRingPaint.setARGB(255, 255, 255, 255);
            mRingPaint.setStyle(Paint.Style.STROKE);
            mRingPaint.setStrokeWidth(RING_WIDTH);
            mRingPaint.setAntiAlias(true);

            // allocate a Calendar for time calculation etc
            mCalendar = Calendar.getInstance();
            mDate = new Date();
            mDateFormat = new SimpleDateFormat(
                    DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEE, MMM d"));
            mDateFormat.setCalendar(mCalendar);

            // battery lolz
            batteryIfilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);

            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
            mBurnInProtection = properties.getBoolean(PROPERTY_BURN_IN_PROTECTION, false);
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();

            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);

            if(mLowBitAmbient) {
                boolean antiAlias = !inAmbientMode;
                mHourPaint.setAntiAlias(antiAlias);
                // and all other strokes
            }
            invalidate();
            updateTimer();
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            // update time
            long now = System.currentTimeMillis();
            mCalendar.setTimeInMillis(now);
            mDate.setTime(now);
            boolean is24Hour = DateFormat.is24HourFormat(RinguFaceService.this);

            // colons only for first half of each second
            mShouldDrawColons = mCalendar.get(Calendar.MILLISECOND) % 1000 < 500;

            int width = bounds.width();
            int height = bounds.height();

            // draw the bg first
            if (!isInAmbientMode()) {
                canvas.drawBitmap(mBackgroundScaledBitmap, 0, 0, null);
            } else {
                canvas.drawRect(bounds, mAmbientBackgroundPaint);
            }

            // make texts
            String hourString;
            if (is24Hour) {
                hourString = formatTwoDigitNumber(mCalendar.get(Calendar.HOUR_OF_DAY));
            } else {
                int hour = mCalendar.get(Calendar.HOUR);
                if (hour == 0) {
                    hour = 12;
                }
                hourString = String.valueOf(hour);
            }
            float hourWidth = mHourPaint.measureText(hourString);

            float minColonWidth = mMinutePaint.measureText(COLON_STRING);

            String minuteString = formatTwoDigitNumber(mCalendar.get(Calendar.MINUTE));
            float minuteWidth = mMinutePaint.measureText(minuteString);

            float timeWidth = hourWidth + minColonWidth + minuteWidth;

            // center HH:MM
            float x = (width - timeWidth) / 2f;
            float y = 180;

            // start rendering
            canvas.drawText(hourString, x, y, mHourPaint);
            x += hourWidth;
            canvas.drawText(COLON_STRING, x, y, mMinutePaint);
            x += minColonWidth;
            canvas.drawText(minuteString, x, y, mMinutePaint);
            x += minuteWidth;
            if (!isInAmbientMode()) {
                if (mShouldDrawColons) {
                    canvas.drawText(COLON_STRING, x, y, mSecondPaint);
                }
                x += mSecondPaint.measureText(COLON_STRING);
                String secondText = formatTwoDigitNumber(mCalendar.get(Calendar.SECOND));
                canvas.drawText(secondText, x, y, mSecondPaint);
            }

            String dateString = mDateFormat.format(mDate);
            x = (width - mDatePaint.measureText(dateString)) / 2f;
            y += mDatePaint.getFontSpacing();
            canvas.drawText(dateString, x, y, mDatePaint);


            // draw the rings
            float radius = (float) Math.floor((Math.min(width, height) - RING_WIDTH) / 2f);
            if(isInAmbientMode()){
                // move the rings in so that they don't get moved off screen by
                // shifting due to burn-in protection
                radius -= 10f;
            }
            float centerX = width / 2f;
            float centerY = height / 2f;
            drawRing(centerX, centerY, radius, batteryPct(), canvas);
            //radius -= RING_WIDTH;
            //drawRing(centerX, centerY, radius, .6f, canvas);
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            if(mBackgroundScaledBitmap == null
                    || mBackgroundScaledBitmap.getWidth() != width
                    || mBackgroundScaledBitmap.getHeight() != height) {
                mBackgroundScaledBitmap = Bitmap.createScaledBitmap(mBackgroundBitmap,
                        width, height, true /*, filter*/);
            }
            super.onSurfaceChanged(holder, format, width, height);
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if(visible) {
                registerReceiver();

                // update time zone in case it changed while we were invisible
                mCalendar.setTimeZone(TimeZone.getDefault());
            } else {
                unregisterReceiver();
            }
        }


        private float batteryPct() {
            // update battery info
            batteryStatus = RinguFaceService.this.registerReceiver(null, batteryIfilter);

            int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);

            return level / (float) scale;
        }

        private void drawRing(float centerX, float centerY, float radius, float percent, Canvas canvas) {
            canvas.drawArc(centerX - radius, centerY - radius, centerX + radius ,centerY + radius,
                    -90f, 360f * percent,
                    false,
                    mRingPaint);
        }

        private Paint createTextPaint(int color, float size) {
            Paint paint = new Paint();
            paint.setColor(color);
            paint.setTypeface(NORMAL_TYPEFACE);
            paint.setAntiAlias(true);
            paint.setTextSize(size);
            return paint;
        }

        private String formatTwoDigitNumber(int num) {
            return String.format("%02d", num);
        }

        private void registerReceiver() {
            if(mRegisteredTimeZoneReceiver) {
                return;
            }

            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter((Intent.ACTION_TIMEZONE_CHANGED));
            RinguFaceService.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if(!mRegisteredTimeZoneReceiver) {
                return;
            }

            mRegisteredTimeZoneReceiver = false;
            RinguFaceService.this.unregisterReceiver(mTimeZoneReceiver);
        }


        private void updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if(shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        private boolean shouldTimerBeRunning() {
            return isVisible() && !isInAmbientMode();
        }

    }

}
