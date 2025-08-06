package com.utez.calendario.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Properties;

public class DatabaseConfig {
    private static final String CONFIG_FILE = "/database.properties";
    private static Properties properties;
    private static volatile HikariDataSource dataSource;
    private static final Object LOCK = new Object();
    private static boolean isOracleConnection = false;
    private static boolean isOfflineMode = false;
    private static String offlineReason = "";
    private static boolean hasBeenOnline = false;

    // CONFIGURACIÓN MEJORADA DE TIMEOUTS
    private static final int CONNECTIVITY_TIMEOUT_MS = 3000; // 3 segundos
    private static final int ORACLE_CONNECTION_TIMEOUT_MS = 10000; // 10 segundos
    private static final int MAX_RECONNECTION_ATTEMPTS = 3;

    public static Connection getConnection() throws SQLException {
        if (dataSource == null || dataSource.isClosed()) {
            synchronized (LOCK) {
                if (dataSource == null || dataSource.isClosed()) {
                    initializeDatabase();
                }
            }
        }
        return dataSource.getConnection();
    }

    private static void initializeDatabase() {
        try {
            loadProperties();

            // LÓGICA CORREGIDA: Solo verificar conectividad si es Oracle
            if (isOracleConnection) {
                System.out.println("Detectada configuración Oracle, verificando conectividad...");

                if (checkOracleConnectivity()) {
                    System.out.println("✅ Conectividad Oracle verificada, iniciando conexión online");
                    try {
                        initializeDataSource();
                        hasBeenOnline = true;
                        System.out.println("✅ Conexión Oracle establecida exitosamente");
                        return; // ÉXITO - salir sin modo offline
                    } catch (Exception e) {
                        System.err.println("❌ Error conectando a Oracle: " + e.getMessage());
                        fallbackToOfflineMode("Error de conexión Oracle: " + e.getMessage());
                    }
                } else {
                    System.out.println("❌ Sin conectividad Oracle, iniciando modo offline");
                    fallbackToOfflineMode("Sin conectividad a Oracle Cloud");
                }
            } else {
                // MySQL u otra BD local - conectar directamente
                System.out.println("Configuración local detectada, conectando directamente...");
                try {
                    initializeDataSource();
                    System.out.println("✅ Conexión local establecida");
                } catch (Exception e) {
                    System.err.println("❌ Error en conexión local: " + e.getMessage());
                    fallbackToOfflineMode("Error en base de datos local: " + e.getMessage());
                }
            }

        } catch (Exception e) {
            System.err.println("❌ Error crítico inicializando base de datos: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("No se pudo inicializar la base de datos", e);
        }
    }

    /**
     * MÉTODO MEJORADO: Verificación específica de conectividad Oracle
     */
    private static boolean checkOracleConnectivity() {
        System.out.println("Verificando conectividad Oracle Cloud...");

        try {
            // 1. Verificar conectividad básica a internet
            if (!InetAddress.getByName("8.8.8.8").isReachable(CONNECTIVITY_TIMEOUT_MS)) {
                System.out.println("   Sin conectividad basica a internet");
                return false;
            }
            System.out.println("   Conectividad basica verificada");

            // 2. Verificar conectividad a múltiples endpoints de Oracle Cloud
            String[] oracleHosts = {
                    "adb.us-phoenix-1.oraclecloud.com",
                    "oracle.com",
                    "cloud.oracle.com"
            };

            boolean oracleReachable = false;
            for (String host : oracleHosts) {
                try {
                    if (InetAddress.getByName(host).isReachable(CONNECTIVITY_TIMEOUT_MS)) {
                        System.out.println("   Conectividad Oracle verificada via: " + host);
                        oracleReachable = true;
                        break;
                    }
                } catch (Exception e) {
                    System.out.println("   No se pudo verificar conectividad a: " + host);
                }
            }

            if (!oracleReachable) {
                System.out.println("   Sin conectividad a ningun endpoint de Oracle Cloud");

                // 3. Como fallback, intentar una conexión real a la base de datos
                System.out.println("   Intentando conexion directa a Oracle como test final...");
                return attemptDirectOracleConnection();
            }

            return true;

        } catch (Exception e) {
            System.out.println("   Error verificando conectividad: " + e.getMessage());

            // Si hay error en la verificación de red, intentar conexión directa
            System.out.println("   Intentando conexion directa como fallback...");
            return attemptDirectOracleConnection();
        }
    }

    /**
     * MÉTODO NUEVO: Intento directo de conexión a Oracle como último recurso
     */
    private static boolean attemptDirectOracleConnection() {
        try {
            System.out.println("   Probando conexion directa a Oracle...");

            // Configurar temporalmente para Oracle
            HikariConfig testConfig = new HikariConfig();
            configureOracleDataSource(testConfig);

            // Configuración especial para test rápido
            testConfig.setConnectionTimeout(8000); // 8 segundos
            testConfig.setValidationTimeout(5000);  // 5 segundos
            testConfig.setMaximumPoolSize(1);
            testConfig.setMinimumIdle(0);

            try (HikariDataSource testDataSource = new HikariDataSource(testConfig);
                 Connection testConn = testDataSource.getConnection()) {

                if (testConn.isValid(3)) {
                    System.out.println("   ✅ Conexion directa a Oracle exitosa");
                    testDataSource.close();
                    return true;
                } else {
                    System.out.println("   ❌ Conexion directa invalida");
                    testDataSource.close();
                    return false;
                }
            }

        } catch (Exception e) {
            System.out.println("   ❌ Conexion directa a Oracle fallo: " + e.getMessage());
            return false;
        }
    }

    /**
     * MÉTODO ALTERNATIVO: Verificación más simple y directa
     */
    private static boolean checkOracleConnectivitySimple() {
        System.out.println("Verificando conectividad Oracle Cloud (modo simple)...");

        // Saltarse las verificaciones de red y ir directo a probar la conexión
        return attemptDirectOracleConnection();
    }

    /**
     * MÉTODO PARA DEBUGGING: Información detallada de conectividad
     */
    private static void debugConnectivity() {
        System.out.println("=== DEBUG DE CONECTIVIDAD ===");

        try {
            // Test de DNS
            System.out.println("1. Resolviendo DNS para adb.us-phoenix-1.oraclecloud.com...");
            InetAddress addr = InetAddress.getByName("adb.us-phoenix-1.oraclecloud.com");
            System.out.println("   IP resuelta: " + addr.getHostAddress());

            // Test de ping
            System.out.println("2. Probando conectividad ICMP...");
            boolean reachable = addr.isReachable(5000);
            System.out.println("   ICMP reachable: " + reachable);

            // Test de socket
            System.out.println("3. Probando socket TCP...");
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(addr, 1522), 5000);
                System.out.println("   Socket TCP: CONECTADO");
            } catch (Exception e) {
                System.out.println("   Socket TCP: FALLO - " + e.getMessage());
            }

        } catch (Exception e) {
            System.out.println("Error en debug: " + e.getMessage());
        }

