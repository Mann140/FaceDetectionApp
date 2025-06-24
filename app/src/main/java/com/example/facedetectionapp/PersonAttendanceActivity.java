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

public class PersonAttendanceActivity extends AppCompatActivity {
    private static final String TAG = "PersonAttendanceActivity";

    private DatabaseHelper databaseHelper;
    private RecyclerView recyclerView;
    private AttendanceRecordsAdapter adapter;
    private TextView emptyView;
    private TextView personInfoText;
    private TextView dateRangeText;
    private ProgressBar progressBar;

    private List<DatabaseHelper.AttendanceRecord> attendanceRecords;
    private long personId;
    private String personName;
    private String employeeId;
    private String currentDateFilter = "today";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_person_attendance);

        Log.d(TAG, "🚀 PersonAttendanceActivity started");

        // Initialize database
        databaseHelper = new DatabaseHelper(this);

        // Get person data from intent
        personId = getIntent().getLongExtra("person_id", -1);
        personName = getIntent().getStringExtra("person_name");
        employeeId = getIntent().getStringExtra("employee_id");

        // Validate person data
        if (personId == -1 || personName == null) {
            Log.e(TAG, "❌ Invalid person data received");
            Toast.makeText(this, "Error: Invalid person data", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        // Setup toolbar
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Attendance - " + personName);
        }

        initializeViews();
        setupRecyclerView();
        updatePersonInfo();
        loadTodayAttendance();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.person_attendance_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.menu_today) {
            loadTodayAttendance();
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
        }

        return super.onOptionsItemSelected(item);
    }

    private void initializeViews() {
        recyclerView = findViewById(R.id.attendanceRecyclerView);
        emptyView = findViewById(R.id.emptyView);
        personInfoText = findViewById(R.id.personInfoText);
        dateRangeText = findViewById(R.id.dateRangeText);
        progressBar = findViewById(R.id.progressBar);

        attendanceRecords = new ArrayList<>();
    }

    private void setupRecyclerView() {
        adapter = new AttendanceRecordsAdapter(this, attendanceRecords);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);
    }

    private void updatePersonInfo() {
        if (personInfoText != null) {
            String infoText = "👤 " + personName;
            if (employeeId != null && !employeeId.isEmpty()) {
                infoText += " (" + employeeId + ")";
            }
            personInfoText.setText(infoText);
        }
    }

    private void loadTodayAttendance() {
        currentDateFilter = "today";
        String today = getCurrentDate();
        updateDateRangeText("📅 Today - " + getFormattedDate(today));
        loadAttendanceForDate(today);
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
                List<DatabaseHelper.AttendanceRecord> allRecords = databaseHelper.getAttendanceByDate(date);

                // Filter records for this specific person
                List<DatabaseHelper.AttendanceRecord> personRecords = new ArrayList<>();
                for (DatabaseHelper.AttendanceRecord record : allRecords) {
                    if (record.personId == personId) {
                        personRecords.add(record);
                    }
                }

                runOnUiThread(() -> {
                    attendanceRecords.clear();
                    attendanceRecords.addAll(personRecords);
                    adapter.updateRecords(attendanceRecords);

                    updateEmptyView();
                    showProgress(false);

                    Log.d(TAG, "📊 Loaded " + personRecords.size() + " records for " + personName + " on " + date);
                });

            } catch (Exception e) {
                Log.e(TAG, "❌ Error loading attendance for person: " + personName, e);
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
                List<DatabaseHelper.AttendanceRecord> allRecords = new ArrayList<>();

                Calendar start = Calendar.getInstance();
                Calendar end = Calendar.getInstance();
                SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

                start.setTime(dateFormat.parse(startDate));
                end.setTime(dateFormat.parse(endDate));

                while (!start.after(end)) {
                    String currentDate = dateFormat.format(start.getTime());
                    List<DatabaseHelper.AttendanceRecord> dayRecords = databaseHelper.getAttendanceByDate(currentDate);

                    // Filter for this person
                    for (DatabaseHelper.AttendanceRecord record : dayRecords) {
                        if (record.personId == personId) {
                            allRecords.add(record);
                        }
                    }

                    start.add(Calendar.DAY_OF_MONTH, 1);
                }

                runOnUiThread(() -> {
                    attendanceRecords.clear();
                    attendanceRecords.addAll(allRecords);
                    adapter.updateRecords(attendanceRecords);

                    updateEmptyView();
                    showProgress(false);

                    Log.d(TAG, "📊 Loaded " + allRecords.size() + " records for " + personName + " in date range");
                });

            } catch (Exception e) {
                Log.e(TAG, "❌ Error loading attendance range for person: " + personName, e);
                runOnUiThread(() -> {
                    showError("Error loading attendance records: " + e.getMessage());
                    showProgress(false);
                });
            }
        }).start();
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
        String personDisplayName = personName != null ? personName : "this person";

        switch (currentDateFilter) {
            case "today":
                return "📭 No attendance records for " + personDisplayName + " today\n\nRecords will appear when they check in or out.";
            case "week":
                return "📭 No attendance records for " + personDisplayName + " this week\n\nThis week's attendance will show up here.";
            case "month":
                return "📭 No attendance records for " + personDisplayName + " this month\n\nThis month's attendance will show up here.";
            default:
                return "📭 No attendance records found for " + personDisplayName + "\n\nRecords will appear when they check in or out.";
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

    // ==================== DATE UTILITY METHODS ====================

    private String getCurrentDate() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
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

    // ==================== ANALYTICS METHODS ====================

    private void calculateAttendanceStats() {
        if (attendanceRecords.isEmpty()) {
            return;
        }

        try {
            int checkIns = 0;
            int checkOuts = 0;
            int totalHours = 0; // You can implement hour calculation logic

            for (DatabaseHelper.AttendanceRecord record : attendanceRecords) {
                if ("check_in".equals(record.actionType.toLowerCase())) {
                    checkIns++;
                } else if ("check_out".equals(record.actionType.toLowerCase())) {
                    checkOuts++;
                }
            }

            // You can display these stats in a summary view
            Log.d(TAG, "📊 Stats for " + personName + " - Check-ins: " + checkIns + ", Check-outs: " + checkOuts);

        } catch (Exception e) {
            Log.e(TAG, "❌ Error calculating attendance stats", e);
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
        // Refresh current data when returning to this activity
        refreshCurrentData();
    }

    private void refreshCurrentData() {
        switch (currentDateFilter) {
            case "today":
                loadTodayAttendance();
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (databaseHelper != null) {
            databaseHelper.close();
        }
        Log.d(TAG, "💥 PersonAttendanceActivity destroyed");
    }
}