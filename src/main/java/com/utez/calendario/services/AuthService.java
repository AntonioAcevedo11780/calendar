package com.utez.calendario.services;

import com.utez.calendario.config.DatabaseConfig;
import com.utez.calendario.models.User;
import java.sql.*;
import java.time.LocalDateTime;

public class AuthService {
    private static AuthService instance;
    private User currentUser;

    private AuthService() {}

    public static AuthService getInstance() {
        if (instance == null) {
            instance = new AuthService();
        }
        return instance;
    }

    public boolean login(String email, String password) {
        System.out.println("=== INTENTO DE LOGIN ===");
        System.out.println("Email: " + email);
        System.out.println("Fecha: " + LocalDateTime.now());

        // Validar formato UTEZ
        if (!email.endsWith("@utez.edu.mx")) {
            System.out.println("✗ Email no es de UTEZ");
            return false;
        }

        String sql = "SELECT * FROM USERS WHERE EMAIL = ? AND ACTIVE = 'Y'";

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, email);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                // Verificar contraseña (simple para desarrollo)
                String dbPassword = rs.getString("PASSWORD");
                if (password.equals("123456") || password.equals(dbPassword)) {

                    // Crear objeto User
                    currentUser = new User();
                    currentUser.setUserId(rs.getString("USER_ID"));
                    currentUser.setMatricula(rs.getString("MATRICULA"));
                    currentUser.setEmail(rs.getString("EMAIL"));
                    currentUser.setFirstName(rs.getString("FIRST_NAME"));
                    currentUser.setLastName(rs.getString("LAST_NAME"));
                    currentUser.setRole(User.Role.fromString(rs.getString("ROLE")));

                    // Actualizar último login
                    updateLastLogin(currentUser.getUserId());

                    System.out.println("✓ LOGIN EXITOSO: " + currentUser.getFullName());
                    return true;
                }
            }

        } catch (SQLException e) {
            System.err.println("Error en login: " + e.getMessage());
        }

        System.out.println("✗ LOGIN FALLIDO");
        return false;
    }

    private void updateLastLogin(String userId) {
        String sql = "UPDATE USERS SET LAST_LOGIN = ? WHERE USER_ID = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setTimestamp(1, Timestamp.valueOf(LocalDateTime.now()));
            pstmt.setString(2, userId);
            pstmt.executeUpdate();

        } catch (SQLException e) {
            System.err.println("Error actualizando login: " + e.getMessage());
        }
    }

    public void logout() {
        if (currentUser != null) {
            System.out.println("Logout: " + currentUser.getFullName());
        }
        currentUser = null;
    }

    public User getCurrentUser() {
        return currentUser;
    }

    public boolean isLoggedIn() {
        return currentUser != null;
    }
}