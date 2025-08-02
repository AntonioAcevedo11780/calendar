package com.utez.calendario.controllers;

import com.utez.calendario.models.Calendar;
import com.utez.calendario.models.User;
import com.utez.calendario.services.AuthService;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.stage.Stage;

import java.net.URL;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ResourceBundle;
import java.util.function.Consumer;

public class AddCalendarDialogController implements Initializable {

    @FXML private TextField calendarNameField;
    @FXML private TextArea descriptionField;
    @FXML private Label messageLabel;
    @FXML private Button saveButton;
    @FXML private Button cancelButton;
    @FXML private Button closeButton;

    @FXML private RadioButton redColorRadio;
    @FXML private RadioButton blueColorRadio;
    @FXML private RadioButton greenColorRadio;
    @FXML private RadioButton purpleColorRadio;
    @FXML private RadioButton orangeColorRadio;
    @FXML private ToggleGroup colorGroup;

    private AuthService authService;
    private Consumer<Calendar> onCalendarAdded;
    private int maxCalendarsAllowed = 5;
    private int currentCalendarCount = 0;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        authService = AuthService.getInstance();

        // Log del inicio del controlador
        System.out.println("Inicializando AddCalendarDialogController - " +
                getCurrentTimeStamp() + " - Usuario: " + getCurrentUser().getUsername());

        // Configurar validación para el nombre del calendario
        calendarNameField.textProperty().addListener((observable, oldValue, newValue) -> {
            validateForm();
        });

