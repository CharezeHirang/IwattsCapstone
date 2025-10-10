package com.example.sampleiwatts;

import android.Manifest;
import android.animation.ObjectAnimator;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.airbnb.lottie.LottieAnimationView;
import com.google.firebase.messaging.FirebaseMessaging;

public class SplashActivity extends AppCompatActivity {
    private static final int ANIMATION_DURATION_MS = 5000;
    private static final int LOGO_DISPLAY_DURATION_MS = 2000;

    private LottieAnimationView lottieCharging;
    private ImageView ivAppLogo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);
        requestNotificationPermission();

        lottieCharging = findViewById(R.id.lottie_charging);
        ivAppLogo = findViewById(R.id.iv_app_logo);



        new Handler(Looper.getMainLooper()).postDelayed(this::showLogoWithAnimation, ANIMATION_DURATION_MS);



    }

    private void showLogoWithAnimation() {
        lottieCharging.setVisibility(View.INVISIBLE);

        ivAppLogo.setVisibility(View.VISIBLE);

        ObjectAnimator scaleX = ObjectAnimator.ofFloat(ivAppLogo, "scaleX", 0f, 1.2f, 1f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(ivAppLogo, "scaleY", 0f, 1.2f, 1f);
        ObjectAnimator alpha = ObjectAnimator.ofFloat(ivAppLogo, "alpha", 0f, 1f);

        scaleX.setDuration(600);
        scaleY.setDuration(600);
        alpha.setDuration(600);

        scaleX.start();
        scaleY.start();
        alpha.start();

        new Handler(Looper.getMainLooper()).postDelayed(this::navigateToNext, LOGO_DISPLAY_DURATION_MS);
    }

    private void navigateToNext() {
        startActivity(new Intent(this, LoginActivity.class));
        finish();
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { // Android 13+
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {

                ActivityCompat.requestPermissions(
                        this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        101  // any request code
                );
            }
        }
    }
}


