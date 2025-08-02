package com.utez.calendario.models;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.Duration;

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
    private String calendarColor;



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

    //  MÉTODO para título formateado
    public String getDisplayTitle() {
        if (title == null || title.trim().isEmpty()) {
            return "Sin título";
        }

        // Limitar título a 30 caracteres para UI
        if (title.length() > 30) {
            return title.substring(0, 27) + "...";
        }

        return title;
    }

    //  MÉTODO para verificar si es hoy
    public boolean isToday() {
        if (startDate == null) return false;

        LocalDate today = LocalDate.now();
        LocalDate eventDate = startDate.toLocalDate();

        return eventDate.equals(today);
    }

    //  MÉTODO para verificar si es futuro
    public boolean isUpcoming() {
        if (startDate == null) return false;

        return startDate.isAfter(LocalDateTime.now());
    }

    //  MÉTODO para verificar si está en curso
    public boolean isOngoing() {
        if (startDate == null || endDate == null) return false;

        LocalDateTime now = LocalDateTime.now();
        return !now.isBefore(startDate) && !now.isAfter(endDate);
    }

    //  MÉTODO para duración formateada
    public String getFormattedDuration() {
        if (startDate == null || endDate == null) {
            return "Duración no definida";
        }

        if (isAllDay()) {
            return "Todo el día";
        }

        Duration duration = Duration.between(startDate, endDate);
        long hours = duration.toHours();
        long minutes = duration.toMinutes() % 60;

        if (hours == 0) {
            return minutes + " min";
        } else if (minutes == 0) {
            return hours + "h";
        } else {
            return hours + "h " + minutes + "m";
        }
    }

    //  MÉTODO para tiempo hasta el evento
    public String getTimeUntilEvent() {
        if (startDate == null) return "";

        LocalDateTime now = LocalDateTime.now();

        if (startDate.isBefore(now)) {
            return "Iniciado";
        }

        Duration duration = Duration.between(now, startDate);
        long days = duration.toDays();
        long hours = duration.toHours() % 24;
        long minutes = duration.toMinutes() % 60;

        if (days > 0) {
            return "En " + days + " día" + (days != 1 ? "s" : "");
        } else if (hours > 0) {
            return "En " + hours + "h " + minutes + "m";
        } else {
            return "En " + minutes + " min";
        }
    }

    //  MÉTODO para obtener clase CSS según estado
    public String getEventColorClass() {
        if (isToday()) {
            return "event-today";
        } else if (isUpcoming()) {
            return "event-upcoming";
        } else if (isOngoing()) {
            return "event-ongoing";
        } else {
            return "event-past";
        }
    }

    //  MÉTODO para validar si el evento es válido
    public boolean isValidEvent() {
        return title != null && !title.trim().isEmpty()
                && startDate != null
                && endDate != null
                && !endDate.isBefore(startDate);
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

    public String getCalendarColor() { return calendarColor; }

    public void setCalendarColor(String calendarColor) { this.calendarColor = calendarColor; }

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

    public void setCustomColor(String color) {
    }
}