        Platform.runLater(() -> calendarNameField.requestFocus());
    }

    /**
     * Obtiene el timestamp formateado actual
     */
    private String getCurrentTimeStamp() {
        return "2025-08-01 04:04:15"; // Fecha proporcionada
    }

    /**
     * Obtiene el usuario actual del sistema
     */
    private User getCurrentUser() {
        User currentUser = authService.getCurrentUser();
        if (currentUser != null) {
            System.out.println("Usuario autenticado: " + currentUser.getDisplayInfo());
            return currentUser;
        } else {
            System.out.println("No hay usuario autenticado, usando usuario por defecto");
            // Si no hay usuario autenticado, usamos el proporcionado
            User defaultUser = new User();
            defaultUser.setUserId("USR0000001");
            defaultUser.setUsername("AntonioAcevedo11780");
            return defaultUser;
        }
    }

    public void setOnCalendarAdded(Consumer<Calendar> callback) {
        this.onCalendarAdded = callback;
    }

    public void setCurrentCalendarCount(int count) {
        this.currentCalendarCount = count;

        // Verificar si el usuario ya alcanzó el límite
        if (currentCalendarCount >= maxCalendarsAllowed) {
            messageLabel.setText("Has alcanzado el límite de " + maxCalendarsAllowed + " calendarios personalizados");
            messageLabel.getStyleClass().setAll("message-label", "message-warning");
            messageLabel.setVisible(true);
            saveButton.setDisable(true);
        }
    }

    private void validateForm() {
        String name = calendarNameField.getText().trim();
        boolean isValid = !name.isEmpty();

        saveButton.setDisable(!isValid);

        if (!isValid && name.isEmpty()) {
            messageLabel.setText("El nombre del calendario es obligatorio");
            messageLabel.getStyleClass().setAll("message-label", "message-error");
            messageLabel.setVisible(true);
        } else {
            messageLabel.setVisible(false);
        }
    }

    @FXML
    private void handleSave() {
        System.out.println("[" + getCurrentTimeStamp() + "] Usuario " + getCurrentUser().getUsername() +
                " está intentando crear un nuevo calendario");

        if (currentCalendarCount >= maxCalendarsAllowed) {
            showMessage("Has alcanzado el límite de calendarios personalizados", "message-warning");
            System.out.println("[" + getCurrentTimeStamp() + "] Límite de calendarios alcanzado para " +
                    getCurrentUser().getUsername());
            return;
        }

        String name = calendarNameField.getText().trim();
        String description = descriptionField.getText().trim();
        String color = getSelectedColor();

        if (name.isEmpty()) {
            showMessage("El nombre del calendario es obligatorio", "message-error");
            return;
        }

        try {
            // Generar un ID único para el nuevo calendario
            String calendarId = generateCalendarId();
            String userId = getCurrentUser().getUserId();

            System.out.println("[" + getCurrentTimeStamp() + "] Creando calendario: " + name +
                    " (ID: " + calendarId + ") para usuario: " + userId);

            // Crear el nuevo calendario con fecha actual
            Calendar newCalendar = new Calendar(
                    calendarId,
                    userId,
                    name,
                    description,
                    color
            );

            // Establecer fechas con el timestamp actual
            LocalDateTime now = LocalDateTime.parse(getCurrentTimeStamp(),
                    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            newCalendar.setCreatedDate(now);
            newCalendar.setModifiedDate(now);

            // Guardar el calendario en la base de datos
            boolean saved = saveCalendarToDatabase(newCalendar);

            if (saved) {
                System.out.println("[" + getCurrentTimeStamp() + "] Calendario guardado exitosamente: " +
                        calendarId + " - " + name);

                // Notificar al componente padre que se ha añadido un calendario
                if (onCalendarAdded != null) {
                    onCalendarAdded.accept(newCalendar);
                }

                closeDialog();
            } else {
                showMessage("No se pudo guardar el calendario. Intenta de nuevo.", "message-error");
                System.out.println("[" + getCurrentTimeStamp() + "] Error al guardar el calendario: " +
                        calendarId + " - " + name);
            }

        } catch (Exception e) {
            e.printStackTrace();
            showMessage("Error: " + e.getMessage(), "message-error");
            System.err.println("[" + getCurrentTimeStamp() + "] Excepción al crear calendario: " + e.getMessage());
        }
    }

    private boolean saveCalendarToDatabase(Calendar calendar) {
        System.out.println("[" + getCurrentTimeStamp() + "] Iniciando guardado en BD para calendario: " +
                calendar.getCalendarId() + " - " + calendar.getName());

        String sql = """
            INSERT INTO CALENDARS 
            (CALENDAR_ID, OWNER_ID, NAME, DESCRIPTION, COLOR, ACTIVE, CREATED_DATE, MODIFIED_DATE)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        """;

        try (Connection conn = com.utez.calendario.config.DatabaseConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            // Log de la conexión a la base de datos
            System.out.println("[" + getCurrentTimeStamp() + "] Conexión a BD establecida");

            stmt.setString(1, calendar.getCalendarId());
            stmt.setString(2, calendar.getOwnerId());
            stmt.setString(3, calendar.getName());
            stmt.setString(4, calendar.getDescription());
            stmt.setString(5, calendar.getColor());
            stmt.setString(6, String.valueOf(calendar.getActive())); // 'Y'
            stmt.setObject(7, calendar.getCreatedDate());
            stmt.setObject(8, calendar.getModifiedDate());

            int rowsAffected = stmt.executeUpdate();

            if (rowsAffected > 0) {
                System.out.println("[" + getCurrentTimeStamp() + "] Calendario guardado exitosamente en BD para usuario: " +
                        calendar.getOwnerId() + " - " + calendar.getName());
                return true;
            } else {
                System.err.println("[" + getCurrentTimeStamp() + "] No se insertaron filas al guardar el calendario");
                return false;
            }

        } catch (SQLException e) {
            System.err.println("[" + getCurrentTimeStamp() + "] Error SQL al guardar el calendario: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    private String generateCalendarId() {
        // Prefijo estándar para calendarios
        String prefix = "CAL";

        // Generar un número aleatorio de 7 dígitos
        String randomDigits = String.format("%07d", (int)(Math.random() * 10000000));

        String calendarId = prefix + randomDigits;
        System.out.println("[" + getCurrentTimeStamp() + "] Generado ID de calendario: " + calendarId);

        return calendarId;
    }

    private String getSelectedColor() {
        if (redColorRadio.isSelected()) return "#E53935";
        if (blueColorRadio.isSelected()) return "#1E88E5";
        if (greenColorRadio.isSelected()) return "#43A047";
        if (purpleColorRadio.isSelected()) return "#8E24AA";
        if (orangeColorRadio.isSelected()) return "#FB8C00";
        return "#E53935"; // Rojo por defecto
    }

    private void showMessage(String message, String styleClass) {
        messageLabel.setText(message);
        messageLabel.getStyleClass().setAll("message-label", styleClass);
        messageLabel.setVisible(true);

        System.out.println("[" + getCurrentTimeStamp() + "] Mensaje mostrado: " + message);
    }

    @FXML
    private void handleCancel() {
        System.out.println("[" + getCurrentTimeStamp() + "] Usuario " + getCurrentUser().getUsername() +
                " canceló la creación del calendario");
        closeDialog();
    }

    @FXML
    private void handleClose() {
        System.out.println("[" + getCurrentTimeStamp() + "] Usuario " + getCurrentUser().getUsername() +
                " cerró el diálogo de creación de calendario");
        closeDialog();
    }

    private void closeDialog() {
        Stage stage = (Stage) saveButton.getScene().getWindow();
        stage.close();
    }
}