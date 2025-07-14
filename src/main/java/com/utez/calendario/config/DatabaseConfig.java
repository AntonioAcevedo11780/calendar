package com.utez.calendario.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;
import java.io.File;

public class DatabaseConfig {
    private static final String CONFIG_FILE = "/database.properties";
    private static Properties properties;
    private static boolean connectionTested = false;
    private static boolean isOracleConnection = false;
    private static HikariDataSource dataSource;

    static {
        loadProperties();
        initializeDataSource();
    }

    private static void loadProperties() {
        properties = new Properties();
        try (InputStream input = DatabaseConfig.class.getResourceAsStream(CONFIG_FILE)) {
            if (input != null) {
                properties.load(input);
                String driver = properties.getProperty("db.driver", "");
                isOracleConnection = driver.contains("oracle");

                // Configurar Oracle Wallet si es necesario
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
            System.setProperty("oracle.net.wallet_location", "(SOURCE=(METHOD=file)(METHOD_DATA=(DIRECTORY=" + walletPath + ")))");
            System.setProperty("oracle.net.tns_admin", walletPath);
            System.setProperty("TNS_ADMIN", walletPath);
        } catch (Exception e) {
            System.err.println("Error configurando wallet: " + e.getMessage());
        }
    }

    private static void setDefaultProperties() {
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
                // Configurar conexión Oracle con wallet
                URL walletUrl = DatabaseConfig.class.getResource("/wallet");
                String walletPath = new File(walletUrl.toURI()).getAbsolutePath();
                String walletPathForURL = walletPath.replace("\\", "/");

                String serviceName = "h0ynnyaxuegd5aok_high";
                String urlWithWallet = "jdbc:oracle:thin:@" + serviceName + "?TNS_ADMIN=" + walletPathForURL;

                config.setJdbcUrl(urlWithWallet);
                config.setUsername("ADMIN");
                config.setPassword("Ithera-2025#");
            } else {
                config.setJdbcUrl(properties.getProperty("db.url"));
                config.setUsername(properties.getProperty("db.username"));
                config.setPassword(properties.getProperty("db.password"));
            }

            // Configuración optimizada para pool de conexiones
            config.setMaximumPoolSize(10);
            config.setMinimumIdle(3);
            config.setIdleTimeout(30000);
            config.setConnectionTimeout(10000);
            config.setMaxLifetime(1800000);

            // Configuraciones específicas de Oracle para mejorar rendimiento
            if (isOracleConnection) {
                config.addDataSourceProperty("cachePrepStmts", "true");
                config.addDataSourceProperty("prepStmtCacheSize", "250");
                config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
            }

            dataSource = new HikariDataSource(config);

            // Verifica la conexión
            if (!connectionTested) {
                System.out.println("Pool de conexiones establecido: " + (isOracleConnection ? "Oracle Cloud" : "MySQL Local"));
                connectionTested = true;
            }
        } catch (Exception e) {
            System.err.println("Error inicializando el pool de conexiones: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    public static void closeDataSource() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }
}