package com.example.facedetectionapp;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class PersonsAdapter extends RecyclerView.Adapter<PersonsAdapter.ViewHolder> {
    private Context context;
    private List<DatabaseHelper.Person> persons;  // Changed to DatabaseHelper.Person
    private OnPersonActionListener listener;

    public interface OnPersonActionListener {
        void onViewAttendance(DatabaseHelper.Person person);  // Changed to DatabaseHelper.Person
        void onEditPerson(DatabaseHelper.Person person);     // Changed to DatabaseHelper.Person
        void onDeletePerson(DatabaseHelper.Person person);   // Changed to DatabaseHelper.Person
    }

    public PersonsAdapter(Context context, List<DatabaseHelper.Person> persons, OnPersonActionListener listener) {
        this.context = context;
        this.persons = persons;
        this.listener = listener;
    }

    public void updatePersons(List<DatabaseHelper.Person> newPersons) {  // Changed to DatabaseHelper.Person
        this.persons = newPersons;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_person, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        DatabaseHelper.Person person = persons.get(position);  // Changed to DatabaseHelper.Person

        // Set person info
        holder.nameText.setText(person.name);
        holder.employeeIdText.setText("ID: " + person.employeeId);
        holder.registrationDateText.setText("Registered: " + person.getFormattedCreatedDate());

        // Set additional info if available (these fields don't exist in DatabaseHelper.Person, so hide them)
        holder.departmentText.setVisibility(View.GONE);
        holder.emailText.setVisibility(View.GONE);

        // Set status
        if (person.isActive) {
            holder.statusText.setText("Active");
            holder.statusText.setTextColor(context.getResources().getColor(android.R.color.holo_green_dark));
            holder.statusText.setBackgroundColor(context.getResources().getColor(android.R.color.background_light));
        } else {
            holder.statusText.setText("Inactive");
            holder.statusText.setTextColor(context.getResources().getColor(android.R.color.holo_red_dark));
            holder.statusText.setBackgroundColor(context.getResources().getColor(android.R.color.background_light));
        }

        // Set button listeners
        holder.viewAttendanceButton.setOnClickListener(v -> {
            if (listener != null) {
                listener.onViewAttendance(person);
            }
        });

        holder.editButton.setOnClickListener(v -> {
            if (listener != null) {
                listener.onEditPerson(person);
            }
        });

        holder.deleteButton.setOnClickListener(v -> {
            if (listener != null) {
                listener.onDeletePerson(person);
            }
        });

        // Enable/disable buttons based on status
        holder.viewAttendanceButton.setEnabled(person.isActive);
        holder.deleteButton.setText(person.isActive ? "Delete" : "Deleted");
        holder.deleteButton.setEnabled(person.isActive);
    }

    @Override
    public int getItemCount() {
        return persons != null ? persons.size() : 0;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView nameText;
        TextView employeeIdText;
        TextView registrationDateText;
        TextView departmentText;
        TextView emailText;
        TextView statusText;
        Button viewAttendanceButton;
        Button editButton;
        Button deleteButton;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);

            nameText = itemView.findViewById(R.id.nameText);
            employeeIdText = itemView.findViewById(R.id.employeeIdText);
            registrationDateText = itemView.findViewById(R.id.registrationDateText);
            departmentText = itemView.findViewById(R.id.departmentText);
            emailText = itemView.findViewById(R.id.emailText);
            statusText = itemView.findViewById(R.id.statusText);
            viewAttendanceButton = itemView.findViewById(R.id.viewAttendanceButton);
            editButton = itemView.findViewById(R.id.editButton);
            deleteButton = itemView.findViewById(R.id.deleteButton);
        }
    }
}