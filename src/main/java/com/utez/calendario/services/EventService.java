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
import java.util.UUID;

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

        // Esta consulta solo muestra eventos de calendarios propiedad del usuario
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

        // Esta consulta solo muestra eventos de calendarios propiedad del usuario
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
     * Alias para getEventsForDate - para mantener compatibilidad con CalendarDayController
     */
    public List<Event> getEventsForDay(String userId, LocalDate date) {
        return getEventsForDate(userId, date);
    }

    /**
     * Obtiene todos los eventos de un usuario para una semana específica
     */
    public List<Event> getEventsForWeek(String userId, LocalDate startDate, LocalDate endDate) {
        List<Event> events = new ArrayList<>();

        // Esta consulta solo muestra eventos de calendarios propiedad del usuario
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
        // Si no tiene ID de calendario, verificar si el usuario ya tiene uno o crear uno nuevo
        if (event.getCalendarId() == null || event.getCalendarId().isEmpty()) {
            String calendarId = getOrCreateUserCalendar(event.getCreatorId());
            event.setCalendarId(calendarId);
        }

        String sql = """
            INSERT INTO EVENTS (EVENT_ID, CALENDAR_ID, CREATOR_ID, TITLE, DESCRIPTION,
                              START_DATE, END_DATE, ALL_DAY, LOCATION, RECURRENCE, ACTIVE, CREATED_DATE)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'Y', NOW())
        """;

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            // Generar ID único corto para el evento
            if (event.getEventId() == null || event.getEventId().isEmpty()) {
                event.setEventId(generateShortId("E"));
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
     * Obtiene o crea un calendario para el usuario
     * Si el usuario ya tiene un calendario, lo devuelve
     * Si no, crea uno nuevo con ID corto
     */
    public String getOrCreateUserCalendar(String userId) {
        String calendarId = null;

        // Primero intentamos encontrar un calendario existente para el usuario
        String selectSql = "SELECT CALENDAR_ID FROM CALENDARS WHERE OWNER_ID = ? AND ACTIVE = 'Y' LIMIT 1";

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(selectSql)) {

            pstmt.setString(1, userId);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                calendarId = rs.getString("CALENDAR_ID");
                System.out.println("✓ Calendario existente encontrado: " + calendarId);
                return calendarId;
            }

            // No hay calendario existente, crear uno nuevo (usando la misma conexión)
            String newCalendarId = createCalendarForUser(userId, conn);
            if (newCalendarId != null) {
                return newCalendarId;
            } else {
                // Si falla la creación, intentamos una última opción
                return "C" + userId.hashCode() % 10000;
            }

        } catch (SQLException e) {
            System.err.println("✗ Error obteniendo/creando calendario: " + e.getMessage());
            // Último recurso: un ID basado en el hash del userId
            return "C" + Math.abs(userId.hashCode() % 10000);
        }
    }

    /**
     * Crea un nuevo calendario para un usuario con ID corto (máximo 10 caracteres)
     */
    private String createCalendarForUser(String userId, Connection conn) {
        // Generar un ID más aleatorio para el calendario
        String timeStamp = String.valueOf(System.currentTimeMillis() % 100000); // 5 dígitos del tiempo
        String random = String.format("%04d", new Random().nextInt(10000)); // 4 dígitos aleatorios
        String calendarId = "C" + timeStamp + random; // "C" + 5 + 4 = 10 caracteres

        String name = "Mi Calendario";
        String description = "Calendario personal";
        String color = "#1976D2"; // Azul

        // Verificar si el ID ya existe antes de intentar insertarlo
        boolean idExists = checkIfCalendarIdExists(calendarId, conn);
        int attempts = 0;

        // Si el ID ya existe, generar uno nuevo hasta encontrar uno disponible
        while (idExists && attempts < 5) {
            random = String.format("%04d", new Random().nextInt(10000));
            calendarId = "C" + timeStamp + random;
            idExists = checkIfCalendarIdExists(calendarId, conn);
            attempts++;
        }

        // Si después de 5 intentos no encontramos un ID único, usamos un UUID corto
        if (idExists) {
            calendarId = "C" + Math.abs(UUID.randomUUID().hashCode() % 100000000); // 9 dígitos
            idExists = checkIfCalendarIdExists(calendarId, conn);

            if (idExists) {
                // Como último recurso, un ID con microsegundos
                calendarId = "C" + System.nanoTime() % 100000000;
            }
        }

        String sql = """
        INSERT INTO CALENDARS (CALENDAR_ID, OWNER_ID, NAME, DESCRIPTION, COLOR, ACTIVE, CREATED_DATE)
        VALUES (?, ?, ?, ?, ?, 'Y', NOW())
    """;

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, calendarId);
            pstmt.setString(2, userId);
            pstmt.setString(3, name);
            pstmt.setString(4, description);
            pstmt.setString(5, color);

            int result = pstmt.executeUpdate();
            boolean success = result > 0;

            if (success) {
                System.out.println("✓ [" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "] " +
                        "Calendario creado para el usuario: " + userId + " (ID: " + calendarId + ")");
                return calendarId;
            } else {
                System.err.println("✗ [" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "] " +
                        "No se pudo crear el calendario para el usuario: " + userId);
                return null;
            }

        } catch (SQLException e) {
            System.err.println("✗ [" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "] " +
                    "Error creando calendario para el usuario " + userId + ": " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Verifica si un ID de calendario ya existe
     */
    private boolean checkIfCalendarIdExists(String calendarId, Connection conn) {
        String sql = "SELECT COUNT(*) FROM CALENDARS WHERE CALENDAR_ID = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, calendarId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            System.err.println("Error verificando ID de calendario: " + e.getMessage());
        }
        return false; // Si hay un error, asumimos que no existe
    }

    /**
     * Obtener el calendario por defecto del usuario
     * Lo crea si no existe
     */
    public String getDefaultCalendarId(String userId) {
        return getOrCreateUserCalendar(userId);
    }

    // ========== GENERADOR DE IDS ==========

    /**
     * Genera un ID para eventos con el formato EVT + fecha (YYYYMMDD) + hora (HHMMSS) + número aleatorio
     */
    private String generateShortId(String prefix) {
        LocalDateTime now = LocalDateTime.now();
        String date = now.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String time = now.format(DateTimeFormatter.ofPattern("HHmmss"));
        String random = String.format("%03d", new Random().nextInt(1000));

        String id = prefix + date + time + random;
        System.out.println("✓ ID generado: " + id);
        return id;
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