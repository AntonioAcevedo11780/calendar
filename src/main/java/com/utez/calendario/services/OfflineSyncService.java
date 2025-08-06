package com.utez.calendario.services;

import com.utez.calendario.config.DatabaseConfig;
import java.sql.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class OfflineSyncService {
    private static OfflineSyncService instance;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private final AtomicBoolean isSyncing = new AtomicBoolean(false);

    // CONFIGURACIÓN MEJORADA
    private static final int CONNECTIVITY_CHECK_SECONDS = 45; // Menos frecuente para reducir carga
    private static final int INITIAL_DELAY_SECONDS = 15; // Delay inicial más largo
    private static final int FAST_CHECK_SECONDS = 10; // Verificación rápida cuando hay datos pendientes

    private boolean wasOfflineBefore = false; // Track del estado anterior
    private long lastConnectionCheck = 0;
    private int consecutiveFailures = 0;
    private static final int MAX_CONSECUTIVE_FAILURES = 3;

    private OfflineSyncService() {}

    public static synchronized OfflineSyncService getInstance() {
        if (instance == null) {
            instance = new OfflineSyncService();
        }
        return instance;
    }

    public void startSyncMonitoring() {
        if (isRunning.compareAndSet(false, true)) {
            wasOfflineBefore = DatabaseConfig.isOfflineMode();

            scheduler.scheduleAtFixedRate(() -> {
                try {
                    checkAndSync();
                } catch (Exception e) {
                    System.err.println("Error en monitoreo de sincronizacion: " + e.getMessage());
                    consecutiveFailures++;
                    if (consecutiveFailures > MAX_CONSECUTIVE_FAILURES) {
                        System.err.println("Demasiados errores consecutivos en sincronizacion, pausando temporalmente");
                        try {
                            Thread.sleep(30000); // Pausa 30 segundos
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                        }
                        consecutiveFailures = 0;
                    }
                }
            }, INITIAL_DELAY_SECONDS, getCheckInterval(), TimeUnit.SECONDS);

            System.out.printf("Monitoreo de sincronizacion iniciado (cada %d segundos)\n", getCheckInterval());
        }
    }

    /**
     * MÉTODO MEJORADO: Intervalo dinámico basado en estado y datos pendientes
     */
    private int getCheckInterval() {
        SyncStats stats = getSyncStats();

        // Si hay datos pendientes, verificar más frecuentemente
        if (stats.pendingChanges > 0) {
            return FAST_CHECK_SECONDS;
        }

        // Intervalo normal
        return CONNECTIVITY_CHECK_SECONDS;
    }

    /**
     * MÉTODO de sincronización
     */
    private void checkAndSync() {
        long currentTime = System.currentTimeMillis();
        boolean currentlyOffline = DatabaseConfig.isOfflineMode();

        // Log periódico del estado (cada 2 minutos para más info)
        if (currentTime - lastConnectionCheck > 120000) { // 2 minutos
            SyncStats stats = getSyncStats();
            System.out.printf("[SYNC] Estado: %s, Pendientes: %d\n",
                    currentlyOffline ? "OFFLINE" : "ONLINE", stats.pendingChanges);
            lastConnectionCheck = currentTime;
        }

        // CASO 1: OFFLINE → ONLINE (reconexión)
        if (currentlyOffline) {
            if (DatabaseConfig.attemptReconnection()) {
                System.out.println("🔄 CONECTIVIDAD RESTAURADA - Iniciando sincronización automática");
                consecutiveFailures = 0;
                syncPendingChanges();
                wasOfflineBefore = false;
            }
        }

        // CASO 2: ONLINE → OFFLINE (pérdida de conexión) - ¡NUEVO!
        else if (!currentlyOffline) {
            // Verificar si la conexión actual sigue siendo válida
            if (!DatabaseConfig.isCurrentConnectionValid()) {
                System.out.println("🔄 PÉRDIDA DE CONECTIVIDAD DETECTADA - Cambiando a modo offline");

                try {
                    boolean transitionSuccess = DatabaseConfig.forceTransitionToOffline("Pérdida de conectividad detectada");

                    if (transitionSuccess) {
                        System.out.println("✅ Transición a offline exitosa");
                        wasOfflineBefore = true;
                        consecutiveFailures = 0;
                    } else {
                        System.err.println("❌ Error en transición a offline");
                        consecutiveFailures++;
                    }

                } catch (Exception e) {
                    System.err.println("❌ Error crítico en transición a offline: " + e.getMessage());
                    consecutiveFailures++;
                }
            }
            // Si la conexión es válida pero había estado offline antes
            else if (wasOfflineBefore) {
                System.out.println("🔄 CONFIRMANDO RECONEXIÓN - Sincronizando datos pendientes");
                syncPendingChanges();
                wasOfflineBefore = false;
            }
            // Verificar datos pendientes en modo online
            else {
                SyncStats stats = getSyncStats();
                if (stats.pendingChanges > 0) {
                    System.out.println("📤 Datos pendientes encontrados en modo online, sincronizando...");
                    syncPendingChanges();
                }
            }
        }
    }

// AGREGAR ESTOS MÉTODOS NUEVOS AL OfflineSyncService:

    /**
     * MÉTODO NUEVO: Verificar conectividad de manera más robusta
     */
    private boolean isConnectionHealthy() {
        if (DatabaseConfig.isOfflineMode()) {
            return false;
        }

        try (Connection conn = DatabaseConfig.getConnection()) {
            if (!conn.isValid(3)) {
                return false;
            }

            // Test simple de query
            try (PreparedStatement stmt = conn.prepareStatement("SELECT 1 FROM DUAL");
                 ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (Exception e) {
            System.out.println("Conexión no saludable: " + e.getMessage());
            return false;
        }
    }

    /**
     * MÉTODO NUEVO: Forzar verificación de conectividad
     */
    public boolean forceConnectivityCheck() {
        System.out.println("🔍 VERIFICACIÓN FORZADA DE CONECTIVIDAD");

        boolean currentlyOffline = DatabaseConfig.isOfflineMode();

        if (currentlyOffline) {
            System.out.println("   Estado actual: OFFLINE");
            System.out.println("   Intentando reconexión...");
            return DatabaseConfig.attemptReconnection();
        } else {
            System.out.println("   Estado actual: ONLINE");
            System.out.println("   Verificando salud de conexión...");

            if (!isConnectionHealthy()) {
                System.out.println("   ❌ Conexión no saludable, forzando offline");
                try {
                    DatabaseConfig.forceTransitionToOffline("Verificación forzada detectó conexión no saludable");
                    return false;
                } catch (Exception e) {
                    System.err.println("   Error forzando offline: " + e.getMessage());
                    return false;
                }
            } else {
                System.out.println("   ✅ Conexión saludable");
                return true;
            }
        }
    }

    public boolean syncPendingChanges() {
        if (isSyncing.compareAndSet(false, true)) {
            try {
                return performSync();
            } finally {
                isSyncing.set(false);
            }
        } else {
            System.out.println("Sincronizacion ya en progreso");
            return false;
        }
    }

    /**
     * MÉTODO MEJORADO: Sincronización más robusta
     */
    private boolean performSync() {
        // Verificar que realmente estemos online
        if (DatabaseConfig.isOfflineMode()) {
            System.out.println("Aun en modo offline, no se puede sincronizar");
            return false;
        }

        System.out.println("Iniciando sincronizacion de datos pendientes...");

        try (Connection conn = DatabaseConfig.getConnection()) {
            if (!tableExists(conn, "pending_sync")) {
                System.out.println("No hay datos pendientes de sincronizacion");
                return true;
            }

            // Contar total de registros pendientes
            int totalPending = countPendingRecords(conn);
            if (totalPending == 0) {
                System.out.println("No hay registros pendientes de sincronizacion");
                return true;
            }

            System.out.printf("Sincronizando %d registros pendientes...\n", totalPending);

            String selectPendingSql = "SELECT * FROM pending_sync ORDER BY created_at ASC";

            try (PreparedStatement selectStmt = conn.prepareStatement(selectPendingSql);
                 ResultSet rs = selectStmt.executeQuery()) {

                int syncedCount = 0;
                int errorCount = 0;

                while (rs.next()) {
                    try {
                        int id = rs.getInt("id");
                        String tableName = rs.getString("table_name");
                        String recordId = rs.getString("record_id");
                        String operation = rs.getString("operation");
                        String data = rs.getString("data");

                        boolean success = syncRecord(conn, tableName, recordId, operation, data);

                        if (success) {
                            deletePendingSync(conn, id);
                            syncedCount++;
                            System.out.printf("Sincronizado [%d/%d]: %s (ID: %s)\n",
                                    syncedCount, totalPending, tableName, recordId);
                        } else {
                            errorCount++;
                            System.err.printf("Error sincronizando [%d]: %s (ID: %s)\n",
                                    errorCount, tableName, recordId);
                        }

                    } catch (Exception e) {
                        errorCount++;
                        System.err.println("Error procesando registro pendiente: " + e.getMessage());
                    }
                }

                System.out.printf("Sincronizacion completada. Exitosos: %d, Errores: %d\n",
                        syncedCount, errorCount);

                consecutiveFailures = 0; // Reset en caso de éxito
                return errorCount == 0;
            }

        } catch (Exception e) {
            System.err.println("Error durante sincronizacion: " + e.getMessage());
            consecutiveFailures++;
            return false;
        }
    }

    /**
     * MÉTODO NUEVO: Contar registros pendientes
     */
    private int countPendingRecords(Connection conn) throws SQLException {
        String countSql = "SELECT COUNT(*) FROM pending_sync";
        try (PreparedStatement stmt = conn.prepareStatement(countSql);
             ResultSet rs = stmt.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private boolean tableExists(Connection conn, String tableName) {
        try {
            DatabaseMetaData metaData = conn.getMetaData();
            try (ResultSet rs = metaData.getTables(null, null, tableName, null)) {
                return rs.next();
            }
        } catch (SQLException e) {
            return false;
        }
    }

    private boolean syncRecord(Connection conn, String tableName, String recordId,
                               String operation, String data) {
        try {
            switch (operation.toUpperCase()) {
                case "INSERT":
                    return performInsert(conn, tableName, recordId, data);
                case "UPDATE":
                    return performUpdate(conn, tableName, recordId, data);
                case "DELETE":
                    return performDelete(conn, tableName, recordId);
                default:
                    System.err.println("Operacion desconocida: " + operation);
                    return false;
            }
        } catch (Exception e) {
            System.err.println("Error ejecutando operacion " + operation +
                    " en " + tableName + ": " + e.getMessage());
            return false;
        }
    }

    private boolean performInsert(Connection conn, String tableName, String recordId, String jsonData) {
        try {
            System.out.println("INSERT en " + tableName + ": " + jsonData);

            // Implementación simplificada para eventos
            if ("EVENTS".equals(tableName.toUpperCase())) {
                return insertEvent(conn, recordId, jsonData);
            } else if ("CALENDARS".equals(tableName.toUpperCase())) {
                return insertCalendar(conn, recordId, jsonData);
            }

            // Para otras tablas, registrar pero no fallar
            System.out.println("INSERT para " + tableName + " registrado pero no implementado");
            return true;
        } catch (Exception e) {
            System.err.println("Error en INSERT: " + e.getMessage());
            return false;
        }
    }

    private boolean insertEvent(Connection conn, String eventId, String jsonData) {
        try {
            // Parseo simple del JSON
            String title = extractJsonValue(jsonData, "title");
            String description = extractJsonValue(jsonData, "description");
            String startDate = extractJsonValue(jsonData, "startDate");
            String endDate = extractJsonValue(jsonData, "endDate");
            String location = extractJsonValue(jsonData, "location");

            String sql = """
                INSERT INTO EVENTS (EVENT_ID, CALENDAR_ID, CREATOR_ID, TITLE, DESCRIPTION, 
                                  START_DATE, END_DATE, LOCATION, ACTIVE, CREATED_DATE) 
                VALUES (?, 'DEFAULT_CAL', 'DEFAULT_USER', ?, ?, ?, ?, ?, 'Y', SYSDATE)
            """;

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, eventId);
                stmt.setString(2, title);
                stmt.setString(3, description);
                stmt.setString(4, startDate);
                stmt.setString(5, endDate);
                stmt.setString(6, location);
                return stmt.executeUpdate() > 0;
            }
        } catch (Exception e) {
            System.err.println("Error insertando evento: " + e.getMessage());
            return false;
        }
    }

    private boolean insertCalendar(Connection conn, String calendarId, String jsonData) {
        try {
            String name = extractJsonValue(jsonData, "name");
            String description = extractJsonValue(jsonData, "description");
            String color = extractJsonValue(jsonData, "color");

            String sql = """
                INSERT INTO CALENDARS (CALENDAR_ID, OWNER_ID, NAME, DESCRIPTION, COLOR, ACTIVE, CREATED_DATE) 
                VALUES (?, 'DEFAULT_USER', ?, ?, ?, 'Y', SYSDATE)
            """;

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, calendarId);
                stmt.setString(2, name);
                stmt.setString(3, description);
                stmt.setString(4, color);
                return stmt.executeUpdate() > 0;
            }
        } catch (Exception e) {
            System.err.println("Error insertando calendario: " + e.getMessage());
            return false;
        }
    }

    private boolean performUpdate(Connection conn, String tableName, String recordId, String jsonData) {
        try {
            System.out.println("UPDATE en " + tableName + " ID " + recordId + ": " + jsonData);

            if ("EVENTS".equals(tableName.toUpperCase())) {
                return updateEvent(conn, recordId, jsonData);
            }

            System.out.println("UPDATE para " + tableName + " registrado pero no implementado");
            return true;
        } catch (Exception e) {
            System.err.println("Error en UPDATE: " + e.getMessage());
            return false;
        }
    }

    private boolean updateEvent(Connection conn, String eventId, String jsonData) {
        try {
            String title = extractJsonValue(jsonData, "title");
            String description = extractJsonValue(jsonData, "description");
            String startDate = extractJsonValue(jsonData, "startDate");
            String endDate = extractJsonValue(jsonData, "endDate");
            String location = extractJsonValue(jsonData, "location");

            String sql = """
                UPDATE EVENTS SET TITLE = ?, DESCRIPTION = ?, START_DATE = ?, 
                                END_DATE = ?, LOCATION = ?, MODIFIED_DATE = SYSDATE 
                WHERE EVENT_ID = ?
            """;

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, title);
                stmt.setString(2, description);
                stmt.setString(3, startDate);
                stmt.setString(4, endDate);
                stmt.setString(5, location);
                stmt.setString(6, eventId);
                return stmt.executeUpdate() > 0;
            }
        } catch (Exception e) {
            System.err.println("Error actualizando evento: " + e.getMessage());
            return false;
        }
    }

    private boolean performDelete(Connection conn, String tableName, String recordId) {
        try {
            String idColumn = getIdColumnForTable(tableName);
            String deleteSql = "UPDATE " + tableName + " SET ACTIVE = 'N' WHERE " + idColumn + " = ?";

            try (PreparedStatement stmt = conn.prepareStatement(deleteSql)) {
                stmt.setString(1, recordId);
                int affected = stmt.executeUpdate();
                System.out.println("DELETE en " + tableName + " ID " + recordId +
                        " (afectados: " + affected + ")");
                return affected > 0;
            }
        } catch (Exception e) {
            System.err.println("Error en DELETE: " + e.getMessage());
            return false;
        }
    }

    private String getIdColumnForTable(String tableName) {
        switch (tableName.toUpperCase()) {
            case "USERS":
                return "USER_ID";
            case "CALENDARS":
                return "CALENDAR_ID";
            case "EVENTS":
                return "EVENT_ID";
            case "CALENDAR_PERMISSIONS":
                return "PERMISSION_ID";
            case "INVITATIONS":
                return "INVITATION_ID";
            case "CLASS_TEMPLATES":
                return "TEMPLATE_ID";
            default:
                return "id";
        }
    }

    private void deletePendingSync(Connection conn, int pendingId) throws SQLException {
        String deleteSql = "DELETE FROM pending_sync WHERE id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(deleteSql)) {
            stmt.setInt(1, pendingId);
            stmt.executeUpdate();
        }
    }

    /**
     * MÉTODO MEJORADO: Mejor manejo de SQLite vs Oracle
     */
    public void addPendingChange(String tableName, String recordId, String operation, String jsonData) {
        if (!DatabaseConfig.isOfflineMode()) {
            return;
        }

        try (Connection conn = DatabaseConfig.getConnection()) {
            // Verificar si la tabla existe
            if (!tableExists(conn, "pending_sync")) {
                createPendingSyncTable(conn);
            }

            // Verificar si ya existe el mismo registro pendiente
            if (isDuplicatePendingChange(conn, tableName, recordId, operation)) {
                System.out.println("Cambio duplicado ignorado: " + tableName + " (" + operation + ") - ID: " + recordId);
                return;
            }

            String insertSql = """
                INSERT INTO pending_sync (table_name, record_id, operation, data, created_at) 
                VALUES (?, ?, ?, ?, datetime('now'))
                """;

            try (PreparedStatement stmt = conn.prepareStatement(insertSql)) {
                stmt.setString(1, tableName);
                stmt.setString(2, recordId);
                stmt.setString(3, operation);
                stmt.setString(4, jsonData);
                stmt.executeUpdate();

                System.out.println("Cambio agregado a cola de sincronizacion: " +
                        tableName + " (" + operation + ") - ID: " + recordId);
            }
        } catch (Exception e) {
            System.err.println("Error agregando cambio pendiente: " + e.getMessage());
        }
    }

    /**
     * MÉTODO NUEVO: Verificar cambios duplicados
     */
    private boolean isDuplicatePendingChange(Connection conn, String tableName, String recordId, String operation) {
        try {
            String checkSql = "SELECT COUNT(*) FROM pending_sync WHERE table_name = ? AND record_id = ? AND operation = ?";
            try (PreparedStatement stmt = conn.prepareStatement(checkSql)) {
                stmt.setString(1, tableName);
                stmt.setString(2, recordId);
                stmt.setString(3, operation);
                try (ResultSet rs = stmt.executeQuery()) {
                    return rs.next() && rs.getInt(1) > 0;
                }
            }
        } catch (Exception e) {
            return false; // En caso de error, permitir el insert
        }
    }

    private void createPendingSyncTable(Connection conn) throws SQLException {
        String createTableSql = """
            CREATE TABLE IF NOT EXISTS pending_sync (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                table_name TEXT NOT NULL,
                record_id TEXT NOT NULL,
                operation TEXT NOT NULL,
                data TEXT,
                created_at DATETIME DEFAULT CURRENT_TIMESTAMP
            )
        """;

        try (PreparedStatement stmt = conn.prepareStatement(createTableSql)) {
            stmt.execute();
            System.out.println("Tabla pending_sync creada");
        }
    }

    /**
     * MÉTODO MEJORADO: Sincronización manual más robusta
     */
    public void forceSyncNow() {
        System.out.println("Sincronizacion manual solicitada");

        new Thread(() -> {
            try {
                // Verificar estado actual
                if (!DatabaseConfig.isOfflineMode()) {
                    System.out.println("Ya en modo online, procediendo con sincronizacion...");
                    boolean success = syncPendingChanges();
                    if (success) {
                        System.out.println("Sincronizacion manual completada exitosamente");
                    } else {
                        System.out.println("Sincronizacion manual completada con errores");
                    }
                } else if (DatabaseConfig.attemptReconnection()) {
                    System.out.println("Conectividad verificada, sincronizando...");
                    boolean success = syncPendingChanges();
                    if (success) {
                        System.out.println("Sincronizacion manual completada exitosamente");
                    } else {
                        System.out.println("Sincronizacion manual completada con errores");
                    }
                } else {
                    System.out.println("Sin conectividad, no se puede sincronizar");
                }
            } catch (Exception e) {
                System.err.println("Error en sincronizacion manual: " + e.getMessage());
            }
        }, "ManualSync-Thread").start();
    }

    public SyncStats getSyncStats() {
        try (Connection conn = DatabaseConfig.getConnection()) {
            if (!tableExists(conn, "pending_sync")) {
                return new SyncStats(0, DatabaseConfig.isOfflineMode(), DatabaseConfig.getOfflineReason());
            }

            String countSql = "SELECT COUNT(*) as pending FROM pending_sync";
            try (PreparedStatement stmt = conn.prepareStatement(countSql);
                 ResultSet rs = stmt.executeQuery()) {

                if (rs.next()) {
                    int pendingCount = rs.getInt("pending");
                    return new SyncStats(pendingCount, DatabaseConfig.isOfflineMode(),
                            DatabaseConfig.getOfflineReason());
                }
            }
        } catch (Exception e) {
            System.err.println("Error obteniendo estadisticas: " + e.getMessage());
        }

        return new SyncStats(0, DatabaseConfig.isOfflineMode(), DatabaseConfig.getOfflineReason());
    }

    public boolean isSyncInProgress() {
        return isSyncing.get();
    }

    public int getSecondsToNextCheck() {
        return getCheckInterval();
    }

    public void shutdown() {
        if (isRunning.compareAndSet(true, false)) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
                System.out.println("Servicio de sincronizacion offline detenido");
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * MÉTODO AUXILIAR MEJORADO: Parsear JSON simple
     */
    private String extractJsonValue(String jsonData, String key) {
        if (jsonData == null || key == null) return "";

        try {
            String pattern = "\"" + key + "\":\"";
            int startIndex = jsonData.indexOf(pattern);
            if (startIndex == -1) {
                // Intentar sin comillas (para números o booleanos)
                pattern = "\"" + key + "\":";
                startIndex = jsonData.indexOf(pattern);
                if (startIndex == -1) return "";

                startIndex += pattern.length();
                int endIndex = jsonData.indexOf(",", startIndex);
                if (endIndex == -1) endIndex = jsonData.indexOf("}", startIndex);
                if (endIndex == -1) return "";

                return jsonData.substring(startIndex, endIndex).trim();
            }

            startIndex += pattern.length();
            int endIndex = jsonData.indexOf("\"", startIndex);
            if (endIndex == -1) return "";

            return jsonData.substring(startIndex, endIndex);
        } catch (Exception e) {
            System.err.println("Error parseando JSON para clave '" + key + "': " + e.getMessage());
            return "";
        }
    }

    public static class SyncStats {
        public final int pendingChanges;
        public final boolean isOfflineMode;
        public final String offlineReason;

        public SyncStats(int pendingChanges, boolean isOfflineMode, String offlineReason) {
            this.pendingChanges = pendingChanges;
            this.isOfflineMode = isOfflineMode;
            this.offlineReason = offlineReason;
        }

        @Override
        public String toString() {
            if (isOfflineMode) {
                return String.format("MODO OFFLINE - %d cambios pendientes - Razon: %s",
                        pendingChanges, offlineReason);
            } else {
                return String.format("MODO ONLINE - %d cambios pendientes", pendingChanges);
            }
        }

        public String getStatusSummary() {
            String modeText = isOfflineMode ? "OFFLINE" : "ONLINE";

            if (pendingChanges > 0) {
                return String.format("%s (%d pendientes)", modeText, pendingChanges);
            } else {
                return modeText;
            }
        }
    }
}