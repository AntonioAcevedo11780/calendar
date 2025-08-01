package com.utez.calendario.controllers;

import com.utez.calendario.models.Event;
import com.utez.calendario.services.AuthService;
import com.utez.calendario.services.EventService;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.net.URL;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class EventDialogController implements Initializable {

    // Elementos del header
    @FXML private Label dialogTitle;
    @FXML private Button closeButton;

    // Elementos de la vista (visualización de evento)
    @FXML private ScrollPane eventViewScrollPane;
    @FXML private VBox eventViewContainer;
    @FXML private Label eventTimeLabel;
    @FXML private HBox locationViewRow;
    @FXML private Label eventLocationLabel;
    @FXML private HBox descriptionViewContainer;
    @FXML private Label eventDescriptionLabel;
    @FXML private Label eventCalendarLabel;
    @FXML private Label createdByLabel;
    @FXML private HBox viewButtonContainer;
    @FXML private Button editButton;
    @FXML private Button deleteViewButton;

    // Elementos del formulario (creación/edición)
    @FXML private ScrollPane eventFormScrollPane;
    @FXML private VBox eventFormContainer;
    @FXML private TextField titleField;
    @FXML private DatePicker datePicker;
    @FXML private ComboBox<String> startTimeComboBox;
    @FXML private ComboBox<String> endTimeComboBox;
    @FXML private CheckBox allDayCheckBox;
    @FXML private Button addGuestsFormButton;
    @FXML private TextField locationField;
    @FXML private TextArea descriptionArea;
    @FXML private ComboBox<String> calendarComboBox;
    @FXML private HBox formButtonContainer;
    @FXML private Button cancelButton;
    @FXML private Button saveButton;
    @FXML private Button updateButton;
    @FXML private Button deleteFormButton;

    private EventService eventService;
    private AuthService authService;
    private String mode = "CREATE"; // CREATE, VIEW, EDIT
    private Event currentEvent;
    private LocalDate selectedDate;
    private LocalTime selectedStartTime;
    private LocalTime selectedEndTime;
    private Runnable onEventChanged;
    private boolean calendarInitialized = false;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        eventService = EventService.getInstance();
        authService = AuthService.getInstance();

        // Inicializar calendarios del usuario
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

    /**
     * Inicializa el diálogo para crear un nuevo evento
     */
    public void initializeForCreate(LocalDate date, Runnable onEventChanged) {
        this.mode = "CREATE";
        this.selectedDate = date;
        this.onEventChanged = onEventChanged;

        dialogTitle.setText("Nuevo evento");
        showFormView();

        // Configurar fecha seleccionada
        datePicker.setValue(date);

        // Configurar horarios por defecto
        if (startTimeComboBox != null) {
            startTimeComboBox.setValue("09:00");
        }
        if (endTimeComboBox != null) {
            endTimeComboBox.setValue("10:00");
        }

        // Si se proporcionaron horas específicas, usarlas
        if (selectedStartTime != null && startTimeComboBox != null) {
            startTimeComboBox.setValue(selectedStartTime.format(DateTimeFormatter.ofPattern("HH:mm")));
        }
        if (selectedEndTime != null && endTimeComboBox != null) {
            endTimeComboBox.setValue(selectedEndTime.format(DateTimeFormatter.ofPattern("HH:mm")));
        }
    }

    /**
     * Inicializa el diálogo para crear un evento con hora específica
     */
    public void initializeForCreateWithTime(LocalDate date, LocalTime startTime, LocalTime endTime, Runnable onEventChanged) {
        this.selectedStartTime = startTime;
        this.selectedEndTime = endTime;
        initializeForCreate(date, onEventChanged);
    }

    /**
     * Inicializa el diálogo para ver eventos de una fecha
     */
    public void initializeForRead(LocalDate date, Runnable onEventChanged) {
        this.mode = "VIEW_LIST";
        this.selectedDate = date;
        this.onEventChanged = onEventChanged;

        loadEventsForDate(date);
    }

    /**
     * Inicializa el diálogo para ver un evento específico
     */
    public void initializeForViewEvent(Event event, Runnable onEventChanged) {
        this.mode = "VIEW";
        this.currentEvent = event;
        this.selectedDate = event.getStartDate().toLocalDate();
        this.onEventChanged = onEventChanged;
        dialogTitle.setText(event.getTitle());
        showEventView();
        loadEventToView(event);
    }

    /**
     * Inicializa el diálogo para editar un evento
     */
    public void initializeForEdit(Event event, Runnable onEventChanged) {
        this.mode = "EDIT";
        this.currentEvent = event;
        this.selectedDate = event.getStartDate().toLocalDate();
        this.onEventChanged = onEventChanged;

        dialogTitle.setText("Editar evento");
        showFormView();
        loadEventToForm(event);
    }

    private void setupComponents() {
        // Configurar DatePicker
        if (datePicker != null) {
            datePicker.setValue(LocalDate.now());
        }

        // Configurar ComboBox de tiempo
        setupTimeComboBoxes();

        // Configurar CheckBox para todo el día
        if (allDayCheckBox != null) {
            allDayCheckBox.setSelected(false);
            allDayCheckBox.selectedProperty().addListener((obs, oldVal, newVal) -> {
                startTimeComboBox.setDisable(newVal);
                endTimeComboBox.setDisable(newVal);

                if (newVal) {
                    startTimeComboBox.setValue("00:00");
                    endTimeComboBox.setValue("23:59");
                } else {
                    startTimeComboBox.setValue("09:00");
                    endTimeComboBox.setValue("10:00");
                }
            });
        }
    }
    private String formatEventDate(Event event) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", new Locale("es", "ES"));
        String dateText = event.getStartDate().toLocalDate().format(formatter);

        if (dateText.length() > 0) {
            dateText = dateText.substring(0, 1).toUpperCase() + dateText.substring(1);
        }

        return dateText;
    }

    private void setupTimeComboBoxes() {
        ObservableList<String> timeOptions = FXCollections.observableArrayList();

        // Generar opciones de tiempo cada 15 minutos
        for (int hour = 0; hour < 24; hour++) {
            for (int minute = 0; minute < 60; minute += 15) {
                String timeString = String.format("%02d:%02d", hour, minute);
                timeOptions.add(timeString);
            }
        }

        if (startTimeComboBox != null) {
            startTimeComboBox.setItems(timeOptions);
            startTimeComboBox.setValue("09:00");
            startTimeComboBox.setEditable(false);
        }

        if (endTimeComboBox != null) {
            endTimeComboBox.setItems(timeOptions);
            endTimeComboBox.setValue("10:00");
            endTimeComboBox.setEditable(false);

            // Auto-ajustar hora de fin cuando cambia la de inicio
            startTimeComboBox.valueProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal != null && !allDayCheckBox.isSelected()) {
                    try {
                        LocalTime startTime = LocalTime.parse(newVal);
                        LocalTime suggestedEndTime = startTime.plusHours(1);
                        String endTimeString = suggestedEndTime.format(DateTimeFormatter.ofPattern("HH:mm"));

                        // Solo cambiar si la nueva hora está disponible en las opciones
                        if (timeOptions.contains(endTimeString)) {
                            endTimeComboBox.setValue(endTimeString);
                        }
                    } catch (Exception e) {
                        // Ignorar errores de parsing
                    }
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
                    calendarComboBox.setValue(items.get(0));
                } else {
                    calendarComboBox.setItems(FXCollections.observableArrayList("Mi Calendario"));
                    calendarComboBox.setValue("Mi Calendario");
                }
            } catch (Exception e) {
                System.err.println("Error cargando calendarios: " + e.getMessage());
                calendarComboBox.setItems(FXCollections.observableArrayList("Mi Calendario"));
                calendarComboBox.setValue("Mi Calendario");
            }
        }
    }

    private void setupEventHandlers() {
        if (closeButton != null) {
            closeButton.setOnAction(e -> closeDialog());
        }

        if (saveButton != null) {
            saveButton.setOnAction(e -> handleSave());
        }

        if (updateButton != null) {
            updateButton.setOnAction(e -> handleUpdate());
        }

        if (deleteFormButton != null) {
            deleteFormButton.setOnAction(e -> handleDelete());
        }

        if (deleteViewButton != null) {
            deleteViewButton.setOnAction(e -> handleDelete());
        }

        if (editButton != null) {
            editButton.setOnAction(e -> switchToEditMode());
        }

        if (cancelButton != null) {
            cancelButton.setOnAction(e -> {
                if ("EDIT".equals(mode)) {
                    switchToViewMode();
                } else {
                    closeDialog();
                }
            });
        }

        if (addGuestsFormButton != null) {
            addGuestsFormButton.setOnAction(e -> handleAddGuests());
        }
    }

    /**
     * Cambia la vista para mostrar el formulario
     */
    private void showFormView() {
        // Ocultar vista de evento
        eventViewContainer.setVisible(false);
        eventViewContainer.setManaged(false);
        viewButtonContainer.setVisible(false);
        viewButtonContainer.setManaged(false);

        // Mostrar formulario
        eventFormContainer.setVisible(true);
        eventFormContainer.setManaged(true);
        formButtonContainer.setVisible(true);
        formButtonContainer.setManaged(true);

        // Configurar botones según el modo
        if ("CREATE".equals(mode)) {
            saveButton.setVisible(true);
            saveButton.setManaged(true);
            updateButton.setVisible(false);
            updateButton.setManaged(false);
            deleteFormButton.setVisible(false);
            deleteFormButton.setManaged(false);
        } else if ("EDIT".equals(mode)) {
            saveButton.setVisible(false);
            saveButton.setManaged(false);
            updateButton.setVisible(true);
            updateButton.setManaged(true);
            deleteFormButton.setVisible(true);
            deleteFormButton.setManaged(true);
        }
    }

    /**
     * Cambia la vista para mostrar el evento
     */
    private void showEventView() {
        // Ocultar formulario
        eventFormContainer.setVisible(false);
        eventFormContainer.setManaged(false);
        formButtonContainer.setVisible(false);
        formButtonContainer.setManaged(false);

        // Mostrar vista de evento
        eventViewContainer.setVisible(true);
        eventViewContainer.setManaged(true);
        viewButtonContainer.setVisible(true);
        viewButtonContainer.setManaged(true);
    }

    /**
     * Carga los eventos para una fecha específica
     */
    private void loadEventsForDate(LocalDate date) {
        try {
            String userId = authService.getCurrentUser().getUserId();
            List<Event> events = eventService.getEventsForDate(userId, date);

            if (events.isEmpty()) {
                // No hay eventos, abrir modo crear
                initializeForCreate(date, onEventChanged);
            } else if (events.size() == 1) {
                // Un solo evento, abrir en modo vista
                initializeForViewEvent(events.get(0), onEventChanged);
            } else {
                // Múltiples eventos, mostrar lista (por implementar)
                initializeForViewEvent(events.get(0), onEventChanged);
            }

        } catch (Exception e) {
            System.err.println("Error cargando eventos: " + e.getMessage());
            showAlert("Error", "No se pudieron cargar los eventos: " + e.getMessage(), Alert.AlertType.ERROR);
        }
    }

    /**
     * Carga un evento en la vista de solo lectura
     */
    private void loadEventToView(Event event) {

        // Configurar fecha y hora - CON PRIMERA LETRA MAYÚSCULA
        String timeText = formatEventTime(event);
        String dateText = formatEventDate(event); // Usar el nuevo método
        eventTimeLabel.setText(dateText + " • " + timeText);

        // Configurar ubicación
        if (event.getLocation() != null && !event.getLocation().trim().isEmpty()) {
            eventLocationLabel.setText(event.getLocation());
            locationViewRow.setVisible(true);
            locationViewRow.setManaged(true);
        } else {
            locationViewRow.setVisible(false);
            locationViewRow.setManaged(false);
        }

        // Configurar descripción
        if (event.getDescription() != null && !event.getDescription().trim().isEmpty()) {
            eventDescriptionLabel.setText(event.getDescription());
            descriptionViewContainer.setVisible(true);
            descriptionViewContainer.setManaged(true);
        } else {
            descriptionViewContainer.setVisible(false);
            descriptionViewContainer.setManaged(false);
        }

        // Configurar información del calendario
        String userId = authService.getCurrentUser().getUserId();
        List<String> calendarNames = eventService.getUserCalendarNames(userId);

        String calendarName = "Mi Calendario";
        for (String name : calendarNames) {
            String calendarId = eventService.getCalendarIdByName(userId, name);
            if (calendarId.equals(event.getCalendarId())) {
                calendarName = name;
                break;
            }
        }

        eventCalendarLabel.setText(calendarName);

        // Información del creador
        String creatorName = authService.getCurrentUser().getDisplayInfo();
        createdByLabel.setText("Creado por: " + creatorName);
    }

    /**
     * Carga un evento en el formulario para edición
     */
    private void loadEventToForm(Event event) {
        titleField.setText(event.getTitle());
        descriptionArea.setText(event.getDescription() != null ? event.getDescription() : "");
        locationField.setText(event.getLocation() != null ? event.getLocation() : "");

        // Configurar calendario
        String userId = authService.getCurrentUser().getUserId();
        List<String> calendarNames = eventService.getUserCalendarNames(userId);

        String selectedCalendarName = calendarNames.get(0);
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
            startTimeComboBox.setValue("00:00");
            endTimeComboBox.setValue("23:59");
        } else {
            startTimeComboBox.setValue(formatTime(event.getStartDate()));
            endTimeComboBox.setValue(formatTime(event.getEndDate()));
        }
    }

    /**
     * Cambia al modo de edición
     */
    private void switchToEditMode() {
        mode = "EDIT";
        dialogTitle.setText("Editar evento");
        showFormView();
        loadEventToForm(currentEvent);
    }

    /**
     * Cambia al modo de vista
     */
    private void switchToViewMode() {
        mode = "VIEW";
        dialogTitle.setText(currentEvent.getTitle());
        showEventView();
        loadEventToView(currentEvent);
    }

    /**
     * Maneja el guardado de un nuevo evento
     */
    @FXML
    private void handleSave() {
        if (!validateForm()) return;

        try {
            Event newEvent = createEventFromForm();

            if (!isTimeSlotAvailable(newEvent)) {
                showAlert("Conflicto de horario",
                        "Ya hay un evento en ese horario para este día.",
                        Alert.AlertType.WARNING);
                return;
            }

            boolean success = eventService.createEvent(newEvent);
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

    /**
     * Maneja la actualización de un evento
     */
    @FXML
    private void handleUpdate() {
        if (currentEvent == null || !validateForm()) return;

        try {
            updateEventFromForm(currentEvent);

            if (!isTimeSlotAvailable(currentEvent)) {
                showAlert("Conflicto de horario",
                        "Ya hay un evento en ese horario para este día.",
                        Alert.AlertType.WARNING);
                return;
            }

            boolean success = eventService.updateEvent(currentEvent);
            if (success) {
                showAlert("Éxito", "Evento actualizado exitosamente", Alert.AlertType.INFORMATION);
                if (onEventChanged != null) onEventChanged.run();
                switchToViewMode();
            } else {
                showAlert("Error", "No se pudo actualizar el evento", Alert.AlertType.ERROR);
            }
        } catch (Exception e) {
            System.err.println("Error actualizando evento: " + e.getMessage());
            showAlert("Error", "Error al actualizar evento: " + e.getMessage(), Alert.AlertType.ERROR);
        }
    }

    /**
     * Maneja la eliminación de un evento
     */
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
                boolean success = eventService.deleteEvent(currentEvent.getEventId());
                if (success) {
                    showAlert("Éxito", "Evento eliminado exitosamente", Alert.AlertType.INFORMATION);
                    if (onEventChanged != null) onEventChanged.run();
                    closeDialog();
                } else {
                    showAlert("Error", "No se pudo eliminar el evento", Alert.AlertType.ERROR);
                }
            } catch (Exception e) {
                System.err.println("Error eliminando evento: " + e.getMessage());
                showAlert("Error", "Error al eliminar evento: " + e.getMessage(), Alert.AlertType.ERROR);
            }
        }
    }

    /**
     * Maneja la funcionalidad de añadir invitados (placeholder)
     */
    private void handleAddGuests() {
        showAlert("Funcionalidad en desarrollo",
                "La función de añadir invitados estará disponible próximamente.",
                Alert.AlertType.INFORMATION);
    }

    /**
     * Crea un nuevo evento desde los datos del formulario
     */
    private Event createEventFromForm() {
        Event event = new Event();
        event.setTitle(titleField.getText().trim());
        event.setDescription(descriptionArea.getText().trim());
        event.setLocation(locationField.getText().trim());

        // Generar ID único
        event.setEventId(generateEventId());

        // Configurar calendario
        String userId = authService.getCurrentUser().getUserId();
        String calendarName = calendarComboBox.getValue();
        String calendarId = eventService.getCalendarIdByName(userId, calendarName);
        event.setCalendarId(calendarId);
        event.setCreatorId(userId);

        // Configurar fecha y hora
        setupEventDateTime(event);

        return event;
    }

    /**
     * Actualiza un evento con los datos del formulario
     */
    private void updateEventFromForm(Event event) {
        event.setTitle(titleField.getText().trim());
        event.setDescription(descriptionArea.getText().trim());
        event.setLocation(locationField.getText().trim());

        // Actualizar calendario
        String userId = authService.getCurrentUser().getUserId();
        String calendarName = calendarComboBox.getValue();
        String calendarId = eventService.getCalendarIdByName(userId, calendarName);
        event.setCalendarId(calendarId);

        // Configurar fecha y hora
        setupEventDateTime(event);
    }

    /**
     * Configura la fecha y hora del evento
     */
    private void setupEventDateTime(Event event) {
        LocalDate date = datePicker.getValue();
        boolean isAllDay = allDayCheckBox.isSelected();

        event.setAllDay(isAllDay ? 'Y' : 'N');

        if (isAllDay) {
            event.setStartDate(LocalDateTime.of(date, LocalTime.MIN));
            event.setEndDate(LocalDateTime.of(date, LocalTime.MAX));
        } else {
            LocalTime startTime = LocalTime.parse(startTimeComboBox.getValue());
            LocalTime endTime = LocalTime.parse(endTimeComboBox.getValue());

            event.setStartDate(LocalDateTime.of(date, startTime));
            event.setEndDate(LocalDateTime.of(date, endTime));
        }
    }

    /**
     * Valida los datos del formulario
     */
    private boolean validateForm() {
        String title = titleField.getText().trim();
        if (title.isEmpty()) {
            showAlert("Error de validación", "El título no puede estar vacío", Alert.AlertType.WARNING);
            titleField.requestFocus();
            return false;
        }

        if (!allDayCheckBox.isSelected()) {
            try {
                LocalTime startTime = LocalTime.parse(startTimeComboBox.getValue());
                LocalTime endTime = LocalTime.parse(endTimeComboBox.getValue());

                if (endTime.isBefore(startTime) || endTime.equals(startTime)) {
                    showAlert("Error de validación",
                            "La hora de fin debe ser posterior a la hora de inicio",
                            Alert.AlertType.WARNING);
                    endTimeComboBox.requestFocus();
                    return false;
                }
            } catch (Exception e) {
                showAlert("Error de validación",
                        "Error en la selección de horarios",
                        Alert.AlertType.WARNING);
                return false;
            }
        }

        return true;
    }

    /**
     * Verifica si el horario está disponible
     */
    private boolean isTimeSlotAvailable(Event newEvent) {
        try {
            String userId = authService.getCurrentUser().getUserId();
            List<Event> events = eventService.getEventsForDate(userId, newEvent.getStartDate().toLocalDate());

            for (Event e : events) {
                // Omitir el evento actual al editar
                if ("EDIT".equals(mode) && currentEvent != null &&
                        e.getEventId().equals(currentEvent.getEventId())) {
                    continue;
                }

                // Verificar solapamiento
                if (newEvent.getStartDate().isBefore(e.getEndDate()) &&
                        newEvent.getEndDate().isAfter(e.getStartDate())) {
                    return false;
                }
            }
            return true;
        } catch (Exception e) {
            System.err.println("Error validando solapamiento: " + e.getMessage());
            return false;
        }
    }

    // Métodos de utilidad
    private String generateEventId() {
        return "E" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"));
    }

    private LocalTime parseTime(String timeStr) {
        return LocalTime.parse(timeStr, DateTimeFormatter.ofPattern("HH:mm"));
    }

    private String formatTime(LocalDateTime dateTime) {
        return dateTime.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"));
    }

    private String formatEventTime(Event event) {
        if (event.isAllDay()) {
            return "Todo el día";
        } else {
            return formatTime(event.getStartDate()) + " - " + formatTime(event.getEndDate());
        }
    }

    private void showAlert(String title, String message, Alert.AlertType type) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);

        // Aplicar estilos CSS si están disponibles
        try {
            DialogPane dialogPane = alert.getDialogPane();
            String css = getClass().getResource("/css/alert-style.css").toExternalForm();
            dialogPane.getStylesheets().add(css);
        } catch (Exception ignored) {
            // CSS no disponible, continuar sin estilos
        }

        alert.showAndWait();
    }

    private void closeDialog() {
        Stage stage = (Stage) closeButton.getScene().getWindow();
        stage.close();
    }
}