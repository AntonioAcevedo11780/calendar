package com.utez.calendario.config;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

public class DatabaseConfig {
    private static final String CONFIG_FILE = "/database.properties";
    private static Properties properties;
    private static boolean connectionTested = false;

    static {
        loadProperties();
    }

    private static void loadProperties() {
        properties = new Properties();
        try (InputStream input = DatabaseConfig.class.getResourceAsStream(CONFIG_FILE)) {
            if (input != null) {
                properties.load(input);
                System.out.println("✓ Propiedades de BD cargadas correctamente");
            } else {
                System.out.println("⚠ Archivo de propiedades no encontrado, usando valores por defecto");
                setDefaultProperties();
            }
        } catch (IOException e) {
            System.err.println("Error al cargar propiedades de BD: " + e.getMessage());
            setDefaultProperties();
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
            Connection conn = DriverManager.getConnection(
                    properties.getProperty("db.url"),
                    properties.getProperty("db.username"),
                    properties.getProperty("db.password")
            );

            if (!connectionTested) {
                System.out.println("✓ Conexión a base de datos establecida exitosamente");
                System.out.println("  URL: " + properties.getProperty("db.url"));
                connectionTested = true;
            }

            return conn;
        } catch (ClassNotFoundException e) {
            throw new SQLException("Driver de base de datos no encontrado: " + e.getMessage(), e);
        } catch (SQLException e) {
            System.err.println("Error de conexión a BD: " + e.getMessage());
            throw e;
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