package com.utez.calendario.models;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.ArrayList;

public class User {
    private String userId;
    private String matricula;  // NUEVO CAMPO
    private String email;
    private String firstName;
    private String lastName;
    private String password;
    private Role role;
    private char active;
    private LocalDateTime createdDate;
    private LocalDateTime lastLogin;

    private static Connection getConnection() throws SQLException {

        return com.utez.calendario.config.DatabaseConfig.getConnection();

    }

    /**
     * Cuenta el total de usuarios registrados
     */
    public static int getTotalUsersCount() {
        int count = 0;
        String sql = "SELECT COUNT(*) FROM users";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            if (rs.next()) {
                count = rs.getInt(1);
            }
        } catch (SQLException e) {
            System.err.println("Error al contar usuarios: " + e.getMessage());
        }

        return count;
    }

    /*
     * Cuenta estudiantes activos
     */
    public static int getActiveStudentsCount() {
        int count = 0;
        String sql = "SELECT COUNT(*) FROM users WHERE role = 'alumno' AND active = 'Y'";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            if (rs.next()) {
                count = rs.getInt(1);
            }
        } catch (SQLException e) {
            System.err.println("Error al contar estudiantes activos: " + e.getMessage());
        }

        return count;
    }

    /**
     * Cuenta eventos para el mes actual
     */
    public static int getEventsForCurrentMonth() {
        int count = 0;
        String sql = "SELECT COUNT(*) FROM events WHERE MONTH(START_DATE) = MONTH(CURRENT_DATE()) " +
                "AND YEAR(START_DATE) = YEAR(CURRENT_DATE())";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            if (rs.next()) {
                count = rs.getInt(1);
            }
        } catch (SQLException e) {
            System.err.println("Error al contar eventos del mes: " + e.getMessage());
        }

        return count;
    }
    /**
     * Cuenta eventos próximos (en los siguientes 7 días)
     */
    public static int getUpcomingEventsCount() {
        int count = 0;
        String sql = "SELECT COUNT(*) FROM events WHERE START_DATE BETWEEN CURRENT_DATE() " +
                "AND DATE_ADD(CURRENT_DATE(), INTERVAL 7 DAY)";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            if (rs.next()) {
                count = rs.getInt(1);
            }
        } catch (SQLException e) {
            System.err.println("Error al contar eventos próximos: " + e.getMessage());
        }

        return count;
    }

    /**
     * Cuenta calendarios activos
     */
    public static int getActiveCalendarsCount() {
        int count = 0;
        String sql = "SELECT COUNT(*) FROM calendars WHERE active = 'Y'";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            if (rs.next()) {
                count = rs.getInt(1);
            }
        } catch (SQLException e) {
            System.err.println("Error al contar calendarios activos: " + e.getMessage());
        }

        return count;
    }

    public static List<User> getAllUsers() {
        List<User> users = new ArrayList<>();
        String sql = "SELECT USER_ID, MATRICULA, EMAIL, FIRST_NAME, LAST_NAME, ROLE, ACTIVE, CREATED_DATE, LAST_LOGIN FROM users";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {

                User user = new User();

                user.setUserId(rs.getString("USER_ID"));
                user.setMatricula(rs.getString("MATRICULA"));
                user.setEmail(rs.getString("EMAIL"));
                user.setFirstName(rs.getString("FIRST_NAME"));
                user.setLastName(rs.getString("LAST_NAME"));
                user.setRole(Role.fromString(rs.getString("ROLE")));
                user.setActive(rs.getString("ACTIVE").charAt(0));

                Timestamp createdDate = rs.getTimestamp("CREATED_DATE");
                if (createdDate != null) {
                    user.setCreatedDate(createdDate.toLocalDateTime());
                }

                Timestamp lastLogin = rs.getTimestamp("LAST_LOGIN");
                if (lastLogin != null) {
                    user.setLastLogin(lastLogin.toLocalDateTime());
                }

                users.add(user);
            }
        } catch (SQLException e) {
            System.err.println("Error al obtener usuarios: " + e.getMessage());
        }

        return users;
    }

    public boolean toggleActive() {
        char newStatus = this.isActive() ? 'N' : 'Y';  // Cambia el estado de el usuario seleccionado pa desactivarlo

        String sql = "UPDATE users SET ACTIVE = ? WHERE USER_ID = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, String.valueOf(newStatus));
            stmt.setString(2, this.getUserId());

            int rowsAffected = stmt.executeUpdate();
            if (rowsAffected > 0) {
                // Actualiza el objeto local también
                this.setActive(newStatus);
                return true;
            }
        } catch (SQLException e) {
            System.err.println("Error al cambiar estado del usuario: " + e.getMessage());
        }

        return false;
    }

    // Enum para los roles UTEZ
    public enum Role {
        ALUMNO("alumno"),
        DOCENTE("docente"),
        ADMIN("admin");

        private final String value;

        Role(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        public static Role fromString(String text) {
            for (Role role : Role.values()) {
                if (role.value.equalsIgnoreCase(text)) {
                    return role;
                }
            }
            throw new IllegalArgumentException("No se encontró rol con texto: " + text);
        }

        public String getDisplayName() {
            switch (this) {
                case ALUMNO: return "Estudiante";
                case DOCENTE: return "Docente";
                case ADMIN: return "Administrador";
                default: return value;
            }
        }
    }

    // Constructores
    public User() {}

    public User(String userId, String matricula, String email, String firstName, String lastName,
                String password, Role role) {
        this.userId = userId;
        this.matricula = matricula;
        this.email = email;
        this.firstName = firstName;
        this.lastName = lastName;
        this.password = password;
        this.role = role;
        this.active = 'Y';
        this.createdDate = LocalDateTime.now();
    }

    // Getters y Setters
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getMatricula() { return matricula; }
    public void setMatricula(String matricula) { this.matricula = matricula; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }

    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }

    public char getActive() { return active; }
    public void setActive(char active) { this.active = active; }

    public LocalDateTime getCreatedDate() { return createdDate; }
    public void setCreatedDate(LocalDateTime createdDate) { this.createdDate = createdDate; }

    public LocalDateTime getLastLogin() { return lastLogin; }
    public void setLastLogin(LocalDateTime lastLogin) { this.lastLogin = lastLogin; }

    // Métodos utilitarios
    public String getFullName() {
        return firstName + " " + lastName;
    }

    public boolean isActive() {
        return active == 'Y';
    }

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }

    public boolean isTeacher() {
        return role == Role.DOCENTE;
    }

    public boolean isStudent() {
        return role == Role.ALUMNO;
    }

    /**
     * Obtiene el identificador de display (matrícula para estudiantes, email para docentes)
     */
    public String getDisplayId() {
        if (isStudent() && matricula != null) {
            return matricula;
        }
        return email.split("@")[0];
    }

    /**
     * Obtiene información completa del usuario para mostrar
     */
    public String getDisplayInfo() {
        return String.format("%s (%s) - %s",
                getFullName(),
                getDisplayId(),
                role.getDisplayName());
    }

    /**
     * Determina si es un email de estudiante UTEZ basado en el patrón
     */
    public static boolean isStudentEmail(String email) {
        return email.matches("\\d{7}[a-z]{2}\\d{3}@utez\\.edu\\.mx");
    }

    /**
     * Determina si es un email de docente UTEZ basado en el patrón
     */
    public static boolean isTeacherEmail(String email) {
        return email.matches("[a-z]+@utez\\.edu\\.mx") && !isStudentEmail(email);
    }

    /**
     * Extrae la matrícula del email si es estudiante
     */
    public static String extractMatriculaFromEmail(String email) {
        if (isStudentEmail(email)) {
            return email.split("@")[0];
        }
        return null;
    }

    /**
     * Valida que el email sea institucional UTEZ
     */
    public static boolean isValidUtezEmail(String email) {
        return email.endsWith("@utez.edu.mx") && (isStudentEmail(email) || isTeacherEmail(email));
    }

    /**
     * Obtiene la fecha de último login formateada
     */
    public String getFormattedLastLogin() {
        if (lastLogin == null) {
            return "Nunca";
        }
        return lastLogin.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
    }

    @Override
    public String toString() {
        return "User{" +
                "userId='" + userId + '\'' +
                ", matricula='" + matricula + '\'' +
                ", email='" + email + '\'' +
                ", fullName='" + getFullName() + '\'' +
                ", role=" + role +
                ", active=" + active +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        User user = (User) o;
        return userId != null ? userId.equals(user.userId) : user.userId == null;
    }

    @Override
    public int hashCode() {
        return userId != null ? userId.hashCode() : 0;
    }
}