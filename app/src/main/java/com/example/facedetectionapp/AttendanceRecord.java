package com.example.facedetectionapp;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class AttendanceRecord {
    public long id;
    public long personId;
    public long timestamp;
    public AttendanceType type;
    public String location;
    public float confidence;

    // Additional fields populated from joins
    public String personName;
    public String personEmployeeId;

    public enum AttendanceType {
        CHECK_IN("Check In"),
        CHECK_OUT("Check Out"),
        BREAK_START("Break Start"),
        BREAK_END("Break End");

        private final String displayName;

        AttendanceType(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }

        @Override
        public String toString() {
            return name();
        }
    }

    public AttendanceRecord() {
        this.timestamp = System.currentTimeMillis();
        this.confidence = 0.0f;
    }

    public AttendanceRecord(long personId, AttendanceType type) {
        this();
        this.personId = personId;
        this.type = type;
    }

    public Date getDate() {
        return new Date(timestamp);
    }

    public String getFormattedTime() {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
        return sdf.format(getDate());
    }

    public String getFormattedDate() {
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
        return sdf.format(getDate());
    }

    public String getFormattedDateTime() {
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault());
        return sdf.format(getDate());
    }

    public String getTypeDisplayName() {
        return type != null ? type.getDisplayName() : "Unknown";
    }

    public boolean isCheckIn() {
        return type == AttendanceType.CHECK_IN;
    }

    public boolean isCheckOut() {
        return type == AttendanceType.CHECK_OUT;
    }

    public boolean isBreakStart() {
        return type == AttendanceType.BREAK_START;
    }

    public boolean isBreakEnd() {
        return type == AttendanceType.BREAK_END;
    }

    @Override
    public String toString() {
        return String.format("AttendanceRecord{id=%d, personId=%d, type=%s, time=%s, location='%s', confidence=%.2f}",
                id, personId, type, getFormattedDateTime(), location, confidence);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;

        AttendanceRecord record = (AttendanceRecord) obj;
        return id == record.id;
    }

    @Override
    public int hashCode() {
        return (int) (id ^ (id >>> 32));
    }
}