package com.example.sampleiwatts;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.example.sampleiwatts.processors.RealTimeDataProcessor;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

public class RealTimeMonitoringActivity extends AppCompatActivity {
    private static final String TAG = "RealTimeMonitoring";
    private static final long REFRESH_INTERVAL = 3 * 60 * 1000; // 3 minutes in milliseconds
    private boolean areaLabelsFetched = false;
    // UI Components - Overall Summary
    private TextView todaysTotalValue;
    private TextView todaysTotalPercentage;
    private TextView peakUsageValue;
    private TextView peakUsageTime;
    private TextView estimatedCostValue;
    private TextView estimatedCostDetails;

    private double yesterdayBaselineConsumption = -1.0;
    private boolean baselineFetched = false;

    // UI Components - Area 1
    private FrameLayout area1ChartContainer;
    private LineChart area1Chart;
    private TextView area1TotalConsumption;
    private TextView area1EstimatedCost;
    private TextView area1PeakConsumption;
    private TextView area1SharePercentage;

    // UI Components - Area 2
    private FrameLayout area2ChartContainer;
    private LineChart area2Chart;
    private TextView area2TotalConsumption;
    private TextView area2EstimatedCost;
    private TextView area2PeakConsumption;
    private TextView area2SharePercentage;

    // UI Components - Area 3
    private FrameLayout area3ChartContainer;
    private LineChart area3Chart;
    private TextView area3TotalConsumption;
    private TextView area3EstimatedCost;
    private TextView area3PeakConsumption;
    private TextView area3SharePercentage;

    // Data processor and refresh handler
    private RealTimeDataProcessor dataProcessor;
    private Handler refreshHandler;
    private Runnable refreshRunnable;

