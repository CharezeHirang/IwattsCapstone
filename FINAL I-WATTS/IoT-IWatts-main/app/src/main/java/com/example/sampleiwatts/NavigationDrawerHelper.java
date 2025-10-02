package com.example.sampleiwatts;

import android.app.Activity;
import android.content.Intent;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

/**
 * Helper class to manage navigation drawer functionality across activities
 */
public class NavigationDrawerHelper {
    
    private Activity activity;
    private DrawerLayout drawerLayout;
    private ImageView hamburgerIcon;
    private ImageView notificationIcon;
    private ImageView closeDrawer;
    private String returnToActivity;

    public NavigationDrawerHelper(Activity activity) {
        this.activity = activity;
    }

    /**
     * Setup navigation drawer for the activity
     */
    public void setupNavigationDrawer() {
        // Initialize drawer components
        drawerLayout = activity.findViewById(R.id.drawer_layout);
        hamburgerIcon = activity.findViewById(R.id.hamburger_icon);
        notificationIcon = activity.findViewById(R.id.notification_icon);
        closeDrawer = activity.findViewById(R.id.close_drawer);
        
        Log.d("NavigationDrawerHelper", "Initializing drawer components:");
        Log.d("NavigationDrawerHelper", "drawerLayout: " + (drawerLayout != null ? "FOUND" : "NULL"));
        Log.d("NavigationDrawerHelper", "hamburgerIcon: " + (hamburgerIcon != null ? "FOUND" : "NULL"));
        Log.d("NavigationDrawerHelper", "closeDrawer: " + (closeDrawer != null ? "FOUND" : "NULL"));
        Log.d("NavigationDrawerHelper", "notificationIcon: " + (notificationIcon != null ? "FOUND" : "NULL"));
        
        if (drawerLayout == null || hamburgerIcon == null || closeDrawer == null) {
            Log.w("NavigationDrawerHelper", "Drawer components not found in layout");
            return;
        }
        
        // Setup hamburger icon click listener
        hamburgerIcon.setOnClickListener(v -> {
            if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                drawerLayout.closeDrawer(GravityCompat.START);
            } else {
                drawerLayout.openDrawer(GravityCompat.START);
            }
        });
        
        // Setup close drawer button (single-tap close with subtle feedback)
        closeDrawer.setOnClickListener(v -> {
            Log.d("NavigationDrawerHelper", "Close drawer (X button) clicked");
            // Prevent double taps during animation
            closeDrawer.setClickable(false);
            // Animate: quick scale + fade, then close
            closeDrawer.animate()
                    .scaleX(0.85f)
                    .scaleY(0.85f)
                    .alpha(0.7f)
                    .setDuration(150)
                    .withEndAction(() -> {
                        drawerLayout.closeDrawers();
                        // Restore X to normal after closing
                        closeDrawer.animate()
                                .scaleX(1f)
                                .scaleY(1f)
                                .alpha(1f)
                                .setDuration(120)
                                .withEndAction(() -> closeDrawer.setClickable(true))
                                .start();
                    })
                    .start();
        });

        // Setup notification icon click listener
        if (notificationIcon != null) {
            notificationIcon.setOnClickListener(v -> {
                try {
                    Intent intent = new Intent(activity, NotificationActivity.class);
                    activity.startActivity(intent);
                    activity.overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
                } catch (Exception e) {
                    Toast.makeText(activity, "Unable to open notifications", Toast.LENGTH_SHORT).show();
                }
            });
        }

        // Ensure X is on top and receives clicks (avoid focus requirement)
        try {
            closeDrawer.bringToFront();
            closeDrawer.setClickable(true);
            closeDrawer.setFocusable(false);
            closeDrawer.setFocusableInTouchMode(false);
        } catch (Exception ignored) { }
        
