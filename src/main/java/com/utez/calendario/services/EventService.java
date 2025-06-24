package com.utez.calendario.services;

import com.utez.calendario.config.DatabaseConfig;
import com.utez.calendario.models.Event;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class EventService {
    private static EventService instance;

    private EventService() {}

    public static EventService getInstance() {
        if (instance == null) {
            instance = new EventService();
        }
        return instance;
    }

    // ========== MÉTODOS DE LECTURA ==========

    /**
     * Obtiene todos los eventos de un usuario para un mes específico
     */
    public List<Event> getEventsForMonth(String userId, LocalDate date) {
        List<Event> events = new ArrayList<>();
        LocalDate startOfMonth = date.withDayOfMonth(1);
        LocalDate endOfMonth = date.withDayOfMonth(date.lengthOfMonth());

        String sql = """
            SELECT e.* FROM EVENTS e
            INNER JOIN CALENDARS c ON e.CALENDAR_ID = c.CALENDAR_ID
            WHERE c.OWNER_ID = ? AND e.ACTIVE = 'Y' AND c.ACTIVE = 'Y'
            AND DATE(e.START_DATE) >= ? AND DATE(e.START_DATE) <= ?
            ORDER BY e.START_DATE
        """;

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, userId);
            pstmt.setDate(2, Date.valueOf(startOfMonth));
            pstmt.setDate(3, Date.valueOf(endOfMonth));

            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                Event event = mapResultSetToEvent(rs);
                events.add(event);
            }

            System.out.println("✓ [" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "] " +
                    "Encontrados " + events.size() + " eventos para " + date.getMonth() + " " + date.getYear());

        } catch (SQLException e) {
            System.err.println("✗ [" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "] " +
                    "Error obteniendo eventos: " + e.getMessage());
            e.printStackTrace();
        }
        return events;
    }

    /**
     * Obtiene todos los eventos de un usuario para una fecha específica
     */
    public List<Event> getEventsForDate(String userId, LocalDate date) {
        List<Event> events = new ArrayList<>();

        String sql = """
            SELECT e.* FROM EVENTS e
            INNER JOIN CALENDARS c ON e.CALENDAR_ID = c.CALENDAR_ID
            WHERE c.OWNER_ID = ? AND e.ACTIVE = 'Y' AND c.ACTIVE = 'Y'
            AND DATE(e.START_DATE) = ?
            ORDER BY e.START_DATE
        """;

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, userId);
            pstmt.setDate(2, Date.valueOf(date));

            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                Event event = mapResultSetToEvent(rs);
                events.add(event);
            }

            System.out.println("✓ [" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "] " +
                    "Encontrados " + events.size() + " eventos para " + date);

        } catch (SQLException e) {
            System.err.println("✗ [" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "] " +
                    "Error obteniendo eventos del día: " + e.getMessage());
        }
        return events;
    }

    /**
     * Obtiene todos los eventos de un usuario para una semana específica
     */
    public List<Event> getEventsForWeek(String userId, LocalDate startDate, LocalDate endDate) {
        List<Event> events = new ArrayList<>();

        String sql = """
            SELECT e.* FROM EVENTS e
            INNER JOIN CALENDARS c ON e.CALENDAR_ID = c.CALENDAR_ID
            WHERE c.OWNER_ID = ? AND e.ACTIVE = 'Y' AND c.ACTIVE = 'Y'
            AND DATE(e.START_DATE) >= ? AND DATE(e.START_DATE) <= ?
            ORDER BY e.START_DATE
        """;

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, userId);
            pstmt.setDate(2, Date.valueOf(startDate));
            pstmt.setDate(3, Date.valueOf(endDate));

            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                Event event = mapResultSetToEvent(rs);
                events.add(event);
            }

            System.out.println("✓ [" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "] " +
                    "Encontrados " + events.size() + " eventos para la semana " + startDate + " - " + endDate);

        } catch (SQLException e) {
            System.err.println("✗ [" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "] " +
                    "Error obteniendo eventos de la semana: " + e.getMessage());
            e.printStackTrace();
        }
        return events;
    }

    // ========== MÉTODOS CRUD ==========

    /**
     * Crear un nuevo evento en la base de datos
     */
    public boolean createEvent(Event event) {
        String sql = """
            INSERT INTO EVENTS (EVENT_ID, CALENDAR_ID, CREATOR_ID, TITLE, DESCRIPTION,
                              START_DATE, END_DATE, ALL_DAY, LOCATION, RECURRENCE, ACTIVE, CREATED_DATE)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'Y', NOW())
        """;

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            // Generar ID único si no existe
            if (event.getEventId() == null || event.getEventId().isEmpty()) {
                event.setEventId(generateEventId());
            }

            pstmt.setString(1, event.getEventId());
            pstmt.setString(2, event.getCalendarId());
            pstmt.setString(3, event.getCreatorId());
            pstmt.setString(4, event.getTitle());
            pstmt.setString(5, event.getDescription());
            pstmt.setTimestamp(6, Timestamp.valueOf(event.getStartDate()));
            pstmt.setTimestamp(7, Timestamp.valueOf(event.getEndDate()));
            pstmt.setString(8, String.valueOf(event.getAllDay()));
            pstmt.setString(9, event.getLocation());
            pstmt.setString(10, event.getRecurrence());

            int result = pstmt.executeUpdate();
            boolean success = result > 0;

            if (success) {
                System.out.println("✓ [" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "] " +
                        "Evento creado: '" + event.getTitle() + "' (ID: " + event.getEventId() + ")");
            } else {
                System.err.println("✗ [" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "] " +
                        "No se pudo crear el evento");
            }

            return success;

        } catch (SQLException e) {
            System.err.println("✗ [" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "] " +
                    "Error creando evento: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Actualizar un evento existente
     */
    public boolean updateEvent(Event event) {
        String sql = """
            UPDATE EVENTS SET 
                TITLE = ?, DESCRIPTION = ?, START_DATE = ?, END_DATE = ?,
                ALL_DAY = ?, LOCATION = ?, RECURRENCE = ?, MODIFIED_DATE = NOW()
            WHERE EVENT_ID = ? AND ACTIVE = 'Y'
        """;

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, event.getTitle());
            pstmt.setString(2, event.getDescription());
            pstmt.setTimestamp(3, Timestamp.valueOf(event.getStartDate()));
            pstmt.setTimestamp(4, Timestamp.valueOf(event.getEndDate()));
            pstmt.setString(5, String.valueOf(event.getAllDay()));
            pstmt.setString(6, event.getLocation());
            pstmt.setString(7, event.getRecurrence());
            pstmt.setString(8, event.getEventId());

            int result = pstmt.executeUpdate();
            boolean success = result > 0;

            if (success) {
                System.out.println("✓ [" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "] " +
                        "Evento actualizado: '" + event.getTitle() + "'");
            } else {
                System.err.println("✗ [" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "] " +
                        "No se pudo actualizar el evento");
            }

            return success;

        } catch (SQLException e) {
            System.err.println("✗ [" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "] " +
                    "Error actualizando evento: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Eliminar un evento (cambiar ACTIVE a 'N')
     */
    public boolean deleteEvent(String eventId) {
        String sql = "UPDATE EVENTS SET ACTIVE = 'N', MODIFIED_DATE = NOW() WHERE EVENT_ID = ?";

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, eventId);

            int result = pstmt.executeUpdate();
            boolean success = result > 0;

            if (success) {
                System.out.println("✓ [" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "] " +
                        "Evento eliminado: " + eventId);
            } else {
                System.err.println("✗ [" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "] " +
                        "No se pudo eliminar el evento");
            }

            return success;

        } catch (SQLException e) {
            System.err.println("✗ [" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "] " +
                    "Error eliminando evento: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Obtener el calendario por defecto del usuario
     */
    public String getDefaultCalendarId(String userId) {
        String sql = "SELECT CALENDAR_ID FROM CALENDARS WHERE OWNER_ID = ? AND ACTIVE = 'Y' LIMIT 1";

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, userId);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                String calendarId = rs.getString("CALENDAR_ID");
                System.out.println("✓ Calendario por defecto encontrado: " + calendarId);
                return calendarId;
            } else {
                System.out.println("⚠ No se encontró calendario por defecto para usuario: " + userId);
            }

        } catch (SQLException e) {
            System.err.println("✗ Error obteniendo calendario por defecto: " + e.getMessage());
        }

        return null;
    }

    // ========== GENERADOR DE IDS ==========

    private String generateEventId() {
        LocalDateTime now = LocalDateTime.now();
        String timestamp = now.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String random = String.format("%03d", new Random().nextInt(1000));

        String eventId = "EVT" + timestamp + random;
        System.out.println("✓ ID generado: " + eventId);
        return eventId;
    }

    // ========== MÉTODOS AUXILIARES ==========

    private Event mapResultSetToEvent(ResultSet rs) throws SQLException {
        Event event = new Event();
        event.setEventId(rs.getString("EVENT_ID"));
        event.setCalendarId(rs.getString("CALENDAR_ID"));
        event.setCreatorId(rs.getString("CREATOR_ID"));
        event.setTitle(rs.getString("TITLE"));
        event.setDescription(rs.getString("DESCRIPTION"));

        Timestamp startDate = rs.getTimestamp("START_DATE");
        if (startDate != null) {
            event.setStartDate(startDate.toLocalDateTime());
        }

        Timestamp endDate = rs.getTimestamp("END_DATE");
        if (endDate != null) {
            event.setEndDate(endDate.toLocalDateTime());
        }

        String allDay = rs.getString("ALL_DAY");
        if (allDay != null && !allDay.isEmpty()) {
            event.setAllDay(allDay.charAt(0));
        }

        event.setLocation(rs.getString("LOCATION"));
        event.setRecurrence(rs.getString("RECURRENCE"));

        return event;
    }

    /**
     * Obtiene todos los eventos de un usuario para un rango de fechas específico
     */
    public List<Event> getEventsForDateRange(String userId, LocalDate startDate, LocalDate endDate) {
        return getEventsForWeek(userId, startDate, endDate); // Reutilizamos el método de semana
    }
}