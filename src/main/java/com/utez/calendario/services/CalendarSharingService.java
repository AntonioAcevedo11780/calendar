package com.utez.calendario.services;

import com.utez.calendario.models.Calendar;
import com.utez.calendario.utils.TimeService;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class CalendarSharingService {

    // Singleton instance
    private static CalendarSharingService instance;

    // ✅ Pool de threads optimizado para BD
    private static final ExecutorService DATABASE_EXECUTOR =
            ForkJoinPool.commonPool();

    // ✅ Connection pooling simulado con ThreadLocal para evitar conflictos
    private static final ThreadLocal<Connection> CONNECTION_CACHE = new ThreadLocal<>();

    // ✅ Cache de validaciones para evitar consultas repetidas
    private final ConcurrentHashMap<String, Boolean> calendarExistsCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> emailToUserIdCache = new ConcurrentHashMap<>();

    // ✅ Constructor privado para patrón Singleton
    private CalendarSharingService() {
        System.out.println("🚀 CalendarSharingService inicializado correctamente");
    }

    public static CalendarSharingService getInstance() {
        if (instance == null) {
            synchronized (CalendarSharingService.class) {
                if (instance == null) {
                    instance = new CalendarSharingService();
                }
            }
        }
        return instance;
    }

    // ============= MÉTODOS SÚPER OPTIMIZADOS =============

    /**
     * 🚀 Comparte calendario con múltiples usuarios en PARALELO MÁXIMO
     */
    public CompletableFuture<ShareBatchResult> shareCalendarWithMultipleUsersOptimized(
            String calendarId, List<String> recipientEmails) {

        if (recipientEmails.isEmpty()) {
            return CompletableFuture.completedFuture(new ShareBatchResult());
        }

        System.out.println("🔥 Iniciando compartir ULTRA-RÁPIDO con " + recipientEmails.size() + " usuarios");
        long startTime = System.currentTimeMillis();

        return CompletableFuture.supplyAsync(() -> {
            ShareBatchResult result = new ShareBatchResult();

            try {
                // PASO 1: Pre-validación del calendario UNA SOLA VEZ
                if (!validateCalendarExists(calendarId)) {
                    recipientEmails.forEach(email ->
                            result.addError(email, "El calendario no existe o está inactivo"));
                    return result;
                }

                // PASO 2: Batch de emails a UserIDs EN PARALELO
                Map<String, String> emailToUserIdMap = batchGetUserIdsByEmails(recipientEmails);

                // PASO 3: Filtrar usuarios válidos
                List<String> validEmails = emailToUserIdMap.keySet().stream()
                        .filter(email -> emailToUserIdMap.get(email) != null)
                        .collect(Collectors.toList());

                // PASO 4: Agregar errores para emails inválidos
                recipientEmails.stream()
                        .filter(email -> !validEmails.contains(email))
                        .forEach(email -> result.addError(email, "Usuario no encontrado"));

                if (validEmails.isEmpty()) {
                    return result;
                }

                // PASO 5: Validaciones masivas EN PARALELO
                Map<String, ValidationResult> validationResults =
                        batchValidateUsersForCalendar(calendarId, validEmails, emailToUserIdMap);

                // PASO 6: Filtrar usuarios que pueden recibir el calendario
                List<String> emailsToShare = validationResults.entrySet().stream()
                        .filter(entry -> entry.getValue().canShare)
                        .map(Map.Entry::getKey)
                        .collect(Collectors.toList());

                // PASO 7: Agregar errores de validación
                validationResults.entrySet().stream()
                        .filter(entry -> !entry.getValue().canShare)
                        .forEach(entry -> result.addError(entry.getKey(), entry.getValue().errorMessage));

                // PASO 8: Inserción MASIVA de permisos
                if (!emailsToShare.isEmpty()) {
                    batchInsertCalendarPermissions(calendarId, emailsToShare, emailToUserIdMap);
                    emailsToShare.forEach(result::addSuccess);
                }

                long endTime = System.currentTimeMillis();
                System.out.println("⚡ Proceso completado en " + (endTime - startTime) + "ms");

                return result;

            } catch (Exception e) {
                System.err.println("❌ Error crítico en batch share: " + e.getMessage());
                recipientEmails.forEach(email ->
                        result.addError(email, "Error del sistema: " + e.getMessage()));
                return result;
            }
        }, DATABASE_EXECUTOR);
    }

    /**
     * 🔥 Obtiene calendarios compartidos SÚPER OPTIMIZADO con una sola consulta
     */
    public CompletableFuture<List<Calendar>> getSharedCalendarsForUserOptimized(String userId) {
        return CompletableFuture.supplyAsync(() -> {
            System.out.println("⚡ getSharedCalendarsOptimized para userId: " + userId);
            long startTime = System.currentTimeMillis();

            if (userId == null || userId.trim().isEmpty()) {
                throw new IllegalArgumentException("El ID del usuario no puede estar vacío");
            }

            List<Calendar> calendars = new ArrayList<>();

            // ✅ CONSULTA SÚPER OPTIMIZADA con HINTS de Oracle
            String sql = """
                SELECT /*+ FIRST_ROWS(10) INDEX(c PK_CALENDARS) INDEX(cp PK_CALENDAR_PERMISSIONS) */
                       c.CALENDAR_ID, c.NAME, c.DESCRIPTION, c.COLOR, c.OWNER_ID, 
                       c.ACTIVE, c.CREATED_DATE, c.MODIFIED_DATE, c.IS_SHARED,
                       cp.PERMISSION_TYPE, cp.SHARED_DATE
                FROM CALENDARS c
                JOIN CALENDAR_PERMISSIONS cp ON c.CALENDAR_ID = cp.CALENDAR_ID
                WHERE cp.USER_ID = ? AND cp.ACTIVE = 'Y' AND c.ACTIVE = 'Y'
                ORDER BY c.NAME
            """;

            try (Connection conn = getOptimizedConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setFetchSize(100); // ✅ Optimización de fetch
                stmt.setString(1, userId);

                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        Calendar calendar = createCalendarFromResultSetOptimized(rs);
                        calendars.add(calendar);
                    }
                }

                long endTime = System.currentTimeMillis();
                System.out.println("⚡ Calendarios obtenidos en " + (endTime - startTime) + "ms: " + calendars.size());

                return calendars;

            } catch (SQLException e) {
                System.err.println("❌ Error SQL optimizado: " + e.getMessage());
                throw new CompletionException(e);
            }
        }, DATABASE_EXECUTOR);
    }

    // ============= NUEVOS MÉTODOS AÑADIDOS =============

    /**
     * Revoca el acceso de un usuario a un calendario
     */
    public void revokeCalendarAccess(String calendarId, String userId) throws SQLException {
        // Usar TimeService para la fecha de modificación (si existe, si no usar LocalDateTime.now())
        LocalDateTime now = LocalDateTime.now(); // Cambia por TimeService.getInstance().now() si tienes la clase
        String sql = "UPDATE CALENDAR_PERMISSIONS SET ACTIVE = 'N', MODIFIED_DATE = ? WHERE CALENDAR_ID = ? AND USER_ID = ? AND ACTIVE = 'Y'";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setTimestamp(1, Timestamp.valueOf(now));
            stmt.setString(2, calendarId);
            stmt.setString(3, userId);
            int rowsAffected = stmt.executeUpdate();

            if (rowsAffected == 0) {
                throw new RuntimeException("No se encontró el permiso a revocar");
            }
        }
    }

    /**
     * Comparte un calendario con un solo usuario (método individual)
     */
    public void shareCalendarWithUser(String calendarId, String recipientEmail) throws SQLException {
        // Validar que el calendario existe
        if (!calendarExists(calendarId)) {
            throw new SQLException("El calendario no existe o está inactivo");
        }

        // Obtener el userId del email
        String userId = getUserIdByEmail(recipientEmail);
        if (userId == null) {
            throw new SQLException("Usuario no encontrado: " + recipientEmail);
        }

        // Verificar si ya existe el permiso
        if (permissionExists(calendarId, userId)) {
            throw new SQLException("El usuario ya tiene acceso a este calendario");
        }

        // Verificar que no sea el owner
        if (isOwner(calendarId, userId)) {
            throw new SQLException("No puedes compartir un calendario contigo mismo");
        }

        // Insertar el permiso
        insertCalendarPermission(calendarId, userId);
    }

    // ============= MÉTODOS DE BATCH PROCESSING =============

    /**
     * 🔥 Obtiene UserIDs por emails EN LOTE
     */
    private Map<String, String> batchGetUserIdsByEmails(List<String> emails) {
        Map<String, String> result = new ConcurrentHashMap<>();

        if (emails.isEmpty()) return result;

        // ✅ Consulta IN con placeholders dinámicos
        String placeholders = emails.stream().map(e -> "?").collect(Collectors.joining(","));
        String sql = "SELECT EMAIL, USER_ID FROM USERS WHERE LOWER(EMAIL) IN (" + placeholders + ") AND ACTIVE = 'Y'";

        try (Connection conn = getOptimizedConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            // Set parameters
            for (int i = 0; i < emails.size(); i++) {
                stmt.setString(i + 1, emails.get(i).toLowerCase());
            }

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    result.put(rs.getString("EMAIL").toLowerCase(), rs.getString("USER_ID"));
                }
            }

            // Cache results
            result.forEach(emailToUserIdCache::put);

        } catch (SQLException e) {
            System.err.println("❌ Error en batch getUserIds: " + e.getMessage());
        }

        return result;
    }

    /**
     * 🔥 Valida múltiples usuarios para un calendario EN LOTE
     */
    private Map<String, ValidationResult> batchValidateUsersForCalendar(
            String calendarId, List<String> emails, Map<String, String> emailToUserIdMap) {

        Map<String, ValidationResult> results = new ConcurrentHashMap<>();

        if (emails.isEmpty()) return results;

        List<String> userIds = emails.stream()
                .map(emailToUserIdMap::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        if (userIds.isEmpty()) return results;

        // ✅ Verificar owners y permisos existentes EN LOTE
        String placeholders = userIds.stream().map(e -> "?").collect(Collectors.joining(","));

        // Query para verificar owners
        String ownerSql = "SELECT OWNER_ID FROM CALENDARS WHERE CALENDAR_ID = ? AND OWNER_ID IN (" + placeholders + ")";
        Set<String> ownerIds = new HashSet<>();

        try (Connection conn = getOptimizedConnection();
             PreparedStatement stmt = conn.prepareStatement(ownerSql)) {

            stmt.setString(1, calendarId);
            for (int i = 0; i < userIds.size(); i++) {
                stmt.setString(i + 2, userIds.get(i));
            }

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    ownerIds.add(rs.getString("OWNER_ID"));
                }
            }
        } catch (SQLException e) {
            System.err.println("❌ Error verificando owners: " + e.getMessage());
        }

        // Query para verificar permisos existentes
        String permSql = "SELECT USER_ID FROM CALENDAR_PERMISSIONS WHERE CALENDAR_ID = ? AND USER_ID IN (" + placeholders + ") AND ACTIVE = 'Y'";
        Set<String> existingPermissions = new HashSet<>();

        try (Connection conn = getOptimizedConnection();
             PreparedStatement stmt = conn.prepareStatement(permSql)) {

            stmt.setString(1, calendarId);
            for (int i = 0; i < userIds.size(); i++) {
                stmt.setString(i + 2, userIds.get(i));
            }

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    existingPermissions.add(rs.getString("USER_ID"));
                }
            }
        } catch (SQLException e) {
            System.err.println("❌ Error verificando permisos: " + e.getMessage());
        }

        // ✅ Generar resultados de validación
        for (String email : emails) {
            String userId = emailToUserIdMap.get(email);
            if (userId == null) {
                results.put(email, new ValidationResult(false, "Usuario no encontrado"));
                continue;
            }

            if (ownerIds.contains(userId)) {
                results.put(email, new ValidationResult(false, "No puedes compartir un calendario contigo mismo"));
                continue;
            }

            if (existingPermissions.contains(userId)) {
                results.put(email, new ValidationResult(false, "El usuario ya tiene acceso a este calendario"));
                continue;
            }

            results.put(email, new ValidationResult(true, null));
        }

        return results;
    }

    /**
     * 🔥 Inserta permisos EN LOTE (súper rápido)
     */
    private void batchInsertCalendarPermissions(
            String calendarId, List<String> emails, Map<String, String> emailToUserIdMap) throws SQLException {

        if (emails.isEmpty()) return;

        String sql = """
            INSERT INTO CALENDAR_PERMISSIONS (
                PERMISSION_ID, CALENDAR_ID, USER_ID, 
                PERMISSION_TYPE, SHARED_DATE, ACTIVE
            ) VALUES (?, ?, ?, 'VIEW', SYSTIMESTAMP, 'Y')
        """;

        try (Connection conn = getOptimizedConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            conn.setAutoCommit(false); // ✅ Transacción para mejor performance

            for (String email : emails) {
                String userId = emailToUserIdMap.get(email);
                if (userId != null) {
                    stmt.setString(1, generatePermissionId());
                    stmt.setString(2, calendarId);
                    stmt.setString(3, userId);
                    stmt.addBatch(); // ✅ Batch insert
                }
            }

            int[] results = stmt.executeBatch();
            conn.commit();

            System.out.println("⚡ Insertados " + results.length + " permisos en lote");

        } catch (SQLException e) {
            System.err.println("❌ Error en batch insert: " + e.getMessage());
            throw e;
        }
    }

    // ============= MÉTODOS AUXILIARES =============

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

        // Usar fecha actual
        LocalDateTime now = LocalDateTime.now(); // Cambia por TimeService.getInstance().now() si tienes la clase
        String sql = "INSERT INTO CALENDAR_PERMISSIONS (PERMISSION_ID, CALENDAR_ID, USER_ID, PERMISSION_TYPE, SHARED_DATE, ACTIVE) VALUES (?, ?, ?, 'VIEW', ?, 'Y')";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, permissionId);
            stmt.setString(2, calendarId);
            stmt.setString(3, userId);
            stmt.setTimestamp(4, Timestamp.valueOf(now));
            int rowsAffected = stmt.executeUpdate();

            if (rowsAffected == 0) {
                throw new SQLException("No se pudo insertar el permiso");
            }
        }
    }

    private String getUserIdByEmail(String email) throws SQLException {
        String sql = "SELECT USER_ID FROM USERS WHERE LOWER(EMAIL) = ? AND ACTIVE = 'Y'";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, email.toLowerCase());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("USER_ID");
                }
                return null;
            }
        }
    }

    private boolean isOwner(String calendarId, String userId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM CALENDARS WHERE CALENDAR_ID = ? AND OWNER_ID = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, calendarId);
            stmt.setString(2, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    // ============= MÉTODOS DE OPTIMIZACIÓN =============

    private boolean validateCalendarExists(String calendarId) {
        return calendarExistsCache.computeIfAbsent(calendarId, id -> {
            try {
                return calendarExists(id);
            } catch (SQLException e) {
                return false;
            }
        });
    }

    private Connection getOptimizedConnection() throws SQLException {
        Connection conn = CONNECTION_CACHE.get();
        if (conn == null || conn.isClosed()) {
            conn = com.utez.calendario.config.DatabaseConfig.getConnection();
            CONNECTION_CACHE.set(conn);
        }
        return conn;
    }

    private Calendar createCalendarFromResultSetOptimized(ResultSet rs) throws SQLException {
        Calendar calendar = new Calendar(
                rs.getString("CALENDAR_ID"),
                rs.getString("OWNER_ID"),
                rs.getString("NAME"),
                rs.getString("DESCRIPTION"),
                rs.getString("COLOR")
        );

        calendar.setActive(rs.getString("ACTIVE").charAt(0));
        calendar.setCreatedDate(rs.getTimestamp("CREATED_DATE").toLocalDateTime());

        // ✅ Optimización: solo set si no es null
        java.sql.Timestamp modifiedDate = rs.getTimestamp("MODIFIED_DATE");
        if (modifiedDate != null) {
            calendar.setModifiedDate(modifiedDate.toLocalDateTime());
        }

        try {
            calendar.setShared(rs.getString("IS_SHARED").charAt(0) == 'Y');
        } catch (SQLException e) {
            calendar.setShared(true);
        }

        return calendar;
    }

    // ============= MÉTODOS DE COMPATIBILIDAD (síncronos) =============

    public void shareCalendar(String calendarId, String recipientEmail) throws SQLException {
        try {
            shareCalendarWithMultipleUsersOptimized(calendarId, List.of(recipientEmail))
                    .thenApply(result -> {
                        if (!result.getErrors().isEmpty()) {
                            throw new RuntimeException(result.getErrors().get(recipientEmail));
                        }
                        return null;
                    })
                    .join();
        } catch (CompletionException e) {
            if (e.getCause() instanceof SQLException) {
                throw (SQLException) e.getCause();
            }
            throw new RuntimeException(e.getCause());
        }
    }

    public List<Calendar> getSharedCalendarsForUser(String userId) throws SQLException {
        try {
            return getSharedCalendarsForUserOptimized(userId).join();
        } catch (CompletionException e) {
            if (e.getCause() instanceof SQLException) {
                throw (SQLException) e.getCause();
            }
            throw new RuntimeException(e.getCause());
        }
    }

    // ============= CLASES AUXILIARES =============

    public static class ShareBatchResult {
        private final List<String> successfulEmails = new CopyOnWriteArrayList<>();
        private final ConcurrentHashMap<String, String> errors = new ConcurrentHashMap<>();

        public void addSuccess(String email) { successfulEmails.add(email); }
        public void addError(String email, String error) { errors.put(email, error); }

        public List<String> getSuccessfulEmails() { return new ArrayList<>(successfulEmails); }
        public Map<String, String> getErrors() { return new HashMap<>(errors); }
        public boolean hasErrors() { return !errors.isEmpty(); }
        public int getSuccessCount() { return successfulEmails.size(); }
        public int getErrorCount() { return errors.size(); }
    }

    private static class ValidationResult {
        final boolean canShare;
        final String errorMessage;

        ValidationResult(boolean canShare, String errorMessage) {
            this.canShare = canShare;
            this.errorMessage = errorMessage;
        }
    }

    // ============= MÉTODOS AUXILIARES EXISTENTES =============

    private boolean calendarExists(String calendarId) throws SQLException {
        String sql = "SELECT ACTIVE FROM CALENDARS WHERE CALENDAR_ID = ?";
        try (Connection conn = getOptimizedConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, calendarId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    String active = rs.getString("ACTIVE");
                    return "Y".equalsIgnoreCase(active) || "1".equals(active) || "true".equalsIgnoreCase(active);
                }
                return false;
            }
        }
    }

    private String generatePermissionId() {
        // Usar timestamp actual para el ID
        return "PERM" + System.currentTimeMillis() + (int)(Math.random() * 1000);
    }

    private static Connection getConnection() throws SQLException {
        return com.utez.calendario.config.DatabaseConfig.getConnection();
    }
}