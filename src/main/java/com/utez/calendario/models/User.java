package com.utez.calendario.models;
import com.utez.calendario.config.DatabaseConfig;
import com.utez.calendario.controllers.AdminOverviewController;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;

import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;

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

    private static AdminOverviewController dashboardController;

    // Datos para la consulta
    public static class DashboardData {
        public int totalUsers;
        public int activeStudents;
        public int eventsThisMonth;
        public int upcomingEvents;
        public int activeCalendars;
    }

    // Una sola consulta para obtener datos del dashboard
    public static DashboardData getDashboardData() {
        DashboardData data = new DashboardData();

        // ✅ Consulta optimizada usando las tablas que SÍ existen
        String sql = """
        SELECT 
                (SELECT COUNT(*) FROM users WHERE role != 'admin') as total_users,
                (SELECT COUNT(*) FROM users WHERE role = 'alumno' AND active = 'Y') as active_students,
                (SELECT COUNT(*) FROM EVENTS WHERE ACTIVE = 'Y' 
                 AND EXTRACT(MONTH FROM START_DATE) = EXTRACT(MONTH FROM SYSDATE) 
                 AND EXTRACT(YEAR FROM START_DATE) = EXTRACT(YEAR FROM SYSDATE)) as events_month,
                (SELECT COUNT(*) FROM EVENTS WHERE ACTIVE = 'Y' 
                 AND START_DATE > SYSDATE) as upcoming_events,
                (SELECT COUNT(*) FROM CALENDARS WHERE ACTIVE = 'Y') as active_calendars
            FROM DUAL
        """;

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            if (rs.next()) {
                data.totalUsers = rs.getInt("total_users");
                data.activeStudents = rs.getInt("active_students");
                data.eventsThisMonth = rs.getInt("events_month");
                data.upcomingEvents = rs.getInt("upcoming_events");
                data.activeCalendars = rs.getInt("active_calendars");
            }
        } catch (SQLException e) {
            System.err.println("Error al obtener datos del dashboard: " + e.getMessage());
        }

        return data;
    }

    public static List<User> getUsers(int page, int pageSize, boolean includeAdmins) {
        List<User> users = new ArrayList<>();
        int offset = page * pageSize;

        String filterClause = includeAdmins ? "" : "WHERE u.role != 'admin'";
        String limitClause = (page >= 0 && pageSize > 0) ?
                "WHERE rn BETWEEN ? AND ?" : "WHERE ROWNUM <= 1000";

        String sql = String.format("""
            SELECT * FROM (
                SELECT u.*, ROW_NUMBER() OVER (ORDER BY CREATED_DATE DESC) as rn
                FROM users u
                %s
            ) %s
            """, filterClause, limitClause);

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            if (page >= 0 && pageSize > 0) {

                stmt.setInt(1, offset + 1);
                stmt.setInt(2, offset + pageSize);

            }

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    User user = mapResultSetToUser(rs);
                    users.add(user);
                }
            }
        } catch (SQLException e) {
            System.err.println("Error al obtener usuarios: " + e.getMessage());
        }

        return users;

    }

    public boolean searchEmail(String email) {
        String sql = "SELECT email FROM users WHERE email = ?";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, email);

            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next(); // Devuelve true si hay al menos un resultado
            }

        } catch (SQLException e) {
            System.err.println("Error al buscar el email: " + e.getMessage());
            return false; // Devuelve false en caso de error
        }
    }

    // Helpers
    public static List<User> getAllUsers() {
        return getUsers(-1, -1, true);  // Sin paginación, con admins
    }

    public static List<User> getUsersPaginated(int page, int pageSize) {
        return getUsers(page, pageSize, false);  // Con paginación, sin admins
    }

    public static void setDashboardController(AdminOverviewController controller) {
        dashboardController = controller;
    }

    private static User mapResultSetToUser(ResultSet rs) throws SQLException {

        User user = new User();

        // Mapear campos básicos
        user.setUserId(rs.getString("USER_ID"));
        user.setMatricula(rs.getString("MATRICULA"));
        user.setEmail(rs.getString("EMAIL"));
        user.setFirstName(rs.getString("FIRST_NAME"));
        user.setLastName(rs.getString("LAST_NAME"));
        user.setRole(Role.fromString(rs.getString("ROLE")));
        user.setActive(rs.getString("ACTIVE").charAt(0));

        // Mapear fechas con verificacion de null
        Timestamp createdDate = rs.getTimestamp("CREATED_DATE");
        if (createdDate != null) {
            user.setCreatedDate(createdDate.toLocalDateTime());
        }

        Timestamp lastLogin = rs.getTimestamp("LAST_LOGIN");
        if (lastLogin != null) {
            user.setLastLogin(lastLogin.toLocalDateTime());
        }

        return user;
    }

    public boolean toggleActive() {
        char oldStatus = this.isActive() ? 'Y' : 'N';
        char newStatus = this.isActive() ? 'N' : 'Y';

        // Consulta optimizada para cambiar el estado del usuario
        String sql = "UPDATE users SET ACTIVE = ? WHERE USER_ID = ?";

        try (Connection conn = getConnection()) {

            conn.setAutoCommit(false);

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, String.valueOf(newStatus));
                stmt.setString(2, this.getUserId());

                int rowsAffected = stmt.executeUpdate();

                if (rowsAffected > 0) {
                    conn.commit();
                    this.setActive(newStatus);

                    // Actualiza el dashboard en segundo plano
                    if (dashboardController != null) {
                        boolean wasActivated = (oldStatus == 'N' && newStatus == 'Y');

                        // La misma logica de ejecutar los threaads enn segundo planno
                        CompletableFuture.runAsync(() -> {
                            try {
                                dashboardController.onUserToggledWithRole(wasActivated, this.getRole());
                            } catch (Exception e) {
                                System.err.println("Error actualizando dashboard: " + e.getMessage());
                            }
                        });
                    }

                    return true;
                } else {

                    conn.rollback();
                    return false;

                }
            } catch (SQLException e) {
                conn.rollback();
                throw e;
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
        if (email == null) return false;
        String normalized = email.toLowerCase();
        // Formato actual: 5 dígitos + 2 letras + 3 dígitos
        return normalized.matches("\\d{5}[a-z]{2}\\d{3}@utez\\.edu\\.mx");
    }

    /**
     * Determina si es un email de docente UTEZ basado en el patrón
     */
    public static boolean isTeacherEmail(String email) {
        if (email == null) return false;
        String normalized = email.toLowerCase();
        // Verificar dominio primero
        if (!normalized.endsWith("@utez.edu.mx")) return false;
        String localPart = normalized.split("@")[0];
        // Patrón flexible para docentes
        boolean validFormat = localPart.matches("[a-z0-9_.-]{3,30}");
        return validFormat && !isStudentEmail(normalized);
    }

    /**
     * Extrae la matrícula del email si es estudiante
     */
    public static String extractMatriculaFromEmail(String email) {
        if (email == null) return null;
        String normalized = email.toLowerCase();
        if (isStudentEmail(normalized)) {
            return normalized.split("@")[0];
        }
        return null;
    }

    /**
     * Valida que el email sea institucional UTEZ
     */
    public static boolean isValidUtezEmail(String email) {
        if (email == null || email.isBlank()) return false;
        String normalized = email.toLowerCase();
        return normalized.endsWith("@utez.edu.mx") &&
                (isStudentEmail(normalized) || isTeacherEmail(normalized));
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

    public ObservableList<PieChart.Data> generateRolePieData() {///Generar la grafica de pastel
        ObservableList<PieChart.Data> pieData = FXCollections.observableArrayList();
        /// /todos menos el admin xd
        String sql = "SELECT ROLE, COUNT(*) AS cantidad FROM USERS WHERE LOWER(ROLE) != 'admin' GROUP BY ROLE";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                String role = rs.getString("ROLE");
                int cantidad = rs.getInt("cantidad");
                pieData.add(new PieChart.Data(role, cantidad));
            }

        } catch (SQLException e) {
            System.err.println("Error al obtener datos para la gráfica: " + e.getMessage());
        }

        return pieData;
    }

    public ObservableList<XYChart.Data<String, Number>> getActiveForEducatorEvents() {
        ObservableList<XYChart.Data<String, Number>> datos = FXCollections.observableArrayList();

        String sql = """
        SELECT u.FIRST_NAME || ' ' || u.LAST_NAME AS nombre_docente,
               COUNT(e.EVENT_ID) AS eventos_activos
        FROM EVENTS e
        JOIN USERS u ON e.CREATOR_ID = u.USER_ID
        WHERE LOWER(u.ROLE) = 'docente'
          AND e.START_DATE > CURRENT_DATE
        GROUP BY u.FIRST_NAME || ' ' || u.LAST_NAME
        ORDER BY eventos_activos DESC
        """;

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                String nombre = rs.getString("nombre_docente");
                int eventos = rs.getInt("eventos_activos");
                datos.add(new XYChart.Data<>(nombre, eventos));
            }

        } catch (SQLException e) {
            System.err.println("Error obteniendo datos del gráfico: " + e.getMessage());
            e.printStackTrace();
        }

        return datos;
    }
}


