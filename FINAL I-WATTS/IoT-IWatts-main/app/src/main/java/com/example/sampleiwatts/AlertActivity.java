package com.example.sampleiwatts;

import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import android.text.TextWatcher;
import android.text.Editable;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import androidx.core.app.NotificationCompat;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.MutableData;
import com.google.firebase.database.Transaction;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.messaging.FirebaseMessaging;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.HashMap;
import java.util.Map;

public class AlertActivity extends AppCompatActivity {

    private DatabaseReference db;
    private EditText etPowerValue, etBudgetValue;
    private Double electricityRatePerKwh = null;
    private boolean updatingFromPower = false;
    private boolean updatingFromBudget = false;
    private boolean settingsAlreadySaved = false;

    MaterialSwitch switchVoltage, switchSystemUpdates, switchPush;
    MaterialButton btnSave;
    private static final String CHANNEL_ID = "alerts_channel";
    private static final int REQ_POST_NOTIF = 1001;
    private Boolean desiredVoltage, desiredSystem, desiredPush;
    private ValueEventListener thresholdRefListener;
    private ValueEventListener costFilterListener;
    private ValueEventListener voltageListener;
    private String lastVoltageKeyNotified = null;
    private long lastCombinedAlertMs = 0L;
    private double lastNotifiedCost = -1.0;
    private double lastNotifiedKwh = -1.0;
    private int lastCostStepSent = -1; // step index from 90% in 3% increments
    private int lastKwhStepSent = -1;
    private boolean finalThresholdAlertSent = false;
    private boolean reachedCostSent = false; // for 100% but < 103%
    private boolean reachedKwhSent = false;
    private static final long MIN_ALERT_INTERVAL_MS = 300_000L; // kept for safety but step logic governs
    private static final double STEP_PERCENT = 3.0; // notify each +3%
    private static final double START_PERCENT = 85.0; // start notifying at 85% (e.g., 6.0/7.0)

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_alert);

        // Back arrow behavior
        android.widget.ImageView backIcon = findViewById(R.id.back_icon);
        if (backIcon != null) {
            backIcon.setOnClickListener(v -> onBackPressed());
        }

        db = FirebaseDatabase.getInstance().getReference();

        etBudgetValue = findViewById(R.id.etBudgetValue);
        etPowerValue = findViewById(R.id.etPowerValue);
        switchPush = findViewById(R.id.switchPush);
        switchVoltage = findViewById(R.id.switchVoltage);
        switchSystemUpdates = findViewById(R.id.switchSystemUpdates);
        btnSave = findViewById(R.id.btnSave);
        attachTextWatchers();
        fetchElectricityRate();

        createNotificationChannel();
        btnSave.setOnClickListener(v -> saveSettingsAndThreshold());

        // Fetch previously saved threshold values and switch states
        fetchThresholdAndSettings();
        
        // Check for missed notifications when app starts
        checkForMissedNotifications();
        
        // Get FCM token for background notifications
        getFCMToken();
    }

    private void attachTextWatchers() {
        if (etPowerValue != null) {
            etPowerValue.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }
                @Override public void afterTextChanged(Editable s) {
                    if (updatingFromBudget) return;
                    computeBudgetFromPower();
                }
            });
        }
        if (etBudgetValue != null) {
            etBudgetValue.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }
                @Override public void afterTextChanged(Editable s) {
                    if (updatingFromPower) return;
                    computePowerFromBudget();
                }
            });
        }
    }

    private void fetchElectricityRate() {
        DatabaseReference electricityRateRef = db.child("system_settings").child("electricity_rate_per_kwh");
        electricityRateRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                Object value = dataSnapshot.getValue();
                if (value instanceof Number) {
                    electricityRatePerKwh = ((Number) value).doubleValue();
                } else {
                    Toast.makeText(AlertActivity.this, "Electricity rate not available", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                Toast.makeText(AlertActivity.this, "Error fetching electricity rate", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void fetchThresholdAndSettings() {
        // Threshold (etPowerValue -> kwh_value, etBudgetValue -> cost_value)
        db.child("threshold").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                Object kwhObj = snapshot.child("kwh_value").getValue();
                Object costObj = snapshot.child("cost_value").getValue();
                String kwhStr = kwhObj == null ? "" : String.valueOf(kwhObj);
                String costStr = costObj == null ? "" : String.valueOf(costObj);

                if (etPowerValue != null) {
                    updatingFromBudget = true; // prevent reverse calc while populating
                    etPowerValue.setText(kwhStr);
                    updatingFromBudget = false;
                }
                if (etBudgetValue != null) {
                    updatingFromPower = true; // prevent reverse calc while populating
                    etBudgetValue.setText(costStr);
                    updatingFromPower = false;
                }
            }
            @Override public void onCancelled(DatabaseError error) { }
        });

        // Switch states under notification_settings
        db.child("notification_settings").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                Boolean v = snapshot.child("voltage_enabled").getValue(Boolean.class);
                Boolean s = snapshot.child("system_updates_enabled").getValue(Boolean.class);
                Boolean p = snapshot.child("push_enabled").getValue(Boolean.class);

                if (switchVoltage != null && v != null) switchVoltage.setChecked(v);
                if (switchSystemUpdates != null && s != null) switchSystemUpdates.setChecked(s);
                if (switchPush != null && p != null) switchPush.setChecked(p);
            }
            @Override public void onCancelled(DatabaseError error) { }
        });
    }

    private void saveSettingsAndThreshold() {
        // Reset the flag for new save operation
        settingsAlreadySaved = false;
        
        // Only Push requires OS notification permission for background delivery
        boolean wantsPush = switchPush != null && switchPush.isChecked();

        if (wantsPush && !hasNotificationPermission()) {
            desiredVoltage = switchVoltage != null && switchVoltage.isChecked();
            desiredSystem = switchSystemUpdates != null && switchSystemUpdates.isChecked();
            desiredPush = true;
            requestNotificationPermission();
            return; // wait for user response
        }

        continueSavingSettings();
    }

    private void continueSavingSettings() {
        // Prevent duplicate execution
        if (settingsAlreadySaved) {
            return;
        }
        settingsAlreadySaved = true;
        
        String kwhText = etPowerValue != null && etPowerValue.getText()!=null ? etPowerValue.getText().toString().trim() : "";
        String costText = etBudgetValue != null && etBudgetValue.getText()!=null ? etBudgetValue.getText().toString().trim() : "";

        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());

        DatabaseReference thresholdRef = db.child("threshold");
        thresholdRef.child("kwh_value").setValue(kwhText);
        thresholdRef.child("cost_value").setValue(costText);
        thresholdRef.child("time").setValue(timestamp);
        // Reset server-side final-notified gate so new thresholds can trigger alerts
        thresholdRef.child("final_notified").setValue(false);
        thresholdRef.child("reached_notified").setValue(false);

        // Reset local alert state so we can notify again for new thresholds
        finalThresholdAlertSent = false;
        reachedCostSent = false;
        reachedKwhSent = false;
        lastCostStepSent = -1;
        lastKwhStepSent = -1;
        lastNotifiedCost = -1.0;
        lastNotifiedKwh = -1.0;

        DatabaseReference settingsRef = db.child("notification_settings");
        settingsRef.child("voltage_enabled").setValue(switchVoltage != null && switchVoltage.isChecked());
        settingsRef.child("system_updates_enabled").setValue(switchSystemUpdates != null && switchSystemUpdates.isChecked());
        settingsRef.child("push_enabled").setValue(switchPush != null && switchPush.isChecked())
                .addOnSuccessListener(aVoid -> Toast.makeText(AlertActivity.this, "Settings saved", Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e -> Toast.makeText(AlertActivity.this, "Failed to save", Toast.LENGTH_SHORT).show());

        // Send toggle alerts for voltage and system updates
        if (switchVoltage != null && switchVoltage.isChecked()) {
            notifyNow("Voltage Fluctuation", "You will receive voltage fluctuation messages.");
            // When voltage switch is turned ON, reset the baseline to NOW to prevent old alerts
            resetVoltageBaseline();
        } else {
            notifyNow("Voltage Fluctuation", "You won't receive voltage fluctuation messages.");
        }
        
        if (switchSystemUpdates != null && switchSystemUpdates.isChecked()) {
            notifyNow("System Updates", "You will receive updates and changes notifications.");
        } else {
            notifyNow("System Updates", "You won't receive system updates notifications.");
        }
        
        // Save Push switch state to Firebase so the service can access it
        boolean pushEnabled = switchPush != null && switchPush.isChecked();
        db.child("notification_settings").child("push_enabled").setValue(pushEnabled);
        
        // Always start background monitoring service for continuous threshold and voltage checking
        // The service will always run to detect changes automatically in the background
        startBackgroundMonitoringService();
        
        // Send notification about Push switch state
        if (pushEnabled) {
            notifyNow("Push Notifications", "Background monitoring enabled. You will receive notifications even when the app is minimized.");
        } else {
            notifyNow("Push Notifications", "In-app monitoring enabled. You will receive notifications while the app is running.");
        }
        
        // Reset backend notification flags when new thresholds are set
        resetBackendNotificationFlags();
        
        // Always trigger immediate threshold check
        new android.os.Handler().postDelayed(() -> {
            checkServiceStatus();
            triggerManualThresholdCheck();
        }, 1000);
    }

    private boolean hasNotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT < 33) return true;
        return ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestNotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, REQ_POST_NOTIF);
        }
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_POST_NOTIF) {
            boolean granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            if (granted) {
                // restore desired states if any were stored
                if (desiredVoltage != null && switchVoltage != null) switchVoltage.setChecked(desiredVoltage);
                if (desiredSystem != null && switchSystemUpdates != null) switchSystemUpdates.setChecked(desiredSystem);
                if (desiredPush != null && switchPush != null) switchPush.setChecked(desiredPush);
                continueSavingSettings();
            } else {
                // turn off only Push and save state
                if (switchPush != null) switchPush.setChecked(false);
                Toast.makeText(this, "Notifications permission denied. Background push disabled.", Toast.LENGTH_LONG).show();
                continueSavingSettings();
            }
            desiredVoltage = desiredSystem = desiredPush = null;
        }
    }

    private void getFCMToken() {
        FirebaseMessaging.getInstance().getToken()
            .addOnCompleteListener(task -> {
                if (!task.isSuccessful()) {
                    android.util.Log.w("FCM", "Fetching FCM registration token failed", task.getException());
                    return;
                }

                // Get new FCM registration token
                String token = task.getResult();
                android.util.Log.d("FCM", "FCM Registration Token: " + token);

                // Save token to Firebase Database
                db.child("fcm_tokens").child(token).setValue(true);
                
                // Save token locally for reference
                android.content.SharedPreferences prefs = getSharedPreferences("FCM_PREFS", Context.MODE_PRIVATE);
                prefs.edit().putString("fcm_token", token).apply();
                
                android.util.Log.d("FCM", "✅ FCM token saved to database");
            });
    }

    private void startBackgroundMonitoringService() {
        android.util.Log.d("BackgroundService", "🚀 Starting background monitoring service");
        Intent serviceIntent = new Intent(this, NotificationMonitorService.class);
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent);
                android.util.Log.d("BackgroundService", "✅ Foreground service started");
            } else {
                startService(serviceIntent);
                android.util.Log.d("BackgroundService", "✅ Background service started");
            }
        } catch (Exception e) {
            android.util.Log.e("BackgroundService", "❌ Failed to start service: " + e.getMessage());
        }
    }

    private void stopBackgroundMonitoringService() {
        android.util.Log.d("BackgroundService", "🛑 Stopping background monitoring service");
        Intent serviceIntent = new Intent(this, NotificationMonitorService.class);
        stopService(serviceIntent);
    }

    private void checkServiceStatus() {
        android.app.ActivityManager manager = (android.app.ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        boolean isServiceRunning = false;
        
        if (manager != null) {
            for (android.app.ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
                if (NotificationMonitorService.class.getName().equals(service.service.getClassName())) {
                    isServiceRunning = true;
                    break;
                }
            }
        }
        
        android.util.Log.d("BackgroundService", "🔍 Service status: " + (isServiceRunning ? "RUNNING" : "NOT RUNNING"));
        
        if (!isServiceRunning) {
            android.util.Log.w("BackgroundService", "⚠️ Service not running, attempting to restart");
            startBackgroundMonitoringService();
        }
    }

    private void triggerManualThresholdCheck() {
        android.util.Log.d("BackgroundService", "🔍 Triggering manual threshold check");
        Intent serviceIntent = new Intent(this, NotificationMonitorService.class);
        serviceIntent.setAction("MANUAL_CHECK");
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
    }

    private void triggerImmediateInAppCheck() {
        android.util.Log.d("InAppCheck", "🔍 Triggering immediate in-app threshold check");
        
        // Get current threshold values
        String kwhText = etPowerValue != null && etPowerValue.getText() != null ? etPowerValue.getText().toString().trim() : "";
        String costText = etBudgetValue != null && etBudgetValue.getText() != null ? etBudgetValue.getText().toString().trim() : "";
        
        Double kwhLimit = null, costLimit = null;
        if (!kwhText.isEmpty()) {
            try { kwhLimit = Double.parseDouble(kwhText); } catch (Exception e) {}
        }
        if (!costText.isEmpty()) {
            try { costLimit = Double.parseDouble(costText); } catch (Exception e) {}
        }
        
        if (kwhLimit == null && costLimit == null) {
            android.util.Log.d("InAppCheck", "⚠️ No threshold limits set");
            return;
        }
        
        android.util.Log.d("InAppCheck", "🔍 Limits: Cost=" + costLimit + ", kWh=" + kwhLimit);
        
        // Get date range and check current totals
        Double finalCostLimit = costLimit;
        Double finalKwhLimit = kwhLimit;
        db.child("cost_filter_date").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot filterSnapshot) {
                String startDate = filterSnapshot.child("starting_date").getValue(String.class);
                String endDate = filterSnapshot.child("ending_date").getValue(String.class);
                
                if (startDate == null || endDate == null) {
                    android.util.Log.d("InAppCheck", "⚠️ No date range set");
                    return;
                }
                
                android.util.Log.d("InAppCheck", "🔍 Date range: " + startDate + " to " + endDate);
                
                // Get hourly summaries and calculate totals
                db.child("hourly_summaries").addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot hourlySnapshot) {
                        double totalCost = 0.0;
                        double totalKwh = 0.0;
                        
                        for (DataSnapshot dateSnap : hourlySnapshot.getChildren()) {
                            String dateKey = dateSnap.getKey();
                            if (dateKey != null && dateKey.compareTo(startDate) >= 0 && dateKey.compareTo(endDate) <= 0) {
                                for (DataSnapshot hourSnap : dateSnap.getChildren()) {
                                    Double cost = hourSnap.child("total_cost").getValue(Double.class);
                                    Double kwh = hourSnap.child("total_kwh").getValue(Double.class);
                                    if (cost != null) totalCost += cost;
                                    if (kwh != null) totalKwh += kwh;
                                }
                            }
                        }
                        
                        android.util.Log.d("InAppCheck", String.format("📊 Current Totals: Cost=%.2f (limit=%.2f), kWh=%.3f (limit=%.3f)", 
                            totalCost, finalCostLimit != null ? finalCostLimit : 0.0,
                            totalKwh, finalKwhLimit != null ? finalKwhLimit : 0.0));
                        
                        // Check thresholds and send in-app notifications
                        checkAndSendInAppNotifications(totalCost, totalKwh, finalCostLimit, finalKwhLimit);
                    }
                    @Override
                    public void onCancelled(DatabaseError error) {
                        android.util.Log.e("InAppCheck", "❌ Error getting hourly summaries: " + error.getMessage());
                    }
                });
            }
            @Override
            public void onCancelled(DatabaseError error) {
                android.util.Log.e("InAppCheck", "❌ Error getting date range: " + error.getMessage());
            }
        });
    }

    private void checkAndSendInAppNotifications(double totalCost, double totalKwh, Double costLimit, Double kwhLimit) {
        boolean costNear = false, costReached = false, costExceeded = false;
        boolean kwhNear = false, kwhReached = false, kwhExceeded = false;
        
        double costPercent = 0.0, kwhPercent = 0.0;
        
        if (costLimit != null && costLimit > 0) {
            costPercent = (totalCost / costLimit) * 100.0;
            costNear = (costPercent >= 85.0 - 0.01) && (costPercent < 100.0 - 0.01);
            costReached = ((totalCost + 0.01 >= costLimit) || (costPercent >= 100.0 - 0.01)) && (costPercent < 101.0 - 0.01);
            costExceeded = costPercent >= 101.0 - 0.01;
        }
        
        if (kwhLimit != null && kwhLimit > 0) {
            kwhPercent = (totalKwh / kwhLimit) * 100.0;
            kwhNear = (kwhPercent >= 85.0 - 0.01) && (kwhPercent < 100.0 - 0.01);
            kwhReached = ((totalKwh + 0.001 >= kwhLimit) || (kwhPercent >= 100.0 - 0.01)) && (kwhPercent < 101.0 - 0.01);
            kwhExceeded = kwhPercent >= 101.0 - 0.01;
        }
        
        android.util.Log.d("InAppCheck", String.format("📊 Threshold check - Cost: %.2f%% (near=%s, reached=%s, exceeded=%s), kWh: %.3f%% (near=%s, reached=%s, exceeded=%s)", 
            costPercent, costNear, costReached, costExceeded, kwhPercent, kwhNear, kwhReached, kwhExceeded));
        
        // Send notifications for current state
        if (costExceeded || kwhExceeded) {
            if (costExceeded && kwhExceeded) {
                double costOver = Math.max(0, costPercent - 100.0);
                double kwhOver = Math.max(0, kwhPercent - 100.0);
                notifyNow("Budget & Energy Limit Exceeded",
                    String.format("You've exceeded both limits: Budget by %d%% and Energy by %d%%.", 
                    Math.round(costOver), Math.round(kwhOver)));
            } else if (costExceeded) {
                double over = Math.max(0, costPercent - 100.0);
                notifyNow("Budget Exceeded",
                    String.format("You've gone over your budget by %d%%: ₱%.2f of ₱%.2f.", 
                    Math.round(over), totalCost, costLimit));
            } else if (kwhExceeded) {
                double over = Math.max(0, kwhPercent - 100.0);
                notifyNow("Energy Limit Exceeded",
                    String.format("You've exceeded your energy limit by %d%%: %.3f kWh of %.3f kWh.", 
                    Math.round(over), totalKwh, kwhLimit));
            }
        } else if (costReached || kwhReached) {
            if (costReached && kwhReached) {
                notifyNow("Budget & Energy Reached (100%%)",
                    String.format("You've reached both limits: ₱%.2f and %.3f kWh.", totalCost, totalKwh));
            } else if (costReached) {
                notifyNow("Budget Reached (100%%)",
                    String.format("You've reached your budget: ₱%.2f of ₱%.2f (100%%).", totalCost, costLimit));
            } else if (kwhReached) {
                notifyNow("Energy Limit Reached (100%%)",
                    String.format("You've reached your energy limit: %.3f kWh of %.3f kWh (100%%).", totalKwh, kwhLimit));
            }
        } else if (costNear || kwhNear) {
            if (costNear) {
                notifyNow("Approaching Budget Limit",
                    String.format("You're approaching your budget limit: %d%% (₱%.2f of ₱%.2f)", 
                    Math.round(costPercent), totalCost, costLimit));
            }
            if (kwhNear) {
                notifyNow("Approaching Energy Limit",
                    String.format("You're approaching your energy limit: %d%% (%.3f kWh of %.3f kWh)", 
                    Math.round(kwhPercent), totalKwh, kwhLimit));
            }
        }
    }

    private void resetBackendNotificationFlags() {
        // Call backend API to reset notification flags when new thresholds are set
        new Thread(() -> {
            try {
                java.net.URL url = new java.net.URL("http://localhost:3000/reset-notifications");
                java.net.HttpURLConnection connection = (java.net.HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);
                
                int responseCode = connection.getResponseCode();
                if (responseCode == 200) {
                    android.util.Log.d("FCM", "✅ Backend notification flags reset successfully");
                } else {
                    android.util.Log.w("FCM", "⚠️ Failed to reset backend flags: " + responseCode);
                }
                connection.disconnect();
            } catch (Exception e) {
                android.util.Log.w("FCM", "⚠️ Could not connect to backend: " + e.getMessage());
                // This is okay - backend might not be running
            }
        }).start();
    }

    private void checkForMissedNotifications() {
        android.util.Log.d("BackgroundCheck", "🔍 Checking for missed notifications while app was closed");
        
        // Check threshold notifications
        db.child("threshold").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                // This will trigger the threshold monitoring logic
                // which will check if any notifications were missed
                android.util.Log.d("BackgroundCheck", "✅ Threshold check completed on app start");
            }
            @Override public void onCancelled(DatabaseError error) { }
        });
        
        // Check voltage fluctuations
        db.child("logs").limitToLast(1).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                if (switchVoltage != null && switchVoltage.isChecked()) {
                    android.util.Log.d("BackgroundCheck", "✅ Voltage check completed on app start");
                    // The voltage monitoring logic will check for missed fluctuations
                }
            }
            @Override public void onCancelled(DatabaseError error) { }
        });
    }

    private void createNotificationChannel() {
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "I-WATTS Alerts", NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("Notifications for voltage, updates, and thresholds");
            manager.createNotificationChannel(channel);
        }
    }

    private void notifyNow(String title, String message) {
        // ALWAYS save to database - notifications are always stored regardless of switches
        logAlertToDatabase(title, message);
        android.util.Log.d("AlertActivity", "✅ Notification saved to database: " + title);
        
        // Only send push notification if Push switch is ON and permission is granted
        boolean canPush = (switchPush != null && switchPush.isChecked()) && hasNotificationPermission();
        if (canPush) {
            android.util.Log.d("AlertActivity", "📲 Push enabled - sending push notification");
            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) {
                NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                        .setSmallIcon(R.drawable.ic_notification)
                        .setContentTitle(title)
                        .setContentText(message)
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setAutoCancel(true);
                int id = (int) System.currentTimeMillis();
                manager.notify(id, builder.build());
                android.util.Log.d("AlertActivity", "✅ Push notification sent");
            }
        } else {
            android.util.Log.d("AlertActivity", "🔕 Push disabled - notification saved to database only (will show in Notification Activity)");
        }
    }

    private void logAlertToDatabase(String title, String message) {
        android.util.Log.d("AlertDatabase", "💾 Saving alert to database - Title: " + title);
        String type = inferAlertType((title == null ? "" : title) + " " + (message == null ? "" : message));
        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
        DatabaseReference alertsRef = db.child("alerts");
        Map<String, Object> data = new HashMap<>();
        data.put("type", type);
        data.put("title", title);
        data.put("message", message);
        data.put("time", timestamp);
        data.put("read", false);
        data.put("delete", false);
        alertsRef.push().setValue(data, (error, ref) -> {
            if (error != null) {
                android.util.Log.e("AlertDatabase", "❌ Failed to save alert: " + error.getMessage());
            } else {
                android.util.Log.d("AlertDatabase", "✅ Alert saved successfully with key: " + ref.getKey());
            }
        });
    }

    private String inferAlertType(String title) {
        String t = title == null ? "" : title.toLowerCase(Locale.getDefault());
        if (t.contains("voltage")) return "fluctuation";
        if (t.contains("system")) return "systemUpdates";
        if (t.contains("kwh") || t.contains("energy")) return "power";
        if (t.contains("cost") || t.contains("budget")) return "budget";
        return "general";
    }

    private void resetVoltageBaseline() {
        android.util.Log.d("VoltageMonitoring", "🔄 Resetting voltage baseline to NOW");
        
        // Get the current latest entry and set it as the baseline
        DatabaseReference logsRef = db.child("logs");
        logsRef.limitToLast(1).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                // Find the absolute latest entry
                DataSnapshot latestDate = null;
                for (DataSnapshot d : snapshot.getChildren()) latestDate = d;
                
                if (latestDate != null) {
                    DataSnapshot latestEntry = null;
                    for (DataSnapshot e : latestDate.getChildren()) latestEntry = e;
                    
                    if (latestEntry != null) {
                        String currentLatestKey = latestEntry.getKey();
                        
                        // FORCE set to current latest (ignore any saved value)
                        lastVoltageKeyNotified = currentLatestKey;
                        db.child("notification_settings").child("last_voltage_notified").setValue(currentLatestKey);
                        android.util.Log.d("VoltageMonitoring", "✅ Voltage baseline RESET to current: " + currentLatestKey);
                        android.util.Log.d("VoltageMonitoring", "⏰ Only fluctuations AFTER this moment will trigger alerts");
                    }
                }
            }
            @Override public void onCancelled(DatabaseError error) {
                android.util.Log.e("VoltageMonitoring", "❌ Error resetting baseline: " + error.getMessage());
            }
        });
    }
    
    private void startVoltageMonitoring() {
        android.util.Log.d("VoltageMonitoring", "🔌 startVoltageMonitoring() called");
        
        // Remove previous listener to avoid stacking
        DatabaseReference logsRef = db.child("logs");
        if (voltageListener != null) {
            logsRef.removeEventListener(voltageListener);
            android.util.Log.d("VoltageMonitoring", "Removed previous listener");
        }
        
        // First, initialize the last notified key to the current latest entry to prevent old notifications
        logsRef.limitToLast(1).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                // Find the absolute latest entry
                DataSnapshot latestDate = null;
                for (DataSnapshot d : snapshot.getChildren()) latestDate = d;
                
                if (latestDate != null) {
                    DataSnapshot latestEntry = null;
                    for (DataSnapshot e : latestDate.getChildren()) latestEntry = e;
                    
                    if (latestEntry != null) {
                        String currentLatestKey = latestEntry.getKey();
                        
                        // Load the saved last notified key
                        db.child("notification_settings").child("last_voltage_notified").addListenerForSingleValueEvent(new ValueEventListener() {
                            @Override
                            public void onDataChange(DataSnapshot savedSnapshot) {
                                String savedKey = savedSnapshot.getValue(String.class);
                                
                                // If no saved key exists, initialize it to current latest to prevent spam
                                if (savedKey == null || savedKey.isEmpty()) {
                                    lastVoltageKeyNotified = currentLatestKey;
                                    db.child("notification_settings").child("last_voltage_notified").setValue(currentLatestKey);
                                    android.util.Log.d("VoltageMonitoring", "Initialized last notified key to current: " + currentLatestKey);
                                } else {
                                    lastVoltageKeyNotified = savedKey;
                                    android.util.Log.d("VoltageMonitoring", "Loaded saved last notified key: " + savedKey);
                                }
                                
                                // Now set up the ongoing listener for FUTURE changes only
                                setupVoltageListener();
                            }
                            @Override public void onCancelled(DatabaseError error) { 
                                setupVoltageListener(); // Set up listener anyway
                            }
                        });
                    } else {
                        setupVoltageListener();
                    }
                } else {
                    setupVoltageListener();
                }
            }
            @Override public void onCancelled(DatabaseError error) { 
                setupVoltageListener(); // Set up listener anyway
            }
        });
    }
    
    private void setupVoltageListener() {
        android.util.Log.d("VoltageMonitoring", "Setting up voltage listener with last key: " + lastVoltageKeyNotified);
        
        DatabaseReference logsRef = db.child("logs");
        voltageListener = new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                // Watch only the latest log to prevent bulk notifications
                DataSnapshot latestDate = null;
                for (DataSnapshot d : snapshot.getChildren()) latestDate = d;
                
                if (latestDate == null) {
                    return;
                }
                
                DataSnapshot latestEntry = null;
                for (DataSnapshot e : latestDate.getChildren()) latestEntry = e;
                
                if (latestEntry == null) {
                    return;
                }

                String key = latestEntry.getKey();
                
                // CRITICAL: Skip if this is the same key or older than what we've already processed
                if (key != null && key.equals(lastVoltageKeyNotified)) {
                    return; // Already processed this entry
                }
                
                // Additional check: if we have a lastVoltageKeyNotified, ensure new key is actually newer
                if (lastVoltageKeyNotified != null && key != null && key.compareTo(lastVoltageKeyNotified) <= 0) {
                    android.util.Log.d("VoltageMonitoring", "Skipping older/same entry: " + key);
                    return;
                }

                int f1 = toInt(latestEntry.child("Fluct1").getValue());
                int f2 = toInt(latestEntry.child("Fluct2").getValue());
                int f3 = toInt(latestEntry.child("Fluct3").getValue());
                
                boolean a1 = (f1 == 1);
                boolean a2 = (f2 == 1);
                boolean a3 = (f3 == 1);
                
                // Update lastVoltageKeyNotified immediately to prevent duplicate processing
                String previousKey = lastVoltageKeyNotified;
                lastVoltageKeyNotified = key;
                db.child("notification_settings").child("last_voltage_notified").setValue(key);
                
                if (a1 || a2 || a3) {
                    // Get date and time information
                    String dateKey = latestDate.getKey();
                    String timeKey = key;
                    
                    // Build the message with areas
                    StringBuilder msg = new StringBuilder("Voltage fluctuation detected in ");
                    if (a1) msg.append("Area 1 ");
                    if (a2) msg.append("Area 2 ");
                    if (a3) msg.append("Area 3 ");
                    
                    // Add date and time information
                    if (dateKey != null && timeKey != null) {
                        try {
                            SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault());
                            SimpleDateFormat outputFormat = new SimpleDateFormat("MMM dd, yyyy hh:mm:ss a", Locale.getDefault());
                            Date timestamp = inputFormat.parse(timeKey);
                            if (timestamp != null) {
                                msg.append("at ").append(outputFormat.format(timestamp));
                            } else {
                                msg.append("on ").append(dateKey).append(" at ").append(timeKey);
                            }
                        } catch (Exception e) {
                            msg.append("on ").append(dateKey).append(" at ").append(timeKey);
                        }
                    }
                    
                    android.util.Log.d("VoltageMonitoring", "⚡ NEW VOLTAGE ALERT: " + msg.toString().trim() + " (previous: " + previousKey + ", current: " + key + ")");
                    notifyNow("Voltage Fluctuation", msg.toString().trim());
                } else {
                    android.util.Log.d("VoltageMonitoring", "Updated last key to " + key + " (no fluctuation)");
                }
            }
            @Override public void onCancelled(DatabaseError error) { }
        };
        logsRef.addValueEventListener(voltageListener);
        android.util.Log.d("VoltageMonitoring", "✅ Listener attached");
    }

    private int toInt(Object v) {
        if (v == null) return 0;
        if (v instanceof Number) return ((Number) v).intValue();
        try { return Integer.parseInt(String.valueOf(v)); } catch (Exception e) { return 0; }
    }

    private void startThresholdMonitoring() {
        android.util.Log.d("ThresholdMonitoring", "🚀 startThresholdMonitoring() called");
        // Listen to thresholds and date filter; compare sums within date range
        DatabaseReference thresholdRef = db.child("threshold");
        if (thresholdRefListener != null) thresholdRef.removeEventListener(thresholdRefListener);
        thresholdRefListener = new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot tSnap) {
                android.util.Log.d("ThresholdMonitoring", "📥 Threshold data changed - evaluating limits");
                Double kwhLimit = parseDouble(tSnap.child("kwh_value").getValue());
                Double costLimit = parseDouble(tSnap.child("cost_value").getValue());
                android.util.Log.d("ThresholdMonitoring", "Limits: kwh=" + kwhLimit + " cost=" + costLimit);
                Boolean finalNotified = (Boolean) tSnap.child("final_notified").getValue(Boolean.class);
                finalThresholdAlertSent = finalNotified != null && finalNotified;
                Boolean reachedNotified = (Boolean) tSnap.child("reached_notified").getValue(Boolean.class);
                reachedCostSent = reachedNotified != null && reachedNotified;
                reachedKwhSent = reachedNotified != null && reachedNotified;
                android.util.Log.d("ThresholdMonitoring", "Flags loaded: finalSent=" + finalThresholdAlertSent + " reachedSent=" + reachedCostSent);
                if (kwhLimit == null && costLimit == null) {
                    android.util.Log.d("ThresholdMonitoring", "❌ No limits set, skipping check");
                    return;
                }
                android.util.Log.d("ThresholdMonitoring", "✅ Limits valid, fetching date range...");

                // Fetch date range
                DatabaseReference filterRef = db.child("cost_filter_date");
                if (costFilterListener != null) filterRef.removeEventListener(costFilterListener);
                costFilterListener = new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot filterSnap) {
                        android.util.Log.d("ThresholdMonitoring", "📅 Date filter loaded");
                        Object sObj = filterSnap.child("starting_date").getValue();
                        Object eObj = filterSnap.child("ending_date").getValue();
                        String startStr = sObj == null ? null : sObj.toString();
                        String endStr = eObj == null ? null : eObj.toString();
                        android.util.Log.d("ThresholdMonitoring", "Date range: " + startStr + " to " + endStr);
                        Date startDate = parseDateLenient(startStr);
                        Date endDate = parseDateLenient(endStr);
                        if (startDate == null || endDate == null) {
                            android.util.Log.e("ThresholdMonitoring", "❌ Invalid date range, aborting check");
                            return;
                        }

                        // Read a fresh snapshot once to avoid double-counting from stacked listeners
                        android.util.Log.d("ThresholdMonitoring", "📊 Fetching hourly_summaries...");
                        db.child("hourly_summaries").addListenerForSingleValueEvent(new ValueEventListener() {
                            @Override
                            public void onDataChange(DataSnapshot dataSnapshot) {
                                android.util.Log.d("ThresholdMonitoring", "✅ hourly_summaries loaded, calculating totals...");
                                double totalKwh = 0.0;
                                double totalCost = 0.0;
                                for (DataSnapshot dateSnap : dataSnapshot.getChildren()) {
                                    String dateKey = dateSnap.getKey();
                                    Date current = parseDateLenient(dateKey);
                                    if (current == null) continue;
                                    if (!isWithinInclusive(current, startDate, endDate)) continue;

                                    for (DataSnapshot hourSnap : dateSnap.getChildren()) {
                                        totalKwh += asDouble(hourSnap.child("total_kwh").getValue());
                                        totalCost += asDouble(hourSnap.child("total_cost").getValue());
                                    }
                                }

                                // Debug logs to help verify numbers
                                android.util.Log.d("ThresholdCheck", "Range=" + startStr + ".." + endStr +
                                        " totalCost=" + String.format(java.util.Locale.getDefault(), "%.2f", totalCost) +
                                        " totalKwh=" + String.format(java.util.Locale.getDefault(), "%.3f", totalKwh) +
                                        " limits cost=" + costLimit + " kwh=" + kwhLimit);
                                android.util.Log.d("ThresholdCheck", "reachedCostSent=" + reachedCostSent + " reachedKwhSent=" + reachedKwhSent + " finalSent=" + finalThresholdAlertSent);

                                long now = System.currentTimeMillis();
                                boolean hasKwhLimit = (kwhLimit != null && kwhLimit > 0);
                                boolean hasCostLimit = (costLimit != null && costLimit > 0);
                                double kwhPercent = hasKwhLimit ? (totalKwh / kwhLimit) * 100.0 : 0.0;
                                double costPercent = hasCostLimit ? (totalCost / costLimit) * 100.0 : 0.0;

                                final double EPS = 1e-4;             // percent tolerance
                                final double MONEY_EPS = 0.01;        // ₱0.01 tolerance
                                final double KWH_EPS = 0.001;         // 0.001 kWh tolerance

                                boolean kwhNear = hasKwhLimit && kwhPercent >= START_PERCENT && kwhPercent < 100.0 - EPS;
                                boolean costNear = hasCostLimit && costPercent >= START_PERCENT && costPercent < 100.0 - EPS;

                                // Reached = at least the limit (with small absolute tolerance) but below 101%
                                boolean kwhReached = hasKwhLimit && ((totalKwh + KWH_EPS >= (kwhLimit != null ? kwhLimit : 0.0)) || (kwhPercent >= 100.0 - EPS)) && (kwhPercent < 101.0 - EPS);
                                boolean costReached = hasCostLimit && ((totalCost + MONEY_EPS >= (costLimit != null ? costLimit : 0.0)) || (costPercent >= 100.0 - EPS)) && (costPercent < 101.0 - EPS);

                                // Exceeded = >= 101%
                                boolean kwhExceeded = hasKwhLimit && kwhPercent >= 101.0 - EPS;
                                boolean costExceeded = hasCostLimit && costPercent >= 101.0 - EPS;

                                android.util.Log.d("ThresholdCheck", "cost%=" + String.format("%.2f", costPercent) + " kwh%=" + String.format("%.2f", kwhPercent));
                                android.util.Log.d("ThresholdCheck", "costReached=" + costReached + " kwhReached=" + kwhReached + " costExceeded=" + costExceeded + " kwhExceeded=" + kwhExceeded);

                                // Final one-time notification when exceeded
                                if (!finalThresholdAlertSent && (kwhExceeded || costExceeded)) {
                                    finalThresholdAlertSent = true;
                                    String title;
                                    if (costExceeded && !kwhExceeded)      title = "Budget Exceeded";
                                    else if (!costExceeded && kwhExceeded) title = "Energy Limit Exceeded";
                                    else                                   title = "Budget and Energy Exceeded";

                                    StringBuilder finalMsg = new StringBuilder();
                                    if (costExceeded && hasCostLimit) {
                                        double over = Math.max(0.0, costPercent - 100.0);
                                        finalMsg.append(String.format(Locale.getDefault(),
                                                "You've gone over your budget by %.0f%%: ₱%.2f of ₱%.2f.",
                                                over, totalCost, costLimit));
                                    }
                                    if (kwhExceeded && hasKwhLimit) {
                                        if (finalMsg.length() > 0) finalMsg.append(" ");
                                        double overK = Math.max(0.0, kwhPercent - 100.0);
                                        finalMsg.append(String.format(Locale.getDefault(),
                                                "You've exceeded your energy limit by %.0f%%: %.3f kWh of %.3f kWh.",
                                                overK, totalKwh, kwhLimit));
                                    }
                                    notifyNow(title, finalMsg.toString());
                                    // persist so we don't re-notify if restarted
                                    db.child("threshold").child("final_notified").setValue(true);
                                    return; // stop further near alerts after final
                                }

                                // One-time 100% reached alert (before 101%)
                                // Check independently: notify if cost reached OR kwh reached (and not already sent)
                                if (!finalThresholdAlertSent) {
                                    boolean shouldNotifyCost = costReached && !reachedCostSent;
                                    boolean shouldNotifyKwh = kwhReached && !reachedKwhSent;
                                    
                                    if (shouldNotifyCost || shouldNotifyKwh) {
                                        String title;
                                        if (shouldNotifyCost && !shouldNotifyKwh)      title = "Budget Reached (100%)";
                                        else if (!shouldNotifyCost && shouldNotifyKwh) title = "Energy Limit Reached (100%)";
                                        else                                            title = "Budget and Energy Reached (100%)";

                                        StringBuilder reachedMsg = new StringBuilder();
                                        if (shouldNotifyCost && hasCostLimit) {
                                            reachedMsg.append(String.format(Locale.getDefault(),
                                                    "You've reached your budget: ₱%.2f of ₱%.2f (100%%).",
                                                    totalCost, costLimit));
                                        }
                                        if (shouldNotifyKwh && hasKwhLimit) {
                                            if (reachedMsg.length() > 0) reachedMsg.append(" ");
                                            reachedMsg.append(String.format(Locale.getDefault(),
                                                    "You've reached your energy limit: %.3f kWh of %.3f kWh (100%%).",
                                                    totalKwh, kwhLimit));
                                        }
                                        android.util.Log.d("ThresholdCheck", "🔔 SENDING REACHED NOTIFICATION: " + title);
                                        android.util.Log.d("ThresholdCheck", "Message: " + reachedMsg.toString());
                                        notifyNow(title, reachedMsg.toString());
                                        android.util.Log.d("ThresholdCheck", "✅ notifyNow() called successfully");
                                        // mark reached flags so Exceeded can still notify later
                                        if (shouldNotifyKwh) reachedKwhSent = true;
                                        if (shouldNotifyCost) reachedCostSent = true;
                                        android.util.Log.d("ThresholdCheck", "REACHED fired: cost%=" + costPercent + " kwh%=" + kwhPercent);
                                        // persist reached notification to database
                                        db.child("threshold").child("reached_notified").setValue(true);
                                        // do not mark final_notified here; allow exceeded to still trigger later
                                    }
                                }

                                if (!finalThresholdAlertSent && (kwhNear || costNear)) {
                                    boolean shouldNotify = false;
                                    StringBuilder msg = new StringBuilder();

                                    if (kwhNear) {
                                        int stepIdx = (int) Math.floor((kwhPercent - START_PERCENT) / STEP_PERCENT);
                                        if (stepIdx > lastKwhStepSent) {
                                            lastKwhStepSent = stepIdx;
                                            shouldNotify = true;
                                        }
                                    }
                                    if (costNear) {
                                        int stepIdx = (int) Math.floor((costPercent - START_PERCENT) / STEP_PERCENT);
                                        if (stepIdx > lastCostStepSent) {
                                            lastCostStepSent = stepIdx;
        
                                            shouldNotify = true;
                                        }
                                    }

                                    if (shouldNotify) {
                                        lastCombinedAlertMs = now;
                                        lastNotifiedKwh = totalKwh;
                                        lastNotifiedCost = totalCost;

                                        String title;
                                        if (costNear && !kwhNear)       title = "Approaching Budget";
                                        else if (!costNear && kwhNear)  title = "Approaching Energy Limit";
                                        else                            title = "Approaching Limits";

                                        if (costNear && hasCostLimit) {
                                            msg.append(String.format(Locale.getDefault(),
                                                    "You're at %.0f%% of your budget: ₱%.2f of ₱%.2f.",
                                                    costPercent, totalCost, costLimit));
                                        }
                                        if (kwhNear && hasKwhLimit) {
                                            if (msg.length() > 0) msg.append(" ");
                                            msg.append(String.format(Locale.getDefault(),
                                                    "You're at %.0f%% of your energy limit: %.3f kWh of %.3f kWh.",
                                                    kwhPercent, totalKwh, kwhLimit));
                                        }
                                        notifyNow(title, msg.toString());
                                    }
                                }
                            }
                            @Override public void onCancelled(DatabaseError error) { }
                        });
                    }
                    @Override public void onCancelled(DatabaseError error) { }
                };
                filterRef.addValueEventListener(costFilterListener);
            }
            @Override public void onCancelled(DatabaseError error) { }
        };
        thresholdRef.addValueEventListener(thresholdRefListener);
        // Trigger an immediate check after (re)starting monitoring
        db.child("threshold").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(DataSnapshot snapshot) { if (thresholdRefListener != null) thresholdRefListener.onDataChange(snapshot); }
            @Override public void onCancelled(DatabaseError error) { }
        });
    }

    private Double parseDouble(Object v) {
        if (v == null) return null;
        if (v instanceof Number) return ((Number) v).doubleValue();
        try { return Double.parseDouble(String.valueOf(v)); } catch (Exception e) { return null; }
    }

    private double asDouble(Object v) {
        if (v == null) return 0.0;
        if (v instanceof Number) return ((Number) v).doubleValue();
        try { return Double.parseDouble(String.valueOf(v)); } catch (Exception e) { return 0.0; }
    }

    private Date parseDateLenient(String s) {
        if (s == null) return null;
        String[] patterns = {"yyyy-MM-dd", "yyyy-M-d"};
        for (String p : patterns) {
            try { return new SimpleDateFormat(p, Locale.getDefault()).parse(s); } catch (Exception ignored) {}
        }
        return null;
    }

    private boolean isWithinInclusive(Date d, Date start, Date end) {
        return (d.equals(start) || d.after(start)) && (d.equals(end) || d.before(end));
    }

    private void computeBudgetFromPower() {
        if (etPowerValue == null || etBudgetValue == null) return;
        String wattsText = etPowerValue.getText() != null ? etPowerValue.getText().toString().trim() : "";
        if (wattsText.isEmpty()) return;
        try {
            // Interpret input directly as kWh
            double kwh = Double.parseDouble(wattsText);
            if (kwh < 0) return;
            if (electricityRatePerKwh == null || electricityRatePerKwh <= 0) return;
            double cost = kwh * electricityRatePerKwh; // total cost for the entered kWh
            updatingFromPower = true;
            etBudgetValue.setText(String.format(Locale.getDefault(), "%.2f", cost));
        } catch (NumberFormatException ignored) {
        } finally {
            updatingFromPower = false;
        }
    }

    private void computePowerFromBudget() {
        if (etPowerValue == null || etBudgetValue == null) return;
        String budgetText = etBudgetValue.getText() != null ? etBudgetValue.getText().toString().trim() : "";
        if (budgetText.isEmpty()) return;
        try {
            double budget = Double.parseDouble(budgetText);
            if (budget < 0) return;
            if (electricityRatePerKwh == null || electricityRatePerKwh <= 0) return;
            double kwh = budget / electricityRatePerKwh; // kWh corresponding to the budget
            updatingFromBudget = true;
            etPowerValue.setText(String.format(Locale.getDefault(), "%.3f", kwh));
        } catch (NumberFormatException ignored) {
        } finally {
            updatingFromBudget = false;
        }
    }




}