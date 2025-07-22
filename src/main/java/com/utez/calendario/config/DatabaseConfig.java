package com.utez.calendario.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;

public class DatabaseConfig {
    private static final String CONFIG_FILE = "/database.properties";
    private static Properties properties;
    private static volatile HikariDataSource dataSource;
    private static final Object LOCK = new Object();
    private static boolean isOracleConnection = false;

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
            initializeDataSource();
            System.out.println("Pool de conexiones establecido: " +
                    (isOracleConnection ? "Oracle Cloud Premium (Educación)" : "MySQL Local"));
        } catch (Exception e) {
            System.err.println("Error inicializando la base de datos: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("No se pudo inicializar la base de datos", e);
        }
    }

    private static void loadProperties() {
        properties = new Properties();
        try (InputStream input = DatabaseConfig.class.getResourceAsStream(CONFIG_FILE)) {
            if (input != null) {
                properties.load(input);
                String driver = properties.getProperty("db.driver", "");
                isOracleConnection = driver.contains("oracle");

                if (isOracleConnection) {
                    configureOracleWallet();
                }
            } else {
                setDefaultProperties();
            }
        } catch (IOException e) {
            System.err.println("Error al cargar propiedades de BD: " + e.getMessage());
            setDefaultProperties();
        }
    }

    private static void configureOracleWallet() {
        try {
            URL walletUrl = DatabaseConfig.class.getResource("/wallet");
            if (walletUrl == null) {
                System.err.println("Wallet no encontrado");
                return;
            }

            String walletPath = new File(walletUrl.toURI()).getAbsolutePath();
            System.setProperty("oracle.net.wallet_location",
                    "(SOURCE=(METHOD=file)(METHOD_DATA=(DIRECTORY=" + walletPath + ")))");
            System.setProperty("oracle.net.tns_admin", walletPath);
            System.setProperty("TNS_ADMIN", walletPath);
        } catch (Exception e) {
            System.err.println("Error configurando wallet: " + e.getMessage());
        }
    }

    private static void setDefaultProperties() {
        properties = new Properties();
        properties.setProperty("db.url", "jdbc:mysql://localhost:3306/calendar");
        properties.setProperty("db.username", "root");
        properties.setProperty("db.password", "");
        properties.setProperty("db.driver", "com.mysql.cj.jdbc.Driver");
    }

    private static void initializeDataSource() {
        try {
            Class.forName(properties.getProperty("db.driver"));

            HikariConfig config = new HikariConfig();

            if (isOracleConnection) {
                URL walletUrl = DatabaseConfig.class.getResource("/wallet");
                String walletPath = new File(walletUrl.toURI()).getAbsolutePath();
                String walletPathForURL = walletPath.replace("\\", "/");

                String serviceName = "h0ynnyaxuegd5aok_high";
                String urlWithWallet = "jdbc:oracle:thin:@" + serviceName + "?TNS_ADMIN=" + walletPathForURL;

                config.setJdbcUrl(urlWithWallet);
                config.setUsername("ADMIN");
                config.setPassword("Ithera-2025#");
                config.setMaximumPoolSize(5);
                config.setMinimumIdle(0);             // Sin conexiones inactivas al inicio
                config.setIdleTimeout(60000);         // 1 minuto
                config.setConnectionTimeout(10000);    // 10 segundos
                config.setMaxLifetime(300000);        // 5 minutos

                // Configuraciones para validación de conexiones
                config.setConnectionTestQuery("SELECT 1 FROM DUAL");
                config.setValidationTimeout(5000);

                // Propiedades específicas para Oracle
                config.addDataSourceProperty("cachePrepStmts", "true");
                config.addDataSourceProperty("prepStmtCacheSize", "250");
                config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
                config.addDataSourceProperty("oracle.jdbc.ReadTimeout", "60000");
                config.addDataSourceProperty("oracle.net.READ_TIMEOUT", "60000");
            } else {
                // Configuración para MySQL
                config.setJdbcUrl(properties.getProperty("db.url"));
                config.setUsername(properties.getProperty("db.username"));
                config.setPassword(properties.getProperty("db.password"));
                config.setMaximumPoolSize(10);
            }

            dataSource = new HikariDataSource(config);

            // Verificar conexión
            try (Connection conn = dataSource.getConnection()) {
                if (conn.isValid(5)) {
                    System.out.println("Conexión a la base de datos verificada correctamente.");
                }
            }
        } catch (Exception e) {
            System.err.println("Error inicializando el pool de conexiones: " + e.getMessage());
            e.printStackTrace();
            if (dataSource != null && !dataSource.isClosed()) {
                dataSource.close();
            }
            throw new RuntimeException("No se pudo inicializar el pool de conexiones", e);
        }
    }

    public static Connection getConnectionWithRetry() throws SQLException {
        int maxRetries = 3;
        int retryCount = 0;
        int retryDelayMs = 1000;

        while (retryCount < maxRetries) {
            try {
                return getConnection();
            } catch (SQLException e) {
                if (e.getMessage().contains("ORA-00018") && retryCount < maxRetries - 1) {
                    System.err.println("Error de máximo de sesiones, reintentando en " +
                            retryDelayMs + "ms... (Intento " + (retryCount+1) +
                            " de " + maxRetries + ")");
                    retryCount++;
                    try {
                        Thread.sleep(retryDelayMs);
                        retryDelayMs *= 2;
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                } else {
                    throw e;
                }
            }
        }
        throw new SQLException("No se pudo obtener conexión después de " + maxRetries + " intentos");
    }

    public static void closeDataSource() {
        if (dataSource != null && !dataSource.isClosed()) {
            System.out.println("Cerrando pool de conexiones...");
            dataSource.close();
            System.out.println("Pool de conexiones cerrado correctamente.");
        }
    }

    // Para monitoreo del pool
    public static HikariDataSource getDataSource() {
        return dataSource;
    }
}