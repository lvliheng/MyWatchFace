package com.watch.henry.myapplication;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v4.content.ContextCompat;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.view.SurfaceHolder;
import android.view.WindowInsets;

import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Digital watch face with seconds. In ambient mode, the seconds aren't displayed. On devices with
 * low-bit ambient mode, the text is drawn without anti-aliasing in ambient mode.
 * <p>
 * Important Note: Because watch face apps do not have a default Activity in
 * their project, you will need to set your Configurations to
 * "Do not launch Activity" for both the Wear and/or Application modules. If you
 * are unsure how to do this, please review the "Run Starter project" section
 * in the Google Watch Face Code Lab:
 * https://codelabs.developers.google.com/codelabs/watchface/index.html#0
 */
public class MyWatchFace extends CanvasWatchFaceService {
    private static final Typeface NORMAL_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);

    /**
     * Update rate in milliseconds for interactive mode. Defaults to one second
     * because the watch face needs to update seconds in interactive mode.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);

    /**
     * Handler message id for updating the time periodically in interactive mode.
     */
    private static final int MSG_UPDATE_TIME = 0;

    private static String CURR_DATE = "curr_date";

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private static class EngineHandler extends Handler {
        private final WeakReference<MyWatchFace.Engine> mWeakReference;

        public EngineHandler(MyWatchFace.Engine reference) {
            mWeakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(Message msg) {
            MyWatchFace.Engine engine = mWeakReference.get();
            if (engine != null) {
                switch (msg.what) {
                    case MSG_UPDATE_TIME:
                        engine.handleUpdateTimeMessage();
                        break;
                }
            }
        }
    }

    private class Engine extends CanvasWatchFaceService.Engine {

        private final Handler mUpdateTimeHandler = new EngineHandler(this);
        private Calendar mCalendar;
        private final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            }
        };
        private boolean mRegisteredTimeZoneReceiver = false;
        private float mTimeXOffset;
        private float mDateXOffset;
        private float mTimeYOffset;
        private float mDateYOffset;

        private float mDayXOffset;
        private float mDayYOffset;

        private Paint mBackgroundPaint;
        private Paint mTimePaint;
        private Paint mDatePaint;
        private Paint mDayPaint;
        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        private boolean mLowBitAmbient;
        private boolean mBurnInProtection;
        private boolean mAmbient;


        private Bitmap mBackgroundBitmap;

        private int mWidth;
        private int mHeight;
        private float mCenterX;
        private float mCenterY;
        private float mScale = 1;


        private String hourText;
        private String monthStr;
        private String dayStr;
        private String weekStr;

        private SharedPreferences sharedPreferences;
        private SharedPreferences.Editor editor;
        private String currDate;
        private SimpleDateFormat simpleDateFormat;

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            setWatchFaceStyle(new WatchFaceStyle.Builder(MyWatchFace.this)
                    .setAcceptsTapEvents(true)
                    .build());




            mCalendar = Calendar.getInstance();

            Resources resources = MyWatchFace.this.getResources();
            mTimeYOffset = resources.getDimension(R.dimen.digital_time_y_offset);
            mDateYOffset = resources.getDimension(R.dimen.digital_date_y_offset);
            mDayYOffset = resources.getDimension(R.dimen.digital_day_y_offset);

            mBackgroundBitmap = BitmapFactory.decodeResource(resources, R.drawable.custom_background);

            // Initializes background.
            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(
                    ContextCompat.getColor(getApplicationContext(), R.color.background));


            // Initializes Watch Face.
            mTimePaint = new Paint();
            mTimePaint.setTypeface(NORMAL_TYPEFACE);
            mTimePaint.setAntiAlias(true);
//            mTimePaint.setColor(
//                    ContextCompat.getColor(getApplicationContext(), R.color.digital_time_text));

            mDatePaint = new Paint();
            mDatePaint.setTypeface(NORMAL_TYPEFACE);
            mDatePaint.setAntiAlias(true);
