package com.example.facedetectionapp;

import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.util.Log;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import java.util.List;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class AttendanceRecordsActivity extends AppCompatActivity {
    private static final String TAG = "AttendanceRecordsActivity";

    private DatabaseHelper databaseHelper;
    private LinearLayout mainLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Initialize database
        databaseHelper = new DatabaseHelper(this);

        // Setup toolbar
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Attendance Records");
        }

        createLayout();
        loadTodayAttendance();
    }

    private void createLayout() {
        // Create main scroll view
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);

        // Create main linear layout
        mainLayout = new LinearLayout(this);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setPadding(32, 32, 32, 32);

        // Add header
        TextView headerView = new TextView(this);
        headerView.setText("📊 Today's Attendance Records");
        headerView.setTextSize(24);
        headerView.setTextColor(ContextCompat.getColor(this, android.R.color.black));
        headerView.setPadding(0, 0, 0, 24);
        headerView.setTypeface(null, android.graphics.Typeface.BOLD);
        mainLayout.addView(headerView);

        // Add date
        TextView dateView = new TextView(this);
        String today = new SimpleDateFormat("EEEE, MMMM dd, yyyy", Locale.getDefault()).format(new Date());
        dateView.setText("📅 " + today);
        dateView.setTextSize(16);
        dateView.setTextColor(ContextCompat.getColor(this, android.R.color.darker_gray));
        dateView.setPadding(0, 0, 0, 32);
        mainLayout.addView(dateView);

        scrollView.addView(mainLayout);
        setContentView(scrollView);
    }

    private void loadTodayAttendance() {
        try {
            String today = getCurrentDate();
            List<DatabaseHelper.AttendanceRecord> records = databaseHelper.getAttendanceByDate(today);

            if (records.isEmpty()) {
                addEmptyMessage();
            } else {
                // Add summary stats
                addSummaryStats(records);

                // Add records
                TextView recordsTitle = new TextView(this);
                recordsTitle.setText("📋 Individual Records");
                recordsTitle.setTextSize(18);
                recordsTitle.setTypeface(null, android.graphics.Typeface.BOLD);
                recordsTitle.setPadding(0, 24, 0, 16);
                mainLayout.addView(recordsTitle);

                for (DatabaseHelper.AttendanceRecord record : records) {
                    addAttendanceRecord(record);
                }
            }

        } catch (Exception e) {
            Log.e(TAG, "❌ Error loading attendance records: " + e.getMessage());
            addErrorMessage("❌ Error loading attendance records: " + e.getMessage());
        }
    }

    private void addSummaryStats(List<DatabaseHelper.AttendanceRecord> records) {
        int checkIns = 0;
        int checkOuts = 0;

        for (DatabaseHelper.AttendanceRecord record : records) {
            if ("CHECK_IN".equals(record.actionType)) {
                checkIns++;
            } else if ("CHECK_OUT".equals(record.actionType)) {
                checkOuts++;
            }
        }

        // Create stats layout
        LinearLayout statsLayout = new LinearLayout(this);
        statsLayout.setOrientation(LinearLayout.HORIZONTAL);
        statsLayout.setPadding(0, 0, 0, 24);

        // Check-ins
        TextView checkInView = new TextView(this);
        checkInView.setText("✅ Check-ins\n" + checkIns);
        checkInView.setTextSize(14);
        checkInView.setPadding(16, 16, 16, 16);
        checkInView.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_green_light));
        checkInView.setTextColor(ContextCompat.getColor(this, android.R.color.white));
        checkInView.setGravity(android.view.Gravity.CENTER);
        checkInView.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        // Check-outs
        TextView checkOutView = new TextView(this);
        checkOutView.setText("❌ Check-outs\n" + checkOuts);
        checkOutView.setTextSize(14);
        checkOutView.setPadding(16, 16, 16, 16);
        checkOutView.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_orange_light));
        checkOutView.setTextColor(ContextCompat.getColor(this, android.R.color.white));
        checkOutView.setGravity(android.view.Gravity.CENTER);
        LinearLayout.LayoutParams checkOutParams = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        checkOutParams.leftMargin = 16;
        checkOutView.setLayoutParams(checkOutParams);

        // Total
        TextView totalView = new TextView(this);
        totalView.setText("📊 Total\n" + records.size());
        totalView.setTextSize(14);
        totalView.setPadding(16, 16, 16, 16);
        totalView.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_blue_light));
        totalView.setTextColor(ContextCompat.getColor(this, android.R.color.white));
        totalView.setGravity(android.view.Gravity.CENTER);
        LinearLayout.LayoutParams totalParams = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        totalParams.leftMargin = 16;
        totalView.setLayoutParams(totalParams);

        statsLayout.addView(checkInView);
        statsLayout.addView(checkOutView);
        statsLayout.addView(totalView);

        mainLayout.addView(statsLayout);
    }

    private void addAttendanceRecord(DatabaseHelper.AttendanceRecord record) {
        LinearLayout recordLayout = new LinearLayout(this);
        recordLayout.setOrientation(LinearLayout.VERTICAL);
        recordLayout.setPadding(20, 16, 20, 16);
        recordLayout.setBackgroundColor(ContextCompat.getColor(this, android.R.color.background_light));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = 8;
        recordLayout.setLayoutParams(params);

        // Top row - name and action
        LinearLayout topRow = new LinearLayout(this);
        topRow.setOrientation(LinearLayout.HORIZONTAL);

        TextView nameView = new TextView(this);
        nameView.setText("👤 " + record.personName);
        nameView.setTextSize(16);
        nameView.setTextColor(ContextCompat.getColor(this, android.R.color.black));
        nameView.setTypeface(null, android.graphics.Typeface.BOLD);
        nameView.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        TextView actionView = new TextView(this);
        String actionIcon = "CHECK_IN".equals(record.actionType) ? "✅" : "❌";
        String actionText = "CHECK_IN".equals(record.actionType) ? "Check In" : "Check Out";
        actionView.setText(actionIcon + " " + actionText);
        actionView.setTextSize(14);
        actionView.setTextColor("CHECK_IN".equals(record.actionType) ?
                ContextCompat.getColor(this, android.R.color.holo_green_dark) :
                ContextCompat.getColor(this, android.R.color.holo_orange_dark));

        topRow.addView(nameView);
        topRow.addView(actionView);

        // Bottom row - employee ID and time
        LinearLayout bottomRow = new LinearLayout(this);
        bottomRow.setOrientation(LinearLayout.HORIZONTAL);
        bottomRow.setPadding(0, 4, 0, 0);

        TextView empIdView = new TextView(this);
        empIdView.setText("🆔 " + record.employeeId);
        empIdView.setTextSize(12);
        empIdView.setTextColor(ContextCompat.getColor(this, android.R.color.darker_gray));
        empIdView.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        TextView timeView = new TextView(this);
        timeView.setText("🕐 " + record.time);
        timeView.setTextSize(12);
        timeView.setTextColor(ContextCompat.getColor(this, android.R.color.darker_gray));

        bottomRow.addView(empIdView);
        bottomRow.addView(timeView);

        recordLayout.addView(topRow);
        recordLayout.addView(bottomRow);

        mainLayout.addView(recordLayout);
    }

    private void addEmptyMessage() {
        TextView emptyView = new TextView(this);
        emptyView.setText("📭 No attendance records for today\n\nRecords will appear here when people check in or out.");
        emptyView.setTextSize(16);
        emptyView.setPadding(24, 40, 24, 40);
        emptyView.setTextColor(ContextCompat.getColor(this, android.R.color.darker_gray));
        emptyView.setBackgroundColor(ContextCompat.getColor(this, android.R.color.background_light));
        mainLayout.addView(emptyView);
    }

    private void addErrorMessage(String message) {
        TextView errorView = new TextView(this);
        errorView.setText(message);
        errorView.setTextSize(16);
        errorView.setPadding(24, 40, 24, 40);
        errorView.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark));
        errorView.setBackgroundColor(ContextCompat.getColor(this, android.R.color.background_light));
        mainLayout.addView(errorView);
    }

    private String getCurrentDate() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
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