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

        DatabaseReference settingsRef = db.child("notification_settings");
        settingsRef.child("voltage_enabled").setValue(switchVoltage != null && switchVoltage.isChecked());
        settingsRef.child("system_updates_enabled").setValue(switchSystemUpdates != null && switchSystemUpdates.isChecked());
        settingsRef.child("push_enabled").setValue(switchPush != null && switchPush.isChecked())
                .addOnSuccessListener(aVoid -> Toast.makeText(AlertActivity.this, "Settings saved", Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e -> Toast.makeText(AlertActivity.this, "Failed to save", Toast.LENGTH_SHORT).show());

        // Send toggle alerts for voltage and system updates
        if (switchVoltage != null && switchVoltage.isChecked()) {
            notifyNow("Voltage Fluctuation", "You will receive voltage fluctuation messages.");
            startVoltageMonitoring();
        } else {
            notifyNow("Voltage Fluctuation", "You won't receive voltage fluctuation messages.");
        }
        
        if (switchSystemUpdates != null && switchSystemUpdates.isChecked()) {
            notifyNow("System Updates", "You will receive updates and changes notifications.");
        } else {
            notifyNow("System Updates", "You won't receive system updates notifications.");
        }
        
        // Send toggle alert for push notifications
        if (switchPush != null && switchPush.isChecked()) {
            notifyNow("Push Notifications", "You will see push notifications.");
            startThresholdMonitoring();
        } else {
            notifyNow("Push Notifications", "You can directly see notifications in the app.");
        }
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
        // Always send to database
        logAlertToDatabase(title, message);
        
        // Send push notification if enabled and permission granted
        boolean canPush = (switchPush != null && switchPush.isChecked()) && hasNotificationPermission();
        if (canPush) {
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
            }
        }
    }

    private void logAlertToDatabase(String title, String message) {
        String type = inferAlertType(title);
        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
        DatabaseReference alertsRef = db.child("alerts");
        Map<String, Object> data = new HashMap<>();
        data.put("type", type);
        data.put("title", title);
        data.put("message", message);
        data.put("time", timestamp);
        data.put("read", false);
        data.put("delete", false);
        alertsRef.push().setValue(data);
    }

    private String inferAlertType(String title) {
        String t = title == null ? "" : title.toLowerCase(Locale.getDefault());
        if (t.contains("voltage")) return "fluctuation";
        if (t.contains("system")) return "systemUpdates";
        if (t.contains("kwh")) return "power";
        if (t.contains("cost")) return "budget";
        return "general";
    }

    private void startVoltageMonitoring() {
        // Remove previous listener to avoid stacking
        DatabaseReference logsRef = db.child("logs");
        if (voltageListener != null) {
            logsRef.removeEventListener(voltageListener);
        }
        voltageListener = new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                // Watch only the latest log to prevent bulk notifications
                DataSnapshot latestDate = null;
                for (DataSnapshot d : snapshot.getChildren()) latestDate = d; // last iterated = latest by key
                if (latestDate == null) return;
                DataSnapshot latestEntry = null;
                for (DataSnapshot e : latestDate.getChildren()) latestEntry = e;
                if (latestEntry == null) return;

                String key = latestEntry.getKey();
                if (key != null && key.equals(lastVoltageKeyNotified)) return; // already notified for this entry

                int f1 = toInt(latestEntry.child("Fluct1").getValue());
                int f2 = toInt(latestEntry.child("Fluct2").getValue());
                int f3 = toInt(latestEntry.child("Fluct3").getValue());
                boolean a1 = (f1 == 1);
                boolean a2 = (f2 == 1);
                boolean a3 = (f3 == 1);
                if (a1 || a2 || a3) {
                    StringBuilder msg = new StringBuilder("Voltage fluctuation detected in ");
                    if (a1) msg.append("Area 1 ");
                    if (a2) msg.append("Area 2 ");
                    if (a3) msg.append("Area 3 ");
                    lastVoltageKeyNotified = key;
                    notifyNow("Voltage Fluctuation", msg.toString().trim());
                }
            }
            @Override public void onCancelled(DatabaseError error) { }
        };
        logsRef.addValueEventListener(voltageListener);
    }

    private int toInt(Object v) {
        if (v == null) return 0;
        if (v instanceof Number) return ((Number) v).intValue();
        try { return Integer.parseInt(String.valueOf(v)); } catch (Exception e) { return 0; }
    }

    private void startThresholdMonitoring() {
        // Listen to thresholds and date filter; compare sums within date range
        DatabaseReference thresholdRef = db.child("threshold");
        if (thresholdRefListener != null) thresholdRef.removeEventListener(thresholdRefListener);
        thresholdRefListener = new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot tSnap) {
                Double kwhLimit = parseDouble(tSnap.child("kwh_value").getValue());
                Double costLimit = parseDouble(tSnap.child("cost_value").getValue());
                Boolean finalNotified = (Boolean) tSnap.child("final_notified").getValue(Boolean.class);
                finalThresholdAlertSent = finalNotified != null && finalNotified;
                if (kwhLimit == null && costLimit == null) return;

                // Fetch date range
                DatabaseReference filterRef = db.child("cost_filter_date");
                if (costFilterListener != null) filterRef.removeEventListener(costFilterListener);
                costFilterListener = new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot filterSnap) {
                        Object sObj = filterSnap.child("starting_date").getValue();
                        Object eObj = filterSnap.child("ending_date").getValue();
                        String startStr = sObj == null ? null : sObj.toString();
                        String endStr = eObj == null ? null : eObj.toString();
                        Date startDate = parseDateLenient(startStr);
                        Date endDate = parseDateLenient(endStr);
                        if (startDate == null || endDate == null) return;

                        // Read a fresh snapshot once to avoid double-counting from stacked listeners
                        db.child("hourly_summaries").addListenerForSingleValueEvent(new ValueEventListener() {
                            @Override
                            public void onDataChange(DataSnapshot dataSnapshot) {
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

                                long now = System.currentTimeMillis();
                                boolean hasKwhLimit = (kwhLimit != null && kwhLimit > 0);
                                boolean hasCostLimit = (costLimit != null && costLimit > 0);
                                double kwhPercent = hasKwhLimit ? (totalKwh / kwhLimit) * 100.0 : 0.0;
                                double costPercent = hasCostLimit ? (totalCost / costLimit) * 100.0 : 0.0;

                                boolean kwhNear = hasKwhLimit && kwhPercent >= START_PERCENT && kwhPercent < 100.0;
                                boolean costNear = hasCostLimit && costPercent >= START_PERCENT && costPercent < 100.0;
                                boolean kwhReached = hasKwhLimit && kwhPercent >= 100.0;
                                boolean costReached = hasCostLimit && costPercent >= 100.0;
                                boolean kwhExceeded = hasKwhLimit && kwhPercent >= 103.0;
                                boolean costExceeded = hasCostLimit && costPercent >= 103.0;

                                // Final one-time notification when exceeded
                                if (!finalThresholdAlertSent && (kwhExceeded || costExceeded)) {
                                    finalThresholdAlertSent = true;
                                    String finalMsg = String.format(Locale.getDefault(), "Exceeded: kWh %.3f / %.3f, Cost ₱%.2f / ₱%.2f",
                                            totalKwh,
                                            kwhLimit != null ? kwhLimit : 0.0,
                                            totalCost,
                                            costLimit != null ? costLimit : 0.0);
                                    notifyNow("Threshold Exceeded", finalMsg);
                                    // persist so we don't re-notify if restarted
                                    db.child("threshold").child("final_notified").setValue(true);
                                    return; // stop further near alerts after final
                                }

                                // One-time 100% reached alert (before 103%)
                                if (!finalThresholdAlertSent && (kwhReached || costReached)) {
                                    boolean send = false;
                                    if (kwhReached && !reachedKwhSent) { reachedKwhSent = true; send = true; }
                                    if (costReached && !reachedCostSent) { reachedCostSent = true; send = true; }
                                    if (send) {
                                        String reachedMsg = String.format(Locale.getDefault(), "Reached: kWh %.3f / %.3f, Cost ₱%.2f / ₱%.2f",
                                                totalKwh,
                                                hasKwhLimit ? kwhLimit : 0.0,
                                                totalCost,
                                                hasCostLimit ? costLimit : 0.0);
                                        notifyNow("Threshold Reached", reachedMsg);
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
                                        msg.append(String.format(Locale.getDefault(), "kWh %.3f / %.3f (%.0f%%)", totalKwh, hasKwhLimit ? kwhLimit : 0.0, kwhPercent));
                                        msg.append(", ");
                                        msg.append(String.format(Locale.getDefault(), "Cost ₱%.2f / ₱%.2f (%.0f%%)", totalCost, hasCostLimit ? costLimit : 0.0, costPercent));
                                        notifyNow("Threshold Alert", msg.toString());
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