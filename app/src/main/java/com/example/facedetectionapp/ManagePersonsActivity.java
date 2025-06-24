package com.example.facedetectionapp;

import android.app.AlertDialog;
import android.content.Intent;
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

import java.util.ArrayList;
import java.util.List;

public class ManagePersonsActivity extends AppCompatActivity implements PersonsAdapter.OnPersonActionListener {
    private static final String TAG = "ManagePersonsActivity";

    private DatabaseHelper databaseHelper;
    private RecyclerView recyclerView;
    private PersonsAdapter adapter;
    private TextView emptyView;
    private ProgressBar progressBar;

    private List<DatabaseHelper.Person> persons;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manage_persons);

        Log.d(TAG, "🚀 ManagePersonsActivity started");

        // Initialize database
        databaseHelper = new DatabaseHelper(this);

        // Setup toolbar
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Manage Persons");
        }

        initializeViews();
        setupRecyclerView();
        loadPersons();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.manage_persons_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.menu_add_person) {
            openRegistrationActivity();
            return true;
        } else if (id == R.id.menu_refresh) {
            refreshPersons();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void initializeViews() {
        recyclerView = findViewById(R.id.personsRecyclerView);
        emptyView = findViewById(R.id.emptyView);
        progressBar = findViewById(R.id.progressBar);

        persons = new ArrayList<>();
    }

    private void setupRecyclerView() {
        adapter = new PersonsAdapter(this, persons, this);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);
    }

    private void loadPersons() {
        showProgress(true);

        new Thread(() -> {
            try {
                List<DatabaseHelper.Person> loadedPersons = databaseHelper.getAllPersons();

                runOnUiThread(() -> {
                    persons.clear();
                    persons.addAll(loadedPersons);
                    adapter.updatePersons(persons);

                    updateEmptyView();
                    showProgress(false);

                    Log.d(TAG, "📊 Loaded " + loadedPersons.size() + " persons");
                });

            } catch (Exception e) {
                Log.e(TAG, "❌ Error loading persons", e);
                runOnUiThread(() -> {
                    showError("Error loading persons: " + e.getMessage());
                    showProgress(false);
                });
            }
        }).start();
    }

    private void refreshPersons() {
        Log.d(TAG, "🔄 Refreshing persons list");
        loadPersons();
    }

    private void updateEmptyView() {
        if (persons.isEmpty()) {
            emptyView.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
            emptyView.setText("👥 No registered persons found\n\nUse the + button to register someone");
        } else {
            emptyView.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
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

    private void openRegistrationActivity() {
        try {
            Intent intent = new Intent(this, RegistrationActivity.class);
            startActivity(intent);
            Log.d(TAG, "🚀 Opening Registration Activity");
        } catch (Exception e) {
            Log.e(TAG, "❌ Error opening Registration Activity", e);
            Toast.makeText(this, "Error opening registration", Toast.LENGTH_SHORT).show();
        }
    }



    @Override
    public void onViewAttendance(DatabaseHelper.Person person) {
        try {
            Intent intent = new Intent(this, PersonAttendanceActivity.class);
            intent.putExtra("person_id", (long) person.id);
            intent.putExtra("person_name", person.name);
            intent.putExtra("employee_id", person.employeeId);
            startActivity(intent);

            Log.d(TAG, "🚀 Opening attendance for: " + person.name);
        } catch (Exception e) {
            Log.e(TAG, "❌ Error opening person attendance", e);
            Toast.makeText(this, "Error opening attendance records", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onEditPerson(DatabaseHelper.Person person) {
        // Placeholder for edit functionality
        Toast.makeText(this, "✏️ Edit functionality for " + person.name + " - Coming Soon!",
                Toast.LENGTH_SHORT).show();

        Log.d(TAG, "✏️ Edit requested for person: " + person.name);

        // TODO: Implement edit person functionality
        // You can create an EditPersonActivity or show a dialog for editing
    }

    @Override
    public void onDeletePerson(DatabaseHelper.Person person) {
        showDeleteConfirmation(person);
    }

    private void showDeleteConfirmation(DatabaseHelper.Person person) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Person")
                .setMessage("Are you sure you want to delete " + person.name + "?\n\n" +
                        "This will mark them as inactive but preserve their attendance history.")
                .setPositiveButton("Delete", (dialog, which) -> {
                    deletePerson(person);
                })
                .setNegativeButton("Cancel", null)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();
    }

    private void deletePerson(DatabaseHelper.Person person) {
        showProgress(true);

        new Thread(() -> {
            try {
                boolean success = databaseHelper.deletePerson(person.id);

                runOnUiThread(() -> {
                    showProgress(false);

                    if (success) {
                        Toast.makeText(this, "✅ " + person.name + " deleted successfully",
                                Toast.LENGTH_SHORT).show();

                        // Remove from local list and update adapter
                        persons.remove(person);
                        adapter.updatePersons(persons);
                        updateEmptyView();

                        Log.d(TAG, "✅ Person deleted: " + person.name);
                    } else {
                        Toast.makeText(this, "❌ Failed to delete " + person.name,
                                Toast.LENGTH_SHORT).show();
                        Log.e(TAG, "❌ Failed to delete person: " + person.name);
                    }
                });

            } catch (Exception e) {
                Log.e(TAG, "❌ Error deleting person: " + person.name, e);
                runOnUiThread(() -> {
                    showProgress(false);
                    showError("Error deleting person: " + e.getMessage());
                });
            }
        }).start();
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
        // Refresh the list when returning to this activity
        refreshPersons();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (databaseHelper != null) {
            databaseHelper.close();
        }
        Log.d(TAG, "💥 ManagePersonsActivity destroyed");
    }
}