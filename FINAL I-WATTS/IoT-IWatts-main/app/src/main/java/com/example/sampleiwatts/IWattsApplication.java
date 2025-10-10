package com.example.sampleiwatts;


import android.app.Application;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.google.firebase.FirebaseApp;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.messaging.FirebaseMessaging;

public class IWattsApplication extends Application {
    private static final String TAG = "IWattsApplication";


    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "I-WATTS Application starting");

        if (FirebaseApp.getApps(this).isEmpty()) {
            FirebaseApp.initializeApp(this);
        }

        try {
            FirebaseDatabase.getInstance().setPersistenceEnabled(true);
            FirebaseDatabase.getInstance().getReference().keepSynced(true);
        } catch (Exception e) {
            Log.w(TAG, "Firebase persistence already enabled");
        }

        // --- Add this ---
        FirebaseMessaging.getInstance().getToken()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        String token = task.getResult();
                        Log.d("FCM", "Device token: " + token);
                    } else {
                        Log.e("FCM", "Token fetch failed", task.getException());
                    }
                });

        FirebaseMessaging.getInstance()
                .subscribeToTopic("iwatts_alerts")
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Log.d("FCM", "✅ Subscribed to iwatts_alerts");
                    } else {
                        Log.e("FCM", "❌ Subscription failed", task.getException());
                    }
                });

        Log.d(TAG, "I-WATTS Application initialized successfully");
    }



}