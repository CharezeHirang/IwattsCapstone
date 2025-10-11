package com.example.sampleiwatts;


import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextWatcher;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;


import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Calendar;
import java.util.Locale;

public class DashboardActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private TextView tvTotalCost, tvElectricityRate, tvBatteryLife,tvTotalConsumption, area1_details, area2_details, area3_details, activated;
    private TextView tvArea1Kwh, tvArea2Kwh, tvArea3Kwh,  tvArea1Percentage, tvArea2Percentage, tvArea3Percentage, tvPeakTime, tvPeakValue;
    private TextView tvPercentageChange,  area1_icon, area2_icon, area3_icon, tvPercent1, tvPercent2, tvPercent3;

    private ImageView ivBatteryImage,tvTrendIcon;
    private LineChart lineChart1, lineChart2, lineChart3;

    private DatabaseReference db;
    private EditText etArea1, etArea2, etArea3;
    LinearLayout popArea1, popArea2, popArea3, percentageChangeContainer;
    CardView area1_card, area2_card, area3_card;
    ImageView ic_close, close2, close3;

    private long lastLogsUpdateAtMs = 0L;
    private long lastDataUpdateAtMs = 0L; // Track successful data fetching
    private String lastActivationSeenKey = null; // latest inner push key we've seen for activation heartbeats
    private Handler connectionHandler = new Handler();
    private static final long TIMEOUT_MS = 20000;
    private final long logsStaleAfterMs = 20_000L; // 2 minutes with no updates => inactive
    private final android.os.Handler activationHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    
    // Track consumption alert thresholds to prevent spam
    private boolean consumptionAlert100Sent = false;
    private boolean consumptionAlert150Sent = false;
    private boolean consumptionAlert200Sent = false;
    private final Runnable activationChecker = new Runnable() {
        @Override public void run() {
            long now = System.currentTimeMillis();
            // Consider connected if either logs are recent OR data has been fetched recently
            boolean active = (now - lastLogsUpdateAtMs) <= logsStaleAfterMs || (now - lastDataUpdateAtMs) <= logsStaleAfterMs;
            updateActivationText(active);
            activationHandler.postDelayed(this, 5_000L); // check every 5 seconds instead of 30
        }
    };





    private boolean isArea1Editable = false;
    private boolean isArea2Editable = false;
    private boolean isArea3Editable = false;

    // Store original names before editing
    private String originalArea1Name = "";
    private String originalArea2Name = "";
    private String originalArea3Name = "";

    // Method to get appropriate icon based on area name
    private String getIconForAreaName(String areaName) {
        if (areaName == null || areaName.trim().isEmpty()) {
            return "🏠"; // Default house icon
        }

        String name = areaName.toLowerCase().trim();

        // Living room variations
        if (name.contains("living") || name.contains("lounge") || name.contains("family")) {
            return "🛋️";
        }

        // Dining area variations
        if (name.contains("dining") || name.contains("eat")) {
            return "🍴";
        }

        // Kitchen variations
        if (name.contains("kitchen") || name.contains("cook")) {
            return "🍳";
        }

        // Bedroom variations
        if (name.contains("bedroom") || name.contains("bed") || name.contains("sleep")) {
            return "🛏️";
        }

        // Bathroom variations
        if (name.contains("bathroom") || name.contains("toilet") || name.contains("comfort") ||
                name.contains("cr") || name.contains("restroom") || name.contains("wash")) {
            return "🚿";
        }

        // Hallway/Corridor variations
        if (name.contains("hallway") || name.contains("corridor") || name.contains("hall") ||
                name.contains("passage") || name.contains("walkway")) {
            return "🏠";
        }

        // Study/Office variations
        if (name.contains("study") || name.contains("office") || name.contains("work") ||
                name.contains("desk") || name.contains("library")) {
            return "📚";
        }

        // Garage variations
        if (name.contains("garage") || name.contains("carport") || name.contains("parking")) {
            return "🚗";
        }

        // Garden/Yard variations
        if (name.contains("garden") || name.contains("yard") || name.contains("outdoor") ||
                name.contains("backyard") || name.contains("front yard")) {
            return "🌿";
        }

        // Laundry area variations
        if (name.contains("laundry") || name.contains("wash") || name.contains("drying")) {
            return "🧺";
        }

        // Balcony/Terrace variations
        if (name.contains("balcony") || name.contains("terrace") || name.contains("patio")) {
            return "\uD83C\uDFE1";
        }

        // Porch/Entrance variations
        if (name.contains("porch") || name.contains("entrance") || name.contains("entrance") ||
                name.contains("foyer") || name.contains("lobby")) {
            return "🏠";
        }

        // Default fallback
        return "🏠";
    }

    private NavigationDrawerHelper drawerHelper;

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        // Enable safe swipe navigation (root content view)
        try {
            View root = findViewById(android.R.id.content);
            com.example.sampleiwatts.managers.SwipeNavigationManager.enableSwipeNavigation(this, root);
            View scroll = findViewById(R.id.scrollView);
            if (scroll != null) {
                com.example.sampleiwatts.managers.SwipeNavigationManager.enableSwipeNavigationForScrollView(this, scroll);
            }
            View main = findViewById(R.id.main);
            if (main != null) {
                com.example.sampleiwatts.managers.SwipeNavigationManager.enableSwipeNavigation(this, main);
            }
        } catch (Exception ignored) { }

        // Setup navigation drawer
        drawerHelper = new NavigationDrawerHelper(this);
        drawerHelper.setupNavigationDrawer();

        // Handle intent extras for navigation tracking
        Intent intent = getIntent();
        if (intent.getBooleanExtra("from_drawer", false)) {
            String sourceActivity = intent.getStringExtra("source_activity");
            drawerHelper.setReturnToActivity(sourceActivity);
        }

        tvPercent1 = findViewById(R.id.tvPercent1);
        tvPercent2 = findViewById(R.id.tvPercent2);
        tvPercent3 = findViewById(R.id.tvPercent3);
        area1_icon = findViewById(R.id.area1_icon);
        area2_icon = findViewById(R.id.area2_icon);
        area3_icon = findViewById(R.id.area3_icon);

        tvPercentageChange = findViewById(R.id.tvPercentageChange);
        tvTrendIcon = findViewById(R.id.tvTrendIcon);
        activated = findViewById(R.id.activated);

        area1_details = findViewById(R.id.area1_details);
        area2_details = findViewById(R.id.area2_details);
        area3_details = findViewById(R.id.area3_details);

        popArea1 = findViewById(R.id.popArea1);
        area1_card = findViewById(R.id.area1_card);
        ic_close = findViewById(R.id.close1);

        area1_card.setOnClickListener(v -> {
            // Hide keyboard when clicking on area card
            hideKeyboard(v);
            if (popArea1.getVisibility() == View.GONE) {
                popArea1.setVisibility(View.VISIBLE);
            } else {
                popArea1.setVisibility(View.GONE);
            }
        });

        ic_close.setOnClickListener(v -> {
            hideKeyboard(v);
            popArea1.setVisibility(View.GONE);
        });

        popArea2 = findViewById(R.id.popArea2);
        area2_card = findViewById(R.id.area2_card);
        close2 = findViewById(R.id.close2);

        area2_card.setOnClickListener(v -> {
            // Hide keyboard when clicking on area card
            hideKeyboard(v);
            if (popArea2.getVisibility() == View.GONE) {
                popArea2.setVisibility(View.VISIBLE);
            } else {
                popArea2.setVisibility(View.GONE);
            }
        });

        close2.setOnClickListener(v -> {
            hideKeyboard(v);
            popArea2.setVisibility(View.GONE);
        });

        popArea3 = findViewById(R.id.popArea3);
        area3_card = findViewById(R.id.area3_card);
        close3 = findViewById(R.id.close3);

        area3_card.setOnClickListener(v -> {
            // Hide keyboard when clicking on area card
            hideKeyboard(v);
            if (popArea3.getVisibility() == View.GONE) {
                popArea3.setVisibility(View.VISIBLE);
            } else {
                popArea3.setVisibility(View.GONE);
            }
        });

        close3.setOnClickListener(v -> {
            hideKeyboard(v);
            popArea3.setVisibility(View.GONE);
        });

        // Add global touch listener to hide keyboard when tapping outside EditTexts
        View rootView = findViewById(android.R.id.content);
        rootView.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                View currentFocus = getCurrentFocus();
                if (currentFocus instanceof EditText) {
                    Rect outRect = new Rect();
                    currentFocus.getGlobalVisibleRect(outRect);
                    if (!outRect.contains((int)event.getRawX(), (int)event.getRawY())) {
                        currentFocus.clearFocus();
                        hideKeyboard(currentFocus);
                    }
                }
            }
            return false;
        });

        percentageChangeContainer = findViewById(R.id.percentageChangeContainer);
        lineChart1 = findViewById(R.id.area1_chart);
        lineChart2 = findViewById(R.id.area2_chart);
        lineChart3 = findViewById(R.id.area3_chart);
        db = FirebaseDatabase.getInstance().getReference();
        tvArea1Kwh = findViewById(R.id.tvArea1Kwh);
        tvArea2Kwh = findViewById(R.id.tvArea2Kwh);
        tvArea3Kwh = findViewById(R.id.tvArea3Kwh);

        tvArea1Percentage = findViewById(R.id.tvArea1Percentage);
        tvArea2Percentage = findViewById(R.id.tvArea2Percentage);
        tvArea3Percentage = findViewById(R.id.tvArea3Percentage);
        tvPeakTime = findViewById(R.id.tvPeakTime);
        tvPeakValue =  findViewById(R.id.tvPeakValue);
        tvPercentageChange = findViewById(R.id.tvPercentageChange);
        tvTrendIcon = findViewById(R.id.tvTrendIcon);

        etArea1 = findViewById(R.id.etArea1);
        etArea2 = findViewById(R.id.etArea2);
        etArea3 = findViewById(R.id.etArea3);