        // Setup menu item click listeners
        setupMenuClickListeners();
    }

    /**
     * Setup menu click listeners for drawer items
     */
    private void setupMenuClickListeners() {
        // Dashboard
        View menuDashboard = activity.findViewById(R.id.menu_dashboard);
        if (menuDashboard != null) {
            View.OnClickListener dashboardClick = v -> {
                Log.d("NavigationDrawerHelper", "Dashboard menu clicked");
                drawerLayout.closeDrawer(GravityCompat.START);
                navigateToActivity(DashboardActivity.class);
            };
            menuDashboard.setOnClickListener(dashboardClick);
            attachDeepClick(menuDashboard, dashboardClick);
        }
        
        // Real-time Monitoring
        View menuMonitoring = activity.findViewById(R.id.menu_monitoring);
        if (menuMonitoring != null) {
            View.OnClickListener monitoringClick = v -> {
                Log.d("NavigationDrawerHelper", "Real-time Monitoring menu clicked");
                drawerLayout.closeDrawer(GravityCompat.START);
                navigateToActivity(RealTimeMonitoringActivity.class);
            };
            menuMonitoring.setOnClickListener(monitoringClick);
            attachDeepClick(menuMonitoring, monitoringClick);
        }
        
        // Historical Data
        View menuHistorical = activity.findViewById(R.id.menu_historical);
        if (menuHistorical != null) {
            View.OnClickListener historicalClick = v -> {
                Log.d("NavigationDrawerHelper", "Historical Data menu clicked");
                drawerLayout.closeDrawer(GravityCompat.START);
                navigateToActivity(HistoricalDataActivity.class);
            };
            menuHistorical.setOnClickListener(historicalClick);
            attachDeepClick(menuHistorical, historicalClick);
        }
        
        // Cost Estimation
        View menuCostEstimation = activity.findViewById(R.id.menu_cost_estimation);
        if (menuCostEstimation != null) {
            View.OnClickListener costClick = v -> {
                Log.d("NavigationDrawerHelper", "Cost Estimation menu clicked");
                drawerLayout.closeDrawer(GravityCompat.START);
                navigateToActivity(CostEstimationActivity.class);
            };
            menuCostEstimation.setOnClickListener(costClick);
            attachDeepClick(menuCostEstimation, costClick);
        }
        
        // Settings
        View menuSettings = activity.findViewById(R.id.menu_settings);
        if (menuSettings != null) {
            View.OnClickListener settingsClick = v -> {
                Log.d("NavigationDrawerHelper", "Settings menu clicked");
                drawerLayout.closeDrawer(GravityCompat.START);
                navigateToActivity(SettingsActivity.class);
            };
            menuSettings.setOnClickListener(settingsClick);
            attachDeepClick(menuSettings, settingsClick);
        }
        
        // Notifications
        View menuNotification = activity.findViewById(R.id.menu_notification);
        if (menuNotification != null) {
            View.OnClickListener notificationClick = v -> {
                Log.d("NavigationDrawerHelper", "Notifications menu clicked");
                drawerLayout.closeDrawer(GravityCompat.START);
                navigateToActivity(NotificationActivity.class);
            };
            menuNotification.setOnClickListener(notificationClick);
            attachDeepClick(menuNotification, notificationClick);
        }

        
        // Tips
        View menuTips = activity.findViewById(R.id.menu_tips);
        if (menuTips != null) {
            View.OnClickListener tipsClick = v -> {
                Log.d("NavigationDrawerHelper", "Tips menu clicked");
                drawerLayout.closeDrawer(GravityCompat.START);
                navigateToActivity(TipsActivity.class);
            };
            menuTips.setOnClickListener(tipsClick);
            attachDeepClick(menuTips, tipsClick);
        }
        
        // Alert
        View menuAlert = activity.findViewById(R.id.menu_alert);
        if (menuAlert != null) {
            View.OnClickListener alertClick = v -> {
                Log.d("NavigationDrawerHelper", "Alert menu clicked");
                drawerLayout.closeDrawer(GravityCompat.START);
                navigateToActivity(AlertActivity.class);
            };
            menuAlert.setOnClickListener(alertClick);
            attachDeepClick(menuAlert, alertClick);
        }
        
        // Logout
        View menuLogout = activity.findViewById(R.id.menu_logout);
        if (menuLogout != null) {
            View.OnClickListener logoutClick = v -> {
                Log.d("NavigationDrawerHelper", "Logout menu clicked");
                drawerLayout.closeDrawer(GravityCompat.START);
                showLogoutConfirmation();
            };
            menuLogout.setOnClickListener(logoutClick);
            attachDeepClick(menuLogout, logoutClick);
        }
    }

    private void showLogoutConfirmation() {
        new AlertDialog.Builder(activity)
                .setTitle("Logout")
                .setMessage("Are you sure you want to logout? You'll need to login again next time you open the app.")
                .setPositiveButton("Logout", (dialog, which) -> {
                    Toast.makeText(activity, "Logging out...", Toast.LENGTH_SHORT).show();
                    LoginActivity.logout(activity);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /**
     * Ensure clicks anywhere inside a menu card (including its child layout/TextView) trigger the same action.
     */
    private void attachDeepClick(View root, View.OnClickListener listener) {
        if (root == null) return;
        root.setClickable(true);
        root.setOnClickListener(listener);
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                View child = group.getChildAt(i);
                attachDeepClick(child, listener);
            }
        }
    }

    /**
     * Navigate to activity with smooth transition
     */
    private void navigateToActivity(Class<?> activityClass) {
        // Avoid re-launching the same activity
        if (!activity.getClass().equals(activityClass)) {
            Intent intent = new Intent(activity, activityClass);
            // Add flag to indicate this navigation came from drawer
            intent.putExtra("from_drawer", true);
            // Let Android back stack handle back navigation naturally

            Log.d("NavigationDrawerHelper", "Navigating to: " + activityClass.getSimpleName());
            String sourceActivityName = activity.getClass().getSimpleName();
            Log.d("NavigationDrawerHelper", "Source activity: " + sourceActivityName);
            Log.d("NavigationDrawerHelper", "from_drawer: true");
            
            activity.startActivity(intent);
            // Add smooth transition animation
            activity.overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
        }
    }

    /**
     * Exit app method
     */
    private void exitApp() {
        // Close all activities and exit the app
        activity.finishAffinity();
        System.exit(0);
    }

    /**
     * Return to original activity if needed
     */
    private void returnToOriginalActivity() {
        try {
            Class<?> targetClass = null;
            switch (returnToActivity) {
                case "RealTimeMonitoringActivity":
                    targetClass = RealTimeMonitoringActivity.class;
                    break;
                case "HistoricalDataActivity":
                    targetClass = HistoricalDataActivity.class;
                    break;
                case "CostEstimationActivity":
                    targetClass = CostEstimationActivity.class;
                    break;
                case "DashboardActivity":
                    targetClass = DashboardActivity.class;
                    break;
                case "SettingsActivity":
                    targetClass = SettingsActivity.class;
                    break;
                case "TipsActivity":
                    targetClass = TipsActivity.class;
                    break;
                case "AlertActivity":
                    targetClass = AlertActivity.class;
                    break;
            }
            
            if (targetClass != null) {
                Intent intent = new Intent(activity, targetClass);
                activity.startActivity(intent);
                activity.overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
                activity.finish();
            }
        } catch (Exception e) {
            Log.e("NavigationDrawerHelper", "Error returning to original activity: " + e.getMessage());
        }
    }


    /**
     * Set the activity to return to when drawer is closed
     */
    public void setReturnToActivity(String activityName) {
        this.returnToActivity = activityName;
    }
}
