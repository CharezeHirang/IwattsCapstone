package com.example.sampleiwatts;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;

import com.example.sampleiwatts.CostEstimationActivity;
import com.example.sampleiwatts.DashboardActivity;
import com.example.sampleiwatts.HistoricalDataActivity;
import com.example.sampleiwatts.R;
import com.example.sampleiwatts.RealTimeMonitoringActivity;
import com.example.sampleiwatts.SettingsActivity;

public class ButtonNavigator {

    // Navigation positions (left to right order)
    private static final int POSITION_HISTORICAL = 0;
    private static final int POSITION_REALTIME = 1;
    private static final int POSITION_DASHBOARD = 2;
    private static final int POSITION_COST = 3;
    private static final int POSITION_SETTINGS = 4;

    // Method to get navigation position for an activity
    private static int getNavigationPosition(Class<?> activityClass) {
        if (activityClass == HistoricalDataActivity.class) {
            return POSITION_HISTORICAL;
        } else if (activityClass == DashboardActivity.class) {
            return POSITION_DASHBOARD;
        } else if (activityClass == RealTimeMonitoringActivity.class) {
            return POSITION_REALTIME;
        } else if (activityClass == CostEstimationActivity.class) {
            return POSITION_COST;
        } else if (activityClass == SettingsActivity.class) {
            return POSITION_SETTINGS;
        }
        return POSITION_DASHBOARD; // Default to dashboard
    }

    // Method to initialize buttons and handle navigation and emphasis
    public static void setupButtons(final Context context, LinearLayout buttonLayout) {
        // Find all ImageViews by ID
        final ImageView historicalAnalysis = buttonLayout.findViewById(R.id.img_historical_analysis);
        final ImageView realTimeMonitoring = buttonLayout.findViewById(R.id.img_realtime_monitoring);
        final ImageView homepage = buttonLayout.findViewById(R.id.img_homepage);
        final ImageView costEstimation = buttonLayout.findViewById(R.id.img_cost_estimation);
        final ImageView settings = buttonLayout.findViewById(R.id.img_settings);

        // Initialize all buttons to inactive state
        setButtonInactiveState(historicalAnalysis);
        setButtonInactiveState(realTimeMonitoring);
        setButtonInactiveState(homepage);
        setButtonInactiveState(costEstimation);
        setButtonInactiveState(settings);

        // Set the current activity as active based on context
        setCurrentActivityActive(context, historicalAnalysis, realTimeMonitoring, homepage, costEstimation, settings);

        // Add ripple effects to all buttons
        addRippleEffect(historicalAnalysis);
        addRippleEffect(realTimeMonitoring);
        addRippleEffect(homepage);
        addRippleEffect(costEstimation);
        addRippleEffect(settings);

        // Set click listeners for each button with enhanced animations
        historicalAnalysis.setOnClickListener(v -> {
            // Add press animation before navigation
            animateButtonPress(historicalAnalysis, () -> 
                navigateToActivity(context, HistoricalDataActivity.class, historicalAnalysis, realTimeMonitoring, homepage, costEstimation, settings));
        });
        
        realTimeMonitoring.setOnClickListener(v -> {
            animateButtonPress(realTimeMonitoring, () -> 
                navigateToActivity(context, RealTimeMonitoringActivity.class, realTimeMonitoring, historicalAnalysis, homepage, costEstimation, settings));
        });
        
        homepage.setOnClickListener(v -> {
            animateButtonPress(homepage, () -> 
                navigateToActivity(context, DashboardActivity.class, homepage, historicalAnalysis, realTimeMonitoring, costEstimation, settings));
        });
        
        costEstimation.setOnClickListener(v -> {
            animateButtonPress(costEstimation, () -> 
                navigateToActivity(context, CostEstimationActivity.class, costEstimation, historicalAnalysis, realTimeMonitoring, homepage, settings));
        });
        
        settings.setOnClickListener(v -> {
            animateButtonPress(settings, () -> 
                navigateToActivity(context, SettingsActivity.class, settings, historicalAnalysis, realTimeMonitoring, homepage, costEstimation));
        });
    }
    
    // Method to set the current activity as active
    private static void setCurrentActivityActive(Context context, ImageView historicalAnalysis, ImageView realTimeMonitoring, ImageView homepage, ImageView costEstimation, ImageView settings) {
        String className = context.getClass().getSimpleName();
        
        switch (className) {
            case "DashboardActivity":
                setButtonActiveState(homepage);
                break;
            case "HistoricalDataActivity":
                setButtonActiveState(historicalAnalysis);
                break;
            case "RealTimeMonitoringActivity":
                setButtonActiveState(realTimeMonitoring);
                break;
            case "CostEstimationActivity":
                setButtonActiveState(costEstimation);
                break;
            case "SettingsActivity":
                setButtonActiveState(settings);
                break;
            default:
                // Default to homepage if unknown activity
                setButtonActiveState(homepage);
                break;
        }
    }

