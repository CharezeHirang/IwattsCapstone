package com.example.sampleiwatts;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

public class HistoricalDataActivity extends AppCompatActivity {

    private static final String TAG = "HistoricalDataActivity";

    // Philippine timezone - same as real-time monitoring
    private static final TimeZone PHILIPPINE_TIMEZONE = TimeZone.getTimeZone("Asia/Manila");

    // Date selection components
    private EditText startDateEdit;
    private EditText endDateEdit;
    private Calendar startDate;
    private Calendar endDate;

    // Date formatters with Philippine timezone
    private SimpleDateFormat displayFormat;
    private SimpleDateFormat firebaseFormat;

    // Summary components
    private TextView selectedTotalConsumption;
    private TextView selectedDailyAverage;
    private TextView selectedTotalCost;
    private TextView selectedPeakDay;

    // Comparison components
    private TextView previousDateRange;
    private TextView previousTotalConsumption;
    private TextView previousTotalCost;
    private TextView selectedDateRange;
    private TextView selectedDateRangeTotalConsumption;
    private TextView selectedDateRangeTotalCost;

    // Chart components
    private FrameLayout mainChartContainer;
    private LineChart mainChart;

    // Area chart components
    private FrameLayout area1ChartContainer;
    private FrameLayout area2ChartContainer;
    private FrameLayout area3ChartContainer;
    private LineChart area1Chart;
    private LineChart area2Chart;
    private LineChart area3Chart;
    private SimpleDateFormat chartFormat;

    // Area data components
    private TextView area1TotalConsumption;
    private TextView area1EstimatedCost;
    private TextView area1PeakConsumption;
    private TextView area1SharePercentage;

    private TextView area2TotalConsumption;
    private TextView area2EstimatedCost;
    private TextView area2PeakConsumption;
    private TextView area2SharePercentage;

    private TextView area3TotalConsumption;
    private TextView area3EstimatedCost;
    private TextView area3PeakConsumption;
    private TextView area3SharePercentage;
    private ImageView closeDaily;
    private ScrollView graph_scroll;
    private ScrollView report_scroll;
    private CardView consumption_card;
    // Data storage
    private Map<String, List<Map<String, Object>>> historicalData; // key "days" -> list of per-day maps
    private double electricityRate = 9.85; // Default BATELEC II rate
    
    // Area names storage
    private String area1Name = "Area 1";
    private String area2Name = "Area 2";
    private String area3Name = "Area 3";
    
    // Area name history storage
    private Map<String, List<Map<String, Object>>> areaNameHistory = new HashMap<>();

