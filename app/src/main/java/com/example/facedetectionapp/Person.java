package com.example.facedetectionapp;

import java.util.Date;

public class Person {
    public long id;
    public String name;
    public String employeeId;
    public float[] faceEmbedding;
    public long registrationTime;
    public boolean isActive;

    // Additional optional fields
    public String department;
    public String designation;
    public String email;
    public String phoneNumber;

    public Person() {
        this.isActive = true;
        this.registrationTime = System.currentTimeMillis();
    }

    public Person(String name, String employeeId, float[] faceEmbedding) {
        this();
        this.name = name;
        this.employeeId = employeeId;
        this.faceEmbedding = faceEmbedding;
    }

    public Date getRegistrationDate() {
        return new Date(registrationTime);
    }

    @Override
    public String toString() {
        return String.format("Person{id=%d, name='%s', employeeId='%s', active=%b, registered=%s}",
                id, name, employeeId, isActive, getRegistrationDate().toString());
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;

        Person person = (Person) obj;
        return id == person.id ||
                (employeeId != null && employeeId.equals(person.employeeId));
    }

    @Override
    public int hashCode() {
        return employeeId != null ? employeeId.hashCode() : (int) id;
    }
}