package com.utez.calendario.controllers;

import com.utez.calendario.models.Event;
import com.utez.calendario.services.AuthService;
import com.utez.calendario.services.EventService;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.StringConverter;

import java.net.URL;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.Optional;

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
    @FXML private ProgressIndicator loadingIndicator;

    private EventService eventService;
    private AuthService authService;
    private LocalDate selectedDate;
    private Event currentEvent;
    private Runnable onEventChanged;
    private String mode;
    private Map<String, Event> eventsMap;
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
            if (eventFormContainer != null) {
                eventFormContainer.getChildren().add(loadingIndicator);
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

    private void setupCalendarComboBox() {
        calendarComboBox.getItems().clear();
        String userId = authService.getCurrentUser().getUserId();

        // Obtener los nombres de calendarios del usuario desde la base de datos
        List<String> calendarNames = eventService.getUserCalendarNames(userId);

        // Si no hay calendarios (raro, pero posible), añadir los predeterminados
        if (calendarNames.isEmpty()) {
            calendarNames.add("Mis Clases");
            calendarNames.add("Tareas y Proyectos");
            calendarNames.add("Personal");
            calendarNames.add("Exámenes");
        }

        calendarComboBox.getItems().addAll(calendarNames);
        calendarComboBox.setValue("Mis Clases");
    }

    private void setupComponents() {
        // Configurar el ComboBox con valores temporales hasta que se inicialicen los calendarios
        calendarComboBox.getItems().addAll(
                "Mis Clases",
                "Tareas y Proyectos",
                "Personal",
                "Exámenes"
        );
        calendarComboBox.setValue("Mis Clases");

        // Configurar otros componentes
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

        // Configurar DatePicker
        datePicker.setConverter(new StringConverter<LocalDate>() {
            private DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");

            @Override
            public String toString(LocalDate date) {
                if (date != null) {
                    return dateFormatter.format(date);
                } else {
                    return "";
                }
            }

            @Override
            public LocalDate fromString(String string) {
                if (string != null && !string.isEmpty()) {
                    return LocalDate.parse(string, dateFormatter);
                } else {
                    return null;
                }
            }
        });
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
        modeLabel.setText("Crear Nuevo Evento - " + formatDate(date));
        eventFormContainer.setVisible(true);
        eventListContainer.setVisible(false);
        saveButton.setVisible(true);
        updateButton.setVisible(false);
        deleteButton.setVisible(false);
        datePicker.setValue(date);
        clearForm();

        // Asegurar que el ComboBox de calendarios esté actualizado
        if (!calendarInitialized) {
            setupCalendarComboBox();
        }
    }

    public void initializeForRead(LocalDate date, Runnable onEventChanged) {
        this.mode = "READ";
        this.selectedDate = date;
        this.onEventChanged = onEventChanged;
        modeLabel.setText("Eventos del " + formatDate(date));
        eventFormContainer.setVisible(true);
        eventListContainer.setVisible(true);
        saveButton.setVisible(false);
        updateButton.setVisible(true);
        deleteButton.setVisible(true);

        // Mostrar indicador de carga
        showLoading(true);
        eventsListView.setPlaceholder(new Label("Cargando eventos..."));

        // Asegurar que el ComboBox de calendarios esté actualizado
        if (!calendarInitialized) {
            setupCalendarComboBox();
        }

        // Cargar eventos de forma asíncrona
        eventService.getEventsForDateAsync(authService.getCurrentUser().getUserId(), date)
                .thenAccept(events -> {
                    Platform.runLater(() -> {
                        // Actualizar la UI en el hilo de JavaFX
                        updateEventsList(events);
                        showLoading(false);
                    });
                })
                .exceptionally(ex -> {
                    Platform.runLater(() -> {
                        // Mostrar error en UI
                        eventsListView.setPlaceholder(new Label("Error al cargar eventos"));
                        showLoading(false);
                        System.err.println("Error: " + ex.getMessage());
                    });
                    return null;
                });
    }

    private String formatDate(LocalDate date) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        return date.format(formatter);
    }

    private void showLoading(boolean show) {

        if (show) {
            System.out.println("Cargando datos...");
        }
    }

    private void updateEventsList(List<Event> events) {
        eventsListView.getItems().clear();
        eventsMap.clear();

        if (events.isEmpty()) {
            eventsListView.setPlaceholder(new Label("No hay eventos para esta fecha"));
            return;
        }

        for (Event event : events) {
            String displayTitle = event.getTitle();
            eventsListView.getItems().add(displayTitle);
            eventsMap.put(displayTitle, event);
        }
    }

    private void loadEventForEdit(String eventTitle) {
        Event event = eventsMap.get(eventTitle);
        if (event != null) {
            currentEvent = event;
            populateForm(event);
        }
    }

    private void populateForm(Event event) {
        titleField.setText(event.getTitle());
        descriptionArea.setText(event.getDescription());
        locationField.setText(event.getLocation());

        // Buscar el nombre del calendario para este evento
        String userId = authService.getCurrentUser().getUserId();
        List<String> calendarNames = eventService.getUserCalendarNames(userId);
        String selectedCalendarName = "Mis Clases"; // Valor por defecto

        // Para cada calendario del usuario, verificar si coincide con el ID del evento
        for (String calendarName : calendarNames) {
            String calendarId = eventService.getCalendarIdByName(userId, calendarName);
            if (calendarId.equals(event.getCalendarId())) {
                selectedCalendarName = calendarName;
                break;
            }
        }

        // Seleccionar el calendario en el ComboBox
        calendarComboBox.setValue(selectedCalendarName);

        // Configurar fecha y hora
        if (event.getStartDate() != null) {
            datePicker.setValue(event.getStartDate().toLocalDate());
            startTimeField.setText(formatTime(event.getStartDate().toLocalTime()));
        }

        if (event.getEndDate() != null) {
            endTimeField.setText(formatTime(event.getEndDate().toLocalTime()));
        }

        allDayCheckBox.setSelected(event.isAllDay());
        startTimeField.setDisable(event.isAllDay());
        endTimeField.setDisable(event.isAllDay());
    }

    private String getCalendarIdByName(String calendarName) {
        String userId = authService.getCurrentUser().getUserId();
        return eventService.getCalendarIdByName(userId, calendarName);
    }

    private String formatTime(LocalTime time) {
        return time.format(DateTimeFormatter.ofPattern("HH:mm"));
    }

    private void clearForm() {
        titleField.clear();
        descriptionArea.clear();
        locationField.clear();
        calendarComboBox.setValue("Mis Clases");
        startTimeField.setText("08:00");
        endTimeField.setText("09:00");
        allDayCheckBox.setSelected(false);
    }

    private void handleSave() {
        if (!validateForm()) return;

        Event event = new Event();
        String userId = authService.getCurrentUser().getUserId();
        event.setCreatorId(userId);
        event.setCalendarId(getCalendarIdByName(calendarComboBox.getValue()));

        populateEventFromForm(event);

        // Mostrar indicador de carga
        showLoading(true);

        // Crear evento de forma asíncrona
        eventService.createEventAsync(event)
                .thenAccept(success -> {
                    Platform.runLater(() -> {
                        showLoading(false);
                        if (success) {
                            if (onEventChanged != null) {
                                onEventChanged.run();
                            }
                            closeDialog();
                        } else {
                            showErrorAlert("No se pudo crear el evento. Intente nuevamente.");
                        }
                    });
                })
                .exceptionally(ex -> {
                    Platform.runLater(() -> {
                        showLoading(false);
                        showErrorAlert("Error al crear el evento: " + ex.getMessage());
                    });
                    return null;
                });
    }

    private void handleUpdate() {
        if (!validateForm() || currentEvent == null) return;

        populateEventFromForm(currentEvent);

        // Mostrar indicador de carga
        showLoading(true);

        // Actualizar evento de forma asíncrona
        eventService.updateEventAsync(currentEvent)
                .thenAccept(success -> {
                    Platform.runLater(() -> {
                        showLoading(false);
                        if (success) {
                            if (onEventChanged != null) {
                                onEventChanged.run();
                            }
                            closeDialog();
                        } else {
                            showErrorAlert("No se pudo actualizar el evento. Intente nuevamente.");
                        }
                    });
                })
                .exceptionally(ex -> {
                    Platform.runLater(() -> {
                        showLoading(false);
                        showErrorAlert("Error al actualizar el evento: " + ex.getMessage());
                    });
                    return null;
                });
    }

    private void handleDelete() {
        if (currentEvent == null) {
            showErrorAlert("Seleccione un evento para eliminar");
            return;
        }

        Alert confirmDialog = new Alert(Alert.AlertType.CONFIRMATION);
        confirmDialog.setTitle("Confirmar eliminación");
        confirmDialog.setHeaderText("¿Está seguro que desea eliminar este evento?");
        confirmDialog.setContentText("Esta acción no se puede deshacer.");

        Optional<ButtonType> result = confirmDialog.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            // Mostrar indicador de carga
            showLoading(true);

            // Eliminar evento de forma asíncrona
            eventService.deleteEventAsync(currentEvent.getEventId())
                    .thenAccept(success -> {
                        Platform.runLater(() -> {
                            showLoading(false);
                            if (success) {
                                if (onEventChanged != null) {
                                    onEventChanged.run();
                                }
                                closeDialog();
                            } else {
                                showErrorAlert("No se pudo eliminar el evento. Intente nuevamente.");
                            }
                        });
                    })
                    .exceptionally(ex -> {
                        Platform.runLater(() -> {
                            showLoading(false);
                            showErrorAlert("Error al eliminar el evento: " + ex.getMessage());
                        });
                        return null;
                    });
        }
    }

    private void handleCancel() {
        closeDialog();
    }

    private boolean validateForm() {
        String title = titleField.getText().trim();
        if (title.isEmpty()) {
            showErrorAlert("El título no puede estar vacío");
            return false;
        }

        try {
            LocalTime startTime = parseTime(startTimeField.getText());
            LocalTime endTime = parseTime(endTimeField.getText());

            if (!allDayCheckBox.isSelected() && startTime.isAfter(endTime)) {
                showErrorAlert("La hora de inicio debe ser anterior a la hora de fin");
                return false;
            }
        } catch (Exception e) {
            showErrorAlert("Formato de hora inválido. Use el formato HH:MM");
            return false;
        }

        return true;
    }

    private void populateEventFromForm(Event event) {
        event.setTitle(titleField.getText().trim());
        event.setDescription(descriptionArea.getText().trim());
        event.setLocation(locationField.getText().trim());

        LocalDate date = datePicker.getValue();
        LocalTime startTime = parseTime(startTimeField.getText());
        LocalTime endTime = parseTime(endTimeField.getText());

        event.setStartDate(LocalDateTime.of(date, startTime));
        event.setEndDate(LocalDateTime.of(date, endTime));
        event.setAllDay(allDayCheckBox.isSelected() ? 'Y' : 'N');

        // Por defecto, sin recurrencia
        event.setRecurrence(null);
    }

    private LocalTime parseTime(String timeString) {
        try {
            return LocalTime.parse(timeString, DateTimeFormatter.ofPattern("HH:mm"));
        } catch (Exception e) {
            // Intentar otros formatos comunes
            try {
                return LocalTime.parse(timeString, DateTimeFormatter.ofPattern("H:mm"));
            } catch (Exception e2) {
                throw new IllegalArgumentException("Formato de hora inválido: " + timeString);
            }
        }
    }

    private void showErrorAlert(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void closeDialog() {
        Stage stage = (Stage) cancelButton.getScene().getWindow();
        stage.close();
    }
}