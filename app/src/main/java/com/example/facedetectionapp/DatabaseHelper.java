package com.example.facedetectionapp;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;
import java.util.ArrayList;
import java.util.List;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class DatabaseHelper extends SQLiteOpenHelper {
    private static final String TAG = "DatabaseHelper";

    // Database constants
    private static final String DATABASE_NAME = "FaceDetectionApp.db";
    private static final int DATABASE_VERSION = 5; // Updated version

    // Table names
    private static final String TABLE_PERSONS = "persons";
    private static final String TABLE_ATTENDANCE = "attendance";

    // Persons table columns
    private static final String COLUMN_ID = "id";
    private static final String COLUMN_NAME = "name";
    private static final String COLUMN_EMPLOYEE_ID = "employee_id";
    private static final String COLUMN_FACE_ENCODING = "face_encoding";
    private static final String COLUMN_IS_ACTIVE = "is_active";
    private static final String COLUMN_CREATED_AT = "created_at";
    private static final String COLUMN_UPDATED_AT = "updated_at";

    // Attendance table columns
    private static final String COLUMN_PERSON_ID = "person_id";
    private static final String COLUMN_ACTION_TYPE = "action_type";
    private static final String COLUMN_DATE = "date";
    private static final String COLUMN_TIMESTAMP = "timestamp";
    private static final String COLUMN_CONFIDENCE = "confidence";
    private static final String COLUMN_LOCATION = "location";

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
        Log.d(TAG, "🗄️ Database helper initialized with version " + DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        Log.d(TAG, "🗄️ Creating database tables...");

        try {
            // Create persons table
            String createPersonsTable = "CREATE TABLE " + TABLE_PERSONS + " (" +
                    COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    COLUMN_NAME + " TEXT NOT NULL, " +
                    COLUMN_EMPLOYEE_ID + " TEXT UNIQUE NOT NULL, " +
                    COLUMN_FACE_ENCODING + " BLOB, " +
                    COLUMN_IS_ACTIVE + " INTEGER DEFAULT 1, " +
                    COLUMN_CREATED_AT + " TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                    COLUMN_UPDATED_AT + " TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                    ")";

            db.execSQL(createPersonsTable);
            Log.d(TAG, "✅ Persons table created successfully");

            // Create attendance table
            String createAttendanceTable = "CREATE TABLE " + TABLE_ATTENDANCE + " (" +
                    COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    COLUMN_PERSON_ID + " INTEGER NOT NULL, " +
                    COLUMN_ACTION_TYPE + " TEXT NOT NULL, " +
                    COLUMN_DATE + " TEXT NOT NULL, " +
                    COLUMN_TIMESTAMP + " TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                    COLUMN_CONFIDENCE + " REAL, " +
                    COLUMN_LOCATION + " TEXT, " +
                    "FOREIGN KEY(" + COLUMN_PERSON_ID + ") REFERENCES " + TABLE_PERSONS + "(" + COLUMN_ID + ")" +
                    ")";

            db.execSQL(createAttendanceTable);
            Log.d(TAG, "✅ Attendance table created successfully");

            // Create indexes for better performance
            db.execSQL("CREATE INDEX idx_persons_employee_id ON " + TABLE_PERSONS + "(" + COLUMN_EMPLOYEE_ID + ")");
            db.execSQL("CREATE INDEX idx_attendance_person_date ON " + TABLE_ATTENDANCE + "(" + COLUMN_PERSON_ID + ", " + COLUMN_DATE + ")");
            Log.d(TAG, "✅ Database indexes created successfully");

        } catch (SQLException e) {
            Log.e(TAG, "❌ Error creating tables", e);
        }
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        Log.d(TAG, "🔄 Upgrading database from version " + oldVersion + " to " + newVersion);

        try {
            if (oldVersion < 2) {
                // Add action_type column if upgrading from version 1
                db.execSQL("ALTER TABLE " + TABLE_ATTENDANCE + " ADD COLUMN " + COLUMN_ACTION_TYPE + " TEXT DEFAULT 'check_in'");
                Log.d(TAG, "✅ Added action_type column to attendance table");
            }

            if (oldVersion < 3) {
                // Create persons table if upgrading from version 2
                String createPersonsTable = "CREATE TABLE IF NOT EXISTS " + TABLE_PERSONS + " (" +
                        COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                        COLUMN_NAME + " TEXT NOT NULL, " +
                        COLUMN_EMPLOYEE_ID + " TEXT UNIQUE NOT NULL, " +
                        COLUMN_FACE_ENCODING + " BLOB, " +
                        COLUMN_IS_ACTIVE + " INTEGER DEFAULT 1, " +
                        COLUMN_CREATED_AT + " TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                        COLUMN_UPDATED_AT + " TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                        ")";
                db.execSQL(createPersonsTable);
                Log.d(TAG, "✅ Created persons table during upgrade");
            }

            if (oldVersion < 4) {
                // Add confidence column to attendance table
                try {
                    db.execSQL("ALTER TABLE " + TABLE_ATTENDANCE + " ADD COLUMN " + COLUMN_CONFIDENCE + " REAL");
                    Log.d(TAG, "✅ Added confidence column to attendance table");
                } catch (SQLException e) {
                    Log.w(TAG, "⚠️ Confidence column might already exist");
                }

                // Create indexes
                try {
                    db.execSQL("CREATE INDEX IF NOT EXISTS idx_persons_employee_id ON " + TABLE_PERSONS + "(" + COLUMN_EMPLOYEE_ID + ")");
                    db.execSQL("CREATE INDEX IF NOT EXISTS idx_attendance_person_date ON " + TABLE_ATTENDANCE + "(" + COLUMN_PERSON_ID + ", " + COLUMN_DATE + ")");
                    Log.d(TAG, "✅ Created database indexes during upgrade");
                } catch (SQLException e) {
                    Log.w(TAG, "⚠️ Some indexes might already exist");
                }
            }

            if (oldVersion < 5) {
                // Add missing columns
                try {
                    db.execSQL("ALTER TABLE " + TABLE_PERSONS + " ADD COLUMN " + COLUMN_CREATED_AT + " TIMESTAMP DEFAULT CURRENT_TIMESTAMP");
                    Log.d(TAG, "✅ Added created_at column to persons table");
                } catch (SQLException e) {
                    Log.w(TAG, "⚠️ created_at column might already exist");
                }

                try {
                    db.execSQL("ALTER TABLE " + TABLE_PERSONS + " ADD COLUMN " + COLUMN_UPDATED_AT + " TIMESTAMP DEFAULT CURRENT_TIMESTAMP");
                    Log.d(TAG, "✅ Added updated_at column to persons table");
                } catch (SQLException e) {
                    Log.w(TAG, "⚠️ updated_at column might already exist");
                }

                try {
                    db.execSQL("ALTER TABLE " + TABLE_ATTENDANCE + " ADD COLUMN " + COLUMN_LOCATION + " TEXT");
                    Log.d(TAG, "✅ Added location column to attendance table");
                } catch (SQLException e) {
                    Log.w(TAG, "⚠️ location column might already exist");
                }
            }

        } catch (SQLException e) {
            Log.e(TAG, "❌ Error during database upgrade", e);
        }
    }



    /**
     * Save a new person with face encoding
     */
    public boolean savePerson(String name, String employeeId, byte[] faceEncoding) {
        SQLiteDatabase db = null;
        try {
            db = this.getWritableDatabase();
            ContentValues values = new ContentValues();
            values.put(COLUMN_NAME, name);
            values.put(COLUMN_EMPLOYEE_ID, employeeId);
            values.put(COLUMN_FACE_ENCODING, faceEncoding);
            values.put(COLUMN_IS_ACTIVE, 1);

            long result = db.insert(TABLE_PERSONS, null, values);

            if (result != -1) {
                Log.d(TAG, "✅ Person saved successfully: " + name + " (ID: " + result + ")");
                return true;
            } else {
                Log.e(TAG, "❌ Failed to save person: " + name);
                return false;
            }

        } catch (SQLException e) {
            Log.e(TAG, "❌ Error saving person: " + name, e);
            return false;
        } finally {
            if (db != null && db.isOpen()) {
                db.close();
            }
        }
    }

    /**
     * Get all active persons
     */
    public List<Person> getAllPersons() {
        List<Person> persons = new ArrayList<>();
        SQLiteDatabase db = null;
        Cursor cursor = null;

        try {
            db = this.getReadableDatabase();
            String query = "SELECT * FROM " + TABLE_PERSONS + " WHERE " + COLUMN_IS_ACTIVE + "=1 ORDER BY " + COLUMN_NAME + " ASC";
            cursor = db.rawQuery(query, null);

            if (cursor.moveToFirst()) {
                do {
                    Person person = new Person();
                    person.id = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_ID));
                    person.name = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_NAME));
                    person.employeeId = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_EMPLOYEE_ID));
                    person.faceEncoding = cursor.getBlob(cursor.getColumnIndexOrThrow(COLUMN_FACE_ENCODING));
                    person.isActive = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_IS_ACTIVE)) == 1;

                    // Handle timestamp fields safely
                    try {
                        person.createdAt = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_CREATED_AT));
                        person.updatedAt = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_UPDATED_AT));
                    } catch (Exception e) {
                        // If columns don't exist, use current time as fallback
                        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
                        String currentTime = sdf.format(new Date());
                        person.createdAt = currentTime;
                        person.updatedAt = currentTime;
                    }

                    persons.add(person);
                } while (cursor.moveToNext());
            }

            Log.d(TAG, "📊 Retrieved " + persons.size() + " active persons");
            return persons;

        } catch (SQLException e) {
            Log.e(TAG, "❌ Error getting all persons", e);
            return persons;
        } finally {
            if (cursor != null) {
                cursor.close();
            }
            if (db != null && db.isOpen()) {
                db.close();
            }
        }
    }

    /**
     * Get person by employee ID
     */
    public Person getPersonByEmployeeId(String employeeId) {
        SQLiteDatabase db = null;
        Cursor cursor = null;

        try {
            db = this.getReadableDatabase();
            String query = "SELECT * FROM " + TABLE_PERSONS + " WHERE " + COLUMN_EMPLOYEE_ID + "=? AND " + COLUMN_IS_ACTIVE + "=1";
            cursor = db.rawQuery(query, new String[]{employeeId});

            if (cursor.moveToFirst()) {
                Person person = new Person();
                person.id = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_ID));
                person.name = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_NAME));
                person.employeeId = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_EMPLOYEE_ID));
                person.faceEncoding = cursor.getBlob(cursor.getColumnIndexOrThrow(COLUMN_FACE_ENCODING));
                person.isActive = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_IS_ACTIVE)) == 1;

                try {
                    person.createdAt = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_CREATED_AT));
                    person.updatedAt = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_UPDATED_AT));
                } catch (Exception e) {
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
                    String currentTime = sdf.format(new Date());
                    person.createdAt = currentTime;
                    person.updatedAt = currentTime;
                }

                return person;
            }

            return null;

        } catch (SQLException e) {
            Log.e(TAG, "❌ Error getting person by employee ID: " + employeeId, e);
            return null;
        } finally {
            if (cursor != null) {
                cursor.close();
            }
            if (db != null && db.isOpen()) {
                db.close();
            }
        }
    }

    /**
     * Update person's face encoding
     */
    public boolean updatePersonFaceEncoding(int personId, byte[] faceEncoding) {
        SQLiteDatabase db = null;
        try {
            db = this.getWritableDatabase();
            ContentValues values = new ContentValues();
            values.put(COLUMN_FACE_ENCODING, faceEncoding);
            values.put(COLUMN_UPDATED_AT, System.currentTimeMillis());

            int result = db.update(TABLE_PERSONS, values, COLUMN_ID + "=?", new String[]{String.valueOf(personId)});

            if (result > 0) {
                Log.d(TAG, "✅ Person face encoding updated successfully: " + personId);
                return true;
            } else {
                Log.e(TAG, "❌ Failed to update person face encoding: " + personId);
                return false;
            }

        } catch (SQLException e) {
            Log.e(TAG, "❌ Error updating person face encoding: " + personId, e);
            return false;
        } finally {
            if (db != null && db.isOpen()) {
                db.close();
            }
        }
    }

    /**
     * Delete person (soft delete - mark as inactive)
     */
    public boolean deletePerson(int personId) {
        SQLiteDatabase db = null;
        try {
            db = this.getWritableDatabase();
            ContentValues values = new ContentValues();
            values.put(COLUMN_IS_ACTIVE, 0);
            values.put(COLUMN_UPDATED_AT, System.currentTimeMillis());

            int result = db.update(TABLE_PERSONS, values, COLUMN_ID + "=?", new String[]{String.valueOf(personId)});

            if (result > 0) {
                Log.d(TAG, "✅ Person deleted successfully: " + personId);
                return true;
            } else {
                Log.e(TAG, "❌ Failed to delete person: " + personId);
                return false;
            }

        } catch (SQLException e) {
            Log.e(TAG, "❌ Error deleting person: " + personId, e);
            return false;
        } finally {
            if (db != null && db.isOpen()) {
                db.close();
            }
        }
    }



    /**
     * Record attendance
     */
    public boolean recordAttendance(int personId, String actionType, float confidence) {
        SQLiteDatabase db = null;
        try {
            db = this.getWritableDatabase();

            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            String today = dateFormat.format(new Date());

            ContentValues values = new ContentValues();
            values.put(COLUMN_PERSON_ID, personId);
            values.put(COLUMN_ACTION_TYPE, actionType);
            values.put(COLUMN_DATE, today);
            values.put(COLUMN_CONFIDENCE, confidence);

            long result = db.insert(TABLE_ATTENDANCE, null, values);

            if (result != -1) {
                Log.d(TAG, "✅ Attendance recorded successfully: Person " + personId + " - " + actionType);
                return true;
            } else {
                Log.e(TAG, "❌ Failed to record attendance: Person " + personId);
                return false;
            }

        } catch (SQLException e) {
            Log.e(TAG, "❌ Error recording attendance", e);
            return false;
        } finally {
            if (db != null && db.isOpen()) {
                db.close();
            }
        }
    }

    /**
     * Get today's attendance statistics
     */
    public Stats getTodaysStats() {
        SQLiteDatabase db = null;
        Cursor cursor = null;

        try {
            db = this.getReadableDatabase();
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            String today = dateFormat.format(new Date());

            String query = "SELECT " + COLUMN_ACTION_TYPE + ", COUNT(*) as count FROM " + TABLE_ATTENDANCE +
                    " WHERE " + COLUMN_DATE + " = ? GROUP BY " + COLUMN_ACTION_TYPE;
            cursor = db.rawQuery(query, new String[]{today});

            int checkedIn = 0;
            int checkedOut = 0;

            if (cursor.moveToFirst()) {
                do {
                    String actionType = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_ACTION_TYPE));
                    int count = cursor.getInt(cursor.getColumnIndexOrThrow("count"));

                    if ("check_in".equals(actionType) || "CHECK_IN".equals(actionType)) {
                        checkedIn = count;
                    } else if ("check_out".equals(actionType) || "CHECK_OUT".equals(actionType)) {
                        checkedOut = count;
                    }
                } while (cursor.moveToNext());
            }

            int total = checkedIn + checkedOut;
            int present = Math.max(0, checkedIn - checkedOut);

            Stats stats = new Stats(total, checkedIn, checkedOut, present);
            Log.d(TAG, "📊 Today's stats: " + stats.toString());
            return stats;

        } catch (SQLException e) {
            Log.e(TAG, "❌ Error getting today's stats", e);
            return new Stats(0, 0, 0, 0);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
            if (db != null && db.isOpen()) {
                db.close();
            }
        }
    }

    /**
     * Get attendance records for a specific date
     */
    public List<AttendanceRecord> getAttendanceByDate(String date) {
        List<AttendanceRecord> records = new ArrayList<>();
        SQLiteDatabase db = null;
        Cursor cursor = null;

        try {
            db = this.getReadableDatabase();
            String query = "SELECT a.*, p." + COLUMN_NAME + ", p." + COLUMN_EMPLOYEE_ID +
                    " FROM " + TABLE_ATTENDANCE + " a" +
                    " JOIN " + TABLE_PERSONS + " p ON a." + COLUMN_PERSON_ID + " = p." + COLUMN_ID +
                    " WHERE a." + COLUMN_DATE + " = ?" +
                    " ORDER BY a." + COLUMN_TIMESTAMP + " DESC";

            cursor = db.rawQuery(query, new String[]{date});

            if (cursor.moveToFirst()) {
                do {
                    AttendanceRecord record = new AttendanceRecord();
                    record.id = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_ID));
                    record.personId = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_PERSON_ID));
                    record.personName = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_NAME));
                    record.personEmployeeId = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_EMPLOYEE_ID));
                    record.actionType = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_ACTION_TYPE));
                    record.date = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_DATE));
                    record.timestamp = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_TIMESTAMP));
                    record.confidence = cursor.getFloat(cursor.getColumnIndexOrThrow(COLUMN_CONFIDENCE));

                    try {
                        record.location = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_LOCATION));
                    } catch (Exception e) {
                        record.location = "";
                    }

                    records.add(record);
                } while (cursor.moveToNext());
            }

            Log.d(TAG, "📊 Retrieved " + records.size() + " attendance records for " + date);
            return records;

        } catch (SQLException e) {
            Log.e(TAG, "❌ Error getting attendance by date: " + date, e);
            return records;
        } finally {
            if (cursor != null) {
                cursor.close();
            }
            if (db != null && db.isOpen()) {
                db.close();
            }
        }
    }

    // ==================== DATA CLASSES ====================

    /**
     * Person data class
     */
    public static class Person {
        public int id;
        public String name;
        public String employeeId;
        public byte[] faceEncoding;
        public boolean isActive;
        public String createdAt;
        public String updatedAt;

        public Person() {
            this.isActive = true;
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
            String currentTime = sdf.format(new Date());
            this.createdAt = currentTime;
            this.updatedAt = currentTime;
        }

        public String getFormattedCreatedDate() {
            try {
                SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
                SimpleDateFormat outputFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
                Date date = inputFormat.parse(createdAt);
                return outputFormat.format(date);
            } catch (Exception e) {
                return createdAt != null ? createdAt : "Unknown";
            }
        }

        @Override
        public String toString() {
            return "Person{id=" + id + ", name='" + name + "', employeeId='" + employeeId +
                    "', isActive=" + isActive + ", hasEncoding=" + (faceEncoding != null) +
                    ", createdAt='" + createdAt + "'}";
        }
    }

    /**
     * Attendance statistics data class
     */
    public static class Stats {
        public int total;
        public int checkedIn;
        public int checkedOut;
        public int present;

        public Stats(int total, int checkedIn, int checkedOut, int present) {
            this.total = total;
            this.checkedIn = checkedIn;
            this.checkedOut = checkedOut;
            this.present = present;
        }

        @Override
        public String toString() {
            return "Stats{total=" + total + ", checkedIn=" + checkedIn +
                    ", checkedOut=" + checkedOut + ", present=" + present + "}";
        }
    }

    /**
     * Attendance record data class
     */
    public static class AttendanceRecord {
        public int id;
        public int personId;
        public String personName;
        public String personEmployeeId;
        public String actionType;
        public String date;
        public String timestamp;
        public float confidence;
        public String location;

        public AttendanceRecord() {
            this.location = "";
        }

        public String getFormattedTime() {
            try {
                SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
                SimpleDateFormat outputFormat = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
                Date date = inputFormat.parse(timestamp);
                return outputFormat.format(date);
            } catch (Exception e) {
                if (timestamp != null && timestamp.contains(" ")) {
                    String[] parts = timestamp.split(" ");
                    if (parts.length > 1) {
                        return parts[1];
                    }
                }
                return timestamp != null ? timestamp : "";
            }
        }

        public String getFormattedDate() {
            try {
                SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
                SimpleDateFormat outputFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
                Date date = inputFormat.parse(timestamp);
                return outputFormat.format(date);
            } catch (Exception e) {
                return this.date != null ? this.date : "";
            }
        }

        @Override
        public String toString() {
            return "AttendanceRecord{id=" + id + ", personName='" + personName +
                    "', personEmployeeId='" + personEmployeeId + "', actionType='" + actionType +
                    "', date='" + date + "', confidence=" + confidence + "}";
        }
    }
}