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

public class AttendanceDatabase extends SQLiteOpenHelper {

    private static final String TAG = "AttendanceDatabase";

    // Database Info
    private static final String DATABASE_NAME = "attendance.db";
    private static final int DATABASE_VERSION = 3; // Updated version for new schema

    // Table Names
    private static final String TABLE_USERS = "users";
    private static final String TABLE_FACE_ENCODINGS = "face_encodings";
    private static final String TABLE_ATTENDANCE = "attendance";

    // Users Table Columns
    private static final String KEY_USER_ID = "user_id";
    private static final String KEY_NAME = "name";
    private static final String KEY_EMPLOYEE_ID = "employee_id";
    private static final String KEY_DEPARTMENT = "department";
    private static final String KEY_CREATED_AT = "created_at";

    // Face Encodings Table Columns
    private static final String KEY_ENCODING_ID = "encoding_id";
    private static final String KEY_ENCODING_USER_ID = "user_id";
    private static final String KEY_ENCODING = "encoding";
    private static final String KEY_ENCODING_CREATED_AT = "created_at";

    // Attendance Table Columns
    private static final String KEY_ATTENDANCE_ID = "attendance_id";
    private static final String KEY_ATTENDANCE_USER_ID = "user_id";
    private static final String KEY_TIMESTAMP = "timestamp";
    private static final String KEY_DATE = "date";
    private static final String KEY_TIME = "time";
    private static final String KEY_STATUS = "status"; // IN/OUT

    public AttendanceDatabase(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
        Log.d(TAG, "🗄️ Database initialized: " + DATABASE_NAME + " v" + DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        Log.d(TAG, "🔨 Creating database tables...");

        // Create Users Table
        String CREATE_USERS_TABLE = "CREATE TABLE " + TABLE_USERS + "("
                + KEY_USER_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + KEY_NAME + " TEXT NOT NULL,"
                + KEY_EMPLOYEE_ID + " TEXT UNIQUE NOT NULL,"
                + KEY_DEPARTMENT + " TEXT,"
                + KEY_CREATED_AT + " DATETIME DEFAULT CURRENT_TIMESTAMP"
                + ")";

        // Create Face Encodings Table
        String CREATE_FACE_ENCODINGS_TABLE = "CREATE TABLE " + TABLE_FACE_ENCODINGS + "("
                + KEY_ENCODING_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + KEY_ENCODING_USER_ID + " INTEGER NOT NULL,"
                + KEY_ENCODING + " TEXT NOT NULL,"
                + KEY_ENCODING_CREATED_AT + " DATETIME DEFAULT CURRENT_TIMESTAMP,"
                + "FOREIGN KEY(" + KEY_ENCODING_USER_ID + ") REFERENCES " + TABLE_USERS + "(" + KEY_USER_ID + ")"
                + ")";

        // Create Attendance Table
        String CREATE_ATTENDANCE_TABLE = "CREATE TABLE " + TABLE_ATTENDANCE + "("
                + KEY_ATTENDANCE_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + KEY_ATTENDANCE_USER_ID + " INTEGER NOT NULL,"
                + KEY_TIMESTAMP + " DATETIME DEFAULT CURRENT_TIMESTAMP,"
                + KEY_DATE + " TEXT NOT NULL,"
                + KEY_TIME + " TEXT NOT NULL,"
                + KEY_STATUS + " TEXT DEFAULT 'IN',"
                + "FOREIGN KEY(" + KEY_ATTENDANCE_USER_ID + ") REFERENCES " + TABLE_USERS + "(" + KEY_USER_ID + ")"
                + ")";

        try {
            db.execSQL(CREATE_USERS_TABLE);
            Log.d(TAG, "✅ Users table created successfully");

            db.execSQL(CREATE_FACE_ENCODINGS_TABLE);
            Log.d(TAG, "✅ Face encodings table created successfully");

            db.execSQL(CREATE_ATTENDANCE_TABLE);
            Log.d(TAG, "✅ Attendance table created successfully");

        } catch (Exception e) {
            Log.e(TAG, "❌ Error creating tables", e);
        }
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        Log.d(TAG, "🔄 Upgrading database from version " + oldVersion + " to " + newVersion);

        // Drop older tables if existed
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_ATTENDANCE);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_FACE_ENCODINGS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_USERS);