    private NavigationDrawerHelper drawerHelper;

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_real_time_monitoring);

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

        // Get the button layout
        LinearLayout buttonLayout = findViewById(R.id.button);

        // Set up buttons using the utility class
        ButtonNavigator.setupButtons(this, buttonLayout);
        Log.d(TAG, "RealTimeMonitoringActivity created");

        initializeViews();
        setupRefreshTimer();
        fetchAreaLabelsOnce();

        // Initialize data processor - UPDATED: No context parameter needed
        dataProcessor = new RealTimeDataProcessor();

        // Load initial data
        loadRealTimeData();
    }



    /**
     * Initialize all UI components
     */
    private void initializeViews() {
        // Overall summary views
        todaysTotalValue = findViewById(R.id.todays_total_value);
        todaysTotalPercentage = findViewById(R.id.todays_total_percentage);
        peakUsageValue = findViewById(R.id.peak_usage_value);
        peakUsageTime = findViewById(R.id.peak_usage_time);
        estimatedCostValue = findViewById(R.id.estimated_cost_value);


        // Area 1 views
        area1ChartContainer = findViewById(R.id.area1_chart_container);
        area1TotalConsumption = findViewById(R.id.area1_total_consumption);
        area1EstimatedCost = findViewById(R.id.area1_estimated_cost);
        area1PeakConsumption = findViewById(R.id.area1_peak_consumption);
        area1SharePercentage = findViewById(R.id.area1_share_percentage);

        // Area 2 views
        area2ChartContainer = findViewById(R.id.area2_chart_container);
        area2TotalConsumption = findViewById(R.id.area2_total_consumption);
        area2EstimatedCost = findViewById(R.id.area2_estimated_cost);
        area2PeakConsumption = findViewById(R.id.area2_peak_consumption);
        area2SharePercentage = findViewById(R.id.area2_share_percentage);

        // Area 3 views
        area3ChartContainer = findViewById(R.id.area3_chart_container);
        area3TotalConsumption = findViewById(R.id.area3_total_consumption);
        area3EstimatedCost = findViewById(R.id.area3_estimated_cost);
        area3PeakConsumption = findViewById(R.id.area3_peak_consumption);
        area3SharePercentage = findViewById(R.id.area3_share_percentage);

        // Setup chart containers
        setupChartContainers();

        Log.d(TAG, "All views initialized successfully");
    }

    /**
     * Setup chart containers with LineChart instances
     */
    private void setupChartContainers() {
        try {
            // Create LineChart for Area 1
            if (area1ChartContainer != null) {
                area1Chart = new LineChart(this);
                area1ChartContainer.removeAllViews();
                area1ChartContainer.addView(area1Chart);
            }

            // Create LineChart for Area 2
            if (area2ChartContainer != null) {
                area2Chart = new LineChart(this);
                area2ChartContainer.removeAllViews();
                area2ChartContainer.addView(area2Chart);
            }

            // Create LineChart for Area 3
            if (area3ChartContainer != null) {
                area3Chart = new LineChart(this);
                area3ChartContainer.removeAllViews();
                area3ChartContainer.addView(area3Chart);
            }

            Log.d(TAG, "Chart containers setup successfully");

        } catch (Exception e) {
            Log.e(TAG, "Error setting up chart containers: " + e.getMessage(), e);
        }
    }

    /**
     * Setup automatic refresh timer
     */
    private void setupRefreshTimer() {
        refreshHandler = new Handler();
        refreshRunnable = new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "Auto-refreshing real-time data...");
                loadRealTimeData();
                // Schedule next refresh
                refreshHandler.postDelayed(this, REFRESH_INTERVAL);
            }
        };
    }

    /**
     * Load real-time data from processor - UPDATED for new logs-based processor
     */
    private void loadRealTimeData() {
        Log.d(TAG, "Loading real-time data...");



        // UPDATED: Use new method signature with date parameter
        String todayDate = getCurrentDate();
        dataProcessor.processRealTimeData(todayDate, new RealTimeDataProcessor.DataProcessingCallback() {
            @Override
            public void onDataProcessed(RealTimeDataProcessor.RealTimeData realTimeData) {



                runOnUiThread(() -> {
                    // 🆕 VALIDATE DATA BEFORE UPDATING UI:
                    if (validateRealTimeData(realTimeData)) {
                        updateUI(realTimeData);
                        Log.d(TAG, "✅ UI updated successfully with valid data");
                    } else {
                        Log.w(TAG, "⚠️ Invalid data received, not updating UI");
                        Toast.makeText(RealTimeMonitoringActivity.this,
                                "Data incomplete - please wait and try again", Toast.LENGTH_SHORT).show();
                    }
                });
            }

            private boolean validateRealTimeData(RealTimeDataProcessor.RealTimeData data) {
                if (data == null) {
                    Log.e(TAG, "❌ Data is null");
                    return false;
                }

                if (data.totalConsumption < 0) {
                    Log.e(TAG, "❌ Negative total consumption: " + data.totalConsumption);
                    return false;
                }

                if (data.hourlyData == null || data.hourlyData.isEmpty()) {
                    Log.e(TAG, "❌ No hourly data available");
                    return false;
                }

                if (data.area1Data == null || data.area2Data == null || data.area3Data == null) {
                    Log.e(TAG, "❌ Missing area data");
                    return false;
                }

                Log.d(TAG, "✅ Data validation passed");
                return true;
            }

            @Override
            public void onError(String error) {

                runOnUiThread(() -> {
                    Log.e(TAG, "Error loading real-time data: " + error);
                    Toast.makeText(RealTimeMonitoringActivity.this,
                            "Failed to load data: " + error, Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onSettingsLoaded(double electricityRate, double voltageReference) {
                Log.d(TAG, "Settings loaded - Rate: " + electricityRate + "/kWh, Voltage: " + voltageReference + "V");
            }
        });
    }

    /**
     * Helper method to get current date in Philippine timezone
     */
    private String getCurrentDate() {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        dateFormat.setTimeZone(TimeZone.getTimeZone("Asia/Manila"));
        return dateFormat.format(new Date());
    }

    private void fetchAreaLabelsOnce() {
        if (areaLabelsFetched) return;

        DatabaseReference systemSettingsRef = FirebaseDatabase.getInstance()
                .getReference("system_settings");

        systemSettingsRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                String area1Name = snapshot.child("area1_name").getValue(String.class);
                String area2Name = snapshot.child("area2_name").getValue(String.class);
                String area3Name = snapshot.child("area3_name").getValue(String.class);

                // Use defaults if not found
                if (area1Name == null) area1Name = "Area 1";
                if (area2Name == null) area2Name = "Area 2";
                if (area3Name == null) area3Name = "Area 3";

                // Update labels once
                updateAreaLabels(area1Name, area2Name, area3Name);
                areaLabelsFetched = true;

                Log.d(TAG, "Area labels updated: " + area1Name + ", " + area2Name + ", " + area3Name);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.w(TAG, "Failed to fetch area names: " + error.getMessage());
                // Use defaults
                updateAreaLabels("Area 1", "Area 2", "Area 3");
                areaLabelsFetched = true;
            }
        });
    }


    /**
     * Update UI with real-time data
     */
    private void updateUI(RealTimeDataProcessor.RealTimeData realTimeData) {
        try {
            // Update overall summary
            updateOverallSummary(realTimeData);

            // Update area data
            updateAreaData(realTimeData);

            // Update charts
            updateCharts(realTimeData);

        } catch (Exception e) {
            Log.e(TAG, "Error updating UI: " + e.getMessage(), e);
        }
    }

    /**
     * Update overall summary section
     */
    private void updateOverallSummary(RealTimeDataProcessor.RealTimeData realTimeData) {
        if (todaysTotalValue != null) {
            todaysTotalValue.setText(String.format(Locale.getDefault(), "%.2f", realTimeData.totalConsumption));
        }

        if (peakUsageValue != null) {
            peakUsageValue.setText(String.format(Locale.getDefault(), "%.0f", realTimeData.peakWatts));
        }

        if (peakUsageTime != null) {
            if (realTimeData.peakTime != null && !realTimeData.peakTime.isEmpty()) {
                peakUsageTime.setText(formatPeakTime(realTimeData.peakTime));
            } else {
                peakUsageTime.setText("--:--");
            }
        }

        if (estimatedCostValue != null) {
            estimatedCostValue.setText(String.format(Locale.getDefault(), "₱%.2f", realTimeData.totalCost));
        }

        // FIXED: Simple percentage calculation
        if (todaysTotalPercentage != null) {
            calculateConsumptionProgress(realTimeData.totalConsumption); // CHANGED method name
        }

    }

    private void calculateConsumptionProgress(double currentConsumption) {
        try {
            if (!baselineFetched) {
                // First time - fetch yesterday's total for comparison
                fetchYesterdayBaseline(currentConsumption);
                baselineFetched = true;
            } else {
                // Show comparison with yesterday
                displayComparisonWithYesterday(currentConsumption);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error calculating percentage: " + e.getMessage());
            todaysTotalPercentage.setText("--");
        }
    }
    private void fetchYesterdayBaseline(double todayConsumption) {
        try {
            // Calculate yesterday's date in Philippine time
            Calendar yesterday = Calendar.getInstance(TimeZone.getTimeZone("Asia/Manila"));
            yesterday.add(Calendar.DAY_OF_MONTH, -1);
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            dateFormat.setTimeZone(TimeZone.getTimeZone("Asia/Manila"));
            String yesterdayDate = dateFormat.format(yesterday.getTime());

            DatabaseReference db = FirebaseDatabase.getInstance().getReference();
            db.child("daily_summaries").child(yesterdayDate).child("total_kwh")
                    .addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot snapshot) {
                            if (snapshot.exists()) {
                                yesterdayBaselineConsumption = snapshot.getValue(Double.class);
                            } else {
                                yesterdayBaselineConsumption = 0.0; // No yesterday data = 0
                            }

                            Log.d(TAG, "Yesterday baseline fetched: " + yesterdayBaselineConsumption + " kWh");

                            // Now show the comparison
                            runOnUiThread(() -> displayComparisonWithYesterday(todayConsumption));
                        }

                        @Override
                        public void onCancelled(@NonNull DatabaseError error) {
                            Log.w(TAG, "Failed to fetch yesterday baseline: " + error.getMessage());
                            yesterdayBaselineConsumption = 0.0; // Default to 0 if fetch fails
                            runOnUiThread(() -> displayComparisonWithYesterday(todayConsumption));
                        }
                    });

        } catch (Exception e) {
            Log.e(TAG, "Error fetching yesterday baseline: " + e.getMessage());
            yesterdayBaselineConsumption = 0.0;
            displayComparisonWithYesterday(todayConsumption);
        }
    }

    /**
     * Display comparison with yesterday's total
     */
    private void displayComparisonWithYesterday(double todayConsumption) {
        try {
            if (yesterdayBaselineConsumption < 0) {
                // Still loading
                todaysTotalPercentage.setText("Loading...");
                todaysTotalPercentage.setTextColor(getResources().getColor(R.color.brown));
                return;
            }

            double difference = todayConsumption - yesterdayBaselineConsumption;

            String text;
            int color;

            if (Math.abs(difference) < 0.01) {
                text = "Same as yesterday";
                color = getResources().getColor(R.color.brown);
            } else if (difference > 0) {
                text = String.format(Locale.getDefault(), "+%.2f kWh vs yesterday", difference);
                color = getResources().getColor(android.R.color.holo_red_dark); // Red for increase
            } else {
                text = String.format(Locale.getDefault(), "%.2f kWh vs yesterday", difference);
                color = getResources().getColor(android.R.color.holo_green_dark); // Green for decrease
            }

            todaysTotalPercentage.setText(text);
            todaysTotalPercentage.setTextColor(color);

            Log.d(TAG, "Comparison: Today=" + String.format("%.3f", todayConsumption) +
                    ", Yesterday=" + String.format("%.3f", yesterdayBaselineConsumption) +
                    ", Difference=" + String.format("%.3f", difference));

        } catch (Exception e) {
            Log.e(TAG, "Error displaying comparison: " + e.getMessage());
            todaysTotalPercentage.setText("--");
        }
    }





    /**
     * FIXED: Format peak time properly (handles 5 pm, 17:00, etc.)
     */
    private String formatPeakTime(String rawTime) {
        try {
            // If time is already in 12-hour format (like "5 pm"), return as is
            if (rawTime.toLowerCase().contains("am") || rawTime.toLowerCase().contains("pm")) {
                return rawTime;
            }

            // If time is in 24-hour format (like "17:00"), convert to 12-hour
            if (rawTime.contains(":")) {
                String[] parts = rawTime.split(":");
                int hour = Integer.parseInt(parts[0]);
                int minute = Integer.parseInt(parts[1]);

                String ampm = hour >= 12 ? "PM" : "AM";
                int displayHour = hour == 0 ? 12 : (hour > 12 ? hour - 12 : hour);

                return String.format(Locale.getDefault(), "%d:%02d %s", displayHour, minute, ampm);
            }

            // If time is just an hour number (like "17"), convert it
            int hour = Integer.parseInt(rawTime);
            String ampm = hour >= 12 ? "PM" : "AM";
            int displayHour = hour == 0 ? 12 : (hour > 12 ? hour - 12 : hour);

            return String.format(Locale.getDefault(), "%d:00 %s", displayHour, ampm);

        } catch (Exception e) {
            Log.w(TAG, "Could not format time: " + rawTime, e);
            return rawTime; // Return original if formatting fails
        }
    }


    /**
     * Update area-specific data
     */
    private void updateAreaData(RealTimeDataProcessor.RealTimeData realTimeData) {
        // Area 1
        updateSingleAreaData(realTimeData.area1Data,
                area1TotalConsumption, area1EstimatedCost,
                area1PeakConsumption, area1SharePercentage);

        // Area 2
        updateSingleAreaData(realTimeData.area2Data,
                area2TotalConsumption, area2EstimatedCost,
                area2PeakConsumption, area2SharePercentage);

        // Area 3
        updateSingleAreaData(realTimeData.area3Data,
                area3TotalConsumption, area3EstimatedCost,
                area3PeakConsumption, area3SharePercentage);
    }

    /**
     * Update single area data
     */
    private void updateSingleAreaData(RealTimeDataProcessor.AreaData areaData,
                                      TextView consumptionView, TextView costView,
                                      TextView peakView, TextView shareView) {
        if (consumptionView != null) {
            consumptionView.setText(String.format(Locale.getDefault(), "%.3f kWh", areaData.consumption));
        }

        if (costView != null) {
            costView.setText(String.format(Locale.getDefault(), "₱%.2f", areaData.cost));
        }

        if (peakView != null) {
            peakView.setText(String.format(Locale.getDefault(), "%.0f W", areaData.peakWatts));
        }

        if (shareView != null) {
            shareView.setText(String.format(Locale.getDefault(), "%.1f%%", areaData.sharePercentage));
        }
    }

    /**
     * Update charts with hourly data
     */
    private void updateCharts(RealTimeDataProcessor.RealTimeData realTimeData) {
        try {
            Log.d(TAG, "Updating charts with " + realTimeData.hourlyData.size() + " hourly data points");

            // BETTER: Create 12 labels for 2-hour windows
            List<String> hourLabels = new ArrayList<>();
            for (int i = 0; i < 12; i++) {
                int hour = i * 2;
                hourLabels.add(String.format("%02d", hour));
            }
            // Result: "00:00", "02:00", "04:00", "06:00", "08:00", "10:00", "12:00", "14:00", "16:00", "18:00", "20:00", "22:00"

            // Update each chart with 2-hour windows
            if (area1Chart != null) {
                setupTwoHourChart(area1Chart, realTimeData, 1, "Area 1", hourLabels);
            }

            if (area2Chart != null) {
                setupTwoHourChart(area2Chart, realTimeData, 2, "Area 2", hourLabels);
            }

            if (area3Chart != null) {
                setupTwoHourChart(area3Chart, realTimeData, 3, "Area 3", hourLabels);
            }

            Log.d(TAG, "Charts updated successfully with 2-hour accuracy");

        } catch (Exception e) {
            Log.e(TAG, "Error updating charts: " + e.getMessage(), e);
        }
    }

    private void setupTwoHourChart(LineChart chart, RealTimeDataProcessor.RealTimeData realTimeData,
                                   int areaNumber, String areaName, List<String> labels) {
        try {
            if (chart == null) return;

            double areaPercentage = getAreaPercentage(realTimeData, areaNumber);
            Log.d(TAG, "Setting up " + areaName + " with 2-hour windows");

            // Create entries for 12 two-hour windows
            List<Entry> entries = new ArrayList<>();

            for (int windowIndex = 0; windowIndex < 12; windowIndex++) {
                int startHour = windowIndex * 2;
                int endHour = startHour + 1;

                float windowTotal = 0f;
                int dataPointsInWindow = 0;

                // Sum consumption data within this 2-hour window
                for (RealTimeDataProcessor.HourlyData hourlyData : realTimeData.hourlyData) {
                    try {
                        int dataHour;

                        if (hourlyData.hour != null && !hourlyData.hour.isEmpty()) {
                            if (hourlyData.hour.contains(":")) {
                                dataHour = Integer.parseInt(hourlyData.hour.split(":")[0]);
                            } else {
                                dataHour = (int) Double.parseDouble(hourlyData.hour);
                            }
                        } else {
                            continue;
                        }

                        // Check if this data point falls within our 2-hour window
                        if (dataHour >= startHour && dataHour <= endHour) {
                            float areaConsumption = (float) (hourlyData.consumption * areaPercentage);
                            windowTotal += areaConsumption;
                            dataPointsInWindow++;

                            Log.d(TAG, areaName + " - Hour " + dataHour + " -> window " + startHour + "-" + endHour +
                                    " (" + windowIndex + "): " + String.format("%.4f", areaConsumption) + " kWh");
                        }

                    } catch (Exception e) {
                        continue;
                    }
                }

                entries.add(new Entry(windowIndex, Math.max(0f, windowTotal)));

                if (windowTotal > 0) {
                    Log.d(TAG, areaName + " - Window " + startHour + "-" + endHour +
                            " (shows as " + String.format("%02d:00", startHour) + "): " +
                            String.format("%.4f", windowTotal) + " kWh");
                }
            }

            // Create dataset
            LineDataSet dataSet = new LineDataSet(entries, areaName);
            dataSet.setColor(getResources().getColor(R.color.brown));
            dataSet.setCircleColor(getResources().getColor(R.color.brown));
            dataSet.setLineWidth(2f);
            dataSet.setCircleRadius(3f);
            dataSet.setDrawCircleHole(false);
            dataSet.setValueTextSize(0f);
            dataSet.setDrawFilled(true);
            dataSet.setFillColor(getResources().getColor(R.color.brown));
            dataSet.setFillAlpha(50);
            dataSet.setDrawCircles(true);

            LineData lineData = new LineData(dataSet);
            chart.setData(lineData);

            configureDashboardChart(chart, labels);
            chart.invalidate();

        } catch (Exception e) {
            Log.e(TAG, "Error setting up " + areaName + " 2-hour chart: " + e.getMessage(), e);
            createEmptyChart(chart, areaName, getResources().getColor(R.color.brown));
        }
    }


    private void updateAreaLabels(String area1Name, String area2Name, String area3Name) {
        runOnUiThread(() -> {
            TextView area1Label = findViewById(R.id.area1_label);
            TextView area2Label = findViewById(R.id.area2_label);
            TextView area3Label = findViewById(R.id.area3_label);

            if (area1Label != null) area1Label.setText(area1Name);
            if (area2Label != null) area2Label.setText(area2Name);
            if (area3Label != null) area3Label.setText(area3Name);
        });
    }



    private void createEmptyChart(LineChart chart, String chartName, int color) {
        try {
            List<Entry> emptyEntries = new ArrayList<>();
            List<String> emptyLabels = new ArrayList<>();

            // Create 6 empty points (every 4 hours)
            for (int i = 0; i < 6; i++) {
                emptyEntries.add(new Entry(i, 0f));
                emptyLabels.add(String.format("%02d:00", i * 4));
            }

            LineDataSet dataSet = new LineDataSet(emptyEntries, chartName + " (No Data)");
            dataSet.setColor(Color.GRAY);
            dataSet.setLineWidth(1f);
            dataSet.setDrawCircles(false);
            dataSet.setDrawFilled(false);

            LineData lineData = new LineData(dataSet);
            chart.setData(lineData);

            configureDashboardChart(chart, emptyLabels);
            chart.invalidate();

        } catch (Exception e) {
            Log.e(TAG, "Error creating empty chart: " + e.getMessage());
        }
    }


    private double getAreaPercentage(RealTimeDataProcessor.RealTimeData realTimeData, int areaNumber) {
        try {
            double totalConsumption = realTimeData.area1Data.consumption +
                    realTimeData.area2Data.consumption +
                    realTimeData.area3Data.consumption;

            if (totalConsumption <= 0) {
                return 0.33; // Default equal split
            }

            switch (areaNumber) {
                case 1:
                    return realTimeData.area1Data.consumption / totalConsumption;
                case 2:
                    return realTimeData.area2Data.consumption / totalConsumption;
                case 3:
                    return realTimeData.area3Data.consumption / totalConsumption;
                default:
                    return 0.33;
            }
        } catch (Exception e) {
            Log.w(TAG, "Error calculating area percentage: " + e.getMessage());
            return 0.33;
        }
    }


    /**
     * Setup individual area chart
     */
    private void setupSimpleChart(LineChart chart, RealTimeDataProcessor.RealTimeData realTimeData,
                                  int areaNumber, String areaName, List<String> labels) {
        try {
            if (chart == null) return;

            // Get area percentage for this specific area
            double areaPercentage = getAreaPercentage(realTimeData, areaNumber);

            Log.d(TAG, "Setting up " + areaName + " with " + String.format("%.1f%%", areaPercentage * 100) + " share");
            Log.d(TAG, "Total hourly data points available: " + realTimeData.hourlyData.size());

            // DEBUG: Print all available hourly data to understand the structure
            for (int i = 0; i < Math.min(5, realTimeData.hourlyData.size()); i++) {
                RealTimeDataProcessor.HourlyData data = realTimeData.hourlyData.get(i);
                Log.d(TAG, "Sample hourly data " + i + ": hour=" + data.hour + ", consumption=" + data.consumption);
            }

            // Create entries - aggregate data for each 4-hour window
            List<Entry> entries = new ArrayList<>();

            // Define 6 time windows: 0-3, 4-7, 8-11, 12-15, 16-19, 20-23
            for (int windowIndex = 0; windowIndex < 6; windowIndex++) {
                int startHour = windowIndex * 4;
                int endHour = startHour + 3;

                float windowTotal = 0f;
                int dataPointsInWindow = 0;

                // Sum all consumption data within this 4-hour window
                for (RealTimeDataProcessor.HourlyData hourlyData : realTimeData.hourlyData) {
                    try {
                        int dataHour;

                        // Parse hour from the data
                        if (hourlyData.hour != null && !hourlyData.hour.isEmpty()) {
                            // Handle different hour formats
                            if (hourlyData.hour.contains(":")) {
                                // Format like "14:00" or "14:30"
                                dataHour = Integer.parseInt(hourlyData.hour.split(":")[0]);
                            } else {
                                // Format like "14" or "14.0"
                                dataHour = (int) Double.parseDouble(hourlyData.hour);
                            }
                        } else {
                            continue; // Skip if no hour data
                        }

                        // Check if this data point falls within our window
                        if (dataHour >= startHour && dataHour <= endHour) {
                            // Add this area's portion of the consumption
                            float areaConsumption = (float) (hourlyData.consumption * areaPercentage);
                            windowTotal += areaConsumption;
                            dataPointsInWindow++;

                            Log.d(TAG, areaName + " - Hour " + dataHour + " (window " + startHour + "-" + endHour +
                                    "): +" + String.format("%.4f", areaConsumption) + " kWh");
                        }

                    } catch (Exception e) {
                        Log.w(TAG, "Error parsing hour data: " + hourlyData.hour + ", error: " + e.getMessage());
                        continue;
                    }
                }

                // Add the aggregated value for this window
                entries.add(new Entry(windowIndex, Math.max(0f, windowTotal)));

                Log.d(TAG, areaName + " - Window " + startHour + "-" + endHour +
                        ": " + String.format("%.4f", windowTotal) + " kWh (" + dataPointsInWindow + " data points)");
            }

            // If no data found, create empty chart
            if (entries.isEmpty() || entries.stream().allMatch(entry -> entry.getY() == 0f)) {
                Log.w(TAG, "No valid data found for " + areaName + ", creating empty chart");
                createEmptyChart(chart, areaName, getResources().getColor(R.color.brown));
                return;
            }

            // Create dataset with Dashboard styling
            LineDataSet dataSet = new LineDataSet(entries, areaName);
            dataSet.setColor(getResources().getColor(R.color.brown));
            dataSet.setCircleColor(getResources().getColor(R.color.brown));
            dataSet.setLineWidth(2f);
            dataSet.setCircleRadius(3f);
            dataSet.setDrawCircleHole(false);
            dataSet.setValueTextSize(0f);
            dataSet.setDrawFilled(true);
            dataSet.setFillColor(getResources().getColor(R.color.brown));
            dataSet.setFillAlpha(50);
            dataSet.setDrawCircles(true);

            LineData lineData = new LineData(dataSet);
            chart.setData(lineData);

            // Apply Dashboard-style configuration
            configureDashboardChart(chart, labels);

            chart.invalidate();

            // Summary log
            float totalShown = 0f;
            for (Entry entry : entries) {
                totalShown += entry.getY();
            }
            Log.d(TAG, areaName + " chart completed - " + entries.size() + " windows, total consumption shown: " +
                    String.format("%.4f", totalShown) + " kWh");

        } catch (Exception e) {
            Log.e(TAG, "Error setting up " + areaName + " chart: " + e.getMessage(), e);
            createEmptyChart(chart, areaName, getResources().getColor(R.color.brown));
        }
    }




    // New method to apply dashboard-style formatting
    private void configureDashboardChart(LineChart chart, List<String> labels) {
        try {
            // Basic settings (copy from Dashboard)
            chart.getDescription().setEnabled(false);
            chart.setTouchEnabled(true);
            chart.setDragEnabled(true);
            chart.setScaleEnabled(false);
            chart.setPinchZoom(false);
            chart.setDrawGridBackground(false);
            chart.setDrawBorders(false);

            // Disable right axis
            chart.getAxisRight().setEnabled(false);

            // Configure left axis (copy from Dashboard)
            chart.getAxisLeft().setGridColor(getResources().getColor(R.color.brown));
            chart.getAxisLeft().setTextColor(getResources().getColor(R.color.brown));
            chart.getAxisLeft().setDrawGridLines(true);
            chart.getAxisLeft().setAxisMinimum(0f);

            // Configure X-axis (copy from Dashboard)
            XAxis xAxis = chart.getXAxis();
            xAxis.setValueFormatter(new IndexAxisValueFormatter(labels));
            xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
            xAxis.setGranularity(1f);
            xAxis.setTextColor(getResources().getColor(R.color.brown));
            xAxis.setGridColor(getResources().getColor(R.color.brown));
            xAxis.setDrawLabels(true);
            xAxis.setLabelCount(labels.size());

            // Configure legend (copy from Dashboard)
            Legend legend = chart.getLegend();
            legend.setVerticalAlignment(Legend.LegendVerticalAlignment.TOP);
            legend.setForm(Legend.LegendForm.SQUARE);
            legend.setTextColor(getResources().getColor(R.color.brown));
            legend.setEnabled(true);

            // Extra spacing (copy from Dashboard)
            chart.setExtraBottomOffset(10f);

        } catch (Exception e) {
            Log.e(TAG, "Error configuring dashboard chart: " + e.getMessage());
        }
    }


    @Override
    protected void onResume() {
        super.onResume();
        // Start refresh timer
        if (refreshHandler != null && refreshRunnable != null) {
            refreshHandler.post(refreshRunnable);
        }
        // Load fresh data
        loadRealTimeData();
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Stop refresh timer
        if (refreshHandler != null && refreshRunnable != null) {
            refreshHandler.removeCallbacks(refreshRunnable);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Clean up
        if (refreshHandler != null && refreshRunnable != null) {
            refreshHandler.removeCallbacks(refreshRunnable);
        }
    }

}