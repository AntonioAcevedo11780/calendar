package com.utez.calendario.controllers;

import com.utez.calendario.models.Calendar;
import com.utez.calendario.models.Event;
import com.utez.calendario.models.User;
import com.utez.calendario.services.AuthService;
import com.utez.calendario.services.EventService;
import com.utez.calendario.services.TimeService;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.net.URL;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;

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
    @FXML private HBox recurrenceOptionsBox;
    @FXML private CheckBox cuatrimestreCheckBox;
    @FXML private HBox weekdaysBox;
    @FXML private CheckBox mondayCheckBox;
    @FXML private CheckBox tuesdayCheckBox;
    @FXML private CheckBox wednesdayCheckBox;
    @FXML private CheckBox thursdayCheckBox;
    @FXML private CheckBox fridayCheckBox;
    @FXML private CheckBox saturdayCheckBox;
    @FXML private HBox recurrenceSection;

    // Elementos para vista de lista de eventos (múltiples eventos en un día)
    @FXML private ScrollPane eventListScrollPane;
    @FXML private VBox eventListContainer;
    @FXML private Label dayEventsTitle;
    @FXML private Label eventCountLabel;
    @FXML private Button addEventButton;

    private EventService eventService;
    private AuthService authService;
    private String mode = "CREATE"; // CREATE, VIEW, EDIT, VIEW_LIST
    private Event currentEvent;
    private LocalDate selectedDate;
    private LocalTime selectedStartTime;
    private LocalTime selectedEndTime;
    private Runnable onEventChanged;
    private boolean calendarInitialized = false;
    private boolean isTeacher = false;

    // Cache para calendarios
    private List<Calendar> allCalendarsCache = new ArrayList<>();
    private Map<String, String> calendarColorCache = new HashMap<>();

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        System.out.println("\n=== INICIALIZANDO EVENT DIALOG ===");
        eventService = EventService.getInstance();
        authService = AuthService.getInstance();

        if (authService.getCurrentUser() != null) {
            String userId = authService.getCurrentUser().getUserId();
            System.out.println("Usuario: " + userId);

            // Cargar calendarios de forma asíncrona
            loadUserCalendarsAsync(userId);
        } else {
            System.err.println("No hay usuario logueado");
        }

        setupComponents();
        setupEventHandlers();
        System.out.println("Event Dialog inicializado correctamente");
        System.out.println("==================================\n");
    }

    /**
     * Carga los calendarios del usuario de forma asíncrona
     */
    private void loadUserCalendarsAsync(String userId) {
        CompletableFuture.supplyAsync(() -> {
            try {
                // Cargar todos los calendarios del usuario
                List<Calendar> calendars = Calendar.getAllUserCalendars(userId);
                if (calendars == null || calendars.isEmpty()) {
                    // Fallback: crear calendarios básicos
                    calendars = createFallbackCalendars(userId);
                }
                return calendars;
            } catch (Exception e) {
                System.err.println("Error cargando calendarios: " + e.getMessage());
                return createFallbackCalendars(userId);
            }
        }).thenAccept(calendars -> {
            Platform.runLater(() -> {
                allCalendarsCache = calendars;

                // Crear cache de colores
                calendarColorCache.clear();
                for (Calendar cal : calendars) {
                    calendarColorCache.put(cal.getCalendarId(), cal.getColor());
                }

                setupCalendarComboBox();
                calendarInitialized = true;
                System.out.println("Calendarios cargados: " + calendars.size());
            });
        }).exceptionally(throwable -> {
            Platform.runLater(() -> {
                System.err.println("Error fatal cargando calendarios: " + throwable.getMessage());
                setupFallbackCalendarComboBox();
                calendarInitialized = true;
            });
            return null;
        });
    }

    /**
     * Crea calendarios básicos como fallback
     */
    private List<Calendar> createFallbackCalendars(String userId) {
        List<Calendar> fallbackCalendars = new ArrayList<>();

        fallbackCalendars.add(new Calendar("CAL0000001", "Mis Clases", "#1E76E8", userId));
        fallbackCalendars.add(new Calendar("CAL0000002", "Tareas y Proyectos", "#2c2c2c", userId));
        fallbackCalendars.add(new Calendar("CAL0000003", "Personal", "#53C925", userId));
        fallbackCalendars.add(new Calendar("CAL0000004", "Exámenes", "#f2c51f", userId));
        fallbackCalendars.add(new Calendar("CAL0000005", "Días Festivos", "#FF6B35", userId));
        fallbackCalendars.add(new Calendar("CAL0000006", "UTEZ", "#8B5CF6", userId));

        return fallbackCalendars;
    }
    /**
     * Establece si el usuario es docente y muestra/oculta las opciones correspondientes
     */
    public void setIsTeacher(boolean isTeacher) {
        this.isTeacher = isTeacher;

        // Mostrar u ocultar opciones específicas para docentes
        Platform.runLater(() -> {
            // Mostrar u ocultar sección completa de recurrencia
            if (recurrenceSection != null) {
                recurrenceSection.setVisible(isTeacher);
                recurrenceSection.setManaged(isTeacher);
            }

            // Inicialmente ocultar la selección de días
            if (weekdaysBox != null) {
                weekdaysBox.setVisible(false);
                weekdaysBox.setManaged(false);
            }

            // Si es docente, mostrar opción de invitar alumnos
            if (addGuestsFormButton != null) {
                // Cambiar el texto para docentes
                if (isTeacher) {
                    addGuestsFormButton.setText("Añadir estudiantes");
                    addGuestsFormButton.setTooltip(new Tooltip("Agregar estudiantes a este evento"));
                } else {
                    addGuestsFormButton.setVisible(false);
                    addGuestsFormButton.setManaged(false);
                }
            }
        });
    }

    /**
     * Configura las opciones de recurrencia y sus comportamientos.
     * Llamar a este método desde initialize()
     */
    private void setupRecurrenceOptions() {
        if (cuatrimestreCheckBox != null) {
            cuatrimestreCheckBox.setOnAction(e -> {
                boolean isSelected = cuatrimestreCheckBox.isSelected();

                // Mostrar u ocultar selección de días de la semana
                if (weekdaysBox != null) {
                    weekdaysBox.setVisible(isSelected);
                    weekdaysBox.setManaged(isSelected);
                }

                // Si se deselecciona, limpiar las selecciones de días
                if (!isSelected) {
                    if (mondayCheckBox != null) mondayCheckBox.setSelected(false);
                    if (tuesdayCheckBox != null) tuesdayCheckBox.setSelected(false);
                    if (wednesdayCheckBox != null) wednesdayCheckBox.setSelected(false);
                    if (thursdayCheckBox != null) thursdayCheckBox.setSelected(false);
                    if (fridayCheckBox != null) fridayCheckBox.setSelected(false);
                    if (saturdayCheckBox != null) saturdayCheckBox.setSelected(false);
                }
            });
        }
    }

    /**
     * Inicializa el diálogo para crear un nuevo evento
     */
    public void initializeForCreate(LocalDate date, Runnable onEventChanged) {
        this.mode = "CREATE";
        this.selectedDate = date;
        this.onEventChanged = onEventChanged;

        Platform.runLater(() -> {
            if (dialogTitle != null) {
                dialogTitle.setText("Nuevo evento");
            }
            showFormView();

            // Configurar fecha seleccionada
            if (datePicker != null) {
                datePicker.setValue(date);
            }

            // Configurar horarios por defecto
            setDefaultTimes();

            // Configurar opciones de recurrencia si es docente
            setupRecurrenceOptions();

            // Asegurarse que los botones correctos están visibles
            if (saveButton != null) {
                saveButton.setVisible(true);
                saveButton.setManaged(true);
            }
            if (updateButton != null) {
                updateButton.setVisible(false);
                updateButton.setManaged(false);
            }
            if (deleteFormButton != null) {
                deleteFormButton.setVisible(false);
                deleteFormButton.setManaged(false);
            }
        });
    }

    /**
     * Inicializa el diálogo para crear un evento con hora específica
     */
    public void initializeForCreateWithTime(LocalDate date, LocalTime startTime, LocalTime endTime, Runnable onEventChanged) {
        this.selectedStartTime = startTime;
        this.selectedEndTime = endTime;
        initializeForCreate(date, onEventChanged);

        // Esperar a que los componentes estén listos antes de configurar los tiempos
        Platform.runLater(() -> {
            if (startTime != null && startTimeComboBox != null) {
                startTimeComboBox.setValue(startTime.format(DateTimeFormatter.ofPattern("HH:mm")));
            }
            if (endTime != null && endTimeComboBox != null) {
                endTimeComboBox.setValue(endTime.format(DateTimeFormatter.ofPattern("HH:mm")));
            }
        });
    }

    /**
     * Inicializa el diálogo para ver eventos de una fecha
     */
    public void initializeForRead(LocalDate date, Runnable onEventChanged) {
        this.mode = "VIEW_LIST";
        this.selectedDate = date;
        this.onEventChanged = onEventChanged;

        Platform.runLater(() -> {
            loadEventsForDateAsync(date);
        });
    }

    /**
     * Inicializa el diálogo para ver un evento específico
     */
    public void initializeForViewEvent(Event event, Runnable onEventChanged) {
        this.mode = "VIEW";
        this.currentEvent = event;
        this.selectedDate = event.getStartDate().toLocalDate();
        this.onEventChanged = onEventChanged;

        Platform.runLater(() -> {
            if (dialogTitle != null) {
                dialogTitle.setText(event.getTitle());
            }
            showEventView();
            loadEventToView(event);

            // Determinar si el usuario actual es el creador del evento
            boolean isCreator = false;
            if (authService.getCurrentUser() != null && event.getCreatorId() != null) {
                isCreator = authService.getCurrentUser().getUserId().equals(event.getCreatorId());
            }

            // Solo mostrar opciones de edición/eliminación si es el creador o administrador
            boolean canEdit = isCreator || (authService.getCurrentUser() != null && authService.getCurrentUser().isAdmin());
            if (editButton != null) {
                editButton.setVisible(canEdit);
                editButton.setManaged(canEdit);
            }
            if (deleteViewButton != null) {
                deleteViewButton.setVisible(canEdit);
                deleteViewButton.setManaged(canEdit);
            }
        });
    }

    /**
     * Inicializa el diálogo para editar un evento
     */
    public void initializeForEdit(Event event, Runnable onEventChanged) {
        this.mode = "EDIT";
        this.currentEvent = event;
        this.selectedDate = event.getStartDate().toLocalDate();
        this.onEventChanged = onEventChanged;

        Platform.runLater(() -> {
            if (dialogTitle != null) {
                dialogTitle.setText("Editar evento");
            }

            showFormView();

            // Esperar a que los calendarios estén cargados
            waitForCalendarsAndLoadEvent(event);

            // Configurar opciones de recurrencia si es docente
            setupRecurrenceOptions();

            // Determinar si el usuario actual es el creador del evento
            boolean isCreator = false;
            if (authService.getCurrentUser() != null && event.getCreatorId() != null) {
                isCreator = authService.getCurrentUser().getUserId().equals(event.getCreatorId());
            }

            // Solo mostrar opciones de edición/eliminación si es el creador o administrador
            boolean canEdit = isCreator || (authService.getCurrentUser() != null && authService.getCurrentUser().isAdmin());
            if (!canEdit) {
                // Si no puede editar, mostrar en modo lectura
                switchToViewMode();
                return;
            }

            // Configurar botones de edición
            if (saveButton != null) {
                saveButton.setVisible(false);
                saveButton.setManaged(false);
            }
            if (updateButton != null) {
                updateButton.setVisible(true);
                updateButton.setManaged(true);
            }
            if (deleteFormButton != null) {
                deleteFormButton.setVisible(true);
                deleteFormButton.setManaged(true);
            }
        });
    }

    /**
     * Espera a que los calendarios estén cargados antes de cargar el evento al formulario
     */
    private void waitForCalendarsAndLoadEvent(Event event) {
        if (calendarInitialized) {
            loadEventToForm(event);
        } else {
            // Esperar 100ms y volver a intentar
            Platform.runLater(() -> {
                Task<Void> waitTask = new Task<Void>() {
                    @Override
                    protected Void call() throws Exception {
                        int attempts = 0;
                        while (!calendarInitialized && attempts < 50) { // Máximo 5 segundos
                            Thread.sleep(100);
                            attempts++;
                        }
                        return null;
                    }
                };

                waitTask.setOnSucceeded(e -> Platform.runLater(() -> loadEventToForm(event)));
                waitTask.setOnFailed(e -> {
                    System.err.println("Timeout esperando calendarios");
                    Platform.runLater(() -> loadEventToForm(event));
                });

                new Thread(waitTask).start();
            });
        }
    }

    private void setupComponents() {
        // Configurar DatePicker
        if (datePicker != null) {
            datePicker.setValue(TimeService.getInstance().now().toLocalDate());
        }

        // Configurar ComboBox de tiempo
        setupTimeComboBoxes();

        // Configurar CheckBox para todo el día
        if (allDayCheckBox != null) {
            allDayCheckBox.setSelected(false);
            allDayCheckBox.selectedProperty().addListener((obs, oldVal, newVal) -> {
                if (startTimeComboBox != null) startTimeComboBox.setDisable(newVal);
                if (endTimeComboBox != null) endTimeComboBox.setDisable(newVal);

                if (newVal) {
                    if (startTimeComboBox != null) startTimeComboBox.setValue("00:00");
                    if (endTimeComboBox != null) endTimeComboBox.setValue("23:59");
                } else {
                    setDefaultTimes();
                }
            });
        }
    }

    private void setDefaultTimes() {
        if (startTimeComboBox != null) {
            startTimeComboBox.setValue(selectedStartTime != null ?
                    selectedStartTime.format(DateTimeFormatter.ofPattern("HH:mm")) : "09:00");
        }
        if (endTimeComboBox != null) {
            endTimeComboBox.setValue(selectedEndTime != null ?
                    selectedEndTime.format(DateTimeFormatter.ofPattern("HH:mm")) : "10:00");
        }
    }

    private String formatEventDate(Event event) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", Locale.of("es", "ES"));
        String dateText = event.getStartDate().toLocalDate().format(formatter);

        if (!dateText.isEmpty()) {
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
            if (startTimeComboBox != null) {
                startTimeComboBox.valueProperty().addListener((obs, oldVal, newVal) -> {
                    if (newVal != null && allDayCheckBox != null && !allDayCheckBox.isSelected()) {
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
    }

    private void setupCalendarComboBox() {
        if (calendarComboBox != null && !allCalendarsCache.isEmpty()) {
            try {
                ObservableList<String> calendarNames = FXCollections.observableArrayList();

                for (Calendar cal : allCalendarsCache) {
                    calendarNames.add(cal.getName());
                }

                calendarComboBox.setItems(calendarNames);

                // Seleccionar el primer calendario por defecto
                if (!calendarNames.isEmpty()) {
                    calendarComboBox.setValue(calendarNames.get(0));
                }

                System.out.println("ComboBox configurado con " + calendarNames.size() + " calendarios");
            } catch (Exception e) {
                System.err.println("Error configurando ComboBox de calendarios: " + e.getMessage());
                setupFallbackCalendarComboBox();
            }
        }
    }

    private void setupFallbackCalendarComboBox() {
        if (calendarComboBox != null) {
            calendarComboBox.setItems(FXCollections.observableArrayList("Mi Calendario"));
            calendarComboBox.setValue("Mi Calendario");
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

        if (addEventButton != null) {
            addEventButton.setOnAction(e -> {
                closeDialog();
                // Abrir nuevo diálogo para crear evento
                Platform.runLater(() -> {
                    if (onEventChanged != null) {
                        // Aquí podrías llamar a un método para crear un nuevo evento
                        // Por ahora solo cerramos el diálogo actual
                    }
                });
            });
        }
    }

    /**
     * Cambia la vista para mostrar el formulario
     */
    private void showFormView() {
        // Ocultar otras vistas
        hideAllViews();

        // Mostrar formulario
        if (eventFormContainer != null) {
            eventFormContainer.setVisible(true);
            eventFormContainer.setManaged(true);
        }
        if (formButtonContainer != null) {
            formButtonContainer.setVisible(true);
            formButtonContainer.setManaged(true);
        }

        // Configurar botones según el modo
        configureFormButtons();
    }

    /**
     * Cambia la vista para mostrar el evento
     */
    private void showEventView() {
        // Ocultar otras vistas
        hideAllViews();

        // Mostrar vista de evento
        if (eventViewContainer != null) {
            eventViewContainer.setVisible(true);
            eventViewContainer.setManaged(true);
        }
        if (viewButtonContainer != null) {
            viewButtonContainer.setVisible(true);
            viewButtonContainer.setManaged(true);
        }
    }

    /**
     * Cambia la vista para mostrar la lista de eventos
     */
    private void showEventListView() {
        // Ocultar otras vistas
        hideAllViews();

        // Mostrar lista de eventos
        if (eventListScrollPane != null) {
            eventListScrollPane.setVisible(true);
            eventListScrollPane.setManaged(true);
        }
    }

    private void hideAllViews() {
        // Ocultar vista de evento
        if (eventViewContainer != null) {
            eventViewContainer.setVisible(false);
            eventViewContainer.setManaged(false);
        }
        if (viewButtonContainer != null) {
            viewButtonContainer.setVisible(false);
            viewButtonContainer.setManaged(false);
        }

        // Ocultar formulario
        if (eventFormContainer != null) {
            eventFormContainer.setVisible(false);
            eventFormContainer.setManaged(false);
        }
        if (formButtonContainer != null) {
            formButtonContainer.setVisible(false);
            formButtonContainer.setManaged(false);
        }

        // Ocultar lista de eventos
        if (eventListScrollPane != null) {
            eventListScrollPane.setVisible(false);
            eventListScrollPane.setManaged(false);
        }
    }

    private void configureFormButtons() {
        if ("CREATE".equals(mode)) {
            if (saveButton != null) {
                saveButton.setVisible(true);
                saveButton.setManaged(true);
            }
            if (updateButton != null) {
                updateButton.setVisible(false);
                updateButton.setManaged(false);
            }
            if (deleteFormButton != null) {
                deleteFormButton.setVisible(false);
                deleteFormButton.setManaged(false);
            }
        } else if ("EDIT".equals(mode)) {
            if (saveButton != null) {
                saveButton.setVisible(false);
                saveButton.setManaged(false);
            }
            if (updateButton != null) {
                updateButton.setVisible(true);
                updateButton.setManaged(true);
            }
            if (deleteFormButton != null) {
                deleteFormButton.setVisible(true);
                deleteFormButton.setManaged(true);
            }
        }
    }

    /**
     * Método para crear un evento desde el formulario considerando recurrencia
     */
    private List<Event> createRecurringEventsFromForm() {
        List<Event> events = new ArrayList<>();

        // Si no es evento recurrente o no es docente, crear un solo evento
        if (!isTeacher || cuatrimestreCheckBox == null || !cuatrimestreCheckBox.isSelected()) {
            events.add(createEventFromForm());
            return events;
        }

        // Obtener días seleccionados para recurrencia
        List<Integer> selectedDays = new ArrayList<>();
        if (mondayCheckBox != null && mondayCheckBox.isSelected()) selectedDays.add(DayOfWeek.MONDAY.getValue());
        if (tuesdayCheckBox != null && tuesdayCheckBox.isSelected()) selectedDays.add(DayOfWeek.TUESDAY.getValue());
        if (wednesdayCheckBox != null && wednesdayCheckBox.isSelected()) selectedDays.add(DayOfWeek.WEDNESDAY.getValue());
        if (thursdayCheckBox != null && thursdayCheckBox.isSelected()) selectedDays.add(DayOfWeek.THURSDAY.getValue());
        if (fridayCheckBox != null && fridayCheckBox.isSelected()) selectedDays.add(DayOfWeek.FRIDAY.getValue());
        if (saturdayCheckBox != null && saturdayCheckBox.isSelected()) selectedDays.add(DayOfWeek.SATURDAY.getValue());

        // Si no hay días seleccionados, crear un solo evento en la fecha elegida
        if (selectedDays.isEmpty()) {
            events.add(createEventFromForm());
            return events;
        }

        // Crear eventos para cada día seleccionado en un periodo de 4 meses (16 semanas)
        LocalDate baseDate = datePicker.getValue();
        LocalTime startTime = parseTime(startTimeComboBox.getValue());
        LocalTime endTime = parseTime(endTimeComboBox.getValue());

        for (int week = 0; week < 16; week++) {
            for (Integer dayValue : selectedDays) {
                LocalDate targetDate = baseDate.with(java.time.temporal.TemporalAdjusters.nextOrSame(DayOfWeek.of(dayValue)));
                targetDate = targetDate.plusWeeks(week);

                Event event = createBaseEventFromForm();
                event.setEventId(generateEventId() + "-" + week + "-" + dayValue);
                event.setStartDate(LocalDateTime.of(targetDate, startTime));
                event.setEndDate(LocalDateTime.of(targetDate, endTime));

                events.add(event);
            }
        }

        return events;
    }

    /**
     * Carga los eventos para una fecha específica de forma asíncrona
     */
    private void loadEventsForDateAsync(LocalDate date) {
        if (authService.getCurrentUser() == null) {
            showAlert("Error", "No hay usuario logueado", Alert.AlertType.ERROR);
            return;
        }

        String userId = authService.getCurrentUser().getUserId();

        CompletableFuture.supplyAsync(() -> {
            try {
                return eventService.getEventsForDate(userId, date);
            } catch (Exception e) {
                System.err.println("Error cargando eventos para fecha: " + e.getMessage());
                return new ArrayList<Event>();
            }
        }).thenAccept(events -> {
            Platform.runLater(() -> {
                handleLoadedEvents(events, date);
            });
        }).exceptionally(throwable -> {
            Platform.runLater(() -> {
                System.err.println("Error cargando eventos: " + throwable.getMessage());
                showAlert("Error", "No se pudieron cargar los eventos: " + throwable.getMessage(),
                        Alert.AlertType.ERROR);
            });
            return null;
        });
    }

    private void handleLoadedEvents(List<Event> events, LocalDate date) {
        if (events.isEmpty()) {
            // No hay eventos, abrir modo crear
            initializeForCreate(date, onEventChanged);
        } else if (events.size() == 1) {
            // Un solo evento, abrir en modo vista
            initializeForViewEvent(events.get(0), onEventChanged);
        } else {
            // Múltiples eventos, mostrar lista
            showEventListForDate(events, date);
        }
    }

    /**
     * Muestra la lista de eventos para una fecha específica
     */
    private void showEventListForDate(List<Event> events, LocalDate date) {
        this.mode = "VIEW_LIST";

        if (dayEventsTitle != null) {
            DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM 'de' yyyy",
                    Locale.of("es", "ES"));
            String dateText = date.format(dateFormatter);
            dayEventsTitle.setText(dateText.substring(0, 1).toUpperCase() + dateText.substring(1));
        }

        if (eventCountLabel != null) {
            eventCountLabel.setText(events.size() + " eventos");
        }

        if (eventListContainer != null) {
            eventListContainer.getChildren().clear();

            for (Event event : events) {
                VBox eventBox = createEventListItem(event);
                eventListContainer.getChildren().add(eventBox);
            }
        }

        showEventListView();
    }

    /**
     * Crea un elemento de lista para un evento
     */
    private VBox createEventListItem(Event event) {
        VBox eventContainer = new VBox(8);
        eventContainer.getStyleClass().add("event-list-item");

        // Título del evento
        Label titleLabel = new Label(event.getTitle());
        titleLabel.getStyleClass().add("event-list-title");

        // Tiempo del evento
        String timeText = formatEventTime(event);
        Label timeLabel = new Label(timeText);
        timeLabel.getStyleClass().add("event-list-time");

        // Botones de acción
        HBox buttonBox = new HBox(8);
        buttonBox.getStyleClass().add("event-list-buttons");

        Button viewButton = new Button("Ver");
        viewButton.getStyleClass().add("secondary-button");
        viewButton.setOnAction(e -> {
            initializeForViewEvent(event, onEventChanged);
        });

        Button editButton = new Button("Editar");
        editButton.getStyleClass().add("primary-button");
        editButton.setOnAction(e -> {
            initializeForEdit(event, onEventChanged);
        });

        buttonBox.getChildren().addAll(viewButton, editButton);

        eventContainer.getChildren().addAll(titleLabel, timeLabel, buttonBox);

        return eventContainer;
    }

    /**
     * Carga un evento en la vista de solo lectura
     */
    private void loadEventToView(Event event) {
        if (event == null) return;

        // Configurar fecha y hora
        if (eventTimeLabel != null) {
            String timeText = formatEventTime(event);
            String dateText = formatEventDate(event);
            eventTimeLabel.setText(dateText + " • " + timeText);
        }

        // Configurar ubicación
        if (locationViewRow != null && eventLocationLabel != null) {
            if (event.getLocation() != null && !event.getLocation().trim().isEmpty()) {
                eventLocationLabel.setText(event.getLocation());
                locationViewRow.setVisible(true);
                locationViewRow.setManaged(true);
            } else {
                locationViewRow.setVisible(false);
                locationViewRow.setManaged(false);
            }
        }

        // Configurar descripción
        if (descriptionViewContainer != null && eventDescriptionLabel != null) {
            if (event.getDescription() != null && !event.getDescription().trim().isEmpty()) {
                eventDescriptionLabel.setText(event.getDescription());
                descriptionViewContainer.setVisible(true);
                descriptionViewContainer.setManaged(true);
            } else {
                descriptionViewContainer.setVisible(false);
                descriptionViewContainer.setManaged(false);
            }
        }

        // Configurar información del calendario
        if (eventCalendarLabel != null) {
            String calendarName = getCalendarNameById(event.getCalendarId());
            eventCalendarLabel.setText(calendarName);
        }

        // Información del creador
        if (createdByLabel != null) {
            String creatorName = "Sistema";

            // Obtener el usuario creador de una forma segura
            try {
                if (event.getCreatorId() != null && !event.getCreatorId().isEmpty()) {
                    // Intentar usar el authService primero
                    if (authService.getCurrentUser() != null &&
                            authService.getCurrentUser().getUserId().equals(event.getCreatorId())) {
                        creatorName = authService.getCurrentUser().getDisplayInfo();
                    } else {
                        // Comprobar si el método estático existe
                        try {
                            // Usar reflexión para verificar si el método existe
                            java.lang.reflect.Method getUserByIdMethod =
                                    User.class.getMethod("getUserById", String.class);

                            // Si llegamos aquí, el método existe
                            User creator = (User) getUserByIdMethod.invoke(null, event.getCreatorId());
                            if (creator != null) {
                                creatorName = creator.getDisplayInfo();
                            }
                        } catch (NoSuchMethodException e) {
                            // El método no existe, usar el ID directamente
                            creatorName = "Usuario " + event.getCreatorId();
                        } catch (Exception e) {
                            // Cualquier otra excepción
                            System.err.println("Error obteniendo creador del evento: " + e.getMessage());
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("Error al obtener información del creador: " + e.getMessage());
            }

            createdByLabel.setText("Creado por: " + creatorName);
        }
    }

    /**
     * Carga un evento en el formulario para edición
     */
    private void loadEventToForm(Event event) {
        if (event == null) return;

        if (titleField != null) {
            titleField.setText(event.getTitle());
        }

        if (descriptionArea != null) {
            descriptionArea.setText(event.getDescription() != null ? event.getDescription() : "");
        }

        if (locationField != null) {
            locationField.setText(event.getLocation() != null ? event.getLocation() : "");
        }

        // Configurar calendario
        if (calendarComboBox != null) {
            String calendarName = getCalendarNameById(event.getCalendarId());
            if (calendarName != null) {
                calendarComboBox.setValue(calendarName);
            }
        }

        // Configurar fecha y hora
        if (datePicker != null) {
            datePicker.setValue(event.getStartDate().toLocalDate());
        }

        boolean isAllDay = event.isAllDay();
        if (allDayCheckBox != null) {
            allDayCheckBox.setSelected(isAllDay);
        }

        if (startTimeComboBox != null && endTimeComboBox != null) {
            if (isAllDay) {
                startTimeComboBox.setValue("00:00");
                endTimeComboBox.setValue("23:59");
            } else {
                startTimeComboBox.setValue(formatTime(event.getStartDate()));
                endTimeComboBox.setValue(formatTime(event.getEndDate()));
            }
        }
    }

    /**
     * Obtiene el nombre del calendario por su ID
     */
    private String getCalendarNameById(String calendarId) {
        // Buscar en cache primero
        for (Calendar cal : allCalendarsCache) {
            if (cal.getCalendarId().equals(calendarId)) {
                return cal.getName();
            }
        }

        // Fallback con nombres conocidos
        switch (calendarId) {
            case "CAL0000001": return "Mis Clases";
            case "CAL0000002": return "Tareas y Proyectos";
            case "CAL0000003": return "Personal";
            case "CAL0000004": return "Exámenes";
            case "CAL0000005": return "Días Festivos";
            case "CAL0000006": return "UTEZ";
            default: return "Calendario";
        }
    }

    /**
     * Obtiene el ID del calendario por su nombre
     */
    private String getCalendarIdByName(String calendarName) {
        // Buscar en cache
        for (Calendar cal : allCalendarsCache) {
            if (cal.getName().equals(calendarName)) {
                return cal.getCalendarId();
            }
        }

        // Fallback
        switch (calendarName) {
            case "Mis Clases": return "CAL0000001";
            case "Tareas y Proyectos": return "CAL0000002";
            case "Personal": return "CAL0000003";
            case "Exámenes": return "CAL0000004";
            case "Días Festivos": return "CAL0000005";
            case "UTEZ": return "CAL0000006";
            default: return "CAL0000001"; // Por defecto
        }
    }

    /**
     * Cambia al modo de edición
     */
    private void switchToEditMode() {
        mode = "EDIT";
        if (dialogTitle != null) {
            dialogTitle.setText("Editar evento");
        }
        showFormView();
        loadEventToForm(currentEvent);
    }

    /**
     * Cambia al modo de vista
     */
    private void switchToViewMode() {
        mode = "VIEW";
        if (dialogTitle != null && currentEvent != null) {
            dialogTitle.setText(currentEvent.getTitle());
        }
        showEventView();
        if (currentEvent != null) {
            loadEventToView(currentEvent);
        }
    }

    /**
     * Maneja el guardado de un nuevo evento
     */
    @FXML
    private void handleSave() {
        if (!validateForm()) return;

        try {
            // Obtener eventos (uno o varios si es recurrente)
            List<Event> newEvents = createRecurringEventsFromForm();

            // Verificar si hay conflictos para cada evento
            boolean hasConflicts = false;
            for (Event event : newEvents) {
                if (!isTimeSlotAvailable(event)) {
                    hasConflicts = true;
                    break;
                }
            }

            if (hasConflicts) {
                showAlert("Conflicto de horario",
                        "Ya hay un evento en alguno de los horarios seleccionados.",
                        Alert.AlertType.WARNING);
                return;
            }

            // Crear eventos de forma asíncrona
            CompletableFuture.supplyAsync(() -> {
                boolean allSuccess = true;
                for (Event event : newEvents) {
                    if (!eventService.createEvent(event)) {
                        allSuccess = false;
                    }
                }
                return allSuccess;
            }).thenAccept(success -> {
                Platform.runLater(() -> {
                    if (success) {
                        int count = newEvents.size();
                        String message = count > 1 ?
                                count + " eventos creados exitosamente" :
                                "Evento creado exitosamente";
                        showAlert("Éxito", message, Alert.AlertType.INFORMATION);
                        if (onEventChanged != null) onEventChanged.run();
                        closeDialog();
                    } else {
                        showAlert("Error", "No se pudieron crear todos los eventos", Alert.AlertType.ERROR);
                    }
                });
            }).exceptionally(throwable -> {
                Platform.runLater(() -> {
                    System.err.println("Error creando eventos: " + throwable.getMessage());
                    showAlert("Error", "Error al crear eventos: " + throwable.getMessage(), Alert.AlertType.ERROR);
                });
                return null;
            });
        } catch (Exception e) {
            System.err.println("Error creando eventos: " + e.getMessage());
            showAlert("Error", "Error al crear eventos: " + e.getMessage(), Alert.AlertType.ERROR);
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

            // Actualizar evento de forma asíncrona
            CompletableFuture.supplyAsync(() -> {
                return eventService.updateEvent(currentEvent);
            }).thenAccept(success -> {
                Platform.runLater(() -> {
                    if (success) {
                        showAlert("Éxito", "Evento actualizado exitosamente", Alert.AlertType.INFORMATION);
                        if (onEventChanged != null) onEventChanged.run();
                        switchToViewMode();
                    } else {
                        showAlert("Error", "No se pudo actualizar el evento", Alert.AlertType.ERROR);
                    }
                });
            }).exceptionally(throwable -> {
                Platform.runLater(() -> {
                    System.err.println("Error actualizando evento: " + throwable.getMessage());
                    showAlert("Error", "Error al actualizar evento: " + throwable.getMessage(), Alert.AlertType.ERROR);
                });
                return null;
            });

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
            // Eliminar evento de forma asíncrona
            CompletableFuture.supplyAsync(() -> {
                return eventService.deleteEvent(currentEvent.getEventId());
            }).thenAccept(success -> {
                Platform.runLater(() -> {
                    if (success) {
                        showAlert("Éxito", "Evento eliminado exitosamente", Alert.AlertType.INFORMATION);
                        if (onEventChanged != null) onEventChanged.run();
                        closeDialog();
                    } else {
                        showAlert("Error", "No se pudo eliminar el evento", Alert.AlertType.ERROR);
                    }
                });
            }).exceptionally(throwable -> {
                Platform.runLater(() -> {
                    System.err.println("Error eliminando evento: " + throwable.getMessage());
                    showAlert("Error", "Error al eliminar evento: " + throwable.getMessage(), Alert.AlertType.ERROR);
                });
                return null;
            });
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

        if (titleField != null) {
            event.setTitle(titleField.getText().trim());
        }

        if (descriptionArea != null) {
            event.setDescription(descriptionArea.getText().trim());
        }

        if (locationField != null) {
            event.setLocation(locationField.getText().trim());
        }

        // Generar ID único
        event.setEventId(generateEventId());

        // Configurar calendario
        if (authService.getCurrentUser() != null && calendarComboBox != null) {
            String userId = authService.getCurrentUser().getUserId();
            String calendarName = calendarComboBox.getValue();
            String calendarId = getCalendarIdByName(calendarName);
            event.setCalendarId(calendarId);
            event.setCreatorId(userId);
        }

        // Configurar fecha y hora
        setupEventDateTime(event);

        return event;
    }

    /**
     * Método auxiliar para crear un evento base del formulario (sin fechas)
     */
    private Event createBaseEventFromForm() {
        Event event = new Event();

        if (titleField != null) {
            event.setTitle(titleField.getText().trim());
        }

        if (descriptionArea != null) {
            event.setDescription(descriptionArea.getText().trim());
        }

        if (locationField != null) {
            event.setLocation(locationField.getText().trim());
        }

        // Configurar calendario
        if (authService.getCurrentUser() != null && calendarComboBox != null) {
            String userId = authService.getCurrentUser().getUserId();
            String calendarName = calendarComboBox.getValue();
            String calendarId = getCalendarIdByName(calendarName);
            event.setCalendarId(calendarId);
            event.setCreatorId(userId);
        }

        // Configurar todo el día
        if (allDayCheckBox != null) {
            event.setAllDay(allDayCheckBox.isSelected() ? 'Y' : 'N');
        }

        return event;
    }

    /**
     * Actualiza un evento con los datos del formulario
     */
    private void updateEventFromForm(Event event) {
        if (titleField != null) {
            event.setTitle(titleField.getText().trim());
        }

        if (descriptionArea != null) {
            event.setDescription(descriptionArea.getText().trim());
        }

        if (locationField != null) {
            event.setLocation(locationField.getText().trim());
        }

        // Actualizar calendario
        if (authService.getCurrentUser() != null && calendarComboBox != null) {
            String userId = authService.getCurrentUser().getUserId();
            String calendarName = calendarComboBox.getValue();
            String calendarId = getCalendarIdByName(calendarName);
            event.setCalendarId(calendarId);
        }

        // Configurar fecha y hora
        setupEventDateTime(event);
    }

    /**
     * Configura la fecha y hora del evento
     */
    private void setupEventDateTime(Event event) {
        LocalDate date = datePicker != null ? datePicker.getValue() : TimeService.getInstance().now().toLocalDate();
        boolean isAllDay = allDayCheckBox != null && allDayCheckBox.isSelected();

        event.setAllDay(isAllDay ? 'Y' : 'N');

        if (isAllDay) {
            event.setStartDate(LocalDateTime.of(date, LocalTime.MIN));
            event.setEndDate(LocalDateTime.of(date, LocalTime.MAX));
        } else {
            LocalTime startTime = LocalTime.MIN;
            LocalTime endTime = LocalTime.MAX;

            try {
                if (startTimeComboBox != null && startTimeComboBox.getValue() != null) {
                    startTime = LocalTime.parse(startTimeComboBox.getValue());
                }
                if (endTimeComboBox != null && endTimeComboBox.getValue() != null) {
                    endTime = LocalTime.parse(endTimeComboBox.getValue());
                }
            } catch (Exception e) {
                System.err.println("Error parsing time: " + e.getMessage());
                startTime = LocalTime.of(9, 0);
                endTime = LocalTime.of(10, 0);
            }

            event.setStartDate(LocalDateTime.of(date, startTime));
            event.setEndDate(LocalDateTime.of(date, endTime));
        }
    }

    /**
     * Valida los datos del formulario
     */
    private boolean validateForm() {
        if (titleField == null) {
            showAlert("Error", "Campo título no encontrado", Alert.AlertType.ERROR);
            return false;
        }

        String title = titleField.getText().trim();
        if (title.isEmpty()) {
            showAlert("Error de validación", "El título no puede estar vacío", Alert.AlertType.WARNING);
            titleField.requestFocus();
            return false;
        }

        if (allDayCheckBox != null && !allDayCheckBox.isSelected()) {
            try {
                if (startTimeComboBox == null || endTimeComboBox == null) {
                    showAlert("Error", "Campos de tiempo no encontrados", Alert.AlertType.ERROR);
                    return false;
                }

                String startTimeStr = startTimeComboBox.getValue();
                String endTimeStr = endTimeComboBox.getValue();

                if (startTimeStr == null || endTimeStr == null) {
                    showAlert("Error de validación", "Debe seleccionar horas de inicio y fin", Alert.AlertType.WARNING);
                    return false;
                }

                LocalTime startTime = LocalTime.parse(startTimeStr);
                LocalTime endTime = LocalTime.parse(endTimeStr);

                if (endTime.isBefore(startTime) || endTime.equals(startTime)) {
                    showAlert("Error de validación",
                            "La hora de fin debe ser posterior a la hora de inicio",
                            Alert.AlertType.WARNING);
                    endTimeComboBox.requestFocus();
                    return false;
                }
            } catch (Exception e) {
                showAlert("Error de validación",
                        "Error en la selección de horarios: " + e.getMessage(),
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
            if (authService.getCurrentUser() == null) {
                return true; // Si no hay usuario, no verificar
            }

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
            return true; // En caso de error, permitir la creación
        }
    }

    // Métodos de utilidad
    private String generateEventId() {
        return "E" + TimeService.getInstance().now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"));
    }

    private LocalTime parseTime(String timeStr) {
        try {
            return LocalTime.parse(timeStr, DateTimeFormatter.ofPattern("HH:mm"));
        } catch (Exception e) {
            System.err.println("Error parsing time: " + timeStr);
            return LocalTime.of(9, 0); // Default fallback
        }
    }

    private String formatTime(LocalDateTime dateTime) {
        if (dateTime == null) return "00:00";
        return dateTime.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"));
    }

    private String formatEventTime(Event event) {
        if (event == null) return "Hora no disponible";

        if (event.isAllDay()) {
            return "Todo el día";
        } else {
            String startTime = formatTime(event.getStartDate());
            String endTime = formatTime(event.getEndDate());
            return startTime + " - " + endTime;
        }
    }

    private void showAlert(String title, String message, Alert.AlertType type) {
        Platform.runLater(() -> {
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
        });
    }

    private void closeDialog() {
        if (closeButton != null && closeButton.getScene() != null && closeButton.getScene().getWindow() != null) {
            Stage stage = (Stage) closeButton.getScene().getWindow();
            stage.close();
        }
    }
}