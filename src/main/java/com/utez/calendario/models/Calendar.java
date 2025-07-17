package com.utez.calendario.models;

import java.util.List;
import java.util.ArrayList;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;

/**
 * Modelo que representa un calendario en el sistema
 */
public class Calendar {
    private String calendarId;
    private String ownerId;
    private String name;
    private String description;
    private String color;
    private char active;
    private LocalDateTime createdDate;
    private LocalDateTime modifiedDate;

    private static Connection getConnection() throws SQLException {

        return com.utez.calendario.config.DatabaseConfig.getConnection();

    }

    // Constructores
    public Calendar() {}

    public Calendar(String calendarId, String ownerId, String name, String description, String color) {
        this.calendarId = calendarId;
        this.ownerId = ownerId;
        this.name = name;
        this.description = description;
        this.color = color;
        this.active = 'Y';
        this.createdDate = LocalDateTime.now();
    }

    // Funciones para el administrador de calendarios (pronto xd)

    // Getters y Setters
    public String getCalendarId() {
        return calendarId;
    }

    public void setCalendarId(String calendarId) {
        this.calendarId = calendarId;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(String ownerId) {
        this.ownerId = ownerId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public char getActive() {
        return active;
    }

    public void setActive(char active) {
        this.active = active;
    }

    public LocalDateTime getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate(LocalDateTime createdDate) {
        this.createdDate = createdDate;
    }

    public LocalDateTime getModifiedDate() {
        return modifiedDate;
    }

    public void setModifiedDate(LocalDateTime modifiedDate) {
        this.modifiedDate = modifiedDate;
    }

    // Métodos utilitarios
    public boolean isActive() {
        return active == 'Y';
    }

    @Override
    public String toString() {
        return "Calendar{" +
                "calendarId='" + calendarId + '\'' +
                ", ownerId='" + ownerId + '\'' +
                ", name='" + name + '\'' +
                ", color='" + color + '\'' +
                ", active=" + active +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Calendar calendar = (Calendar) o;
        return calendarId != null ? calendarId.equals(calendar.calendarId) : calendar.calendarId == null;
    }

    @Override
    public int hashCode() {
        return calendarId != null ? calendarId.hashCode() : 0;
    }
}