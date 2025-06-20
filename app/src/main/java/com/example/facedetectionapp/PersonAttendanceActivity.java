package com.example.facedetectionapp;

import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.util.Log;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashMap;
import java.util.Calendar;

public class PersonAttendanceActivity extends AppCompatActivity {
    private static final String TAG = "PersonAttendanceActivity";

    private DatabaseHelper databaseHelper;
    private LinearLayout mainLayout;
    private long personId;
    private String personName;
    private String employeeId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Initialize database
        databaseHelper = new DatabaseHelper(this);

        // Get person data from intent
        personId = getIntent().getLongExtra("person_id", -1);
        personName = getIntent().getStringExtra("person_name");
        employeeId = getIntent().getStringExtra("employee_id");

        // Setup toolbar
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Attendance - " + (personName != null ? personName : "Unknown"));
        }

        createLayout();
        loadAttendanceData();
    }

    private void createLayout() {
        // Create main scroll view
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);

        // Create main linear layout
        mainLayout = new LinearLayout(this);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setPadding(32, 32, 32, 32);

        // Add person info header
        addPersonInfoHeader();

        // Add attendance summary
        addAttendanceSummary();

        // Add recent attendance section
        addRecentAttendanceSection();

        scrollView.addView(mainLayout);
        setContentView(scrollView);
    }

    private void addPersonInfoHeader() {
        TextView headerView = new TextView(this);
        headerView.setText("👤 " + (personName != null ? personName : "Unknown Person"));
        headerView.setTextSize(24);
        headerView.setTextColor(ContextCompat.getColor(this, android.R.color.black));
        headerView.setPadding(0, 0, 0, 16);
        headerView.setTypeface(null, android.graphics.Typeface.BOLD);
        mainLayout.addView(headerView);

        if (employeeId != null) {
            TextView empIdView = new TextView(this);
            empIdView.setText("🆔 Employee ID: " + employeeId);
            empIdView.setTextSize(16);
            empIdView.setTextColor(ContextCompat.getColor(this, android.R.color.darker_gray));
            empIdView.setPadding(0, 0, 0, 24);
            mainLayout.addView(empIdView);
        }
    }

    private void addAttendanceSummary() {
        TextView summaryTitle = new TextView(this);
        summaryTitle.setText("📊 Attendance Summary");
        summaryTitle.setTextSize(20);
        summaryTitle.setTextColor(ContextCompat.getColor(this, android.R.color.black));
        summaryTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        summaryTitle.setPadding(0, 16, 0, 16);
        mainLayout.addView(summaryTitle);

        // Today's summary
        TextView todayView = new TextView(this);
        todayView.setText("📅 Today: Loading...");
        todayView.setTextSize(16);
        todayView.setPadding(16, 8, 16, 8);
        todayView.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_blue_light));
        todayView.setTextColor(ContextCompat.getColor(this, android.R.color.white));
        mainLayout.addView(todayView);

        // This week summary
        TextView weekView = new TextView(this);
        weekView.setText("📅 This Week: Loading...");
        weekView.setTextSize(16);
        weekView.setPadding(16, 8, 16, 8);
        weekView.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_green_light));
        weekView.setTextColor(ContextCompat.getColor(this, android.R.color.white));
        LinearLayout.LayoutParams weekParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        weekParams.topMargin = 8;
        weekView.setLayoutParams(weekParams);
        mainLayout.addView(weekView);

        // This month summary
        TextView monthView = new TextView(this);
        monthView.setText("📅 This Month: Loading...");
        monthView.setTextSize(16);
        monthView.setPadding(16, 8, 16, 8);
        monthView.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_orange_light));
        monthView.setTextColor(ContextCompat.getColor(this, android.R.color.white));
        LinearLayout.LayoutParams monthParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        monthParams.topMargin = 8;
        monthView.setLayoutParams(monthParams);
        mainLayout.addView(monthView);
    }

    private void addRecentAttendanceSection() {
        TextView recentTitle = new TextView(this);
        recentTitle.setText("📋 Recent Attendance");
        recentTitle.setTextSize(20);
        recentTitle.setTextColor(ContextCompat.getColor(this, android.R.color.black));
        recentTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        recentTitle.setPadding(0, 32, 0, 16);
        mainLayout.addView(recentTitle);
    }

    private void loadAttendanceData() {
        if (personId == -1) {
            addErrorMessage("❌ Invalid person ID");
            return;
        }

        try {
            // Load recent attendance records
            loadRecentAttendance();

            // Update summary statistics
            updateSummaryStats();

        } catch (Exception e) {
            Log.e(TAG, "❌ Error loading attendance data: " + e.getMessage());
            addErrorMessage("❌ Error loading attendance data");
        }
    }

    private void loadRecentAttendance() {
        // Get today's attendance
        String today = getCurrentDate();
        List<DatabaseHelper.AttendanceRecord> todayRecords = databaseHelper.getAttendanceByDate(today);

        // Filter records for this person
        boolean hasRecordsToday = false;
        for (DatabaseHelper.AttendanceRecord record : todayRecords) {
            if (record.personId == personId) {
                addAttendanceRecord(record);
                hasRecordsToday = true;
            }
        }

        if (!hasRecordsToday) {
            TextView noRecordsView = new TextView(this);
            noRecordsView.setText("📭 No attendance records for today");
            noRecordsView.setTextSize(16);
            noRecordsView.setPadding(16, 16, 16, 16);
            noRecordsView.setTextColor(ContextCompat.getColor(this, android.R.color.darker_gray));
            mainLayout.addView(noRecordsView);
        }

        // Add yesterday's records if available
        String yesterday = getYesterdayDate();
        List<DatabaseHelper.AttendanceRecord> yesterdayRecords = databaseHelper.getAttendanceByDate(yesterday);

        boolean hasYesterdayRecords = false;
        for (DatabaseHelper.AttendanceRecord record : yesterdayRecords) {
            if (record.personId == personId) {
                if (!hasYesterdayRecords) {
                    TextView yesterdayHeader = new TextView(this);
                    yesterdayHeader.setText("📅 Yesterday (" + yesterday + ")");
                    yesterdayHeader.setTextSize(16);
                    yesterdayHeader.setTypeface(null, android.graphics.Typeface.BOLD);
                    yesterdayHeader.setPadding(0, 24, 0, 8);
                    yesterdayHeader.setTextColor(ContextCompat.getColor(this, android.R.color.darker_gray));
                    mainLayout.addView(yesterdayHeader);
                    hasYesterdayRecords = true;
                }
                addAttendanceRecord(record);
            }
        }
    }

    private void addAttendanceRecord(DatabaseHelper.AttendanceRecord record) {
        LinearLayout recordLayout = new LinearLayout(this);
        recordLayout.setOrientation(LinearLayout.HORIZONTAL);
        recordLayout.setPadding(16, 12, 16, 12);
        recordLayout.setBackgroundColor(ContextCompat.getColor(this, android.R.color.background_light));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = 4;
        params.bottomMargin = 4;
        recordLayout.setLayoutParams(params);

        // Action type icon and text
        TextView actionView = new TextView(this);
        String actionIcon = "CHECK_IN".equals(record.actionType) ? "✅" : "❌";
        String actionText = "CHECK_IN".equals(record.actionType) ? "Check In" : "Check Out";
        actionView.setText(actionIcon + " " + actionText);
        actionView.setTextSize(16);
        actionView.setTextColor(ContextCompat.getColor(this, android.R.color.black));
        actionView.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        // Time
        TextView timeView = new TextView(this);
        timeView.setText("🕐 " + record.time);
        timeView.setTextSize(14);
        timeView.setTextColor(ContextCompat.getColor(this, android.R.color.darker_gray));

        recordLayout.addView(actionView);
        recordLayout.addView(timeView);

        mainLayout.addView(recordLayout);
    }

    private void updateSummaryStats() {
        // This is a simplified version - in a full implementation, you'd calculate
        // proper statistics from the database

        // Update today's summary
        TextView todayView = (TextView) mainLayout.getChildAt(3); // Assuming it's the 4th child
        if (todayView != null) {
            todayView.setText("📅 Today: Check attendance above");
        }

        // Update week summary
        TextView weekView = (TextView) mainLayout.getChildAt(4);
        if (weekView != null) {
            weekView.setText("📅 This Week: Feature coming soon");
        }

        // Update month summary
        TextView monthView = (TextView) mainLayout.getChildAt(5);
        if (monthView != null) {
            monthView.setText("📅 This Month: Feature coming soon");
        }
    }

    private void addErrorMessage(String message) {
        TextView errorView = new TextView(this);
        errorView.setText(message);
        errorView.setTextSize(16);
        errorView.setPadding(16, 16, 16, 16);
        errorView.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark));
        errorView.setBackgroundColor(ContextCompat.getColor(this, android.R.color.background_light));
        mainLayout.addView(errorView);
    }

    // Utility methods
    private String getCurrentDate() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
    }

    private String getYesterdayDate() {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DAY_OF_YEAR, -1);
        return new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(calendar.getTime());
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (databaseHelper != null) {
            databaseHelper.close();
        }
    }
}