    private NavigationDrawerHelper drawerHelper;

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_historical_data);

        // Enable safe swipe navigation (root content view)
        try {
            View root = findViewById(android.R.id.content);
            com.example.sampleiwatts.managers.SwipeNavigationManager.enableSwipeNavigation(this, root);
            View scroll = findViewById(R.id.graph_scroll);
            if (scroll == null) scroll = findViewById(R.id.report_scroll);
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

        // Set up bottom navigation
        LinearLayout buttonLayout = findViewById(R.id.button);
        ButtonNavigator.setupButtons(this, buttonLayout);

        initializeViews();
        initializeDateFormatters();
        initializeDates();
        setupDatePickers();
        setupChartContainers();
        fetchAreaNamesAndHistory();
        loadHistoricalData();

        closeDaily = findViewById(R.id.closeDaily);
        graph_scroll = findViewById(R.id.graph_scroll);
        report_scroll = findViewById(R.id.report_scroll);
        consumption_card = findViewById(R.id.consumption_card);

        consumption_card.setOnClickListener(v -> {
            if (graph_scroll.getVisibility() == View.GONE) {
                graph_scroll.setVisibility(View.VISIBLE);
            } else {
                graph_scroll.setVisibility(View.GONE);
            }
        });

        closeDaily.setOnClickListener(v -> {
            graph_scroll.setVisibility(View.GONE);
        });

        // Report card opens report_scroll modal
        CardView reportCard = findViewById(R.id.report_card);
        if (reportCard != null) {
            reportCard.setOnClickListener(v -> {
                populateReportCard();
                report_scroll.setVisibility(View.VISIBLE);
            });
        }

        TextView closeReport = findViewById(R.id.closeReport);
        if (closeReport != null) {
            closeReport.setOnClickListener(v -> {
                report_scroll.setVisibility(View.GONE);
            });
        }
        loadHistoricalData();



        Log.d(TAG, "HistoricalDataActivity created with Philippine timezone");
    }

    /**
     * Initialize all UI components
     */
    private void initializeViews() {
        // Date selection
        startDateEdit = findViewById(R.id.startdate);
        endDateEdit = findViewById(R.id.enddate);

        // Summary views
        selectedTotalConsumption = findViewById(R.id.selected_total_consumption);
        selectedDailyAverage = findViewById(R.id.selected_daily_average);
        selectedTotalCost = findViewById(R.id.selected_total_cost);
        selectedPeakDay = findViewById(R.id.selected_peak_day);

        // Comparison views
        previousDateRange = findViewById(R.id.previousdaterange);
        previousTotalConsumption = findViewById(R.id.previoustotalconsumption);
        previousTotalCost = findViewById(R.id.previoustotalcost);
        selectedDateRange = findViewById(R.id.selecteddaterange);
        selectedDateRangeTotalConsumption = findViewById(R.id.selecteddaterangetotalconsumption);
        selectedDateRangeTotalCost = findViewById(R.id.selecteddaterangetotalcost);

        // Chart containers
        mainChartContainer = findViewById(R.id.selecteddaterange_chart_container);
        area1ChartContainer = findViewById(R.id.area1_chart_container);
        area2ChartContainer = findViewById(R.id.area2_chart_container);
        area3ChartContainer = findViewById(R.id.area3_chart_container);

        // Area data views
        area1TotalConsumption = findViewById(R.id.area1_total_consumption);
        area1EstimatedCost = findViewById(R.id.area1_estimated_cost);
        area1PeakConsumption = findViewById(R.id.area1_peak_consumption);
        area1SharePercentage = findViewById(R.id.area1_share_percentage);

        area2TotalConsumption = findViewById(R.id.area2_total_consumption);
        area2EstimatedCost = findViewById(R.id.area2_estimated_cost);
        area2PeakConsumption = findViewById(R.id.area2_peak_consumption);
        area2SharePercentage = findViewById(R.id.area2_share_percentage);

        area3TotalConsumption = findViewById(R.id.area3_total_consumption);
        area3EstimatedCost = findViewById(R.id.area3_estimated_cost);
        area3PeakConsumption = findViewById(R.id.area3_peak_consumption);
        area3SharePercentage = findViewById(R.id.area3_share_percentage);

        // Initialize data storage
        historicalData = new HashMap<>();

        Log.d(TAG, "Views initialized successfully");
    }




    /**
     * Fetch area names and name history from Firebase system_settings
     */
    private void fetchAreaNamesAndHistory() {
        try {
            DatabaseReference systemSettingsRef = FirebaseDatabase.getInstance()
                    .getReference("system_settings");

            systemSettingsRef.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    // Fetch current area names
                    String fetchedArea1Name = snapshot.child("area1_name").getValue(String.class);
                    String fetchedArea2Name = snapshot.child("area2_name").getValue(String.class);
                    String fetchedArea3Name = snapshot.child("area3_name").getValue(String.class);

                    // Use fetched names or keep defaults
                    if (fetchedArea1Name != null && !fetchedArea1Name.trim().isEmpty()) {
                        area1Name = fetchedArea1Name;
                    }
                    if (fetchedArea2Name != null && !fetchedArea2Name.trim().isEmpty()) {
                        area2Name = fetchedArea2Name;
                    }
                    if (fetchedArea3Name != null && !fetchedArea3Name.trim().isEmpty()) {
                        area3Name = fetchedArea3Name;
                    }

                    // Fetch name history
                    DataSnapshot nameSnapshot = snapshot.child("name");
                    if (nameSnapshot.exists()) {
                        processNameHistory(nameSnapshot);
                    }

                    Log.d(TAG, "Area names loaded: " + area1Name + ", " + area2Name + ", " + area3Name);
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    Log.w(TAG, "Failed to fetch area names: " + error.getMessage());
                    // Keep default names
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error fetching area names", e);
        }
    }

    /**
     * Process name history data from Firebase
     */
    private void processNameHistory(DataSnapshot nameSnapshot) {
        try {
            areaNameHistory.clear();
            
            // Process each area's name history
            String[] areaKeys = {"area1_history", "area2_history", "area3_history"};
            
            for (String areaKey : areaKeys) {
                DataSnapshot areaHistorySnapshot = nameSnapshot.child(areaKey);
                List<Map<String, Object>> historyList = new ArrayList<>();
                
                if (areaHistorySnapshot.exists()) {
                    for (DataSnapshot historyEntry : areaHistorySnapshot.getChildren()) {
                        Map<String, Object> entry = new HashMap<>();
                        entry.put("timestamp", historyEntry.child("timestamp").getValue(String.class));
                        entry.put("name", historyEntry.child("name").getValue(String.class));
                        entry.put("previous_name", historyEntry.child("previous_name").getValue(String.class));
                        historyList.add(entry);
                    }
                }
                
                areaNameHistory.put(areaKey, historyList);
            }
            
            Log.d(TAG, "Name history loaded for " + areaNameHistory.size() + " areas");
        } catch (Exception e) {
            Log.e(TAG, "Error processing name history", e);
        }
    }

    /**
     * Get the area name that was active on a specific date
     */
    private String getAreaNameForDate(int areaNumber, String date) {
        try {
            String historyKey = "area" + areaNumber + "_history";
            List<Map<String, Object>> historyList = areaNameHistory.get(historyKey);
            
            if (historyList == null || historyList.isEmpty()) {
                // No history, return current name
                switch (areaNumber) {
                    case 1: return area1Name;
                    case 2: return area2Name;
                    case 3: return area3Name;
                    default: return "Area " + areaNumber;
                }
            }
            
            // Parse the target date
            Date targetDate = firebaseFormat.parse(date);
            if (targetDate == null) {
                return "Area " + areaNumber;
            }
            
            // Find the most recent name change before or on the target date
            String activeName = null;
            for (Map<String, Object> entry : historyList) {
                String timestamp = (String) entry.get("timestamp");
                if (timestamp != null) {
                    try {
                        // Parse timestamp (assuming format like "2024-09-20 14:30:00")
                        SimpleDateFormat timestampFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
                        timestampFormat.setTimeZone(PHILIPPINE_TIMEZONE);
                        Date changeDate = timestampFormat.parse(timestamp);
                        
                        if (changeDate != null && !changeDate.after(targetDate)) {
                            activeName = (String) entry.get("name");
                        }
                    } catch (Exception e) {
                        Log.w(TAG, "Error parsing timestamp: " + timestamp);
                    }
                }
            }
            
            // If no history entry found, use the previous name from the first entry
            if (activeName == null && !historyList.isEmpty()) {
                Map<String, Object> firstEntry = historyList.get(0);
                activeName = (String) firstEntry.get("previous_name");
            }
            
            // Fallback to current name if still null
            if (activeName == null || activeName.trim().isEmpty()) {
                switch (areaNumber) {
                    case 1: return area1Name;
                    case 2: return area2Name;
                    case 3: return area3Name;
                    default: return "Area " + areaNumber;
                }
            }
            
            return activeName;
            
        } catch (Exception e) {
            Log.e(TAG, "Error getting area name for date " + date + ": " + e.getMessage());
            // Fallback to current name
            switch (areaNumber) {
                case 1: return area1Name;
                case 2: return area2Name;
                case 3: return area3Name;
                default: return "Area " + areaNumber;
            }
        }
    }

    /**
     * Initialize default date range (last 7 days) using Philippine timezone
     */
    private void initializeDates() {
        // Initialize calendars with Philippine timezone
        startDate = Calendar.getInstance(PHILIPPINE_TIMEZONE);
        endDate = Calendar.getInstance(PHILIPPINE_TIMEZONE);

        // Set default range to last 7 days
        startDate.add(Calendar.DAY_OF_MONTH, -7);

        updateDateDisplays();

        Log.d(TAG, "Dates initialized - Start: " + firebaseFormat.format(startDate.getTime()) +
                ", End: " + firebaseFormat.format(endDate.getTime()));
    }

    /**
     * Setup date picker dialogs
     */
    private void setupDatePickers() {
        startDateEdit.setOnClickListener(v -> showDatePicker(true));
        endDateEdit.setOnClickListener(v -> showDatePicker(false));
    }

    /**
     * Show date picker dialog with Philippine timezone consideration
     */
    private void showDatePicker(boolean isStartDate) {
        Calendar calendar = isStartDate ? startDate : endDate;

        DatePickerDialog datePickerDialog = new DatePickerDialog(
                this,
                (view, year, month, dayOfMonth) -> {
                    // Set the date in Philippine timezone
                    calendar.setTimeZone(PHILIPPINE_TIMEZONE);
                    calendar.set(Calendar.YEAR, year);
                    calendar.set(Calendar.MONTH, month);
                    calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                    calendar.set(Calendar.HOUR_OF_DAY, 0);
                    calendar.set(Calendar.MINUTE, 0);
                    calendar.set(Calendar.SECOND, 0);
                    calendar.set(Calendar.MILLISECOND, 0);

                    // Validate date range
                    if (startDate.after(endDate)) {
                        Toast.makeText(this, "Start date cannot be after end date", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    updateDateDisplays();
                    loadHistoricalData();

                    Log.d(TAG, "Date selected - " + (isStartDate ? "Start" : "End") +
                            ": " + firebaseFormat.format(calendar.getTime()));
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
        );

        datePickerDialog.show();
    }

    /**
     * Update date display fields using Philippine timezone
     */
    private void updateDateDisplays() {
        startDateEdit.setText(displayFormat.format(startDate.getTime()));
        endDateEdit.setText(displayFormat.format(endDate.getTime()));

        // Update comparison date ranges
        String selectedRange = displayFormat.format(startDate.getTime()) + " - " + displayFormat.format(endDate.getTime());
        selectedDateRange.setText(selectedRange);

        // Calculate previous period using Philippine timezone
        long daysDiff = (endDate.getTimeInMillis() - startDate.getTimeInMillis()) / (1000 * 60 * 60 * 24);
        Calendar prevStart = Calendar.getInstance(PHILIPPINE_TIMEZONE);
        Calendar prevEnd = Calendar.getInstance(PHILIPPINE_TIMEZONE);

        prevStart.setTime(startDate.getTime());
        prevEnd.setTime(endDate.getTime());
        prevStart.add(Calendar.DAY_OF_MONTH, -(int)daysDiff - 1);
        prevEnd.add(Calendar.DAY_OF_MONTH, -(int)daysDiff - 1);

        String previousRange = displayFormat.format(prevStart.getTime()) + " - " + displayFormat.format(prevEnd.getTime());
        previousDateRange.setText(previousRange);

        Log.d(TAG, "Date displays updated - Selected: " + selectedRange + ", Previous: " + previousRange);
    }

    private void initializeDateFormatters() {
        // Display format for UI (MM/dd/yyyy)
        displayFormat = new SimpleDateFormat("MM/dd/yyyy", Locale.getDefault());
        displayFormat.setTimeZone(PHILIPPINE_TIMEZONE);

        // Firebase format for database queries (yyyy-MM-dd)
        firebaseFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        firebaseFormat.setTimeZone(PHILIPPINE_TIMEZONE);

        // ADD THIS: Chart format without year (MMM dd)
        chartFormat = new SimpleDateFormat("MMM dd", Locale.getDefault());
        chartFormat.setTimeZone(PHILIPPINE_TIMEZONE);

        Log.d(TAG, "Date formatters initialized with Philippine timezone: " + PHILIPPINE_TIMEZONE.getDisplayName());
    }


    /**
     * Setup LineChart instances in FrameLayout containers
     */
    private void setupChartContainers() {
        try {
            // Create main consumption chart
            if (mainChartContainer != null) {
                mainChart = new LineChart(this);
                mainChartContainer.removeAllViews();
                mainChartContainer.addView(mainChart);
            }

            // Create area charts
            if (area1ChartContainer != null) {
                area1Chart = new LineChart(this);
                area1ChartContainer.removeAllViews();
                area1ChartContainer.addView(area1Chart);
            }

            if (area2ChartContainer != null) {
                area2Chart = new LineChart(this);
                area2ChartContainer.removeAllViews();
                area2ChartContainer.addView(area2Chart);
            }

            if (area3ChartContainer != null) {
                area3Chart = new LineChart(this);
                area3ChartContainer.removeAllViews();
                area3ChartContainer.addView(area3Chart);
            }

            Log.d(TAG, "Chart containers setup successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error setting up chart containers", e);
        }
    }

    /**
     * FIXED: Load historical data using correct query method
     */
    private void loadHistoricalData() {
        try {
            DatabaseReference summariesRef = FirebaseDatabase.getInstance().getReference("daily_summaries");

            // Format dates for Firebase query using Philippine timezone
            String startDateStr = firebaseFormat.format(startDate.getTime());
            String endDateStr = firebaseFormat.format(endDate.getTime());

            Log.d(TAG, "Loading historical data from " + startDateStr + " to " + endDateStr);

            // FIXED: Query all daily summaries, then filter by date range
            summariesRef.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                    processHistoricalData(dataSnapshot, startDateStr, endDateStr);
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    Log.e(TAG, "Failed to load historical data: " + error.getMessage());
                    Toast.makeText(HistoricalDataActivity.this,
                            "Failed to load historical data", Toast.LENGTH_SHORT).show();
                }
            });

        } catch (Exception e) {
            Log.e(TAG, "Error loading historical data", e);
        }
    }

    /**
     * FIXED: Process historical data with correct field names and date filtering
     */
    private void processHistoricalData(DataSnapshot dataSnapshot, String startDateStr, String endDateStr) {
        try {
            historicalData.clear();

            double totalConsumption = 0.0;
            double totalCost = 0.0;
            String peakDay = "";
            double peakConsumption = 0.0;
            double maxDailyPeak = 0.0;
            int dayCount = 0;

            // Area totals and peaks
            double area1Total = 0.0, area2Total = 0.0, area3Total = 0.0;
            double area1MaxPeak = 0.0, area2MaxPeak = 0.0, area3MaxPeak = 0.0;

            double area1TotalCost = 0.0;
            double area2TotalCost = 0.0;
            double area3TotalCost = 0.0;

            // Daily data for charts
            List<Double> dailyConsumption = new ArrayList<>();
            List<String> dateLabels = new ArrayList<>();
            Map<String, List<Double>> areaDailyData = new HashMap<>();
            areaDailyData.put("area1", new ArrayList<>());
            areaDailyData.put("area2", new ArrayList<>());
            areaDailyData.put("area3", new ArrayList<>());

            // Prepare container for day-by-day report
            List<Map<String, Object>> daysList = new ArrayList<>();

            for (DataSnapshot daySnapshot : dataSnapshot.getChildren()) {
                try {
                    String dateKey = daySnapshot.getKey();

                    // FIXED: Filter by date range using string comparison
                    if (dateKey != null && dateKey.compareTo(startDateStr) >= 0 && dateKey.compareTo(endDateStr) <= 0) {

                        Map<String, Object> dayData = (Map<String, Object>) daySnapshot.getValue();
                        if (dayData == null) continue;

                        // FIXED: Use correct field names from your database structure
                        Double dayConsumption = getDoubleValue(dayData.get("total_kwh"));     // NOT total_energy_kwh
                        Double dayCost = getDoubleValue(dayData.get("total_cost"));          // NOT total_cost_php
                        Double dayPeak = getDoubleValue(dayData.get("peak_watts"));
                        String peakTime = (String) dayData.get("peak_time");

                        if (dayConsumption != null) {
                            totalConsumption += dayConsumption;
                            dailyConsumption.add(dayConsumption);
                            try {
                                Date date = firebaseFormat.parse(dateKey);
                                String formattedDate = chartFormat.format(date); // "Jan 15"
                                dateLabels.add(formattedDate);
                            } catch (Exception e) {
                                Log.w(TAG, "Error formatting date: " + dateKey);
                                dateLabels.add(dateKey); // Fallback to original
                            }
                            dayCount++;

                            // Track peak day by consumption
                            if (dayConsumption > peakConsumption) {
                                peakConsumption = dayConsumption;
                                peakDay = dateKey;
                            }
                        }

                        if (dayCost != null) {
                            totalCost += dayCost;
                        }

                        // Track highest daily peak watts
                        if (dayPeak != null && dayPeak > maxDailyPeak) {
                            maxDailyPeak = dayPeak;
                        }

                        // FIXED: Process area data from area_breakdown (not area_data)
                        Map<String, Object> areaBreakdown = (Map<String, Object>) dayData.get("area_breakdown");
                        if (areaBreakdown != null) {
                            // Get area consumption values
                            double area1Kwh = getAreaConsumption(areaBreakdown, "area1");
                            double area2Kwh = getAreaConsumption(areaBreakdown, "area2");
                            double area3Kwh = getAreaConsumption(areaBreakdown, "area3");

                            area1Total += area1Kwh;
                            area2Total += area2Kwh;
                            area3Total += area3Kwh;

                            Map<String, Object> area1Data = (Map<String, Object>) areaBreakdown.get("area1");
                            if (area1Data != null) {
                                Double area1Cost = getDoubleValue(area1Data.get("cost"));
                                if (area1Cost != null) area1TotalCost += area1Cost;
                            }

                            Map<String, Object> area2Data = (Map<String, Object>) areaBreakdown.get("area2");
                            if (area2Data != null) {
                                Double area2Cost = getDoubleValue(area2Data.get("cost"));
                                if (area2Cost != null) area2TotalCost += area2Cost;
                            }

                            Map<String, Object> area3Data = (Map<String, Object>) areaBreakdown.get("area3");
                            if (area3Data != null) {
                                Double area3Cost = getDoubleValue(area3Data.get("cost"));
                                if (area3Cost != null) area3TotalCost += area3Cost;
                            }

                            // FIXED: Calculate proportional peaks for this day
                            if (dayPeak != null && dayConsumption != null && dayConsumption > 0) {
                                double area1Peak = dayPeak * (area1Kwh / dayConsumption);
                                double area2Peak = dayPeak * (area2Kwh / dayConsumption);
                                double area3Peak = dayPeak * (area3Kwh / dayConsumption);

                                // Track maximum peaks across all days
                                area1MaxPeak = Math.max(area1MaxPeak, area1Peak);
                                area2MaxPeak = Math.max(area2MaxPeak, area2Peak);
                                area3MaxPeak = Math.max(area3MaxPeak, area3Peak);
                            }

                            // Process hourly data for charts
                            processAreaDailyData(areaDailyData, "area1", area1Kwh);
                            processAreaDailyData(areaDailyData, "area2", area2Kwh);
                            processAreaDailyData(areaDailyData, "area3", area3Kwh);
                        }

                        // Build compact per-day record for report card
                        Map<String, Object> reportItem = new HashMap<>();
                        reportItem.put("date", dateKey);
                        reportItem.put("kwh", dayConsumption != null ? dayConsumption : 0.0);
                        reportItem.put("cost", dayCost != null ? dayCost : 0.0);
                        reportItem.put("peak_watts", dayPeak != null ? dayPeak : 0.0);
                        reportItem.put("peak_time", peakTime != null ? peakTime : "--:--");
                        
                        // Store area breakdown data for this day
                        Map<String, Object> areaBreakdownCopy = new HashMap<>();
                        if (areaBreakdown != null) {
                            double area1Kwh = getAreaConsumption(areaBreakdown, "area1");
                            double area2Kwh = getAreaConsumption(areaBreakdown, "area2");
                            double area3Kwh = getAreaConsumption(areaBreakdown, "area3");
                            
                            areaBreakdownCopy.put("area1", area1Kwh);
                            areaBreakdownCopy.put("area2", area2Kwh);
                            areaBreakdownCopy.put("area3", area3Kwh);
                            
                            Log.d(TAG, "Storing area data for " + dateKey + ": A1=" + area1Kwh + ", A2=" + area2Kwh + ", A3=" + area3Kwh);
                        } else {
                            Log.w(TAG, "No area breakdown data for " + dateKey);
                        }
                        reportItem.put("areas", areaBreakdownCopy);
                        daysList.add(reportItem);
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Error processing day data for " + daySnapshot.getKey() + ": " + e.getMessage());
                }
            }

            Log.d(TAG, String.format("Processed %d days of historical data", dayCount));

            // Store day list for report
            historicalData.put("days", daysList);

            // Update UI with processed data
            updateSummaryDisplay(totalConsumption, totalCost, peakDay, dayCount);
            updateAreaDisplays(area1Total, area2Total, area3Total, totalConsumption,
                    area1MaxPeak, area2MaxPeak, area3MaxPeak,
                    area1TotalCost, area2TotalCost, area3TotalCost);
            updateCharts(dailyConsumption, dateLabels, areaDailyData);

            // Load previous period for comparison
            loadPreviousPeriodData();

        } catch (Exception e) {
            Log.e(TAG, "Error processing historical data", e);
        }
    }

    /**
     * FIXED: Get area consumption with correct field names
     */
    private double getAreaConsumption(Map<String, Object> areaBreakdown, String areaKey) {
        try {
            Map<String, Object> areaData = (Map<String, Object>) areaBreakdown.get(areaKey);
            if (areaData != null) {
                // FIXED: Use 'kwh' field (not 'total_energy_kwh')
                Double consumption = getDoubleValue(areaData.get("kwh"));
                if (consumption != null) {
                    return consumption;
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Error getting area consumption for " + areaKey + ": " + e.getMessage());
        }
        return 0.0;
    }

    /**
     * Process area hourly data for charts
     */
    private void processAreaDailyData(Map<String, List<Double>> areaDailyData, String areaKey, double dailyConsumption) {
        List<Double> dailyList = areaDailyData.get(areaKey);
        if (dailyList != null) {
            dailyList.add(dailyConsumption);  // Simply add the daily consumption
        }
    }

    /**
     * Load previous period data for comparison using Philippine timezone
     */
    private void loadPreviousPeriodData() {
        try {
            long daysDiff = (endDate.getTimeInMillis() - startDate.getTimeInMillis()) / (1000 * 60 * 60 * 24);
            Calendar prevStart = Calendar.getInstance(PHILIPPINE_TIMEZONE);
            Calendar prevEnd = Calendar.getInstance(PHILIPPINE_TIMEZONE);

            prevStart.setTime(startDate.getTime());
            prevEnd.setTime(endDate.getTime());
            prevStart.add(Calendar.DAY_OF_MONTH, -(int)daysDiff - 1);
            prevEnd.add(Calendar.DAY_OF_MONTH, -(int)daysDiff - 1);

            String prevStartStr = firebaseFormat.format(prevStart.getTime());
            String prevEndStr = firebaseFormat.format(prevEnd.getTime());

            Log.d(TAG, "Loading previous period: " + prevStartStr + " to " + prevEndStr);

            DatabaseReference summariesRef = FirebaseDatabase.getInstance().getReference("daily_summaries");
            summariesRef.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                    processPreviousPeriodData(dataSnapshot, prevStartStr, prevEndStr);
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    Log.w(TAG, "Failed to load previous period data: " + error.getMessage());
                }
            });

        } catch (Exception e) {
            Log.e(TAG, "Error loading previous period data", e);
        }
    }

    /**
     * FIXED: Process previous period data with correct field names
     */
    private void processPreviousPeriodData(DataSnapshot dataSnapshot, String prevStartStr, String prevEndStr) {
        try {
            double prevTotalConsumption = 0.0;
            double prevTotalCost = 0.0;

            for (DataSnapshot daySnapshot : dataSnapshot.getChildren()) {
                String dateKey = daySnapshot.getKey();

                if (dateKey != null && dateKey.compareTo(prevStartStr) >= 0 && dateKey.compareTo(prevEndStr) <= 0) {
                    Map<String, Object> dayData = (Map<String, Object>) daySnapshot.getValue();
                    if (dayData != null) {
                        // FIXED: Use correct field names
                        Double dayConsumption = getDoubleValue(dayData.get("total_kwh"));
                        Double dayCost = getDoubleValue(dayData.get("total_cost"));

                        if (dayConsumption != null) {
                            prevTotalConsumption += dayConsumption;
                        }
                        if (dayCost != null) {
                            prevTotalCost += dayCost;
                        }
                    }
                }
            }

            updateComparisonDisplay(prevTotalConsumption, prevTotalCost);

        } catch (Exception e) {
            Log.e(TAG, "Error processing previous period data", e);
        }
    }

    /**
     * Update summary display with processed data
     */
    private void updateSummaryDisplay(double totalConsumption, double totalCost, String peakDay, int dayCount) {
        try {
            selectedTotalConsumption.setText(String.format(Locale.getDefault(), "%.2f", totalConsumption));
            selectedTotalCost.setText(String.format(Locale.getDefault(), "₱ %.2f", totalCost));

            if (dayCount > 0) {
                double dailyAvg = totalConsumption / dayCount;
                selectedDailyAverage.setText(String.format(Locale.getDefault(), "%.2f", dailyAvg));
            } else {
                selectedDailyAverage.setText("0.0");
            }

            if (!peakDay.isEmpty()) {
                selectedPeakDay.setText(peakDay);
            } else {
                selectedPeakDay.setText("--");
            }

            // Update comparison section (selected period)
            selectedDateRangeTotalConsumption.setText(String.format(Locale.getDefault(), "%.2f kwh", totalConsumption));
            selectedDateRangeTotalCost.setText(String.format(Locale.getDefault(), "₱ %.2f", totalCost));

        } catch (Exception e) {
            Log.e(TAG, "Error updating summary display", e);
        }
    }

    /**
     * FIXED: Update area displays with proper peak calculations
     */
    private void updateAreaDisplays(double area1Total, double area2Total, double area3Total,
                                    double grandTotal, double area1Peak, double area2Peak, double area3Peak,
                                    double area1TotalCost, double area2TotalCost, double area3TotalCost) {
        try {
            // Calculate percentages
            double area1Percentage = grandTotal > 0 ? (area1Total / grandTotal) * 100 : 0;
            double area2Percentage = grandTotal > 0 ? (area2Total / grandTotal) * 100 : 0;
            double area3Percentage = grandTotal > 0 ? (area3Total / grandTotal) * 100 : 0;

            // Update Area 1
            area1TotalConsumption.setText(String.format(Locale.getDefault(), "%.2f kWh", area1Total));
            area1EstimatedCost.setText(String.format(Locale.getDefault(), "₱%.2f", area1TotalCost));
            area1SharePercentage.setText(String.format(Locale.getDefault(), "%.1f%%", area1Percentage));
            area1PeakConsumption.setText(String.format(Locale.getDefault(), "%.0f W", area1Peak));

            // Update Area 2
            area2TotalConsumption.setText(String.format(Locale.getDefault(), "%.2f kWh", area2Total));
            area2EstimatedCost.setText(String.format(Locale.getDefault(), "₱%.2f", area2TotalCost));
            area2SharePercentage.setText(String.format(Locale.getDefault(), "%.1f%%", area2Percentage));
            area2PeakConsumption.setText(String.format(Locale.getDefault(), "%.0f W", area2Peak));

            // Update Area 3
            area3TotalConsumption.setText(String.format(Locale.getDefault(), "%.2f kWh", area3Total));
            area3EstimatedCost.setText(String.format(Locale.getDefault(), "₱%.2f", area3TotalCost));
            area3SharePercentage.setText(String.format(Locale.getDefault(), "%.1f%%", area3Percentage));
            area3PeakConsumption.setText(String.format(Locale.getDefault(), "%.0f W", area3Peak));

            // Verify peak calculations add up
            double calculatedTotal = area1Peak + area2Peak + area3Peak;
            Log.d(TAG, String.format("Historical area peaks: %.0f + %.0f + %.0f = %.0f W",
                    area1Peak, area2Peak, area3Peak, calculatedTotal));

        } catch (Exception e) {
            Log.e(TAG, "Error updating area displays", e);
        }
    }

    /**
     * Update comparison display with previous period data
     */
    private void updateComparisonDisplay(double prevTotalConsumption, double prevTotalCost) {
        try {
            previousTotalConsumption.setText(String.format(Locale.getDefault(), "%.2f kwh", prevTotalConsumption));
            previousTotalCost.setText(String.format(Locale.getDefault(), "₱ %.2f", prevTotalCost));
        } catch (Exception e) {
            Log.e(TAG, "Error updating comparison display", e);
        }
    }

    /**
     * Update all charts with historical data
     */
    private void updateCharts(List<Double> dailyConsumption, List<String> dateLabels,
                              Map<String, List<Double>> areaDailyData) {
        try {
            // Update main consumption chart
            if (mainChart != null && !dailyConsumption.isEmpty()) {
                setupDailyChart(mainChart, dailyConsumption, dateLabels, "Daily Consumption", Color.rgb(134, 59, 23));
            }

            // Update area charts with 24-hour patterns
            if (area1Chart != null) {
                List<Double> area1Data = areaDailyData.get("area1");  // ✅ Renamed
                if (area1Data != null && !area1Data.isEmpty()) {
                    setupAreaDailyChart(area1Chart, area1Data, dateLabels, area1Name, Color.rgb(255, 193, 7));  // ✅ Renamed method
                }
            }

            if (area2Chart != null) {
                List<Double> area2Data = areaDailyData.get("area2");  // ✅ Renamed
                if (area2Data != null && !area2Data.isEmpty()) {
                    setupAreaDailyChart(area2Chart, area2Data, dateLabels, area2Name, Color.rgb(220, 53, 69));  // ✅ Renamed method
                }
            }

            if (area3Chart != null) {
                List<Double> area3Data = areaDailyData.get("area3");  // ✅ Renamed
                if (area3Data != null && !area3Data.isEmpty()) {
                    setupAreaDailyChart(area3Chart, area3Data, dateLabels, area3Name, Color.rgb(40, 167, 69));  // ✅ Renamed method
                }
            }

        } catch (Exception e) {
            Log.e(TAG, "Error updating charts", e);
        }
    }

    /**
     * Setup daily consumption chart
     */
    private void setupDailyChart(LineChart chart, List<Double> data, List<String> labels, String chartName, int color) {
        try {
            List<Entry> entries = new ArrayList<>();
            for (int i = 0; i < data.size(); i++) {
                entries.add(new Entry(i, data.get(i).floatValue()));
            }

            LineDataSet dataSet = new LineDataSet(entries, chartName);
            dataSet.setColor(color);
            dataSet.setCircleColor(color);
            dataSet.setLineWidth(2f);

            // FIXED: Scale circle size based on data density
            if (data.size() <= 7) {
                dataSet.setCircleRadius(4f); // Larger circles for few points
            } else if (data.size() <= 14) {
                dataSet.setCircleRadius(3f); // Medium circles
            } else {
                dataSet.setCircleRadius(2f); // Smaller circles for many points
            }

            dataSet.setDrawCircleHole(false);
            dataSet.setValueTextSize(0f);
            dataSet.setDrawFilled(true);
            dataSet.setFillColor(color);
            dataSet.setFillAlpha(50);

            LineData lineData = new LineData(dataSet);
            chart.setData(lineData);

            configureChart(chart, labels.toArray(new String[0]));

        } catch (Exception e) {
            Log.e(TAG, "Error setting up daily chart", e);
        }
    }


    /**
     * Setup 24-hour area chart
     */
    private void setupAreaDailyChart(LineChart chart, List<Double> dailyAreaData, List<String> dateLabels, String areaName, int color) {
        try {
            List<Entry> entries = new ArrayList<>();
            for (int day = 0; day < dailyAreaData.size(); day++) {
                entries.add(new Entry(day, dailyAreaData.get(day).floatValue()));
            }

            LineDataSet dataSet = new LineDataSet(entries, areaName + " Daily Usage");
            dataSet.setColor(color);
            dataSet.setCircleColor(color);
            dataSet.setLineWidth(2f);

            // FIXED: Scale circle size based on data density
            if (dailyAreaData.size() <= 7) {
                dataSet.setCircleRadius(3f);
            } else if (dailyAreaData.size() <= 14) {
                dataSet.setCircleRadius(2f);
            } else {
                dataSet.setCircleRadius(1.5f);
            }

            dataSet.setDrawCircleHole(false);
            dataSet.setValueTextSize(0f);
            dataSet.setDrawFilled(true);
            dataSet.setFillColor(color);
            dataSet.setFillAlpha(50);

            LineData lineData = new LineData(dataSet);
            chart.setData(lineData);

            configureChart(chart, dateLabels.toArray(new String[0]));

        } catch (Exception e) {
            Log.e(TAG, "Error setting up area daily chart", e);
        }
    }

    /**
     * Configure chart appearance
     */
    private void configureChart(LineChart chart, String[] labels) {
        try {
            chart.getDescription().setEnabled(false);
            chart.setTouchEnabled(true);
            chart.setDragEnabled(true);
            chart.setScaleEnabled(false);
            chart.setPinchZoom(false);
            chart.setDrawGridBackground(false);
            chart.setDrawBorders(false);
            chart.getLegend().setEnabled(false);

            // Configure X-axis with improved label spacing
            XAxis xAxis = chart.getXAxis();
            xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
            xAxis.setDrawGridLines(true);
            xAxis.setGridColor(Color.LTGRAY);
            xAxis.setTextColor(Color.GRAY);
            xAxis.setTextSize(8f);

            // FIXED: Smart label reduction for better readability
            String[] displayLabels;
            if (labels.length <= 7) {
                // Show all labels for 7 days or less
                displayLabels = labels;
                xAxis.setLabelCount(labels.length);
            } else if (labels.length <= 14) {
                // Show every other label for 8-14 days
                List<String> reducedLabels = new ArrayList<>();
                for (int i = 0; i < labels.length; i += 2) {
                    reducedLabels.add(labels[i]);
                }
                displayLabels = reducedLabels.toArray(new String[0]);
                xAxis.setLabelCount(displayLabels.length);
            } else {
                // Show every 3rd label for 15+ days
                List<String> reducedLabels = new ArrayList<>();
                for (int i = 0; i < labels.length; i += 3) {
                    reducedLabels.add(labels[i]);
                }
                displayLabels = reducedLabels.toArray(new String[0]);
                xAxis.setLabelCount(displayLabels.length);
            }

            xAxis.setValueFormatter(new IndexAxisValueFormatter(displayLabels));
            xAxis.setGranularity(1f);
            xAxis.setLabelRotationAngle(0f); // Keep labels horizontal for better readability

            // Configure Y-axis (left)
            YAxis leftAxis = chart.getAxisLeft();
            leftAxis.setDrawGridLines(true);
            leftAxis.setGridColor(Color.LTGRAY);
            leftAxis.setTextColor(Color.GRAY);
            leftAxis.setTextSize(8f);
            leftAxis.setAxisMinimum(0f);

            // Disable right Y-axis
            YAxis rightAxis = chart.getAxisRight();
            rightAxis.setEnabled(false);

            // Add padding for better label visibility
            chart.setExtraBottomOffset(10f);
            chart.setExtraLeftOffset(5f);
            chart.setExtraRightOffset(5f);

        } catch (Exception e) {
            Log.e(TAG, "Error configuring chart", e);
        }
    }

    /**
     * Safely convert Object to Double
     */
    private Double getDoubleValue(Object value) {
        if (value == null) return null;

        try {
            if (value instanceof Double) {
                return (Double) value;
            } else if (value instanceof Number) {
                return ((Number) value).doubleValue();
            } else if (value instanceof String) {
                return Double.parseDouble((String) value);
            }
        } catch (Exception e) {
            Log.w(TAG, "Could not convert value to Double: " + value);
        }

        return null;
    }

    private void populateReportCard() {
        LinearLayout reportContainer = findViewById(R.id.report_container);
        if (reportContainer == null) return;
        reportContainer.removeAllViews();

        // Title and range
        TextView range = new TextView(this);
        range.setText("Report Period: " + displayFormat.format(startDate.getTime()) + " - " + displayFormat.format(endDate.getTime()));
        range.setTextColor(Color.parseColor("#2E7D32"));
        range.setTextSize(14);
        reportContainer.addView(range);

        // Summary rows
        addReportRow(reportContainer, "Total Consumption", selectedTotalConsumption.getText().toString() + " kWh");
        addReportRow(reportContainer, "Average Daily", selectedDailyAverage.getText().toString() + " kWh");
        addReportRow(reportContainer, "Total Cost", selectedTotalCost.getText().toString());
        addReportRow(reportContainer, "Peak Day", selectedPeakDay.getText().toString());

        // Area breakdown
        TextView section = new TextView(this);
        section.setText("Area Breakdown");
        section.setTextColor(Color.parseColor("#863B17"));
        section.setTextSize(16);
        section.setPadding(0, 16, 0, 8);
        section.setTypeface(null, android.graphics.Typeface.BOLD);
        reportContainer.addView(section);

        addReportRow(reportContainer, "Area 1 Usage", area1TotalConsumption.getText().toString());
        addReportRow(reportContainer, "Area 1 Cost", area1EstimatedCost.getText().toString());
        addReportRow(reportContainer, "Area 1 Share", area1SharePercentage.getText().toString());

        // Divider between areas
        View areaDiv1 = new View(this);
        areaDiv1.setBackgroundColor(Color.parseColor("#DDDDDD"));
        LinearLayout.LayoutParams areaDiv1Lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1);
        areaDiv1Lp.setMargins(0, 12, 0, 12);
        areaDiv1.setLayoutParams(areaDiv1Lp);
        reportContainer.addView(areaDiv1);

        addReportRow(reportContainer, "Area 2 Usage", area2TotalConsumption.getText().toString());
        addReportRow(reportContainer, "Area 2 Cost", area2EstimatedCost.getText().toString());
        addReportRow(reportContainer, "Area 2 Share", area2SharePercentage.getText().toString());

        // Divider between areas
        View areaDiv2 = new View(this);
        areaDiv2.setBackgroundColor(Color.parseColor("#DDDDDD"));
        LinearLayout.LayoutParams areaDiv2Lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1);
        areaDiv2Lp.setMargins(0, 12, 0, 12);
        areaDiv2.setLayoutParams(areaDiv2Lp);
        reportContainer.addView(areaDiv2);

        addReportRow(reportContainer, "Area 3 Usage", area3TotalConsumption.getText().toString());
        addReportRow(reportContainer, "Area 3 Cost", area3EstimatedCost.getText().toString());
        addReportRow(reportContainer, "Area 3 Share", area3SharePercentage.getText().toString());

        // Removed Recommendations section as requested

        // Day-by-day table
        List<Map<String, Object>> days = historicalData.get("days");
        if (days != null && !days.isEmpty()) {
            TextView dayHeader = new TextView(this);
            dayHeader.setText("Day-by-Day Details");
            dayHeader.setTextColor(Color.parseColor("#863B17"));
            dayHeader.setTextSize(16);
            dayHeader.setTypeface(null, android.graphics.Typeface.BOLD);
            dayHeader.setPadding(0, 16, 0, 8);
            reportContainer.addView(dayHeader);

            for (Map<String, Object> d : days) {
                LinearLayout card = new LinearLayout(this);
                card.setOrientation(LinearLayout.VERTICAL);
                card.setPadding(16, 12, 16, 12);

                TextView dTitle = new TextView(this);
                dTitle.setText(String.valueOf(d.get("date")));
                dTitle.setTextColor(Color.parseColor("#863B17"));
                dTitle.setTextSize(14);
                dTitle.setTypeface(null, android.graphics.Typeface.BOLD);
                card.addView(dTitle);

                addReportRow(card, "kWh", String.format(Locale.getDefault(), "%.2f", (Double) d.get("kwh")));
                addReportRow(card, "Cost", String.format(Locale.getDefault(), "₱ %.2f", (Double) d.get("cost")));
                addReportRow(card, "Peak", String.format(Locale.getDefault(), "%.0f W", (Double) d.get("peak_watts")));
                
                // Add peak time if available
                String peakTime = (String) d.get("peak_time");
                if (peakTime != null && !peakTime.equals("--:--") && !peakTime.trim().isEmpty()) {
                    addReportRow(card, "Peak Time", formatPeakTime(peakTime));
                }

                Object areasObj = d.get("areas");
                if (areasObj instanceof Map) {
                    Map<String, Object> ar = (Map<String, Object>) areasObj;
                    boolean hasAny = false;
                    String dateStr = (String) d.get("date");
                    Double a1 = getDoubleValue(ar.get("area1"));
                    Double a2 = getDoubleValue(ar.get("area2"));
                    Double a3 = getDoubleValue(ar.get("area3"));
                    
                    // Get the area names that were active on this specific date
                    String area1NameForDate = getAreaNameForDate(1, dateStr);
                    String area2NameForDate = getAreaNameForDate(2, dateStr);
                    String area3NameForDate = getAreaNameForDate(3, dateStr);
                    
                    Log.d(TAG, "Date " + dateStr + " - Area 1: " + a1 + " (" + area1NameForDate + 
                          "), Area 2: " + a2 + " (" + area2NameForDate + 
                          "), Area 3: " + a3 + " (" + area3NameForDate + ")");
                    
                    if (a1 != null && a1 > 0) { addReportRow(card, area1NameForDate, formatArea(a1)); hasAny = true; }
                    if (a2 != null && a2 > 0) { addReportRow(card, area2NameForDate, formatArea(a2)); hasAny = true; }
                    if (a3 != null && a3 > 0) { addReportRow(card, area3NameForDate, formatArea(a3)); hasAny = true; }
                    // If there is no area breakdown in DB, simply skip area rows
                    if (!hasAny) {
                        // no-op
                    }
                }

                // divider
                View div = new View(this);
                div.setBackgroundColor(Color.parseColor("#EEEEEE"));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1);
                lp.setMargins(0, 12, 0, 12);
                div.setLayoutParams(lp);

                reportContainer.addView(card);
                reportContainer.addView(div);
            }
        }

        // Wire up XML export buttons
        TextView btnPdf = findViewById(R.id.btnExportPdf);
        if (btnPdf != null) btnPdf.setOnClickListener(v -> confirmExportPdf());
        TextView btnDoc = findViewById(R.id.btnExportDoc);
        if (btnDoc != null) btnDoc.setOnClickListener(v -> confirmExportDoc());
    }

    private void confirmExportPdf() {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle("Export PDF");
        builder.setMessage("Do you want to export this report as a PDF to Downloads?");
        builder.setPositiveButton("Export", (d, w) -> exportToPDF());
        builder.setNegativeButton("Cancel", (d, w) -> d.dismiss());
        android.app.AlertDialog dialog = builder.create();
        dialog.show();
        dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setBackgroundColor(Color.parseColor("#863B17"));
        dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setTextColor(Color.WHITE);
        dialog.getButton(android.app.AlertDialog.BUTTON_NEGATIVE).setBackgroundColor(Color.parseColor("#CCCCCC"));
        dialog.getButton(android.app.AlertDialog.BUTTON_NEGATIVE).setTextColor(Color.parseColor("#863B17"));
    }

    private void confirmExportDoc() {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle("Export Document");
        builder.setMessage("Do you want to export this report as a DOCX to Downloads?");
        builder.setPositiveButton("Export", (d, w) -> exportToDocument());
        builder.setNegativeButton("Cancel", (d, w) -> d.dismiss());
        android.app.AlertDialog dialog = builder.create();
        dialog.show();
        dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setBackgroundColor(Color.parseColor("#863B17"));
        dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setTextColor(Color.WHITE);
        dialog.getButton(android.app.AlertDialog.BUTTON_NEGATIVE).setBackgroundColor(Color.parseColor("#CCCCCC"));
        dialog.getButton(android.app.AlertDialog.BUTTON_NEGATIVE).setTextColor(Color.parseColor("#863B17"));
    }

    private void exportToPDF() {
        try {
            LinearLayout reportContainer = findViewById(R.id.report_container);
            if (reportContainer == null) return;

            String pretty = buildReportText(reportContainer);
            String[] lines = pretty.split("\n");

            // PDF setup: A4 (595x842 points at 72dpi)
            int pageWidth = 595;  // points
            int pageHeight = 842; // points
            int margin = 36;      // 0.5 inch
            int y = margin;

            android.graphics.pdf.PdfDocument pdf = new android.graphics.pdf.PdfDocument();
            android.graphics.Paint paint = new android.graphics.Paint();
            paint.setColor(android.graphics.Color.BLACK);
            paint.setTextSize(12f);
            paint.setAntiAlias(true);

            // Typography
            android.graphics.Paint titlePaint = new android.graphics.Paint(paint);
            titlePaint.setTextSize(24f);
            titlePaint.setFakeBoldText(true);

            android.graphics.Paint subtitlePaint = new android.graphics.Paint(paint);
            subtitlePaint.setTextSize(16f);
            subtitlePaint.setFakeBoldText(true);

            android.graphics.Paint headerPaint = new android.graphics.Paint(paint);
            headerPaint.setTextSize(16f);
            headerPaint.setFakeBoldText(true);

            android.graphics.Paint datePaint = new android.graphics.Paint(paint);
            datePaint.setTextSize(14f);
            datePaint.setFakeBoldText(true);

            android.graphics.pdf.PdfDocument.PageInfo pageInfo =
                    new android.graphics.pdf.PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create();
            android.graphics.pdf.PdfDocument.Page page = pdf.startPage(pageInfo);
            android.graphics.Canvas canvas = page.getCanvas();

            int lineHeight = (int) (paint.getFontMetrics().bottom - paint.getFontMetrics().top) + 2;
            int usableWidth = pageWidth - margin * 2;

            // Title centered
            String title = "I-WATTS ENERGY REPORT";
            float titleWidth = titlePaint.measureText(title);
            canvas.drawText(title, (pageWidth - titleWidth) / 2f, y, titlePaint);
            y += (int) (titlePaint.getFontMetrics().bottom - titlePaint.getFontMetrics().top) + 10;

            // Report Period (if present in first few lines)
            int idx = 0;
            if (lines.length > 0 && lines[0].startsWith("Report Period")) {
                String rp = lines[0];
                float rpW = subtitlePaint.measureText(rp);
                canvas.drawText(rp, (pageWidth - rpW) / 2f, y, subtitlePaint);
                y += (int) (subtitlePaint.getFontMetrics().bottom - subtitlePaint.getFontMetrics().top) + 12;
                idx = 1; // start rendering body from next line
            }

            for (int i = idx; i < lines.length; i++) {
                String raw = lines[i];
                String line = raw;
                // Headers and spacing rules
                if (line.equalsIgnoreCase("Area Breakdown")) {
                    y += 6;
                    // Left aligned header
                    canvas.drawText(line, margin, y, headerPaint);
                    y += (int) (headerPaint.getFontMetrics().bottom - headerPaint.getFontMetrics().top) + 6;
                    continue;
                }
                if (line.equalsIgnoreCase("Day-by-Day Details")) {
                    y += 8;
                    canvas.drawText(line, margin, y, headerPaint);
                    y += (int) (headerPaint.getFontMetrics().bottom - headerPaint.getFontMetrics().top) + 6;
                    continue;
                }
                if (line.matches("\\d{4}-\\d{2}-\\d{2}")) {
                    y += 6; // space before each date
                    // Left aligned date
                    canvas.drawText(line, margin, y, datePaint);
                    y += (int) (datePaint.getFontMetrics().bottom - datePaint.getFontMetrics().top) + 4;
                    continue;
                }
                if (line.startsWith("Area 2 Usage") || line.startsWith("Area 3 Usage")) {
                    // minimal spacing before new area block
                    y += 2;
                }
                // Wrap long lines to fit page width
                while (line.length() > 0) {
                    int count = paint.breakText(line, true, usableWidth, null);
                    String part = line.substring(0, count);
                    if (y + lineHeight > pageHeight - margin) {
                        pdf.finishPage(page);
                        pageInfo = new android.graphics.pdf.PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pdf.getPages().size() + 1).create();
                        page = pdf.startPage(pageInfo);
                        canvas = page.getCanvas();
                        y = margin;
                    }
                    if (part.trim().length() > 0) {
                        canvas.drawText(part, margin, y, paint);
                        y += lineHeight;
                    }
                    line = line.substring(count);
                }
            }

            pdf.finishPage(page);

            // Write to Downloads as application/pdf
            String fileName = "IWatts_Report_" + System.currentTimeMillis() + ".pdf";

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                android.content.ContentValues values = new android.content.ContentValues();
                values.put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, fileName);
                values.put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "application/pdf");
                values.put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS);

                android.content.ContentResolver resolver = getContentResolver();
                android.net.Uri uri = resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                if (uri == null) throw new IllegalStateException("Insert failed");
                try (java.io.OutputStream os = resolver.openOutputStream(uri)) {
                    if (os != null) pdf.writeTo(os);
                }
                Toast.makeText(this, "Saved to Downloads/" + fileName, Toast.LENGTH_LONG).show();
            } else {
                java.io.File dir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS);
                if (!dir.exists()) dir.mkdirs();
                java.io.File out = new java.io.File(dir, fileName);
                try (java.io.FileOutputStream fos = new java.io.FileOutputStream(out)) {
                    pdf.writeTo(fos);
                }
                Toast.makeText(this, "Saved to Downloads/" + fileName, Toast.LENGTH_LONG).show();
            }

            pdf.close();

        } catch (Exception e) {
            Log.e(TAG, "Export PDF failed", e);
            Toast.makeText(this, "Export failed", Toast.LENGTH_SHORT).show();
        }
    }

    private void collectText(View root, StringBuilder out) {
        if (root instanceof TextView) {
            CharSequence t = ((TextView) root).getText();
            if (t != null) out.append(t).append('\n');
        } else if (root instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) root;
            for (int i = 0; i < vg.getChildCount(); i++) collectText(vg.getChildAt(i), out);
        }
    }

    private String buildReportText(View root) {
        StringBuilder sb = new StringBuilder();
        // Only include content derived from the on-screen report; title is drawn by PDF renderer
        renderView(root, sb);
        return sb.toString();
    }

    private void renderView(View v, StringBuilder sb) {
        if (v instanceof LinearLayout) {
            LinearLayout ll = (LinearLayout) v;
            if (ll.getChildCount() == 2 && ll.getChildAt(0) instanceof TextView && ll.getChildAt(1) instanceof TextView) {
                String left = String.valueOf(((TextView) ll.getChildAt(0)).getText());
                String right = String.valueOf(((TextView) ll.getChildAt(1)).getText());
                sb.append(formatRow(left, right, 32)).append('\n');
            } else {
                for (int i = 0; i < ll.getChildCount(); i++) renderView(ll.getChildAt(i), sb);
            }
        } else if (v instanceof TextView) {
            String text = String.valueOf(((TextView) v).getText());
            if (text.trim().length() > 0) {
                // Emphasize section headers
                if (text.equalsIgnoreCase("Area Breakdown") || text.equalsIgnoreCase("Day-by-Day Details")) {
                    sb.append('\n').append(text.toUpperCase()).append('\n');
                } else if (text.startsWith("Area 1 ") || text.startsWith("Area 2 ") || text.startsWith("Area 3 ")) {
                    sb.append(text).append('\n');
                } else {
                    sb.append(text).append('\n');
                }
            }
        } else if (v instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) v;
            for (int i = 0; i < vg.getChildCount(); i++) renderView(vg.getChildAt(i), sb);
        }
    }

    private String formatRow(String left, String right, int leftWidth) {
        left = left.replace('\n', ' ').trim();
        right = right.replace('\n', ' ').trim();
        if (left.length() > leftWidth) left = left.substring(0, leftWidth);
        String padded = String.format(java.util.Locale.getDefault(), "%-" + leftWidth + "s", left);
        return padded + "  " + right;
    }

    private String repeat(char c, int n) {
        char[] arr = new char[n];
        java.util.Arrays.fill(arr, c);
        return new String(arr);
    }

    private void exportToDocument() {
        try {
            LinearLayout reportContainer = findViewById(R.id.report_container);
            if (reportContainer == null) return;

            String pretty = buildReportText(reportContainer);
            String[] lines = pretty.split("\n");

            // Build minimal DOCX (WordprocessingML) with centered title/period and styled headers/dates
            byte[] docxData = buildDocx(lines);
            String fileName = "IWatts_Report_" + System.currentTimeMillis() + ".docx";
            saveToDownloads(fileName, "application/vnd.openxmlformats-officedocument.wordprocessingml.document", docxData);
        } catch (Exception e) {
            Log.e(TAG, "Export DOC failed", e);
            Toast.makeText(this, "Export failed", Toast.LENGTH_SHORT).show();
        }
    }

    private byte[] buildDocx(String[] lines) throws Exception {
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(baos);

        // [Content_Types].xml
        String contentTypes = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">" +
                "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>" +
                "<Default Extension=\"xml\" ContentType=\"application/xml\"/>" +
                "<Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/>" +
                "</Types>";
        putZipEntry(zos, "[Content_Types].xml", contentTypes.getBytes());

        // _rels/.rels
        String rels = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
                "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"word/document.xml\"/>" +
                "</Relationships>";
        putZipEntry(zos, "_rels/.rels", rels.getBytes());

        // word/document.xml
        StringBuilder doc = new StringBuilder();
        doc.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>");
        doc.append("<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\">");
        doc.append("<w:body>");

        // Title centered big bold
        doc.append(p("I-WATTS ENERGY REPORT", true, 28, "center"));

        // Report period if first line
        int startIdx = 0;
        if (lines.length > 0 && lines[0].startsWith("Report Period")) {
            doc.append(p(lines[0], true, 20, "center"));
            startIdx = 1;
        }

        // Body
        for (int i = startIdx; i < lines.length; i++) {
            String line = lines[i];
            if (line.equalsIgnoreCase("Area Breakdown")) {
                doc.append(spacer());
                doc.append(p(line, true, 22, "left"));
            } else if (line.equalsIgnoreCase("Day-by-Day Details")) {
                doc.append(spacer());
                doc.append(p(line, true, 22, "left"));
            } else if (line.matches("\\\\d{4}-\\\\d{2}-\\\\d{2}")) {
                doc.append(spacer());
                doc.append(p(line, true, 18, "left"));
                } else if (line.startsWith("Area 2 ") || line.startsWith("Area 3 ")) {
                // add spacing before new area blocks
                doc.append(spacer());
                doc.append(p(line, false, 12, "left"));
            } else {
                doc.append(p(line, false, 12, "left"));
            }
        }

        doc.append("<w:sectPr/>");
        doc.append("</w:body></w:document>");
        putZipEntry(zos, "word/document.xml", doc.toString().getBytes("UTF-8"));

        zos.close();
        return baos.toByteArray();
    }

    private String p(String text, boolean bold, int sizeHalfPoints, String align) {
        // sizeHalfPoints is font size in half-points (Word expects half-points)
        int sz = sizeHalfPoints;
        String jc = "left";
        if ("center".equalsIgnoreCase(align)) jc = "center";
        StringBuilder sb = new StringBuilder();
        sb.append("<w:p><w:pPr><w:jc w:val=\"").append(jc).append("\"/>");
        sb.append("</w:pPr><w:r><w:rPr>");
        if (bold) sb.append("<w:b/>");
        sb.append("<w:sz w:val=\"").append(sz).append("\"/>");
        sb.append("</w:rPr><w:t>").append(escapeXml(text)).append("</w:t></w:r></w:p>");
        return sb.toString();
    }

    private String spacer() {
        return "<w:p><w:r><w:t> </w:t></w:r></w:p>";
    }

    private String escapeXml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private void putZipEntry(java.util.zip.ZipOutputStream zos, String path, byte[] data) throws Exception {
        zos.putNextEntry(new java.util.zip.ZipEntry(path));
        zos.write(data);
        zos.closeEntry();
    }

    private void saveToDownloads(String fileName, String mimeType, byte[] data) throws Exception {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            android.content.ContentValues values = new android.content.ContentValues();
            values.put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, fileName);
            values.put(android.provider.MediaStore.MediaColumns.MIME_TYPE, mimeType);
            values.put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS);
            values.put(android.provider.MediaStore.MediaColumns.IS_PENDING, 1);

            android.net.Uri collection = android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI;
            android.content.ContentResolver resolver = getContentResolver();
            android.net.Uri item = resolver.insert(collection, values);
            if (item == null) throw new IllegalStateException("Insert failed");
            try (java.io.OutputStream os = resolver.openOutputStream(item)) {
                if (os != null) os.write(data);
            }
            values.clear();
            values.put(android.provider.MediaStore.MediaColumns.IS_PENDING, 0);
            resolver.update(item, values, null, null);
            Toast.makeText(this, "Saved to Downloads/" + fileName, Toast.LENGTH_LONG).show();
        } else {
            java.io.File dir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS);
            if (!dir.exists()) dir.mkdirs();
            java.io.File out = new java.io.File(dir, fileName);
            try (java.io.FileOutputStream fos = new java.io.FileOutputStream(out)) {
                fos.write(data);
            }
            Toast.makeText(this, "Saved to Downloads/" + fileName, Toast.LENGTH_LONG).show();
        }
    }

    private void addReportRow(LinearLayout parent, String title, String value) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, 6, 0, 6);

        TextView t = new TextView(this);
        t.setText(title);
        t.setTextColor(Color.parseColor("#863B17"));
        t.setTextSize(12);
        t.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        TextView v = new TextView(this);
        v.setText(value);
        v.setTextColor(Color.parseColor("#863B17"));
        v.setTextSize(12);

        row.addView(t);
        row.addView(v);
        parent.addView(row);
    }

    private String formatArea(Object v) {
        Double d = getDoubleValue(v);
        if (d == null) d = 0.0;
        return String.format(Locale.getDefault(), "%.2f kWh", d);
    }

    /**
     * Format peak time for display (convert 24-hour to 12-hour format)
     */
    private String formatPeakTime(String rawTime) {
        try {
            if (rawTime == null || rawTime.trim().isEmpty() || rawTime.equals("--:--")) {
                return "--:--";
            }
            
            // Parse the time (assuming format like "14:30" or "14:30:00")
            SimpleDateFormat inputFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
            inputFormat.setTimeZone(PHILIPPINE_TIMEZONE);
            
            // Handle different time formats
            if (rawTime.length() > 5) {
                inputFormat = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
                inputFormat.setTimeZone(PHILIPPINE_TIMEZONE);
            }
            
            Date time = inputFormat.parse(rawTime);
            if (time != null) {
                // Format as 12-hour with AM/PM
                SimpleDateFormat outputFormat = new SimpleDateFormat("h:mm a", Locale.getDefault());
                outputFormat.setTimeZone(PHILIPPINE_TIMEZONE);
                return outputFormat.format(time);
            }
        } catch (Exception e) {
            Log.w(TAG, "Error formatting peak time: " + rawTime + " - " + e.getMessage());
        }
        
        // Return original if formatting fails
        return rawTime;
    }

}