package com.example.facedetectionapp;

import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class AttendanceRecordsActivity extends AppCompatActivity {
    private static final String TAG = "AttendanceRecordsActivity";

    private DatabaseHelper databaseHelper;
    private RecyclerView recyclerView;
    private AttendanceRecordsAdapter adapter;
    private TextView emptyView;
    private TextView dateRangeText;
    private ProgressBar progressBar;

    private List<DatabaseHelper.AttendanceRecord> attendanceRecords;
    private String currentDateFilter = "today";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_attendance_records);

        Log.d(TAG, "🚀 AttendanceRecordsActivity started");

        // Initialize database
        databaseHelper = new DatabaseHelper(this);

        // Setup toolbar
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Attendance Records");
        }

        initializeViews();
        setupRecyclerView();
        loadTodayAttendance();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.attendance_records_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.menu_today) {
            loadTodayAttendance();
            return true;
        } else if (id == R.id.menu_yesterday) {
            loadYesterdayAttendance();
            return true;
        } else if (id == R.id.menu_this_week) {
            loadThisWeekAttendance();
            return true;
        } else if (id == R.id.menu_this_month) {
            loadThisMonthAttendance();
            return true;
        } else if (id == R.id.menu_custom_date) {
            Toast.makeText(this, "Custom date picker - Coming Soon", Toast.LENGTH_SHORT).show();
            return true;
        } else if (id == R.id.menu_export) {
            exportAttendanceData();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void initializeViews() {
        recyclerView = findViewById(R.id.attendanceRecyclerView);
        emptyView = findViewById(R.id.emptyView);
        dateRangeText = findViewById(R.id.dateRangeText);
        progressBar = findViewById(R.id.progressBar);

        attendanceRecords = new ArrayList<>();
    }

    private void setupRecyclerView() {
        adapter = new AttendanceRecordsAdapter(this, attendanceRecords);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);
    }

    private void loadTodayAttendance() {
        currentDateFilter = "today";
        String today = getCurrentDate();
        updateDateRangeText("📅 Today - " + getFormattedDate(today));
        loadAttendanceForDate(today);
    }

    private void loadYesterdayAttendance() {
        currentDateFilter = "yesterday";
        String yesterday = getYesterdayDate();
        updateDateRangeText("📅 Yesterday - " + getFormattedDate(yesterday));
        loadAttendanceForDate(yesterday);
    }

    private void loadThisWeekAttendance() {
        currentDateFilter = "week";
        updateDateRangeText("📅 This Week");
        loadAttendanceForDateRange(getWeekStartDate(), getCurrentDate());
    }

    private void loadThisMonthAttendance() {
        currentDateFilter = "month";
        updateDateRangeText("📅 This Month");
        loadAttendanceForDateRange(getMonthStartDate(), getCurrentDate());
    }

    private void loadAttendanceForDate(String date) {
        showProgress(true);

        new Thread(() -> {
            try {
                List<DatabaseHelper.AttendanceRecord> records = databaseHelper.getAttendanceByDate(date);

                runOnUiThread(() -> {
                    attendanceRecords.clear();
                    attendanceRecords.addAll(records);
                    adapter.updateRecords(attendanceRecords);

                    updateEmptyView();
                    showProgress(false);

                    Log.d(TAG, "📊 Loaded " + records.size() + " records for " + date);
                });

            } catch (Exception e) {
                Log.e(TAG, "❌ Error loading attendance for date: " + date, e);
                runOnUiThread(() -> {
                    showError("Error loading attendance records: " + e.getMessage());
                    showProgress(false);
                });
            }
        }).start();
    }

    private void loadAttendanceForDateRange(String startDate, String endDate) {
        showProgress(true);

        new Thread(() -> {
            try {
                // For simplicity, we'll aggregate records from each day in the range
                List<DatabaseHelper.AttendanceRecord> allRecords = new ArrayList<>();

                Calendar start = Calendar.getInstance();
                Calendar end = Calendar.getInstance();
                SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

                start.setTime(dateFormat.parse(startDate));
                end.setTime(dateFormat.parse(endDate));

                while (!start.after(end)) {
                    String currentDate = dateFormat.format(start.getTime());
                    List<DatabaseHelper.AttendanceRecord> dayRecords = databaseHelper.getAttendanceByDate(currentDate);
                    allRecords.addAll(dayRecords);
                    start.add(Calendar.DAY_OF_MONTH, 1);
                }

                runOnUiThread(() -> {
                    attendanceRecords.clear();
                    attendanceRecords.addAll(allRecords);
                    adapter.updateRecords(attendanceRecords);

                    updateEmptyView();
                    showProgress(false);

                    Log.d(TAG, "📊 Loaded " + allRecords.size() + " records for date range");
                });

            } catch (Exception e) {
                Log.e(TAG, "❌ Error loading attendance for date range", e);
                runOnUiThread(() -> {
                    showError("Error loading attendance records: " + e.getMessage());
                    showProgress(false);
                });
            }
        }).start();
    }

    private void refreshCurrentData() {
        switch (currentDateFilter) {
            case "today":
                loadTodayAttendance();
                break;
            case "yesterday":
                loadYesterdayAttendance();
                break;
            case "week":
                loadThisWeekAttendance();
                break;
            case "month":
                loadThisMonthAttendance();
                break;
            default:
                loadTodayAttendance();
                break;
        }
    }

    private void updateDateRangeText(String text) {
        if (dateRangeText != null) {
            dateRangeText.setText(text);
        }
    }

    private void updateEmptyView() {
        if (attendanceRecords.isEmpty()) {
            emptyView.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);

            String emptyMessage = getEmptyMessage();
            emptyView.setText(emptyMessage);
        } else {
            emptyView.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
        }
    }

    private String getEmptyMessage() {
        switch (currentDateFilter) {
            case "today":
                return "📭 No attendance records for today\n\nRecords will appear here when people check in or out.";
            case "yesterday":
                return "📭 No attendance records for yesterday\n\nYesterday was a quiet day!";
            case "week":
                return "📭 No attendance records for this week\n\nThis week's attendance will show up here.";
            case "month":
                return "📭 No attendance records for this month\n\nThis month's attendance will show up here.";
            default:
                return "📭 No attendance records found\n\nRecords will appear here when people check in or out.";
        }
    }

    private void showProgress(boolean show) {
        if (progressBar != null) {
            progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        }

        if (show) {
            emptyView.setVisibility(View.GONE);
            recyclerView.setVisibility(View.GONE);
        }
    }

    private void showError(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        Log.e(TAG, "❌ " + message);
    }

    private void exportAttendanceData() {
        try {
            // Simple export notification - you can implement actual CSV export here
            Toast.makeText(this, "📄 Export feature - Coming Soon!\nWill export " +
                    attendanceRecords.size() + " records", Toast.LENGTH_LONG).show();

            Log.d(TAG, "📄 Export requested for " + attendanceRecords.size() + " records");

        } catch (Exception e) {
            Log.e(TAG, "❌ Error exporting data", e);
            showError("Error exporting data: " + e.getMessage());
        }
    }

    // ==================== DATE UTILITY METHODS ====================

    private String getCurrentDate() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
    }

    private String getYesterdayDate() {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DAY_OF_YEAR, -1);
        return new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(calendar.getTime());
    }

    private String getWeekStartDate() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.DAY_OF_WEEK, calendar.getFirstDayOfWeek());
        return new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(calendar.getTime());
    }

    private String getMonthStartDate() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.DAY_OF_MONTH, 1);
        return new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(calendar.getTime());
    }

    private String getFormattedDate(String dateString) {
        try {
            SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            SimpleDateFormat outputFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
            Date date = inputFormat.parse(dateString);
            return outputFormat.format(date);
        } catch (Exception e) {
            return dateString;
        }
    }

    // ==================== LIFECYCLE METHODS ====================

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Refresh data when returning to this activity
        refreshCurrentData();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (databaseHelper != null) {
            databaseHelper.close();
        }
        Log.d(TAG, "💥 AttendanceRecordsActivity destroyed");
    }
}