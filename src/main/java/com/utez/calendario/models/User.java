package com.utez.calendario.models;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

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