    // Method to navigate and emphasize the selected button with directional transitions
    private static void navigateToActivity(Context context, Class<?> activityClass, ImageView selectedButton, ImageView... otherButtons) {
        // Get current and target positions
        int currentPosition = getNavigationPosition(context.getClass());
        int targetPosition = getNavigationPosition(activityClass);
        
        // Determine transition direction
        int enterAnim, exitAnim;
        if (targetPosition > currentPosition) {
            // Moving right: slide in from right, slide out to left
            enterAnim = R.anim.slide_in_right;
            exitAnim = R.anim.slide_out_left;
        } else if (targetPosition < currentPosition) {
            // Moving left: slide in from left, slide out to right
            enterAnim = R.anim.slide_in_left;
            exitAnim = R.anim.slide_out_right;
        } else {
            // Same position: fade transition
            enterAnim = R.anim.fade_in;
            exitAnim = R.anim.fade_out;
        }
        
        // Start the activity with custom transitions
        Intent intent = new Intent(context, activityClass);
        context.startActivity(intent);
        
        // Apply transitions if context is an Activity
        if (context instanceof Activity) {
            ((Activity) context).overridePendingTransition(enterAnim, exitAnim);
        }

        // Emphasize the selected button and reset others
        emphasizeButton(selectedButton, otherButtons);
    }