        System.out.println("=== FIN DEBUG ===");
    }

    /**
     * MÉTODO NUEVO: Cambiar a modo offline de forma limpia
     */
    private static void fallbackToOfflineMode(String reason) {
        System.out.println("\nCAMBIANDO A MODO OFFLINE");
        System.out.println("   Razon: " + reason);

        // Cerrar conexión existente si hay una
        if (dataSource != null && !dataSource.isClosed()) {
            try {
                dataSource.close();
                System.out.println("   Conexion anterior cerrada");
            } catch (Exception e) {
                System.err.println("   Error cerrando conexion: " + e.getMessage());
            }
            dataSource = null;
        }

        // Configurar modo offline
        isOfflineMode = true;
        offlineReason = reason;
        boolean wasOracleConnection = isOracleConnection;
        isOracleConnection = false; // Temporalmente para SQLite

        // Reconfigurar properties para SQLite
        properties.setProperty("db.url", "jdbc:sqlite:calendario_offline.db");
        properties.setProperty("db.driver", "org.sqlite.JDBC");
        properties.setProperty("db.username", "");
        properties.setProperty("db.password", "");

        try {
            initializeDataSource();
            System.out.println("   Base de datos SQLite offline inicializada");

            // Restaurar flag de Oracle para reconexión futura
            if (wasOracleConnection) {
                isOracleConnection = true;
            }

        } catch (Exception e) {
            System.err.println("   Error critico inicializando SQLite: " + e.getMessage());
            throw new RuntimeException("No se pudo inicializar base de datos offline", e);
        }
    }

    private static void loadProperties() {
        properties = new Properties();
        try (InputStream input = DatabaseConfig.class.getResourceAsStream(CONFIG_FILE)) {
            if (input != null) {
                properties.load(input);
                String driver = properties.getProperty("db.driver", "");
                isOracleConnection = driver.contains("oracle");

                System.out.println("Configuracion cargada:");
                System.out.println("   Driver: " + driver);
                System.out.println("   Es Oracle: " + (isOracleConnection ? "SI" : "NO"));

                if (isOracleConnection) {
                    configureOracleWallet();
                }
            } else {
                System.out.println("Archivo de configuracion no encontrado, usando valores por defecto");
                setDefaultProperties();
            }
        } catch (IOException e) {
            System.err.println("Error cargando propiedades: " + e.getMessage());
            setDefaultProperties();
        }
    }

    private static void configureOracleWallet() {
        try {
            URL walletUrl = DatabaseConfig.class.getResource("/wallet");
            if (walletUrl == null) {
                System.err.println("Wallet Oracle no encontrado en recursos");
                return;
            }

            String walletPath = new File(walletUrl.toURI()).getAbsolutePath();
            System.setProperty("oracle.net.wallet_location",
                    "(SOURCE=(METHOD=file)(METHOD_DATA=(DIRECTORY=" + walletPath + ")))");
            System.setProperty("oracle.net.tns_admin", walletPath);
            System.setProperty("TNS_ADMIN", walletPath);

            System.out.println("   Wallet Oracle configurado: " + walletPath);

        } catch (Exception e) {
            System.err.println("Error configurando wallet Oracle: " + e.getMessage());
        }
    }

    private static void setDefaultProperties() {
        properties = new Properties();
        properties.setProperty("db.url", "jdbc:mysql://localhost:3306/calendar");
        properties.setProperty("db.username", "root");
        properties.setProperty("db.password", "");
        properties.setProperty("db.driver", "com.mysql.cj.jdbc.Driver");
        isOracleConnection = false;
        System.out.println("Configuracion por defecto establecida (MySQL)");
    }

    private static void initializeDataSource() {
        try {
            String driver = properties.getProperty("db.driver");
            if (driver == null || driver.isEmpty()) {
                throw new SQLException("Driver de base de datos no configurado");
            }

            Class.forName(driver);
            HikariConfig config = new HikariConfig();

            if (isOracleConnection && !isOfflineMode) {
                configureOracleDataSource(config);
            } else if (isOfflineMode) {
                configureSQLiteDataSource(config);
            } else {
                configureMySQLDataSource(config);
            }

            dataSource = new HikariDataSource(config);

            // Verificar conexión
            try (Connection conn = dataSource.getConnection()) {
                if (conn.isValid(5)) {
                    if (isOfflineMode) {
                        System.out.println("Base de datos SQLite offline verificada");
                        initializeOfflineSchema(conn);
                    } else {
                        System.out.println("Conexion a base de datos online verificada");
                        hasBeenOnline = true;
                    }
                } else {
                    throw new SQLException("Conexion no valida");
                }
            }

        } catch (Exception e) {
            System.err.println("Error inicializando DataSource: " + e.getMessage());
            if (dataSource != null && !dataSource.isClosed()) {
                dataSource.close();
                dataSource = null;
            }
            throw new RuntimeException("No se pudo inicializar el pool de conexiones", e);
        }
    }

    private static void configureOracleDataSource(HikariConfig config) throws Exception {
        URL walletUrl = DatabaseConfig.class.getResource("/wallet");
        if (walletUrl == null) {
            throw new RuntimeException("Wallet Oracle no encontrado");
        }

        String walletPath = new File(walletUrl.toURI()).getAbsolutePath();
        String walletPathForURL = walletPath.replace("\\", "/");
        String serviceName = "h0ynnyaxuegd5aok_high";
        String urlWithWallet = "jdbc:oracle:thin:@" + serviceName + "?TNS_ADMIN=" + walletPathForURL;

        config.setJdbcUrl(urlWithWallet);
        config.setUsername("ADMIN");
        config.setPassword("Ithera-2025#");

        // CONFIGURACIÓN OPTIMIZADA PARA ORACLE
        config.setMaximumPoolSize(5);
        config.setMinimumIdle(1); // Al menos 1 conexión activa
        config.setIdleTimeout(300000); // 5 minutos
        config.setConnectionTimeout(ORACLE_CONNECTION_TIMEOUT_MS);
        config.setValidationTimeout(5000);
        config.setMaxLifetime(600000); // 10 minutos
        config.setConnectionTestQuery("SELECT 1 FROM DUAL");

        // Propiedades Oracle específicas
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        config.addDataSourceProperty("oracle.jdbc.ReadTimeout", "30000");
        config.addDataSourceProperty("oracle.net.READ_TIMEOUT", "30000");

        System.out.println("DataSource Oracle configurado");
    }

    private static void configureSQLiteDataSource(HikariConfig config) {
        String url = "jdbc:sqlite:calendario_offline.db";

        config.setJdbcUrl(url);
        config.setMaximumPoolSize(1); // SQLite solo soporta 1 escritor
        config.setMinimumIdle(0);
        config.setConnectionTimeout(30000);
        config.setValidationTimeout(5000);
        config.setConnectionTestQuery("SELECT 1");

        // Configuraciones SQLite específicas
        config.addDataSourceProperty("foreign_keys", "true");
        config.addDataSourceProperty("journal_mode", "WAL");
        config.addDataSourceProperty("synchronous", "NORMAL");
        config.addDataSourceProperty("cache_size", "2000");
        config.addDataSourceProperty("temp_store", "memory");

        System.out.println("DataSource SQLite configurado: " + url);
    }

    private static void configureMySQLDataSource(HikariConfig config) {
        config.setJdbcUrl(properties.getProperty("db.url"));
        config.setUsername(properties.getProperty("db.username"));
        config.setPassword(properties.getProperty("db.password"));
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setIdleTimeout(300000);
        config.setConnectionTimeout(10000);
        config.setValidationTimeout(3000);
        config.setMaxLifetime(600000);

        System.out.println("DataSource MySQL configurado");
    }

    /**
     * MÉTODO MEJORADO DE RECONEXIÓN
     */
    public static boolean attemptReconnection() {
        if (!isOfflineMode) {
            System.out.println("✅ Ya en modo online, no necesita reconexión");
            return true;
        }

        System.out.println("\n🔄 INTENTANDO RECONEXIÓN A BASE DE DATOS ONLINE...");

        // Solo intentar reconexión si originalmente era Oracle
        if (!isOracleConnection) {
            System.out.println("❌ Configuración no es Oracle, no se puede reconectar");
            return false;
        }

        int attempts = 0;
        while (attempts < MAX_RECONNECTION_ATTEMPTS) {
            attempts++;
            System.out.printf("🔍 Intento %d/%d de reconexión...\n", attempts, MAX_RECONNECTION_ATTEMPTS);

            if (checkOracleConnectivity()) {
                try {
                    // Cerrar conexión offline actual
                    closeDataSource();
                    dataSource = null;

                    // Resetear flags
                    isOfflineMode = false;
                    offlineReason = "";

                    // Recargar configuración Oracle
                    loadProperties();

                    // Intentar conexión online
                    initializeDataSource();

                    System.out.println("✅ RECONEXIÓN EXITOSA - Volviendo a modo online");
                    hasBeenOnline = true;
                    return true;

                } catch (Exception e) {
                    System.err.printf("❌ Error en intento %d: %s\n", attempts, e.getMessage());

                    if (attempts < MAX_RECONNECTION_ATTEMPTS) {
                        try {
                            Thread.sleep(2000); // Esperar 2 segundos antes del siguiente intento
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }
            } else {
                System.out.println("❌ Sin conectividad Oracle en intento " + attempts);
                if (attempts < MAX_RECONNECTION_ATTEMPTS) {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        // Si falla, asegurar que vuelva a modo offline
        System.out.println("Reconexion fallo despues de " + MAX_RECONNECTION_ATTEMPTS + " intentos");
        fallbackToOfflineMode("Fallo en reconexion despues de " + MAX_RECONNECTION_ATTEMPTS + " intentos");

        // Re-inicializar offline
        try {
            initializeDataSource();
        } catch (Exception e) {
            System.err.println("Error critico re-inicializando offline: " + e.getMessage());
        }

        return false;
    }

    private static void initializeOfflineSchema(Connection conn) {
        try {
            System.out.println("Inicializando esquema completo de base de datos offline...");

            if (needsSchemaUpdate(conn)) {
                System.out.println("Detectando esquema desactualizado, recreando tablas...");
                dropExistingTables(conn);
            }

            String[] createTables = {
                    """
                CREATE TABLE IF NOT EXISTS USERS (
                    USER_ID TEXT PRIMARY KEY,
                    MATRICULA TEXT UNIQUE,
                    EMAIL TEXT UNIQUE NOT NULL,
                    FIRST_NAME TEXT NOT NULL,
                    LAST_NAME TEXT NOT NULL,
                    PASSWORD TEXT NOT NULL,
                    ROLE TEXT NOT NULL DEFAULT 'alumno',
                    ACTIVE TEXT NOT NULL DEFAULT 'Y',
                    CREATED_DATE DATETIME DEFAULT CURRENT_TIMESTAMP,
                    LAST_LOGIN DATETIME
                )
                """,

                    """
                CREATE TABLE IF NOT EXISTS CALENDARS (
                    CALENDAR_ID TEXT PRIMARY KEY,
                    OWNER_ID TEXT NOT NULL,
                    NAME TEXT NOT NULL,
                    DESCRIPTION TEXT,
                    COLOR TEXT DEFAULT '#3498db',
                    ACTIVE TEXT DEFAULT 'Y',
                    CREATED_DATE DATETIME DEFAULT CURRENT_TIMESTAMP,
                    MODIFIED_DATE DATETIME,
                    SHARE_CODE TEXT,
                    SHARE_CODE_EXPIRY DATETIME,
                    IS_SHARED TEXT DEFAULT 'N',
                    FOREIGN KEY (OWNER_ID) REFERENCES USERS(USER_ID) ON DELETE CASCADE
                )
                """,

                    """
                CREATE TABLE IF NOT EXISTS EVENTS (
                    EVENT_ID TEXT PRIMARY KEY,
                    CALENDAR_ID TEXT NOT NULL,
                    CREATOR_ID TEXT,
                    TITLE TEXT NOT NULL,
                    DESCRIPTION TEXT,
                    START_DATE DATETIME NOT NULL,
                    END_DATE DATETIME,
                    ALL_DAY TEXT DEFAULT 'N',
                    LOCATION TEXT,
                    RECURRENCE TEXT,
                    RECURRENCE_END_DATE DATETIME,
                    ACTIVE TEXT DEFAULT 'Y',
                    CREATED_DATE DATETIME DEFAULT CURRENT_TIMESTAMP,
                    MODIFIED_DATE DATETIME,
                    FOREIGN KEY (CALENDAR_ID) REFERENCES CALENDARS(CALENDAR_ID) ON DELETE CASCADE,
                    FOREIGN KEY (CREATOR_ID) REFERENCES USERS(USER_ID)
                )
                """,

                    """
                CREATE TABLE IF NOT EXISTS CALENDAR_PERMISSIONS (
                    PERMISSION_ID TEXT PRIMARY KEY,
                    CALENDAR_ID TEXT NOT NULL,
                    USER_ID TEXT NOT NULL,
                    PERMISSION_LEVEL TEXT NOT NULL DEFAULT 'read',
                    PERMISSION_TYPE TEXT NOT NULL DEFAULT 'shared',
                    GRANTED_BY TEXT NOT NULL,
                    GRANTED_AT DATETIME DEFAULT CURRENT_TIMESTAMP,
                    SHARED_DATE DATETIME DEFAULT CURRENT_TIMESTAMP,
                    ACTIVE TEXT DEFAULT 'Y',
                    FOREIGN KEY (CALENDAR_ID) REFERENCES CALENDARS(CALENDAR_ID) ON DELETE CASCADE,
                    FOREIGN KEY (USER_ID) REFERENCES USERS(USER_ID) ON DELETE CASCADE,
                    FOREIGN KEY (GRANTED_BY) REFERENCES USERS(USER_ID)
                )
                """,

                    """
                CREATE TABLE IF NOT EXISTS INVITATIONS (
                    INVITATION_ID TEXT PRIMARY KEY,
                    CALENDAR_ID TEXT NOT NULL,
                    INVITED_USER_ID TEXT NOT NULL,
                    INVITER_USER_ID TEXT NOT NULL,
                    PERMISSION_TYPE TEXT DEFAULT 'read',
                    STATUS TEXT DEFAULT 'PENDING',
                    INVITATION_DATE DATETIME DEFAULT CURRENT_TIMESTAMP,
                    RESPONSE_DATE DATETIME,
                    FOREIGN KEY (CALENDAR_ID) REFERENCES CALENDARS(CALENDAR_ID) ON DELETE CASCADE,
                    FOREIGN KEY (INVITED_USER_ID) REFERENCES USERS(USER_ID) ON DELETE CASCADE,
                    FOREIGN KEY (INVITER_USER_ID) REFERENCES USERS(USER_ID) ON DELETE CASCADE
                )
                """,

                    """
                CREATE TABLE IF NOT EXISTS CLASS_TEMPLATES (
                    TEMPLATE_ID TEXT PRIMARY KEY,
                    TEACHER_ID TEXT NOT NULL,
                    TEMPLATE_NAME TEXT NOT NULL,
                    SUBJECT TEXT,
                    GROUP_NAME TEXT,
                    DURATION_MINUTES INTEGER,
                    WEEK_DAYS TEXT,
                    START_TIME TIME,
                    CREATED_DATE DATETIME DEFAULT CURRENT_TIMESTAMP,
                    ACTIVE TEXT DEFAULT 'Y',
                    FOREIGN KEY (TEACHER_ID) REFERENCES USERS(USER_ID) ON DELETE CASCADE
                )
                """,

                    """
                CREATE TABLE IF NOT EXISTS pending_sync (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    table_name TEXT NOT NULL,
                    record_id TEXT NOT NULL,
                    operation TEXT NOT NULL,
                    data TEXT,
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
                )
                """
            };

            for (String createTable : createTables) {
                try {
                    conn.createStatement().execute(createTable);
                } catch (Exception e) {
                    System.err.println("Error creando tabla: " + e.getMessage());
                }
            }

            createTestDataIfNeeded(conn);

            String[] createIndexes = {
                    "CREATE INDEX IF NOT EXISTS idx_calendars_owner_id ON CALENDARS(OWNER_ID)",
                    "CREATE INDEX IF NOT EXISTS idx_events_calendar_id ON EVENTS(CALENDAR_ID)",
                    "CREATE INDEX IF NOT EXISTS idx_events_creator_id ON EVENTS(CREATOR_ID)",
                    "CREATE INDEX IF NOT EXISTS idx_events_start_date ON EVENTS(START_DATE)",
                    "CREATE INDEX IF NOT EXISTS idx_permissions_calendar_id ON CALENDAR_PERMISSIONS(CALENDAR_ID)",
                    "CREATE INDEX IF NOT EXISTS idx_permissions_user_id ON CALENDAR_PERMISSIONS(USER_ID)",
                    "CREATE INDEX IF NOT EXISTS idx_invitations_calendar_id ON INVITATIONS(CALENDAR_ID)",
                    "CREATE INDEX IF NOT EXISTS idx_invitations_invited_user ON INVITATIONS(INVITED_USER_ID)"
            };

            for (String createIndex : createIndexes) {
                try {
                    conn.createStatement().execute(createIndex);
                } catch (Exception e) {
                    // Indices pueden ya existir
                }
            }

            System.out.println("Esquema completo de base de datos offline inicializado correctamente");
        } catch (Exception e) {
            System.err.println("Error inicializando esquema offline: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void createTestDataIfNeeded(Connection conn) throws SQLException {
        String checkUser = "SELECT COUNT(*) FROM USERS WHERE EMAIL = '20243ds076@utez.edu.mx'";
        try (var stmt = conn.createStatement();
             var rs = stmt.executeQuery(checkUser)) {

            if (rs.next() && rs.getInt(1) == 0) {
                String insertTestUser = """
                    INSERT INTO USERS (USER_ID, MATRICULA, FIRST_NAME, LAST_NAME, EMAIL, PASSWORD, ROLE, ACTIVE) 
                    VALUES ('USR0000001', '20243ds076', 'Usuario', 'Prueba', '20243ds076@utez.edu.mx', '123456', 'alumno', 'Y')
                """;
                stmt.execute(insertTestUser);
                System.out.println("Usuario de prueba creado para modo offline");

                String[] calendars = {
                        "INSERT INTO CALENDARS (CALENDAR_ID, OWNER_ID, NAME, DESCRIPTION, COLOR, ACTIVE) VALUES ('CAL0000001', 'USR0000001', 'Mis Clases', 'Calendario principal', '#1976D2', 'Y')",
                        "INSERT INTO CALENDARS (CALENDAR_ID, OWNER_ID, NAME, DESCRIPTION, COLOR, ACTIVE) VALUES ('CAL0000002', 'USR0000001', 'Personal', 'Eventos personales', '#4CAF50', 'Y')",
                        "INSERT INTO CALENDARS (CALENDAR_ID, OWNER_ID, NAME, DESCRIPTION, COLOR, ACTIVE) VALUES ('CAL0000003', 'USR0000001', 'Tareas y Proyectos', 'Tareas academicas', '#FF5722', 'Y')"
                };

                for (String calendarSql : calendars) {
                    stmt.execute(calendarSql);
                }
                System.out.println("Calendarios por defecto creados");

                String[] events = {
                        "INSERT INTO EVENTS (EVENT_ID, CALENDAR_ID, CREATOR_ID, TITLE, DESCRIPTION, START_DATE, END_DATE, LOCATION, ACTIVE) VALUES ('EVE0000001', 'CAL0000001', 'USR0000001', 'Clase de Programacion', 'Clase de Java avanzado', datetime('now', '+1 day', '+8 hours'), datetime('now', '+1 day', '+10 hours'), 'Aula A101', 'Y')",
                        "INSERT INTO EVENTS (EVENT_ID, CALENDAR_ID, CREATOR_ID, TITLE, DESCRIPTION, START_DATE, END_DATE, LOCATION, ACTIVE) VALUES ('EVE0000002', 'CAL0000001', 'USR0000001', 'Laboratorio BD', 'Practica de bases de datos', datetime('now', '+2 days', '+14 hours'), datetime('now', '+2 days', '+16 hours'), 'Lab Computo', 'Y')",
                        "INSERT INTO EVENTS (EVENT_ID, CALENDAR_ID, CREATOR_ID, TITLE, DESCRIPTION, START_DATE, END_DATE, LOCATION, ACTIVE) VALUES ('EVE0000003', 'CAL0000002', 'USR0000001', 'Reunion Personal', 'Cita importante', datetime('now', '+3 days', '+16 hours'), datetime('now', '+3 days', '+17 hours'), 'Casa', 'Y')"
                };

                for (String eventSql : events) {
                    stmt.execute(eventSql);
                }
                System.out.println("Eventos de ejemplo creados");
            }
        }
    }

    private static boolean needsSchemaUpdate(Connection conn) {
        try {
            String checkColumnSql = "PRAGMA table_info(CALENDAR_PERMISSIONS)";
            try (var stmt = conn.createStatement();
                 var rs = stmt.executeQuery(checkColumnSql)) {

                boolean hasSharedDate = false;
                boolean hasPermissionType = false;

                while (rs.next()) {
                    String columnName = rs.getString("name");
                    if ("SHARED_DATE".equals(columnName)) {
                        hasSharedDate = true;
                    }
                    if ("PERMISSION_TYPE".equals(columnName)) {
                        hasPermissionType = true;
                    }
                }

                return !hasSharedDate || !hasPermissionType;
            }
        } catch (Exception e) {
            return true;
        }
    }

    private static void dropExistingTables(Connection conn) {
        String[] tables = {
                "pending_sync", "CLASS_TEMPLATES", "INVITATIONS",
                "CALENDAR_PERMISSIONS", "EVENTS", "CALENDARS", "USERS"
        };

        for (String table : tables) {
            try {
                conn.createStatement().execute("DROP TABLE IF EXISTS " + table);
                System.out.println("Tabla " + table + " eliminada");
            } catch (Exception e) {
                // No es critico
            }
        }
    }

    public static Connection getConnectionWithRetry() throws SQLException {
        int maxRetries = isOfflineMode ? 1 : 2;
        int retryCount = 0;
        int retryDelayMs = 500;

        while (retryCount < maxRetries) {
            try {
                return getConnection();
            } catch (SQLException e) {
                if (!isOfflineMode && e.getMessage().contains("ORA-00018") && retryCount < maxRetries - 1) {
                    System.err.println("Error de maximo de sesiones, reintentando en " +
                            retryDelayMs + "ms... (Intento " + (retryCount + 1) +
                            " de " + maxRetries + ")");
                    retryCount++;
                    try {
                        Thread.sleep(retryDelayMs);
                        retryDelayMs *= 1.5;
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                } else {
                    throw e;
                }
            }
        }
        throw new SQLException("No se pudo obtener conexion despues de " + maxRetries + " intentos");
    }

    public static void closeDataSource() {
        if (dataSource != null && !dataSource.isClosed()) {
            System.out.println("Cerrando pool de conexiones...");
            dataSource.close();
            System.out.println("Pool de conexiones cerrado correctamente.");
        }
    }

    // Métodos de utilidad
    public static boolean isOfflineMode() {
        return isOfflineMode;
    }

    public static String getOfflineReason() {
        return offlineReason;
    }

    public static String getDatabaseInfo() {
        if (isOfflineMode) {
            return "SQLite (Modo Offline) - " + offlineReason;
        } else if (isOracleConnection) {
            return "Oracle Cloud Premium (Online)";
        } else {
            return "MySQL Local (Online)";
        }
    }

    public static void forceOfflineMode(String reason) {
        System.out.println("Forzando modo offline: " + reason);
        closeDataSource();
        dataSource = null;
        fallbackToOfflineMode(reason);
        try {
            initializeDataSource();
        } catch (Exception e) {
            System.err.println("Error inicializando modo offline forzado: " + e.getMessage());
        }
    }

    public static HikariDataSource getDataSource() {
        return dataSource;
    }

    public static boolean hasBeenOnline() {
        return hasBeenOnline;
    }

    public static void resetOnlineFlag() {
        hasBeenOnline = false;
    }

    // AGREGAR ESTOS MÉTODOS A DatabaseConfig.java

    /**
     * MÉTODO NUEVO: Verificar si la conexión actual sigue siendo válida
     */
    public static boolean isCurrentConnectionValid() {
        if (dataSource == null || dataSource.isClosed()) {
            return false;
        }

        try (Connection conn = dataSource.getConnection()) {
            return conn.isValid(3); // 3 segundos timeout
        } catch (Exception e) {
            System.out.println("Conexion actual no valida: " + e.getMessage());
            return false;
        }
    }

    /**
     * MÉTODO NUEVO: Forzar transición a modo offline cuando se pierde conectividad
     */
    public static synchronized boolean forceTransitionToOffline(String reason) {
        System.out.println("\n🔄 FORZANDO TRANSICIÓN A MODO OFFLINE");
        System.out.println("   Razón: " + reason);

        try {
            // 1. Migrar datos críticos ANTES de cambiar a offline
            if (!isOfflineMode && dataSource != null) {
                System.out.println("   Migrando datos críticos a SQLite...");
                migrateDataToOffline();
            }

            // 2. Cerrar conexión online
            if (dataSource != null && !dataSource.isClosed()) {
                try {
                    dataSource.close();
                    System.out.println("   Conexión online cerrada");
                } catch (Exception e) {
                    System.err.println("   Error cerrando conexión: " + e.getMessage());
                }
            }
            dataSource = null;

            // 3. Configurar flags de offline
            isOfflineMode = true;
            offlineReason = reason;
            boolean wasOracleConnection = isOracleConnection;
            isOracleConnection = false; // Temporalmente para SQLite

            // 4. Reconfigurar properties para SQLite
            properties.setProperty("db.url", "jdbc:sqlite:calendario_offline.db");
            properties.setProperty("db.driver", "org.sqlite.JDBC");
            properties.setProperty("db.username", "");
            properties.setProperty("db.password", "");

            // 5. Inicializar SQLite
            initializeDataSource();
            System.out.println("   ✅ Transición a offline completada");

            // 6. Restaurar flag de Oracle para futuras reconexiones
            if (wasOracleConnection) {
                isOracleConnection = true;
            }

            return true;

        } catch (Exception e) {
            System.err.println("   ❌ Error crítico en transición a offline: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("No se pudo hacer transición a modo offline", e);
        }
    }

    /**
     * MÉTODO NUEVO: Migrar datos importantes de Oracle a SQLite
     */
    private static void migrateDataToOffline() {
        System.out.println("     Iniciando migración de datos a SQLite...");

        try (Connection onlineConn = dataSource.getConnection()) {
            // Verificar que tenemos conexión válida
            if (!onlineConn.isValid(2)) {
                System.out.println("     Conexión online no válida, saltando migración");
                return;
            }

            // Inicializar conexión SQLite temporal para migración
            String sqliteUrl = "jdbc:sqlite:calendario_offline.db";
            try (Connection offlineConn = java.sql.DriverManager.getConnection(sqliteUrl)) {

                // Crear esquema si no existe
                createOfflineSchemaForMigration(offlineConn);

                // Migrar usuarios
                migrateUsers(onlineConn, offlineConn);

                // Migrar calendarios
                migrateCalendars(onlineConn, offlineConn);

                // Migrar eventos
                migrateEvents(onlineConn, offlineConn);

                // Migrar permisos
                migratePermissions(onlineConn, offlineConn);

                System.out.println("     ✅ Migración de datos completada");
            }

        } catch (Exception e) {
            System.err.println("     ❌ Error en migración: " + e.getMessage());
            // No es crítico - la app puede funcionar sin migrar
        }
    }

    /**
     * MÉTODO AUXILIAR: Crear esquema básico para migración
     */
    private static void createOfflineSchemaForMigration(Connection offlineConn) throws SQLException {
        String[] createTables = {
                """
        CREATE TABLE IF NOT EXISTS USERS (
            USER_ID TEXT PRIMARY KEY,
            MATRICULA TEXT UNIQUE,
            EMAIL TEXT UNIQUE NOT NULL,
            FIRST_NAME TEXT NOT NULL,
            LAST_NAME TEXT NOT NULL,
            PASSWORD TEXT NOT NULL,
            ROLE TEXT NOT NULL DEFAULT 'alumno',
            ACTIVE TEXT NOT NULL DEFAULT 'Y',
            CREATED_DATE DATETIME DEFAULT CURRENT_TIMESTAMP,
            LAST_LOGIN DATETIME
        )
        """,

                """
        CREATE TABLE IF NOT EXISTS CALENDARS (
            CALENDAR_ID TEXT PRIMARY KEY,
            OWNER_ID TEXT NOT NULL,
            NAME TEXT NOT NULL,
            DESCRIPTION TEXT,
            COLOR TEXT DEFAULT '#3498db',
            ACTIVE TEXT DEFAULT 'Y',
            CREATED_DATE DATETIME DEFAULT CURRENT_TIMESTAMP,
            MODIFIED_DATE DATETIME
        )
        """,

                """
        CREATE TABLE IF NOT EXISTS EVENTS (
            EVENT_ID TEXT PRIMARY KEY,
            CALENDAR_ID TEXT NOT NULL,
            CREATOR_ID TEXT,
            TITLE TEXT NOT NULL,
            DESCRIPTION TEXT,
            START_DATE DATETIME NOT NULL,
            END_DATE DATETIME,
            LOCATION TEXT,
            ACTIVE TEXT DEFAULT 'Y',
            CREATED_DATE DATETIME DEFAULT CURRENT_TIMESTAMP,
            MODIFIED_DATE DATETIME
        )
        """
        };

        for (String sql : createTables) {
            offlineConn.createStatement().execute(sql);
        }
    }

    /**
     * MÉTODO AUXILIAR: Migrar usuarios
     */
    private static void migrateUsers(Connection onlineConn, Connection offlineConn) {
        try {
            String selectSql = "SELECT USER_ID, MATRICULA, EMAIL, FIRST_NAME, LAST_NAME, PASSWORD, ROLE, ACTIVE FROM USERS WHERE ACTIVE = 'Y'";
            String insertSql = "INSERT OR REPLACE INTO USERS (USER_ID, MATRICULA, EMAIL, FIRST_NAME, LAST_NAME, PASSWORD, ROLE, ACTIVE) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

            try (PreparedStatement selectStmt = onlineConn.prepareStatement(selectSql);
                 PreparedStatement insertStmt = offlineConn.prepareStatement(insertSql);
                 ResultSet rs = selectStmt.executeQuery()) {

                int count = 0;
                while (rs.next()) {
                    insertStmt.setString(1, rs.getString("USER_ID"));
                    insertStmt.setString(2, rs.getString("MATRICULA"));
                    insertStmt.setString(3, rs.getString("EMAIL"));
                    insertStmt.setString(4, rs.getString("FIRST_NAME"));
                    insertStmt.setString(5, rs.getString("LAST_NAME"));
                    insertStmt.setString(6, rs.getString("PASSWORD"));
                    insertStmt.setString(7, rs.getString("ROLE"));
                    insertStmt.setString(8, rs.getString("ACTIVE"));
                    insertStmt.executeUpdate();
                    count++;
                }
                System.out.println("     Usuarios migrados: " + count);
            }
        } catch (Exception e) {
            System.err.println("     Error migrando usuarios: " + e.getMessage());
        }
    }

    /**
     * MÉTODO AUXILIAR: Migrar calendarios
     */
    private static void migrateCalendars(Connection onlineConn, Connection offlineConn) {
        try {
            String selectSql = "SELECT CALENDAR_ID, OWNER_ID, NAME, DESCRIPTION, COLOR, ACTIVE FROM CALENDARS WHERE ACTIVE = 'Y'";
            String insertSql = "INSERT OR REPLACE INTO CALENDARS (CALENDAR_ID, OWNER_ID, NAME, DESCRIPTION, COLOR, ACTIVE) VALUES (?, ?, ?, ?, ?, ?)";

            try (PreparedStatement selectStmt = onlineConn.prepareStatement(selectSql);
                 PreparedStatement insertStmt = offlineConn.prepareStatement(insertSql);
                 ResultSet rs = selectStmt.executeQuery()) {

                int count = 0;
                while (rs.next()) {
                    insertStmt.setString(1, rs.getString("CALENDAR_ID"));
                    insertStmt.setString(2, rs.getString("OWNER_ID"));
                    insertStmt.setString(3, rs.getString("NAME"));
                    insertStmt.setString(4, rs.getString("DESCRIPTION"));
                    insertStmt.setString(5, rs.getString("COLOR"));
                    insertStmt.setString(6, rs.getString("ACTIVE"));
                    insertStmt.executeUpdate();
                    count++;
                }
                System.out.println("     Calendarios migrados: " + count);
            }
        } catch (Exception e) {
            System.err.println("     Error migrando calendarios: " + e.getMessage());
        }
    }

    /**
     * MÉTODO AUXILIAR: Migrar eventos
     */
    private static void migrateEvents(Connection onlineConn, Connection offlineConn) {
        try {
            // Migrar solo eventos recientes y futuros
            String selectSql = """
            SELECT EVENT_ID, CALENDAR_ID, CREATOR_ID, TITLE, DESCRIPTION, 
                   START_DATE, END_DATE, LOCATION, ACTIVE 
            FROM EVENTS 
            WHERE ACTIVE = 'Y' 
            AND START_DATE >= SYSDATE - 30
            ORDER BY START_DATE DESC
            """;

            String insertSql = "INSERT OR REPLACE INTO EVENTS (EVENT_ID, CALENDAR_ID, CREATOR_ID, TITLE, DESCRIPTION, START_DATE, END_DATE, LOCATION, ACTIVE) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

            try (PreparedStatement selectStmt = onlineConn.prepareStatement(selectSql);
                 PreparedStatement insertStmt = offlineConn.prepareStatement(insertSql);
                 ResultSet rs = selectStmt.executeQuery()) {

                int count = 0;
                while (rs.next()) {
                    insertStmt.setString(1, rs.getString("EVENT_ID"));
                    insertStmt.setString(2, rs.getString("CALENDAR_ID"));
                    insertStmt.setString(3, rs.getString("CREATOR_ID"));
                    insertStmt.setString(4, rs.getString("TITLE"));
                    insertStmt.setString(5, rs.getString("DESCRIPTION"));
                    insertStmt.setTimestamp(6, rs.getTimestamp("START_DATE"));
                    insertStmt.setTimestamp(7, rs.getTimestamp("END_DATE"));
                    insertStmt.setString(8, rs.getString("LOCATION"));
                    insertStmt.setString(9, rs.getString("ACTIVE"));
                    insertStmt.executeUpdate();
                    count++;
                }
                System.out.println("     Eventos migrados: " + count);
            }
        } catch (Exception e) {
            System.err.println("     Error migrando eventos: " + e.getMessage());
        }
    }

    /**
     * MÉTODO AUXILIAR: Migrar permisos básicos
     */
    private static void migratePermissions(Connection onlineConn, Connection offlineConn) {
        try {
            // Crear tabla de permisos si no existe
            String createPermissions = """
            CREATE TABLE IF NOT EXISTS CALENDAR_PERMISSIONS (
                PERMISSION_ID TEXT PRIMARY KEY,
                CALENDAR_ID TEXT NOT NULL,
                USER_ID TEXT NOT NULL,
                PERMISSION_LEVEL TEXT NOT NULL DEFAULT 'read',
                ACTIVE TEXT DEFAULT 'Y'
            )
            """;
            offlineConn.createStatement().execute(createPermissions);

            String selectSql = "SELECT PERMISSION_ID, CALENDAR_ID, USER_ID, PERMISSION_LEVEL, ACTIVE FROM CALENDAR_PERMISSIONS WHERE ACTIVE = 'Y'";
            String insertSql = "INSERT OR REPLACE INTO CALENDAR_PERMISSIONS (PERMISSION_ID, CALENDAR_ID, USER_ID, PERMISSION_LEVEL, ACTIVE) VALUES (?, ?, ?, ?, ?)";

            try (PreparedStatement selectStmt = onlineConn.prepareStatement(selectSql);
                 PreparedStatement insertStmt = offlineConn.prepareStatement(insertSql);
                 ResultSet rs = selectStmt.executeQuery()) {

                int count = 0;
                while (rs.next()) {
                    insertStmt.setString(1, rs.getString("PERMISSION_ID"));
                    insertStmt.setString(2, rs.getString("CALENDAR_ID"));
                    insertStmt.setString(3, rs.getString("USER_ID"));
                    insertStmt.setString(4, rs.getString("PERMISSION_LEVEL"));
                    insertStmt.setString(5, rs.getString("ACTIVE"));
                    insertStmt.executeUpdate();
                    count++;
                }
                System.out.println("     Permisos migrados: " + count);
            }
        } catch (Exception e) {
            System.err.println("     Error migrando permisos: " + e.getMessage());
        }
    }


}
