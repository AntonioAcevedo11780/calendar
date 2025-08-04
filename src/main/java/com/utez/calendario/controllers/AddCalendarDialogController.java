package com.utez.calendario.controllers;

import com.utez.calendario.models.Calendar;
import com.utez.calendario.services.AuthService;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

public class AddCalendarDialogController {
    @FXML private TextField nameField;
    @FXML private TextArea descriptionField;
    @FXML private ComboBox<String> predefinedColorBox;
    @FXML private ColorPicker colorPicker;
    @FXML private Label colorPreviewLabel;
    @FXML private Button saveButton;
    @FXML private Button cancelButton;
    @FXML private Button closeButton;

    private Stage dialogStage;
    private boolean calendarCreated = false;

    @FXML
    private void initialize() {
        // Inicializar colores predefinidos
        predefinedColorBox.getItems().addAll(
                "Azul", "Rojo", "Verde", "Púrpura", "Naranja", "Amarillo", "Turquesa"
        );
        predefinedColorBox.setValue("Azul");

        // Asociar colores con el ColorPicker
        predefinedColorBox.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                Color selectedColor = null;
                switch (newVal) {
                    case "Rojo":
                        selectedColor = Color.valueOf("#E74C3C");
                        break;
                    case "Azul":
                        selectedColor = Color.valueOf("#3498DB");
                        break;
                    case "Verde":
                        selectedColor = Color.valueOf("#2ECC71");
                        break;
                    case "Púrpura":
                        selectedColor = Color.valueOf("#9B59B6");
                        break;
                    case "Naranja":
                        selectedColor = Color.valueOf("#E67E22");
                        break;
                    case "Amarillo":
                        selectedColor = Color.valueOf("#F1C40F");
                        break;
                    case "Turquesa":
                        selectedColor = Color.valueOf("#1ABC9C");
                        break;
                    default:
                        selectedColor = Color.valueOf("#3498DB");
                        break;
                }

                if (selectedColor != null) {
                    colorPicker.setValue(selectedColor);
                    updateColorPreview(selectedColor);
                }
            }
        });

        // Listener para el ColorPicker
        colorPicker.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                updateColorPreview(newVal);
            }
        });

        // Establecer color inicial
        Color initialColor = Color.valueOf("#3498DB");
        colorPicker.setValue(initialColor);
        updateColorPreview(initialColor);
    }

    /**
     * Actualiza la vista previa del color seleccionado
     */
    private void updateColorPreview(Color color) {
        if (colorPreviewLabel != null && color != null) {
            String colorHex = String.format("#%02X%02X%02X",
                    (int)(color.getRed() * 255),
                    (int)(color.getGreen() * 255),
                    (int)(color.getBlue() * 255));

            // Determinar si usar texto blanco o negro según la luminancia del color
            String textColor = isLightColor(color) ? "#000000" : "#ffffff";

            colorPreviewLabel.setStyle(String.format(
                    "-fx-background-color: %s; -fx-text-fill: %s; -fx-padding: 5 10 5 10; -fx-background-radius: 3;",
                    colorHex, textColor
            ));
            colorPreviewLabel.setText("Vista previa del color: " + colorHex);
        }
    }

    /**
     * Determina si un color es claro (necesita texto negro)
     */
    private boolean isLightColor(Color color) {
        // Calcular luminancia relativa
        double luminance = 0.299 * color.getRed() + 0.587 * color.getGreen() + 0.114 * color.getBlue();
        return luminance > 0.5;
    }

    public void setDialogStage(Stage dialogStage) {
        this.dialogStage = dialogStage;
    }

    @FXML
    private void handleSave() {
        String userId = AuthService.getInstance().getCurrentUser().getUserId();
        String name = nameField.getText().trim();
        String description = descriptionField.getText().trim();

        // Validaciones
        if (name.isEmpty()) {
            showMessage("El nombre del calendario no puede estar vacío", true);
            return;
        }

        if (name.length() > 50) {
            showMessage("El nombre del calendario no puede exceder 50 caracteres", true);
            return;
        }

        // Verificar límite de 5 calendarios personalizados
        try {
            if (Calendar.getUserCustomCalendarsCount(userId) >= 5) {
                showMessage("Has alcanzado el límite de 5 calendarios personalizados", true);
                return;
            }
        } catch (Exception e) {
            System.err.println("Error verificando límite de calendarios: " + e.getMessage());
            showMessage("Error al verificar el límite de calendarios", true);
            return;
        }

        // Obtener el color seleccionado en formato hexadecimal
        String colorHex = String.format("#%02X%02X%02X",
                (int)(colorPicker.getValue().getRed() * 255),
                (int)(colorPicker.getValue().getGreen() * 255),
                (int)(colorPicker.getValue().getBlue() * 255));

        // Crear el calendario
        try {
            boolean success = Calendar.createCustomCalendar(userId, name, colorHex);

            if (success) {
                showMessage("Calendario '" + name + "' creado con éxito", false);
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
                showMessage("Error al crear el calendario. Verifica que el nombre no esté duplicado.", true);
            }
        } catch (Exception e) {
            System.err.println("Error creando calendario: " + e.getMessage());
            e.printStackTrace();
            showMessage("Error al crear el calendario: " + e.getMessage(), true);
        }
    }

    /**
     * Muestra un mensaje al usuario
     */
    private void showMessage(String message, boolean isError) {
        Alert alert = new Alert(isError ? Alert.AlertType.ERROR : Alert.AlertType.INFORMATION);
        alert.setTitle(isError ? "Error" : "Éxito");
        alert.setHeaderText(null);
        alert.setContentText(message);

        // Configurar el stage padre si existe
        if (dialogStage != null) {
            alert.initOwner(dialogStage);
        }

        alert.showAndWait();
    }

    @FXML
    private void handleCancel() {
        if (dialogStage != null) {
            dialogStage.close();
        }
    }

    @FXML
    private void handleClose() {
        if (dialogStage != null) {
            dialogStage.close();
        }
    }

    /**
     * Indica si se creó exitosamente un calendario
     */
    public boolean isCalendarCreated() {
        return calendarCreated;
    }

    /**
     * Limpia todos los campos del formulario
     */
    private void clearForm() {
        if (nameField != null) {
            nameField.clear();
        }
        if (descriptionField != null) {
            descriptionField.clear();
        }
        if (predefinedColorBox != null) {
            predefinedColorBox.setValue("Azul");
        }
        if (colorPicker != null) {
            colorPicker.setValue(Color.valueOf("#3498DB"));
        }
    }

    /**
     * Configura el foco inicial en el campo de nombre
     */
    public void setInitialFocus() {
        if (nameField != null) {
            javafx.application.Platform.runLater(() -> nameField.requestFocus());
        }
    }
}