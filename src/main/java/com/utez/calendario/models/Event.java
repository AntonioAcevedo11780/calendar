package com.utez.calendario.models;

import java.time.LocalDateTime;

/**
 * Modelo que representa un evento en el sistema
 */
public class Event {
    private String eventId;
    private String calendarId;
    private String creatorId;
    private String title;
    private String description;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private char allDay;
    private String location;
    private String recurrence;
    private LocalDateTime recurrenceEndDate;
    private char active;
    private LocalDateTime createdDate;
    private LocalDateTime modifiedDate;

    // Constructores
    public Event() {}

    public Event(String eventId, String calendarId, String creatorId, String title,
                 LocalDateTime startDate, LocalDateTime endDate) {
        this.eventId = eventId;
        this.calendarId = calendarId;
        this.creatorId = creatorId;
        this.title = title;
        this.startDate = startDate;
        this.endDate = endDate;
        this.allDay = 'N';
        this.active = 'Y';
        this.createdDate = LocalDateTime.now();
    }

    // Getters y Setters
    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getCalendarId() {
        return calendarId;
    }

    public void setCalendarId(String calendarId) {
        this.calendarId = calendarId;
    }

    public String getCreatorId() {
        return creatorId;
    }

    public void setCreatorId(String creatorId) {
        this.creatorId = creatorId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public LocalDateTime getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDateTime startDate) {
        this.startDate = startDate;
    }

    public LocalDateTime getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDateTime endDate) {
        this.endDate = endDate;
    }

    public char getAllDay() {
        return allDay;
    }

    public void setAllDay(char allDay) {
        this.allDay = allDay;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getRecurrence() {
        return recurrence;
    }

    public void setRecurrence(String recurrence) {
        this.recurrence = recurrence;
    }

    public LocalDateTime getRecurrenceEndDate() {
        return recurrenceEndDate;
    }

    public void setRecurrenceEndDate(LocalDateTime recurrenceEndDate) {
        this.recurrenceEndDate = recurrenceEndDate;
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
    public boolean isAllDay() {
        return allDay == 'Y';
    }

    public boolean isActive() {
        return active == 'Y';
    }

    public boolean hasRecurrence() {
        return recurrence != null && !recurrence.trim().isEmpty();
    }

    @Override
    public String toString() {
        return "Event{" +
                "eventId='" + eventId + '\'' +
                ", title='" + title + '\'' +
                ", startDate=" + startDate +
                ", endDate=" + endDate +
                ", allDay=" + allDay +
                ", active=" + active +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Event event = (Event) o;
        return eventId != null ? eventId.equals(event.eventId) : event.eventId == null;
    }

    @Override
    public int hashCode() {
        return eventId != null ? eventId.hashCode() : 0;
    }
}