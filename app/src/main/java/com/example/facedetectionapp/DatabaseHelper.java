package com.example.facedetectionapp;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class DatabaseHelper extends SQLiteOpenHelper {
    private static final String TAG = "DatabaseHelper";
    private static final String DATABASE_NAME = "attendance.db";
    private static final int DATABASE_VERSION = 2;

    // Tables
    private static final String TABLE_PERSONS = "persons";
    private static final String TABLE_ATTENDANCE = "attendance";

    // Person table columns
    private static final String PERSON_ID = "id";
    private static final String PERSON_NAME = "name";
    private static final String PERSON_EMPLOYEE_ID = "employee_id";
    private static final String PERSON_FACE_EMBEDDING = "face_embedding";
    private static final String PERSON_IS_ACTIVE = "is_active";
    private static final String PERSON_CREATED_AT = "created_at";

    // Attendance table columns
    private static final String ATTENDANCE_ID = "id";
    private static final String ATTENDANCE_PERSON_ID = "person_id";
    private static final String ATTENDANCE_ACTION_TYPE = "action_type";
    private static final String ATTENDANCE_TIMESTAMP = "timestamp";
    private static final String ATTENDANCE_DATE = "date";
    private static final String ATTENDANCE_TIME = "time";
    private static final String ATTENDANCE_CONFIDENCE = "confidence";

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
        Log.d(TAG, "🗄️ Database helper initialized");
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        Log.d(TAG, "🏗️ Creating database tables");

        // Create persons table
        String createPersonsTable = "CREATE TABLE " + TABLE_PERSONS + " (" +
                PERSON_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                PERSON_NAME + " TEXT NOT NULL, " +
                PERSON_EMPLOYEE_ID + " TEXT UNIQUE NOT NULL, " +
                PERSON_FACE_EMBEDDING + " BLOB NOT NULL, " +
                PERSON_IS_ACTIVE + " INTEGER DEFAULT 1, " +
                PERSON_CREATED_AT + " TEXT NOT NULL" +
                ")";

        // Create attendance table
        String createAttendanceTable = "CREATE TABLE " + TABLE_ATTENDANCE + " (" +
                ATTENDANCE_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                ATTENDANCE_PERSON_ID + " INTEGER NOT NULL, " +
                ATTENDANCE_ACTION_TYPE + " TEXT NOT NULL, " +
                ATTENDANCE_TIMESTAMP + " TEXT NOT NULL, " +
                ATTENDANCE_DATE + " TEXT NOT NULL, " +
                ATTENDANCE_TIME + " TEXT NOT NULL, " +
                ATTENDANCE_CONFIDENCE + " REAL DEFAULT 0.0, " +
                "FOREIGN KEY(" + ATTENDANCE_PERSON_ID + ") REFERENCES " + TABLE_PERSONS + "(" + PERSON_ID + ")" +
                ")";

        db.execSQL(createPersonsTable);
        db.execSQL(createAttendanceTable);

        Log.d(TAG, "✅ Database tables created successfully");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        Log.d(TAG, "🔄 Upgrading database from version " + oldVersion + " to " + newVersion);

        // Drop existing tables
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_ATTENDANCE);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_PERSONS);

        // Recreate tables
        onCreate(db);

        Log.d(TAG, "✅ Database upgrade completed");
    }

    // Person operations
    public synchronized long addPerson(String name, String employeeId, byte[] faceEmbedding) {
        SQLiteDatabase db = null;
        try {
            db = this.getWritableDatabase();
            ContentValues values = new ContentValues();
            values.put(PERSON_NAME, name);
            values.put(PERSON_EMPLOYEE_ID, employeeId);
            values.put(PERSON_FACE_EMBEDDING, faceEmbedding);
            values.put(PERSON_IS_ACTIVE, 1);
            values.put(PERSON_CREATED_AT, getCurrentTimestamp());

            long result = db.insert(TABLE_PERSONS, null, values);
            if (result != -1) {
                Log.d(TAG, "✅ Person added: " + name + " (ID: " + employeeId + ")");
            } else {
                Log.e(TAG, "❌ Failed to add person: " + name);
            }
            return result;
        } catch (Exception e) {
            Log.e(TAG, "❌ Error adding person: " + e.getMessage());
            return -1;
        } finally {
            // Don't close db here as it's managed by the helper
        }
    }

    public synchronized List<Person> getAllPersons() {
        List<Person> persons = new ArrayList<>();
        SQLiteDatabase db = null;
        Cursor cursor = null;

        try {
            db = this.getReadableDatabase();
            cursor = db.query(TABLE_PERSONS, null, PERSON_IS_ACTIVE + "=1", null, null, null, PERSON_NAME + " ASC");

            while (cursor.moveToNext()) {
                Person person = new Person();
                person.id = cursor.getLong(cursor.getColumnIndexOrThrow(PERSON_ID));
                person.name = cursor.getString(cursor.getColumnIndexOrThrow(PERSON_NAME));
                person.employeeId = cursor.getString(cursor.getColumnIndexOrThrow(PERSON_EMPLOYEE_ID));
                person.embedding = cursor.getBlob(cursor.getColumnIndexOrThrow(PERSON_FACE_EMBEDDING));
                person.isActive = cursor.getInt(cursor.getColumnIndexOrThrow(PERSON_IS_ACTIVE)) == 1;
                person.createdAt = cursor.getString(cursor.getColumnIndexOrThrow(PERSON_CREATED_AT));
                persons.add(person);
            }

            Log.d(TAG, "📊 Retrieved " + persons.size() + " active persons");
            return persons;

        } catch (Exception e) {
            Log.e(TAG, "❌ Error getting all persons: " + e.getMessage());
            return new ArrayList<>();
        } finally {
            if (cursor != null && !cursor.isClosed()) {
                cursor.close();
            }
            // Don't close db here as it's managed by the helper
        }
    }

    public synchronized Person getPersonByEmployeeId(String employeeId) {
        SQLiteDatabase db = null;
        Cursor cursor = null;

        try {
            db = this.getReadableDatabase();
            cursor = db.query(TABLE_PERSONS, null, PERSON_EMPLOYEE_ID + "=? AND " + PERSON_IS_ACTIVE + "=1",
                    new String[]{employeeId}, null, null, null);

            if (cursor.moveToFirst()) {
                Person person = new Person();
                person.id = cursor.getLong(cursor.getColumnIndexOrThrow(PERSON_ID));
                person.name = cursor.getString(cursor.getColumnIndexOrThrow(PERSON_NAME));
                person.employeeId = cursor.getString(cursor.getColumnIndexOrThrow(PERSON_EMPLOYEE_ID));
                person.embedding = cursor.getBlob(cursor.getColumnIndexOrThrow(PERSON_FACE_EMBEDDING));
                person.isActive = cursor.getInt(cursor.getColumnIndexOrThrow(PERSON_IS_ACTIVE)) == 1;
                person.createdAt = cursor.getString(cursor.getColumnIndexOrThrow(PERSON_CREATED_AT));
                return person;
            }
            return null;

        } catch (Exception e) {
            Log.e(TAG, "❌ Error getting person by employee ID: " + e.getMessage());
            return null;
        } finally {
            if (cursor != null && !cursor.isClosed()) {
                cursor.close();
            }
        }
    }

    // Attendance operations
    public synchronized long addAttendance(long personId, String actionType, double confidence) {
        SQLiteDatabase db = null;
        try {
            db = this.getWritableDatabase();
            String timestamp = getCurrentTimestamp();
            String date = getCurrentDate();
            String time = getCurrentTime();

            ContentValues values = new ContentValues();
            values.put(ATTENDANCE_PERSON_ID, personId);
            values.put(ATTENDANCE_ACTION_TYPE, actionType);
            values.put(ATTENDANCE_TIMESTAMP, timestamp);
            values.put(ATTENDANCE_DATE, date);
            values.put(ATTENDANCE_TIME, time);
            values.put(ATTENDANCE_CONFIDENCE, confidence);

            long result = db.insert(TABLE_ATTENDANCE, null, values);
            if (result != -1) {
                Log.d(TAG, "✅ Attendance recorded: " + actionType + " for person ID " + personId);
            }
            return result;

        } catch (Exception e) {
            Log.e(TAG, "❌ Error adding attendance: " + e.getMessage());
            return -1;
        } finally {
            // Don't close db here as it's managed by the helper
        }
    }

    public synchronized Stats getTodayStats() {
        SQLiteDatabase db = null;
        Cursor cursor = null;
        try {
            db = this.getReadableDatabase();
            String today = getCurrentDate();

            String query = "SELECT " + ATTENDANCE_ACTION_TYPE + ", COUNT(*) as count FROM " + TABLE_ATTENDANCE +
                    " WHERE " + ATTENDANCE_DATE + " = ? GROUP BY " + ATTENDANCE_ACTION_TYPE;
            cursor = db.rawQuery(query, new String[]{today});

            int checkedIn = 0;
            int checkedOut = 0;

            while (cursor.moveToNext()) {
                String actionType = cursor.getString(0);
                int count = cursor.getInt(1);

                if ("CHECK_IN".equals(actionType)) {
                    checkedIn = count;
                } else if ("CHECK_OUT".equals(actionType)) {
                    checkedOut = count;
                }
            }

            int total = checkedIn + checkedOut;
            int present = checkedIn - checkedOut;
            if (present < 0) present = 0;

            Stats stats = new Stats(total, checkedIn, checkedOut, present);
            Log.d(TAG, "📊 Today's stats: " + stats);
            return stats;

        } catch (Exception e) {
            Log.e(TAG, "❌ Error getting today's stats: " + e.getMessage());
            return new Stats(0, 0, 0, 0);
        } finally {
            if (cursor != null && !cursor.isClosed()) {
                cursor.close();
            }
            // Don't close db here as it's managed by the helper
        }
    }

    public synchronized List<AttendanceRecord> getAttendanceByDate(String date) {
        List<AttendanceRecord> records = new ArrayList<>();
        SQLiteDatabase db = null;
        Cursor cursor = null;

        try {
            db = this.getReadableDatabase();
            String query = "SELECT a.*, p." + PERSON_NAME + ", p." + PERSON_EMPLOYEE_ID +
                    " FROM " + TABLE_ATTENDANCE + " a" +
                    " JOIN " + TABLE_PERSONS + " p ON a." + ATTENDANCE_PERSON_ID + " = p." + PERSON_ID +
                    " WHERE a." + ATTENDANCE_DATE + " = ?" +
                    " ORDER BY a." + ATTENDANCE_TIMESTAMP + " DESC";

            cursor = db.rawQuery(query, new String[]{date});

            while (cursor.moveToNext()) {
                AttendanceRecord record = new AttendanceRecord();
                record.id = cursor.getLong(cursor.getColumnIndexOrThrow(ATTENDANCE_ID));
                record.personId = cursor.getLong(cursor.getColumnIndexOrThrow(ATTENDANCE_PERSON_ID));
                record.personName = cursor.getString(cursor.getColumnIndexOrThrow(PERSON_NAME));
                record.employeeId = cursor.getString(cursor.getColumnIndexOrThrow(PERSON_EMPLOYEE_ID));
                record.actionType = cursor.getString(cursor.getColumnIndexOrThrow(ATTENDANCE_ACTION_TYPE));
                record.timestamp = cursor.getString(cursor.getColumnIndexOrThrow(ATTENDANCE_TIMESTAMP));
                record.date = cursor.getString(cursor.getColumnIndexOrThrow(ATTENDANCE_DATE));
                record.time = cursor.getString(cursor.getColumnIndexOrThrow(ATTENDANCE_TIME));
                record.confidence = cursor.getDouble(cursor.getColumnIndexOrThrow(ATTENDANCE_CONFIDENCE));
                records.add(record);
            }

            return records;

        } catch (Exception e) {
            Log.e(TAG, "❌ Error getting attendance by date: " + e.getMessage());
            return new ArrayList<>();
        } finally {
            if (cursor != null && !cursor.isClosed()) {
                cursor.close();
            }
        }
    }

    // Utility methods
    private String getCurrentTimestamp() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
    }

    private String getCurrentDate() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
    }

    private String getCurrentTime() {
        return new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
    }

    // Data classes
    public static class Person {
        public long id;
        public String name;
        public String employeeId;
        public byte[] embedding;
        public boolean isActive;
        public String createdAt;

        @Override
        public String toString() {
            return "Person{" +
                    "id=" + id +
                    ", name='" + name + '\'' +
                    ", employeeId='" + employeeId + '\'' +
                    ", isActive=" + isActive +
                    ", createdAt='" + createdAt + '\'' +
                    '}';
        }
    }

    public static class AttendanceRecord {
        public long id;
        public long personId;
        public String personName;
        public String employeeId;
        public String actionType;
        public String timestamp;
        public String date;
        public String time;
        public double confidence;

        @Override
        public String toString() {
            return "AttendanceRecord{" +
                    "id=" + id +
                    ", personName='" + personName + '\'' +
                    ", employeeId='" + employeeId + '\'' +
                    ", actionType='" + actionType + '\'' +
                    ", time='" + time + '\'' +
                    ", confidence=" + confidence +
                    '}';
        }
    }

    public static class Stats {
        public final int total;
        public final int checkedIn;
        public final int checkedOut;
        public final int present;

        public Stats(int total, int checkedIn, int checkedOut, int present) {
            this.total = total;
            this.checkedIn = checkedIn;
            this.checkedOut = checkedOut;
            this.present = present;
        }

        @Override
        public String toString() {
            return "Stats{" +
                    "total=" + total +
                    ", checkedIn=" + checkedIn +
                    ", checkedOut=" + checkedOut +
                    ", present=" + present +
                    '}';
        }
    }
}