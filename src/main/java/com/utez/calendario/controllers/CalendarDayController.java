package com.utez.calendario.controllers;

import com.utez.calendario.models.Calendar;
import com.utez.calendario.models.Event;
import com.utez.calendario.models.User;
import com.utez.calendario.services.AuthService;
import com.utez.calendario.services.EventService;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.animation.Timeline;
import javafx.geometry.Rectangle2D;

import java.io.IOException;
import java.net.URL;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class CalendarDayController implements Initializable {

    // Colores para calendarios predeterminados
    private static final String COLOR_CLASSES = "#1E76E8";  // Color para Mis Clases
    private static final String COLOR_TASKS = "#2c2c2c";    // Color para Tareas y Proyectos
    private static final String COLOR_PERSONAL = "#53C925"; // Color para Personal
    private static final String COLOR_EXAMS = "#f2c51f";    // Color para Exámenes
    private static final String COLOR_HOLIDAYS = "#FF6B35"; // Color para Días Festivos
    private static final String COLOR_UTEZ = "#8B5CF6";     // Color para UTEZ

    // Campos FXML existentes
    @FXML private ScrollPane dayScrollPane;
    @FXML private GridPane calendarGrid;
    @FXML private Label monthYearLabel;
    @FXML private Button createButton;
    @FXML private CheckBox userCalendarCheck;
    @FXML private CheckBox tasksCalendarCheck;
    @FXML private CheckBox personalCalendarCheck;
    @FXML private CheckBox examsCalendarCheck;
    @FXML private CheckBox holidaysCalendarCheck;
    @FXML private CheckBox utezCalendarCheck;
    @FXML private HBox allDayEventsPane;
    @FXML private Label statusLabel;
    @FXML private StackPane contentArea;
    @FXML private ScrollPane customCalendarsScroll;
    @FXML private VBox customCalendarsContainer;
    @FXML private Button addCalendarButton;

    // Variables de clase existentes
    private Timeline clockTimeline;
    private LocalDate currentDate;
    private List<Event> events;
    private EventService eventService;
    private AuthService authService;
    private final int START_HOUR = 0;
    private final int TOTAL_HOURS = 24;

    // Variables para manejar calendarios personalizados (igual que en semana)
    private Map<String, CheckBox> customCalendarCheckboxes = new HashMap<>();
    private Map<String, Button> customCalendarDeleteButtons = new HashMap<>();
    private List<Calendar> customCalendarsCache = new ArrayList<>();
    private List<Calendar> allCalendarsCache = new ArrayList<>();
    private volatile boolean isLoadingEvents = false;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        System.out.println("\n=== INICIANDO VISTA DIARIA ===");
        System.out.println("Fecha/Hora: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));

        eventService = EventService.getInstance();
        authService = AuthService.getInstance();

        if (authService.getCurrentUser() != null) {
            User currentUser = authService.getCurrentUser();
            System.out.println("Usuario logueado: " + currentUser.getDisplayInfo());
        }

        initializeCalendar();
        setupCalendarCheckboxes();

        Platform.runLater(() -> {
            setupCalendarGrid();
            loadCustomCalendarsAsync(); // Cargar calendarios personalizados de forma asíncrona
            setupScrollPane();
        });
    }

    private void initializeCalendar() {
        currentDate = LocalDate.now();
        events = new ArrayList<>();
        updateCalendarView();
    }

    private void setupCalendarCheckboxes() {
        // Configurar checkboxes por defecto como seleccionados
        if (userCalendarCheck != null) {
            userCalendarCheck.setSelected(true);
            userCalendarCheck.setOnAction(e -> refreshCalendarDisplay());
        }
        if (tasksCalendarCheck != null) {
            tasksCalendarCheck.setSelected(true);
            tasksCalendarCheck.setOnAction(e -> refreshCalendarDisplay());
        }
        if (personalCalendarCheck != null) {
            personalCalendarCheck.setSelected(true);
            personalCalendarCheck.setOnAction(e -> refreshCalendarDisplay());
        }
        if (examsCalendarCheck != null) {
            examsCalendarCheck.setSelected(true);
            examsCalendarCheck.setOnAction(e -> refreshCalendarDisplay());
        }
        if (holidaysCalendarCheck != null) {
            holidaysCalendarCheck.setSelected(true);
            holidaysCalendarCheck.setOnAction(e -> refreshCalendarDisplay());
        }
        if (utezCalendarCheck != null) {
            utezCalendarCheck.setSelected(true);
            utezCalendarCheck.setOnAction(e -> refreshCalendarDisplay());
        }
    }

    private void refreshCalendarDisplay() {
        if (isLoadingEvents) {
            return; // Evitar múltiples cargas simultáneas
        }

        CompletableFuture.runAsync(() -> {
            Platform.runLater(() -> {
                loadEventsFromDatabaseAsync();
            });
        });
    }

    // Método actualizado para determinar si mostrar un evento (igual que en semana)
    private boolean shouldShowEvent(Event event) {
        String calendarId = event.getCalendarId();

        // Buscar el calendario en cache para obtener su nombre
        String calendarName = "";
        for (Calendar cal : allCalendarsCache) {
            if (cal.getCalendarId().equals(calendarId)) {
                calendarName = cal.getName().toLowerCase();
                break;
            }
        }

        // Si no se encuentra en cache, intentar obtenerlo de la BD
        if (calendarName.isEmpty()) {
            try {
                Calendar cal = Calendar.getCalendarById(calendarId);
                if (cal != null) {
                    calendarName = cal.getName().toLowerCase();
                }
            } catch (Exception e) {
                System.err.println("Error obteniendo calendario: " + e.getMessage());
            }
        }

        // Mapear por nombre del calendario a checkbox
        if (calendarName.contains("clase") || calendarName.contains("class")) {
            return userCalendarCheck != null && userCalendarCheck.isSelected();
        } else if (calendarName.contains("tarea") || calendarName.contains("proyecto") || calendarName.contains("task")) {
            return tasksCalendarCheck != null && tasksCalendarCheck.isSelected();
        } else if (calendarName.contains("personal")) {
            return personalCalendarCheck != null && personalCalendarCheck.isSelected();
        } else if (calendarName.contains("examen") || calendarName.contains("exam")) {
            return examsCalendarCheck != null && examsCalendarCheck.isSelected();
        } else if (calendarName.contains("festivo") || calendarName.contains("holiday")) {
            return holidaysCalendarCheck != null && holidaysCalendarCheck.isSelected();
        } else if (calendarName.contains("utez")) {
            return utezCalendarCheck != null && utezCalendarCheck.isSelected();
        }

        // Fallback: verificar IDs fijos (por compatibilidad)
        switch (calendarId) {
            case "CAL0000001": // Mis Clases
                return userCalendarCheck != null && userCalendarCheck.isSelected();
            case "CAL0000002": // Tareas y Proyectos
                return tasksCalendarCheck != null && tasksCalendarCheck.isSelected();
            case "CAL0000003": // Personal
                return personalCalendarCheck != null && personalCalendarCheck.isSelected();
            case "CAL0000004": // Exámenes
                return examsCalendarCheck != null && examsCalendarCheck.isSelected();
            case "CAL0000005": // Días Festivos
                return holidaysCalendarCheck != null && holidaysCalendarCheck.isSelected();
            case "CAL0000006": // UTEZ
                return utezCalendarCheck != null && utezCalendarCheck.isSelected();
            default:
                // Verificar si es un calendario personalizado
                if (customCalendarCheckboxes != null && customCalendarCheckboxes.containsKey(calendarId)) {
                    CheckBox checkBox = customCalendarCheckboxes.get(calendarId);
                    return checkBox != null && checkBox.isSelected();
                }

                // Mostrar por defecto si no se puede determinar
                System.out.println("No se pudo determinar visibilidad para calendario: " + calendarName + " (ID: " + calendarId + ")");
                return true;
        }
    }

    // Método asíncrono para cargar calendarios personalizados (igual que en semana)
    private void loadCustomCalendarsAsync() {
        CompletableFuture.supplyAsync(() -> {
            if (authService.getCurrentUser() != null) {
                String userId = authService.getCurrentUser().getUserId();
                // Cargar TODOS los calendarios del usuario (predeterminados + personalizados)
                List<Calendar> allCalendars = Calendar.getAllUserCalendars(userId); // Necesitas este método en tu modelo
                List<Calendar> customCalendars = Calendar.getUserCustomCalendars(userId);

                Map<String, Object> result = new HashMap<>();
                result.put("all", allCalendars);
                result.put("custom", customCalendars);
                return result;
            }
            return new HashMap<String, Object>();
        }).thenAccept(result -> {
            Platform.runLater(() -> {
                @SuppressWarnings("unchecked")
                List<Calendar> allCalendars = (List<Calendar>) result.get("all");
                @SuppressWarnings("unchecked")
                List<Calendar> customCalendars = (List<Calendar>) result.get("custom");

                if (allCalendars != null) {
                    allCalendarsCache = allCalendars;
                    System.out.println("Calendarios cargados:");
                    for (Calendar cal : allCalendars) {
                        System.out.println("  - " + cal.getName() + " (ID: " + cal.getCalendarId() + ", Color: " + cal.getColor() + ")");
                    }
                }

                if (customCalendars != null) {
                    customCalendarsCache = customCalendars;
                }

                loadCustomCalendarsUI();
                loadEventsFromDatabaseAsync();
            });
        }).exceptionally(throwable -> {
            System.err.println("Error cargando calendarios: " + throwable.getMessage());
            Platform.runLater(() -> {
                // Fallback: crear mapeo manual si no existe el método getAllUserCalendars
                createManualCalendarMapping();
                loadEventsFromDatabaseAsync();
            });
            return null;
        });
    }

    /**
     * Método fallback para crear mapeo manual de calendarios
     */
    private void createManualCalendarMapping() {
        System.out.println("Creando mapeo manual de calendarios...");

        // Obtener los calendarios personalizados que sí funcionan
        if (authService.getCurrentUser() != null) {
            String userId = authService.getCurrentUser().getUserId();
            customCalendarsCache = Calendar.getUserCustomCalendars(userId);

            // Crear calendarios predeterminados ficticios para el mapeo
            allCalendarsCache = new ArrayList<>(customCalendarsCache);

            // Agregar calendarios predeterminados al cache
            allCalendarsCache.add(new Calendar("CAL0000001", "Mis Clases", COLOR_CLASSES, userId));
            allCalendarsCache.add(new Calendar("CAL0000002", "Tareas y Proyectos", COLOR_TASKS, userId));
            allCalendarsCache.add(new Calendar("CAL0000003", "Personal", COLOR_PERSONAL, userId));
            allCalendarsCache.add(new Calendar("CAL0000004", "Exámenes", COLOR_EXAMS, userId));
            allCalendarsCache.add(new Calendar("CAL0000005", "Días Festivos", COLOR_HOLIDAYS, userId));
            allCalendarsCache.add(new Calendar("CAL0000006", "UTEZ", COLOR_UTEZ, userId));

            System.out.println("Mapeo manual creado con " + allCalendarsCache.size() + " calendarios");
        }

        loadCustomCalendarsUI();
    }

    // Método para actualizar la UI con los calendarios personalizados (igual que en semana)
    private void loadCustomCalendarsUI() {
        // Limpiar los checkboxes existentes
        if (customCalendarCheckboxes != null) {
            customCalendarCheckboxes.clear();
        } else {
            customCalendarCheckboxes = new HashMap<>();
        }

        if (customCalendarDeleteButtons != null) {
            customCalendarDeleteButtons.clear();
        } else {
            customCalendarDeleteButtons = new HashMap<>();
        }

        // Obtener el contenedor donde se agregarán los checkboxes
        if (customCalendarsContainer != null) {
            // Limpiar el contenedor
            customCalendarsContainer.getChildren().clear();

            System.out.println("Cargando " + customCalendarsCache.size() + " calendarios personalizados");

            for (Calendar cal : customCalendarsCache) {
                // Crear contenedor horizontal para checkbox y botón eliminar
                HBox calendarRow = new HBox();
                calendarRow.setSpacing(5);
                calendarRow.setAlignment(Pos.CENTER_LEFT);

                // Crear checkbox para el calendario
                CheckBox checkBox = new CheckBox(cal.getName());
                checkBox.setSelected(true); // Por defecto activado

                // Aplicar estilo de color al checkbox
                String colorHex = cal.getColor();
                String colorStyle = String.format("-fx-text-fill: %s;", colorHex);
                checkBox.setStyle(colorStyle);

                // Guardar referencia al checkbox con su ID de calendario
                customCalendarCheckboxes.put(cal.getCalendarId(), checkBox);

                // Añadir listener para actualizar la vista al cambiar la selección
                checkBox.setOnAction(e -> refreshCalendarDisplay());

                // Crear botón para eliminar calendario
                Button deleteButton = new Button("🗑");
                deleteButton.getStyleClass().add("delete-calendar-button");
                deleteButton.setStyle("-fx-background-color: #ff4444; -fx-text-fill: white; -fx-font-size: 10px; -fx-padding: 2 6 2 6;");
                deleteButton.setTooltip(new Tooltip("Eliminar calendario"));

                // Guardar referencia al botón
                customCalendarDeleteButtons.put(cal.getCalendarId(), deleteButton);

                // Añadir acción para eliminar calendario
                deleteButton.setOnAction(e -> {
                    Alert confirmAlert = new Alert(Alert.AlertType.CONFIRMATION);
                    confirmAlert.setTitle("Eliminar Calendario");
                    confirmAlert.setHeaderText("¿Estás seguro?");
                    confirmAlert.setContentText("¿Deseas eliminar el calendario '" + cal.getName() + "'?\nEsta acción no se puede deshacer.");

                    Optional<ButtonType> result = confirmAlert.showAndWait();
                    if (result.isPresent() && result.get() == ButtonType.OK) {
                        deleteCustomCalendarAsync(cal.getCalendarId());
                    }
                });

                // Región que crece para empujar el botón hacia la derecha
                Region spacer = new Region();
                HBox.setHgrow(spacer, Priority.ALWAYS);

                deleteButton.setMinWidth(32);
                deleteButton.setMaxWidth(32);

                calendarRow.getChildren().addAll(checkBox, spacer, deleteButton);

                // Agregar al contenedor principal
                customCalendarsContainer.getChildren().add(calendarRow);

                System.out.println("Añadido calendario: " + cal.getName() + " con color: " + colorHex);
            }
        } else {
            System.err.println("Error: customCalendarsContainer es null");
        }
    }

    /**
     * Elimina un calendario personalizado de forma asíncrona
     */
    private void deleteCustomCalendarAsync(String calendarId) {
        CompletableFuture.supplyAsync(() -> {
            return Calendar.deleteCalendar(calendarId);
        }).thenAccept(deleted -> {
            Platform.runLater(() -> {
                if (deleted) {
                    System.out.println("Calendario eliminado correctamente: " + calendarId);
                    loadCustomCalendarsAsync(); // Recargar calendarios
                    showAlert("Éxito", "Calendario eliminado correctamente", Alert.AlertType.INFORMATION);
                } else {
                    showAlert("Error", "No se pudo eliminar el calendario", Alert.AlertType.ERROR);
                }
            });
        }).exceptionally(throwable -> {
            Platform.runLater(() -> {
                System.err.println("Error al eliminar calendario: " + throwable.getMessage());
                showAlert("Error", "Error al eliminar el calendario: " + throwable.getMessage(), Alert.AlertType.ERROR);
            });
            return null;
        });
    }

    /**
     * Carga eventos desde la base de datos de forma asíncrona (igual que en semana)
     */
    private void loadEventsFromDatabaseAsync() {
        if (isLoadingEvents) {
            return; // Evitar cargas simultáneas
        }

        if (authService.getCurrentUser() == null) {
            System.out.println("No hay usuario logueado");
            events.clear();
            Platform.runLater(this::createDayView);
            return;
        }

        isLoadingEvents = true;
        String userId = authService.getCurrentUser().getUserId();

        System.out.println("\n[" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "] " +
                "Cargando eventos desde BD de forma asíncrona...");
        System.out.println("Usuario ID: " + userId);
        System.out.println("Día actual: " + currentDate);

        // Usar Task para operación en background
        Task<List<Event>> loadEventsTask = new Task<List<Event>>() {
            @Override
            protected List<Event> call() throws Exception {
                return eventService.getEventsForDay(userId, currentDate);
            }
        };

        loadEventsTask.setOnSucceeded(e -> {
            List<Event> dayEvents = loadEventsTask.getValue();

            Platform.runLater(() -> {
                try {
                    events.clear();

                    // Filtrar eventos según la configuración de visibilidad de calendarios
                    if (dayEvents != null) {
                        for (Event event : dayEvents) {
                            if (shouldShowEvent(event)) {
                                events.add(event);
                            }
                        }
                    }

                    createDayView();
                    System.out.println("[" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "] " +
                            "Eventos cargados correctamente desde BD (Asíncrono)");

                } finally {
                    isLoadingEvents = false;
                }
            });
        });

        loadEventsTask.setOnFailed(e -> {
            Platform.runLater(() -> {
                try {
                    Throwable exception = loadEventsTask.getException();
                    System.err.println("[" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "] " +
                            "Error cargando eventos (Asíncrono): " + exception.getMessage());
                    exception.printStackTrace();
                    showAlert("Error de Conexión",
                            "No se pueden cargar los eventos desde la base de datos.\nVerifica tu conexión y configuración.",
                            Alert.AlertType.WARNING);
                } finally {
                    isLoadingEvents = false;
                }
            });
        });

        // Ejecutar la tarea en un hilo separado
        Thread backgroundThread = new Thread(loadEventsTask);
        backgroundThread.setDaemon(true);
        backgroundThread.start();
    }

    private void setupScrollPane() {
        if (dayScrollPane != null) {
            dayScrollPane.setFitToWidth(true);
            dayScrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
            dayScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER); // No scroll horizontal en vista diaria

            // Scroll a las 8 AM al inicio
            Platform.runLater(() -> {
                double scrollPosition = 8.0 / TOTAL_HOURS;
                dayScrollPane.setVvalue(scrollPosition);
            });
        }
    }

    private void setupCalendarGrid() {
        if (calendarGrid == null) return;

        calendarGrid.getChildren().clear();
        calendarGrid.getColumnConstraints().clear();
        calendarGrid.getRowConstraints().clear();

        // ELIMINAR TODOS LOS GAPS
        calendarGrid.setHgap(0);
        calendarGrid.setVgap(0);
        calendarGrid.setPadding(new Insets(0));

        // Columna para etiquetas de hora (sin gaps)
        ColumnConstraints hourColumn = new ColumnConstraints();
        hourColumn.setMinWidth(80);
        hourColumn.setPrefWidth(80);
        hourColumn.setMaxWidth(80);
        hourColumn.setFillWidth(false);
        calendarGrid.getColumnConstraints().add(hourColumn);

        // Una sola columna para el día (sin gaps)
        ColumnConstraints dayColumn = new ColumnConstraints();
        dayColumn.setMinWidth(400);
        dayColumn.setHgrow(Priority.ALWAYS);
        dayColumn.setFillWidth(true);
        calendarGrid.getColumnConstraints().add(dayColumn);

        // Fila para encabezado (sin gaps)
        RowConstraints headerRow = new RowConstraints();
        headerRow.setMinHeight(60);
        headerRow.setPrefHeight(60);
        headerRow.setVgrow(Priority.NEVER);
        headerRow.setFillHeight(false);
        calendarGrid.getRowConstraints().add(headerRow);

        // Filas para cada hora (sin gaps)
        for (int i = 0; i < TOTAL_HOURS; i++) {
            RowConstraints hourRow = new RowConstraints();
            hourRow.setMinHeight(60);
            hourRow.setPrefHeight(60);
            hourRow.setVgrow(Priority.NEVER);
            hourRow.setFillHeight(false);
            calendarGrid.getRowConstraints().add(hourRow);
        }

        createDayView();
    }

    private void createDayView() {
        // Limpiar contenido existente pero mantener constraints
        calendarGrid.getChildren().clear();

        // Celda vacía para esquina superior izquierda
        Label cornerLabel = new Label("");
        cornerLabel.getStyleClass().add("corner-cell");
        GridPane.setMargin(cornerLabel, new javafx.geometry.Insets(0));
        calendarGrid.add(cornerLabel, 0, 0);

        // Encabezado del día
        VBox dayHeader = createDayHeader();
        GridPane.setMargin(dayHeader, new javafx.geometry.Insets(0));
        calendarGrid.add(dayHeader, 1, 0);

        // Etiquetas de horas y celdas
        for (int hour = 0; hour < TOTAL_HOURS; hour++) {
            int actualHour = START_HOUR + hour;

            Label hourLabel = createHourLabel(actualHour);
            GridPane.setMargin(hourLabel, new javafx.geometry.Insets(0));
            calendarGrid.add(hourLabel, 0, hour + 1);

            VBox hourCell = createHourCell(actualHour);
            GridPane.setMargin(hourCell, new javafx.geometry.Insets(0));
            calendarGrid.add(hourCell, 1, hour + 1);
        }

        // Mostrar eventos (incluyendo todo el día)
        for (Event event : events) {
            if (!shouldShowEvent(event)) continue;
            if (!event.getStartDate().toLocalDate().equals(currentDate)) continue;

            if (isAllDay(event)) {
                // === EVENTO DE TODO EL DÍA ===
                int rowIndex = 1;  // debajo del encabezado
                int rowSpan = TOTAL_HOURS;

                Label eventLabel = createEventLabel(event);
                eventLabel.getStyleClass().add("all-day-event");
                eventLabel.setMinHeight((60 * TOTAL_HOURS)); // altura completa

                GridPane.setRowIndex(eventLabel, rowIndex);
                GridPane.setColumnIndex(eventLabel, 1);
                GridPane.setRowSpan(eventLabel, rowSpan);

                calendarGrid.getChildren().add(eventLabel);
            }
            else {
                // === EVENTO NORMAL ===
                int startHour = event.getStartDate().getHour();
                int endHour = event.getEndDate().getHour();
                int rowIndex = startHour + 1;
                int rowSpan = Math.max(1, endHour - startHour);

                Label eventLabel = createEventLabel(event);
                eventLabel.setMinHeight(60 * rowSpan);

                GridPane.setRowIndex(eventLabel, rowIndex);
                GridPane.setColumnIndex(eventLabel, 1);
                GridPane.setRowSpan(eventLabel, rowSpan);

                calendarGrid.getChildren().add(eventLabel);
            }
        }

        // Agregar línea de hora actual si es hoy
        if (currentDate.equals(LocalDate.now())) {
            addCurrentTimeLine();
        }

        System.out.println("✓ Vista diaria creada para: " + currentDate + " con " + TOTAL_HOURS + " horas");
    }

    /**
     * Obtiene el color de un calendario según su ID (igual que en semana)
     */
    private String getCalendarColor(String calendarId) {
        // Primero buscar en el cache de todos los calendarios
        for (Calendar cal : allCalendarsCache) {
            if (cal.getCalendarId().equals(calendarId)) {
                System.out.println("Color encontrado para " + cal.getName() + " (ID: " + calendarId + "): " + cal.getColor());
                return cal.getColor();
            }
        }

        // Fallback: buscar por nombre conocido en eventos de la BD
        String colorByName = getColorByCalendarName(calendarId);
        if (colorByName != null) {
            System.out.println("Color encontrado por nombre para ID " + calendarId + ": " + colorByName);
            return colorByName;
        }

        // Fallback final: verificar IDs fijos (por si acaso)
        switch (calendarId) {
            case "CAL0000001": return COLOR_CLASSES;
            case "CAL0000002": return COLOR_TASKS;
            case "CAL0000003": return COLOR_PERSONAL;
            case "CAL0000004": return COLOR_EXAMS;
            case "CAL0000005": return COLOR_HOLIDAYS;
            case "CAL0000006": return COLOR_UTEZ;
        }

        System.out.println("No se encontró color para calendario ID: " + calendarId);
        return "#808080"; // Color gris por defecto
    }

    /**
     * Método para obtener color basado en el nombre del calendario
     */
    private String getColorByCalendarName(String calendarId) {
        try {
            if (authService.getCurrentUser() != null) {
                // Buscar el calendario en la BD por ID
                Calendar calendar = Calendar.getCalendarById(calendarId);

                if (calendar != null) {
                    String name = calendar.getName().toLowerCase();

                    // Mapear por nombre
                    if (name.contains("clase") || name.contains("class")) {
                        return COLOR_CLASSES;
                    } else if (name.contains("tarea") || name.contains("proyecto") || name.contains("task")) {
                        return COLOR_TASKS;
                    } else if (name.contains("personal")) {
                        return COLOR_PERSONAL;
                    } else if (name.contains("examen") || name.contains("exam")) {
                        return COLOR_EXAMS;
                    } else if (name.contains("festivo") || name.contains("holiday")) {
                        return COLOR_HOLIDAYS;
                    } else if (name.contains("utez")) {
                        return COLOR_UTEZ;
                    }

                    // Si es un calendario personalizado, usar su color
                    return calendar.getColor();
                }
            }
        } catch (Exception e) {
            System.err.println("Error obteniendo calendario por ID " + calendarId + ": " + e.getMessage());
        }

        return null;
    }

    /**
     * Determina si un color es claro (necesita texto negro)
     */
    private boolean isLightColor(String hexColor) {
        if (hexColor == null || !hexColor.startsWith("#")) {
            return true;
        }

        try {
            // Remover el # y convertir a RGB
            String hex = hexColor.substring(1);
            int r = Integer.parseInt(hex.substring(0, 2), 16);
            int g = Integer.parseInt(hex.substring(2, 4), 16);
            int b = Integer.parseInt(hex.substring(4, 6), 16);

            // Calcular luminancia relativa
            double luminance = (0.299 * r + 0.587 * g + 0.114 * b) / 255;

            return luminance > 0.5;
        } catch (Exception e) {
            return true; // En caso de error, asumir que es claro
        }
    }

    private boolean isAllDay(Event event) {
        LocalTime start = event.getStartDate().toLocalTime();
        LocalTime end = event.getEndDate().toLocalTime();
        return start.equals(LocalTime.MIDNIGHT) && end.equals(LocalTime.MIDNIGHT);
    }

    private VBox createDayHeader() {
        VBox header = new VBox();
        header.getStyleClass().add("day-header");
        header.setAlignment(Pos.CENTER);
        header.setSpacing(4);
        header.setPadding(new javafx.geometry.Insets(8, 15, 8, 15));

        // Nombre del día
        String dayName = currentDate.getDayOfWeek().getDisplayName(
                java.time.format.TextStyle.FULL, Locale.of("es", "ES")).toUpperCase();
        Label dayLabel = new Label(dayName);
        dayLabel.getStyleClass().add("day-header-name");

        // Número del día
        Label dateLabel = new Label(String.valueOf(currentDate.getDayOfMonth()));
        dateLabel.getStyleClass().add("day-header-number");

        // Marcar día actual
        if (currentDate.equals(LocalDate.now())) {
            dateLabel.getStyleClass().add("day-header-today");
        }

        header.getChildren().addAll(dayLabel, dateLabel);
        return header;
    }

    private Label createHourLabel(int hour) {
        String timeText = formatHour(hour);
        Label hourLabel = new Label(timeText);
        hourLabel.getStyleClass().add("hour-label");
        // ASEGURAR ALINEACIÓN Y SIN PADDING EXTRA
        hourLabel.setAlignment(Pos.CENTER_RIGHT);
        hourLabel.setPadding(new javafx.geometry.Insets(0, 15, 0, 0));
        return hourLabel;
    }

    private String formatHour(int hour) {
        if (hour == 0) return "12 AM";
        if (hour < 12) return hour + " AM";
        if (hour == 12) return "12 PM";
        return (hour - 12) + " PM";
    }

    private VBox createHourCell(int hour) {
        VBox cell = new VBox();
        cell.getStyleClass().add("hour-cell");
        cell.setAlignment(Pos.TOP_LEFT);
        cell.setSpacing(2);
        cell.setPadding(new javafx.geometry.Insets(4));

        // Efectos hover
        cell.setOnMouseEntered(e -> cell.getStyleClass().add("hour-cell-hover"));
        cell.setOnMouseExited(e -> cell.getStyleClass().remove("hour-cell-hover"));

        // Click para crear evento con hora específica (ahora con un solo clic)
        cell.setOnMouseClicked(e -> {
            if (e.getClickCount() == 1) {
                LocalTime startTime = LocalTime.of(hour, 0);
                LocalTime endTime = LocalTime.of(hour + 1, 0);
                openEventDialogWithTime(currentDate, startTime, endTime);
            }
        });

        return cell;
    }

    private void addCurrentTimeLine() {
        LocalTime now = LocalTime.now();
        int currentHour = now.getHour();
        int currentMinute = now.getMinute();

        // Calcular posición exacta dentro de la hora
        double hourProgress = currentMinute / 60.0;
        int rowIndex = currentHour + 1; // +1 porque la primera fila es el encabezado

        // Crear contenedor para la línea de tiempo con bolita
        HBox timeContainer = new HBox();
        timeContainer.getStyleClass().add("current-time-container");
        timeContainer.setAlignment(Pos.CENTER_LEFT);
        timeContainer.setSpacing(0);
        timeContainer.setMaxWidth(Double.MAX_VALUE);
        timeContainer.setPadding(new javafx.geometry.Insets(0));

        // Crear bolita roja
        Region timeIndicator = new Region();
        timeIndicator.getStyleClass().add("current-time-indicator");

        // Crear línea roja
        Region timeLine = new Region();
        timeLine.getStyleClass().add("current-time-line");
        HBox.setHgrow(timeLine, Priority.ALWAYS);

        // Agregar bolita y línea al contenedor
        timeContainer.getChildren().addAll(timeIndicator, timeLine);

        // Agregar el contenedor en la posición correcta SIN MARGIN
        GridPane.setMargin(timeContainer, new javafx.geometry.Insets(hourProgress * 60, 0, 0, 0));
        calendarGrid.add(timeContainer, 1, rowIndex);
    }

    // Método actualizado para aplicar colores personalizados (igual que en semana)
    private Label createEventLabel(Event event) {
        // Formatear hora de inicio y fin
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");
        String startTime = event.getStartDate().toLocalTime().format(timeFormatter);
        String endTime = event.getEndDate().toLocalTime().format(timeFormatter);

        String labelText = event.getTitle() + " (" + startTime + " - " + endTime + ")";

        Label eventLabel = new Label(labelText);
        eventLabel.getStyleClass().add("event-label");
        eventLabel.setMaxWidth(Double.MAX_VALUE);
        eventLabel.setWrapText(true);

        // Aplicar color según el calendario
        String calendarId = event.getCalendarId();
        String backgroundColor = getCalendarColor(calendarId);

        if (backgroundColor != null) {
            // Calcular si necesitamos texto blanco o negro basado en el color de fondo
            String textColor = isLightColor(backgroundColor) ? "#000000" : "#ffffff";
            eventLabel.setStyle(String.format("-fx-background-color: %s; -fx-text-fill: %s; -fx-background-radius: 3; -fx-padding: 2 4 2 4;",
                    backgroundColor, textColor));
        } else {
            // Fallback con estilos CSS por defecto
            eventLabel.getStyleClass().add("event-block");

            switch (calendarId) {
                case "CAL0000001": // Mis Clases
                    eventLabel.getStyleClass().add("event-blue");
                    break;
                case "CAL0000002": // Tareas y Proyectos
                    eventLabel.getStyleClass().add("event-red");
                    break;
                case "CAL0000003": // Personal
                    eventLabel.getStyleClass().add("event-green");
                    break;
                case "CAL0000004": // Exámenes
                    eventLabel.getStyleClass().add("event-orange");
                    break;
                default:
                    eventLabel.getStyleClass().add("event-default");
            }
        }

        // Click para abrir evento específico
        final Event currentEvent = event;
        eventLabel.setOnMouseClicked(e -> {
            if (e.getClickCount() == 1) {
                openSpecificEvent(currentEvent);
            }
        });

        return eventLabel;
    }

    @FXML
    private void handleCreateButton() {
        openEventDialogForCreate(currentDate);
    }

    @FXML
    private void handlePreviousDay() {
        currentDate = currentDate.minusDays(1);
        updateCalendarView();
        loadEventsFromDatabaseAsync();
    }

    @FXML
    private void handleNextDay() {
        currentDate = currentDate.plusDays(1);
        updateCalendarView();
        loadEventsFromDatabaseAsync();
    }

    @FXML
    private void handleTodayClick() {
        currentDate = LocalDate.now();
        updateCalendarView();
        loadEventsFromDatabaseAsync();
    }

    private void updateCalendarView() {
        // Actualizar etiqueta de mes y año
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM 'de' yyyy", new Locale("es", "ES"));
        String formattedDate = currentDate.format(formatter);
        monthYearLabel.setText(formattedDate.toUpperCase());
    }

    private void openEventDialogForCreate(LocalDate date) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/event-dialog.fxml"));
            Parent dialogRoot = loader.load();
            EventDialogController dialogController = loader.getController();

            Runnable onEventChanged = this::loadEventsFromDatabaseAsync;
            dialogController.initializeForCreate(date, onEventChanged);

            Stage dialogStage = new Stage();

            // Remover decoraciones de la ventana
            dialogStage.initStyle(StageStyle.UNDECORATED);

            dialogStage.initModality(Modality.WINDOW_MODAL);
            dialogStage.initOwner(createButton.getScene().getWindow());
            Scene dialogScene = new Scene(dialogRoot);

            try {
                dialogScene.getStylesheets().add(getClass().getResource("/css/dialog-styles.css").toExternalForm());
            } catch (Exception ignored) {}

            dialogStage.setScene(dialogScene);
            dialogStage.setResizable(false);

            // Hacer la ventana arrastrable
            makeDialogDraggable(dialogRoot, dialogStage);

            dialogStage.showAndWait();

        } catch (IOException e) {
            e.printStackTrace();
            showAlert("Error", "No se pudo abrir el diálogo de eventos: " + e.getMessage(),
                    Alert.AlertType.ERROR);
        }
    }

    private void openEventDialogWithTime(LocalDate date, LocalTime startTime, LocalTime endTime) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/event-dialog.fxml"));
            Parent dialogRoot = loader.load();
            EventDialogController dialogController = loader.getController();

            Runnable onEventChanged = this::loadEventsFromDatabaseAsync;
            dialogController.initializeForCreateWithTime(date, startTime, endTime, onEventChanged);

            Stage dialogStage = new Stage();

            // Remover decoraciones de la ventana
            dialogStage.initStyle(StageStyle.UNDECORATED);

            dialogStage.initModality(Modality.WINDOW_MODAL);
            dialogStage.initOwner(createButton.getScene().getWindow());
            Scene dialogScene = new Scene(dialogRoot);

            try {
                dialogScene.getStylesheets().add(getClass().getResource("/css/dialog-styles.css").toExternalForm());
            } catch (Exception ignored) {}

            dialogStage.setScene(dialogScene);
            dialogStage.setResizable(false);

            // Hacer la ventana arrastrable
            makeDialogDraggable(dialogRoot, dialogStage);

            dialogStage.showAndWait();

        } catch (IOException e) {
            e.printStackTrace();
            showAlert("Error", "No se pudo abrir el diálogo de eventos: " + e.getMessage(),
                    Alert.AlertType.ERROR);
        }
    }

    public void openSpecificEvent(Event event) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/event-dialog.fxml"));
            Parent dialogRoot = loader.load();
            EventDialogController dialogController = loader.getController();

            Runnable onEventChanged = this::loadEventsFromDatabaseAsync;
            dialogController.initializeForViewEvent(event, onEventChanged);

            Stage dialogStage = new Stage();

            // Remover decoraciones de la ventana
            dialogStage.initStyle(StageStyle.UNDECORATED);

            dialogStage.initModality(Modality.WINDOW_MODAL);
            dialogStage.initOwner(createButton.getScene().getWindow());
            Scene dialogScene = new Scene(dialogRoot);

            try {
                dialogScene.getStylesheets().add(getClass().getResource("/css/dialog-styles.css").toExternalForm());
            } catch (Exception ignored) {}

            dialogStage.setScene(dialogScene);
            dialogStage.setResizable(false);

            // Hacer la ventana arrastrable
            makeDialogDraggable(dialogRoot, dialogStage);

            dialogStage.showAndWait();

        } catch (IOException e) {
            e.printStackTrace();
            showAlert("Error", "No se pudo abrir el evento: " + e.getMessage(),
                    Alert.AlertType.ERROR);
        }
    }

    private void openEventDialogForRead(LocalDate date) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/event-dialog.fxml"));
            Parent dialogRoot = loader.load();
            EventDialogController dialogController = loader.getController();

            Runnable onEventChanged = this::loadEventsFromDatabaseAsync;
            dialogController.initializeForRead(date, onEventChanged);

            Stage dialogStage = new Stage();

            // Remover decoraciones de la ventana
            dialogStage.initStyle(StageStyle.UNDECORATED);

            dialogStage.initModality(Modality.WINDOW_MODAL);
            dialogStage.initOwner(createButton.getScene().getWindow());
            Scene dialogScene = new Scene(dialogRoot);

            try {
                dialogScene.getStylesheets().add(getClass().getResource("/css/dialog-styles.css").toExternalForm());
            } catch (Exception ignored) {}

            dialogStage.setScene(dialogScene);
            dialogStage.setResizable(false);

            // Hacer la ventana arrastrable
            makeDialogDraggable(dialogRoot, dialogStage);

            dialogStage.showAndWait();

        } catch (IOException e) {
            e.printStackTrace();
            showAlert("Error", "No se pudo abrir el diálogo de eventos: " + e.getMessage(),
                    Alert.AlertType.ERROR);
        }
    }

    // ========== NAVEGACIÓN ENTRE VISTAS ==========

    @FXML
    private void handleDayView() {
        // Ya estamos en vista diaria
    }

    @FXML
    private void handleWeekView() {
        navigateToView("/fxml/calendar-week.fxml", "/css/styles-week.css", "Vista Semana");
    }

    @FXML
    private void handleMonthView() {
        navigateToView("/fxml/calendar-month.fxml", "/css/styles-month.css", "Vista Mes");
    }

    @FXML
    private void handleYearView() {
        navigateToView("/fxml/calendar-year.fxml", "/css/styles-year.css", "Vista Año");
    }

    private void navigateToView(String fxmlPath, String cssPath, String title) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
            Parent root = loader.load();

            Stage stage = (Stage) calendarGrid.getScene().getWindow();

            // Guardar dimensiones
            double currentWidth = stage.getWidth() > 100 ? stage.getWidth() : 1200;
            double currentHeight = stage.getHeight() > 100 ? stage.getHeight() : 800;
            boolean isMaximized = stage.isMaximized();

            Scene scene = new Scene(root);
            scene.getStylesheets().add(getClass().getResource(cssPath).toExternalForm());

            stage.setScene(scene);
            stage.setTitle("UTEZ Calendar - " + title);

            // Restaurar dimensiones
            if (isMaximized) {
                stage.setMaximized(true);
            } else {
                stage.setWidth(currentWidth);
                stage.setHeight(currentHeight);
                Platform.runLater(stage::centerOnScreen);
            }

        } catch (IOException e) {
            e.printStackTrace();
            showAlert("Error", "No se pudo cargar la vista: " + e.getMessage(), Alert.AlertType.ERROR);
        }
    }

    @FXML
    private void handleCloseButton() {
        if (authService.getCurrentUser() != null) {
            authService.logout();
        }
        Platform.exit();
    }

    // ========== UTILIDADES ==========
    private void showAlert(String title, String message, Alert.AlertType type) {
        Platform.runLater(() -> {
            Alert alert = new Alert(type);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    // Método para agregar calendarios personalizados (igual que en semana)
    @FXML
    private void handleAddCalendar() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/add-calendar-dialog.fxml"));
            Parent dialogRoot = loader.load();
            AddCalendarDialogController controller = loader.getController();

            Stage dialogStage = new Stage();
            dialogStage.initStyle(StageStyle.UNDECORATED);
            dialogStage.initModality(Modality.WINDOW_MODAL);
            dialogStage.initOwner(calendarGrid.getScene().getWindow());

            controller.setDialogStage(dialogStage);

            Scene dialogScene = new Scene(dialogRoot);

            try {
                dialogScene.getStylesheets().add(getClass().getResource("/css/dialog-styles.css").toExternalForm());
            } catch (Exception ignored) {
                System.out.println("No se pudo cargar el CSS para el diálogo");
            }

            dialogStage.setScene(dialogScene);
            dialogStage.setResizable(false);

            // Hacer la ventana arrastrable
            makeDialogDraggable(dialogRoot, dialogStage);

            dialogStage.showAndWait();

            if (controller.isCalendarCreated()) {
                // Recargar los calendarios personalizados
                loadCustomCalendarsAsync();
            }

        } catch (IOException e) {
            System.err.println("Error abriendo diálogo: " + e.getMessage());
            e.printStackTrace();
            showAlert("Error", "No se pudo abrir el diálogo para crear calendario", Alert.AlertType.ERROR);
        }
    }

    // Método para hacer arrastrable un diálogo
    private void makeDialogDraggable(Parent root, Stage stage) {
        final double[] xOffset = {0};
        final double[] yOffset = {0};

        root.setOnMousePressed(event -> {
            xOffset[0] = event.getSceneX();
            yOffset[0] = event.getSceneY();
        });

        root.setOnMouseDragged(event -> {
            stage.setX(event.getScreenX() - xOffset[0]);
            stage.setY(event.getScreenY() - yOffset[0]);
        });
    }

    private void setStatus(String message) {
        if (statusLabel != null) {
            Platform.runLater(() -> statusLabel.setText(message));
        }
    }

    private void returnToLogin() {
        try {
            System.out.println("Regresando al login...");

            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/login.fxml"));
            Parent loginRoot = loader.load();

            Stage stage = (Stage) calendarGrid.getScene().getWindow();

            Rectangle2D screenBounds = Screen.getPrimary().getVisualBounds();
            double width = Math.min(1100, screenBounds.getWidth() * 0.95);
            double height = Math.min(700, screenBounds.getHeight() * 0.95);

            Scene loginScene = new Scene(loginRoot, width, height);

            // CSS EXACTO DEL login
            loginScene.getStylesheets().add(getClass().getResource("/css/login.css").toExternalForm());

            stage.setTitle("Ithera");
            stage.setScene(loginScene);
            stage.setMinWidth(800);
            stage.setMinHeight(600);
            stage.show();
            stage.centerOnScreen();

            System.out.println("Login cargado exitosamente");

        } catch (Exception e) {
            System.err.println("No se pudo volver al login: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @FXML
    private void handleLogout() {
        try {
            setStatus("Cerrando sesión...");

            // Detener el reloj antes de cerrar sesión
            if (clockTimeline != null) {
                clockTimeline.stop();
            }

            AuthService.getInstance().logout();
            Platform.runLater(this::returnToLogin);
        } catch (Exception e) {
            System.err.println("Error al cerrar sesión: " + e.getMessage());
            setStatus("Error al cerrar sesión");
        }
    }
}