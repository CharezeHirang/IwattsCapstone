package com.example.sampleiwatts.processors;

import android.util.Log;
import androidx.annotation.NonNull;
import com.google.firebase.database.*;
import java.text.SimpleDateFormat;
import java.util.*;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import android.os.Handler;
import android.os.Looper;



public class RealTimeDataProcessor {
    private static final String TAG = "RealTimeDataProcessor";

    // Philippine timezone
    private static final TimeZone PHILIPPINE_TIMEZONE = TimeZone.getTimeZone("Asia/Manila");

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private DatabaseReference databaseRef;
    private boolean hasTriedFallback = false;
    private SimpleDateFormat dateFormat;
    private SimpleDateFormat timeFormat;
    private SimpleDateFormat dateTimeFormat;
    private boolean isLoading = false;

    public RealTimeDataProcessor() {
        this.databaseRef = FirebaseDatabase.getInstance().getReference();

        // Set all date formatters to Philippine timezone
        this.dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        this.dateFormat.setTimeZone(PHILIPPINE_TIMEZONE);

        this.timeFormat = new SimpleDateFormat("HH", Locale.getDefault());
        this.timeFormat.setTimeZone(PHILIPPINE_TIMEZONE);

        this.dateTimeFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault());
        this.dateTimeFormat.setTimeZone(PHILIPPINE_TIMEZONE);
    }

    public interface DataProcessingCallback {
        void onDataProcessed(RealTimeData data);
        void onError(String error);
        void onSettingsLoaded(double electricityRate, double voltageReference);
    }

    public interface CurrentHourCallback {
        void onCurrentHourProcessed(CurrentHourData data);
        void onError(String error);
    }

    public static class RealTimeData {
        public String date;
        public double totalConsumption;
        public double totalCost;
        public double peakWatts;

        public String peakTime;
        public int batteryPercent;
        public boolean isCharging;
        public AreaData area1Data = new AreaData();
        public AreaData area2Data = new AreaData();
        public AreaData area3Data = new AreaData();
        public List<HourlyData> hourlyData = new ArrayList<>();
    }

    public static class AreaData {
        public double consumption;
        public double cost;
        public double sharePercentage;
        public double peakWatts;
        public String peakTime = "--:--";
        public boolean hasFluctuation;
        public double fluctuationValue;
    }

    public static class HourlyData {
        public String hour;
        public double consumption;
        public double avgWatts;
    }

    public static class CurrentHourData {
        public String hour;
        public double avgTotalWatts;
        public double avgArea1Watts;
        public double avgArea2Watts;
        public double avgArea3Watts;
        public double peakWatts;
        public double partialKwh;
        public double partialCost;
        public int validReadings;
        public int batteryPercent;
        public boolean isCharging;

        public boolean isFallbackData = false;
        public String fallbackMessage = "";
    }

    /**
     * Load system settings and process real-time data
     */
    public void loadSystemSettingsAndProcess(String date, DataProcessingCallback callback) {
        databaseRef.child("system_settings").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                double electricityRate = 12.5; // Default
                double voltageReference = 220.0; // Default

                if (snapshot.exists()) {
                    if (snapshot.child("electricity_rate_per_kwh").exists()) {
                        electricityRate = snapshot.child("electricity_rate_per_kwh").getValue(Double.class);
                    }
                    if (snapshot.child("voltage_reference").exists()) {
                        voltageReference = snapshot.child("voltage_reference").getValue(Double.class);
                    }
                }

                Log.d(TAG, "Using settings - Rate: " + electricityRate + "/kWh, Voltage: " + voltageReference + "V");
                callback.onSettingsLoaded(electricityRate, voltageReference);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                callback.onError(error.getMessage());
            }
        });
    }

    /**
     * Process hourly summaries AND current hour log data for TRUE real-time
     */
    private void processHourlyDataWithCurrentHour(String date, double electricityRate, double voltageReference, DataProcessingCallback callback) {
        DatabaseReference hourlyRef = databaseRef.child("hourly_summaries").child(date);

        // First get hourly summaries
        hourlyRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot hourlySnapshot) {
                // Then get current hour log data (with fallback handling)
                getCurrentHourLogData(databaseRef.child("logs"), date, voltageReference, new CurrentHourCallback() {
                    @Override
                    public void onCurrentHourProcessed(CurrentHourData currentHourData) {
                        try {
                            RealTimeData realTimeData = buildCompleteRealTimeData(
                                    hourlySnapshot, currentHourData, electricityRate, date
                            );

                            if (currentHourData.isFallbackData && !currentHourData.fallbackMessage.isEmpty()) {
                                Log.i(TAG, "Real-time data note: " + currentHourData.fallbackMessage);
                            }

                            calculateRealAreaPeaksFromSnapshot(hourlySnapshot, realTimeData, date, callback);
                        } catch (Exception e) {
                            Log.e(TAG, "Error building real-time data: " + e.getMessage(), e);
                            callback.onError("Failed to process real-time data");
                        }
                    }

                    @Override
                    public void onError(String error) {
                        // Even if current hour processing fails, still try to show data from hourly summaries
                        Log.w(TAG, "Current hour processing failed: " + error + ", using hourly summaries only");

                        // Create empty current hour data and continue
                        createEmptyCurrentHourData(new CurrentHourCallback() {
                            @Override
                            public void onCurrentHourProcessed(CurrentHourData emptyData) {
                                try {
                                    RealTimeData realTimeData = buildCompleteRealTimeData(hourlySnapshot, emptyData, electricityRate, date);
                                    calculateRealAreaPeaksFromLogData(realTimeData, date, voltageReference, new DataProcessingCallback() {
                                        @Override
                                        public void onDataProcessed(RealTimeData dataWithPeaks) {
                                            // 🔹 Only now pass to UI once peaks are ready
                                            callback.onDataProcessed(dataWithPeaks);
                                        }

                                        @Override public void onError(String error) { callback.onError(error); }
                                        @Override public void onSettingsLoaded(double rate, double voltage) { }
                                    });
                                } catch (Exception e) {
                                    Log.e(TAG, "Error building real-time data with empty current hour: " + e.getMessage(), e);
                                    callback.onError("Failed to process real-time data");
                                }
                            }

                            @Override
                            public void onError(String error) {
                                callback.onError("Failed to create fallback data");
                            }
                        });
                    }
                });
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                callback.onError(error.getMessage());
            }
        });
    }

    /**
     * Get current hour log data for real-time updates
     */
    private void getCurrentHourLogData(DatabaseReference logsRef, String date, double voltageReference, CurrentHourCallback callback) {
        Calendar now = Calendar.getInstance(PHILIPPINE_TIMEZONE);
        String currentHourStr = timeFormat.format(now.getTime());
        int currentHour = now.get(Calendar.HOUR_OF_DAY);

        // Create time range for current hour
        String startTime = date + "T" + String.format("%02d:00:00", currentHour);
        String endTime = date + "T" + String.format("%02d:59:59", currentHour);

        Log.d(TAG, "Getting current hour log data for " + currentHourStr + " (" + startTime + " to " + endTime + ")");

        logsRef.orderByKey()
                .startAt(startTime)
                .endAt(endTime)
                .limitToLast(50) // Get recent logs for current hour
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (snapshot.exists()) {
                            // Current hour has data, process it
                            try {
                                executor.execute(() -> {
                                    CurrentHourData currentHourData = processCurrentHourLogs(snapshot, currentHourStr, voltageReference);
                                    mainHandler.post(() -> callback.onCurrentHourProcessed(currentHourData));
                                });
                            } catch (Exception e) {
                                Log.e(TAG, "Error processing current hour logs: " + e.getMessage(), e);
                                // Fallback to previous hour
                                tryPreviousHourAsFallback(logsRef, date, currentHour, voltageReference, callback);
                            }
                        } else {
                            if (!hasTriedFallback) {
                                hasTriedFallback = true;
                                Log.d(TAG, "No current hour log data found, trying fallback to previous hour");
                                tryPreviousHourAsFallback(logsRef, date, currentHour, voltageReference, callback);
                            } else {
                                Log.w(TAG, "Skipping redundant fallback attempt — already tried once.");
                                createEmptyCurrentHourData(callback);
                            }
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Log.e(TAG, "Error getting current hour log data: " + error.getMessage());
                        // Try fallback even on database error
                        tryPreviousHourAsFallback(logsRef, date, currentHour, voltageReference, callback);
                    }
                });
    }

    private void tryPreviousHourAsFallback(DatabaseReference logsRef, String date, int currentHour,
                                           double voltageReference, CurrentHourCallback callback) {
        // Try previous hour (or previous day's last hour if current hour is 0)
        Calendar previousHourCal = Calendar.getInstance(PHILIPPINE_TIMEZONE);
        previousHourCal.add(Calendar.HOUR_OF_DAY, -1);

        String fallbackDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(previousHourCal.getTime());
        int fallbackHour = previousHourCal.get(Calendar.HOUR_OF_DAY);
        String fallbackHourStr = String.format("%02d", fallbackHour);

        String startTime = fallbackDate + "T" + String.format("%02d:00:00", fallbackHour);
        String endTime = fallbackDate + "T" + String.format("%02d:59:59", fallbackHour);

        Log.d(TAG, "Trying fallback to previous hour: " + fallbackHourStr + " (" + startTime + " to " + endTime + ")");

        logsRef.orderByKey()
                .startAt(startTime)
                .endAt(endTime)
                .limitToLast(50)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (snapshot.exists()) {
                            try {
                                // Process previous hour data but mark it as fallback
                                CurrentHourData fallbackData = processCurrentHourLogs(snapshot, fallbackHourStr, voltageReference);
                                fallbackData.isFallbackData = true; // Add this flag to CurrentHourData class
                                fallbackData.fallbackMessage = "Using previous hour data (no current data available)";
                                callback.onCurrentHourProcessed(fallbackData);
                                Log.d(TAG, "Successfully using previous hour as fallback");
                            } catch (Exception e) {
                                Log.e(TAG, "Error processing fallback hour data: " + e.getMessage(), e);
                                // Create empty current hour data as final fallback
                                createEmptyCurrentHourData(callback);
                            }
                        } else {
                            Log.d(TAG, "No fallback hour data found either, using empty data");
                            // No data in previous hour either, create empty data
                            createEmptyCurrentHourData(callback);
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Log.e(TAG, "Error getting fallback hour data: " + error.getMessage());
                        createEmptyCurrentHourData(callback);
                    }
                });
    }




    private void calculateRealAreaPeaksFromSnapshot(
            DataSnapshot snapshot, RealTimeData realTimeData,
            String date, DataProcessingCallback callback) {

        double maxA1=0, maxA2=0, maxA3=0;
        String t1="--:--", t2="--:--", t3="--:--";

        for (DataSnapshot hourSnap : snapshot.getChildren()) {
            String hour = hourSnap.getKey();
            Double a1 = getDoubleValue(hourSnap.child("area1_peak_watts").getValue());
            Double a2 = getDoubleValue(hourSnap.child("area2_peak_watts").getValue());
            Double a3 = getDoubleValue(hourSnap.child("area3_peak_watts").getValue());
            if (a1!=null && a1>maxA1){maxA1=a1;t1=hour+":00";}
            if (a2!=null && a2>maxA2){maxA2=a2;t2=hour+":00";}
            if (a3!=null && a3>maxA3){maxA3=a3;t3=hour+":00";}
        }

        realTimeData.area1Data.peakWatts=maxA1; realTimeData.area1Data.peakTime=t1;
        realTimeData.area2Data.peakWatts=maxA2; realTimeData.area2Data.peakTime=t2;
        realTimeData.area3Data.peakWatts=maxA3; realTimeData.area3Data.peakTime=t3;

        double overall = Math.max(maxA1,Math.max(maxA2,maxA3));
        realTimeData.peakWatts = Math.max(realTimeData.peakWatts, overall);
        realTimeData.peakTime  = overall==maxA1?t1:overall==maxA2?t2:t3;

        callback.onDataProcessed(realTimeData);
    }


    /**
     * NEW METHOD: Create empty current hour data when no data is available
     */
    private void createEmptyCurrentHourData(CurrentHourCallback callback) {
        Calendar now = Calendar.getInstance(PHILIPPINE_TIMEZONE);
        String currentHourStr = timeFormat.format(now.getTime());

        CurrentHourData emptyData = new CurrentHourData();
        emptyData.hour = currentHourStr;
        emptyData.avgTotalWatts = 0.0;
        emptyData.avgArea1Watts = 0.0;
        emptyData.avgArea2Watts = 0.0;
        emptyData.avgArea3Watts = 0.0;
        emptyData.peakWatts = 0.0;
        emptyData.partialKwh = 0.0;
        emptyData.partialCost = 0.0;
        emptyData.validReadings = 0;
        emptyData.batteryPercent = 0;
        emptyData.isCharging = false;
        emptyData.isFallbackData = false;
        emptyData.fallbackMessage = "No data available for current period";

        Log.d(TAG, "Created empty current hour data for display");
        callback.onCurrentHourProcessed(emptyData);
    }

    /**
     * Process current hour log data - Updated for new log structure
     */
    private CurrentHourData processCurrentHourLogs(DataSnapshot logs, String hour, double voltageReference) {
        CurrentHourData currentHourData = new CurrentHourData();
        currentHourData.hour = hour;

        int validReadings = 0;
        double totalWatts = 0.0;
        double area1Watts = 0.0;
        double area2Watts = 0.0;
        double area3Watts = 0.0;
        double maxWatts = 0.0;

        // Battery status from latest reading
        int latestBatteryPercent = 0;
        boolean latestChargingStatus = false;

        for (DataSnapshot logSnapshot : logs.getChildren()) {
            try {
                // Each log entry is a timestamp containing the data object
                for (DataSnapshot timestampSnapshot : logSnapshot.getChildren()) {
                    Map<String, Object> logData = (Map<String, Object>) timestampSnapshot.getValue();
                    if (logData != null) {
                        // Extract current values from new log structure
                        Double current1 = getDoubleValue(logData.get("C1_A")); // Previously A1
                        Double current2 = getDoubleValue(logData.get("C2_A")); // Previously A2
                        Double current3 = getDoubleValue(logData.get("C3_A")); // Previously A3

                        if (current1 != null && current2 != null && current3 != null) {
                            // Calculate watts: P = V * I (same calculation as before)
                            double area1Power = voltageReference * current1;
                            double area2Power = voltageReference * current2;
                            double area3Power = voltageReference * current3;
                            double totalPower = area1Power + area2Power + area3Power;

                            area1Watts += area1Power;
                            area2Watts += area2Power;
                            area3Watts += area3Power;
                            totalWatts += totalPower;
                            maxWatts = Math.max(maxWatts, totalPower);

                            validReadings++;

                            // Update battery status from latest reading
                            Double batteryPercent = getDoubleValue(logData.get("vbat_percent"));
                            Boolean charging = getBooleanValue(logData.get("charging"));

                            if (batteryPercent != null) {
                                latestBatteryPercent = batteryPercent.intValue();
                            }
                            if (charging != null) {
                                latestChargingStatus = charging;
                            }
                        }
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "Error parsing log entry: " + e.getMessage());
            }
        }

        if (validReadings > 0) {
            // Calculate averages (same logic as before)
            currentHourData.avgTotalWatts = totalWatts / validReadings;
            currentHourData.avgArea1Watts = area1Watts / validReadings;
            currentHourData.avgArea2Watts = area2Watts / validReadings;
            currentHourData.avgArea3Watts = area3Watts / validReadings;
            currentHourData.peakWatts = maxWatts;
            currentHourData.validReadings = validReadings;
            currentHourData.batteryPercent = latestBatteryPercent;
            currentHourData.isCharging = latestChargingStatus;

            // Calculate partial kWh based on elapsed time in current hour
            Calendar now = Calendar.getInstance(PHILIPPINE_TIMEZONE);
            int minutesElapsed = now.get(Calendar.MINUTE);
            double hourProgress = minutesElapsed / 60.0;

            // Use actual reading count instead of fixed intervals
            currentHourData.partialKwh = (currentHourData.avgTotalWatts / 1000.0) * hourProgress;

            Log.d(TAG, String.format("Current hour processed: %d readings, %.2fW avg, %.3f kWh partial, %d%% battery",
                    validReadings, currentHourData.avgTotalWatts, currentHourData.partialKwh, latestBatteryPercent));
        } else {
            Log.w(TAG, "No valid log data found for current hour");
        }

        return currentHourData;
    }

    /**
     * Enhanced method to calculate REAL area peaks from log data
     */
    private void calculateRealAreaPeaksFromLogData(RealTimeData realTimeData, String date,
                                                   double voltageReference, DataProcessingCallback callback) {
        Log.d(TAG, "Calculating real area peaks from log data for date: " + date);

        DatabaseReference hourlySummariesRef = databaseRef.child("hourly_summaries").child(date);

        hourlySummariesRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) {
                    Log.w(TAG, "No hourly summaries found, using proportional fallback");
                    useProportionalPeakDistribution(realTimeData);
                    callback.onDataProcessed(realTimeData);
                    return;
                }

                double maxArea1 = 0, maxArea2 = 0, maxArea3 = 0;
                String area1PeakTime = "--:--", area2PeakTime = "--:--", area3PeakTime = "--:--";

                // Scan through hourly summaries to find max peaks
                for (DataSnapshot hourSnapshot : snapshot.getChildren()) {
                    String hour = hourSnapshot.getKey();

                    // ✅ Read the new fields from Cloud Function
                    Double area1Peak = getDoubleValue(hourSnapshot.child("area1_peak_watts").getValue());
                    Double area2Peak = getDoubleValue(hourSnapshot.child("area2_peak_watts").getValue());
                    Double area3Peak = getDoubleValue(hourSnapshot.child("area3_peak_watts").getValue());

                    // Track maximum peaks across all hours
                    if (area1Peak != null && area1Peak > maxArea1) {
                        maxArea1 = area1Peak;
                        area1PeakTime = hour + ":00";
                    }
                    if (area2Peak != null && area2Peak > maxArea2) {
                        maxArea2 = area2Peak;
                        area2PeakTime = hour + ":00";
                    }
                    if (area3Peak != null && area3Peak > maxArea3) {
                        maxArea3 = area3Peak;
                        area3PeakTime = hour + ":00";
                    }
                }

                // Update real-time data with calculated peaks
                realTimeData.area1Data.peakWatts = maxArea1;
                realTimeData.area1Data.peakTime = area1PeakTime;
                realTimeData.area1Data.hasFluctuation = false;  // Not tracked in summaries
                realTimeData.area1Data.fluctuationValue = 0;

                realTimeData.area2Data.peakWatts = maxArea2;
                realTimeData.area2Data.peakTime = area2PeakTime;
                realTimeData.area2Data.hasFluctuation = false;
                realTimeData.area2Data.fluctuationValue = 0;

                realTimeData.area3Data.peakWatts = maxArea3;
                realTimeData.area3Data.peakTime = area3PeakTime;
                realTimeData.area3Data.hasFluctuation = false;
                realTimeData.area3Data.fluctuationValue = 0;

                // Find overall peak
                double overallMaxPeak = Math.max(maxArea1, Math.max(maxArea2, maxArea3));
                String overallPeakTime = "--:--";

                if (overallMaxPeak == maxArea1) {
                    overallPeakTime = area1PeakTime;
                } else if (overallMaxPeak == maxArea2) {
                    overallPeakTime = area2PeakTime;
                } else if (overallMaxPeak == maxArea3) {
                    overallPeakTime = area3PeakTime;
                }

                realTimeData.peakTime = overallPeakTime;
                realTimeData.peakWatts = overallMaxPeak;

                Log.d(TAG, String.format("✅ Peaks from summaries - A1: %.1fW@%s, A2: %.1fW@%s, A3: %.1fW@%s",
                        maxArea1, area1PeakTime, maxArea2, area2PeakTime, maxArea3, area3PeakTime));

                callback.onDataProcessed(realTimeData);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Error reading hourly summaries: " + error.getMessage());
                useProportionalPeakDistribution(realTimeData);
                callback.onDataProcessed(realTimeData);
            }
        });
    }

    /**
     * Build complete real-time data combining hourly summaries and current hour
     */
    private RealTimeData buildCompleteRealTimeData(DataSnapshot hourlySnapshot, CurrentHourData currentHourData,
                                                   double electricityRate, String date) {
        RealTimeData realTimeData = new RealTimeData();
        realTimeData.date = date;
        realTimeData.batteryPercent = currentHourData.batteryPercent;
        realTimeData.isCharging = currentHourData.isCharging;

        double totalDailyConsumption = 0.0;
        double totalArea1Consumption = 0.0;
        double totalArea2Consumption = 0.0;
        double totalArea3Consumption = 0.0;
        double maxPeakWatts = 0.0;
        String overallPeakTime = "--:--";

        // Process completed hours from hourly summaries
        if (hourlySnapshot.exists()) {
            for (DataSnapshot hourSnapshot : hourlySnapshot.getChildren()) {
                try {
                    Map<String, Object> hourData = (Map<String, Object>) hourSnapshot.getValue();
                    if (hourData != null) {
                        Double hourKwh = getDoubleValue(hourData.get("total_kwh"));
                        Double area1Kwh = getDoubleValue(hourData.get("area1_kwh"));
                        Double area2Kwh = getDoubleValue(hourData.get("area2_kwh"));
                        Double area3Kwh = getDoubleValue(hourData.get("area3_kwh"));
                        Double peakWatts = getDoubleValue(hourData.get("peak_watts"));


                        if (area1Kwh != null) totalArea1Consumption += area1Kwh;
                        if (area2Kwh != null) totalArea2Consumption += area2Kwh;
                        if (area3Kwh != null) totalArea3Consumption += area3Kwh;

                        if (peakWatts != null && peakWatts > maxPeakWatts) {
                            maxPeakWatts = peakWatts;
                            overallPeakTime = hourSnapshot.getKey() + ":00";
                        }

                        // Add to hourly data for charts
                        HourlyData hourlyData = new HourlyData();
                        hourlyData.hour = hourSnapshot.getKey();
                        hourlyData.consumption = hourKwh != null ? hourKwh : 0.0;
                        Double avgWatts = getDoubleValue(hourData.get("avg_watts"));
                        hourlyData.avgWatts = avgWatts != null ? avgWatts : 0.0;
                        realTimeData.hourlyData.add(hourlyData);
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Error processing hourly data: " + e.getMessage());
                }
            }
        }

        // Add current hour partial data
        totalDailyConsumption += currentHourData.partialKwh;
        totalArea1Consumption += (currentHourData.avgArea1Watts / 1000.0) * getCurrentHourProgress();
        totalArea2Consumption += (currentHourData.avgArea2Watts / 1000.0) * getCurrentHourProgress();
        totalArea3Consumption += (currentHourData.avgArea3Watts / 1000.0) * getCurrentHourProgress();

        if (currentHourData.peakWatts > maxPeakWatts) {
            maxPeakWatts = currentHourData.peakWatts;
            overallPeakTime = currentHourData.hour + ":XX";
        }
        totalDailyConsumption = totalArea1Consumption + totalArea2Consumption + totalArea3Consumption;

        // Set main totals
        realTimeData.totalConsumption = totalDailyConsumption;
        realTimeData.totalCost = totalDailyConsumption * electricityRate;
        realTimeData.peakWatts = maxPeakWatts;
        realTimeData.peakTime = overallPeakTime;

        // Set area data
        realTimeData.area1Data.consumption = totalArea1Consumption;
        realTimeData.area1Data.cost = totalArea1Consumption * electricityRate;

        realTimeData.area2Data.consumption = totalArea2Consumption;
        realTimeData.area2Data.cost = totalArea2Consumption * electricityRate;

        realTimeData.area3Data.consumption = totalArea3Consumption;
        realTimeData.area3Data.cost = totalArea3Consumption * electricityRate;

        // Calculate share percentages
        if (totalDailyConsumption > 0) {
            realTimeData.area1Data.sharePercentage = (totalArea1Consumption / totalDailyConsumption) * 100.0;
            realTimeData.area2Data.sharePercentage = (totalArea2Consumption / totalDailyConsumption) * 100.0;
            realTimeData.area3Data.sharePercentage = (totalArea3Consumption / totalDailyConsumption) * 100.0;
        } else {
            realTimeData.area1Data.sharePercentage = 0.0;
            realTimeData.area2Data.sharePercentage = 0.0;
            realTimeData.area3Data.sharePercentage = 0.0;
        }

        return realTimeData;
    }

    /**
     * Fallback method when log data is unavailable
     */
    private void useProportionalPeakDistribution(RealTimeData realTimeData) {
        double totalConsumption = realTimeData.area1Data.consumption +
                realTimeData.area2Data.consumption +
                realTimeData.area3Data.consumption;

        if (totalConsumption > 0) {
            double basePeak = realTimeData.peakWatts;
            realTimeData.area1Data.peakWatts = basePeak * (realTimeData.area1Data.consumption / totalConsumption);
            realTimeData.area2Data.peakWatts = basePeak * (realTimeData.area2Data.consumption / totalConsumption);
            realTimeData.area3Data.peakWatts = basePeak * (realTimeData.area3Data.consumption / totalConsumption);
        }
    }

    /**
     * Helper method to get current hour progress (0.0 to 1.0)
     */
    private double getCurrentHourProgress() {
        Calendar now = Calendar.getInstance(PHILIPPINE_TIMEZONE);
        int minutes = now.get(Calendar.MINUTE);
        return minutes / 60.0;
    }

    /**
     * Helper method to safely extract Double values
     */
    private Double getDoubleValue(Object value) {
        if (value == null) return null;
        if (value instanceof Double) return (Double) value;
        if (value instanceof Number) return ((Number) value).doubleValue();
        if (value instanceof String) {
            try {
                return Double.parseDouble((String) value);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    /**
     * Helper method to safely extract Boolean values
     */
    private Boolean getBooleanValue(Object value) {
        if (value == null) return null;
        if (value instanceof Boolean) return (Boolean) value;
        if (value instanceof String) {
            String str = ((String) value).toLowerCase();
            return "true".equals(str) || "1".equals(str);
        }
        if (value instanceof Number) {
            return ((Number) value).intValue() != 0;
        }
        return null;
    }

    /**
     * Helper method to safely parse double values (legacy)
     */
    private double parseDouble(String value) {
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    /**
     * Main entry point for processing real-time data
     */
    public void processRealTimeData(String date, DataProcessingCallback callback) {
        loadSystemSettingsAndProcess(date, new DataProcessingCallback() {


            @Override
            public void onSettingsLoaded(double electricityRate, double voltageReference) {
                processHourlyDataWithCurrentHour(date, electricityRate, voltageReference, callback);
            }

            @Override
            public void onDataProcessed(RealTimeData data) {
                callback.onDataProcessed(data);
            }

            @Override
            public void onError(String error) {
                callback.onError(error);
            }
        });
    }
}