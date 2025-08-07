package com.utez.calendario.controllers;

import com.utez.calendario.models.Calendar;
import com.utez.calendario.models.Event;
import com.utez.calendario.models.User;
import com.utez.calendario.services.AuthService;
import com.utez.calendario.services.EventService;
import javafx.animation.FadeTransition;
import javafx.animation.ScaleTransition;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.HPos;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.geometry.VPos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

import java.io.IOException;
import java.net.URL;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class CalendarMonthController implements Initializable {

    // Colores para calendarios predeterminados
    private static final String COLOR_CLASSES = "#1E76E8";  // Color para Mis Clases
    private static final String COLOR_TASKS = "#2c2c2c";    // Color para Tareas y Proyectos
    private static final String COLOR_PERSONAL = "#53C925"; // Color para Personal
    private static final String COLOR_EXAMS = "#f2c51f";    // Color para Exámenes
    private static final String COLOR_HOLIDAYS = "#FF6B35"; // Color para Días Festivos
    private static final String COLOR_UTEZ = "#8B5CF6";     // Color para UTEZ

    @FXML private Label monthYearLabel;
    @FXML private GridPane calendarGrid;
    @FXML private Label statusLabel;
    @FXML private Button createButton;
    @FXML private Label todayLabel;
    @FXML private CheckBox userCalendarCheck;
    @FXML private CheckBox tasksCalendarCheck;
    @FXML private CheckBox personalCalendarCheck;
    @FXML private CheckBox examsCalendarCheck;
    @FXML private CheckBox holidaysCalendarCheck;
    @FXML private CheckBox utezCalendarCheck;
    @FXML private ScrollPane customCalendarsScroll;
    @FXML private VBox customCalendarsContainer;
    @FXML private Button addCalendarButton;

    @FXML private StackPane contentArea;
    private javafx.animation.Timeline clockTimeline;

    private YearMonth currentYearMonth;
    private LocalDate selectedDate;
    private Map<LocalDate, List<Event>> eventCache = new HashMap<>(); // Cambiado para almacenar objetos Event
    private Map<String, CheckBox> customCalendarCheckboxes = new HashMap<>();
    private Map<String, Button> customCalendarDeleteButtons = new HashMap<>();
    private List<String> viewModes = Arrays.asList("Día", "Semana", "Mes", "Año");
    private int currentViewMode = 2; // Mes por defecto

    private EventService eventService;
    private AuthService authService;

    // Cache para calendarios personalizados
    private List<Calendar> customCalendarsCache = new ArrayList<>();
    private List<Calendar> allCalendarsCache = new ArrayList<>(); // Cache para TODOS los calendarios
    private volatile boolean isLoadingEvents = false;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        System.out.println("\n=== INICIANDO CALENDARIO UTEZ ===");
        System.out.println("Fecha/Hora UTC: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        System.out.println("Usuario Sistema: " + AuthService.getInstance().getCurrentUser().getUserId());

        eventService = EventService.getInstance();
        authService = AuthService.getInstance();

        if (authService.getCurrentUser() != null) {
            User currentUser = authService.getCurrentUser();
            System.out.println("Usuario logueado: " + currentUser.getDisplayInfo());
        } else {
            System.out.println("No hay usuario logueado");
        }

        initializeCalendar();
        setupAnimations();
        setupCalendarCheckboxes();
        updateStatusBar();

        Platform.runLater(() -> {
            setupCalendarGridConstraints();
            loadCustomCalendarsAsync(); // Cargar calendarios personalizados de forma asíncrona
        });

        System.out.println("Calendario inicializado correctamente");
        System.out.println("=================================\n");
    }

    private void initializeCalendar() {
        currentYearMonth = YearMonth.now();
        selectedDate = LocalDate.now();
        eventCache.clear();
        updateCalendarView();
    }

    private void setupCalendarGridConstraints() {
        if (calendarGrid != null) {
            VBox.setVgrow(calendarGrid, Priority.ALWAYS);
            HBox.setHgrow(calendarGrid, Priority.ALWAYS);
            calendarGrid.setMaxWidth(Double.MAX_VALUE);
            calendarGrid.setMaxHeight(Double.MAX_VALUE);
        }
    }

    private void setupCalendarCheckboxes() {
        // Configurar checkboxes por defecto como seleccionados
        if (userCalendarCheck != null) {
            userCalendarCheck.setSelected(true);
            userCalendarCheck.setOnAction(e -> refreshCalendarDisplayAsync());
        }
        if (tasksCalendarCheck != null) {
            tasksCalendarCheck.setSelected(true);
            tasksCalendarCheck.setOnAction(e -> refreshCalendarDisplayAsync());
        }
        if (personalCalendarCheck != null) {
            personalCalendarCheck.setSelected(true);
            personalCalendarCheck.setOnAction(e -> refreshCalendarDisplayAsync());
        }
        if (examsCalendarCheck != null) {
            examsCalendarCheck.setSelected(true);
            examsCalendarCheck.setOnAction(e -> refreshCalendarDisplayAsync());
        }
        if (holidaysCalendarCheck != null) {
            holidaysCalendarCheck.setSelected(true);
            holidaysCalendarCheck.setOnAction(e -> refreshCalendarDisplayAsync());
        }
        if (utezCalendarCheck != null) {
            utezCalendarCheck.setSelected(true);
            utezCalendarCheck.setOnAction(e -> refreshCalendarDisplayAsync());
        }
    }

    private void refreshCalendarDisplayAsync() {
        if (isLoadingEvents) {
            return; // Evitar múltiples cargas simultáneas
        }

        CompletableFuture.runAsync(() -> {
            Platform.runLater(() -> {
                loadEventsFromDatabaseAsync();
            });
        });
    }

    // Método actualizado para determinar si mostrar un evento
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
                System.out.println(" No se pudo determinar visibilidad para calendario: " + calendarName + " (ID: " + calendarId + ")");
                return true;
        }
    }

    // Método asíncrono para cargar calendarios personalizados
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
     * Úsalo si no tienes el método getAllUserCalendars en tu modelo Calendar
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

            System.out.println(" Mapeo manual creado con " + allCalendarsCache.size() + " calendarios");
        }

        loadCustomCalendarsUI();
    }

    // Método para actualizar la UI con los calendarios personalizados
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
                checkBox.setOnAction(e -> refreshCalendarDisplayAsync());

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
     * Carga eventos desde la base de datos de forma asíncrona
     */
    private void loadEventsFromDatabaseAsync() {
        if (isLoadingEvents) {
            return; // Evitar cargas simultáneas
        }

        if (authService.getCurrentUser() == null) {
            System.out.println("No hay usuario logueado");
            eventCache.clear();
            updateCalendarView();
            return;
        }

        isLoadingEvents = true;
        String userId = authService.getCurrentUser().getUserId();

        System.out.println("\n[" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "] " +
                "Cargando eventos desde BD de forma asíncrona...");
        System.out.println("Usuario ID: " + userId);
        System.out.println("Mes actual: " + currentYearMonth.getMonth() + " " + currentYearMonth.getYear());

        // Usar Task para operación en background
        Task<List<Event>> loadEventsTask = new Task<List<Event>>() {
            @Override
            protected List<Event> call() throws Exception {
                return eventService.getEventsForMonth(userId, currentYearMonth.atDay(1));
            }
        };

        loadEventsTask.setOnSucceeded(e -> {
            List<Event> monthEvents = loadEventsTask.getValue();

            Platform.runLater(() -> {
                try {
                    eventCache.clear();

                    // Filtrar eventos según la configuración de visibilidad de calendarios
                    for (Event event : monthEvents) {
                        if (shouldShowEvent(event)) {
                            LocalDate eventDate = event.getStartDate().toLocalDate();
                            eventCache.computeIfAbsent(eventDate, k -> new ArrayList<>()).add(event);
                        }
                    }

                    updateCalendarView();
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

    private void setupAnimations() {
        if (createButton != null) {
            createButton.setOnMouseEntered(e -> animateButtonHover(createButton, true));
            createButton.setOnMouseExited(e -> animateButtonHover(createButton, false));
        }
    }

    private void animateButtonHover(Button button, boolean hover) {
        ScaleTransition scaleTransition = new ScaleTransition(Duration.millis(200), button);
        scaleTransition.setToX(hover ? 1.05 : 1.0);
        scaleTransition.setToY(hover ? 1.05 : 1.0);
        scaleTransition.play();
    }

    private void updateCalendarView() {
        updateMonthYearLabel();
        populateCalendarGrid();
        updateStatusBar();
        if (calendarGrid != null) {
            FadeTransition fadeTransition = new FadeTransition(Duration.millis(300), calendarGrid);
            fadeTransition.setFromValue(0.8);
            fadeTransition.setToValue(1.0);
            fadeTransition.play();
        }
    }

    private void updateMonthYearLabel() {
        // Usar Locale.of() en lugar del constructor deprecado
        Locale spanishLocale = Locale.of("es", "ES");
        String monthName = currentYearMonth.getMonth().getDisplayName(TextStyle.FULL, spanishLocale);
        String year = String.valueOf(currentYearMonth.getYear());
        if (monthYearLabel != null) {
            monthYearLabel.setText(monthName.toUpperCase() + " " + year);
        }
    }

    private void populateCalendarGrid() {
        if (calendarGrid == null) return;

        calendarGrid.getChildren().clear();
        calendarGrid.getColumnConstraints().clear();
        calendarGrid.getRowConstraints().clear();

        // Configurar columnas
        for (int i = 0; i < 7; i++) {
            ColumnConstraints colConstraints = new ColumnConstraints();
            colConstraints.setPercentWidth(100.0 / 7);
            colConstraints.setHalignment(HPos.LEFT);
            colConstraints.setHgrow(Priority.ALWAYS);
            colConstraints.setFillWidth(true);
            calendarGrid.getColumnConstraints().add(colConstraints);
        }

        // Configurar filas
        for (int i = 0; i < 6; i++) {
            RowConstraints rowConstraints = new RowConstraints();
            rowConstraints.setMinHeight(80);
            rowConstraints.setVgrow(Priority.ALWAYS);
            rowConstraints.setFillHeight(true);
            rowConstraints.setValignment(VPos.TOP);
            calendarGrid.getRowConstraints().add(rowConstraints);
        }

        LocalDate firstOfMonth = currentYearMonth.atDay(1);
        int dayOfWeek = firstOfMonth.getDayOfWeek().getValue() % 7;
        LocalDate startDate = firstOfMonth.minusDays(dayOfWeek);

        String[] dayNames = {"DOM", "LUN", "MAR", "MIÉ", "JUE", "VIE", "SÁB"};

        // Poblar grid
        for (int row = 0; row < 6; row++) {
            for (int col = 0; col < 7; col++) {
                LocalDate cellDate = startDate.plusDays(row * 7 + col);
                VBox cellContainer = createCalendarCell(cellDate, row == 0 ? dayNames[col] : null);
                calendarGrid.add(cellContainer, col, row);
            }
        }
    }

    // Método actualizado createCalendarCell para manejar múltiples eventos
    private VBox createCalendarCell(LocalDate date, String dayHeader) {
        VBox cell = new VBox();
        cell.getStyleClass().add("calendar-cell");
        cell.setMaxWidth(Double.MAX_VALUE);
        cell.setMaxHeight(Double.MAX_VALUE);

        boolean isCurrentMonth = date.getMonth() == currentYearMonth.getMonth() && date.getYear() == currentYearMonth.getYear();
        boolean isToday = date.equals(LocalDate.now());
        boolean isSelected = date.equals(selectedDate);

        // Aplicar estilos CSS
        if (!isCurrentMonth) cell.getStyleClass().add("calendar-cell-other-month");
        if (isToday) cell.getStyleClass().add("calendar-cell-today");
        if (isSelected) cell.getStyleClass().add("calendar-cell-selected");

        // Header del día
        if (dayHeader != null) {
            Label headerLabel = new Label(dayHeader);
            headerLabel.getStyleClass().add("day-header-integrated");
            cell.getChildren().add(headerLabel);
        }

        // Número del día
        Label dayNumber = new Label(String.valueOf(date.getDayOfMonth()));
        dayNumber.getStyleClass().add("day-number");
        if (isToday) dayNumber.getStyleClass().add("day-number-today");
        cell.getChildren().add(dayNumber);

        // Eventos del día desde cache
        List<Event> dayEvents = eventCache.getOrDefault(date, new ArrayList<>());
        int maxEventsToShow = 3;

        // Mostrar eventos - TODOS son clickeables individualmente
        for (int i = 0; i < Math.min(dayEvents.size(), maxEventsToShow); i++) {
            Event event = dayEvents.get(i);
            Label eventLabel = new Label(event.getTitle());
            eventLabel.getStyleClass().add("event-item");

            // Aplicar color según el calendario
            String calendarId = event.getCalendarId();
            String backgroundColor = getCalendarColor(calendarId);

            String baseStyle;
            if (backgroundColor != null) {
                // Calcular si necesitamos texto blanco o negro basado en el color de fondo
                String textColor = isLightColor(backgroundColor) ? "#000000" : "#ffffff";
                baseStyle = String.format("-fx-background-color: %s; -fx-text-fill: %s; -fx-background-radius: 3; -fx-padding: 2 4 2 4; -fx-cursor: hand;",
                        backgroundColor, textColor);
            } else {
                // Color por defecto si no se encuentra
                baseStyle = "-fx-background-color: #cccccc; -fx-text-fill: #000000; -fx-background-radius: 3; -fx-padding: 2 4 2 4; -fx-cursor: hand;";
            }

            eventLabel.setStyle(baseStyle);

            // Hacer que cada evento sea clickeable individualmente
            eventLabel.setOnMouseClicked(e -> {
                e.consume(); // Evitar que se propague al evento de la celda
                openSpecificEvent(event);
            });

            // Efectos hover para mejor UX
            eventLabel.setOnMouseEntered(e -> {
                eventLabel.setStyle(baseStyle + "; -fx-opacity: 0.8;");
            });

            eventLabel.setOnMouseExited(e -> {
                eventLabel.setStyle(baseStyle);
            });

            cell.getChildren().add(eventLabel);
        }

        // Indicador de "más eventos" - AHORA CLICKEABLE
        if (dayEvents.size() > maxEventsToShow) {
            Label moreLabel = new Label("+" + (dayEvents.size() - maxEventsToShow) + " más");
            moreLabel.getStyleClass().add("more-events-label");

            // Hacer el label clickeable
            moreLabel.setStyle("-fx-cursor: hand; -fx-text-fill: #007ACC; -fx-underline: true; -fx-font-weight: bold;");

            moreLabel.setOnMouseClicked(e -> {
                e.consume(); // Evitar que se propague al evento de la celda
                openDayEventsDialog(date, dayEvents);
            });

            // Efectos hover
            moreLabel.setOnMouseEntered(e -> {
                moreLabel.setStyle(moreLabel.getStyle() + "; -fx-text-fill: #005499;");
            });

            moreLabel.setOnMouseExited(e -> {
                moreLabel.setStyle("-fx-cursor: hand; -fx-text-fill: #007ACC; -fx-underline: true; -fx-font-weight: bold;");
            });

            cell.getChildren().add(moreLabel);
        }

        // Event handlers para la celda (solo para seleccionar fecha)
        cell.setOnMouseClicked(e -> {
            if (e.getTarget() == cell || e.getTarget() == dayNumber) {
                handleDateClick(date);
            }
        });

        cell.setOnMouseEntered(e -> cell.getStyleClass().add("calendar-cell-hover"));
        cell.setOnMouseExited(e -> cell.getStyleClass().remove("calendar-cell-hover"));

        return cell;
    }

    /**
     * Abre un diálogo que muestra todos los eventos de un día específico
     */
    private void openDayEventsDialog(LocalDate date, List<Event> dayEvents) {
        // Crear un diálogo personalizado usando JavaFX nativo
        createCustomDayEventsDialog(date, dayEvents);
    }

    /**
     * Crea un diálogo personalizado para mostrar eventos del día usando JavaFX nativo
     */
    private void createCustomDayEventsDialog(LocalDate date, List<Event> dayEvents) {
        Stage dialogStage = new Stage();
        dialogStage.initStyle(StageStyle.UNDECORATED);
        dialogStage.initModality(Modality.WINDOW_MODAL);

        // Obtener la ventana padre de forma más robusta
        Stage parentStage = null;
        try {
            if (createButton != null && createButton.getScene() != null && createButton.getScene().getWindow() != null) {
                parentStage = (Stage) createButton.getScene().getWindow();
                dialogStage.initOwner(parentStage);
            }
        } catch (Exception e) {
            System.out.println("No se pudo establecer ventana padre para diálogo de día: " + e.getMessage());
        }

        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM 'de' yyyy",
                Locale.of("es", "ES"));

        // Crear el contenedor principal con estilos CSS
        VBox mainContainer = new VBox();
        mainContainer.getStyleClass().add("event-dialog");

        // Header del diálogo
        HBox headerContainer = new HBox();
        headerContainer.getStyleClass().add("dialog-header");
        headerContainer.setAlignment(Pos.CENTER_LEFT);

        VBox titleContainer = new VBox(2);
        HBox.setHgrow(titleContainer, Priority.ALWAYS);

        // Título sin "Eventos del día"
        Label dateLabel = new Label(date.format(dateFormatter));
        dateLabel.getStyleClass().add("dialog-title");

        Label countLabel = new Label(dayEvents.size() + " eventos");
        countLabel.getStyleClass().add("event-detail-label");

        titleContainer.getChildren().addAll(dateLabel, countLabel);

        // Botón cerrar estilizado
        Button closeButton = new Button("×");
        closeButton.getStyleClass().add("close-btn-modern");
        closeButton.setOnAction(e -> dialogStage.close());

        headerContainer.getChildren().addAll(titleContainer, closeButton);

        // Separador
        Region headerSeparator = new Region();
        headerSeparator.getStyleClass().add("header-separator");

        // ScrollPane para la lista de eventos
        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background-color: transparent; -fx-background: white;");
        scrollPane.setPadding(new javafx.geometry.Insets(0));

        VBox eventsContainer = new VBox();
        eventsContainer.getStyleClass().add("event-view-container");

        // Agregar cada evento con estilos CSS
        for (Event event : dayEvents) {
            VBox eventBox = createStyledEventBox(event, dialogStage);
            eventsContainer.getChildren().add(eventBox);
        }

        scrollPane.setContent(eventsContainer);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        // Botones inferiores
        HBox buttonBox = new HBox(12);
        buttonBox.getStyleClass().add("button-container-repositioned");

        Button addEventButton = new Button("Nuevo Evento");
        addEventButton.getStyleClass().add("primary-button");
        addEventButton.setOnAction(e -> {
            dialogStage.close();
            openEventDialog("CREATE", date);
        });

        Button cancelButton = new Button("Cerrar");
        cancelButton.getStyleClass().add("cancel-button");
        cancelButton.setOnAction(e -> dialogStage.close());

        buttonBox.getChildren().addAll(addEventButton, cancelButton);

        mainContainer.getChildren().addAll(headerContainer, headerSeparator, scrollPane, buttonBox);

        Scene scene = new Scene(mainContainer);

        // Aplicar los estilos CSS
        try {
            scene.getStylesheets().add(getClass().getResource("/css/dialog-styles.css").toExternalForm());
        } catch (Exception e) {
            System.out.println("No se pudo cargar el CSS para el diálogo");
        }

        dialogStage.setScene(scene);
        dialogStage.setResizable(true);
        if (parentStage != null) {
            Stage finalParentStage = parentStage;
            Platform.runLater(() -> {
                dialogStage.setX(finalParentStage.getX() + (finalParentStage.getWidth() - dialogStage.getWidth()) / 2);
                dialogStage.setY(finalParentStage.getY() + (finalParentStage.getHeight() - dialogStage.getHeight()) / 2);
            });
        }

        // Hacer la ventana arrastrable
        makeDialogDraggable(mainContainer, dialogStage);

        dialogStage.showAndWait();
    }

    /**
     * Crea una caja de evento estilizada para el diálogo - VERSIÓN CORREGIDA SIN CONFLICTOS
     */
    private VBox createStyledEventBox(Event event, Stage parentStage) {
        VBox eventContainer = new VBox(8);
        eventContainer.setStyle("-fx-padding: 16; -fx-background-color: #f8f9fa; -fx-background-radius: 8; " +
                "-fx-border-color: #e8eaed; -fx-border-radius: 8; -fx-border-width: 1;");

        // Título del evento
        Label titleLabel = new Label(event.getTitle());
        titleLabel.getStyleClass().add("event-title-display");
        titleLabel.setStyle("-fx-font-size: 16px; -fx-padding: 0 0 8 0; -fx-font-weight: bold;");

        // Información de fecha y hora
        HBox timeRow = new HBox(12);
        timeRow.getStyleClass().add("event-detail-row");
        timeRow.setAlignment(Pos.CENTER_LEFT);

        // Ícono de tiempo
        Label timeIcon = new Label("🕐");
        timeIcon.setStyle("-fx-font-size: 16px;");

        String timeText = "";
        if (event.getStartDate() != null) {
            timeText = event.getStartDate().format(DateTimeFormatter.ofPattern("HH:mm"));
            if (event.getEndDate() != null) {
                timeText += " - " + event.getEndDate().format(DateTimeFormatter.ofPattern("HH:mm"));
            }
        } else {
            timeText = "Todo el día";
        }

        Label timeLabel = new Label(timeText);
        timeLabel.getStyleClass().add("event-detail-text");

        timeRow.getChildren().addAll(timeIcon, timeLabel);

        // Descripción si existe
        VBox descriptionBox = new VBox();
        if (event.getDescription() != null && !event.getDescription().trim().isEmpty()) {
            HBox descRow = new HBox(12);
            descRow.getStyleClass().add("event-detail-row");
            descRow.setAlignment(Pos.TOP_LEFT);

            Label descIcon = new Label("📝");
            descIcon.setStyle("-fx-font-size: 16px;");

            Label descLabel = new Label(event.getDescription());
            descLabel.getStyleClass().add("event-description-text-inline");
            descLabel.setWrapText(true);
            HBox.setHgrow(descLabel, Priority.ALWAYS);

            descRow.getChildren().addAll(descIcon, descLabel);
            descriptionBox.getChildren().add(descRow);
        }

        // Información del calendario
        HBox calendarRow = new HBox(12);
        calendarRow.getStyleClass().add("event-detail-row");
        calendarRow.setAlignment(Pos.CENTER_LEFT);

        // Indicador de color del calendario
        Region colorIndicator = new Region();
        colorIndicator.setPrefWidth(16);
        colorIndicator.setPrefHeight(16);
        colorIndicator.setMaxWidth(16);
        colorIndicator.setMaxHeight(16);
        String calendarColor = getCalendarColor(event.getCalendarId());
        colorIndicator.setStyle("-fx-background-color: " + (calendarColor != null ? calendarColor : "#808080") +
                "; -fx-background-radius: 8;");

        // Nombre del calendario
        String calendarName = getCalendarNameById(event.getCalendarId());
        Label calendarLabel = new Label(calendarName);
        calendarLabel.getStyleClass().add("calendar-name-inline");

        calendarRow.getChildren().addAll(colorIndicator, calendarLabel);

        // Botón de acción - SIMPLIFICADO
        HBox buttonRow = new HBox();
        buttonRow.setAlignment(Pos.CENTER_RIGHT);
        buttonRow.setStyle("-fx-padding: 8 0 0 0;");

        Button viewButton = new Button("Ver detalles");
        viewButton.getStyleClass().add("primary-button");
        viewButton.setStyle("-fx-font-size: 12px; -fx-padding: 6 12; -fx-cursor: hand;");

        // ACCIÓN SIMPLIFICADA DEL BOTÓN - Sin conflictos
        viewButton.setOnAction(e -> {
            parentStage.close();
            openSpecificEvent(event);
        });

        buttonRow.getChildren().add(viewButton);

        // Agregar todos los elementos al contenedor
        eventContainer.getChildren().addAll(titleLabel, timeRow, descriptionBox, calendarRow, buttonRow);

        // REMOVER TODOS LOS EVENT HANDLERS DE CLICK DEL CONTENEDOR
        // Solo mantener efectos visuales suaves sin interactividad
        final String originalStyle = eventContainer.getStyle();
        final String hoverStyle = originalStyle.replace("#f8f9fa", "#f0f0f0");

        // Efectos hover MUY suaves - solo cambio de color de fondo
        eventContainer.setOnMouseEntered(e -> {
            eventContainer.setStyle(hoverStyle);
        });

        eventContainer.setOnMouseExited(e -> {
            eventContainer.setStyle(originalStyle);
        });

        return eventContainer;
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
     * Obtiene el color de un calendario según su ID
     */
    private String getCalendarColor(String calendarId) {
        // Primero buscar en el cache de todos los calendarios
        for (Calendar cal : allCalendarsCache) {
            if (cal.getCalendarId().equals(calendarId)) {
                System.out.println(" Color encontrado para " + cal.getName() + " (ID: " + calendarId + "): " + cal.getColor());
                return cal.getColor();
            }
        }

        // Fallback: buscar por nombre conocido en eventos de la BD
        String colorByName = getColorByCalendarName(calendarId);
        if (colorByName != null) {
            System.out.println(" Color encontrado por nombre para ID " + calendarId + ": " + colorByName);
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

        System.out.println(" No se encontró color para calendario ID: " + calendarId);
        return "#808080"; // Color gris por defecto
    }

    /**
     * Método para obtener color basado en el nombre del calendario
     */
    private String getColorByCalendarName(String calendarId) {
        // Si tienes acceso al objeto Event completo, podrías usar event.getCalendar().getName()
        // Por ahora, implementaremos una búsqueda en BD

        try {
            if (authService.getCurrentUser() != null) {
                String userId = authService.getCurrentUser().getUserId();

                // Buscar el calendario en la BD por ID
                Calendar calendar = Calendar.getCalendarById(calendarId); // Necesitas este método

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

    private void updateStatusBar() {
        if (statusLabel != null) {
            String userInfo = "";
            if (authService.getCurrentUser() != null) {
                User user = authService.getCurrentUser();
                userInfo = " | " + user.getDisplayId() + " (" + user.getRole().getDisplayName() + ")";
            }

            // Usar Locale.of() en lugar del constructor deprecado
            Locale spanishLocale = Locale.of("es", "ES");

            // Contar eventos totales en cache
            int totalEvents = eventCache.values().stream().mapToInt(List::size).sum();

            String status = String.format("Vista: %s | Mes: %s %d | Eventos: %d%s",
                    viewModes.get(currentViewMode),
                    currentYearMonth.getMonth().getDisplayName(TextStyle.FULL, spanishLocale),
                    currentYearMonth.getYear(),
                    totalEvents,
                    userInfo);
            statusLabel.setText(status);
        }
    }

    // ========== EVENT HANDLERS ==========

    @FXML
    private void handleTodayClick() {
        currentYearMonth = YearMonth.now();
        selectedDate = LocalDate.now();
        loadEventsFromDatabaseAsync();
    }

    @FXML
    private void handlePreviousMonth() {
        currentYearMonth = currentYearMonth.minusMonths(1);
        loadEventsFromDatabaseAsync();
    }

    @FXML
    private void handleNextMonth() {
        currentYearMonth = currentYearMonth.plusMonths(1);
        loadEventsFromDatabaseAsync();
    }

    @FXML
    private void handleCreateButton() {
        if (authService.getCurrentUser() != null) {
            LocalDate dateToUse = selectedDate != null ? selectedDate : LocalDate.now();
            openEventDialog("CREATE", dateToUse);
        } else {
            showAlert("Error", "No hay usuario logueado", Alert.AlertType.ERROR);
        }
    }

    private void handleDateClick(LocalDate date) {
        selectedDate = date;
        if (date.getMonth() != currentYearMonth.getMonth() || date.getYear() != currentYearMonth.getYear()) {
            currentYearMonth = YearMonth.from(date);
            loadEventsFromDatabaseAsync();
        } else {
            updateCalendarView();
        }

        openEventDialog("READ", date);
    }

    private void openEventDialog(String mode, LocalDate date) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/event-dialog.fxml"));
            Parent dialogRoot = loader.load();
            EventDialogController dialogController = loader.getController();

            Runnable onEventChanged = () -> {
                System.out.println("✓ Recargando eventos tras cambio");
                loadEventsFromDatabaseAsync();
            };

            // Configurar el controlador según el modo
            if ("CREATE".equals(mode)) {
                dialogController.initializeForCreate(date, onEventChanged);
            } else if ("READ".equals(mode)) {
                dialogController.initializeForRead(date, onEventChanged);
            }

            Stage dialogStage = new Stage();

            dialogStage.initStyle(StageStyle.UNDECORATED);

            dialogStage.initModality(Modality.WINDOW_MODAL);
            dialogStage.initOwner(createButton.getScene().getWindow());

            Scene dialogScene = new Scene(dialogRoot);

            // Cargar estilos CSS
            try {
                dialogScene.getStylesheets().add(getClass().getResource("/css/dialog-styles.css").toExternalForm());
            } catch (Exception ignored) {
                System.out.println("No se pudo cargar CSS para el diálogo");
            }

            dialogStage.setScene(dialogScene);
            dialogStage.setResizable(false); // Desactivar redimensionado para mejor apariencia

            //  Hacer la ventana arrastrable
            makeDialogDraggable(dialogRoot, dialogStage);

            dialogStage.showAndWait();

        } catch (IOException e) {
            System.err.println("Error abriendo diálogo: " + e.getMessage());
            e.printStackTrace();
            showAlert("Error", "No se pudo abrir el diálogo de eventos:\n" + e.getMessage(),
                    Alert.AlertType.ERROR);
        }
    }

    // Método para hacer arrastrable el diálogo
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

    @FXML
    private void handleCloseButton() {
        if (authService.getCurrentUser() != null) {
            authService.logout();
        }
        Platform.exit();
        System.exit(0);
    }

    @FXML
    private void handleDayView() {
        currentViewMode = 0;
        updateViewModeUI();

        // Navegar a la vista diaria
        try {
            System.out.println("\n[" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "] " +
                    "Navegando a vista diaria...");

            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/calendar-day.fxml"));
            Parent dayRoot = loader.load();

            // Obtener la ventana actual y sus propiedades
            Stage stage = (Stage) calendarGrid.getScene().getWindow();

            // Guardar las dimensiones actuales SOLO si son válidas
            double currentWidth = stage.getWidth() > 100 ? stage.getWidth() : 1000;
            double currentHeight = stage.getHeight() > 100 ? stage.getHeight() : 700;
            boolean isMaximized = stage.isMaximized();

            Scene scene = new Scene(dayRoot);

            // Cargar los estilos CSS para la vista diaria
            scene.getStylesheets().add(getClass().getResource("/css/calendar-day.css").toExternalForm());

            // Aplicar la nueva escena
            stage.setScene(scene);
            stage.setTitle("UTEZ Calendar - Vista Día");

            // Configurar propiedades de la ventana
            stage.setResizable(true);
            stage.setMinWidth(800);
            stage.setMinHeight(600);

            // Restaurar dimensiones
            if (isMaximized) {
                stage.setMaximized(true);
            } else {
                stage.setWidth(currentWidth);
                stage.setHeight(currentHeight);
                // Centrar la ventana después del cambio
                Platform.runLater(() -> stage.centerOnScreen());
            }

            System.out.println("[" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "] " +
                    "Vista diaria cargada correctamente con dimensiones: " + currentWidth + "x" + currentHeight);

        } catch (IOException e) {
            System.err.println("✗ [" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "] " +
                    "Error cargando vista diaria: " + e.getMessage());
            e.printStackTrace();
            showAlert("Error", "No se pudo cargar la vista diaria:\n" + e.getMessage(), Alert.AlertType.ERROR);
        }
    }

    @FXML
    private void handleWeekView() {
        currentViewMode = 1;
        updateViewModeUI();

        // Navegar a la vista semanal
        try {
            System.out.println("\n[" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "] " +
                    "Navegando a vista semanal...");

            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/calendar-week.fxml"));
            Parent weekRoot = loader.load();

            // Obtener la ventana actual y sus propiedades
            Stage stage = (Stage) calendarGrid.getScene().getWindow();

            // Guardar las dimensiones actuales SOLO si son válidas
            double currentWidth = stage.getWidth() > 100 ? stage.getWidth() : 1200;
            double currentHeight = stage.getHeight() > 100 ? stage.getHeight() : 800;
            boolean isMaximized = stage.isMaximized();

            Scene scene = new Scene(weekRoot);

            // Cargar los estilos CSS para la vista semanal
            scene.getStylesheets().add(getClass().getResource("/css/styles-week.css").toExternalForm());

            // Aplicar la nueva escena
            stage.setScene(scene);
            stage.setTitle("UTEZ Calendar - Vista Semana");

            // Configurar propiedades de la ventana
            stage.setResizable(true);
            stage.setMinWidth(1000);
            stage.setMinHeight(700);

            // Restaurar dimensiones
            if (isMaximized) {
                stage.setMaximized(true);
            } else {
                stage.setWidth(currentWidth);
                stage.setHeight(currentHeight);
                // Centrar la ventana después del cambio
                Platform.runLater(() -> stage.centerOnScreen());
            }

            System.out.println("[" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "] " +
                    "Vista semanal cargada correctamente con dimensiones: " + currentWidth + "x" + currentHeight);

        } catch (IOException e) {
            System.err.println("✗ [" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "] " +
                    "Error cargando vista semanal: " + e.getMessage());
            e.printStackTrace();
            showAlert("Error", "No se pudo cargar la vista semanal:\n" + e.getMessage(), Alert.AlertType.ERROR);
        }
    }

    @FXML
    private void handleMonthView() {
        currentViewMode = 2;
        updateViewModeUI();
        updateCalendarView();
    }

    @FXML
    private void handleYearView() {
        currentViewMode = 3;
        updateViewModeUI();

        // Navegar a la vista anual
        try {
            System.out.println("\n[" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "] " +
                    "Navegando a vista anual...");

            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/calendar-year.fxml"));
            Parent yearRoot = loader.load();

            // Obtener el controlador de la vista anual
            CalendarYearController yearController = loader.getController();

            // Obtener la ventana actual y sus propiedades
            Stage stage = (Stage) calendarGrid.getScene().getWindow();

            // Guardar las dimensiones actuales SOLO si son válidas
            double currentWidth = stage.getWidth() > 100 ? stage.getWidth() : 1200;
            double currentHeight = stage.getHeight() > 100 ? stage.getHeight() : 800;
            boolean isMaximized = stage.isMaximized();

            Scene scene = new Scene(yearRoot);

            // Cargar los estilos CSS para la vista anual
            scene.getStylesheets().add(getClass().getResource("/css/styles-year.css").toExternalForm());

            // Aplicar la nueva escena
            stage.setScene(scene);
            stage.setTitle("UTEZ Calendar - Vista Anual");

            // Configurar propiedades de la ventana
            stage.setResizable(true);
            stage.setMinWidth(1000);
            stage.setMinHeight(700);

            // Restaurar dimensiones
            if (isMaximized) {
                stage.setMaximized(true);
            } else {
                stage.setWidth(currentWidth);
                stage.setHeight(currentHeight);
                // Centrar la ventana después del cambio
                Platform.runLater(() -> stage.centerOnScreen());
            }

            System.out.println("[" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "] " +
                    "Vista anual cargada correctamente con dimensiones: " + currentWidth + "x" + currentHeight);

        } catch (IOException e) {
            System.err.println("✗ [" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "] " +
                    "Error cargando vista anual: " + e.getMessage());
            e.printStackTrace();
            showAlert("Error", "No se pudo cargar la vista anual:\n" + e.getMessage(), Alert.AlertType.ERROR);
        }
    }

    // Método para añadir calendarios personalizados
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

    @FXML
    private void handleLogout(){
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

    private void updateViewModeUI() {
        updateStatusBar();
    }

    /**
     * Abre el diálogo para crear un evento con hora específica
     */
    private void openEventDialogWithTime(LocalDate date, LocalTime startTime, LocalTime endTime) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/event-dialog.fxml"));
            Parent dialogRoot = loader.load();
            EventDialogController dialogController = loader.getController();

            Runnable onEventChanged = () -> {
                System.out.println("✓ [" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "] " +
                        "Recargando eventos tras cambio");
                loadEventsFromDatabaseAsync();
            };

            // Usar el método específico para crear con tiempo
            dialogController.initializeForCreateWithTime(date, startTime, endTime, onEventChanged);

            Stage dialogStage = new Stage();
            dialogStage.setTitle("UTEZ Calendar - Nuevo Evento");
            dialogStage.initModality(Modality.WINDOW_MODAL);
            dialogStage.initOwner(createButton.getScene().getWindow());
            Scene dialogScene = new Scene(dialogRoot);

            try {
                dialogScene.getStylesheets().add(getClass().getResource("/css/dialog-styles.css").toExternalForm());
            } catch (Exception ignored) {
                System.out.println("No se pudo cargar CSS para el diálogo");
            }

            dialogStage.setScene(dialogScene);
            dialogStage.setResizable(true);
            dialogStage.setMinWidth(600);
            dialogStage.setMinHeight(500);
            dialogStage.showAndWait();

        } catch (IOException e) {
            System.err.println("Error abriendo diálogo con tiempo: " + e.getMessage());
            e.printStackTrace();
            showAlert("Error", "No se pudo abrir el diálogo de eventos:\n" + e.getMessage(),
                    Alert.AlertType.ERROR);
        }
    }

    /**
     * Abre el diálogo para visualizar un evento específico
     */
    public void openSpecificEvent(Event event) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/event-dialog.fxml"));
            Parent dialogRoot = loader.load();
            EventDialogController dialogController = loader.getController();

            Runnable onEventChanged = () -> {
                System.out.println("✓ Recargando eventos tras cambio");
                loadEventsFromDatabaseAsync();
            };

            dialogController.initializeForViewEvent(event, onEventChanged);

            Stage dialogStage = new Stage();

            // Configurar el diálogo sin barra de título pero funcional
            dialogStage.initStyle(StageStyle.UNDECORATED);
            dialogStage.initModality(Modality.WINDOW_MODAL);

            // Obtener la ventana padre de forma más robusta
            Stage parentStage = null;
            try {
                if (createButton != null && createButton.getScene() != null && createButton.getScene().getWindow() != null) {
                    parentStage = (Stage) createButton.getScene().getWindow();
                    dialogStage.initOwner(parentStage);
                }
            } catch (Exception e) {
                System.out.println("No se pudo establecer ventana padre: " + e.getMessage());
            }

            Scene dialogScene = new Scene(dialogRoot);

            try {
                dialogScene.getStylesheets().add(getClass().getResource("/css/dialog-styles.css").toExternalForm());
            } catch (Exception ignored) {
                System.out.println("No se pudo cargar CSS para el diálogo");
            }

            dialogStage.setScene(dialogScene);
            dialogStage.setResizable(false);

            // Establecer tamaño fijo para evitar problemas de visibilidad
            dialogStage.setWidth(600);
            dialogStage.setHeight(690);

            // Centrar el diálogo manualmente
            if (parentStage != null) {
                dialogStage.setX(parentStage.getX() + (parentStage.getWidth() - 600) / 2);
                dialogStage.setY(parentStage.getY() + (parentStage.getHeight() - 690) / 2);
            }

            // Hacer la ventana arrastrable
            makeDialogDraggable(dialogRoot, dialogStage);

            dialogStage.showAndWait();

        } catch (IOException e) {
            System.err.println("Error abriendo evento específico: " + e.getMessage());
            e.printStackTrace();
            showAlert("Error", "No se pudo abrir el evento:\n" + e.getMessage(), Alert.AlertType.ERROR);
        }
    }

    // ========== MÉTODOS PÚBLICOS ==========

    public void addEvent(LocalDate date, String eventName) {
        // Esta función está obsoleta con el nuevo sistema de cache
        loadEventsFromDatabaseAsync();
    }

    public void removeEvent(LocalDate date, String eventName) {
        // Esta función está obsoleta con el nuevo sistema de cache
        loadEventsFromDatabaseAsync();
    }

    public List<String> getEventsForDate(LocalDate date) {
        List<Event> events = eventCache.getOrDefault(date, new ArrayList<>());
        return events.stream().map(Event::getTitle).collect(java.util.stream.Collectors.toList());
    }

    public void navigateToDate(LocalDate date) {
        currentYearMonth = YearMonth.from(date);
        selectedDate = date;
        loadEventsFromDatabaseAsync();
    }

    // ========== MÉTODOS AUXILIARES ==========

    private void showAlert(String title, String message, Alert.AlertType type) {
        Platform.runLater(() -> {
            Alert alert = new Alert(type);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
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
}