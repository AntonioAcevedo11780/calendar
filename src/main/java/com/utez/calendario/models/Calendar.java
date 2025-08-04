package com.utez.calendario.models;

import java.util.List;
import java.util.ArrayList;
import java.util.Random;

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
    private boolean isDefault; // Añadido para marcar si es calendario predeterminado

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

    // Constructor adicional para el controlador
    public Calendar(String calendarId, String name, String color, String ownerId) {
        this.calendarId = calendarId;
        this.name = name;
        this.color = color;
        this.ownerId = ownerId;
        this.active = 'Y';
        this.createdDate = LocalDateTime.now();
    }

    // Funciones para el administrador de calendarios (pronto xd)
    public List<Event> getCurrentMonthEvents() {
        List<Event> events = new ArrayList<>();

        String sql = """
            SELECT EVENT_ID, TITLE, START_DATE, END_DATE, LOCATION, ALL_DAY, DESCRIPTION
            FROM EVENTS 
            WHERE CALENDAR_ID = ? 
            AND START_DATE >= TRUNC(SYSDATE, 'MM') 
            AND START_DATE < ADD_MONTHS(TRUNC(SYSDATE, 'MM'), 1)
            AND ACTIVE = 'Y'
            ORDER BY START_DATE
        """;

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, this.calendarId);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Event event = new Event();
                    event.setEventId(rs.getString("EVENT_ID"));
                    event.setCalendarId(this.calendarId);
                    event.setTitle(rs.getString("TITLE"));
                    event.setStartDate(rs.getObject("START_DATE", LocalDateTime.class));
                    event.setEndDate(rs.getObject("END_DATE", LocalDateTime.class));
                    event.setLocation(rs.getString("LOCATION"));
                    event.setAllDay(rs.getString("ALL_DAY").charAt(0));
                    event.setDescription(rs.getString("DESCRIPTION"));
                    event.setActive('Y');

                    events.add(event);
                }
            }
        } catch (Exception e) {
            System.err.println("Error obteniendo eventos del calendario " + this.calendarId + ": " + e.getMessage());
        }

        return events;
    }

    public String getDisplayName() {
        return (name != null && !name.trim().isEmpty()) ? name : "Calendario " + calendarId;
    }

    public static List<Calendar> getAllActiveCalendars() {
        List<Calendar> calendars = new ArrayList<>();

        String sql = """
            SELECT CALENDAR_ID, OWNER_ID, NAME, DESCRIPTION, COLOR, 
                   CREATED_DATE, MODIFIED_DATE 
            FROM CALENDARS 
            WHERE ACTIVE = 'Y'
            ORDER BY MODIFIED_DATE DESC
        """;

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                Calendar calendar = new Calendar();
                calendar.setCalendarId(rs.getString("CALENDAR_ID"));
                calendar.setOwnerId(rs.getString("OWNER_ID"));
                calendar.setName(rs.getString("NAME"));
                calendar.setDescription(rs.getString("DESCRIPTION"));
                calendar.setColor(rs.getString("COLOR"));
                calendar.setCreatedDate(rs.getObject("CREATED_DATE", LocalDateTime.class));
                calendar.setModifiedDate(rs.getObject("MODIFIED_DATE", LocalDateTime.class));
                calendar.setActive('Y');

                // Determinar si es un calendario predeterminado
                String name = calendar.getName();
                if (name != null && (name.equals("Mis Clases") || name.equals("Tareas y Proyectos") ||
                        name.equals("Personal") || name.equals("Exámenes"))) {
                    calendar.setDefault(true);
                }

                calendars.add(calendar);
            }

        } catch (SQLException e) {
            e.printStackTrace();
            System.err.println("Error al obtener los calendarios: " + e.getMessage());
        }

        return calendars;
    }

    /**
     * NUEVO MÉTODO requerido por el controlador
     * Obtiene TODOS los calendarios de un usuario específico (predeterminados + personalizados)
     */
    public static List<Calendar> getAllUserCalendars(String userId) {
        List<Calendar> calendars = new ArrayList<>();

        String sql = """
            SELECT CALENDAR_ID, OWNER_ID, NAME, DESCRIPTION, COLOR, 
                   CREATED_DATE, MODIFIED_DATE 
            FROM CALENDARS 
            WHERE OWNER_ID = ? 
            AND ACTIVE = 'Y'
            ORDER BY 
            CASE 
                WHEN NAME IN ('Mis Clases', 'Tareas y Proyectos', 'Personal', 'Exámenes') THEN 0
                ELSE 1
            END,
            CASE NAME
                WHEN 'Mis Clases' THEN 1
                WHEN 'Tareas y Proyectos' THEN 2
                WHEN 'Personal' THEN 3
                WHEN 'Exámenes' THEN 4
                ELSE 5
            END,
            CREATED_DATE DESC
        """;

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, userId);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Calendar calendar = new Calendar();
                    calendar.setCalendarId(rs.getString("CALENDAR_ID"));
                    calendar.setOwnerId(rs.getString("OWNER_ID"));
                    calendar.setName(rs.getString("NAME"));
                    calendar.setDescription(rs.getString("DESCRIPTION"));
                    calendar.setColor(rs.getString("COLOR"));
                    calendar.setCreatedDate(rs.getObject("CREATED_DATE", LocalDateTime.class));
                    calendar.setModifiedDate(rs.getObject("MODIFIED_DATE", LocalDateTime.class));
                    calendar.setActive('Y');

                    // Determinar si es predeterminado o personalizado
                    String name = calendar.getName();
                    boolean isDefault = name != null && (name.equals("Mis Clases") ||
                            name.equals("Tareas y Proyectos") ||
                            name.equals("Personal") ||
                            name.equals("Exámenes"));
                    calendar.setDefault(isDefault);

                    calendars.add(calendar);
                }
            }
        } catch (SQLException e) {
            System.err.println("Error obteniendo todos los calendarios del usuario " + userId + ": " + e.getMessage());
            e.printStackTrace();
        }

        return calendars;
    }

    /**
     * NUEVO MÉTODO requerido por el controlador
     * Obtiene un calendario específico por su ID
     */
    public static Calendar getCalendarById(String calendarId) {
        String sql = """
            SELECT CALENDAR_ID, OWNER_ID, NAME, DESCRIPTION, COLOR, 
                   CREATED_DATE, MODIFIED_DATE 
            FROM CALENDARS 
            WHERE CALENDAR_ID = ? 
            AND ACTIVE = 'Y'
        """;

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, calendarId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    Calendar calendar = new Calendar();
                    calendar.setCalendarId(rs.getString("CALENDAR_ID"));
                    calendar.setOwnerId(rs.getString("OWNER_ID"));
                    calendar.setName(rs.getString("NAME"));
                    calendar.setDescription(rs.getString("DESCRIPTION"));
                    calendar.setColor(rs.getString("COLOR"));
                    calendar.setCreatedDate(rs.getObject("CREATED_DATE", LocalDateTime.class));
                    calendar.setModifiedDate(rs.getObject("MODIFIED_DATE", LocalDateTime.class));
                    calendar.setActive('Y');

                    // Determinar si es predeterminado
                    String name = calendar.getName();
                    boolean isDefault = name != null && (name.equals("Mis Clases") ||
                            name.equals("Tareas y Proyectos") ||
                            name.equals("Personal") ||
                            name.equals("Exámenes"));
                    calendar.setDefault(isDefault);

                    return calendar;
                }
            }
        } catch (SQLException e) {
            System.err.println("Error obteniendo calendario por ID " + calendarId + ": " + e.getMessage());
            e.printStackTrace();
        }

        return null;
    }

    // MÉTODO para contar eventos activos del calendario
    public int getActiveEventsCount() {
        String sql = "SELECT COUNT(*) FROM EVENTS WHERE CALENDAR_ID = ? AND ACTIVE = 'Y'";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, this.calendarId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (Exception e) {
            System.err.println("Error contando eventos del calendario " + this.calendarId + ": " + e.getMessage());
        }

        return 0;
    }

    //  MÉTODO para verificar si el calendario tiene eventos
    public boolean hasEvents() {
        return getActiveEventsCount() > 0;
    }

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

    public boolean isDefault() {
        return isDefault;
    }

    public void setDefault(boolean isDefault) {
        this.isDefault = isDefault;
    }

    /**
     * Obtiene el número de calendarios personalizados (no predeterminados) de un usuario
     */
    public static int getUserCustomCalendarsCount(String userId) {
        int count = 0;
        String sql = """
        SELECT COUNT(*) 
        FROM CALENDARS 
        WHERE OWNER_ID = ? 
        AND NAME NOT IN ('Mis Clases', 'Tareas y Proyectos', 'Personal', 'Exámenes')
        AND ACTIVE = 'Y'
    """;

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, userId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    count = rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            System.err.println("Error contando calendarios personalizados: " + e.getMessage());
        }

        return count;
    }

    /**
     * Genera un ID único para un nuevo calendario
     */
    public static String generateCalendarId() {
        // Formato: CAL + 7 caracteres alfanuméricos aleatorios
        String characters = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder builder = new StringBuilder("CAL");
        Random random = new Random();

        for (int i = 0; i < 7; i++) {
            int index = random.nextInt(characters.length());
            builder.append(characters.charAt(index));
        }

        return builder.toString();
    }

    /**
     * Crea un nuevo calendario personalizado para el usuario
     */
    public static boolean createCustomCalendar(String userId, String name, String color) {
        if (getUserCustomCalendarsCount(userId) >= 5) {
            return false; // Límite alcanzado
        }

        String calendarId = generateCalendarId();
        String description = "Calendario personalizado";

        String sql = """
        INSERT INTO CALENDARS (CALENDAR_ID, OWNER_ID, NAME, DESCRIPTION, COLOR, ACTIVE, CREATED_DATE)
        VALUES (?, ?, ?, ?, ?, 'Y', CURRENT_TIMESTAMP)
    """;

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, calendarId);
            stmt.setString(2, userId);
            stmt.setString(3, name);
            stmt.setString(4, description);
            stmt.setString(5, color);

            int result = stmt.executeUpdate();
            return result > 0;

        } catch (SQLException e) {
            System.err.println("Error creando calendario personalizado: " + e.getMessage());
            return false;
        }
    }

    /**
     * Obtiene los calendarios personalizados de un usuario
     */
    public static List<Calendar> getUserCustomCalendars(String userId) {
        List<Calendar> customCalendars = new ArrayList<>();

        String sql = """
        SELECT CALENDAR_ID, OWNER_ID, NAME, DESCRIPTION, COLOR, 
               CREATED_DATE, MODIFIED_DATE 
        FROM CALENDARS 
        WHERE OWNER_ID = ?
        AND NAME NOT IN ('Mis Clases', 'Tareas y Proyectos', 'Personal', 'Exámenes')
        AND ACTIVE = 'Y'
        ORDER BY CREATED_DATE DESC
    """;

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, userId);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Calendar calendar = new Calendar();
                    calendar.setCalendarId(rs.getString("CALENDAR_ID"));
                    calendar.setOwnerId(rs.getString("OWNER_ID"));
                    calendar.setName(rs.getString("NAME"));
                    calendar.setDescription(rs.getString("DESCRIPTION"));
                    calendar.setColor(rs.getString("COLOR"));
                    calendar.setCreatedDate(rs.getObject("CREATED_DATE", LocalDateTime.class));
                    calendar.setModifiedDate(rs.getObject("MODIFIED_DATE", LocalDateTime.class));
                    calendar.setActive('Y');
                    calendar.setDefault(false); // No es predeterminado

                    customCalendars.add(calendar);
                }
            }
        } catch (SQLException e) {
            System.err.println("Error obteniendo calendarios personalizados: " + e.getMessage());
        }

        return customCalendars;
    }

    /**
     * Obtiene los calendarios predeterminados de un usuario
     */
    public static List<Calendar> getUserDefaultCalendars(String userId) {
        List<Calendar> defaultCalendars = new ArrayList<>();

        String sql = """
        SELECT CALENDAR_ID, OWNER_ID, NAME, DESCRIPTION, COLOR, 
               CREATED_DATE, MODIFIED_DATE 
        FROM CALENDARS 
        WHERE OWNER_ID = ?
        AND NAME IN ('Mis Clases', 'Tareas y Proyectos', 'Personal', 'Exámenes')
        AND ACTIVE = 'Y'
        ORDER BY 
        CASE NAME
            WHEN 'Mis Clases' THEN 1
            WHEN 'Tareas y Proyectos' THEN 2
            WHEN 'Personal' THEN 3
            WHEN 'Exámenes' THEN 4
        END
    """;

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, userId);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Calendar calendar = new Calendar();
                    calendar.setCalendarId(rs.getString("CALENDAR_ID"));
                    calendar.setOwnerId(rs.getString("OWNER_ID"));
                    calendar.setName(rs.getString("NAME"));
                    calendar.setDescription(rs.getString("DESCRIPTION"));
                    calendar.setColor(rs.getString("COLOR"));
                    calendar.setCreatedDate(rs.getObject("CREATED_DATE", LocalDateTime.class));
                    calendar.setModifiedDate(rs.getObject("MODIFIED_DATE", LocalDateTime.class));
                    calendar.setActive('Y');
                    calendar.setDefault(true); // Es predeterminado

                    defaultCalendars.add(calendar);
                }
            }
        } catch (SQLException e) {
            System.err.println("Error obteniendo calendarios predeterminados: " + e.getMessage());
        }

        return defaultCalendars;
    }

    /**
     * Elimina un calendario personalizado (solo marca como inactivo)
     * No permite eliminar calendarios predeterminados
     */
    public static boolean deleteCalendar(String calendarId) {
        // Verificar que no sea un calendario predeterminado
        if (calendarId.startsWith("CAL000000")) {
            System.err.println("No se pueden eliminar calendarios predeterminados");
            return false;
        }

        String sql = """
        UPDATE CALENDARS 
        SET ACTIVE = 'N', MODIFIED_DATE = CURRENT_TIMESTAMP 
        WHERE CALENDAR_ID = ? 
        AND ACTIVE = 'Y'
        AND NAME NOT IN ('Mis Clases', 'Tareas y Proyectos', 'Personal', 'Exámenes')
    """;

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, calendarId);

            int rowsAffected = stmt.executeUpdate();

            if (rowsAffected > 0) {
                System.out.println("Calendario " + calendarId + " marcado como inactivo");
                return true;
            } else {
                System.err.println("No se pudo eliminar el calendario " + calendarId +
                        " (posiblemente no existe o es predeterminado)");
                return false;
            }

        } catch (SQLException e) {
            System.err.println("Error eliminando calendario " + calendarId + ": " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Elimina permanentemente un calendario personalizado de la base de datos
     * CUIDADO: Esta operación no se puede deshacer
     */
    public static boolean deleteCalendarPermanently(String calendarId) {
        // Verificar que no sea un calendario predeterminado
        if (calendarId.startsWith("CAL000000")) {
            System.err.println("No se pueden eliminar calendarios predeterminados");
            return false;
        }

        // Primero eliminar todos los eventos asociados
        String deleteEventsSQL = """
        DELETE FROM EVENTS 
        WHERE CALENDAR_ID = ?
    """;

        // Luego eliminar el calendario
        String deleteCalendarSQL = """
        DELETE FROM CALENDARS 
        WHERE CALENDAR_ID = ? 
        AND NAME NOT IN ('Mis Clases', 'Tareas y Proyectos', 'Personal', 'Exámenes')
    """;

        try (Connection conn = getConnection()) {
            // Desactivar auto-commit para usar transacción
            conn.setAutoCommit(false);

            try {
                // Eliminar eventos primero
                try (PreparedStatement eventsStmt = conn.prepareStatement(deleteEventsSQL)) {
                    eventsStmt.setString(1, calendarId);
                    int eventsDeleted = eventsStmt.executeUpdate();
                    System.out.println("Eliminados " + eventsDeleted + " eventos del calendario " + calendarId);
                }

                // Eliminar calendario
                try (PreparedStatement calendarStmt = conn.prepareStatement(deleteCalendarSQL)) {
                    calendarStmt.setString(1, calendarId);
                    int calendarsDeleted = calendarStmt.executeUpdate();

                    if (calendarsDeleted > 0) {
                        conn.commit(); // Confirmar transacción
                        System.out.println("Calendario " + calendarId + " eliminado permanentemente");
                        return true;
                    } else {
                        conn.rollback(); // Revertir cambios
                        System.err.println("No se pudo eliminar el calendario " + calendarId);
                        return false;
                    }
                }

            } catch (SQLException e) {
                conn.rollback(); // Revertir en caso de error
                throw e;
            } finally {
                conn.setAutoCommit(true); // Restaurar auto-commit
            }

        } catch (SQLException e) {
            System.err.println("Error eliminando permanentemente el calendario " + calendarId + ": " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Reactiva un calendario que fue marcado como inactivo
     */
    public static boolean reactivateCalendar(String calendarId) {
        String sql = """
        UPDATE CALENDARS 
        SET ACTIVE = 'Y', MODIFIED_DATE = CURRENT_TIMESTAMP 
        WHERE CALENDAR_ID = ? 
        AND ACTIVE = 'N'
    """;

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, calendarId);

            int rowsAffected = stmt.executeUpdate();

            if (rowsAffected > 0) {
                System.out.println("Calendario " + calendarId + " reactivado");
                return true;
            } else {
                System.err.println("No se pudo reactivar el calendario " + calendarId);
                return false;
            }

        } catch (SQLException e) {
            System.err.println("Error reactivando calendario " + calendarId + ": " + e.getMessage());
            e.printStackTrace();
            return false;
        }
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