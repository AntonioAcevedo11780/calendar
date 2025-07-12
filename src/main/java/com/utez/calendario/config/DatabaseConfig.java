package com.utez.calendario.config;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;
import java.io.File;

public class DatabaseConfig {
    private static final String CONFIG_FILE = "/database.properties";
    private static Properties properties;
    private static boolean connectionTested = false;
    private static boolean isOracleConnection = false;

    static {
        loadProperties();
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

    public static Connection getConnection() throws SQLException {
        try {

            Class.forName(properties.getProperty("db.driver"));

            Connection conn = null;

            if (isOracleConnection) {
                // Configurar conexión Oracle con wallet
                URL walletUrl = DatabaseConfig.class.getResource("/wallet");
                String walletPath = new File(walletUrl.toURI()).getAbsolutePath();
                String walletPathForURL = walletPath.replace("\\", "/");

                String serviceName = "h0ynnyaxuegd5aok_high";
                String urlWithWallet = "jdbc:oracle:thin:@" + serviceName + "?TNS_ADMIN=" + walletPathForURL;

                // Conectar con credenciales ADMIN
                conn = DriverManager.getConnection(urlWithWallet, "ADMIN", "Ithera-2025#");

            } else {
                // Conexión MySQL normal
                conn = DriverManager.getConnection(
                        properties.getProperty("db.url"),
                        properties.getProperty("db.username"),
                        properties.getProperty("db.password")
                );
            }

            // Verifica la conexión xd
            if (!connectionTested) {
                System.out.println("Conexión establecida: " + (isOracleConnection ? "Oracle Cloud" : "MySQL Local"));
                connectionTested = true;
            }

            return conn;

        } catch (ClassNotFoundException e) {

            throw new SQLException("Driver no encontrado: " + e.getMessage(), e);

        } catch (SQLException e) {

            System.err.println("Error de conexión: " + e.getMessage());

            throw e;

        } catch (URISyntaxException e) {

            throw new RuntimeException(e);

        }
    }

    public static String getProperty(String key) {
        return properties.getProperty(key);
    }

    public static boolean testConnection() {
        try (Connection conn = getConnection()) {
            return conn != null && !conn.isClosed();
        } catch (SQLException e) {
            System.err.println("Test de conexión falló: " + e.getMessage());
            return false;
        }
    }
}