        // Create tables again
        onCreate(db);
    }

    // ===== USER MANAGEMENT METHODS =====

    /**
     * Add a new user to the database
     */
    public long addUser(String name, String employeeId, String department) {
        SQLiteDatabase db = this.getWritableDatabase();

        try {
            ContentValues values = new ContentValues();
            values.put(KEY_NAME, name);
            values.put(KEY_EMPLOYEE_ID, employeeId);
            values.put(KEY_DEPARTMENT, department);

            long userId = db.insert(TABLE_USERS, null, values);

            if (userId != -1) {
                Log.d(TAG, "✅ User added successfully: " + name + " (ID: " + userId + ")");
            } else {
                Log.e(TAG, "❌ Failed to add user: " + name);
            }

            return userId;

        } catch (Exception e) {
            Log.e(TAG, "❌ Error adding user: " + name, e);
            return -1;
        }
    }

    /**
     * Get user by ID
     */
    public User getUserById(long userId) {
        SQLiteDatabase db = this.getReadableDatabase();
        User user = null;

        Cursor cursor = db.query(TABLE_USERS,
                new String[]{KEY_USER_ID, KEY_NAME, KEY_EMPLOYEE_ID, KEY_DEPARTMENT},
                KEY_USER_ID + " = ?",
                new String[]{String.valueOf(userId)},
                null, null, null);

        try {
            if (cursor.moveToFirst()) {
                long id = cursor.getLong(0);
                String name = cursor.getString(1);
                String employeeId = cursor.getString(2);
                String department = cursor.getString(3);

                user = new User(id, name, employeeId, department);
                Log.d(TAG, "✅ User found: " + name + " (ID: " + id + ")");
            } else {
                Log.d(TAG, "❓ No user found with ID: " + userId);
            }
        } finally {
            cursor.close();
        }

        return user;
    }

    /**
     * Get user by employee ID
     */
    public User getUserByEmployeeId(String employeeId) {
        SQLiteDatabase db = this.getReadableDatabase();
        User user = null;

        Cursor cursor = db.query(TABLE_USERS,
                new String[]{KEY_USER_ID, KEY_NAME, KEY_EMPLOYEE_ID, KEY_DEPARTMENT},
                KEY_EMPLOYEE_ID + " = ?",
                new String[]{employeeId},
                null, null, null);

        try {
            if (cursor.moveToFirst()) {
                long id = cursor.getLong(0);
                String name = cursor.getString(1);
                String empId = cursor.getString(2);
                String department = cursor.getString(3);

                user = new User(id, name, empId, department);
            }
        } finally {
            cursor.close();
        }

        return user;
    }

    /**
     * Get all users
     */
    public List<User> getAllUsers() {
        List<User> users = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();

        String selectQuery = "SELECT * FROM " + TABLE_USERS + " ORDER BY " + KEY_NAME;
        Cursor cursor = db.rawQuery(selectQuery, null);

        try {
            if (cursor.moveToFirst()) {
                do {
                    long id = cursor.getLong(cursor.getColumnIndexOrThrow(KEY_USER_ID));
                    String name = cursor.getString(cursor.getColumnIndexOrThrow(KEY_NAME));
                    String employeeId = cursor.getString(cursor.getColumnIndexOrThrow(KEY_EMPLOYEE_ID));
                    String department = cursor.getString(cursor.getColumnIndexOrThrow(KEY_DEPARTMENT));

                    users.add(new User(id, name, employeeId, department));
                } while (cursor.moveToNext());
            }
        } finally {
            cursor.close();
        }

        Log.d(TAG, "📦 Retrieved " + users.size() + " users");
        return users;
    }

    // ===== FACE ENCODING METHODS =====

    /**
     * Add face encoding for a user
     */
    public long addFaceEncoding(long userId, String encoding) {
        SQLiteDatabase db = this.getWritableDatabase();

        try {
            ContentValues values = new ContentValues();
            values.put(KEY_ENCODING_USER_ID, userId);
            values.put(KEY_ENCODING, encoding);

            long encodingId = db.insert(TABLE_FACE_ENCODINGS, null, values);

            if (encodingId != -1) {
                Log.d(TAG, "✅ Face encoding added for user " + userId + " (Encoding ID: " + encodingId + ")");
            } else {
                Log.e(TAG, "❌ Failed to add face encoding for user " + userId);
            }

            return encodingId;

        } catch (Exception e) {
            Log.e(TAG, "❌ Error adding face encoding for user " + userId, e);
            return -1;
        }
    }

    /**
     * Get all face encodings from the database
     */
    public List<FaceEncoding> getAllFaceEncodings() {
        List<FaceEncoding> encodings = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();

        Cursor cursor = db.query(TABLE_FACE_ENCODINGS,
                new String[]{KEY_ENCODING_ID, KEY_ENCODING_USER_ID, KEY_ENCODING},
                null, null, null, null, null);

        try {
            if (cursor.moveToFirst()) {
                do {
                    long encodingId = cursor.getLong(0);
                    long userId = cursor.getLong(1);
                    String encoding = cursor.getString(2);

                    encodings.add(new FaceEncoding(encodingId, userId, encoding));
                } while (cursor.moveToNext());
            }
        } finally {
            cursor.close();
        }

        Log.d(TAG, "📦 Retrieved " + encodings.size() + " face encodings");
        return encodings;
    }

    /**
     * Get face encodings for a specific user
     */
    public List<FaceEncoding> getFaceEncodingsByUserId(long userId) {
        List<FaceEncoding> encodings = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();

        Cursor cursor = db.query(TABLE_FACE_ENCODINGS,
                new String[]{KEY_ENCODING_ID, KEY_ENCODING_USER_ID, KEY_ENCODING},
                KEY_ENCODING_USER_ID + " = ?",
                new String[]{String.valueOf(userId)},
                null, null, null);

        try {
            if (cursor.moveToFirst()) {
                do {
                    long encodingId = cursor.getLong(0);
                    long userIdFromDb = cursor.getLong(1);
                    String encoding = cursor.getString(2);

                    encodings.add(new FaceEncoding(encodingId, userIdFromDb, encoding));
                } while (cursor.moveToNext());
            }
        } finally {
            cursor.close();
        }

        return encodings;
    }

    // ===== ATTENDANCE METHODS =====

    /**
     * Mark attendance for a user
     */
    public long markAttendance(long userId) {
        return markAttendance(userId, "IN");
    }

    /**
     * Mark attendance for a user with status
     */
    public long markAttendance(long userId, String status) {
        SQLiteDatabase db = this.getWritableDatabase();

        try {
            // Get current date and time
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
            Date now = new Date();

            String currentDate = dateFormat.format(now);
            String currentTime = timeFormat.format(now);

            ContentValues values = new ContentValues();
            values.put(KEY_ATTENDANCE_USER_ID, userId);
            values.put(KEY_DATE, currentDate);
            values.put(KEY_TIME, currentTime);
            values.put(KEY_STATUS, status);

            long attendanceId = db.insert(TABLE_ATTENDANCE, null, values);

            if (attendanceId != -1) {
                User user = getUserById(userId);
                String userName = (user != null) ? user.name : "Unknown";
                Log.d(TAG, "✅ Attendance marked: " + userName + " (" + status + ") at " + currentTime);
            } else {
                Log.e(TAG, "❌ Failed to mark attendance for user " + userId);
            }

            return attendanceId;

        } catch (Exception e) {
            Log.e(TAG, "❌ Error marking attendance for user " + userId, e);
            return -1;
        }
    }

    /**
     * Get attendance records for a user on a specific date
     */
    public List<AttendanceRecord> getAttendanceByUserAndDate(long userId, String date) {
        List<AttendanceRecord> records = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();

        Cursor cursor = db.query(TABLE_ATTENDANCE,
                new String[]{KEY_ATTENDANCE_ID, KEY_ATTENDANCE_USER_ID, KEY_TIMESTAMP, KEY_DATE, KEY_TIME, KEY_STATUS},
                KEY_ATTENDANCE_USER_ID + " = ? AND " + KEY_DATE + " = ?",
                new String[]{String.valueOf(userId), date},
                null, null, KEY_TIME + " ASC");

        try {
            if (cursor.moveToFirst()) {
                do {
                    long id = cursor.getLong(0);
                    long userIdFromDb = cursor.getLong(1);
                    String timestamp = cursor.getString(2);
                    String recordDate = cursor.getString(3);
                    String time = cursor.getString(4);
                    String status = cursor.getString(5);

                    records.add(new AttendanceRecord(id, userIdFromDb, timestamp, recordDate, time, status));
                } while (cursor.moveToNext());
            }
        } finally {
            cursor.close();
        }

        return records;
    }

    /**
     * Get all attendance records for today
     */
    public List<AttendanceRecord> getTodayAttendance() {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        String today = dateFormat.format(new Date());

        List<AttendanceRecord> records = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();

        String query = "SELECT a.*, u." + KEY_NAME + " FROM " + TABLE_ATTENDANCE + " a " +
                "JOIN " + TABLE_USERS + " u ON a." + KEY_ATTENDANCE_USER_ID + " = u." + KEY_USER_ID + " " +
                "WHERE a." + KEY_DATE + " = ? " +
                "ORDER BY a." + KEY_TIME + " DESC";

        Cursor cursor = db.rawQuery(query, new String[]{today});

        try {
            if (cursor.moveToFirst()) {
                do {
                    long id = cursor.getLong(cursor.getColumnIndexOrThrow(KEY_ATTENDANCE_ID));
                    long userId = cursor.getLong(cursor.getColumnIndexOrThrow(KEY_ATTENDANCE_USER_ID));
                    String timestamp = cursor.getString(cursor.getColumnIndexOrThrow(KEY_TIMESTAMP));
                    String date = cursor.getString(cursor.getColumnIndexOrThrow(KEY_DATE));
                    String time = cursor.getString(cursor.getColumnIndexOrThrow(KEY_TIME));
                    String status = cursor.getString(cursor.getColumnIndexOrThrow(KEY_STATUS));

                    AttendanceRecord record = new AttendanceRecord(id, userId, timestamp, date, time, status);
                    records.add(record);
                } while (cursor.moveToNext());
            }
        } finally {
            cursor.close();
        }

        Log.d(TAG, "📦 Retrieved " + records.size() + " attendance records for today");
        return records;
    }

    /**
     * Check if user already marked attendance today
     */
    public boolean hasAttendanceToday(long userId) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        String today = dateFormat.format(new Date());

        SQLiteDatabase db = this.getReadableDatabase();

        Cursor cursor = db.query(TABLE_ATTENDANCE,
                new String[]{KEY_ATTENDANCE_ID},
                KEY_ATTENDANCE_USER_ID + " = ? AND " + KEY_DATE + " = ?",
                new String[]{String.valueOf(userId), today},
                null, null, null);

        boolean hasAttendance = cursor.getCount() > 0;
        cursor.close();

        return hasAttendance;
    }

    // ===== UTILITY METHODS =====

    /**
     * Get database stats
     */
    public DatabaseStats getDatabaseStats() {
        SQLiteDatabase db = this.getReadableDatabase();

        int userCount = 0;
        int encodingCount = 0;
        int attendanceCount = 0;

        // Count users
        Cursor userCursor = db.rawQuery("SELECT COUNT(*) FROM " + TABLE_USERS, null);
        if (userCursor.moveToFirst()) {
            userCount = userCursor.getInt(0);
        }
        userCursor.close();

        // Count encodings
        Cursor encodingCursor = db.rawQuery("SELECT COUNT(*) FROM " + TABLE_FACE_ENCODINGS, null);
        if (encodingCursor.moveToFirst()) {
            encodingCount = encodingCursor.getInt(0);
        }
        encodingCursor.close();

        // Count attendance records
        Cursor attendanceCursor = db.rawQuery("SELECT COUNT(*) FROM " + TABLE_ATTENDANCE, null);
        if (attendanceCursor.moveToFirst()) {
            attendanceCount = attendanceCursor.getInt(0);
        }
        attendanceCursor.close();

        return new DatabaseStats(userCount, encodingCount, attendanceCount);
    }

    /**
     * Clear all data (for testing)
     */
    public void clearAllData() {
        SQLiteDatabase db = this.getWritableDatabase();

        db.delete(TABLE_ATTENDANCE, null, null);
        db.delete(TABLE_FACE_ENCODINGS, null, null);
        db.delete(TABLE_USERS, null, null);

        Log.d(TAG, "🗑️ All database data cleared");
    }

    // ===== INNER CLASSES =====

    /**
     * User data class
     */
    public static class User {
        public final long id;
        public final String name;
        public final String employeeId;
        public final String department;

        public User(long id, String name, String employeeId, String department) {
            this.id = id;
            this.name = name;
            this.employeeId = employeeId;
            this.department = department;
        }

        @Override
        public String toString() {
            return "User{id=" + id + ", name='" + name + "', employeeId='" + employeeId + "', department='" + department + "'}";
        }
    }

    /**
     * Face encoding data class
     */
    public static class FaceEncoding {
        public final long encodingId;
        public final long userId;
        public final String encoding;

        public FaceEncoding(long encodingId, long userId, String encoding) {
            this.encodingId = encodingId;
            this.userId = userId;
            this.encoding = encoding;
        }

        @Override
        public String toString() {
            return "FaceEncoding{encodingId=" + encodingId + ", userId=" + userId + ", encoding='" + encoding.substring(0, Math.min(50, encoding.length())) + "...'}";
        }
    }

    /**
     * Attendance record data class
     */
    public static class AttendanceRecord {
        public final long id;
        public final long userId;
        public final String timestamp;
        public final String date;
        public final String time;
        public final String status;

        public AttendanceRecord(long id, long userId, String timestamp, String date, String time, String status) {
            this.id = id;
            this.userId = userId;
            this.timestamp = timestamp;
            this.date = date;
            this.time = time;
            this.status = status;
        }

        @Override
        public String toString() {
            return "AttendanceRecord{id=" + id + ", userId=" + userId + ", date='" + date + "', time='" + time + "', status='" + status + "'}";
        }
    }

    /**
     * Database statistics data class
     */
    public static class DatabaseStats {
        public final int userCount;
        public final int encodingCount;
        public final int attendanceCount;

        public DatabaseStats(int userCount, int encodingCount, int attendanceCount) {
            this.userCount = userCount;
            this.encodingCount = encodingCount;
            this.attendanceCount = attendanceCount;
        }

        @Override
        public String toString() {
            return "DatabaseStats{users=" + userCount + ", encodings=" + encodingCount + ", attendance=" + attendanceCount + "}";
        }
    }
}