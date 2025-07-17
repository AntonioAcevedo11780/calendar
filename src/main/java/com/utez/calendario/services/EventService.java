package com.utez.calendario.services;

import com.utez.calendario.config.DatabaseConfig;
import com.utez.calendario.models.Event;
import java.sql.*;
import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class EventService {
    private static EventService instance;
    // Pool de hilos dedicado para operaciones de BD
    private final ExecutorService executor = Executors.newFixedThreadPool(5);
    // Caché de calendarios por usuario {userId -> {calendarName -> calendarId}}
    private final Map<String, Map<String, String>> userCalendars = new HashMap<>();
    private static final String DATE_TIME_FORMAT = "yyyy-MM-dd HH:mm:ss";

    private EventService() {}

    public static EventService getInstance() {
        if (instance == null) instance = new EventService();
        return instance;
    }

    // ========== MÉTODOS ASÍNCRONOS ==========
    /**
     * Obtiene todos los eventos de un usuario para un mes específico de forma asíncrona
     */
    public CompletableFuture<List<Event>> getEventsForMonthAsync(String userId, LocalDate date) {
        return CompletableFuture.supplyAsync(() -> getEventsForMonth(userId, date), executor);
    }

    /**
     * Obtiene todos los eventos de un usuario para una fecha específica de forma asíncrona
     */
    public CompletableFuture<List<Event>> getEventsForDateAsync(String userId, LocalDate date) {
        return CompletableFuture.supplyAsync(() -> getEventsForDate(userId, date), executor);
    }

    /**
     * Alias para getEventsForDateAsync - para mantener compatibilidad con CalendarDayController
     */
    public CompletableFuture<List<Event>> getEventsForDayAsync(String userId, LocalDate date) {
        return getEventsForDateAsync(userId, date);
    }

    /**
     * Obtiene todos los eventos de un usuario para una semana específica de forma asíncrona
     */
    public CompletableFuture<List<Event>> getEventsForWeekAsync(String userId, LocalDate startDate, LocalDate endDate) {
        return CompletableFuture.supplyAsync(() -> getEventsForWeek(userId, startDate, endDate), executor);
    }

    /**
     * Obtiene todos los eventos de un usuario para un rango de fechas específico de forma asíncrona
     */
    public CompletableFuture<List<Event>> getEventsForDateRangeAsync(String userId, LocalDate startDate, LocalDate endDate) {
        return CompletableFuture.supplyAsync(() -> getEventsForDateRange(userId, startDate, endDate), executor);
    }

    /**
     * Crea un nuevo evento de forma asíncrona
     */
    public CompletableFuture<Boolean> createEventAsync(Event event) {
        return CompletableFuture.supplyAsync(() -> createEvent(event), executor);
    }

    /**
     * Actualiza un evento existente de forma asíncrona
     */
    public CompletableFuture<Boolean> updateEventAsync(Event event) {
        return CompletableFuture.supplyAsync(() -> updateEvent(event), executor);
    }

    /**
     * Elimina un evento de forma asíncrona (marca como inactivo)
     */
    public CompletableFuture<Boolean> deleteEventAsync(String eventId) {
        return CompletableFuture.supplyAsync(() -> deleteEvent(eventId), executor);
    }

    /**
     * Obtiene o crea un calendario para el usuario de forma asíncrona
     */
    public CompletableFuture<String> getOrCreateUserCalendarAsync(String userId) {
        return CompletableFuture.supplyAsync(() -> getOrCreateUserCalendar(userId), executor);
    }

    /**
     * Inicializa los calendarios para un usuario de forma asíncrona
     */
    public CompletableFuture<Void> initializeUserCalendarsAsync(String userId) {
        return CompletableFuture.supplyAsync(() -> { initializeUserCalendars(userId); return null; }, executor);
    }

    // ========== MÉTODOS DE CONSULTA ==========
    /**
     * Obtiene todos los eventos de un usuario para un mes específico
     */
    public List<Event> getEventsForMonth(String userId, LocalDate date) {
        return getEventsByDateRange(userId, date.withDayOfMonth(1), date.withDayOfMonth(date.lengthOfMonth()),
                "Encontrados %d eventos para " + date.getMonth() + " " + date.getYear());
    }

    /**
     * Obtiene todos los eventos de un usuario para una fecha específica
     */
    public List<Event> getEventsForDate(String userId, LocalDate date) {
        return getEventsByDateRange(userId, date, date, "Encontrados %d eventos para " + date);
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
        return getEventsByDateRange(userId, startDate, endDate,
                "Encontrados %d eventos para la semana " + startDate + " - " + endDate);
    }

    /**
     * Obtiene todos los eventos de un usuario para un rango de fechas específico
     */
    public List<Event> getEventsForDateRange(String userId, LocalDate startDate, LocalDate endDate) {
        return getEventsForWeek(userId, startDate, endDate);
    }

    // ========== MÉTODOS CRUD ==========
    /**
     * Crear un nuevo evento en la base de datos
     */
    public boolean createEvent(Event event) {
        // Si no tiene ID de calendario, verificar si el usuario ya tiene uno o crear uno nuevo
        if (event.getCalendarId() == null || event.getCalendarId().isEmpty()) {
            initializeUserCalendars(event.getCreatorId());
            event.setCalendarId(getDefaultCalendarId(event.getCreatorId()));
        }

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(
                     "INSERT INTO EVENTS (EVENT_ID, CALENDAR_ID, CREATOR_ID, TITLE, DESCRIPTION, " +
                             "START_DATE, END_DATE, ALL_DAY, LOCATION, RECURRENCE, ACTIVE, CREATED_DATE) " +
                             "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'Y', SYSDATE)")) {

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
            if (result > 0) {
                log("Evento creado: '" + event.getTitle() + "' (ID: " + event.getEventId() + ")");
                return true;
            }
            logError("No se pudo crear el evento");
            return false;
        } catch (SQLException e) {
            logError("Error creando evento: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Actualizar un evento existente
     */
    public boolean updateEvent(Event event) {
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(
                     "UPDATE EVENTS SET TITLE = ?, DESCRIPTION = ?, START_DATE = ?, END_DATE = ?, " +
                             "ALL_DAY = ?, LOCATION = ?, RECURRENCE = ?, MODIFIED_DATE = SYSDATE " +
                             "WHERE EVENT_ID = ? AND ACTIVE = 'Y'")) {

            pstmt.setString(1, event.getTitle());
            pstmt.setString(2, event.getDescription());
            pstmt.setTimestamp(3, Timestamp.valueOf(event.getStartDate()));
            pstmt.setTimestamp(4, Timestamp.valueOf(event.getEndDate()));
            pstmt.setString(5, String.valueOf(event.getAllDay()));
            pstmt.setString(6, event.getLocation());
            pstmt.setString(7, event.getRecurrence());
            pstmt.setString(8, event.getEventId());

            int result = pstmt.executeUpdate();
            if (result > 0) {
                log("Evento actualizado: '" + event.getTitle() + "'");
                return true;
            }
            logError("No se pudo actualizar el evento");
            return false;
        } catch (SQLException e) {
            logError("Error actualizando evento: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Eliminar un evento (cambiar ACTIVE a 'N')
     */
    public boolean deleteEvent(String eventId) {
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(
                     "UPDATE EVENTS SET ACTIVE = 'N', MODIFIED_DATE = SYSDATE WHERE EVENT_ID = ?")) {

            pstmt.setString(1, eventId);
            int result = pstmt.executeUpdate();

            if (result > 0) {
                log("Evento eliminado: " + eventId);
                return true;
            }
            logError("No se pudo eliminar el evento");
            return false;
        } catch (SQLException e) {
            logError("Error eliminando evento: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    // ========== GESTIÓN DE CALENDARIOS ==========
    /**
     * Inicializa los calendarios para un usuario
     * Crea los calendarios predeterminados si no existen
     */
    public void initializeUserCalendars(String userId) {
        if (userCalendars.containsKey(userId)) return;

        Map<String, String> calendarsMap = new HashMap<>();
        // Definición de calendarios predeterminados con sus colores
        String[][] calendarData = {
                {"Mis Clases", "Calendario principal", "#1976D2"},
                {"Tareas y Proyectos", "Calendario para tareas académicas", "#FF5722"},
                {"Personal", "Calendario de eventos personales", "#4CAF50"},
                {"Exámenes", "Calendario de exámenes", "#9C27B0"}
        };

        try (Connection conn = DatabaseConfig.getConnection()) {
            for (String[] calData : calendarData) {
                String calId = createCalendarIfNotExists(userId, calData[0], calData[1], calData[2], conn);
                calendarsMap.put(calData[0], calId);
            }
            userCalendars.put(userId, calendarsMap);
            log("Calendarios inicializados para el usuario " + userId);
        } catch (SQLException e) {
            logError("Error inicializando calendarios: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Crea un calendario si no existe y devuelve su ID
     */
    private String createCalendarIfNotExists(String userId, String name, String description, String color, Connection conn) throws SQLException {
        // Generar ID único y estable para el calendario - LIMITADO A 10 CARACTERES MÁXIMO
        // Formato: C + primeros 4 dígitos del hash de userId + primeros 5 dígitos del hash del nombre
        String userPart = String.valueOf(Math.abs(userId.hashCode()) % 10000);
        String namePart = String.valueOf(Math.abs(name.hashCode()) % 10000);

        // Asegurar que tenga 4 dígitos rellenando con ceros a la izquierda si es necesario
        userPart = String.format("%04d", Integer.parseInt(userPart));
        namePart = String.format("%04d", Integer.parseInt(namePart));

        // Combinamos para formar un ID de 9 caracteres
        String calendarId = "C" + userPart + namePart;

        // Verificar si ya existe
        try (PreparedStatement checkStmt = conn.prepareStatement("SELECT CALENDAR_ID FROM CALENDARS WHERE OWNER_ID = ? AND NAME = ? AND ACTIVE = 'Y' AND ROWNUM = 1")) {
            checkStmt.setString(1, userId);
            checkStmt.setString(2, name);
            ResultSet rs = checkStmt.executeQuery();

            if (rs.next()) {
                String existingId = rs.getString("CALENDAR_ID");
                log("Calendario existente encontrado: " + existingId + " (" + name + ")");
                return existingId;
            }
        }

        // Verificar si el ID generado ya existe
        boolean idExists = false;
        try (PreparedStatement checkIdStmt = conn.prepareStatement("SELECT COUNT(*) FROM CALENDARS WHERE CALENDAR_ID = ?")) {
            checkIdStmt.setString(1, calendarId);
            ResultSet rs = checkIdStmt.executeQuery();
            if (rs.next()) {
                idExists = rs.getInt(1) > 0;
            }
        }

        // Si ya exitse, añadir un dígito aleatorio
        if (idExists) {
            int randomDigit = new Random().nextInt(10);
            // Truncar el ID para mantenerlo dentro del límite de 10 caracteres
            calendarId = calendarId.substring(0, 8) + randomDigit;
        }

        // Si no existe, crear uno nuevo
        try (PreparedStatement insertStmt = conn.prepareStatement(
                "INSERT INTO CALENDARS (CALENDAR_ID, OWNER_ID, NAME, DESCRIPTION, COLOR, ACTIVE, CREATED_DATE) " +
                        "VALUES (?, ?, ?, ?, ?, 'Y', SYSDATE)")) {

            insertStmt.setString(1, calendarId);
            insertStmt.setString(2, userId);
            insertStmt.setString(3, name);
            insertStmt.setString(4, description);
            insertStmt.setString(5, color);
            insertStmt.executeUpdate();

            log("Calendario creado: " + name + " (ID: " + calendarId + ")");
        }

        return calendarId;
    }

    /**
     * Obtiene o crea un calendario para el usuario
     * Si el usuario ya tiene un calendario, lo devuelve
     * Si no, crea uno nuevo con ID corto
     */
    public String getOrCreateUserCalendar(String userId) {
        // Inicializar calendarios si aún no se ha hecho
        initializeUserCalendars(userId);

        // Devolver el calendario de "Mis Clases"
        Map<String, String> userCalendarMap = userCalendars.get(userId);
        if (userCalendarMap != null && userCalendarMap.containsKey("Mis Clases")) {
            return userCalendarMap.get("Mis Clases");
        }

        // Como respaldo, intentamos buscar o crear en la base de datos
        try (Connection conn = DatabaseConfig.getConnection()) {
            String calendarId = createCalendarIfNotExists(userId, "Mis Clases", "Calendario principal", "#1976D2", conn);

            if (userCalendarMap == null) {
                userCalendarMap = new HashMap<>();
                userCalendars.put(userId, userCalendarMap);
            }
            userCalendarMap.put("Mis Clases", calendarId);

            return calendarId;
        } catch (SQLException e) {
            logError("Error creando calendario: " + e.getMessage());

            // Como último recurso, generar un ID corto
            String userPart = String.format("%04d", Math.abs(userId.hashCode()) % 10000);
            return "C" + userPart + "0000";
        }
    }

    /**
     * Obtener el calendario por defecto del usuario
     * Lo crea si no existe
     */
    public String getDefaultCalendarId(String userId) {
        return getOrCreateUserCalendar(userId);
    }

    /**
     * Obtiene un calendario por nombre para un usuario específico
     */
    public String getCalendarIdByName(String userId, String calendarName) {
        // Asegurar que los calendarios estén inicializados
        initializeUserCalendars(userId);

        // Verificar si está en caché
        Map<String, String> userCalendarMap = userCalendars.get(userId);
        if (userCalendarMap != null && userCalendarMap.containsKey(calendarName)) {
            return userCalendarMap.get(calendarName);
        }

        // No está en caché, buscar o crear en la base de datos
        try (Connection conn = DatabaseConfig.getConnection()) {
            // Seleccionar el color según el tipo de calendario
            String color =
                    calendarName.equals("Mis Clases") ? "#1976D2" :
                            calendarName.equals("Tareas y Proyectos") ? "#FF5722" :
                                    calendarName.equals("Personal") ? "#4CAF50" :
                                            calendarName.equals("Exámenes") ? "#9C27B0" : "#2196F3";

            String calendarId = createCalendarIfNotExists(
                    userId, calendarName, "Calendario " + calendarName, color, conn);

            // Actualizar caché
            if (userCalendarMap == null) {
                userCalendarMap = new HashMap<>();
                userCalendars.put(userId, userCalendarMap);
            }
            userCalendarMap.put(calendarName, calendarId);

            return calendarId;
        } catch (SQLException e) {
            logError("Error obteniendo calendario por nombre: " + e.getMessage());
            return getDefaultCalendarId(userId);
        }
    }

    // ========== MÉTODOS AUXILIARES ==========
    /**
     * Método unificado para obtener eventos por rango de fechas
     */
    private List<Event> getEventsByDateRange(String userId, LocalDate startDate, LocalDate endDate, String logFormat) {
        List<Event> events = new ArrayList<>();
        // Ajustar la condición SQL según si es un día específico o un rango
        String dateCondition = startDate.equals(endDate) ? "TRUNC(e.START_DATE) = ?" : "TRUNC(e.START_DATE) >= ? AND TRUNC(e.START_DATE) <= ?";

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(
                     "SELECT e.* FROM EVENTS e INNER JOIN CALENDARS c ON e.CALENDAR_ID = c.CALENDAR_ID " +
                             "WHERE c.OWNER_ID = ? AND e.ACTIVE = 'Y' AND c.ACTIVE = 'Y' AND " + dateCondition + " ORDER BY e.START_DATE")) {

            pstmt.setString(1, userId);
            pstmt.setDate(2, Date.valueOf(startDate));
            if (!startDate.equals(endDate)) {
                pstmt.setDate(3, Date.valueOf(endDate));
            }

            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                events.add(mapResultSetToEvent(rs));
            }

            log(String.format(logFormat, events.size()));
        } catch (SQLException e) {
            logError("Error obteniendo eventos: " + e.getMessage());
            e.printStackTrace();
        }
        return events;
    }

    /**
     * Genera un ID para eventos con el formato E + fecha (YYYYMMDD) + hora (HHMMSS) + número aleatorio
     * Por ejemplo: E20250713235030123
     */
    private String generateShortId(String prefix) {
        LocalDateTime now = LocalDateTime.now();
        String dateTime = now.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String random = String.format("%03d", new Random().nextInt(1000));
        String id = prefix + dateTime + random;
        log("ID generado: " + id);
        return id;
    }

    /**
     * Mapea un ResultSet a un objeto Event
     */
    private Event mapResultSetToEvent(ResultSet rs) throws SQLException {
        Event event = new Event();
        event.setEventId(rs.getString("EVENT_ID"));
        event.setCalendarId(rs.getString("CALENDAR_ID"));
        event.setCreatorId(rs.getString("CREATOR_ID"));
        event.setTitle(rs.getString("TITLE"));
        event.setDescription(rs.getString("DESCRIPTION"));

        Timestamp startDate = rs.getTimestamp("START_DATE");
        if (startDate != null) event.setStartDate(startDate.toLocalDateTime());

        Timestamp endDate = rs.getTimestamp("END_DATE");
        if (endDate != null) event.setEndDate(endDate.toLocalDateTime());

        String allDay = rs.getString("ALL_DAY");
        if (allDay != null && !allDay.isEmpty()) event.setAllDay(allDay.charAt(0));

        event.setLocation(rs.getString("LOCATION"));
        event.setRecurrence(rs.getString("RECURRENCE"));

        return event;
    }

    /**
     * Registra un mensaje de éxito en la consola
     */
    private void log(String message) {
        System.out.println("✓ [" + LocalDateTime.now().format(DateTimeFormatter.ofPattern(DATE_TIME_FORMAT)) + "] " + message);
    }

    /**
     * Registra un mensaje de error en la consola
     */
    private void logError(String message) {
        System.err.println("✗ [" + LocalDateTime.now().format(DateTimeFormatter.ofPattern(DATE_TIME_FORMAT)) + "] " + message);
    }

    /**
     * Obtiene la lista de nombres de calendarios de un usuario
     */
    public List<String> getUserCalendarNames(String userId) {
        List<String> calendarNames = new ArrayList<>();
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement pstmt = conn.prepareStatement("SELECT NAME FROM CALENDARS WHERE OWNER_ID = ? AND ACTIVE = 'Y'")) {
            pstmt.setString(1, userId);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                calendarNames.add(rs.getString("NAME"));
            }
        } catch (SQLException e) {
            logError("Error obteniendo nombres de calendarios: " + e.getMessage());
        }
        return calendarNames;
    }

    /**
     * Cierra el ExecutorService cuando la aplicación se cierra
     */
    public void shutdown() {
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
        }
    }
}