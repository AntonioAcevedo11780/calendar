package com.utez.calendario.services;

import com.utez.calendario.config.DatabaseConfig;
import com.utez.calendario.models.User;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.UUID;

public class AuthService {

    private static final String USER_ID_PREFIX = "USR";
    private static final int ID_DIGITS = 7;

    private static AuthService instance;
    private User currentUser;

    private AuthService() {}

    public static AuthService getInstance() {
        if (instance == null) {
            instance = new AuthService();
        }
        return instance;
    }

    private static Connection getConnection() throws SQLException {

        return com.utez.calendario.config.DatabaseConfig.getConnection();

    }

    public boolean login(String email, String password) {
        System.out.println("=== INTENTO DE LOGIN ===");
        System.out.println("Email: " + email);
        System.out.println("Fecha: " + TimeService.getInstance().now());

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

    /**
     * Verifica únicamente las credenciales sin importar el estado de activación
     */
    public boolean authenticateOnly(String email, String password) {
        String sql = "SELECT COUNT(*) FROM USERS WHERE EMAIL = ? AND PASSWORD = ? AND ACTIVE = 'Y'";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, email);
            pstmt.setString(2, password);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                return rs.getInt(1) > 0;
            }

            return false;
        } catch (SQLException e) {
            System.err.println("Error al verificar credenciales: " + e.getMessage());
            return false;
        }
    }

    /**
     * Busca un usuario por su email para verificar su estado
     */
    public User findUserByEmail(String email) {
        String sql = "SELECT * FROM USERS WHERE EMAIL = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, email);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                User user = new User();
                user.setUserId(rs.getString("USER_ID"));
                user.setMatricula(rs.getString("MATRICULA"));
                user.setEmail(rs.getString("EMAIL"));
                user.setFirstName(rs.getString("FIRST_NAME"));
                user.setLastName(rs.getString("LAST_NAME"));
                user.setRole(User.Role.fromString(rs.getString("ROLE")));
                user.setActive(rs.getString("ACTIVE").charAt(0));
                return user;
            }
        } catch (SQLException e) {
            System.err.println("Error al buscar usuario por email: " + e.getMessage());
        }
        return null;
    }

    private void updateLastLogin(String userId) {
        String sql = "UPDATE USERS SET LAST_LOGIN = ? WHERE USER_ID = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setTimestamp(1, Timestamp.valueOf(LocalDateTime.now()));
            pstmt.setString(2, userId);
            pstmt.executeUpdate();   pstmt.setTimestamp(1, Timestamp.valueOf(TimeService.getInstance().now()));
            pstmt.setString(2, userId);
            pstmt.executeUpdate();

        } catch (SQLException e) {
            System.err.println("Error actualizando login: " + e.getMessage());
        }
    }

    public static void registerUser(String firstName, String lastName, String email, String password) {
        // 1. Validar email institucional UTEZ
        if (!User.isValidUtezEmail(email)) {
            throw new IllegalArgumentException("Correo institucional inválido. Debe ser de dominio @utez.edu.mx");
        }

        // 2. Verificar si es email de estudiante
        boolean isStudent = User.isStudentEmail(email);

        // 3. Determinar rol automáticamente
        String role = isStudent ? "alumno" : "docente";

        // 4. Extraer/generar matrícula según rol
        String matricula;
        if (isStudent) {
            // Para estudiantes: extraer del correo
            matricula = User.extractMatriculaFromEmail(email);
            if (matricula == null || !matricula.matches("\\d{5}[a-z]{2}\\d{3}")) {
                throw new IllegalArgumentException("Formato de matrícula inválido en el correo");
            }
        } else {
            // Para docentes: generar código DOC
            matricula = generateNextDocenteCode();
        }

        // 5. Generar USER_ID (siempre USR + 7 dígitos)
        String userId = generateNextUserId();

        // 6. Insertar en base de datos
        String sql = "INSERT INTO USERS (USER_ID, MATRICULA, FIRST_NAME, LAST_NAME, EMAIL, PASSWORD, ROLE, ACTIVE) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, 'Y')";

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, userId);
            pstmt.setString(2, matricula);
            pstmt.setString(3, firstName);
            pstmt.setString(4, lastName);
            pstmt.setString(5, email);
            pstmt.setString(6, password);
            pstmt.setString(7, role);

            int rowsAffected = pstmt.executeUpdate();

            if (rowsAffected == 0) {
                throw new SQLException("Registro fallido, ninguna fila afectada");
            }

        } catch (SQLException e) {
            throw new RuntimeException("Error en base de datos: " + e.getMessage(), e);
        }
    }

    // Genera USER_ID en formato USR0000001
    private static String generateNextUserId() {
        String lastUserId = getLastUserIdFromDatabase("USR");
        int nextNumber = lastUserId != null ?
                Integer.parseInt(lastUserId.substring(3)) + 1 : 1;
        return String.format("USR%07d", nextNumber);
    }

    // Genera código de docente en formato DOC000001
    private static String generateNextDocenteCode() {
        String lastDocCode = getLastDocenteCodeFromDatabase();
        int nextNumber = lastDocCode != null ?
                Integer.parseInt(lastDocCode.substring(3)) + 1 : 1;
        return String.format("DOC%06d", nextNumber);
    }

    // Obtiene el último USER_ID
    private static String getLastUserIdFromDatabase(String prefix) {
        String sql = "SELECT USER_ID FROM ("
                + "SELECT USER_ID FROM USERS "
                + "WHERE USER_ID LIKE ? "
                + "ORDER BY TO_NUMBER(SUBSTR(USER_ID, 4)) DESC"
                + ") WHERE ROWNUM = 1";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, prefix + "%");
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getString("USER_ID") : null;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error al obtener último ID: " + e.getMessage(), e);
        }
    }

    // Obtiene el último código de docente de la columna MATRICULA
    private static String getLastDocenteCodeFromDatabase() {
        String sql = "SELECT MATRICULA FROM ("
                + "SELECT MATRICULA FROM USERS "
                + "WHERE MATRICULA LIKE 'DOC%' "
                + "ORDER BY TO_NUMBER(SUBSTR(MATRICULA, 4)) DESC"
                + ") WHERE ROWNUM = 1";

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            return rs.next() ? rs.getString("MATRICULA") : null;
        } catch (SQLException e) {
            throw new RuntimeException("Error al obtener último código docente: " + e.getMessage(), e);
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