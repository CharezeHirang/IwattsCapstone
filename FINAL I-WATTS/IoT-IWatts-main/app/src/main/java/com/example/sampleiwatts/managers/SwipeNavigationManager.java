package com.example.sampleiwatts.managers;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;

import com.example.sampleiwatts.CostEstimationActivity;
import com.example.sampleiwatts.DashboardActivity;
import com.example.sampleiwatts.HistoricalDataActivity;
import com.example.sampleiwatts.RealTimeMonitoringActivity;
import com.example.sampleiwatts.R;
import com.example.sampleiwatts.SettingsActivity;

public class SwipeNavigationManager {

    private static final String TAG = "SwipeNavigationManager";

    public enum ActivityPosition {
        HISTORICAL_DATA(0),
        REAL_TIME_MONITORING(1),
        DASHBOARD(2),
        COST_ESTIMATION(3),
        SETTINGS(4);

        private final int position;
        ActivityPosition(int position) { this.position = position; }
        public int getPosition() { return position; }

        public static ActivityPosition fromClass(Class<?> activityClass) {
            if (activityClass.equals(HistoricalDataActivity.class)) return HISTORICAL_DATA;
            if (activityClass.equals(RealTimeMonitoringActivity.class)) return REAL_TIME_MONITORING;
            if (activityClass.equals(DashboardActivity.class)) return DASHBOARD;
            if (activityClass.equals(CostEstimationActivity.class)) return COST_ESTIMATION;
            if (activityClass.equals(SettingsActivity.class)) return SETTINGS;
            return DASHBOARD;
        }

        public Class<?> getActivityClass() {
            switch (this) {
                case HISTORICAL_DATA: return HistoricalDataActivity.class;
                case REAL_TIME_MONITORING: return RealTimeMonitoringActivity.class;
                case DASHBOARD: return DashboardActivity.class;
                case COST_ESTIMATION: return CostEstimationActivity.class;
                case SETTINGS: return SettingsActivity.class;
                default: return DashboardActivity.class;
            }
        }
    }

    private final Context context;
    private GestureDetector gestureDetector;
    private final ActivityPosition currentPosition;

    public SwipeNavigationManager(Context context) {
        this.context = context;
        this.currentPosition = ActivityPosition.fromClass(context.getClass());
        setupGestureDetector();
    }

    private void setupGestureDetector() {
        gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            private static final int SWIPE_THRESHOLD = 30;
            private static final int SWIPE_VELOCITY_THRESHOLD = 30;
            private static final float HORIZONTAL_BIAS = 1.5f;

            @Override public boolean onDown(MotionEvent e) { return true; }

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float vx, float vy) {
                if (e1 == null || e2 == null) return false;
                float diffX = e2.getX() - e1.getX();
                float diffY = e2.getY() - e1.getY();
                if (Math.abs(diffX) > Math.abs(diffY) * HORIZONTAL_BIAS &&
                        Math.abs(diffX) > SWIPE_THRESHOLD && Math.abs(vx) > SWIPE_VELOCITY_THRESHOLD) {
                    if (diffX > 0) navigateToPrevious(); else navigateToNext();
                    return true;
                }
                return false;
            }

            @Override
            public boolean onScroll(MotionEvent e1, MotionEvent e2, float dx, float dy) {
                if (e1 == null || e2 == null) return false;
                float diffX = e2.getX() - e1.getX();
                float diffY = e2.getY() - e1.getY();
                if (Math.abs(diffX) > Math.abs(diffY) * HORIZONTAL_BIAS && Math.abs(diffX) > 50f) {
                    if (diffX > 0) navigateToPrevious(); else navigateToNext();
                    return true;
                }
                return false;
            }
        });
    }

    public void attachToView(View view) {
        if (view == null) return;
        view.setOnTouchListener(new View.OnTouchListener() {
            private float startX, startY;
            private boolean isTracking, isHorizontalSwipe;
            private static final float SWIPE_THRESHOLD = 50f;
            private static final float HORIZONTAL_BIAS = 2.0f;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        startX = event.getX(); startY = event.getY();
                        isTracking = true; isHorizontalSwipe = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        if (!isTracking) return false;
                        float dx = Math.abs(event.getX() - startX);
                        float dy = Math.abs(event.getY() - startY);
                        if (dx > SWIPE_THRESHOLD && dx > dy * HORIZONTAL_BIAS) {
                            isHorizontalSwipe = true;
                            if (event.getX() > startX) navigateToPrevious(); else navigateToNext();
                            isTracking = false;
                            return true;
                        }
                        if (dy > dx * HORIZONTAL_BIAS && dy > SWIPE_THRESHOLD) {
                            isTracking = false;
                            return false;
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        boolean consume = isHorizontalSwipe;
                        isTracking = false;
                        return consume;
                }
                return false;
            }
        });
    }

    private void navigateToPrevious() {
        ActivityPosition p = getPreviousPosition();
        if (p != null && !p.equals(currentPosition)) navigateToActivity(p.getActivityClass());
    }

    private void navigateToNext() {
        ActivityPosition n = getNextPosition();
        if (n != null && !n.equals(currentPosition)) navigateToActivity(n.getActivityClass());
    }

    private ActivityPosition getPreviousPosition() {
        int pos = currentPosition.getPosition();
        return pos > 0 ? ActivityPosition.values()[pos - 1] : null;
    }

    private ActivityPosition getNextPosition() {
        int pos = currentPosition.getPosition();
        return pos < ActivityPosition.values().length - 1 ? ActivityPosition.values()[pos + 1] : null;
    }

    private void navigateToActivity(Class<?> activityClass) {
        if (context.getClass().equals(activityClass)) return;
        try {
            Intent intent = new Intent(context, activityClass);
            context.startActivity(intent);
            if (context instanceof Activity) {
                Activity activity = (Activity) context;
                ActivityPosition target = ActivityPosition.fromClass(activityClass);
                boolean movingLeft = target.getPosition() < currentPosition.getPosition();
                int enter = movingLeft ? R.anim.slide_in_left : R.anim.slide_in_right;
                int exit  = movingLeft ? R.anim.slide_out_right : R.anim.slide_out_left;
                activity.overridePendingTransition(enter, exit);
            }
        } catch (Exception e) {
            Log.e(TAG, "Navigation error: " + e.getMessage());
        }
    }

    public static void enableSwipeNavigation(Activity activity, View mainContentView) {
        if (activity == null || mainContentView == null) return;
        new SwipeNavigationManager(activity).attachToView(mainContentView);
    }

    public static void enableSwipeNavigationForScrollView(Activity activity, View scrollView) {
        if (activity == null || scrollView == null) return;
        SwipeNavigationManager mgr = new SwipeNavigationManager(activity);
        mgr.attachToView(scrollView);
        View root = activity.findViewById(android.R.id.content);
        if (root != null && root != scrollView) mgr.attachToView(root);
    }
}


