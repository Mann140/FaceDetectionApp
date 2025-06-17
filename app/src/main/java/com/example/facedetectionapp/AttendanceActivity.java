package com.example.facedetectionapp;

import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class AttendanceActivity extends AppCompatActivity {

    private static final String TAG = "AttendanceListActivity";

    private RecyclerView attendanceRecyclerView;
    private AttendanceAdapter attendanceAdapter;
    private TextView titleTextView;
    private AttendanceDatabase database;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_attendance);

        initializeViews();
        initializeDatabase();
        loadAttendanceData();
    }

    private void initializeViews() {
        titleTextView = findViewById(R.id.titleTextView);
        attendanceRecyclerView = findViewById(R.id.attendanceRecyclerView);

        // Set up RecyclerView
        attendanceRecyclerView.setLayoutManager(new LinearLayoutManager(this));

        // Set title with current date
        SimpleDateFormat dateFormat = new SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault());
        String currentDate = dateFormat.format(new Date());
        titleTextView.setText("Attendance - " + currentDate);
    }

    private void initializeDatabase() {
        database = new AttendanceDatabase(this);
    }

    private void loadAttendanceData() {
        try {
            // Get today's attendance records
            List<AttendanceDatabase.AttendanceRecord> attendanceRecords = database.getTodayAttendance();

            // Convert to adapter model
            List<AttendanceAdapter.AttendanceItemModel> attendanceItems =
                    convertToAdapterModel(attendanceRecords);

            // Set up adapter
            attendanceAdapter = new AttendanceAdapter(this, attendanceItems);
            attendanceRecyclerView.setAdapter(attendanceAdapter);

            Log.d(TAG, "✅ Loaded " + attendanceItems.size() + " attendance records");

        } catch (Exception e) {
            Log.e(TAG, "❌ Error loading attendance data", e);
        }
    }

    /**
     * Convert database records to adapter model with proper grouping
     */
    private List<AttendanceAdapter.AttendanceItemModel> convertToAdapterModel(
            List<AttendanceDatabase.AttendanceRecord> records) {

        List<AttendanceAdapter.AttendanceItemModel> items = new ArrayList<>();

        // Group records by user and date
        Map<String, UserAttendanceData> userAttendanceMap = new HashMap<>();

        for (AttendanceDatabase.AttendanceRecord record : records) {
            try {
                // Get user information
                AttendanceDatabase.User user = database.getUserById(record.userId);
                if (user == null) {
                    Log.w(TAG, "⚠️ User not found for ID: " + record.userId);
                    continue;
                }

                String key = user.id + "_" + record.date;
                UserAttendanceData attendanceData = userAttendanceMap.get(key);

                if (attendanceData == null) {
                    attendanceData = new UserAttendanceData(user, record.date);
                    userAttendanceMap.put(key, attendanceData);
                }

                // Add check-in or check-out time
                if ("IN".equals(record.status)) {
                    attendanceData.checkInTime = record.time;
                } else if ("OUT".equals(record.status)) {
                    attendanceData.checkOutTime = record.time;
                }

            } catch (Exception e) {
                Log.e(TAG, "❌ Error processing record: " + record.id, e);
            }
        }

        // Convert to adapter items
        for (UserAttendanceData data : userAttendanceMap.values()) {
            String status = determineAttendanceStatus(data);

            AttendanceAdapter.AttendanceItemModel item = new AttendanceAdapter.AttendanceItemModel(
                    data.user.name,
                    data.user.employeeId,
                    data.user.department,
                    data.date,
                    data.checkInTime != null ? formatTimeForDisplay(data.checkInTime) : "",
                    data.checkOutTime != null ? formatTimeForDisplay(data.checkOutTime) : "",
                    85.0f, // Default confidence for now, you can enhance this later
                    status
            );

            items.add(item);
        }

        return items;
    }

    /**
     * Determine attendance status based on check-in/out times
     */
    private String determineAttendanceStatus(UserAttendanceData data) {
        if (data.checkInTime != null && data.checkOutTime != null) {
            return "PRESENT"; // Full day attendance
        } else if (data.checkInTime != null) {
            return "IN"; // Checked in only
        } else if (data.checkOutTime != null) {
            return "OUT"; // Checked out only (unusual)
        } else {
            return "ABSENT"; // No attendance
        }
    }

    /**
     * Format time string for display (24h to 12h format)
     */
    private String formatTimeForDisplay(String timeString) {
        try {
            SimpleDateFormat inputFormat = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
            SimpleDateFormat outputFormat = new SimpleDateFormat("hh:mm a", Locale.getDefault());
            Date time = inputFormat.parse(timeString);
            return outputFormat.format(time);
        } catch (Exception e) {
            Log.e(TAG, "❌ Error formatting time: " + timeString, e);
            return timeString; // Return original if parsing fails
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Refresh data when returning to activity
        loadAttendanceData();
    }

    // ===== HELPER CLASSES =====

    /**
     * Helper class to group user attendance data
     */
    private static class UserAttendanceData {
        public final AttendanceDatabase.User user;
        public final String date;
        public String checkInTime;
        public String checkOutTime;

        public UserAttendanceData(AttendanceDatabase.User user, String date) {
            this.user = user;
            this.date = date;
        }
    }
}