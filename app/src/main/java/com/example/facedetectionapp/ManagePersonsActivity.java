package com.example.facedetectionapp;

import android.content.Intent;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Button;
import android.util.Log;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import java.util.List;

public class ManagePersonsActivity extends AppCompatActivity {
    private static final String TAG = "ManagePersonsActivity";

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
            getSupportActionBar().setTitle("Manage Persons");
        }

        createLayout();
        loadPersons();
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
        headerView.setText("👥 Registered Persons");
        headerView.setTextSize(24);
        headerView.setTextColor(ContextCompat.getColor(this, android.R.color.black));
        headerView.setPadding(0, 0, 0, 24);
        headerView.setTypeface(null, android.graphics.Typeface.BOLD);
        mainLayout.addView(headerView);

        scrollView.addView(mainLayout);
        setContentView(scrollView);
    }

    private void loadPersons() {
        try {
            List<DatabaseHelper.Person> persons = databaseHelper.getAllPersons();

            if (persons.isEmpty()) {
                addEmptyMessage();
            } else {
                for (DatabaseHelper.Person person : persons) {
                    addPersonCard(person);
                }
            }

        } catch (Exception e) {
            Log.e(TAG, "❌ Error loading persons: " + e.getMessage());
            addErrorMessage("❌ Error loading persons: " + e.getMessage());
        }
    }

    private void addPersonCard(DatabaseHelper.Person person) {
        // Create card layout
        LinearLayout cardLayout = new LinearLayout(this);
        cardLayout.setOrientation(LinearLayout.VERTICAL);
        cardLayout.setPadding(24, 20, 24, 20);
        cardLayout.setBackgroundColor(ContextCompat.getColor(this, android.R.color.background_light));

        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cardParams.bottomMargin = 16;
        cardLayout.setLayoutParams(cardParams);

        // Person info layout
        LinearLayout infoLayout = new LinearLayout(this);
        infoLayout.setOrientation(LinearLayout.HORIZONTAL);
        infoLayout.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        // Left side - person info
        LinearLayout leftLayout = new LinearLayout(this);
        leftLayout.setOrientation(LinearLayout.VERTICAL);
        leftLayout.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        // Name
        TextView nameView = new TextView(this);
        nameView.setText("👤 " + person.name);
        nameView.setTextSize(18);
        nameView.setTextColor(ContextCompat.getColor(this, android.R.color.black));
        nameView.setTypeface(null, android.graphics.Typeface.BOLD);
        leftLayout.addView(nameView);

        // Employee ID
        TextView empIdView = new TextView(this);
        empIdView.setText("🆔 " + person.employeeId);
        empIdView.setTextSize(14);
        empIdView.setTextColor(ContextCompat.getColor(this, android.R.color.darker_gray));
        empIdView.setPadding(0, 4, 0, 0);
        leftLayout.addView(empIdView);

        // Created date
        TextView dateView = new TextView(this);
        dateView.setText("📅 " + person.createdAt);
        dateView.setTextSize(12);
        dateView.setTextColor(ContextCompat.getColor(this, android.R.color.darker_gray));
        dateView.setPadding(0, 4, 0, 0);
        leftLayout.addView(dateView);

        // Right side - buttons
        LinearLayout buttonLayout = new LinearLayout(this);
        buttonLayout.setOrientation(LinearLayout.VERTICAL);
        buttonLayout.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        // View Attendance button
        Button viewAttendanceBtn = new Button(this);
        viewAttendanceBtn.setText("📊 View Attendance");
        viewAttendanceBtn.setTextSize(12);
        viewAttendanceBtn.setPadding(16, 8, 16, 8);
        viewAttendanceBtn.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_blue_light));
        viewAttendanceBtn.setTextColor(ContextCompat.getColor(this, android.R.color.white));
        viewAttendanceBtn.setOnClickListener(v -> openPersonAttendance(person));
        buttonLayout.addView(viewAttendanceBtn);

        // Edit button (placeholder)
        Button editBtn = new Button(this);
        editBtn.setText("✏️ Edit");
        editBtn.setTextSize(12);
        editBtn.setPadding(16, 8, 16, 8);
        editBtn.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_orange_light));
        editBtn.setTextColor(ContextCompat.getColor(this, android.R.color.white));
        editBtn.setOnClickListener(v -> editPerson(person));
        LinearLayout.LayoutParams editParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        editParams.topMargin = 8;
        editBtn.setLayoutParams(editParams);
        buttonLayout.addView(editBtn);

        // Assemble the card
        infoLayout.addView(leftLayout);
        infoLayout.addView(buttonLayout);
        cardLayout.addView(infoLayout);

        mainLayout.addView(cardLayout);
    }

    private void openPersonAttendance(DatabaseHelper.Person person) {
        Intent intent = new Intent(this, PersonAttendanceActivity.class);
        intent.putExtra("person_id", person.id);
        intent.putExtra("person_name", person.name);
        intent.putExtra("employee_id", person.employeeId);
        startActivity(intent);
    }

    private void editPerson(DatabaseHelper.Person person) {
        // Placeholder for edit functionality
        android.widget.Toast.makeText(this,
                "Edit functionality for " + person.name + " coming soon!",
                android.widget.Toast.LENGTH_SHORT).show();
    }

    private void addEmptyMessage() {
        TextView emptyView = new TextView(this);
        emptyView.setText("📭 No persons registered yet\n\nUse the main screen menu to register new persons.");
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