// Make EditText fields non-editable by default
        etArea1.setFocusable(false);
        etArea1.setClickable(false);
        etArea1.setLongClickable(false);
        etArea1.setCursorVisible(false);

        etArea2.setFocusable(false);
        etArea2.setClickable(false);
        etArea2.setLongClickable(false);
        etArea2.setCursorVisible(false);

        etArea3.setFocusable(false);
        etArea3.setClickable(false);
        etArea3.setLongClickable(false);
        etArea3.setCursorVisible(false);

// Add text change listeners to update icons dynamically
        etArea1.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                updateAreaIcon(1, s.toString());
                updateAreaDetailsHeader(1, s.toString());
            }
        });


        etArea2.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                updateAreaIcon(2, s.toString());
                updateAreaDetailsHeader(2, s.toString());
            }
        });


        etArea3.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                updateAreaIcon(3, s.toString());
                updateAreaDetailsHeader(3, s.toString());
            }
        });
        etArea1.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_NEXT) {
                etArea1.clearFocus(); // remove cursor first
                // Use Handler to ensure keyboard hiding happens after focus change
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    hideKeyboard(v);
                    hideKeyboardFromCurrentFocus(); // Additional fallback
                }, 100);
                return true;
            }
            return false;
        });

        etArea2.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_NEXT) {
                etArea2.clearFocus();
                // Use Handler to ensure keyboard hiding happens after focus change
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    hideKeyboard(v);
                    hideKeyboardFromCurrentFocus(); // Additional fallback
                }, 100);
                return true;
            }
            return false;
        });

        etArea3.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_NEXT) {
                etArea3.clearFocus();
                // Use Handler to ensure keyboard hiding happens after focus change
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    hideKeyboard(v);
                    hideKeyboardFromCurrentFocus(); // Additional fallback
                }, 100);
                return true;
            }
            return false;
        });

        // Add focus change listeners to handle keyboard hiding
        etArea1.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                hideKeyboard(v);
            }
        });

        etArea2.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                hideKeyboard(v);
            }
        });

        etArea3.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                hideKeyboard(v);
            }
        });

        // Add touch listeners to handle edit icon clicks
        setupEditTextTouchListener(etArea1, 1);
        setupEditTextTouchListener(etArea2, 2);
        setupEditTextTouchListener(etArea3, 3);
        tvTotalConsumption = findViewById(R.id.tvTotalConsumption);
        ivBatteryImage = findViewById(R.id.ivBatteryImage);
        tvBatteryLife = findViewById(R.id.tvBatteryLife);
        tvElectricityRate = findViewById(R.id.tvTotalKwh);
        tvTotalCost = findViewById(R.id.tvTotalCost);
        fetchTotalCost();
        fetchElectricityRate();
        fetchBatteryLife();
        startActivationWatcher();
        fetchTotalKwh();
        fetchAreaNames();
        fetchAreaKwh();
        fetchPeakWatts();
        fetchArea1();
        fetchArea2();
        fetchArea3();
        fetchUsageTrend();
        LinearLayout buttonLayout = findViewById(R.id.button);

        ButtonNavigator.setupButtons(this, buttonLayout);



        Log.d(TAG, "MainActivity created");


    }
    @Override
    protected void onResume() {
        super.onResume();

        // Kick off activation checker loop
        activationHandler.removeCallbacks(activationChecker);
        activationHandler.post(activationChecker);
    }
    private void hideKeyboard(View view) {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null && view != null) {
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    // Alternative method to hide keyboard from current focus
    private void hideKeyboardFromCurrentFocus() {
        View currentFocus = getCurrentFocus();
        if (currentFocus != null) {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(currentFocus.getWindowToken(), 0);
            }
        }
    }

    // Force hide keyboard using activity context
    private void forceHideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.toggleSoftInput(InputMethodManager.SHOW_IMPLICIT, 0);
        }
    }

    private void fetchTotalCost() {
        DatabaseReference costFilterDateRef = db.child("cost_filter_date");

        costFilterDateRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                String startingDateString = dataSnapshot.child("starting_date").getValue(String.class);
                String endingDateString = dataSnapshot.child("ending_date").getValue(String.class);

                // Log the raw data fetched
                Log.d("CostEstimation", "Raw Starting Date: " + startingDateString);
                Log.d("CostEstimation", "Raw Ending Date: " + endingDateString);

                SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                try {
                    Date startingDate = dateFormatter.parse(startingDateString);
                    Date endingDate = dateFormatter.parse(endingDateString);

                    Log.d("CostEstimation", "Parsed Starting Date: " + startingDate);
                    Log.d("CostEstimation", "Parsed Ending Date: " + endingDate);

                    db.child("hourly_summaries").orderByKey().addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(DataSnapshot dataSnapshot) {
                            double cumulativeCost = 0;

                            // Loop through each date in the hourly summaries
                            for (DataSnapshot dateSnapshot : dataSnapshot.getChildren()) {
                                String dateKey = dateSnapshot.getKey();
                                Date currentDate = null;
                                try {
                                    currentDate = dateFormatter.parse(dateKey);
                                } catch (ParseException e) {
                                    Log.e("CostEstimation", "Error parsing date: " + dateKey);
                                }

                                Log.d("CostEstimation", "Processing Date: " + dateKey);
                                Log.d("CostEstimation", "Current Date: " + currentDate);

                                // Check if the current date is within the range of starting and ending dates
                                if ((currentDate.equals(startingDate) || currentDate.after(startingDate)) &&
                                        (currentDate.equals(endingDate) || currentDate.before(endingDate))) {

                                    // Loop through hourly data for the current date
                                    for (DataSnapshot hourSnapshot : dateSnapshot.getChildren()) {
                                        Double hourlyCost = hourSnapshot.child("total_cost").getValue(Double.class);

                                        if (hourlyCost != null) {
                                            Log.d("CostEstimation", "Hourly Cost for " + dateKey + ": ₱" + hourlyCost);
                                            cumulativeCost += hourlyCost;
                                        } else {
                                            Log.d("CostEstimation", "No hourly cost for " + dateKey);
                                        }
                                    }
                                }
                            }

                            // Format the total cost
                            String formattedCost = String.format("%.2f", cumulativeCost);
                            tvTotalCost.setText("₱ " + formattedCost);

                            // Update data fetch timestamp for connection status
                            lastDataUpdateAtMs = System.currentTimeMillis();

                            // Immediately update connection status to Connected
                            updateActivationText(true);
                        }

                        @Override
                        public void onCancelled(DatabaseError databaseError) {
                            Log.e("FirebaseError", "Error fetching hourly summaries: " + databaseError.getMessage());
                        }
                    });
                } catch (ParseException e) {
                    e.printStackTrace();
                    Log.e("CostEstimation", "Error parsing dates: " + e.getMessage());
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                Log.e("FirebaseError", "Error fetching cost filter data: " + databaseError.getMessage());
            }
        });


    }
    private void fetchElectricityRate() {
        DatabaseReference electricityRateRef = db.child("system_settings").child("electricity_rate_per_kwh");
        electricityRateRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                Object value = dataSnapshot.getValue();

                if (value instanceof Number) {
                    Double electricityRatePerKwh = ((Number) value).doubleValue();

                    String formattedRate = String.format("%.2f", electricityRatePerKwh);

                    // Update the UI
                    tvElectricityRate.setText("₱ " + formattedRate + " / kwh");
                } else {
                    tvElectricityRate.setText("Electricity rate not available");
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                // Handle database error
                Toast.makeText(DashboardActivity.this, "Error fetching electricity rate", Toast.LENGTH_SHORT).show();
            }
        });
    }
    private void fetchBatteryLife() {
        DatabaseReference logsRef = db.child("logs");

        // Optimize: Only load last 50 date buckets instead of ALL data
        logsRef.orderByKey().limitToLast(50).addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                int batteryPercentage = 0;
                boolean isCharging = false;

                // Keep original logic: collect all entries with battery data and their scores
                java.util.List<java.util.Map<String, Object>> batteryEntries = new java.util.ArrayList<>();

                // REMOVED: Excessive logging here - only log summary

                for (DataSnapshot dateSnapshot : dataSnapshot.getChildren()) {
                    String dateKey = dateSnapshot.getKey();

                    for (DataSnapshot logSnapshot : dateSnapshot.getChildren()) {
                        String pushKey = logSnapshot.getKey();
                        if (pushKey == null) continue;

                        // Check if this entry has battery data
                        Object vbatPercent = logSnapshot.child("Vbat_percent").getValue();
                        Object charging = logSnapshot.child("Charging").getValue();

                        if (vbatPercent != null || charging != null) {
                            // This entry has battery data, calculate its score
                            Long ts = extractTimestamp(logSnapshot);
                            long score = Long.MIN_VALUE;

                            if (ts != null) {
                                score = ts;
                            } else {
                                // Use date bucket score as fallback
                                score = computeDateBucketScore(dateKey);
                            }

                            // REMOVED: Individual entry logging to fix performance

                            java.util.Map<String, Object> entry = new java.util.HashMap<>();
                            entry.put("snapshot", logSnapshot);
                            entry.put("dateKey", dateKey);
                            entry.put("pushKey", pushKey);
                            entry.put("score", score);
                            entry.put("hasTimestamp", ts != null);
                            batteryEntries.add(entry);
                        }
                    }
                }

                Log.d("BatteryDebug", "Found " + batteryEntries.size() + " entries with battery data");

                // Sort by score (highest first) and select the best entry - ORIGINAL LOGIC
                String selectedDateKey = null;
                String selectedPushKey = null;
                DataSnapshot selectedSnapshot = null;
                long bestTimestamp = Long.MIN_VALUE;
                boolean usedTimestamp = false;

                if (!batteryEntries.isEmpty()) {
                    batteryEntries.sort((a, b) -> {
                        long scoreA = (Long) a.get("score");
                        long scoreB = (Long) b.get("score");
                        if (scoreA != scoreB) {
                            return Long.compare(scoreB, scoreA); // Higher score first
                        }
                        // Tie-breaker: prefer entries with timestamps
                        boolean hasTsA = (Boolean) a.get("hasTimestamp");
                        boolean hasTsB = (Boolean) b.get("hasTimestamp");
                        if (hasTsA != hasTsB) {
                            return hasTsA ? -1 : 1;
                        }
                        // Final tie-breaker: push key comparison
                        String keyA = (String) a.get("pushKey");
                        String keyB = (String) b.get("pushKey");
                        return keyB.compareTo(keyA);
                    });

                    java.util.Map<String, Object> bestEntry = batteryEntries.get(0);
                    selectedSnapshot = (DataSnapshot) bestEntry.get("snapshot");
                    selectedDateKey = (String) bestEntry.get("dateKey");
                    selectedPushKey = (String) bestEntry.get("pushKey");
                    bestTimestamp = (Long) bestEntry.get("score");
                    usedTimestamp = (Boolean) bestEntry.get("hasTimestamp");

                    Log.d("BatteryDebug", "Selected best battery entry: " + selectedPushKey + " in " + selectedDateKey +
                            " with score=" + bestTimestamp + ", hasTimestamp=" + usedTimestamp);
                }

                if (selectedSnapshot != null) {
                    Object vbatPercentObj = selectedSnapshot.child("Vbat_percent").getValue();
                    Object chargingObj = selectedSnapshot.child("Charging").getValue();

                    if (vbatPercentObj instanceof Number) {
                        batteryPercentage = ((Number) vbatPercentObj).intValue();
                    } else if (vbatPercentObj instanceof String) {
                        try {
                            batteryPercentage = Integer.parseInt((String) vbatPercentObj);
                        } catch (NumberFormatException ignored) { }
                    }

                    if (chargingObj instanceof Boolean) {
                        isCharging = (Boolean) chargingObj;
                    } else if (chargingObj instanceof String) {
                        isCharging = Boolean.parseBoolean((String) chargingObj);
                    }

                    Log.d("BatteryLifeSelected", "date=" + selectedDateKey + ", key=" + selectedPushKey +
                            ", pct=" + batteryPercentage + ", charging=" + isCharging +
                            ", usedTimestamp=" + usedTimestamp + (usedTimestamp ? (", ts=" + bestTimestamp) : ""));
                }

                // ORIGINAL DISPLAY LOGIC - unchanged
                if (selectedSnapshot != null) {
                    String displayText = isCharging ? "Charging" : (batteryPercentage + "%");

                    if (isCharging) {
                        tvBatteryLife.setText(displayText);
                        tvBatteryLife.setTextSize(17); // Set text size to 17sp for "Charging"
                        ivBatteryImage.setImageResource(R.drawable.ic_battery10);
                    } else if (batteryPercentage >= 95) {
                        tvBatteryLife.setText(displayText);
                        ivBatteryImage.setImageResource(R.drawable.ic_battery1);
                    } else if (batteryPercentage >= 70) {
                        tvBatteryLife.setText(displayText);
                        ivBatteryImage.setImageResource(R.drawable.ic_battery2);
                    } else if (batteryPercentage >= 55) {
                        tvBatteryLife.setText(displayText);
                        ivBatteryImage.setImageResource(R.drawable.ic_battery3);
                    } else if (batteryPercentage >= 40) {
                        tvBatteryLife.setText(displayText);
                        ivBatteryImage.setImageResource(R.drawable.ic_battery4);
                    } else if (batteryPercentage >= 25) {
                        tvBatteryLife.setText(displayText);
                        ivBatteryImage.setImageResource(R.drawable.ic_battery5);
                    } else if (batteryPercentage >= 10) {
                        tvBatteryLife.setText(displayText);
                        ivBatteryImage.setImageResource(R.drawable.ic_battery6);
                    } else if (batteryPercentage >= 5) {
                        tvBatteryLife.setText(displayText);
                        ivBatteryImage.setImageResource(R.drawable.ic_battery7);
                    } else {
                        tvBatteryLife.setText(displayText);
                        ivBatteryImage.setImageResource(R.drawable.ic_battery8);
                    }
                } else {
                    tvBatteryLife.setText("Battery Life not available");
                    ivBatteryImage.setImageResource(R.drawable.ic_battery9);
                }

                // Update data fetch timestamp for connection status
                lastDataUpdateAtMs = System.currentTimeMillis();

                // Immediately update connection status to Connected
                updateActivationText(true);
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                Toast.makeText(DashboardActivity.this, "Error fetching battery life", Toast.LENGTH_SHORT).show();
            }
        });
    }

    // Dedicated activation watcher: listens to any change under logs and marks device active by heartbeat
    private void startActivationWatcher() {
        DatabaseReference logsRef = db.child("logs");
        logsRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(DataSnapshot snapshot) {
                // Find the newest inner push key across all date buckets
                String latestKey = null;
                for (DataSnapshot dateSnap : snapshot.getChildren()) {
                    for (DataSnapshot logSnap : dateSnap.getChildren()) {
                        String k = logSnap.getKey();
                        if (k == null) continue;
                        if (latestKey == null || k.compareTo(latestKey) > 0) latestKey = k;
                    }
                }

                if (latestKey == null) {
                    // No logs at all
                    updateActivationText(false);
                    return;
                }

                if (lastActivationSeenKey == null) {
                    // Initialize without marking active yet; wait for a newer key
                    lastActivationSeenKey = latestKey;
                    updateActivationText(false);
                    return;
                }

                if (!latestKey.equals(lastActivationSeenKey)) {
                    // Newer log arrived → mark active and remember key/time
                    lastActivationSeenKey = latestKey;
                    lastLogsUpdateAtMs = System.currentTimeMillis();  // ✅ this timestamp drives the timeout
                    updateActivationText(true);
                }
            }

            @Override public void onCancelled(DatabaseError error) { }
        });
    }

    private void startConnectionTimeoutChecker() {
        connectionHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (lastLogsUpdateAtMs > 0) {
                    long diff = System.currentTimeMillis() - lastLogsUpdateAtMs;
                    if (diff > TIMEOUT_MS) {
                        // No new logs in 20 sec → Not connected
                        updateActivationText(false);
                    }
                }
                // Keep checking every 5 seconds
                connectionHandler.postDelayed(this, 5000);
            }
        }, 5000);
    }
    private void updateActivationText(boolean active) {
        if (activated == null) return;

        if (active) {
            activated.setText("Connected");
            try {
                int color = getResources().getColor(R.color.green);
                activated.setTextColor(color);
            } catch (Exception ignored) { }
        } else {
            activated.setText("Not connected");
            try {
                int color = getResources().getColor(R.color.red);
                activated.setTextColor(color);
            } catch (Exception ignored) { }

            // 👉 Extra actions for "Not connected"
            if (ivBatteryImage != null) {
                ivBatteryImage.setImageResource(R.drawable.ic_battery9);
            }
            if (tvBatteryLife != null) {
                tvBatteryLife.setText("---");
            }
        }
    }

    private void fetchTotalKwh() {
        // Reference to the cost_filter_date to get the starting and ending dates
        DatabaseReference costFilterDateRef = db.child("cost_filter_date");

        costFilterDateRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                // Fetch the starting and ending dates from Firebase
                String startingDateString = dataSnapshot.child("starting_date").getValue(String.class);
                String endingDateString = dataSnapshot.child("ending_date").getValue(String.class);

                // Log the fetched data for debugging
                Log.d("CostEstimation", "Starting Date: " + startingDateString);
                Log.d("CostEstimation", "Ending Date: " + endingDateString);

                SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());  // Adjust as per your Firebase format
                try {
                    Date startingDate = dateFormatter.parse(startingDateString);
                    Date endingDate = dateFormatter.parse(endingDateString);

                    Log.d("CostEstimation", "Parsed Starting Date: " + startingDate);
                    Log.d("CostEstimation", "Parsed Ending Date: " + endingDate);

                    // Now, fetch the hourly summaries within the date range
                    db.child("hourly_summaries").orderByKey().addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(DataSnapshot dataSnapshot) {
                            double cumulativeKwh = 0;

                            // Loop through the hourly summaries to accumulate the total kWh
                            for (DataSnapshot dateSnapshot : dataSnapshot.getChildren()) {
                                String dateKey = dateSnapshot.getKey();
                                Date currentDate = null;
                                try {
                                    currentDate = dateFormatter.parse(dateKey);
                                } catch (ParseException e) {
                                    Log.e("CostEstimation", "Error parsing date: " + dateKey);
                                }

                                Log.d("CostEstimation", "Processing Date: " + dateKey);
                                Log.d("CostEstimation", "Current Date: " + currentDate);

                                // Check if the current date is within the range of starting and ending dates
                                if ((currentDate.equals(startingDate) || currentDate.after(startingDate)) &&
                                        (currentDate.equals(endingDate) || currentDate.before(endingDate))) {

                                    // Loop through the hourly data for the current date
                                    for (DataSnapshot hourSnapshot : dateSnapshot.getChildren()) {
                                        Double hourlyKwh = hourSnapshot.child("total_kwh").getValue(Double.class);

                                        if (hourlyKwh != null) {
                                            Log.d("CostEstimation", "Hourly KWh for " + dateKey + ": " + hourlyKwh);
                                            cumulativeKwh += hourlyKwh;
                                        } else {
                                            Log.d("CostEstimation", "No hourly kWh for " + dateKey);
                                        }
                                    }
                                }
                            }

                            String formattedKwh = String.format("%.3f", cumulativeKwh);
                            Log.d("KwhTotal", "Total KWh: " + formattedKwh);

                            String totalKwhText = formattedKwh +" kwh";
                            tvTotalConsumption.setText(totalKwhText);

                            // Update data fetch timestamp for connection status
                            lastDataUpdateAtMs = System.currentTimeMillis();

                            // Immediately update connection status to Connected
                            updateActivationText(true);

                        }

                        @Override
                        public void onCancelled(DatabaseError databaseError) {
                            Log.e("FirebaseError", "Error fetching hourly summaries: " + databaseError.getMessage());
                        }
                    });
                } catch (ParseException e) {
                    e.printStackTrace();
                    Log.e("CostEstimation", "Error parsing dates: " + e.getMessage());
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                Log.e("FirebaseError", "Error fetching cost filter data: " + databaseError.getMessage());
            }
        });
    }
    private void updateArea1Name() {
        String area1Name = etArea1.getText().toString().trim();
        if (area1Name.isEmpty()) {
            Toast.makeText(DashboardActivity.this, "Area 1 Name cannot be empty!", Toast.LENGTH_SHORT).show();
            return;
        }
        area1Name = capitalizeAreaName(area1Name);

        // Show confirmation dialog
        showAreaNameChangeConfirmation("Area 1", area1Name, 1);
    }
    private String capitalizeFirstLetter(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1).toLowerCase();
    }

    // Helper method to update area icon based on area number and name
    private void updateAreaIcon(int areaNumber, String areaName) {
        String icon = getIconForAreaName(areaName);

        switch (areaNumber) {
            case 1:
                if (area1_icon != null) {
                    area1_icon.setText(icon);
                }
                break;
            case 2:
                if (area2_icon != null) {
                    area2_icon.setText(icon);
                }
                break;
            case 3:
                if (area3_icon != null) {
                    area3_icon.setText(icon);
                }
                break;
        }
    }

    // Helper method to update area details header with icon
    private void updateAreaDetailsHeader(int areaNumber, String areaName) {
        String icon = getIconForAreaName(areaName);
        String headerText = icon + " " + areaName + " Details";

        switch (areaNumber) {
            case 1:
                if (area1_details != null) {
                    area1_details.setText(headerText);
                }
                break;
            case 2:
                if (area2_details != null) {
                    area2_details.setText(headerText);
                }
                break;
            case 3:
                if (area3_details != null) {
                    area3_details.setText(headerText);
                }
                break;
        }
    }

    // Setup touch listener for EditText to detect clicks on edit icon
    private void setupEditTextTouchListener(EditText editText, int areaNumber) {
        editText.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    Drawable drawableEnd = editText.getCompoundDrawables()[2]; // Get drawableEnd
                    if (drawableEnd != null) {
                        int drawableWidth = drawableEnd.getIntrinsicWidth();
                        int editTextWidth = editText.getWidth();
                        int drawableX = editTextWidth - editText.getPaddingRight() - drawableWidth;

                        if (event.getX() >= drawableX) {
                            // Edit icon was clicked
                            enableEditing(editText, areaNumber);
                            return true;
                        }
                    }
                }
                return false;
            }
        });
    }

    // Enable editing for the specified EditText
    private void enableEditing(EditText editText, int areaNumber) {
        // Store the original name before editing starts
        String currentText = editText.getText().toString().trim();
        switch (areaNumber) {
            case 1:
                originalArea1Name = currentText;
                isArea1Editable = true;
                break;
            case 2:
                originalArea2Name = currentText;
                isArea2Editable = true;
                break;
            case 3:
                originalArea3Name = currentText;
                isArea3Editable = true;
                break;
        }

        editText.setFocusable(true);
        editText.setFocusableInTouchMode(true);
        editText.setClickable(true);
        editText.setCursorVisible(true);
        editText.requestFocus();

        // Add OnEditorActionListener to handle Enter key
        editText.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE ||
                    (event != null && event.getKeyCode() == android.view.KeyEvent.KEYCODE_ENTER)) {
                // Save the changes
                switch (areaNumber) {
                    case 1:
                        updateArea1Name();
                        break;
                    case 2:
                        updateArea2Name();
                        break;
                    case 3:
                        updateArea3Name();
                        break;
                }
                return true;
            }
            return false;
        });
    }

    // Disable editing for the specified EditText
    private void disableEditing(EditText editText, int areaNumber) {
        editText.setFocusable(false);
        editText.setClickable(false);
        editText.setLongClickable(false);
        editText.setCursorVisible(false);
        editText.clearFocus();
        editText.setOnEditorActionListener(null);

        // Clear the appropriate editable flag
        switch (areaNumber) {
            case 1:
                isArea1Editable = false;
                break;
            case 2:
                isArea2Editable = false;
                break;
            case 3:
                isArea3Editable = false;
                break;
        }
    }

    private void updateArea2Name() {
        String area2Name = etArea2.getText().toString().trim();
        if (area2Name.isEmpty()) {
            Toast.makeText(DashboardActivity.this, "Area 2 Name cannot be empty!", Toast.LENGTH_SHORT).show();
            return;
        }
        area2Name = capitalizeAreaName(area2Name);

        // Show confirmation dialog
        showAreaNameChangeConfirmation("Area 2", area2Name, 2);
    }
    private void updateArea3Name() {
        String area3Name = etArea3.getText().toString().trim();
        if (area3Name.isEmpty()) {
            Toast.makeText(DashboardActivity.this, "Area 3 Name cannot be empty!", Toast.LENGTH_SHORT).show();
            return;
        }
        area3Name = capitalizeAreaName(area3Name);

        // Show confirmation dialog
        showAreaNameChangeConfirmation("Area 3", area3Name, 3);
    }

    // Show confirmation dialog for area name change
    private void showAreaNameChangeConfirmation(String areaLabel, String newName, int areaNumber) {
        // Capitalize the name for display
        String capitalizedName = capitalizeAreaName(newName);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Confirm Area Name Change")
                .setMessage("Are you sure you want to change " + areaLabel + " name to \"" + capitalizedName + "\"?\n\n" +
                        "This action will update the database and may affect data tracking.")
                .setPositiveButton("Yes, Update", (dialog, which) -> {
                    // User confirmed, proceed with database update
                    updateAreaNameInDatabase(newName, areaNumber);
                })
                .setNegativeButton("Cancel", (dialog, which) -> {
                    // User cancelled, restore original name and disable editing
                    restoreOriginalName(areaNumber);
                    disableEditingForArea(areaNumber);
                    dialog.dismiss();
                })
                .setCancelable(false)
                .show();
    }

    // Update area name in database with history tracking
    private void updateAreaNameInDatabase(String newName, int areaNumber) {
        // Capitalize the area name properly
        newName = capitalizeAreaName(newName);
        DatabaseReference systemSettingsRef = db.child("system_settings");

        // Create timestamp
        String timestamp = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                .format(new java.util.Date());

        // Get the original name (before editing started) to store in history
        String originalName = getOriginalAreaName(areaNumber);

        // Get the appropriate field names
        String fieldName = "area" + areaNumber + "_name";
        String historyFieldName = "area" + areaNumber + "_history";

        // Update the current area name
        systemSettingsRef.child(fieldName).setValue(newName);

        // Create history entry under the "name" child
        DatabaseReference nameRef = systemSettingsRef.child("name");
        DatabaseReference historyRef = nameRef.child(historyFieldName);

        // Create a new history entry with timestamp
        String historyKey = timestamp.replace(" ", "_").replace(":", "-"); // Make it a valid Firebase key
        DatabaseReference newHistoryEntry = historyRef.child(historyKey);

        // Store both the name and timestamp in the history
        newHistoryEntry.child("name").setValue(newName);
        newHistoryEntry.child("timestamp").setValue(timestamp);
        String finalNewName = newName;
        newHistoryEntry.child("previous_name").setValue(originalName)
                .addOnSuccessListener(aVoid -> {
                    // Update EditText with capitalized name
                    EditText editText = getEditTextForArea(areaNumber);
                    if (editText != null) {
                        editText.setText(finalNewName);
                        editText.clearFocus();
                    }

                    Toast.makeText(DashboardActivity.this,
                            "Area " + areaNumber + " name updated successfully!", Toast.LENGTH_SHORT).show();

                    // Make field non-editable again after successful update
                    disableEditingForArea(areaNumber);
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(DashboardActivity.this,
                            "Error updating Area " + areaNumber + " name", Toast.LENGTH_SHORT).show();

                    // Make field non-editable again even on failure
                    disableEditingForArea(areaNumber);
                });
    }

    // Get the current area name from the EditText
    private String getCurrentAreaName(int areaNumber) {
        EditText editText = getEditTextForArea(areaNumber);
        if (editText != null) {
            return editText.getText().toString().trim();
        }
        return "";
    }

    // Get the original area name (before editing started)
    private String getOriginalAreaName(int areaNumber) {
        switch (areaNumber) {
            case 1:
                return originalArea1Name;
            case 2:
                return originalArea2Name;
            case 3:
                return originalArea3Name;
            default:
                return "";
        }
    }

    // Get the appropriate EditText for the area number
    private EditText getEditTextForArea(int areaNumber) {
        switch (areaNumber) {
            case 1: return etArea1;
            case 2: return etArea2;
            case 3: return etArea3;
            default: return null;
        }
    }

    /**
     * Capitalize area name properly (first letter of each word)
     */
    private String capitalizeAreaName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return name;
        }

        String trimmed = name.trim();
        String[] words = trimmed.split("\\s+");
        StringBuilder result = new StringBuilder();

        for (int i = 0; i < words.length; i++) {
            if (i > 0) {
                result.append(" ");
            }

            String word = words[i];
            if (word.length() > 0) {
                result.append(Character.toUpperCase(word.charAt(0)));
                if (word.length() > 1) {
                    result.append(word.substring(1).toLowerCase());
                }
            }
        }

        return result.toString();
    }

    // Disable editing for the specified area
    private void disableEditingForArea(int areaNumber) {
        EditText editText = getEditTextForArea(areaNumber);
        if (editText != null) {
            disableEditing(editText, areaNumber);
        }
    }

    // Restore the original name when user cancels editing
    private void restoreOriginalName(int areaNumber) {
        EditText editText = getEditTextForArea(areaNumber);
        if (editText != null) {
            String originalName = "";
            switch (areaNumber) {
                case 1:
                    originalName = originalArea1Name;
                    break;
                case 2:
                    originalName = originalArea2Name;
                    break;
                case 3:
                    originalName = originalArea3Name;
                    break;
            }
            editText.setText(originalName);
        }
    }
    private void fetchAreaNames() {
        DatabaseReference areaNamesRef = db.child("system_settings");
        areaNamesRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                // Fetch the area names
                String area1Name = dataSnapshot.child("area1_name").getValue(String.class);
                String area2Name = dataSnapshot.child("area2_name").getValue(String.class);
                String area3Name = dataSnapshot.child("area3_name").getValue(String.class);

                // Set the area names to the EditTexts
                if (area1Name != null) {
                    etArea1.setText(area1Name);
                    updateAreaIcon(1, area1Name);
                    updateAreaDetailsHeader(1, area1Name);
                } else {
                    etArea1.setText("Area 1 Name not available");
                    updateAreaIcon(1, "Area 1");
                    updateAreaDetailsHeader(1, "Area 1");
                }

                if (area2Name != null) {
                    etArea2.setText(area2Name);
                    updateAreaIcon(2, area2Name);
                    updateAreaDetailsHeader(2, area2Name);
                } else {
                    etArea2.setText("Area 2 Name not available");
                    updateAreaIcon(2, "Area 2");
                    updateAreaDetailsHeader(2, "Area 2");
                }

                if (area3Name != null) {
                    etArea3.setText(area3Name);
                    updateAreaIcon(3, area3Name);
                    updateAreaDetailsHeader(3, area3Name);
                } else {
                    etArea3.setText("Area 3 Name not available");
                    updateAreaIcon(3, "Area 3");
                    updateAreaDetailsHeader(3, "Area 3");
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                // Handle any error in retrieving the data
                Toast.makeText(DashboardActivity.this, "Error fetching area names", Toast.LENGTH_SHORT).show();
            }
        });
    }
    private void fetchAreaKwh() {
        DatabaseReference costFilterDateRef = db.child("cost_filter_date");

        costFilterDateRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                // Fetch the starting and ending dates from Firebase
                String startingDateString = dataSnapshot.child("starting_date").getValue(String.class);
                String endingDateString = dataSnapshot.child("ending_date").getValue(String.class);

                // Log the fetched data for debugging
                Log.d("CostEstimation", "Starting Date: " + startingDateString);
                Log.d("CostEstimation", "Ending Date: " + endingDateString);

                SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());  // Adjust as per your Firebase format
                try {
                    Date startingDate = dateFormatter.parse(startingDateString);
                    Date endingDate = dateFormatter.parse(endingDateString);

                    Log.d("CostEstimation", "Parsed Starting Date: " + startingDate);
                    Log.d("CostEstimation", "Parsed Ending Date: " + endingDate);

                    // Now, fetch the hourly summaries within the date range
                    db.child("hourly_summaries").orderByKey().addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(DataSnapshot dataSnapshot) {
                            double cumulativeArea1Kwh = 0;
                            double cumulativeArea2Kwh = 0;
                            double cumulativeArea3Kwh = 0;

                            // Loop through the hourly summaries to accumulate the total kWh for each area
                            for (DataSnapshot dateSnapshot : dataSnapshot.getChildren()) {
                                String dateKey = dateSnapshot.getKey();
                                Date currentDate = null;
                                try {
                                    currentDate = dateFormatter.parse(dateKey);
                                } catch (ParseException e) {
                                    Log.e("CostEstimation", "Error parsing date: " + dateKey);
                                }

                                Log.d("CostEstimation", "Processing Date: " + dateKey);
                                Log.d("CostEstimation", "Current Date: " + currentDate);

                                // Check if the current date is within the range of starting and ending dates
                                if ((currentDate.equals(startingDate) || currentDate.after(startingDate)) &&
                                        (currentDate.equals(endingDate) || currentDate.before(endingDate))) {

                                    // Loop through the hourly data for the current date
                                    for (DataSnapshot hourSnapshot : dateSnapshot.getChildren()) {
                                        // Fetch kWh for each area (assuming your data has area1_kwh, area2_kwh, and area3_kwh)
                                        Double area1Kwh = hourSnapshot.child("area1_kwh").getValue(Double.class);
                                        Double area2Kwh = hourSnapshot.child("area2_kwh").getValue(Double.class);
                                        Double area3Kwh = hourSnapshot.child("area3_kwh").getValue(Double.class);

                                        if (area1Kwh != null) {
                                            Log.d("CostEstimation", "Area 1 Hourly kWh for " + dateKey + ": " + area1Kwh);
                                            cumulativeArea1Kwh += area1Kwh;
                                        }

                                        if (area2Kwh != null) {
                                            Log.d("CostEstimation", "Area 2 Hourly kWh for " + dateKey + ": " + area2Kwh);
                                            cumulativeArea2Kwh += area2Kwh;
                                        }

                                        if (area3Kwh != null) {
                                            Log.d("CostEstimation", "Area 3 Hourly kWh for " + dateKey + ": " + area3Kwh);
                                            cumulativeArea3Kwh += area3Kwh;
                                        }
                                    }
                                }
                            }

                            // Calculate total kWh from all areas
                            double totalKwh = cumulativeArea1Kwh + cumulativeArea2Kwh + cumulativeArea3Kwh;

                            // Calculate percentage for each area
                            double area1Percentage = (cumulativeArea1Kwh / totalKwh) * 100;
                            double area2Percentage = (cumulativeArea2Kwh / totalKwh) * 100;
                            double area3Percentage = (cumulativeArea3Kwh / totalKwh) * 100;

                            // Format and display the total kWh value for each area
                            String formattedArea1Kwh = String.format("%.3f", cumulativeArea1Kwh);
                            String formattedArea2Kwh = String.format("%.3f", cumulativeArea2Kwh);
                            String formattedArea3Kwh = String.format("%.3f", cumulativeArea3Kwh);

                            String formattedArea1Percentage = String.format("%.2f", area1Percentage);
                            String formattedArea2Percentage = String.format("%.2f", area2Percentage);
                            String formattedArea3Percentage = String.format("%.2f", area3Percentage);

                            Log.d("AreaKwhTotal", "Area 1 Total kWh: " + formattedArea1Kwh + " (" + formattedArea1Percentage + "%)");
                            Log.d("AreaKwhTotal", "Area 2 Total kWh: " + formattedArea2Kwh + " (" + formattedArea2Percentage + "%)");
                            Log.d("AreaKwhTotal", "Area 3 Total kWh: " + formattedArea3Kwh + " (" + formattedArea3Percentage + "%)");

                            // Set the total kWh for each area
                            tvArea1Kwh.setText(formattedArea1Kwh);
                            tvArea2Kwh.setText(formattedArea2Kwh);
                            tvArea3Kwh.setText(formattedArea3Kwh);

                            // Set the corresponding percentages for each area in separate TextViews
                            tvArea1Percentage.setText(formattedArea1Percentage );
                            tvPercent1.setText(formattedArea1Percentage + " %");
                            tvArea2Percentage.setText(formattedArea2Percentage );
                            tvPercent2.setText(formattedArea2Percentage + " %");
                            tvArea3Percentage.setText(formattedArea3Percentage );
                            tvPercent3.setText(formattedArea3Percentage + " %");
                        }

                        @Override
                        public void onCancelled(DatabaseError databaseError) {
                            Log.e("FirebaseError", "Error fetching hourly summaries: " + databaseError.getMessage());
                        }
                    });
                } catch (ParseException e) {
                    e.printStackTrace();
                    Log.e("CostEstimation", "Error parsing dates: " + e.getMessage());
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                Log.e("FirebaseError", "Error fetching cost filter data: " + databaseError.getMessage());
            }
        });
    }
    private void fetchPeakWatts() {
        // Reference to the cost_filter_date to get the starting and ending dates
        DatabaseReference costFilterDateRef = db.child("cost_filter_date");

        costFilterDateRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                // Fetch the starting and ending dates from Firebase
                String startingDateString = dataSnapshot.child("starting_date").getValue(String.class);
                String endingDateString = dataSnapshot.child("ending_date").getValue(String.class);

                // Log the fetched data for debugging
                Log.d("PeakWatts", "Starting Date: " + startingDateString);
                Log.d("PeakWatts", "Ending Date: " + endingDateString);

                SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                try {
                    Date startingDate = dateFormatter.parse(startingDateString);
                    Date endingDate = dateFormatter.parse(endingDateString);
                    
                    // Get today's date
                    Date today = new Date();
                    
                    // Use the lesser of today or ending date as the effective end date
                    Date effectiveEndDate;
                    if (today.before(endingDate)) {
                        effectiveEndDate = today;
                        Log.d("PeakWatts", "Using today as effective end date: " + dateFormatter.format(today));
                    } else {
                        effectiveEndDate = endingDate;
                    }
                    Log.d("PeakWatts", "Parsed Starting Date: " + startingDate);
                    Log.d("PeakWatts", "Effective Ending Date: " + dateFormatter.format(effectiveEndDate));

                    // Fetch hourly summaries to calculate peak watts from actual data
                    db.child("hourly_summaries").orderByKey().addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(DataSnapshot dataSnapshot) {
                            double highestPeakWatts = 0;
                            String peakDate = "";
                            String peakHour = "";

                            // Loop through the hourly summaries to find the highest total_watts
                            for (DataSnapshot dateSnapshot : dataSnapshot.getChildren()) {
                                String dateKey = dateSnapshot.getKey();
                                Date currentDate = null;
                                try {
                                    currentDate = dateFormatter.parse(dateKey);
                                } catch (ParseException e) {
                                    Log.e("PeakWatts", "Error parsing date: " + dateKey);
                                    continue;
                                }

                                // Check if the current date is within the range (from starting date to effective end date)
                                if ((currentDate.equals(startingDate) || currentDate.after(startingDate)) &&
                                        (currentDate.equals(effectiveEndDate) || currentDate.before(effectiveEndDate))) {

                                    // Loop through hourly data to find peak
                                    for (DataSnapshot hourSnapshot : dateSnapshot.getChildren()) {
                                        String hourKey = hourSnapshot.getKey();
                                        
                                        // Get the peak_watts field which stores the highest power reading for that hour
                                        Double peakWatts = hourSnapshot.child("peak_watts").getValue(Double.class);
                                        
                                        // Fallback: try other fields if peak_watts not available
                                        if (peakWatts == null) {
                                            peakWatts = hourSnapshot.child("total_watts").getValue(Double.class);
                                        }
                                        if (peakWatts == null) {
                                            peakWatts = hourSnapshot.child("avg_watts").getValue(Double.class);
                                        }

                                        if (peakWatts != null && peakWatts > highestPeakWatts) {
                                            highestPeakWatts = peakWatts;
                                            peakDate = dateKey;
                                            peakHour = hourKey;
                                            Log.d("PeakWatts", "New peak found: " + peakWatts + "W on " + dateKey + " at hour " + hourKey);
                                        }
                                    }
                                }
                            }

                            // Check if we found any peak data
                            if (highestPeakWatts > 0 && !peakHour.isEmpty() && !peakDate.isEmpty()) {
                                try {
                                    // Format the date from "yyyy-MM-dd" to "MMM d" (e.g., "Oct 9")
                                    SimpleDateFormat inputDateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                                    SimpleDateFormat outputDateFormat = new SimpleDateFormat("MMM d", Locale.getDefault());
                                    Date date = inputDateFormat.parse(peakDate);
                                    String formattedDate = outputDateFormat.format(date);
                                    
                                    // Format the hour (assuming format like "14" for hour or "14:00:00")
                                    String timeString = peakHour;
                                    SimpleDateFormat time12Format;
                                    
                                    // Handle different hour formats
                                    if (peakHour.contains(":")) {
                                        // Format: "HH:mm:ss" or "HH:mm"
                                        SimpleDateFormat time24Format = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
                                        try {
                                            Date peakTimeDate = time24Format.parse(peakHour);
                                            time12Format = new SimpleDateFormat("hh:mm a", Locale.getDefault());
                                            timeString = time12Format.format(peakTimeDate);
                                        } catch (ParseException e) {
                                            // Try HH:mm format
                                            time24Format = new SimpleDateFormat("HH:mm", Locale.getDefault());
                                            Date peakTimeDate = time24Format.parse(peakHour);
                                            time12Format = new SimpleDateFormat("hh:mm a", Locale.getDefault());
                                            timeString = time12Format.format(peakTimeDate);
                                        }
                                    } else {
                                        // Format: just hour number like "14"
                                        int hour = Integer.parseInt(peakHour);
                                        String ampm = hour >= 12 ? "PM" : "AM";
                                        int displayHour = hour % 12;
                                        if (displayHour == 0) displayHour = 12;
                                        timeString = String.format(Locale.getDefault(), "%d:00 %s", displayHour, ampm);
                                    }

                                    // Display the highest peak watts value
                                    String formattedPeakWatts = String.format("%.0f", highestPeakWatts);
                                    Log.d("PeakWatts", "Highest Peak: " + formattedPeakWatts + "W on " + formattedDate + " at " + timeString);

                                    // Update UI with formatted date and time (e.g., "Oct 9 at 6:00 AM")
                                    tvPeakTime.setText(formattedDate + " at " + timeString);
                                    tvPeakValue.setText(formattedPeakWatts + " watts");
                                    
                                } catch (Exception e) {
                                    Log.e("PeakWatts", "Error formatting peak time: " + e.getMessage());
                                    String formattedPeakWatts = String.format("%.0f", highestPeakWatts);
                                    tvPeakValue.setText(formattedPeakWatts + " W");
                                    tvPeakTime.setText(peakDate);
                                }
                            } else {
                                // No peak data found in the date range
                                Log.d("PeakWatts", "No peak data found in the selected date range");
                                tvPeakValue.setText("---");
                                tvPeakTime.setText("No data");
                            }
                        }

                        @Override
                        public void onCancelled(DatabaseError databaseError) {
                            Log.e("FirebaseError", "Error fetching hourly summaries: " + databaseError.getMessage());
                            tvPeakValue.setText("---");
                            tvPeakTime.setText("Error");
                        }
                    });
                } catch (ParseException e) {
                    e.printStackTrace();
                    Log.e("PeakWatts", "Error parsing dates: " + e.getMessage());
                    tvPeakValue.setText("---");
                    tvPeakTime.setText("Error");
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                Log.e("FirebaseError", "Error fetching cost filter data: " + databaseError.getMessage());
                tvPeakValue.setText("---");
                tvPeakTime.setText("Error");
            }
        });
    }

    // Computes usage trend: yesterday (from daily_summaries) vs today-so-far (sum of hourly_summaries for current date)
    private void fetchUsageTrend() {
        final SimpleDateFormat ymd = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        final Calendar cal = Calendar.getInstance();
        final String todayKey = ymd.format(cal.getTime());
        cal.add(Calendar.DAY_OF_YEAR, -1);
        final String yesterdayKey = ymd.format(cal.getTime());
        
        // Reset consumption alert flags daily (when new day starts)
        resetConsumptionAlertFlags();

        // Step 1: read yesterday total_kwh from daily_summaries
        db.child("daily_summaries").child(yesterdayKey).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(DataSnapshot dsYesterday) {
                Double yesterdayTotal = dsYesterday.child("total_kwh").getValue(Double.class);
                if (yesterdayTotal == null) yesterdayTotal = 0.0;

                // Step 2: sum today's hourly total_kwh from hourly_summaries/{today}
                Double finalYesterdayTotal = yesterdayTotal;
                db.child("hourly_summaries").child(todayKey).addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override public void onDataChange(DataSnapshot dsTodayHours) {
                        double todaySoFar = 0.0;
                        for (DataSnapshot hourSnap : dsTodayHours.getChildren()) {
                            Double k = hourSnap.child("total_kwh").getValue(Double.class);
                            if (k != null) todaySoFar += k;
                        }

                        // Compute percentage change ((today - yesterday) / yesterday) * 100
                        Double pctChange;
                        if (finalYesterdayTotal <= 0.0) {
                            // If no data yesterday, define change as 100% if today>0 else 0%
                            pctChange = todaySoFar > 0.0 ? 100.0 : 0.0;
                        } else {
                            pctChange = ((todaySoFar - finalYesterdayTotal) / finalYesterdayTotal) * 100.0;
                        }

                        // Update UI
                        String sign = pctChange > 0 ? "+" : "";
                        String pctText = String.format(Locale.getDefault(), "%s%.1f%%", sign, pctChange);
                        tvPercentageChange.setText(pctText);

                        if (pctChange == 0.0) {
                            // Hide trend icon when percentage change is exactly 0.0
                            tvTrendIcon.setVisibility(View.GONE);
                        } else {
                            // Show trend icon for any non-zero change
                            tvTrendIcon.setVisibility(View.VISIBLE);
                            if (pctChange > 0) {
                                tvTrendIcon.setImageResource(R.drawable.ic_up);
                                percentageChangeContainer.setBackgroundResource(R.drawable.bg_percentage);
                                
                                // Send consumption alerts at specific intervals: 100%, 150%, 200%
                                if (pctChange >= 100.0 && !consumptionAlert100Sent) {
                                    sendConsumptionAlert(pctChange, todaySoFar, finalYesterdayTotal);
                                    consumptionAlert100Sent = true;
                                    Log.d(TAG, "✅ Consumption alert sent at 100% threshold");
                                } else if (pctChange >= 150.0 && !consumptionAlert150Sent) {
                                    sendConsumptionAlert(pctChange, todaySoFar, finalYesterdayTotal);
                                    consumptionAlert150Sent = true;
                                    Log.d(TAG, "✅ Consumption alert sent at 150% threshold");
                                } else if (pctChange >= 200.0 && !consumptionAlert200Sent) {
                                    sendConsumptionAlert(pctChange, todaySoFar, finalYesterdayTotal);
                                    consumptionAlert200Sent = true;
                                    Log.d(TAG, "✅ Consumption alert sent at 200% threshold");
                                }
                            } else {
                                tvTrendIcon.setImageResource(R.drawable.ic_down);
                                percentageChangeContainer.setBackgroundResource(R.drawable.bg_percentage_change);
                            }
                        }

                    }

                    @Override public void onCancelled(DatabaseError error) { }
                });
            }

            @Override public void onCancelled(DatabaseError error) { }
        });
    }
    
    private void resetConsumptionAlertFlags() {
        // Reset flags at the start of each day
        consumptionAlert100Sent = false;
        consumptionAlert150Sent = false;
        consumptionAlert200Sent = false;
        Log.d(TAG, "🔄 Consumption alert flags reset for new day");
    }
    
    private void sendConsumptionAlert(double percentageChange, double todayConsumption, double yesterdayConsumption) {
        DatabaseReference alertsRef = db.child("alerts");
        
        // Determine which threshold was reached
        String thresholdText = "";
        if (percentageChange >= 200.0) {
            thresholdText = " (200%+ threshold reached)";
        } else if (percentageChange >= 150.0) {
            thresholdText = " (150%+ threshold reached)";
        } else if (percentageChange >= 100.0) {
            thresholdText = " (100%+ threshold reached)";
        }
        
        String title = "High Consumption Alert" + thresholdText;
        String message = String.format(Locale.getDefault(), 
            "Your today's consumption (%.2f kWh) is %.1f%% higher than yesterday (%.2f kWh). Please monitor your energy usage.",
            todayConsumption, percentageChange, yesterdayConsumption);
        
        java.util.Map<String, Object> alertData = new java.util.HashMap<>();
        alertData.put("type", "consumption");
        alertData.put("title", title);
        alertData.put("message", message);
        alertData.put("time", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date()));
        alertData.put("read", false);
        alertData.put("delete", false);
        
        alertsRef.push().setValue(alertData)
            .addOnSuccessListener(aVoid -> {
                Log.d(TAG, "✅ Consumption alert saved to database");
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "❌ Failed to save consumption alert: " + e.getMessage());
            });
    }
    
    private void fetchArea1() {
        DatabaseReference costFilterDateRef = db.child("cost_filter_date");

        costFilterDateRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                String startingDateString = dataSnapshot.child("starting_date").getValue(String.class);
                String endingDateString = dataSnapshot.child("ending_date").getValue(String.class);

                SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                try {
                    Date startingDate = dateFormatter.parse(startingDateString);
                    Date endingDate = dateFormatter.parse(endingDateString);

                    db.child("hourly_summaries").orderByKey().addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(DataSnapshot dataSnapshot) {
                            ArrayList<Entry> entries = new ArrayList<>();
                            ArrayList<String> dateLabels = new ArrayList<>();
                            int index = 0;

                            for (DataSnapshot dateSnapshot : dataSnapshot.getChildren()) {
                                String dateKey = dateSnapshot.getKey();
                                Date currentDate = null;
                                try {
                                    currentDate = dateFormatter.parse(dateKey);
                                } catch (ParseException e) {
                                    Log.e("CostEstimation", "Error parsing date: " + dateKey);
                                }

                                if ((currentDate.equals(startingDate) || currentDate.after(startingDate)) &&
                                        (currentDate.equals(endingDate) || currentDate.before(endingDate))) {
                                    double totalArea1Kwh = 0.0;

                                    for (DataSnapshot hourSnapshot : dateSnapshot.getChildren()) {
                                        Double area1Kwh = hourSnapshot.child("area1_kwh").getValue(Double.class);

                                        if (area1Kwh != null) {
                                            totalArea1Kwh += area1Kwh;
                                        }
                                    }

                                    entries.add(new Entry(index, (float) totalArea1Kwh));
                                    dateLabels.add(new SimpleDateFormat("MM-dd", Locale.getDefault()).format(currentDate));
                                    index++;
                                }
                            }

                            if (!entries.isEmpty()) {
                                LineDataSet dataSet = new LineDataSet(entries, "Area 1 Consumption (kWh)");
                                LineData lineData = new LineData(dataSet);

                                dataSet.setColor(getResources().getColor(R.color.brown));
                                dataSet.setValueTextColor(getResources().getColor(R.color.brown));
                                dataSet.setDrawFilled(true);
                                dataSet.setLineWidth(2f);
                                dataSet.setDrawCircles(true);

                                lineChart1.setData(lineData);
                                lineChart1.getAxisRight().setEnabled(false);
                                lineChart1.getAxisLeft().setGridColor(getResources().getColor(R.color.brown));
                                lineChart1.getAxisLeft().setTextColor(getResources().getColor(R.color.brown));
                                lineChart1.getXAxis().setGridColor(getResources().getColor(R.color.brown));
                                lineChart1.getAxisRight().setGridColor(getResources().getColor(R.color.brown));
                                lineChart1.getLegend().setTextColor(getResources().getColor(R.color.brown));


                                XAxis xAxis = lineChart1.getXAxis();
                                xAxis.setValueFormatter(new IndexAxisValueFormatter(dateLabels));
                                xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
                                xAxis.setGranularity(1f);
                                xAxis.setTextColor(getResources().getColor(R.color.brown));
                                lineChart1.getDescription().setEnabled(false);
                                lineChart1.setExtraBottomOffset(10f);
                                xAxis.setDrawLabels(true);


                                Legend legend = lineChart1.getLegend();
                                legend.setVerticalAlignment(Legend.LegendVerticalAlignment.TOP);
                                legend.setForm(Legend.LegendForm.SQUARE);
                                legend.setTextColor(getResources().getColor(R.color.brown));

                                lineChart1.invalidate();
                            } else {
                                Log.d("CostEstimation", "No data available for the selected date range.");
                            }
                        }

                        @Override
                        public void onCancelled(DatabaseError databaseError) {
                            Log.e("FirebaseError", "Error fetching hourly summaries: " + databaseError.getMessage());
                        }
                    });
                } catch (ParseException e) {
                    e.printStackTrace();
                    Log.e("CostEstimation", "Error parsing dates: " + e.getMessage());
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                Log.e("FirebaseError", "Error fetching cost filter data: " + databaseError.getMessage());
            }
        });
    }
    private void fetchArea2() {
        DatabaseReference costFilterDateRef = db.child("cost_filter_date");

        costFilterDateRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                String startingDateString = dataSnapshot.child("starting_date").getValue(String.class);
                String endingDateString = dataSnapshot.child("ending_date").getValue(String.class);

                SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                try {
                    Date startingDate = dateFormatter.parse(startingDateString);
                    Date endingDate = dateFormatter.parse(endingDateString);

                    db.child("hourly_summaries").orderByKey().addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(DataSnapshot dataSnapshot) {
                            ArrayList<Entry> entries = new ArrayList<>();
                            ArrayList<String> dateLabels = new ArrayList<>();
                            int index = 0;

                            for (DataSnapshot dateSnapshot : dataSnapshot.getChildren()) {
                                String dateKey = dateSnapshot.getKey();
                                Date currentDate = null;
                                try {
                                    currentDate = dateFormatter.parse(dateKey);
                                } catch (ParseException e) {
                                    Log.e("CostEstimation", "Error parsing date: " + dateKey);
                                }

                                if ((currentDate.equals(startingDate) || currentDate.after(startingDate)) &&
                                        (currentDate.equals(endingDate) || currentDate.before(endingDate))) {
                                    double totalArea2Kwh = 0.0;

                                    for (DataSnapshot hourSnapshot : dateSnapshot.getChildren()) {
                                        Double area2Kwh = hourSnapshot.child("area2_kwh").getValue(Double.class);

                                        if (area2Kwh != null) {
                                            totalArea2Kwh += area2Kwh;
                                        }
                                    }

                                    entries.add(new Entry(index, (float) totalArea2Kwh));
                                    dateLabels.add(new SimpleDateFormat("MM-dd", Locale.getDefault()).format(currentDate));
                                    index++;
                                }
                            }

                            if (!entries.isEmpty()) {
                                LineDataSet dataSet = new LineDataSet(entries, "Area 2 Consumption (kWh)");
                                LineData lineData = new LineData(dataSet);

                                dataSet.setColor(getResources().getColor(R.color.brown));
                                dataSet.setValueTextColor(getResources().getColor(R.color.brown));
                                dataSet.setDrawFilled(true);
                                dataSet.setLineWidth(2f);
                                dataSet.setDrawCircles(true);

                                lineChart2.setData(lineData);
                                lineChart2.getAxisRight().setEnabled(false);
                                lineChart2.getAxisLeft().setGridColor(getResources().getColor(R.color.brown));
                                lineChart2.getAxisLeft().setTextColor(getResources().getColor(R.color.brown));
                                lineChart2.getXAxis().setGridColor(getResources().getColor(R.color.brown));
                                lineChart2.getAxisRight().setGridColor(getResources().getColor(R.color.brown));
                                lineChart2.getLegend().setTextColor(getResources().getColor(R.color.brown));


                                XAxis xAxis = lineChart2.getXAxis();
                                xAxis.setValueFormatter(new IndexAxisValueFormatter(dateLabels));
                                xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
                                xAxis.setGranularity(1f);
                                xAxis.setTextColor(getResources().getColor(R.color.brown));
                                lineChart2.getDescription().setEnabled(false);
                                lineChart2.setExtraBottomOffset(10f);
                                xAxis.setDrawLabels(true);


                                Legend legend = lineChart2.getLegend();
                                legend.setVerticalAlignment(Legend.LegendVerticalAlignment.TOP);
                                legend.setForm(Legend.LegendForm.SQUARE);
                                legend.setTextColor(getResources().getColor(R.color.brown));

                                lineChart2.invalidate();
                            } else {
                                Log.d("CostEstimation", "No data available for the selected date range.");
                            }
                        }

                        @Override
                        public void onCancelled(DatabaseError databaseError) {
                            Log.e("FirebaseError", "Error fetching hourly summaries: " + databaseError.getMessage());
                        }
                    });
                } catch (ParseException e) {
                    e.printStackTrace();
                    Log.e("CostEstimation", "Error parsing dates: " + e.getMessage());
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                Log.e("FirebaseError", "Error fetching cost filter data: " + databaseError.getMessage());
            }
        });
    }
    private void fetchArea3() {
        DatabaseReference costFilterDateRef = db.child("cost_filter_date");

        costFilterDateRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                String startingDateString = dataSnapshot.child("starting_date").getValue(String.class);
                String endingDateString = dataSnapshot.child("ending_date").getValue(String.class);

                SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                try {
                    Date startingDate = dateFormatter.parse(startingDateString);
                    Date endingDate = dateFormatter.parse(endingDateString);

                    db.child("hourly_summaries").orderByKey().addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(DataSnapshot dataSnapshot) {
                            ArrayList<Entry> entries = new ArrayList<>();
                            ArrayList<String> dateLabels = new ArrayList<>();
                            int index = 0;

                            for (DataSnapshot dateSnapshot : dataSnapshot.getChildren()) {
                                String dateKey = dateSnapshot.getKey();
                                Date currentDate = null;
                                try {
                                    currentDate = dateFormatter.parse(dateKey);
                                } catch (ParseException e) {
                                    Log.e("CostEstimation", "Error parsing date: " + dateKey);
                                }

                                if ((currentDate.equals(startingDate) || currentDate.after(startingDate)) &&
                                        (currentDate.equals(endingDate) || currentDate.before(endingDate))) {
                                    double totalArea3Kwh = 0.0;

                                    for (DataSnapshot hourSnapshot : dateSnapshot.getChildren()) {
                                        Double area3Kwh = hourSnapshot.child("area3_kwh").getValue(Double.class);

                                        if (area3Kwh != null) {
                                            totalArea3Kwh += area3Kwh;
                                        }
                                    }

                                    entries.add(new Entry(index, (float) totalArea3Kwh));
                                    dateLabels.add(new SimpleDateFormat("MM-dd", Locale.getDefault()).format(currentDate));
                                    index++;
                                }
                            }

                            if (!entries.isEmpty()) {
                                LineDataSet dataSet = new LineDataSet(entries, "Area 3 Consumption (kWh)");
                                LineData lineData = new LineData(dataSet);

                                dataSet.setColor(getResources().getColor(R.color.brown));
                                dataSet.setValueTextColor(getResources().getColor(R.color.brown));
                                dataSet.setDrawFilled(true);
                                dataSet.setLineWidth(2f);
                                dataSet.setDrawCircles(true);

                                lineChart3.setData(lineData);
                                lineChart3.getAxisRight().setEnabled(false);
                                lineChart3.getAxisLeft().setGridColor(getResources().getColor(R.color.brown));
                                lineChart3.getAxisLeft().setTextColor(getResources().getColor(R.color.brown));
                                lineChart3.getXAxis().setGridColor(getResources().getColor(R.color.brown));
                                lineChart3.getAxisRight().setGridColor(getResources().getColor(R.color.brown));
                                lineChart3.getLegend().setTextColor(getResources().getColor(R.color.brown));


                                XAxis xAxis = lineChart3.getXAxis();
                                xAxis.setValueFormatter(new IndexAxisValueFormatter(dateLabels));
                                xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
                                xAxis.setGranularity(1f);
                                xAxis.setTextColor(getResources().getColor(R.color.brown));
                                lineChart3.getDescription().setEnabled(false);
                                lineChart3.setExtraBottomOffset(10f);
                                xAxis.setDrawLabels(true);


                                Legend legend = lineChart3.getLegend();
                                legend.setVerticalAlignment(Legend.LegendVerticalAlignment.TOP);
                                legend.setForm(Legend.LegendForm.SQUARE);
                                legend.setTextColor(getResources().getColor(R.color.brown));

                                lineChart3.invalidate();
                            } else {
                                Log.d("CostEstimation", "No data available for the selected date range.");
                            }
                        }

                        @Override
                        public void onCancelled(DatabaseError databaseError) {
                            Log.e("FirebaseError", "Error fetching hourly summaries: " + databaseError.getMessage());
                        }
                    });
                } catch (ParseException e) {
                    e.printStackTrace();
                    Log.e("CostEstimation", "Error parsing dates: " + e.getMessage());
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                Log.e("FirebaseError", "Error fetching cost filter data: " + databaseError.getMessage());
            }
        });
    }

    private Long extractTimestamp(DataSnapshot logSnapshot) {
        // Try common field names for timestamps and coerce to long
        Object v;
        String[] keys = new String[] { "timestamp", "created_at", "createdAt", "ts", "time" };
        for (String k : keys) {
            v = logSnapshot.child(k).getValue();
            if (v instanceof Number) return ((Number) v).longValue();
            if (v instanceof String) {
                try {
                    return Long.parseLong((String) v);
                } catch (NumberFormatException ignored) { }
            }
        }
        return null;
    }
    // Heuristic score for ordering date buckets with mixed formats
    private long computeDateBucketScore(String key) {
        if (key == null) return Long.MIN_VALUE;
        try {
            if (key.startsWith("UPTIME-")) {
                // UPTIME-HH:MM:SS → convert to seconds
                String t = key.substring("UPTIME-".length());
                String[] parts = t.split(":");
                if (parts.length == 3) {
                    int h = Integer.parseInt(parts[0]);
                    int m = Integer.parseInt(parts[1]);
                    int s = Integer.parseInt(parts[2]);
                    return 1_000_000_000L + h * 3600L + m * 60L + s; // add bias to prefer uptime over plain dates
                }
            }
        } catch (Exception ignored) { }

        try {
            // Try ISO-like 2025-09-18T17:48:06Z
            SimpleDateFormat iso = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault());
            Date d = iso.parse(key);
            if (d != null) return d.getTime();
        } catch (Exception ignored) { }

        return key.hashCode();
    }
















}