    // Method to emphasize the selected button with smooth animations and active state styling
    private static void emphasizeButton(ImageView selectedButton, ImageView... otherButtons) {
        // Create animation set for smooth transitions
        AnimatorSet animatorSet = new AnimatorSet();
        
        // Animate other buttons to dimmed state and set inactive styling
        for (ImageView button : otherButtons) {
            // Set inactive background and icon
            setButtonInactiveState(button);
            
            ObjectAnimator alphaAnim = ObjectAnimator.ofFloat(button, "alpha", button.getAlpha(), 0.6f);
            ObjectAnimator scaleXAnim = ObjectAnimator.ofFloat(button, "scaleX", button.getScaleX(), 1.0f);
            ObjectAnimator scaleYAnim = ObjectAnimator.ofFloat(button, "scaleY", button.getScaleY(), 1.0f);
            
            alphaAnim.setDuration(200);
            scaleXAnim.setDuration(200);
            scaleYAnim.setDuration(200);
            
            alphaAnim.setInterpolator(new DecelerateInterpolator());
            scaleXAnim.setInterpolator(new AccelerateDecelerateInterpolator());
            scaleYAnim.setInterpolator(new AccelerateDecelerateInterpolator());
            
            animatorSet.playTogether(alphaAnim, scaleXAnim, scaleYAnim);
        }
        
        // Set active state styling for selected button
        setButtonActiveState(selectedButton);
        
        // Animate selected button to highlighted state
        ObjectAnimator selectedAlphaAnim = ObjectAnimator.ofFloat(selectedButton, "alpha", selectedButton.getAlpha(), 1.0f);
        ObjectAnimator selectedScaleXAnim = ObjectAnimator.ofFloat(selectedButton, "scaleX", selectedButton.getScaleX(), 1.15f);
        ObjectAnimator selectedScaleYAnim = ObjectAnimator.ofFloat(selectedButton, "scaleY", selectedButton.getScaleY(), 1.15f);
        
        selectedAlphaAnim.setDuration(250);
        selectedScaleXAnim.setDuration(250);
        selectedScaleYAnim.setDuration(250);
        
        selectedAlphaAnim.setInterpolator(new AccelerateDecelerateInterpolator());
        selectedScaleXAnim.setInterpolator(new AccelerateDecelerateInterpolator());
        selectedScaleYAnim.setInterpolator(new AccelerateDecelerateInterpolator());
        
        // Add a subtle bounce effect
        selectedScaleXAnim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                // Add a subtle bounce back effect
                ObjectAnimator bounceX = ObjectAnimator.ofFloat(selectedButton, "scaleX", 1.15f, 1.1f);
                ObjectAnimator bounceY = ObjectAnimator.ofFloat(selectedButton, "scaleY", 1.15f, 1.1f);
                bounceX.setDuration(100);
                bounceY.setDuration(100);
                bounceX.setInterpolator(new DecelerateInterpolator());
                bounceY.setInterpolator(new DecelerateInterpolator());
                
                AnimatorSet bounceSet = new AnimatorSet();
                bounceSet.playTogether(bounceX, bounceY);
                bounceSet.start();
            }
        });
        
        // Start all animations
        animatorSet.start();
        selectedAlphaAnim.start();
        selectedScaleXAnim.start();
        selectedScaleYAnim.start();
    }
    
    // Method to set button to active state with pill background (no elevation)
    private static void setButtonActiveState(ImageView button) {
        button.setBackgroundResource(R.drawable.nav_button_active);
        button.setSelected(true);
        button.setElevation(0f); // No elevation for subtle look
        
        // Set active icon based on button ID - use ic_img_* set with red tint
        int buttonId = button.getId();
        if (buttonId == R.id.img_homepage) {
            button.setImageResource(R.drawable.ic_img_homepage);
            button.setColorFilter(0xFF7a0000, android.graphics.PorterDuff.Mode.SRC_ATOP);
        } else if (buttonId == R.id.img_historical_analysis) {
            button.setImageResource(R.drawable.ic_img_realtime_monitoring);
            button.setColorFilter(0xFF7a0000, android.graphics.PorterDuff.Mode.SRC_ATOP);
        } else if (buttonId == R.id.img_realtime_monitoring) {
            button.setImageResource(R.drawable.ic_img_historical_analysis);
            button.setColorFilter(0xFF7a0000, android.graphics.PorterDuff.Mode.SRC_ATOP);
        } else if (buttonId == R.id.img_cost_estimation) {
            button.setImageResource(R.drawable.ic_img_cost_estimation);
            button.setColorFilter(0xFF7a0000, android.graphics.PorterDuff.Mode.SRC_ATOP);
        } else if (buttonId == R.id.img_settings) {
            button.setImageResource(R.drawable.ic_img_settings);
            button.setColorFilter(0xFF7a0000, android.graphics.PorterDuff.Mode.SRC_ATOP);
        }
    }
    
    // Method to set button to inactive state
    private static void setButtonInactiveState(ImageView button) {
        button.setBackgroundResource(R.drawable.nav_button_inactive);
        button.setSelected(false);
        button.setElevation(0f);
        
        // Set inactive icon based on button ID - use ic_img_* and clear tint
        int buttonId = button.getId();
        if (buttonId == R.id.img_homepage) {
            button.setImageResource(R.drawable.ic_img_homepage);
            button.clearColorFilter();
        } else if (buttonId == R.id.img_historical_analysis) {
            button.setImageResource(R.drawable.ic_img_realtime_monitoring);
            button.clearColorFilter();
        } else if (buttonId == R.id.img_realtime_monitoring) {
            button.setImageResource(R.drawable.ic_img_historical_analysis);
            button.clearColorFilter();
        } else if (buttonId == R.id.img_cost_estimation) {
            button.setImageResource(R.drawable.ic_img_cost_estimation);
            button.clearColorFilter();
        } else if (buttonId == R.id.img_settings) {
            button.setImageResource(R.drawable.ic_img_settings);
            button.clearColorFilter();
        }
    }
    
    // Method to add ripple effect on button press
    public static void addRippleEffect(ImageView button) {
        button.setOnClickListener(v -> {
            // Create ripple animation
            ObjectAnimator rippleX = ObjectAnimator.ofFloat(button, "scaleX", 1.0f, 0.95f, 1.0f);
            ObjectAnimator rippleY = ObjectAnimator.ofFloat(button, "scaleY", 1.0f, 0.95f, 1.0f);
            ObjectAnimator rippleAlpha = ObjectAnimator.ofFloat(button, "alpha", 1.0f, 0.8f, 1.0f);
            
            rippleX.setDuration(150);
            rippleY.setDuration(150);
            rippleAlpha.setDuration(150);
            
            rippleX.setInterpolator(new AccelerateDecelerateInterpolator());
            rippleY.setInterpolator(new AccelerateDecelerateInterpolator());
            rippleAlpha.setInterpolator(new AccelerateDecelerateInterpolator());
            
            // Add red color tint effect
            button.setColorFilter(0xFF7a0000, android.graphics.PorterDuff.Mode.SRC_ATOP);
            button.postDelayed(() -> button.clearColorFilter(), 150);
            
            AnimatorSet rippleSet = new AnimatorSet();
            rippleSet.playTogether(rippleX, rippleY, rippleAlpha);
            rippleSet.start();
        });
    }
    
    // Method to animate button press with callback
    private static void animateButtonPress(ImageView button, Runnable onComplete) {
        // Create press animation
        ObjectAnimator pressX = ObjectAnimator.ofFloat(button, "scaleX", 1.0f, 0.9f, 1.0f);
        ObjectAnimator pressY = ObjectAnimator.ofFloat(button, "scaleY", 1.0f, 0.9f, 1.0f);
        ObjectAnimator pressAlpha = ObjectAnimator.ofFloat(button, "alpha", 1.0f, 0.7f, 1.0f);
        
        pressX.setDuration(100);
        pressY.setDuration(100);
        pressAlpha.setDuration(100);
        
        pressX.setInterpolator(new AccelerateDecelerateInterpolator());
        pressY.setInterpolator(new AccelerateDecelerateInterpolator());
        pressAlpha.setInterpolator(new AccelerateDecelerateInterpolator());
        
        AnimatorSet pressSet = new AnimatorSet();
        pressSet.playTogether(pressX, pressY, pressAlpha);
        
        // Add completion callback
        pressSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (onComplete != null) {
                    onComplete.run();
                }
            }
        });
        
        pressSet.start();
    }
}
