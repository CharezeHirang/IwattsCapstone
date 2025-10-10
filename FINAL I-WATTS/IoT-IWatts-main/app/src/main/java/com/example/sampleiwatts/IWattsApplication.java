package com.example.sampleiwatts;

import android.app.Application;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.firebase.FirebaseApp;
import com.google.firebase.database.FirebaseDatabase;

public class IWattsApplication extends Application {
    private static final String TAG = "IWattsApplication";

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "I-WATTS Application starting");

        // Check Google Play Services
        int resultCode = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(this);
        if (resultCode == ConnectionResult.SUCCESS) {
            Log.d(TAG, "✅ Google Play Services is available");
        } else {
            Log.e(TAG, "❌ Google Play Services NOT available");
        }

        // Initialize Firebase
        FirebaseApp.initializeApp(this);
        Log.d(TAG, "Firebase initialized");

        // Enable Firebase offline persistence
        try {
            FirebaseDatabase.getInstance().setPersistenceEnabled(true);
            FirebaseDatabase.getInstance().getReference().keepSynced(true);
        } catch (Exception e) {
            Log.w(TAG, "Firebase persistence already enabled");
        }

        Log.d(TAG, "I-WATTS Application initialized successfully");
    }
}