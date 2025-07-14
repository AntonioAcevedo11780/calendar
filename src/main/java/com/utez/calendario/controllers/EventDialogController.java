package com.utez.calendario.controllers;

import com.utez.calendario.models.Event;
import com.utez.calendario.services.AuthService;
import com.utez.calendario.services.EventService;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.geometry.Pos;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.AnchorPane;

import java.net.URL;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Consumer;

public class EventDialogController implements Initializable {

    @FXML
    private Label modeLabel;

    @FXML
    private TextField titleField;

    @FXML
    private TextArea descriptionArea;

    @FXML
    private TextField locationField;

    @FXML
    private ComboBox<String> calendarComboBox;

    @FXML
    private DatePicker datePicker;

    @FXML
    private TextField startTimeField;

    @FXML
    private TextField endTimeField;

    @FXML
    private CheckBox allDayCheckBox;

    @FXML
    private Button saveButton;

    @FXML
    private Button updateButton;

    @FXML
    private Button deleteButton;

    @FXML
    private Button cancelButton;

    @FXML
    private VBox eventFormContainer;

    @FXML
    private VBox eventListContainer;

    @FXML
    private ListView<Event> eventListView;

    private EventService eventService;
    private AuthService authService;
    private String mode = "CREATE";
    private Event currentEvent;
    private LocalDate selectedDate;
    private Map<String, Event> eventsMap;
    private Runnable onEventChanged;
    private ProgressIndicator loadingIndicator;
    private boolean calendarInitialized = false;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        eventService = EventService.getInstance();
        authService = AuthService.getInstance();
        eventsMap = new HashMap<>();

        // Añadir indicador de carga si no existe
        if (loadingIndicator == null) {
            loadingIndicator = new ProgressIndicator();
            loadingIndicator.setVisible(false);
            loadingIndicator.setMaxSize(50, 50);

            // Aplicar estilos CSS para centrar
            loadingIndicator.setStyle("-fx-translate-x: 50%; -fx-translate-y: 50%; -fx-background-color: rgba(255, 255, 255, 0.7); -fx-padding: 10px;");

            if (eventFormContainer != null) {
                // Usar un AnchorPane para posicionar el indicador
                AnchorPane anchorPane = new AnchorPane(loadingIndicator);
                AnchorPane.setTopAnchor(loadingIndicator, 0.0);
                AnchorPane.setRightAnchor(loadingIndicator, 0.0);
                AnchorPane.setBottomAnchor(loadingIndicator, 0.0);
                AnchorPane.setLeftAnchor(loadingIndicator, 0.0);

                eventFormContainer.getChildren().add(anchorPane);
            }
        }

        // Inicializar los calendarios del usuario para evitar problemas de FK
        String userId = authService.getCurrentUser().getUserId();
        eventService.initializeUserCalendarsAsync(userId)
                .thenRun(() -> {
                    Platform.runLater(() -> {
                        setupCalendarComboBox();
                        calendarInitialized = true;
                    });
                })
                .exceptionally(ex -> {
                    System.err.println("Error inicializando calendarios: " + ex.getMessage());
                    return null;
                });

