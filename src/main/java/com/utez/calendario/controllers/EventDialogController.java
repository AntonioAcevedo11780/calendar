package com.utez.calendario.controllers;

import com.utez.calendario.models.Event;
import com.utez.calendario.services.AuthService;
import com.utez.calendario.services.EventService;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.net.URL;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;

public class EventDialogController implements Initializable {

    @FXML private VBox eventFormContainer;
    @FXML private VBox eventListContainer;
    @FXML private TextField titleField;
    @FXML private TextArea descriptionArea;
    @FXML private TextField locationField;
    @FXML private ComboBox<String> calendarComboBox;
    @FXML private DatePicker datePicker;
    @FXML private TextField startTimeField;
    @FXML private TextField endTimeField;
    @FXML private CheckBox allDayCheckBox;
    @FXML private Button saveButton;
    @FXML private Button updateButton;
    @FXML private Button deleteButton;
    @FXML private Button cancelButton;
    @FXML private Label modeLabel;
    @FXML private ListView<String> eventsListView;

    private EventService eventService;
    private AuthService authService;
    private LocalDate selectedDate;
    private Event currentEvent;
    private Runnable onEventChanged;
    private String mode;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        eventService = EventService.getInstance();
        authService = AuthService.getInstance();
        setupComponents();
        setupEventHandlers();
    }

    private void setupComponents() {
        calendarComboBox.getItems().addAll(
                "Mis Clases",
                "Tareas y Proyectos",
                "Personal",
                "Exámenes"
        );
        calendarComboBox.setValue("Mis Clases");
        startTimeField.setPromptText("08:00");
        endTimeField.setPromptText("10:00");
        descriptionArea.setWrapText(true);
        descriptionArea.setPrefRowCount(3);
        eventsListView.getSelectionModel().selectedItemProperty().addListener(
                (obs, oldVal, newVal) -> {
                    if (newVal != null) {
                        loadEventForEdit(newVal);
                    }
                }
        );
    }

    private void setupEventHandlers() {
        allDayCheckBox.setOnAction(e -> {
            boolean allDay = allDayCheckBox.isSelected();
            startTimeField.setDisable(allDay);
            endTimeField.setDisable(allDay);
            if (allDay) {
                startTimeField.setText("00:00");
                endTimeField.setText("23:59");
            }
        });

        saveButton.setOnAction(e -> handleSave());
        updateButton.setOnAction(e -> handleUpdate());
        deleteButton.setOnAction(e -> handleDelete());
        cancelButton.setOnAction(e -> handleCancel());
    }

    public void initializeForCreate(LocalDate date, Runnable onEventChanged) {
        this.mode = "CREATE";
        this.selectedDate = date;
        this.onEventChanged = onEventChanged;
        modeLabel.setText("Crear Nuevo Evento - " + date);
        eventFormContainer.setVisible(true);
        eventListContainer.setVisible(false);
        saveButton.setVisible(true);
        updateButton.setVisible(false);
        deleteButton.setVisible(false);
        datePicker.setValue(date);
        clearForm();
    }

    public void initializeForRead(LocalDate date, Runnable onEventChanged) {
        this.mode = "READ";
        this.selectedDate = date;
        this.onEventChanged = onEventChanged;
        modeLabel.setText("Eventos del " + date);
        eventFormContainer.setVisible(true);
        eventListContainer.setVisible(true);
        saveButton.setVisible(false);
        updateButton.setVisible(true);
        deleteButton.setVisible(true);
        loadEventsForDate(date);
        clearForm();
    }

    private void loadEventsForDate(LocalDate date) {
        if (authService.getCurrentUser() == null) {
            showAlert("Error", "No hay usuario logueado", Alert.AlertType.ERROR);
            return;
        }
        String userId = authService.getCurrentUser().getUserId();
        try {
            List<Event> events = eventService.getEventsForDate(userId, date);
            eventsListView.getItems().clear();
            if (events.isEmpty()) {
                eventsListView.getItems().add("No hay eventos para este día");
                updateButton.setDisable(true);
                deleteButton.setDisable(true);
            } else {
                for (Event event : events) {
                    String displayText = String.format("%s - %s",
                            event.getStartDate().toLocalTime().toString().substring(0, 5),
                            event.getTitle());
                    eventsListView.getItems().add(displayText);
                }
                updateButton.setDisable(false);
                deleteButton.setDisable(false);
            }
        } catch (Exception e) {
            System.err.println("Error cargando eventos: " + e.getMessage());
            showAlert("Error", "No se pudieron cargar los eventos: " + e.getMessage(), Alert.AlertType.ERROR);
        }
    }

    private void loadEventForEdit(String selectedItem) {
        if (selectedItem.startsWith("No hay eventos")) return;
        try {
            String title = selectedItem.substring(selectedItem.indexOf(" - ") + 3);
            String userId = authService.getCurrentUser().getUserId();
            List<Event> events = eventService.getEventsForDate(userId, selectedDate);
            Optional<Event> eventOpt = events.stream()
                    .filter(e -> e.getTitle().equals(title))
                    .findFirst();
            if (eventOpt.isPresent()) {
                currentEvent = eventOpt.get();
                loadEventToForm(currentEvent);
                mode = "UPDATE";
            }
        } catch (Exception e) {
            System.err.println("Error cargando evento para editar: " + e.getMessage());
            showAlert("Error", "Error al cargar evento: " + e.getMessage(), Alert.AlertType.ERROR);
        }
    }

    private void loadEventToForm(Event event) {
        titleField.setText(event.getTitle());
        descriptionArea.setText(event.getDescription());
        datePicker.setValue(event.getStartDate().toLocalDate());
        locationField.setText(event.getLocation());
        if (event.isAllDay()) {
            allDayCheckBox.setSelected(true);
            startTimeField.setText("00:00");
            endTimeField.setText("23:59");
            startTimeField.setDisable(true);
            endTimeField.setDisable(true);
        } else {
            allDayCheckBox.setSelected(false);
            startTimeField.setText(event.getStartDate().toLocalTime().toString().substring(0, 5));
            endTimeField.setText(event.getEndDate().toLocalTime().toString().substring(0, 5));
            startTimeField.setDisable(false);
            endTimeField.setDisable(false);
        }
    }

    @FXML
    private void handleSave() {
        if (!validateForm()) return;
        try {
            Event newEvent = createEventFromForm();
            boolean success = saveEventToDatabase(newEvent);
            if (success) {
                showAlert("Éxito", "Evento creado exitosamente", Alert.AlertType.INFORMATION);
                if (onEventChanged != null) onEventChanged.run();
                closeDialog();
            } else {
                showAlert("Error", "No se pudo crear el evento", Alert.AlertType.ERROR);
            }
        } catch (Exception e) {
            System.err.println("Error creando evento: " + e.getMessage());
            showAlert("Error", "Error al crear evento: " + e.getMessage(), Alert.AlertType.ERROR);
        }
    }

    @FXML
    private void handleUpdate() {
        if (currentEvent == null) {
            showAlert("Error", "No hay evento seleccionado para actualizar", Alert.AlertType.WARNING);
            return;
        }
        if (!validateForm()) return;
        try {
            updateEventFromForm(currentEvent);
            boolean success = updateEventInDatabase(currentEvent);
            if (success) {
                showAlert("Éxito", "Evento actualizado exitosamente", Alert.AlertType.INFORMATION);
                if (onEventChanged != null) onEventChanged.run();
                loadEventsForDate(selectedDate);
                clearForm();
            } else {
                showAlert("Error", "No se pudo actualizar el evento", Alert.AlertType.ERROR);
            }
        } catch (Exception e) {
            System.err.println("Error actualizando evento: " + e.getMessage());
            showAlert("Error", "Error al actualizar evento: " + e.getMessage(), Alert.AlertType.ERROR);
        }
    }

    @FXML
    private void handleDelete() {
        if (currentEvent == null) {
            showAlert("Error", "No hay evento seleccionado para eliminar", Alert.AlertType.WARNING);
            return;
        }
        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
        confirmation.setTitle("Confirmar eliminación");
        confirmation.setHeaderText("¿Eliminar evento?");
        confirmation.setContentText("¿Estás seguro de que quieres eliminar:\n\"" + currentEvent.getTitle() + "\"?");
        Optional<ButtonType> result = confirmation.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            try {
                boolean success = deleteEventFromDatabase(currentEvent.getEventId());
                if (success) {
                    showAlert("Éxito", "Evento eliminado exitosamente", Alert.AlertType.INFORMATION);
                    if (onEventChanged != null) onEventChanged.run();
                    loadEventsForDate(selectedDate);
                    clearForm();
                    currentEvent = null;
                } else {
                    showAlert("Error", "No se pudo eliminar el evento", Alert.AlertType.ERROR);
                }
            } catch (Exception e) {
                System.err.println("Error eliminando evento: " + e.getMessage());
                showAlert("Error", "Error al eliminar evento: " + e.getMessage(), Alert.AlertType.ERROR);
            }
        }
    }

    @FXML
    private void handleCancel() {
        closeDialog();
    }

    private Event createEventFromForm() throws Exception {
        Event event = new Event();
        event.setCreatorId(authService.getCurrentUser().getUserId());
        event.setTitle(titleField.getText().trim());
        event.setDescription(descriptionArea.getText().trim());
        event.setLocation(locationField.getText().trim());

        LocalDate date = datePicker.getValue();

        if (allDayCheckBox.isSelected()) {
            event.setStartDate(date.atStartOfDay());
            event.setEndDate(date.atTime(23, 59));
            event.setAllDay('Y');
        } else {
            LocalTime startTime = LocalTime.parse(startTimeField.getText().trim());
            LocalTime endTime = LocalTime.parse(endTimeField.getText().trim());
            event.setStartDate(date.atTime(startTime));
            event.setEndDate(date.atTime(endTime));
            event.setAllDay('N');
        }
        event.setCreatedDate(LocalDateTime.now());
        return event;
    }

    private void updateEventFromForm(Event event) throws Exception {
        event.setTitle(titleField.getText().trim());
        event.setDescription(descriptionArea.getText().trim());
        event.setLocation(locationField.getText().trim());
        LocalDate date = datePicker.getValue();
        if (allDayCheckBox.isSelected()) {
            event.setStartDate(date.atStartOfDay());
            event.setEndDate(date.atTime(23, 59));
            event.setAllDay('Y');
        } else {
            LocalTime startTime = LocalTime.parse(startTimeField.getText().trim());
            LocalTime endTime = LocalTime.parse(endTimeField.getText().trim());
            event.setStartDate(date.atTime(startTime));
            event.setEndDate(date.atTime(endTime));
            event.setAllDay('N');
        }
        event.setModifiedDate(LocalDateTime.now());
    }

    private boolean validateForm() {
        if (titleField.getText().trim().isEmpty()) {
            showAlert("Error de validación", "El título es obligatorio", Alert.AlertType.WARNING);
            titleField.requestFocus();
            return false;
        }
        if (datePicker.getValue() == null) {
            showAlert("Error de validación", "La fecha es obligatoria", Alert.AlertType.WARNING);
            datePicker.requestFocus();
            return false;
        }
        if (!allDayCheckBox.isSelected()) {
            try {
                LocalTime.parse(startTimeField.getText().trim());
                LocalTime.parse(endTimeField.getText().trim());
            } catch (Exception e) {
                showAlert("Error de validación", "Formato de hora inválido (use HH:MM)", Alert.AlertType.WARNING);
                startTimeField.requestFocus();
                return false;
            }
        }
        return true;
    }

    private void clearForm() {
        titleField.clear();
        descriptionArea.clear();
        locationField.clear();
        startTimeField.setText("08:00");
        endTimeField.setText("10:00");
        allDayCheckBox.setSelected(false);
        startTimeField.setDisable(false);
        endTimeField.setDisable(false);
        calendarComboBox.setValue("Mis Clases");
        currentEvent = null;
        mode = "CREATE";
    }

    private boolean saveEventToDatabase(Event event) {
        try {
            if (event.getCalendarId() == null || event.getCalendarId().isEmpty()) {
                String defaultCalendarId = eventService.getDefaultCalendarId(event.getCreatorId());
                if (defaultCalendarId != null) {
                    event.setCalendarId(defaultCalendarId);
                } else {
                    System.err.println("No se encontró calendario por defecto para el usuario");
                    showAlert("Error", "No se encontró un calendario válido para crear el evento", Alert.AlertType.ERROR);
                    return false;
                }
            }
            boolean success = eventService.createEvent(event);
            if (success) {
                System.out.println("Evento guardado exitosamente en BD");
            }
            return success;
        } catch (Exception e) {
            System.err.println("Error en saveEventToDatabase: " + e.getMessage());
            return false;
        }
    }

    private boolean updateEventInDatabase(Event event) {
        try {
            boolean success = eventService.updateEvent(event);
            if (success) {
                System.out.println("Evento actualizado exitosamente en BD");
            }
            return success;
        } catch (Exception e) {
            System.err.println("Error en updateEventInDatabase: " + e.getMessage());
            return false;
        }
    }

    private boolean deleteEventFromDatabase(String eventId) {
        try {
            boolean success = eventService.deleteEvent(eventId);
            if (success) {
                System.out.println("Evento eliminado exitosamente de BD");
            }
            return success;
        } catch (Exception e) {
            System.err.println("Error en deleteEventFromDatabase: " + e.getMessage());
            return false;
        }
    }

    private void showAlert(String title, String message, Alert.AlertType type) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    public void initializeForEdit(Event event, Runnable onEventChanged) {
        this.mode = "UPDATE";
        this.currentEvent = event;
        this.selectedDate = event.getStartDate().toLocalDate();
        this.onEventChanged = onEventChanged;

        modeLabel.setText("Editar Evento - " + event.getTitle());
        eventFormContainer.setVisible(true);
        eventListContainer.setVisible(false);
        saveButton.setVisible(false);
        updateButton.setVisible(true);
        deleteButton.setVisible(true);

        loadEventToForm(event);
    }

    private void closeDialog() {
        Stage stage = (Stage) titleField.getScene().getWindow();
        stage.close();
    }
}