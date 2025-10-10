package com.example.sampleiwatts;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class NotificationMonitorService extends Service {
    
    private static final String TAG = "NotificationMonitor";
    private static final String CHANNEL_ID = "monitoring_channel";
    private static final String ALERT_CHANNEL_ID = "alerts_channel";
    private static final int FOREGROUND_ID = 2001;
    
    private DatabaseReference db;
    private ValueEventListener thresholdListener;
    private ValueEventListener voltageListener;
    private ValueEventListener hourlySummariesListener;
    private android.os.Handler periodicHandler;
    private Runnable periodicRunnable;
    
    private String lastVoltageKeyNotified = null;
    private double lastNotifiedCost = -1.0;
    private double lastNotifiedKwh = -1.0;
    private int lastCostStepSent = -1;
    private int lastKwhStepSent = -1;
    private boolean finalThresholdAlertSent = false;
    private boolean reachedCostSent = false;
    private boolean reachedKwhSent = false;
    
    private static final double EPS = 0.01;
    private static final double MONEY_EPS = 0.01;
    private static final double KWH_EPS = 0.001;
    private static final double STEP_PERCENT = 3.0;
    private static final double START_PERCENT = 85.0;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "🚀 Service created");
        db = FirebaseDatabase.getInstance().getReference();
        
        // Create notification channels
        createNotificationChannels();
        
        // Start as foreground service
        startForeground(FOREGROUND_ID, createForegroundNotification());
        
        // Load last notified voltage key
        loadLastVoltageKey();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "═══════════════════════════════════════════════");
        Log.d(TAG, "🎬 SERVICE STARTED");
        Log.d(TAG, "═══════════════════════════════════════════════");
        
        // Check if this is a manual trigger
        if (intent != null && "MANUAL_CHECK".equals(intent.getAction())) {
            Log.d(TAG, "🔍 Manual threshold check requested");
            manualThresholdCheck();
            return START_NOT_STICKY; // Don't restart for manual checks
        }
        
        // Reset step tracking when service starts
        Log.d(TAG, "🔄 Resetting all tracking flags...");
        resetStepTracking();
        
        // Start monitoring
        Log.d(TAG, "🚀 Starting threshold monitoring...");
        startThresholdMonitoring();
        
        Log.d(TAG, "🚀 Starting voltage monitoring...");
        startVoltageMonitoring();
        
        Log.d(TAG, "🚀 Starting hourly summaries monitoring...");
        startHourlySummariesMonitoring();
        
        // Start periodic checks every 30 seconds to catch any missed changes
        Log.d(TAG, "⏰ Starting periodic checks (every 30 seconds)...");
        startPeriodicChecks();
        
        // Perform initial threshold check after a short delay
        new android.os.Handler().postDelayed(() -> {
            Log.d(TAG, "═══════════════════════════════════════════════");
            Log.d(TAG, "🔍 PERFORMING INITIAL THRESHOLD CHECK");
            Log.d(TAG, "═══════════════════════════════════════════════");
            performThresholdCheck();
        }, 2000); // 2 second delay to ensure everything is set up
        
        Log.d(TAG, "✅ Service initialization complete - monitoring is active");
        return START_STICKY; // Service will restart if killed
    }

    private void createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager == null) return;
            
            // Channel for foreground service
            NotificationChannel monitoringChannel = new NotificationChannel(
                CHANNEL_ID,
                "Background Monitoring",
                NotificationManager.IMPORTANCE_LOW
            );
            monitoringChannel.setDescription("Keeps the app monitoring for alerts in the background");
            manager.createNotificationChannel(monitoringChannel);
            
            // Channel for alerts
            NotificationChannel alertChannel = new NotificationChannel(
                ALERT_CHANNEL_ID,
                "Alerts",
                NotificationManager.IMPORTANCE_HIGH
            );
            alertChannel.setDescription("Important alerts and notifications");
            manager.createNotificationChannel(alertChannel);
        }
    }

    private android.app.Notification createForegroundNotification() {
        Intent notificationIntent = new Intent(this, DashboardActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE
        );

        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("I-WATTS Monitoring")
            .setContentText("Monitoring your energy usage")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build();
    }

    private void loadLastVoltageKey() {
        db.child("notification_settings").child("last_voltage_notified")
            .addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot snapshot) {
                    lastVoltageKeyNotified = snapshot.getValue(String.class);
                    Log.d(TAG, "📥 Last voltage key loaded: " + lastVoltageKeyNotified);
                }
                @Override
                public void onCancelled(DatabaseError error) {}
            });
    }

    private void startThresholdMonitoring() {
        Log.d(TAG, "🚀 Starting threshold monitoring");
        
        thresholdListener = db.child("threshold").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                String kwhText = snapshot.child("kwh_value").getValue(String.class);
                String costText = snapshot.child("cost_value").getValue(String.class);
                
                Double kwhLimit = null, costLimit = null;
                if (kwhText != null && !kwhText.isEmpty()) {
                    try { kwhLimit = Double.parseDouble(kwhText); } catch (Exception e) {}
                }
                if (costText != null && !costText.isEmpty()) {
                    try { costLimit = Double.parseDouble(costText); } catch (Exception e) {}
                }
                
                if (kwhLimit == null && costLimit == null) {
                    Log.d(TAG, "⚠️ No threshold limits set");
                    return;
                }
                
                // Load flags from server
                Boolean serverFinal = snapshot.child("final_notified").getValue(Boolean.class);
                Boolean serverReached = snapshot.child("reached_notified").getValue(Boolean.class);
                if (serverFinal != null) finalThresholdAlertSent = serverFinal;
                if (serverReached != null) {
                    reachedCostSent = serverReached;
                    reachedKwhSent = serverReached;
                }
                
                final Double finalKwhLimit = kwhLimit;
                final Double finalCostLimit = costLimit;
                
                // Get date range
                db.child("cost_filter_date").addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot filterSnapshot) {
                        String startDate = filterSnapshot.child("starting_date").getValue(String.class);
                        String endDate = filterSnapshot.child("ending_date").getValue(String.class);
                        
                        if (startDate == null || endDate == null) return;
                        
                        // Calculate totals
                        calculateTotalsAndCheckThresholds(startDate, endDate, finalKwhLimit, finalCostLimit);
                    }
                    @Override
                    public void onCancelled(DatabaseError error) {}
                });
            }
            @Override
            public void onCancelled(DatabaseError error) {}
        });
    }

    private void calculateTotalsAndCheckThresholds(String startDate, String endDate, Double kwhLimit, Double costLimit) {
        db.child("hourly_summaries").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                double totalCost = 0.0;
                double totalKwh = 0.0;
                
                for (DataSnapshot dateSnap : snapshot.getChildren()) {
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
                
                Log.d(TAG, String.format("📊 Totals: Cost=%.2f, kWh=%.3f", totalCost, totalKwh));
                
                // Check thresholds
                checkThresholds(totalCost, totalKwh, costLimit, kwhLimit);
            }
            @Override
            public void onCancelled(DatabaseError error) {}
        });
    }

    private void checkThresholds(double totalCost, double totalKwh, Double costLimit, Double kwhLimit) {
        Log.d(TAG, "═══════════════════════════════════════════════");
        Log.d(TAG, "🔍 CHECKING THRESHOLDS");
        Log.d(TAG, String.format("📊 Total Cost: %.2f (Limit: %.2f)", totalCost, costLimit != null ? costLimit : 0.0));
        Log.d(TAG, String.format("📊 Total kWh: %.3f (Limit: %.3f)", totalKwh, kwhLimit != null ? kwhLimit : 0.0));
        
        boolean costNear = false, costReached = false, costExceeded = false;
        boolean kwhNear = false, kwhReached = false, kwhExceeded = false;
        
        double costPercent = 0.0, kwhPercent = 0.0;
        
        if (costLimit != null && costLimit > 0) {
            costPercent = (totalCost / costLimit) * 100.0;
            Log.d(TAG, String.format("💰 Cost Percentage: %.2f%%", costPercent));
            costNear = (costPercent >= START_PERCENT - EPS) && (costPercent < 100.0 - EPS);
            costReached = ((totalCost + MONEY_EPS >= costLimit) || (costPercent >= 100.0 - EPS)) && (costPercent < 101.0 - EPS);
            costExceeded = costPercent >= 101.0 - EPS;
            Log.d(TAG, String.format("💰 Cost Status: Near=%s, Reached=%s, Exceeded=%s", costNear, costReached, costExceeded));
        } else {
            Log.d(TAG, "⚠️ No cost limit set or limit is 0");
        }
        
        if (kwhLimit != null && kwhLimit > 0) {
            kwhPercent = (totalKwh / kwhLimit) * 100.0;
            Log.d(TAG, String.format("⚡ kWh Percentage: %.2f%%", kwhPercent));
            kwhNear = (kwhPercent >= START_PERCENT - EPS) && (kwhPercent < 100.0 - EPS);
            kwhReached = ((totalKwh + KWH_EPS >= kwhLimit) || (kwhPercent >= 100.0 - EPS)) && (kwhPercent < 101.0 - EPS);
            kwhExceeded = kwhPercent >= 101.0 - EPS;
            Log.d(TAG, String.format("⚡ kWh Status: Near=%s, Reached=%s, Exceeded=%s", kwhNear, kwhReached, kwhExceeded));
        } else {
            Log.d(TAG, "⚠️ No kWh limit set or limit is 0");
        }
        
        // Send notifications
        if (!finalThresholdAlertSent && (costExceeded || kwhExceeded)) {
            finalThresholdAlertSent = true;
            db.child("threshold").child("final_notified").setValue(true);
            
            if (costExceeded && kwhExceeded) {
                double costOver = Math.max(0, costPercent - 100.0);
                double kwhOver = Math.max(0, kwhPercent - 100.0);
                sendNotification("Budget & Energy Limit Exceeded",
                    String.format("You've exceeded both limits: Budget by %d%% and Energy by %d%%.", 
                    Math.round(costOver), Math.round(kwhOver)));
            } else if (costExceeded) {
                double over = Math.max(0, costPercent - 100.0);
                sendNotification("Budget Exceeded",
                    String.format("You've gone over your budget by %d%%: ₱%.2f of ₱%.2f.", 
                    Math.round(over), totalCost, costLimit));
            } else if (kwhExceeded) {
                double over = Math.max(0, kwhPercent - 100.0);
                sendNotification("Energy Limit Exceeded",
                    String.format("You've exceeded your energy limit by %d%%: %.3f kWh of %.3f kWh.", 
                    Math.round(over), totalKwh, kwhLimit));
            }
        }
        
        if ((!reachedCostSent && costReached) || (!reachedKwhSent && kwhReached)) {
            if (costReached) reachedCostSent = true;
            if (kwhReached) reachedKwhSent = true;
            db.child("threshold").child("reached_notified").setValue(true);
            
            if (costReached && kwhReached) {
                sendNotification("Budget & Energy Reached (100%%)",
                    String.format("You've reached both limits: ₱%.2f and %.3f kWh.", totalCost, totalKwh));
            } else if (costReached) {
                sendNotification("Budget Reached (100%%)",
                    String.format("You've reached your budget: ₱%.2f of ₱%.2f (100%%).", totalCost, costLimit));
            } else if (kwhReached) {
                sendNotification("Energy Limit Reached (100%%)",
                    String.format("You've reached your energy limit: %.3f kWh of %.3f kWh (100%%).", totalKwh, kwhLimit));
            }
        }
        
        if (costNear || kwhNear) {
            Log.d(TAG, "📈 Approaching threshold detected!");
            Log.d(TAG, String.format("📊 Cost: %.2f%% (last notified: %.2f%%)", costPercent, lastNotifiedCost));
            Log.d(TAG, String.format("📊 kWh: %.3f%% (last notified: %.3f%%)", kwhPercent, lastNotifiedKwh));
            
            // Send notification if percentage increased by at least 3% since last notification
            if (costNear && (lastNotifiedCost < 0 || costPercent >= lastNotifiedCost + STEP_PERCENT)) {
                Log.d(TAG, String.format("🔔 SENDING COST APPROACHING NOTIFICATION: %.2f%%", costPercent));
                lastNotifiedCost = costPercent;
                sendNotification("Approaching Budget Limit",
                    String.format("You're approaching your budget limit: %d%% (₱%.2f of ₱%.2f)", 
                    Math.round(costPercent), totalCost, costLimit));
            } else if (costNear) {
                Log.d(TAG, String.format("⏭️ Cost %.2f%% not enough change from %.2f%%", costPercent, lastNotifiedCost));
            }
            
            if (kwhNear && (lastNotifiedKwh < 0 || kwhPercent >= lastNotifiedKwh + STEP_PERCENT)) {
                Log.d(TAG, String.format("🔔 SENDING KWH APPROACHING NOTIFICATION: %.3f%%", kwhPercent));
                lastNotifiedKwh = kwhPercent;
                sendNotification("Approaching Energy Limit",
                    String.format("You're approaching your energy limit: %d%% (%.3f kWh of %.3f kWh)", 
                    Math.round(kwhPercent), totalKwh, kwhLimit));
            } else if (kwhNear) {
                Log.d(TAG, String.format("⏭️ kWh %.3f%% not enough change from %.3f%%", kwhPercent, lastNotifiedKwh));
            }
        } else {
            Log.d(TAG, "✅ No approaching threshold notifications needed");
        }
        Log.d(TAG, "═══════════════════════════════════════════════");
    }

    private void startHourlySummariesMonitoring() {
        Log.d(TAG, "🚀 Starting hourly summaries monitoring");
        
        // Monitor hourly_summaries for any changes (when new data is added)
        hourlySummariesListener = db.child("hourly_summaries").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                Log.d(TAG, "📊 Hourly summaries changed - checking thresholds");
                Log.d(TAG, "📊 Snapshot has " + snapshot.getChildrenCount() + " dates");
                
                // Force a threshold check whenever ANY data changes
                performThresholdCheck();
                
                // Get current threshold settings
                db.child("threshold").addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot thresholdSnapshot) {
                        String kwhText = thresholdSnapshot.child("kwh_value").getValue(String.class);
                        String costText = thresholdSnapshot.child("cost_value").getValue(String.class);
                        
                        Double kwhLimit = null, costLimit = null;
                        if (kwhText != null && !kwhText.isEmpty()) {
                            try { kwhLimit = Double.parseDouble(kwhText); } catch (Exception e) {}
                        }
                        if (costText != null && !costText.isEmpty()) {
                            try { costLimit = Double.parseDouble(costText); } catch (Exception e) {}
                        }
                        
                        if (kwhLimit == null && costLimit == null) {
                            Log.d(TAG, "⚠️ No threshold limits set");
                            return;
                        }
                        
                        final Double finalKwhLimit = kwhLimit;
                        final Double finalCostLimit = costLimit;
                        
                        // Get date range and check thresholds
                        db.child("cost_filter_date").addListenerForSingleValueEvent(new ValueEventListener() {
                            @Override
                            public void onDataChange(DataSnapshot filterSnapshot) {
                                String startDate = filterSnapshot.child("starting_date").getValue(String.class);
                                String endDate = filterSnapshot.child("ending_date").getValue(String.class);
                                
                                if (startDate == null || endDate == null) return;
                                
                                // Calculate totals and check thresholds
                                calculateTotalsAndCheckThresholdsFromSnapshot(snapshot, startDate, endDate, finalKwhLimit, finalCostLimit);
                            }
                            @Override
                            public void onCancelled(DatabaseError error) {}
                        });
                    }
                    @Override
                    public void onCancelled(DatabaseError error) {}
                });
            }
            @Override
            public void onCancelled(DatabaseError error) {
                Log.e(TAG, "❌ Error monitoring hourly summaries: " + error.getMessage());
            }
        });
    }

    private void calculateTotalsAndCheckThresholdsFromSnapshot(DataSnapshot snapshot, String startDate, String endDate, Double kwhLimit, Double costLimit) {
        double totalCost = 0.0;
        double totalKwh = 0.0;
        
        for (DataSnapshot dateSnap : snapshot.getChildren()) {
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
        
        Log.d(TAG, String.format("📊 Current Totals: Cost=%.2f (limit=%.2f), kWh=%.3f (limit=%.3f)", 
            totalCost, costLimit != null ? costLimit : 0.0, 
            totalKwh, kwhLimit != null ? kwhLimit : 0.0));
        
        // Check thresholds
        checkThresholds(totalCost, totalKwh, costLimit, kwhLimit);
    }

    private void startVoltageMonitoring() {
        Log.d(TAG, "🚀 Starting voltage monitoring");
        
        voltageListener = db.child("logs").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                for (DataSnapshot dateSnapshot : snapshot.getChildren()) {
                    String dateKey = dateSnapshot.getKey();
                    for (DataSnapshot entrySnapshot : dateSnapshot.getChildren()) {
                        String timeKey = entrySnapshot.getKey();
                        String key = dateKey + "_" + timeKey;
                        
                        if (lastVoltageKeyNotified != null && key.equals(lastVoltageKeyNotified)) {
                            continue;
                        }
                        
                        Integer f1 = entrySnapshot.child("Fluct1").getValue(Integer.class);
                        Integer f2 = entrySnapshot.child("Fluct2").getValue(Integer.class);
                        Integer f3 = entrySnapshot.child("Fluct3").getValue(Integer.class);
                        
                        boolean a1 = (f1 != null && f1 == 1);
                        boolean a2 = (f2 != null && f2 == 1);
                        boolean a3 = (f3 != null && f3 == 1);
                        
                        if (a1 || a2 || a3) {
                            StringBuilder msg = new StringBuilder("Voltage fluctuation detected in ");
                            if (a1) msg.append("Area 1 ");
                            if (a2) msg.append("Area 2 ");
                            if (a3) msg.append("Area 3 ");
                            
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
                            
                            lastVoltageKeyNotified = key;
                            db.child("notification_settings").child("last_voltage_notified").setValue(key);
                            
                            sendNotification("Voltage Fluctuation", msg.toString());
                        }
                    }
                }
            }
            @Override
            public void onCancelled(DatabaseError error) {}
        });
    }

    private void sendNotification(String title, String message) {
        Log.d(TAG, "🔔 Sending notification: " + title);
        
        // Save to database
        saveAlertToDatabase(title, message);
        
        // Show notification
        Intent intent = new Intent(this, NotificationActivity.class);
        intent.putExtra("alert_title", title);
        intent.putExtra("alert_message", message);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        
        PendingIntent pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(message)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent);

        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify((int) System.currentTimeMillis(), builder.build());
        }
    }

    private void performThresholdCheck() {
        Log.d(TAG, "🔍 Performing threshold check");
        
        // Get threshold settings
        db.child("threshold").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot thresholdSnapshot) {
                String kwhText = thresholdSnapshot.child("kwh_value").getValue(String.class);
                String costText = thresholdSnapshot.child("cost_value").getValue(String.class);
                
                Double kwhLimit = null, costLimit = null;
                if (kwhText != null && !kwhText.isEmpty()) {
                    try { kwhLimit = Double.parseDouble(kwhText); } catch (Exception e) {}
                }
                if (costText != null && !costText.isEmpty()) {
                    try { costLimit = Double.parseDouble(costText); } catch (Exception e) {}
                }
                
                if (kwhLimit == null && costLimit == null) {
                    Log.d(TAG, "⚠️ No threshold limits set");
                    return;
                }
                
                Log.d(TAG, "🔍 Threshold check - Limits: Cost=" + costLimit + ", kWh=" + kwhLimit);
                
                final Double finalKwhLimit = kwhLimit;
                final Double finalCostLimit = costLimit;
                
                // Get date range
                db.child("cost_filter_date").addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot filterSnapshot) {
                        String startDate = filterSnapshot.child("starting_date").getValue(String.class);
                        String endDate = filterSnapshot.child("ending_date").getValue(String.class);
                        
                        if (startDate == null || endDate == null) {
                            Log.d(TAG, "⚠️ No date range set");
                            return;
                        }
                        
                        Log.d(TAG, "🔍 Date range: " + startDate + " to " + endDate);
                        
                        // Get hourly summaries and calculate
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
                                
                                Log.d(TAG, String.format("📊 Current Totals: Cost=%.2f (limit=%.2f), kWh=%.3f (limit=%.3f)", 
                                    totalCost, finalCostLimit != null ? finalCostLimit : 0.0, 
                                    totalKwh, finalKwhLimit != null ? finalKwhLimit : 0.0));
                                
                                // Check thresholds
                                checkThresholds(totalCost, totalKwh, finalCostLimit, finalKwhLimit);
                            }
                            @Override
                            public void onCancelled(DatabaseError error) {
                                Log.e(TAG, "❌ Error getting hourly summaries: " + error.getMessage());
                            }
                        });
                    }
                    @Override
                    public void onCancelled(DatabaseError error) {
                        Log.e(TAG, "❌ Error getting date range: " + error.getMessage());
                    }
                });
            }
            @Override
            public void onCancelled(DatabaseError error) {
                Log.e(TAG, "❌ Error getting thresholds: " + error.getMessage());
            }
        });
    }

    private void startPeriodicChecks() {
        Log.d(TAG, "⏰ Starting periodic checks every 30 seconds");
        
        periodicHandler = new android.os.Handler();
        periodicRunnable = new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "⏰ Periodic threshold check");
                performThresholdCheck();
                
                // Schedule next check in 30 seconds
                if (periodicHandler != null) {
                    periodicHandler.postDelayed(this, 30000);
                }
            }
        };
        
        // Start the first check after 30 seconds
        periodicHandler.postDelayed(periodicRunnable, 30000);
    }

    private void resetStepTracking() {
        Log.d(TAG, "🔄 Resetting step tracking");
        lastCostStepSent = -1;
        lastKwhStepSent = -1;
        finalThresholdAlertSent = false;
        reachedCostSent = false;
        reachedKwhSent = false;
        lastNotifiedCost = -1.0;
        lastNotifiedKwh = -1.0;
    }

    private void manualThresholdCheck() {
        Log.d(TAG, "🔍 Performing manual threshold check");
        
        // Get threshold settings
        db.child("threshold").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot thresholdSnapshot) {
                String kwhText = thresholdSnapshot.child("kwh_value").getValue(String.class);
                String costText = thresholdSnapshot.child("cost_value").getValue(String.class);
                
                Double kwhLimit = null, costLimit = null;
                if (kwhText != null && !kwhText.isEmpty()) {
                    try { kwhLimit = Double.parseDouble(kwhText); } catch (Exception e) {}
                }
                if (costText != null && !costText.isEmpty()) {
                    try { costLimit = Double.parseDouble(costText); } catch (Exception e) {}
                }
                
                if (kwhLimit == null && costLimit == null) {
                    Log.d(TAG, "⚠️ No threshold limits set for manual check");
                    return;
                }
                
                Log.d(TAG, "🔍 Manual check - Limits: Cost=" + costLimit + ", kWh=" + kwhLimit);
                
                final Double finalKwhLimit = kwhLimit;
                final Double finalCostLimit = costLimit;
                
                // Get date range and hourly summaries
                db.child("cost_filter_date").addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot filterSnapshot) {
                        String startDate = filterSnapshot.child("starting_date").getValue(String.class);
                        String endDate = filterSnapshot.child("ending_date").getValue(String.class);
                        
                        if (startDate == null || endDate == null) {
                            Log.d(TAG, "⚠️ No date range set for manual check");
                            return;
                        }
                        
                        Log.d(TAG, "🔍 Manual check - Date range: " + startDate + " to " + endDate);
                        
                        // Get hourly summaries
                        db.child("hourly_summaries").addListenerForSingleValueEvent(new ValueEventListener() {
                            @Override
                            public void onDataChange(DataSnapshot hourlySnapshot) {
                                calculateTotalsAndCheckThresholdsFromSnapshot(hourlySnapshot, startDate, endDate, finalKwhLimit, finalCostLimit);
                            }
                            @Override
                            public void onCancelled(DatabaseError error) {
                                Log.e(TAG, "❌ Error getting hourly summaries for manual check: " + error.getMessage());
                            }
                        });
                    }
                    @Override
                    public void onCancelled(DatabaseError error) {
                        Log.e(TAG, "❌ Error getting date range for manual check: " + error.getMessage());
                    }
                });
            }
            @Override
            public void onCancelled(DatabaseError error) {
                Log.e(TAG, "❌ Error getting thresholds for manual check: " + error.getMessage());
            }
        });
    }

    private void saveAlertToDatabase(String title, String message) {
        DatabaseReference alertsRef = db.child("alerts");
        Map<String, Object> data = new HashMap<>();
        data.put("type", "threshold");
        data.put("title", title);
        data.put("message", message);
        data.put("time", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date()));
        data.put("read", false);
        data.put("delete", false);
        alertsRef.push().setValue(data);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "🛑 Service destroyed");
        
        // Stop periodic checks
        if (periodicHandler != null && periodicRunnable != null) {
            periodicHandler.removeCallbacks(periodicRunnable);
            periodicHandler = null;
        }
        
        // Remove listeners
        if (thresholdListener != null) {
            db.child("threshold").removeEventListener(thresholdListener);
        }
        if (voltageListener != null) {
            db.child("logs").removeEventListener(voltageListener);
        }
        if (hourlySummariesListener != null) {
            db.child("hourly_summaries").removeEventListener(hourlySummariesListener);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
