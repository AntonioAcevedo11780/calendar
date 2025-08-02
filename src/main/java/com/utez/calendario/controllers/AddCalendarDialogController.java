package com.utez.calendario.controllers;

import com.utez.calendario.models.Calendar;
import com.utez.calendario.services.AuthService;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;

public class AddCalendarDialogController {
    @FXML private TextField calendarNameField;
    @FXML private TextArea descriptionField;
    @FXML private RadioButton redColorRadio;
    @FXML private RadioButton blueColorRadio;
    @FXML private RadioButton greenColorRadio;
    @FXML private RadioButton purpleColorRadio;
    @FXML private RadioButton orangeColorRadio;
    @FXML private Button saveButton;
    @FXML private Button cancelButton;
    @FXML private Button closeButton;
    @FXML private Label messageLabel;

    private Stage dialogStage;
    private boolean calendarCreated = false;

    @FXML
    private void initialize() {
        // Inicialización de componentes
    }

    public void setDialogStage(Stage dialogStage) {
        this.dialogStage = dialogStage;
    }

    @FXML
    private void handleSave() {
        String userId = AuthService.getInstance().getCurrentUser().getUserId();
        String name = calendarNameField.getText().trim();

        if (name.isEmpty()) {
            showMessage("El nombre del calendario no puede estar vacío", true);
            return;
        }

        // Verificar si ya alcanzó el límite de 5 calendarios personalizados
        if (Calendar.getUserCustomCalendarsCount(userId) >= 5) {
            showMessage("Has alcanzado el límite de 5 calendarios personalizados", true);
            return;
        }

        // Obtener el color seleccionado
        String color = getSelectedColor();

        // Crear el calendario
        boolean success = Calendar.createCustomCalendar(userId, name, color);

        if (success) {
            showMessage("Calendario creado con éxito", false);
            calendarCreated = true;

            // Cerrar automáticamente después de 1.5 segundos
            new Thread(() -> {
                try {
                    Thread.sleep(1500);
                    javafx.application.Platform.runLater(this::handleClose);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }).start();
        } else {
            showMessage("Error al crear el calendario. Por favor, inténtalo de nuevo.", true);
        }
    }

    private String getSelectedColor() {
        if (redColorRadio.isSelected()) return "#E74C3C"; // Rojo
        if (blueColorRadio.isSelected()) return "#3498DB"; // Azul
        if (greenColorRadio.isSelected()) return "#2ECC71"; // Verde
        if (purpleColorRadio.isSelected()) return "#9B59B6"; // Púrpura
        if (orangeColorRadio.isSelected()) return "#E67E22"; // Naranja

        // Color por defecto si ninguno está seleccionado
        return "#3498DB";
    }

    private void showMessage(String message, boolean isError) {
        messageLabel.setText(message);
        messageLabel.setVisible(true);

        if (isError) {
            messageLabel.getStyleClass().remove("success-message");
            messageLabel.getStyleClass().add("error-message");
        } else {
            messageLabel.getStyleClass().remove("error-message");
            messageLabel.getStyleClass().add("success-message");
        }
    }

    @FXML
    private void handleCancel() {
        dialogStage.close();
    }

    @FXML
    private void handleClose() {
        dialogStage.close();
    }

    public boolean isCalendarCreated() {
        return calendarCreated;
    }
}