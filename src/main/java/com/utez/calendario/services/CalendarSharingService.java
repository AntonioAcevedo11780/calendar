package com.utez.calendario.services;

import com.utez.calendario.models.Calendar;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class CalendarSharingService {

    private static CalendarSharingService instance;

    public static CalendarSharingService getInstance() {

        if (instance == null) instance = new CalendarSharingService();
        return instance;

    }

    public void shareCalendar(String calendarId, String recipientEmail) throws SQLException {
        // Validar parámetros
        if (calendarId == null || calendarId.trim().isEmpty()) {
            throw new IllegalArgumentException("El ID del calendario no puede estar vacío");
        }
        if (recipientEmail == null || recipientEmail.trim().isEmpty()) {
            throw new IllegalArgumentException("El email del destinatario no puede estar vacío");
        }

        // Verificar que el calendario existe y está activo
        if (!calendarExists(calendarId)) {
            throw new RuntimeException("El calendario no existe o está inactivo");
        }

        // Obtener ID del usuario receptor por email
        String recipientId = getUserIdByEmail(recipientEmail);
        if (recipientId == null) {
            throw new RuntimeException("Usuario no encontrado: " + recipientEmail);
        }

        // Verificar que el usuario no sea el propietario
        if (isCalendarOwner(calendarId, recipientId)) {
            throw new RuntimeException("No puedes compartir un calendario contigo mismo");
        }

        // Verificar si ya tiene permiso
        if (permissionExists(calendarId, recipientId)) {
            throw new RuntimeException("El usuario ya tiene acceso a este calendario");
        }

        // Insertar nuevo permiso
        insertCalendarPermission(calendarId, recipientId);
    }

    // Obtener calendarios compartidos para un usuario
    public List<Calendar> getSharedCalendarsForUser(String userId) throws SQLException {
        System.out.println("\n🔍 [CalendarSharingService] getSharedCalendarsForUser");
        System.out.println("   👤 Usuario ID: " + userId);

        if (userId == null || userId.trim().isEmpty()) {
            throw new IllegalArgumentException("El ID del usuario no puede estar vacío");
        }

        List<Calendar> sharedCalendars = new ArrayList<>();

        String sql = """
        SELECT c.CALENDAR_ID, c.NAME, c.DESCRIPTION, c.COLOR, c.OWNER_ID, 
               c.ACTIVE, c.CREATED_DATE, c.MODIFIED_DATE, c.IS_SHARED,
               cp.PERMISSION_ID, cp.PERMISSION_TYPE, cp.SHARED_DATE
        FROM CALENDARS c
        JOIN CALENDAR_PERMISSIONS cp ON c.CALENDAR_ID = cp.CALENDAR_ID
        WHERE cp.USER_ID = ? AND cp.ACTIVE = 'Y' AND c.ACTIVE = 'Y'
        ORDER BY c.NAME
    """;

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, userId);
            System.out.println("   📋 Ejecutando consulta de calendarios compartidos...");

            ResultSet rs = stmt.executeQuery();
            int count = 0;

            while (rs.next()) {
                count++;
                Calendar calendar = new Calendar(
                        rs.getString("CALENDAR_ID"),
                        rs.getString("OWNER_ID"),
                        rs.getString("NAME"),
                        rs.getString("DESCRIPTION"),
                        rs.getString("COLOR")
                );
                calendar.setActive(rs.getString("ACTIVE").charAt(0));
                calendar.setCreatedDate(rs.getTimestamp("CREATED_DATE").toLocalDateTime());
                calendar.setModifiedDate(rs.getTimestamp("MODIFIED_DATE") != null ?
                        rs.getTimestamp("MODIFIED_DATE").toLocalDateTime() : null);

                // Usar IS_SHARED si existe
                try {
                    calendar.setShared(rs.getString("IS_SHARED").charAt(0) == 'Y');
                } catch (SQLException e) {
                    // Si no existe la columna IS_SHARED, marcar como compartido manualmente
                    calendar.setShared(true);
                }

                sharedCalendars.add(calendar);

                System.out.println("   📋 Calendario compartido encontrado:");
                System.out.println("      - Nombre: " + calendar.getName());
                System.out.println("      - ID: " + calendar.getCalendarId());
                System.out.println("      - Propietario: " + rs.getString("OWNER_ID"));
                System.out.println("      - Permiso: " + rs.getString("PERMISSION_TYPE"));
                System.out.println("      - Fecha compartido: " + rs.getTimestamp("SHARED_DATE"));
            }

            System.out.println("   ✅ Total calendarios compartidos: " + count);
        } catch (SQLException e) {
            System.err.println("   ❌ Error SQL: " + e.getMessage());
            System.err.println("   SQL State: " + e.getSQLState());
            System.err.println("   Error Code: " + e.getErrorCode());
            throw e;
        }

        return sharedCalendars;
    }

    // Revocar acceso a un calendario compartido
    public void revokeCalendarAccess(String calendarId, String userId) throws SQLException {
        if (calendarId == null || userId == null) {
            throw new IllegalArgumentException("Los parámetros no pueden ser nulos");
        }

        String sql = """
            UPDATE CALENDAR_PERMISSIONS 
            SET ACTIVE = 'N', MODIFIED_DATE = SYSTIMESTAMP 
            WHERE CALENDAR_ID = ? AND USER_ID = ? AND ACTIVE = 'Y'
        """;

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, calendarId);
            stmt.setString(2, userId);
            int rowsAffected = stmt.executeUpdate();

            if (rowsAffected == 0) {
                throw new RuntimeException("No se encontró el permiso a revocar");
            }
        }
    }

    // Obtener usuarios con acceso a un calendario
    public List<String> getUsersWithAccess(String calendarId) throws SQLException {
        List<String> users = new ArrayList<>();
        String sql = """
            SELECT u.EMAIL 
            FROM USERS u
            JOIN CALENDAR_PERMISSIONS cp ON u.USER_ID = cp.USER_ID
            WHERE cp.CALENDAR_ID = ? AND cp.ACTIVE = 'Y'
            ORDER BY u.EMAIL
        """;

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, calendarId);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                users.add(rs.getString("EMAIL"));
            }
        }
        return users;
    }

    // Métodos auxiliares
    private String getUserIdByEmail(String email) throws SQLException {
        String sql = "SELECT USER_ID FROM USERS WHERE EMAIL = ? AND ACTIVE = 'Y'";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, email.toLowerCase()); // Normalizar email
            ResultSet rs = stmt.executeQuery();
            return rs.next() ? rs.getString("USER_ID") : null;
        }
    }

    private boolean calendarExists(String calendarId) throws SQLException {
        String sql = "SELECT CALENDAR_ID, NAME, ACTIVE FROM CALENDARS WHERE CALENDAR_ID = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, calendarId);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                String name = rs.getString("NAME");
                String active = rs.getString("ACTIVE");

                System.out.println("=== DEBUG CALENDAR EXISTS ===");
                System.out.println("Calendar ID: " + calendarId);
                System.out.println("Calendar Name: " + name);
                System.out.println("Calendar Active: '" + active + "'");
                System.out.println("Active equals 'Y': " + "Y".equals(active));
                System.out.println("Active equals 'y': " + "y".equals(active));
                System.out.println("Active char at 0: '" + (active != null ? active.charAt(0) : "NULL") + "'");
                System.out.println("=============================");

                // Verificar diferentes variantes del campo ACTIVE
                return active != null && (
                        "Y".equals(active.trim()) ||
                                "y".equals(active.trim()) ||
                                "1".equals(active.trim()) ||
                                "true".equalsIgnoreCase(active.trim())
                );
            } else {
                System.out.println("=== DEBUG CALENDAR NOT FOUND ===");
                System.out.println("No se encontró calendario con ID: " + calendarId);
                System.out.println("================================");
                return false;
            }
        }
    }

    private boolean isCalendarOwner(String calendarId, String userId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM CALENDARS WHERE CALENDAR_ID = ? AND OWNER_ID = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, calendarId);
            stmt.setString(2, userId);
            ResultSet rs = stmt.executeQuery();
            return rs.next() && rs.getInt(1) > 0;
        }
    }

    private boolean permissionExists(String calendarId, String userId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM CALENDAR_PERMISSIONS " +
                "WHERE CALENDAR_ID = ? AND USER_ID = ? AND ACTIVE = 'Y'";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, calendarId);
            stmt.setString(2, userId);
            ResultSet rs = stmt.executeQuery();
            return rs.next() && rs.getInt(1) > 0;
        }
    }

    private void insertCalendarPermission(String calendarId, String userId) throws SQLException {
        String permissionId = generatePermissionId();
        String sql = """
            INSERT INTO CALENDAR_PERMISSIONS (
                PERMISSION_ID, CALENDAR_ID, USER_ID, 
                PERMISSION_TYPE, SHARED_DATE, ACTIVE
            ) VALUES (?, ?, ?, 'VIEW', SYSTIMESTAMP, 'Y')
        """;

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, permissionId);
            stmt.setString(2, calendarId);
            stmt.setString(3, userId);
            int rowsAffected = stmt.executeUpdate();

            if (rowsAffected == 0) {
                throw new SQLException("No se pudo crear el permiso");
            }
        }
    }

    private String generatePermissionId() {
        return "PERM" + System.currentTimeMillis() + (int)(Math.random() * 1000);
    }

    private static Connection getConnection() throws SQLException {
        return com.utez.calendario.config.DatabaseConfig.getConnection();
    }
}