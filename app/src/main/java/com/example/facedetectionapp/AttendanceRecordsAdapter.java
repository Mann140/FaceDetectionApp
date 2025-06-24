package com.example.facedetectionapp;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class AttendanceRecordsAdapter extends RecyclerView.Adapter<AttendanceRecordsAdapter.ViewHolder> {
    private Context context;
    private List<DatabaseHelper.AttendanceRecord> records;

    public AttendanceRecordsAdapter(Context context, List<DatabaseHelper.AttendanceRecord> records) {
        this.context = context;
        this.records = records;
    }

    public void updateRecords(List<DatabaseHelper.AttendanceRecord> newRecords) {
        this.records = newRecords;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_attendance_record, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        DatabaseHelper.AttendanceRecord record = records.get(position);

        // Set person info
        holder.nameText.setText(record.personName != null ? record.personName : "Unknown");
        holder.employeeIdText.setText(record.personEmployeeId != null ? record.personEmployeeId : "N/A");

        // Set time info using the getter methods
        holder.timeText.setText(record.getFormattedTime());
        holder.dateText.setText(record.getFormattedDate());

        // Set type with icon and color
        String typeText = getTypeDisplayText(record.actionType);
        holder.typeText.setText(typeText);

        // Set icon and colors based on type
        setTypeIconAndColor(holder, record.actionType);

        // Set confidence
        if (record.confidence > 0) {
            holder.confidenceText.setText(String.format("%.0f%%", record.confidence * 100));
            holder.confidenceText.setVisibility(View.VISIBLE);

            // Color based on confidence
            if (record.confidence >= 0.8f) {
                holder.confidenceText.setTextColor(context.getResources().getColor(android.R.color.holo_green_dark));
                holder.confidenceText.setBackgroundColor(context.getResources().getColor(android.R.color.background_light));
            } else if (record.confidence >= 0.6f) {
                holder.confidenceText.setTextColor(context.getResources().getColor(android.R.color.holo_orange_dark));
                holder.confidenceText.setBackgroundColor(context.getResources().getColor(android.R.color.background_light));
            } else {
                holder.confidenceText.setTextColor(context.getResources().getColor(android.R.color.holo_red_dark));
                holder.confidenceText.setBackgroundColor(context.getResources().getColor(android.R.color.background_light));
            }
        } else {
            holder.confidenceText.setVisibility(View.GONE);
        }

        // Set location
        if (record.location != null && !record.location.isEmpty()) {
            holder.locationText.setText(record.location);
            holder.locationText.setVisibility(View.VISIBLE);
        } else {
            holder.locationText.setVisibility(View.GONE);
        }
    }

    private String getTypeDisplayText(String actionType) {
        if (actionType == null) return "UNKNOWN";

        switch (actionType.toLowerCase()) {
            case "check_in":
                return "Check In";
            case "check_out":
                return "Check Out";
            case "break_start":
                return "Break Start";
            case "break_end":
                return "Break End";
            default:
                return actionType.replace("_", " ").toUpperCase();
        }
    }

    private void setTypeIconAndColor(ViewHolder holder, String actionType) {
        if (actionType == null) {
            setDefaultIconAndColor(holder);
            return;
        }

        switch (actionType.toLowerCase()) {
            case "check_in":
                holder.typeIcon.setImageResource(R.drawable.ic_login);
                holder.typeText.setTextColor(context.getResources().getColor(android.R.color.holo_green_dark));
                holder.typeIcon.setColorFilter(context.getResources().getColor(android.R.color.holo_green_dark));
                break;
            case "check_out":
                holder.typeIcon.setImageResource(R.drawable.ic_logout);
                holder.typeText.setTextColor(context.getResources().getColor(android.R.color.holo_orange_dark));
                holder.typeIcon.setColorFilter(context.getResources().getColor(android.R.color.holo_orange_dark));
                break;
            case "break_start":
                holder.typeIcon.setImageResource(R.drawable.ic_pause);
                holder.typeText.setTextColor(context.getResources().getColor(android.R.color.holo_blue_dark));
                holder.typeIcon.setColorFilter(context.getResources().getColor(android.R.color.holo_blue_dark));
                break;
            case "break_end":
                holder.typeIcon.setImageResource(R.drawable.ic_play);
                holder.typeText.setTextColor(context.getResources().getColor(android.R.color.holo_blue_dark));
                holder.typeIcon.setColorFilter(context.getResources().getColor(android.R.color.holo_blue_dark));
                break;
            default:
                setDefaultIconAndColor(holder);
                break;
        }
    }

    private void setDefaultIconAndColor(ViewHolder holder) {
        holder.typeIcon.setImageResource(R.drawable.ic_check);
        holder.typeText.setTextColor(context.getResources().getColor(android.R.color.darker_gray));
        holder.typeIcon.setColorFilter(context.getResources().getColor(android.R.color.darker_gray));
    }

    @Override
    public int getItemCount() {
        return records != null ? records.size() : 0;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView nameText;
        TextView employeeIdText;
        TextView timeText;
        TextView dateText;
        TextView typeText;
        TextView confidenceText;
        TextView locationText;
        ImageView typeIcon;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);

            // Initialize all the views
            nameText = itemView.findViewById(R.id.nameText);
            employeeIdText = itemView.findViewById(R.id.employeeIdText);
            timeText = itemView.findViewById(R.id.timeText);
            dateText = itemView.findViewById(R.id.dateText);
            typeText = itemView.findViewById(R.id.typeText);
            confidenceText = itemView.findViewById(R.id.confidenceText);
            locationText = itemView.findViewById(R.id.locationText);
            typeIcon = itemView.findViewById(R.id.typeIcon);
        }
    }
}