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
    private List<AttendanceRecord> records;

    public AttendanceRecordsAdapter(Context context, List<AttendanceRecord> records) {
        this.context = context;
        this.records = records;
    }

    public void updateRecords(List<AttendanceRecord> newRecords) {
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
        AttendanceRecord record = records.get(position);

        // Set person info
        holder.nameText.setText(record.personName != null ? record.personName : "Unknown");
        holder.employeeIdText.setText(record.personEmployeeId != null ? record.personEmployeeId : "N/A");

        // Set time info
        holder.timeText.setText(record.getFormattedTime());
        holder.dateText.setText(record.getFormattedDate());

        // Set type with icon and color
        String typeText = record.getTypeDisplayName();
        holder.typeText.setText(typeText);

        // Set icon and colors based on type
        if (record.isCheckIn()) {
            holder.typeIcon.setImageResource(R.drawable.ic_login);
            holder.typeText.setTextColor(context.getResources().getColor(android.R.color.holo_green_dark));
            holder.typeIcon.setColorFilter(context.getResources().getColor(android.R.color.holo_green_dark));
        } else if (record.isCheckOut()) {
            holder.typeIcon.setImageResource(R.drawable.ic_logout);
            holder.typeText.setTextColor(context.getResources().getColor(android.R.color.holo_orange_dark));
            holder.typeIcon.setColorFilter(context.getResources().getColor(android.R.color.holo_orange_dark));
        } else if (record.isBreakStart()) {
            holder.typeIcon.setImageResource(R.drawable.ic_pause);
            holder.typeText.setTextColor(context.getResources().getColor(android.R.color.holo_blue_dark));
            holder.typeIcon.setColorFilter(context.getResources().getColor(android.R.color.holo_blue_dark));
        } else if (record.isBreakEnd()) {
            holder.typeIcon.setImageResource(R.drawable.ic_play);
            holder.typeText.setTextColor(context.getResources().getColor(android.R.color.holo_blue_dark));
            holder.typeIcon.setColorFilter(context.getResources().getColor(android.R.color.holo_blue_dark));
        }

        // Set confidence
        if (record.confidence > 0) {
            holder.confidenceText.setText(String.format("%.0f%%", record.confidence));
            holder.confidenceText.setVisibility(View.VISIBLE);

            // Color based on confidence
            if (record.confidence >= 80) {
                holder.confidenceText.setTextColor(context.getResources().getColor(android.R.color.holo_green_dark));
            } else if (record.confidence >= 60) {
                holder.confidenceText.setTextColor(context.getResources().getColor(android.R.color.holo_orange_dark));
            } else {
                holder.confidenceText.setTextColor(context.getResources().getColor(android.R.color.holo_red_dark));
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

    @Override
    public int getItemCount() {
        return records.size();
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