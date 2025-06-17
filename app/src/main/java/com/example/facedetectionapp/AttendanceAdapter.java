package com.example.facedetectionapp;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class AttendanceAdapter extends RecyclerView.Adapter<AttendanceAdapter.AttendanceViewHolder> {

    private List<AttendanceItemModel> attendanceList;
    private Context context;

    public AttendanceAdapter(Context context, List<AttendanceItemModel> attendanceList) {
        this.context = context;
        this.attendanceList = attendanceList;
    }

    @NonNull
    @Override
    public AttendanceViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_attendance, parent, false);
        return new AttendanceViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull AttendanceViewHolder holder, int position) {
        AttendanceItemModel item = attendanceList.get(position);

        // Set user name
        holder.userNameTextView.setText(item.userName);

        // Set employee ID
        holder.employeeIdTextView.setText("ID: " + item.employeeId);

        // Set department
        if (item.department != null && !item.department.isEmpty()) {
            holder.departmentTextView.setText(item.department);
            holder.departmentTextView.setVisibility(View.VISIBLE);
        } else {
            holder.departmentTextView.setVisibility(View.GONE);
        }

        // Set date
        holder.dateTextView.setText(item.date);

        // Set check-in time
        if (item.checkInTime != null && !item.checkInTime.isEmpty()) {
            holder.checkInTimeTextView.setText("In: " + item.checkInTime);
            holder.checkInTimeTextView.setVisibility(View.VISIBLE);
        } else {
            holder.checkInTimeTextView.setText("In: --:--");
        }

        // Set check-out time
        if (item.checkOutTime != null && !item.checkOutTime.isEmpty()) {
            holder.checkOutTimeTextView.setText("Out: " + item.checkOutTime);
            holder.checkOutTimeTextView.setVisibility(View.VISIBLE);
        } else {
            holder.checkOutTimeTextView.setText("Out: --:--");
        }

        // Set confidence if available
        if (item.confidence > 0) {
            holder.confidenceTextView.setText(String.format("%.1f%% confidence", item.confidence));
            holder.confidenceTextView.setVisibility(View.VISIBLE);
        } else {
            holder.confidenceTextView.setVisibility(View.GONE);
        }

        // Set status
        holder.statusTextView.setText(item.status);

        // Set status color based on type
        int statusColor;
        switch (item.status.toUpperCase()) {
            case "PRESENT":
                statusColor = context.getResources().getColor(android.R.color.holo_green_dark);
                break;
            case "ABSENT":
                statusColor = context.getResources().getColor(android.R.color.holo_red_dark);
                break;
            case "IN":
                statusColor = context.getResources().getColor(android.R.color.holo_blue_dark);
                break;
            case "OUT":
                statusColor = context.getResources().getColor(android.R.color.holo_orange_dark);
                break;
            default:
                statusColor = context.getResources().getColor(android.R.color.darker_gray);
                break;
        }
        holder.statusTextView.setTextColor(statusColor);
    }

    @Override
    public int getItemCount() {
        return attendanceList != null ? attendanceList.size() : 0;
    }

    public void updateData(List<AttendanceItemModel> newAttendanceList) {
        this.attendanceList = newAttendanceList;
        notifyDataSetChanged();
    }

    public static class AttendanceViewHolder extends RecyclerView.ViewHolder {
        TextView userNameTextView;
        TextView employeeIdTextView;
        TextView departmentTextView;
        TextView dateTextView;
        TextView checkInTimeTextView;
        TextView checkOutTimeTextView;
        TextView confidenceTextView;
        TextView statusTextView;

        public AttendanceViewHolder(@NonNull View itemView) {
            super(itemView);

            userNameTextView = itemView.findViewById(R.id.userNameTextView);
            employeeIdTextView = itemView.findViewById(R.id.employeeIdTextView);
            departmentTextView = itemView.findViewById(R.id.departmentTextView);
            dateTextView = itemView.findViewById(R.id.dateTextView);
            checkInTimeTextView = itemView.findViewById(R.id.checkInTimeTextView);
            checkOutTimeTextView = itemView.findViewById(R.id.checkOutTimeTextView);
            confidenceTextView = itemView.findViewById(R.id.confidenceTextView);
            statusTextView = itemView.findViewById(R.id.statusTextView);
        }
    }

    // ===== ATTENDANCE ITEM MODEL CLASS =====

    public static class AttendanceItemModel {
        public final String userName;
        public final String employeeId;
        public final String department;
        public final String date;
        public final String checkInTime;
        public final String checkOutTime;
        public final float confidence;
        public final String status;

        public AttendanceItemModel(String userName, String employeeId, String department,
                                   String date, String checkInTime, String checkOutTime,
                                   float confidence, String status) {
            this.userName = userName;
            this.employeeId = employeeId;
            this.department = department;
            this.date = date;
            this.checkInTime = checkInTime;
            this.checkOutTime = checkOutTime;
            this.confidence = confidence;
            this.status = status;
        }

        @Override
        public String toString() {
            return "AttendanceItemModel{" +
                    "userName='" + userName + '\'' +
                    ", employeeId='" + employeeId + '\'' +
                    ", date='" + date + '\'' +
                    ", checkInTime='" + checkInTime + '\'' +
                    ", checkOutTime='" + checkOutTime + '\'' +
                    ", status='" + status + '\'' +
                    '}';
        }
    }

    // ===== UTILITY METHODS =====

    /**
     * Convert database records to adapter models
     */
    public static AttendanceItemModel fromDatabaseRecord(AttendanceDatabase.AttendanceRecord record,
                                                         AttendanceDatabase.User user) {
        return new AttendanceItemModel(
                user != null ? user.name : "Unknown User",
                user != null ? user.employeeId : "N/A",
                user != null ? user.department : "",
                record.date,
                record.status.equals("IN") ? record.time : "",
                record.status.equals("OUT") ? record.time : "",
                0.0f, // Confidence not stored in basic attendance records
                record.status
        );
    }

    /**
     * Create attendance item from enhanced record (with confidence)
     */
    public static AttendanceItemModel fromEnhancedRecord(AttendanceDatabase.AttendanceRecord record,
                                                         AttendanceDatabase.User user,
                                                         float confidence) {
        return new AttendanceItemModel(
                user != null ? user.name : "Unknown User",
                user != null ? user.employeeId : "N/A",
                user != null ? user.department : "",
                record.date,
                record.status.equals("IN") ? record.time : "",
                record.status.equals("OUT") ? record.time : "",
                confidence,
                record.status
        );
    }

    /**
     * Format time string for display
     */
    private String formatTimeForDisplay(String timeString) {
        try {
            SimpleDateFormat inputFormat = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
            SimpleDateFormat outputFormat = new SimpleDateFormat("hh:mm a", Locale.getDefault());
            Date time = inputFormat.parse(timeString);
            return outputFormat.format(time);
        } catch (ParseException e) {
            return timeString; // Return original if parsing fails
        }
    }
}