//            mDatePaint.setColor(
//                    ContextCompat.getColor(getApplicationContext(), R.color.digital_date_text));
            mDatePaint.setColor(
                    ContextCompat.getColor(getApplicationContext(), android.R.color.white));

            mDayPaint = new Paint();
            mDayPaint.setTypeface(NORMAL_TYPEFACE);
            mDayPaint.setAntiAlias(true);
            mDayPaint.setColor(
                    ContextCompat.getColor(getApplicationContext(), android.R.color.white));

            sharedPreferences = getSharedPreferences(getString(R.string.app_name), Context.MODE_PRIVATE);
            editor = sharedPreferences.edit();

            simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            super.onDestroy();
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                registerReceiver();

                // Update time zone in case it changed while we weren't visible.
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            } else {
                unregisterReceiver();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            super.onSurfaceChanged(holder, format, width, height);
            mWidth = width;
            mHeight = height;
            /*
             * Find the coordinates of the center point on the screen.
             * Ignore the window insets so that, on round watches
             * with a "chin", the watch face is centered on the entire screen,
             * not just the usable portion.
             */
            mCenterX = mWidth / 2f;
            mCenterY = mHeight / 2f;
            mScale = ((float) width) / (float) mBackgroundBitmap.getWidth();
            /*
             * Calculate the lengths of the watch hands and store them in member variables.
             */
            mBackgroundBitmap = Bitmap.createScaledBitmap(mBackgroundBitmap,
                    (int) (mBackgroundBitmap.getWidth() * mScale),
                    (int) (mBackgroundBitmap.getHeight() * mScale), true);
        }

        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            MyWatchFace.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            MyWatchFace.this.unregisterReceiver(mTimeZoneReceiver);
        }

        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            super.onApplyWindowInsets(insets);

            // Load resources that have alternate values for round watches.
            Resources resources = MyWatchFace.this.getResources();
            boolean isRound = insets.isRound();
            mTimeXOffset = resources.getDimension(isRound
                    ? R.dimen.digital_time_x_offset_round : R.dimen.digital_time_x_offset);
            mDateXOffset = resources.getDimension(isRound
                    ? R.dimen.digital_date_x_offset_round : R.dimen.digital_date_x_offset);
            mDayXOffset = resources.getDimension(isRound
                    ? R.dimen.digital_day_x_offset_round : R.dimen.digital_day_x_offset);
            float timeTextSize = resources.getDimension(isRound
                    ? R.dimen.digital_time_text_size_round : R.dimen.digital_time_text_size);
            float dateTextSize = resources.getDimension(isRound
                    ? R.dimen.digital_date_text_size_round : R.dimen.digital_date_text_size);

            float dayTextSize = resources.getDimension(isRound
                    ? R.dimen.digital_day_text_size_round : R.dimen.digital_day_text_size);

            mTimePaint.setTextSize(timeTextSize);
            mDatePaint.setTextSize(dateTextSize);
            mDayPaint.setTextSize(dayTextSize);
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

            mAmbient = inAmbientMode;
            if (mLowBitAmbient) {
                mTimePaint.setAntiAlias(!inAmbientMode);
                mDatePaint.setAntiAlias(!inAmbientMode);
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        /**
         * Captures tap event (and tap type) and toggles the background color if the user finishes
         * a tap.
         */
        @Override
        public void onTapCommand(int tapType, int x, int y, long eventTime) {
            switch (tapType) {
                case TAP_TYPE_TOUCH:
                    // The user has started touching the screen.
                    break;
                case TAP_TYPE_TOUCH_CANCEL:
                    // The user has started a different gesture or otherwise cancelled the tap.
                    break;
                case TAP_TYPE_TAP:
                    // The user has completed the tap gesture.
                    // TODO: Add code to handle the tap gesture.
//                    Toast.makeText(getApplicationContext(), R.string.message, Toast.LENGTH_SHORT)
//                            .show();
                    break;
            }
            invalidate();
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {

            mCalendar.setTimeInMillis(System.currentTimeMillis());

            if (isInAmbientMode()) {
                canvas.drawColor(Color.BLACK);
                mTimePaint.setColor(
                        ContextCompat.getColor(getApplicationContext(), android.R.color.white));
            } else {
//                canvas.drawBitmap(mBackgroundBitmap, 0, 0, mTimePaint);
//                mTimePaint.setColor(
//                        ContextCompat.getColor(getApplicationContext(), R.color.digital_time_text));

                canvas.drawColor(Color.BLACK);
                mTimePaint.setColor(
                        ContextCompat.getColor(getApplicationContext(), android.R.color.white));

                monthStr = mCalendar.getDisplayName(Calendar.MONTH, Calendar.SHORT, Locale.getDefault());
                dayStr = String.format(getResources().getString(R.string.day_format), mCalendar.get(Calendar.DAY_OF_MONTH));
                weekStr = mCalendar.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.SHORT, Locale.getDefault());
                canvas.drawText(weekStr + ", " + monthStr + " " + dayStr, mDateXOffset, mDateYOffset, mDatePaint);

                canvas.drawText(Utils.getDays(), mDayXOffset, mDayYOffset, mDayPaint);
            }

            hourText = String.format(getResources().getString(R.string.time_format), mCalendar.get(Calendar.HOUR_OF_DAY),
                    mCalendar.get(Calendar.MINUTE));

//            if (!isShow()) {
//                showNotification(MyWatchFace.this);
//            }

            canvas.drawText(hourText, mTimeXOffset, mTimeYOffset, mTimePaint);
        }

        private boolean isShow() {
            currDate = sharedPreferences.getString(CURR_DATE, "");

            if (simpleDateFormat.format(new Date()).equals(currDate)) {
                return true;
            } else {
                editor.putString(CURR_DATE, simpleDateFormat.format(new Date()));
                editor.apply();
                return false;
            }
        }

        private void showNotification(Context context) {
            NotificationCompat.Builder builder = new NotificationCompat.Builder(context)
                    .setSmallIcon(R.mipmap.vivid_icon)
                    .setLargeIcon(BitmapFactory.decodeResource(context.getResources(), R.mipmap.header))
                    .setContentTitle(getNotificationContent(context));

            NotificationManagerCompat.from(context).notify(0, builder.build());
        }

        private SpannableString getNotificationContent(Context context) {
            String day = Utils.getDays();
            String days = day + " days";

            SpannableString spannableString = new SpannableString(days);

            ForegroundColorSpan foregroundColorSpanOrange = new ForegroundColorSpan(context.getResources().getColor(R.color.orange));
            spannableString.setSpan(foregroundColorSpanOrange, 0, day.length(), Spanned.SPAN_INCLUSIVE_INCLUSIVE);

            RelativeSizeSpan relativeSizeSpan = new RelativeSizeSpan(1.2f);
            spannableString.setSpan(relativeSizeSpan, 0, day.length(), Spanned.SPAN_INCLUSIVE_INCLUSIVE);

            ForegroundColorSpan foregroundColorSpanGray = new ForegroundColorSpan(context.getResources().getColor(android.R.color.darker_gray));
            spannableString.setSpan(foregroundColorSpanGray, day.length(), days.length(), Spanned.SPAN_EXCLUSIVE_INCLUSIVE);

            StyleSpan styleSpan = new StyleSpan(Typeface.ITALIC);
            spannableString.setSpan(styleSpan, 0, days.length(), Spanned.SPAN_INCLUSIVE_INCLUSIVE);

            return spannableString;
        }

        /**
         * Starts the {@link #mUpdateTimeHandler} timer if it should be running and isn't currently
         * or stops it if it shouldn't be running but currently is.
         */
        private void updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        /**
         * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer should
         * only run when we're visible and in interactive mode.
         */
        private boolean shouldTimerBeRunning() {
            return isVisible() && !isInAmbientMode();
        }

        /**
         * Handle updating the time periodically in interactive mode.
         */
        private void handleUpdateTimeMessage() {
            invalidate();
            if (shouldTimerBeRunning()) {
                long timeMs = System.currentTimeMillis();
                long delayMs = INTERACTIVE_UPDATE_RATE_MS
                        - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
            }
        }
    }
}