        setupComponents();
        setupEventHandlers();
    }

    public void initializeForCreate(LocalDate date, Runnable onEventChanged) {
        this.mode = "CREATE";
        this.selectedDate = date;
        this.onEventChanged = onEventChanged;

        modeLabel.setText("Crear Nuevo Evento - " + date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
        eventFormContainer.setVisible(true);
        eventListContainer.setVisible(false);
        saveButton.setVisible(true);
        updateButton.setVisible(false);
        deleteButton.setVisible(false);

        // Establecer fecha seleccionada
        datePicker.setValue(date);

        // Establecer horario predeterminado
        startTimeField.setText("08:00");
        endTimeField.setText("09:00");
    }

    public void initializeForView(LocalDate date, Runnable onEventChanged) {
        this.mode = "VIEW";
        this.selectedDate = date;
        this.onEventChanged = onEventChanged;

        modeLabel.setText("Eventos para " + date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
        eventFormContainer.setVisible(false);
        eventListContainer.setVisible(true);

        loadEventsForDate(date);
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

    private void setupComponents() {
        // Configurar la lista de eventos
        if (eventListView != null) {
            eventListView.setCellFactory(param -> new ListCell<Event>() {
                @Override
                protected void updateItem(Event event, boolean empty) {
                    super.updateItem(event, empty);
                    if (empty || event == null) {
                        setText(null);
                        setGraphic(null);
                    } else {
                        VBox container = new VBox(5);
                        container.getStyleClass().add("event-item");

                        Label titleLabel = new Label(event.getTitle());
                        titleLabel.getStyleClass().add("event-title");

                        String timeText = formatEventTime(event);
                        Label timeLabel = new Label(timeText);
                        timeLabel.getStyleClass().add("event-time");

                        HBox buttonBox = new HBox(10);
                        Button editButton = new Button("Editar");
                        editButton.getStyleClass().add("edit-button");
                        editButton.setOnAction(e -> handleEditEvent(event));

                        Button deleteButton = new Button("Eliminar");
                        deleteButton.getStyleClass().add("delete-button");
                        deleteButton.setOnAction(e -> handleDeleteEvent(event));

                        buttonBox.getChildren().addAll(editButton, deleteButton);
                        buttonBox.setAlignment(Pos.CENTER_RIGHT);
                        HBox.setHgrow(buttonBox, Priority.ALWAYS);

                        container.getChildren().addAll(titleLabel, timeLabel, buttonBox);
                        setGraphic(container);
                    }
                }
            });
        }

        // Configurar DatePicker
        if (datePicker != null) {
            datePicker.setValue(LocalDate.now());
        }

        // Configurar CheckBox para todo el día
        if (allDayCheckBox != null) {
            allDayCheckBox.setSelected(false);
            allDayCheckBox.selectedProperty().addListener((obs, oldVal, newVal) -> {
                startTimeField.setDisable(newVal);
                endTimeField.setDisable(newVal);

                if (newVal) {
                    startTimeField.setText("00:00");
                    endTimeField.setText("23:59");
                } else {
                    startTimeField.setText("08:00");
                    endTimeField.setText("09:00");
                }
            });
        }
    }

    private void setupCalendarComboBox() {
        if (calendarComboBox != null) {
            try {
                String userId = authService.getCurrentUser().getUserId();
                List<String> calendarNames = eventService.getUserCalendarNames(userId);

                if (calendarNames != null && !calendarNames.isEmpty()) {
                    ObservableList<String> items = FXCollections.observableArrayList(calendarNames);
                    calendarComboBox.setItems(items);
                    calendarComboBox.setValue(items.get(0)); // Seleccionar el primer calendario por defecto
                } else {
                    System.err.println("No se encontraron calendarios para el usuario");
                    calendarComboBox.setItems(FXCollections.observableArrayList("Mis Clases"));
                    calendarComboBox.setValue("Mis Clases");
                }
            } catch (Exception e) {
                System.err.println("Error cargando calendarios: " + e.getMessage());
                calendarComboBox.setItems(FXCollections.observableArrayList("Mis Clases"));
                calendarComboBox.setValue("Mis Clases");
            }
        }
    }

    private void setupEventHandlers() {
        if (saveButton != null) {
            saveButton.setOnAction(event -> handleSave());
        }

        if (updateButton != null) {
            updateButton.setOnAction(event -> handleUpdate());
        }

        if (deleteButton != null) {
            deleteButton.setOnAction(event -> handleDelete());
        }

        if (cancelButton != null) {
            cancelButton.setOnAction(event -> closeDialog());
        }
    }

    private void loadEventToForm(Event event) {
        titleField.setText(event.getTitle());
        descriptionArea.setText(event.getDescription());
        locationField.setText(event.getLocation());

        // Buscar el nombre del calendario para este evento
        String userId = authService.getCurrentUser().getUserId();
        List<String> calendarNames = eventService.getUserCalendarNames(userId);

        String selectedCalendarName = calendarNames.get(0); // valor por defecto
        for (String calendarName : calendarNames) {
            String calendarId = eventService.getCalendarIdByName(userId, calendarName);
            if (calendarId.equals(event.getCalendarId())) {
                selectedCalendarName = calendarName;
                break;
            }
        }

        calendarComboBox.setValue(selectedCalendarName);

        // Configurar fecha y hora
        datePicker.setValue(event.getStartDate().toLocalDate());

        boolean isAllDay = event.isAllDay();
        allDayCheckBox.setSelected(isAllDay);

        if (isAllDay) {
            startTimeField.setText("00:00");
            endTimeField.setText("23:59");
        } else {
            startTimeField.setText(formatTime(event.getStartDate()));
            endTimeField.setText(formatTime(event.getEndDate()));
        }
    }

    private void loadEventsForDate(LocalDate date) {
        try {
            showLoading(true);
            String userId = authService.getCurrentUser().getUserId();

            eventService.getEventsForDateAsync(userId, date)
                    .thenAccept(events -> {
                        Platform.runLater(() -> {
                            eventsMap.clear();
                            for (Event event : events) {
                                eventsMap.put(event.getEventId(), event);
                            }

                            ObservableList<Event> items = FXCollections.observableArrayList(events);
                            eventListView.setItems(items);
                            showLoading(false);
                        });
                    })
                    .exceptionally(ex -> {
                        Platform.runLater(() -> {
                            System.err.println("Error cargando eventos: " + ex.getMessage());
                            showAlert("Error", "No se pudieron cargar los eventos: " + ex.getMessage(), Alert.AlertType.ERROR);
                            showLoading(false);
                        });
                        return null;
                    });
        } catch (Exception e) {
            System.err.println("Error cargando eventos: " + e.getMessage());
            showLoading(false);
        }
    }

    private Event createEventFromForm() {
        Event event = new Event();
        event.setTitle(titleField.getText().trim());
        event.setDescription(descriptionArea.getText().trim());
        event.setLocation(locationField.getText().trim());

        // Generar ID para el evento
        String eventId = generateEventId();
        event.setEventId(eventId);

        // Obtener ID del calendario seleccionado
        String userId = authService.getCurrentUser().getUserId();
        String calendarName = calendarComboBox.getValue();
        String calendarId = eventService.getCalendarIdByName(userId, calendarName);
        event.setCalendarId(calendarId);

        // Establecer creador
        event.setCreatorId(userId);

        // Configurar fecha y hora
        LocalDate date = datePicker.getValue();
        boolean isAllDay = allDayCheckBox.isSelected();
        event.setAllDay(isAllDay);

        if (isAllDay) {
            event.setStartDate(LocalDateTime.of(date, LocalTime.MIN));
            event.setEndDate(LocalDateTime.of(date, LocalTime.MAX));
        } else {
            LocalTime startTime = parseTime(startTimeField.getText());
            LocalTime endTime = parseTime(endTimeField.getText());

            event.setStartDate(LocalDateTime.of(date, startTime));
            event.setEndDate(LocalDateTime.of(date, endTime));
        }

        return event;
    }

    private void updateEventFromForm(Event event) {
        event.setTitle(titleField.getText().trim());
        event.setDescription(descriptionArea.getText().trim());
        event.setLocation(locationField.getText().trim());

        // Actualizar ID del calendario si ha cambiado
        String userId = authService.getCurrentUser().getUserId();
        String calendarName = calendarComboBox.getValue();
        String calendarId = eventService.getCalendarIdByName(userId, calendarName);
        event.setCalendarId(calendarId);

        // Configurar fecha y hora
        LocalDate date = datePicker.getValue();
        boolean isAllDay = allDayCheckBox.isSelected();
        event.setAllDay(isAllDay);

        if (isAllDay) {
            event.setStartDate(LocalDateTime.of(date, LocalTime.MIN));
            event.setEndDate(LocalDateTime.of(date, LocalTime.MAX));
        } else {
            LocalTime startTime = parseTime(startTimeField.getText());
            LocalTime endTime = parseTime(endTimeField.getText());

            event.setStartDate(LocalDateTime.of(date, startTime));
            event.setEndDate(LocalDateTime.of(date, endTime));
        }
    }

    @FXML
    private void handleSave() {
        if (!validateForm()) return;

        try {
            Event newEvent = createEventFromForm();

            if (!isTimeSlotAvailable(newEvent)) {
                showAlert("Conflicto de horario", "Ya hay un evento en ese horario para este día.", Alert.AlertType.WARNING);
                return;
            }

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

            if (!isTimeSlotAvailable(currentEvent)) {
                showAlert("Conflicto de horario", "Ya hay un evento en ese horario para este día.", Alert.AlertType.WARNING);
                return;
            }

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

    private void handleEditEvent(Event event) {
        currentEvent = event;
        modeLabel.setText("Editar Evento - " + event.getTitle());
        eventFormContainer.setVisible(true);
        eventListContainer.setVisible(false);
        saveButton.setVisible(false);
        updateButton.setVisible(true);
        deleteButton.setVisible(true);

        loadEventToForm(event);
    }

    private void handleDeleteEvent(Event event) {
        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
        confirmation.setTitle("Confirmar eliminación");
        confirmation.setHeaderText("¿Eliminar evento?");
        confirmation.setContentText("¿Estás seguro de que quieres eliminar:\n\"" + event.getTitle() + "\"?");

        Optional<ButtonType> result = confirmation.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            try {
                showLoading(true);
                boolean success = deleteEventFromDatabase(event.getEventId());
                if (success) {
                    showAlert("Éxito", "Evento eliminado exitosamente", Alert.AlertType.INFORMATION);
                    loadEventsForDate(selectedDate);
                    if (onEventChanged != null) {
                        onEventChanged.run();
                    }
                } else {
                    showAlert("Error", "No se pudo eliminar el evento", Alert.AlertType.ERROR);
                }
                showLoading(false);
            } catch (Exception e) {
                System.err.println("Error eliminando evento: " + e.getMessage());
                showAlert("Error", "No se pudo eliminar el evento: " + e.getMessage(), Alert.AlertType.ERROR);
                showLoading(false);
            }
        }
    }

    private String generateEventId() {
        // Generar un ID único para el evento
        LocalDateTime now = LocalDateTime.now();
        String timestamp = now.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"));
        return "E" + timestamp;
    }

    private boolean validateForm() {
        String title = titleField.getText().trim();
        if (title.isEmpty()) {
            showAlert("Error de validación", "El título no puede estar vacío", Alert.AlertType.WARNING);
            return false;
        }

        try {
            parseTime(startTimeField.getText());
        } catch (Exception e) {
            showAlert("Error de validación", "El formato de la hora de inicio es incorrecto. Use HH:mm", Alert.AlertType.WARNING);
            return false;
        }

        try {
            parseTime(endTimeField.getText());
        } catch (Exception e) {
            showAlert("Error de validación", "El formato de la hora de fin es incorrecto. Use HH:mm", Alert.AlertType.WARNING);
            return false;
        }

        LocalTime startTime = parseTime(startTimeField.getText());
        LocalTime endTime = parseTime(endTimeField.getText());

        if (!allDayCheckBox.isSelected() && endTime.isBefore(startTime)) {
            showAlert("Error de validación", "La hora de fin no puede ser anterior a la hora de inicio", Alert.AlertType.WARNING);
            return false;
        }

        return true;
    }

    private boolean saveEventToDatabase(Event event) {
        try {
            showLoading(true);
            return eventService.createEvent(event);
        } catch (Exception e) {
            System.err.println("Error guardando evento: " + e.getMessage());
            return false;
        } finally {
            showLoading(false);
        }
    }

    private boolean updateEventInDatabase(Event event) {
        try {
            showLoading(true);
            return eventService.updateEvent(event);
        } catch (Exception e) {
            System.err.println("Error actualizando evento: " + e.getMessage());
            return false;
        } finally {
            showLoading(false);
        }
    }

    private boolean deleteEventFromDatabase(String eventId) {
        try {
            showLoading(true);
            return eventService.deleteEvent(eventId);
        } catch (Exception e) {
            System.err.println("Error eliminando evento: " + e.getMessage());
            return false;
        } finally {
            showLoading(false);
        }
    }

    private LocalTime parseTime(String timeStr) {
        return LocalTime.parse(timeStr, DateTimeFormatter.ofPattern("HH:mm"));
    }

    private String formatTime(LocalDateTime dateTime) {
        LocalTime time = dateTime.toLocalTime();
        return time.format(DateTimeFormatter.ofPattern("HH:mm"));
    }

    private boolean isTimeSlotAvailable(Event newEvent) {
        try {
            String userId = authService.getCurrentUser().getUserId();
            List<Event> events = eventService.getEventsForDate(userId, newEvent.getStartDate().toLocalDate());

            for (Event e : events) {
                if (mode.equals("UPDATE") && currentEvent != null && e.getEventId().equals(currentEvent.getEventId())) {
                    continue;
                }

                System.out.println("Comparando con evento: " + e.getTitle() + " de " + e.getStartDate() + " a " + e.getEndDate());

                if (newEvent.getStartDate().isBefore(e.getEndDate()) && newEvent.getEndDate().isAfter(e.getStartDate())) {
                    System.out.println("Solapamiento detectado.");
                    return false;
                }
            }
            return true;
        } catch (Exception e) {
            System.err.println("Error validando solapamiento: " + e.getMessage());
            return false;
        }
    }

    private void clearForm() {
        titleField.clear();
        descriptionArea.clear();
        locationField.clear();

        if (calendarComboBox.getItems().size() > 0) {
            calendarComboBox.setValue(calendarComboBox.getItems().get(0));
        }

        datePicker.setValue(LocalDate.now());
        startTimeField.setText("08:00");
        endTimeField.setText("09:00");
        allDayCheckBox.setSelected(false);
    }

    private String formatEventTime(Event event) {
        if (event.isAllDay()) {
            return "Todo el día";
        } else {
            return formatTime(event.getStartDate()) + " - " + formatTime(event.getEndDate());
        }
    }

    private void showLoading(boolean show) {
        // Método showLoading simplificado que solo deshabilita los controles durante la carga
        if (titleField != null) titleField.setDisable(show);
        if (descriptionArea != null) descriptionArea.setDisable(show);
        if (locationField != null) locationField.setDisable(show);
        if (calendarComboBox != null) calendarComboBox.setDisable(show);
        if (datePicker != null) datePicker.setDisable(show);
        if (startTimeField != null) startTimeField.setDisable(show);
        if (endTimeField != null) endTimeField.setDisable(show);
        if (allDayCheckBox != null) allDayCheckBox.setDisable(show);
        if (saveButton != null) saveButton.setDisable(show);
        if (updateButton != null) updateButton.setDisable(show);
        if (deleteButton != null) deleteButton.setDisable(show);
        if (cancelButton != null) cancelButton.setDisable(show);
    }

    private void showAlert(String title, String message, Alert.AlertType type) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);

        DialogPane dialogPane = alert.getDialogPane();
        try {
            String css = getClass().getResource("/css/alert-style.css").toExternalForm();
            dialogPane.getStylesheets().add(css);
            System.out.println("CSS cargado: " + css);
        } catch (Exception ex) {
            System.err.println("No se pudo cargar el CSS: " + ex.getMessage());
        }

        alert.showAndWait();
    }

    private void closeDialog() {
        Stage stage = (Stage) cancelButton.getScene().getWindow();
        stage.close();
    }
}