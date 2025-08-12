package com.utez.calendario.controllers;

import com.utez.calendario.MainApp;
import com.utez.calendario.models.Calendar;
import com.utez.calendario.models.Event;
import com.utez.calendario.models.User;
import com.utez.calendario.services.AuthService;
import com.utez.calendario.services.EventService;
import com.utez.calendario.services.MailService;
import javafx.animation.FadeTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.HPos;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.geometry.VPos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

import java.io.IOException;
import java.net.URL;
import java.sql.SQLException;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class CalendarYearController implements Initializable {

    //========= CALENDARIOS PREDETERMINADOS ==========
    private Calendar calMisClases;
    private Calendar calTareas;
    private Calendar calPersonal;
    private Calendar calExamenes;

    // ========== CONSTANTES ==========
    private final Map<String, Calendar> buttonCalendarMap  = new HashMap<>();

    // Colores para calendarios predeterminados
    private static final String COLOR_CLASSES = "#1E76E8";  // Color para Mis Clases
    private static final String COLOR_TASKS = "#2c2c2c";    // Color para Tareas y Proyectos
    private static final String COLOR_PERSONAL = "#53C925"; // Color para Personal
    private static final String COLOR_EXAMS = "#f2c51f";    // Color para Exámenes
    private static final String COLOR_HOLIDAYS = "#FF6B35"; // Color para Días Festivos
    private static final String COLOR_UTEZ = "#8B5CF6";     // Color para UTEZ

    // ========== FXML ELEMENTS ==========
    @FXML private Label yearLabel;
    @FXML private GridPane yearGrid;
    @FXML private Label statusLabel;
    @FXML private Button createButton;
    @FXML private Button prevButton;
    @FXML private Button nextButton;

    // Checkboxes para calendarios
    @FXML private CheckBox userCalendarCheck;
    @FXML private CheckBox tasksCalendarCheck;
    @FXML private CheckBox personalCalendarCheck;
    @FXML private CheckBox examsCalendarCheck;
    @FXML private CheckBox holidaysCalendarCheck;
    @FXML private CheckBox utezCalendarCheck;
    @FXML private ScrollPane customCalendarsScroll;
    @FXML private VBox customCalendarsContainer;
    @FXML private Button addCalendarButton;
    @FXML private Button btnMisClases;
    @FXML private Button btnTareas;
    @FXML private Button btnPersonal;
    @FXML private Button btnExamenes;


    // ========== VARIABLES DE ESTADO ==========
    private int currentYear;
    private LocalDate selectedDate;
    private Map<LocalDate, List<Event>> events; // Cambiado para almacenar objetos Event
    private List<String> viewModes = Arrays.asList("Día", "Semana", "Mes", "Año");
    private int currentViewMode = 3; // Año por defecto

    private EventService eventService;
    private AuthService authService;

    private double xOffset = 0;
    private double yOffset = 0;

    // Cache para calendarios personalizados
    private List<Calendar> customCalendarsCache = new ArrayList<>();
    private List<Calendar> allCalendarsCache = new ArrayList<>(); // Cache para TODOS los calendarios
    private volatile boolean isLoadingEvents = false;
    private Map<String, CheckBox> customCalendarCheckboxes = new HashMap<>();
    private Map<String, Button> customCalendarDeleteButtons = new HashMap<>();
    private List<Calendar> sharedCalendarsCache = new ArrayList<>();

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        System.out.println("\n=== INICIANDO VISTA ANUAL ===");
        System.out.println("Fecha/Hora UTC: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));

        eventService = EventService.getInstance();
        authService = AuthService.getInstance();

        // Inicializar calendarios predeterminados
        String userId = authService.getCurrentUser() != null ?
                authService.getCurrentUser().getUserId() : "default";

        calMisClases = new Calendar("CAL0000001", "Mis Clases", COLOR_CLASSES, userId);
        calTareas = new Calendar("CAL0000002", "Tareas y Proyectos", COLOR_TASKS, userId);
        calPersonal = new Calendar("CAL0000003", "Personal", COLOR_PERSONAL, userId);
        calExamenes = new Calendar("CAL0000004", "Exámenes", COLOR_EXAMS, userId);

        // Configurar el mapa de botones-calendarios
        if (btnMisClases != null) buttonCalendarMap.put(btnMisClases.getText().toLowerCase(), calMisClases);
        if (btnTareas != null) buttonCalendarMap.put(btnTareas.getText().toLowerCase(), calTareas);
        if (btnPersonal != null) buttonCalendarMap.put(btnPersonal.getText().toLowerCase(), calPersonal);
        if (btnExamenes != null) buttonCalendarMap.put(btnExamenes.getText().toLowerCase(), calExamenes);

        if (authService.getCurrentUser() != null) {
            User currentUser = authService.getCurrentUser();
            System.out.println("Usuario logueado: " + currentUser.getDisplayInfo());
        } else {
            System.out.println("No hay usuario logueado");
        }

        initializeYearView();
        setupAnimations();
        setupCalendarCheckboxes();
        updateStatusBar();

        Platform.runLater(() -> {
            setupYearGridConstraints();
            loadCustomCalendarsAsync(); // Cargar calendarios personalizados de forma asíncrona
        });

        System.out.println("Vista anual inicializada correctamente");
        System.out.println("===============================\n");
    }

    private void initializeYearView() {
        currentYear = LocalDate.now().getYear();
        selectedDate = LocalDate.now();
        events = new HashMap<>();
        updateYearView();
    }

    private void setupYearGridConstraints() {
        if (yearGrid != null) {
            VBox.setVgrow(yearGrid, Priority.ALWAYS);
            HBox.setHgrow(yearGrid, Priority.ALWAYS);
            yearGrid.setMaxWidth(Double.MAX_VALUE);
            yearGrid.setMaxHeight(Double.MAX_VALUE);
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
                loadEventsFromDatabase();
            });
        });
    }

    // Método actualizado para determinar si mostrar un evento
    private boolean shouldShowEvent(Event event) {
        String calendarId = event.getCalendarId();

        System.out.println("🔍 Verificando visibilidad para evento: " + event.getTitle());
        System.out.println("   📋 Calendario ID: " + calendarId);

        // Buscar el calendario en todos los caches
        String calendarName = "";
        boolean isSharedCalendar = false;

        // Buscar en calendarios propios
        if (allCalendarsCache != null) {
            for (Calendar cal : allCalendarsCache) {
                if (cal.getCalendarId().equals(calendarId)) {
                    calendarName = cal.getName().toLowerCase();
                    System.out.println("   ✅ Encontrado en cache propio: " + cal.getName());
                    break;
                }
            }
        }

        // Si no se encuentra, buscar en calendarios compartidos
        if (calendarName.isEmpty() && sharedCalendarsCache != null) {
            for (Calendar cal : sharedCalendarsCache) {
                if (cal.getCalendarId().equals(calendarId)) {
                    calendarName = cal.getName().toLowerCase();
                    isSharedCalendar = true;
                    System.out.println("   ✅ Encontrado en cache compartido: " + cal.getName());
                    break;
                }
            }
        }

        if (calendarName.isEmpty()) {
            System.out.println("   ❌ Calendario no encontrado en caches para ID: " + calendarId);
            // Intentar obtener de BD como última opción
            try {
                Calendar cal = Calendar.getCalendarById(calendarId);
                if (cal != null) {
                    calendarName = cal.getName().toLowerCase();
                    System.out.println("   ✅ Obtenido de BD: " + cal.getName());
                }
            } catch (Exception e) {
                System.err.println("   ❌ Error obteniendo calendario de BD: " + e.getMessage());
            }
        }

        boolean shouldShow = shouldShowCalendarByName(calendarName, calendarId);

        System.out.println("   📊 Resultado:");
        System.out.println("      - Nombre calendario: " + calendarName);
        System.out.println("      - Es compartido: " + isSharedCalendar);
        System.out.println("      - Mostrar evento: " + shouldShow);

        return shouldShow;
    }

    // Método auxiliar para centralizar la lógica de visibilidad
    private boolean shouldShowCalendarByName(String calendarName, String calendarId) {
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

        // Verificar calendarios personalizados/compartidos por ID
        if (customCalendarCheckboxes != null && customCalendarCheckboxes.containsKey(calendarId)) {
            CheckBox checkBox = customCalendarCheckboxes.get(calendarId);
            return checkBox != null && checkBox.isSelected();
        }

        // Fallback: verificar IDs fijos
        switch (calendarId) {
            case "CAL0000001": return userCalendarCheck != null && userCalendarCheck.isSelected();
            case "CAL0000002": return tasksCalendarCheck != null && tasksCalendarCheck.isSelected();
            case "CAL0000003": return personalCalendarCheck != null && personalCalendarCheck.isSelected();
            case "CAL0000004": return examsCalendarCheck != null && examsCalendarCheck.isSelected();
            case "CAL0000005": return holidaysCalendarCheck != null && holidaysCalendarCheck.isSelected();
            case "CAL0000006": return utezCalendarCheck != null && utezCalendarCheck.isSelected();
            default:
                System.out.println("🔍 No se pudo determinar visibilidad para: " + calendarName + " (ID: " + calendarId + ")");
                return true; // Mostrar por defecto
        }
    }

    // Método asíncrono para cargar calendarios personalizados
    private void loadCustomCalendarsAsync() {
        CompletableFuture.supplyAsync(() -> {
            try {
                if (authService.getCurrentUser() != null) {
                    String userId = authService.getCurrentUser().getUserId();

                    // Cargar TODOS los calendarios del usuario (predeterminados + personalizados)
                    List<Calendar> allCalendars = Calendar.getAllUserCalendars(userId);
                    List<Calendar> customCalendars = Calendar.getUserCustomCalendars(userId);
                    List<Calendar> sharedCalendars = Calendar.getSharedCalendars(userId);

                    Map<String, Object> result = new HashMap<>();
                    result.put("all", allCalendars);
                    result.put("custom", customCalendars);
                    result.put("shared", sharedCalendars);
                    return result;
                }
            } catch (SQLException e) {
                e.printStackTrace();
                Platform.runLater(() -> showAlert("Error", "No se pudieron cargar los calendarios: " + e.getMessage(), Alert.AlertType.ERROR));
            }
            return new HashMap<String, Object>();
        }).thenAccept(result -> {
            Platform.runLater(() -> {
                @SuppressWarnings("unchecked")
                List<Calendar> allCalendars = (List<Calendar>) result.get("all");
                @SuppressWarnings("unchecked")
                List<Calendar> customCalendars = (List<Calendar>) result.get("custom");
                @SuppressWarnings("unchecked")
                List<Calendar> sharedCalendars = (List<Calendar>) result.get("shared");

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

                if (sharedCalendars != null) {
                    sharedCalendarsCache = sharedCalendars;
                    System.out.println("Calendarios compartidos cargados: " + sharedCalendars.size());
                }

                loadCustomCalendarsUI();
                loadEventsFromDatabase();
            });
        }).exceptionally(throwable -> {
            System.err.println("Error cargando calendarios: " + throwable.getMessage());
            Platform.runLater(() -> {
                // Fallback: crear mapeo manual si no existe el método getAllUserCalendars
                createManualCalendarMapping();
                loadEventsFromDatabase();
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

            System.out.println("Mapeo manual creado con " + allCalendarsCache.size() + " calendarios");
        }

        loadCustomCalendarsUI();
    }

    // Método para actualizar la UI con los calendarios personalizados
    private void loadCustomCalendarsUI() {
        // Limpiar estructuras existentes
        if (customCalendarCheckboxes == null) {
            customCalendarCheckboxes = new HashMap<>();
        } else {
            customCalendarCheckboxes.clear();
        }

        if (customCalendarDeleteButtons == null) {
            customCalendarDeleteButtons = new HashMap<>();
        } else {
            customCalendarDeleteButtons.clear();
        }

        // Limpiar contenedor principal
        if (customCalendarsContainer != null) {
            customCalendarsContainer.getChildren().clear();

            System.out.println("Cargando calendarios:");
            System.out.println(" - Personalizados: " + (customCalendarsCache != null ? customCalendarsCache.size() : 0));
            System.out.println(" - Compartidos: " + (sharedCalendarsCache != null ? sharedCalendarsCache.size() : 0));

            // Cargar calendarios personalizados primero
            if (customCalendarsCache != null) {
                for (Calendar cal : customCalendarsCache) {
                    addCalendarToUI(cal, false); // false = no es compartido
                }
            }

            // Cargar calendarios compartidos después
            if (sharedCalendarsCache != null) {
                for (Calendar cal : sharedCalendarsCache) {
                    addCalendarToUI(cal, true); // true = es compartido
                }
            }
        } else {
            System.err.println("Error: customCalendarsContainer es null");
        }
    }

    // Método auxiliar simplificado para agregar calendarios a la UI
    private void addCalendarToUI(Calendar cal, boolean isShared) {
        // Crear contenedor horizontal
        HBox calendarRow = new HBox();
        calendarRow.getStyleClass().addAll("calendar-item-modern", "calendar-item-custom");
        calendarRow.setSpacing(5);
        calendarRow.setAlignment(Pos.CENTER_LEFT);

        // Crear checkbox
        CheckBox checkBox = new CheckBox();
        checkBox.setSelected(true);
        customCalendarCheckboxes.put(cal.getCalendarId(), checkBox);
        checkBox.setOnAction(e -> refreshCalendarDisplayAsync());

        // Aplicar estilo de color
        String colorHex = cal.getColor();
        String colorStyle = String.format("-fx-text-fill: %s;", colorHex);
        checkBox.setStyle(colorStyle);

        // Ícono distintivo para calendarios compartidos
        if (isShared) {
            Label sharedIcon = new Label("👥");
            sharedIcon.setTooltip(new Tooltip("Compartido contigo"));
            sharedIcon.setStyle("-fx-font-size: 12px; -fx-padding: 0 3 0 0;");
            calendarRow.getChildren().add(sharedIcon);
        }

        // Botón para el nombre (ahora abre diálogo de compartir)
        Button nameButton = new Button(cal.getName());
        nameButton.getStyleClass().add("calendar-name-button");
        nameButton.setStyle("-fx-text-fill: " + cal.getColor() + ";");
        nameButton.setOnAction(e -> handleCalendarSelection(cal)); // Usa tu método existente

        // Agregar checkbox y nombre
        calendarRow.getChildren().addAll(checkBox, nameButton);

        // Región que crece para empujar elementos hacia la derecha
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        calendarRow.getChildren().add(spacer);

        // Solo agregar botón de eliminar para calendarios personales
        if (!isShared) {
            Button deleteButton = new Button("🗑");
            deleteButton.getStyleClass().add("delete-calendar-button");
            deleteButton.setStyle("-fx-background-color: #ff4444; -fx-text-fill: white; -fx-font-size: 10px; -fx-padding: 2 6 2 6;");
            deleteButton.setTooltip(new Tooltip("Eliminar calendario"));
            customCalendarDeleteButtons.put(cal.getCalendarId(), deleteButton);

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

            deleteButton.setMinWidth(32);
            deleteButton.setMaxWidth(32);
            calendarRow.getChildren().add(deleteButton);

        } else {
            // Para calendarios compartidos, agregar indicador de solo lectura
            Label readOnlyLabel = new Label("Solo lectura");
            readOnlyLabel.setStyle("-fx-font-size: 9px; -fx-text-fill: #666; -fx-padding: 2 6 2 6;");
            readOnlyLabel.setTooltip(new Tooltip("No puedes eliminar calendarios compartidos"));
            calendarRow.getChildren().add(readOnlyLabel);
        }

        // Agregar al contenedor principal
        customCalendarsContainer.getChildren().add(calendarRow);

        System.out.println("Añadido calendario " + (isShared ? "compartido" : "personal") +
                ": " + cal.getName() + " con color: " + colorHex);
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

    private void loadEventsFromDatabase() {
        if (authService.getCurrentUser() != null) {
            String userId = authService.getCurrentUser().getUserId();

            System.out.println("\n[" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "] " +
                    "Cargando eventos anuales (incluyendo compartidos)...");
            System.out.println("Usuario ID: " + userId);
            System.out.println("Año actual: " + currentYear);

            try {
                // Cargar todos los eventos del año (propios + compartidos)
                LocalDate startOfYear = LocalDate.of(currentYear, 1, 1);
                LocalDate endOfYear = LocalDate.of(currentYear, 12, 31);

                // ¡CAMBIO IMPORTANTE! Usar el nuevo método que incluye eventos compartidos
                List<Event> yearEvents = eventService.getEventsForDateRangeIncludingShared(userId, startOfYear, endOfYear);
                events.clear();

                // Filtrar eventos según la configuración de visibilidad de calendarios
                for (Event event : yearEvents) {
                    if (shouldShowEvent(event)) {
                        LocalDate eventDate = event.getStartDate().toLocalDate();
                        events.computeIfAbsent(eventDate, k -> new ArrayList<>()).add(event);
                    }
                }

                updateYearView();
                System.out.println("[" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "] " +
                        "Eventos anuales (incluyendo compartidos) cargados: " + yearEvents.size());

            } catch (Exception e) {
                System.err.println("Error cargando eventos: " + e.getMessage());
                e.printStackTrace();
                showAlert("Error de Conexión",
                        "No se pueden cargar los eventos.\nVerifica tu conexión y configuración.",
                        Alert.AlertType.WARNING);
            }
        }
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

    private void updateYearView() {
        updateYearLabel();
        populateYearGrid();
        updateStatusBar();

        if (yearGrid != null) {
            FadeTransition fadeTransition = new FadeTransition(Duration.millis(300), yearGrid);
            fadeTransition.setFromValue(0.8);
            fadeTransition.setToValue(1.0);
            fadeTransition.play();
        }
    }

    private void updateYearLabel() {
        if (yearLabel != null) {
            yearLabel.setText(String.valueOf(currentYear));
        }
    }

    private void populateYearGrid() {
        if (yearGrid == null) return;

        yearGrid.getChildren().clear();
        yearGrid.getColumnConstraints().clear();
        yearGrid.getRowConstraints().clear();

        // Configurar grid 4x3 para 12 meses
        for (int i = 0; i < 4; i++) {
            ColumnConstraints colConstraints = new ColumnConstraints();
            colConstraints.setPercentWidth(25.0);
            colConstraints.setHalignment(HPos.CENTER);
            colConstraints.setHgrow(Priority.ALWAYS);
            colConstraints.setFillWidth(true);
            yearGrid.getColumnConstraints().add(colConstraints);
        }

        for (int i = 0; i < 3; i++) {
            RowConstraints rowConstraints = new RowConstraints();
            rowConstraints.setPercentHeight(33.33);
            rowConstraints.setVgrow(Priority.ALWAYS);
            rowConstraints.setFillHeight(true);
            rowConstraints.setValignment(VPos.CENTER);
            yearGrid.getRowConstraints().add(rowConstraints);
        }

        // Crear mini-calendarios para cada mes
        for (int month = 0; month < 12; month++) {
            VBox miniCalendar = createMiniCalendar(month);
            int row = month / 4;
            int col = month % 4;
            yearGrid.add(miniCalendar, col, row);
        }
    }

    private VBox createMiniCalendar(int month) {
        VBox vbox = new VBox(4);
        vbox.getStyleClass().add("mini-calendar");
        vbox.setPrefWidth(200);
        vbox.setAlignment(Pos.CENTER);

        // Nombre del mes en mayúsculas clickeable para navegar al mes
        Label lblMonth = new Label(Month.of(month + 1).getDisplayName(TextStyle.FULL, new Locale("es")).toUpperCase());
        lblMonth.getStyleClass().add("mini-month-title");
        lblMonth.getStyleClass().add("clickable-month"); // Nueva clase para estilo hover
        lblMonth.setMaxWidth(Double.MAX_VALUE);
        lblMonth.setAlignment(Pos.CENTER);

        //Agregar evento de clic al nombre del mes
        lblMonth.setOnMouseClicked(e -> navigateToMonth(month, 1));

        // Días de la semana
        HBox weekDays = new HBox(2);
        weekDays.setAlignment(Pos.CENTER);
        weekDays.getStyleClass().add("mini-week-days");

        String[] dayNames = {"D", "L", "M", "M", "J", "V", "S"};
        for (String dayName : dayNames) {
            Label day = new Label(dayName);
            day.getStyleClass().add("mini-day-header");
            day.setPrefWidth(20);
            day.setAlignment(Pos.CENTER);
            weekDays.getChildren().add(day);
        }

        // Días del mes (grid)
        GridPane daysGrid = new GridPane();
        daysGrid.setHgap(1);
        daysGrid.setVgap(1);
        daysGrid.setAlignment(Pos.CENTER);
        daysGrid.getStyleClass().add("mini-days-grid");

        LocalDate firstDay = LocalDate.of(currentYear, month + 1, 1);
        int lengthOfMonth = firstDay.lengthOfMonth();
        int startDay = firstDay.getDayOfWeek().getValue() % 7; // Lunes=1,...,Domingo=7=>0

        // Calcular días del mes anterior para llenar espacios vacíos
        LocalDate prevMonth = firstDay.minusMonths(1);
        int prevMonthDays = prevMonth.lengthOfMonth();

        // Calcular días del mes siguiente
        LocalDate nextMonth = firstDay.plusMonths(1);

        int dayNum = 1;
        int nextMonthDay = 1;

        for (int week = 0; week < 6; week++) {
            for (int dow = 0; dow < 7; dow++) {
                Label dayLabel = new Label();
                dayLabel.setPrefWidth(20);
                dayLabel.setAlignment(Pos.CENTER);

                if (week == 0 && dow < startDay) {
                    // Días del mes anterior
                    int prevDay = prevMonthDays - startDay + dow + 1;
                    dayLabel.setText(String.valueOf(prevDay));
                    dayLabel.getStyleClass().add("mini-day-other-month");

                    // Click handler para navegar al mes anterior
                    final int finalPrevMonth = month - 1 < 0 ? 11 : month - 1;
                    final int finalPrevDay = prevDay;
                    final int finalPrevYear = month - 1 < 0 ? currentYear - 1 : currentYear;
                    dayLabel.setOnMouseClicked(e -> navigateToSpecificMonthAndDay(finalPrevYear, finalPrevMonth, finalPrevDay));

                } else if (dayNum <= lengthOfMonth) {
                    // Días del mes actual
                    LocalDate dayDate = LocalDate.of(currentYear, month + 1, dayNum);
                    dayLabel.setText(String.valueOf(dayNum));
                    dayLabel.getStyleClass().add("mini-day");

                    // Destacar día actual
                    if (dayDate.equals(LocalDate.now())) {
                        dayLabel.getStyleClass().add("mini-day-today");
                    }

                    // Indicar si hay eventos
                    if (events.containsKey(dayDate)) {
                        dayLabel.getStyleClass().add("mini-day-with-events");
                    }

                    // Click handler para navegar al mes
                    final int finalMonth = month;
                    final int finalDay = dayNum;
                    dayLabel.setOnMouseClicked(e -> navigateToSpecificMonthAndDay(currentYear, finalMonth, finalDay));

                    dayNum++;
                } else {
                    // Días del mes siguiente
                    dayLabel.setText(String.valueOf(nextMonthDay));
                    dayLabel.getStyleClass().add("mini-day-other-month");

                    // Click handler para navegar al mes siguiente
                    final int finalNextMonth = month + 1 > 11 ? 0 : month + 1;
                    final int finalNextDay = nextMonthDay;
                    final int finalNextYear = month + 1 > 11 ? currentYear + 1 : currentYear;
                    dayLabel.setOnMouseClicked(e -> navigateToSpecificMonthAndDay(finalNextYear, finalNextMonth, finalNextDay));

                    nextMonthDay++;
                }

                daysGrid.add(dayLabel, dow, week);
            }

            // Si ya hemos completado todos los días del mes y los siguientes necesarios, salir
            if (dayNum > lengthOfMonth && nextMonthDay > 7) {
                break;
            }
        }

        vbox.getChildren().addAll(lblMonth, weekDays, daysGrid);
        return vbox;
    }

    private void navigateToMonth(int month, int day) {
        navigateToSpecificMonthAndDay(currentYear, month, day);
    }

    //  Navegación específica a un mes y día concretos
    private void navigateToSpecificMonthAndDay(int year, int month, int day) {
        try {
            // Cargar la vista mensual
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/calendar-month.fxml"));
            Parent monthRoot = loader.load();

            // Obtener el controlador y enviarle la fecha seleccionada
            CalendarMonthController monthController = loader.getController();

            // Navegar a la fecha específica utilizando el método del controlador mensual
            LocalDate targetDate = LocalDate.of(year, month + 1, day);

            // NOTA: Esta línea es crítica - navega específicamente a la fecha
            monthController.navigateToDate(targetDate);

            // Cambiar la escena
            Stage stage = (Stage) yearGrid.getScene().getWindow();
            Scene scene = new Scene(monthRoot);
            scene.getStylesheets().add(getClass().getResource("/css/styles-month.css").toExternalForm());
            stage.setScene(scene);

            System.out.println("Navegación exitosa a " + targetDate);

        } catch (IOException e) {
            System.err.println("Error navegando a vista mensual: " + e.getMessage());
            e.printStackTrace();
            showAlert("Error", "No se pudo navegar a la vista mensual:\n" + e.getMessage(), Alert.AlertType.ERROR);
        }
    }

    private void updateStatusBar() {
        if (statusLabel != null) {
            String userInfo = "";
            if (authService.getCurrentUser() != null) {
                User user = authService.getCurrentUser();
                userInfo = " | " + user.getDisplayId() + " (" + user.getRole().getDisplayName() + ")";
            }

            // Contar eventos totales en cache
            int totalEvents = events.values().stream().mapToInt(List::size).sum();

            String status = String.format("Vista: %s | Año: %d | Eventos: %d%s",
                    viewModes.get(currentViewMode),
                    currentYear,
                    totalEvents,
                    userInfo);
            statusLabel.setText(status);
        }
    }

    // ========== EVENT HANDLERS ==========

    @FXML
    private void handleTodayClick() {
        currentYear = LocalDate.now().getYear();
        selectedDate = LocalDate.now();
        loadEventsFromDatabase();
    }

    @FXML
    private void handlePreviousYear() {
        currentYear--;
        loadEventsFromDatabase();
    }

    @FXML
    private void handleNextYear() {
        currentYear++;
        loadEventsFromDatabase();
    }

    @FXML
    private void handleCreateButton() {
        if (authService.getCurrentUser() != null) {
            openEventDialog("CREATE", selectedDate != null ? selectedDate : LocalDate.now());
        } else {
            showAlert("Error", "No hay usuario logueado", Alert.AlertType.ERROR);
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
            dialogStage.initOwner(yearGrid.getScene().getWindow());

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

    //wbd pa compartir calendario XDD y si ven esto cópienlo en los demás controllers que tengan una vista XD
    private void handleShareCalendar(Calendar selectedCalendar) {

        try {

            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/share-calendar-dialog.fxml"));
            Parent dialogRoot = loader.load();
            ShareCalendarDialogController dialogController = loader.getController();

            Stage dialogStage = new Stage();

            dialogStage.initStyle(StageStyle.UNDECORATED);
            dialogStage.initModality(Modality.WINDOW_MODAL);
            dialogStage.initOwner(yearGrid.getScene().getWindow());

            MailService mailService = MainApp.getEmailService();

            dialogController.setMailService(mailService);
            dialogController.setDialogStage(dialogStage);
            dialogController.setCalendar(selectedCalendar);

            Scene dialogScene = new Scene(dialogRoot);

            try {
                dialogScene.getStylesheets().add(getClass().getResource("/css/dialog-styles.css").toExternalForm());
            } catch (Exception ignored) {
                System.out.println("No se pudo cargar CSS para el diálogo");
            }

            dialogStage.setScene(dialogScene);
            dialogStage.setResizable(false);

            // Hacer la ventana arrastrable
            makeDialogDraggable(dialogRoot, dialogStage);

            dialogStage.showAndWait();

        } catch (IOException e) {
            System.err.println("Error abriendo diálogo: " + e.getMessage());
            e.printStackTrace();
            showAlert("Error", "No se pudo abrir el diálogo para compartir calendario", Alert.AlertType.ERROR);
        }

    }

    private void openEventDialog(String mode, LocalDate date) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/event-dialog.fxml"));
            Parent dialogRoot = loader.load();
            EventDialogController dialogController = loader.getController();

            Runnable onEventChanged = () -> {
                System.out.println("✓ [" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "] " +
                        "Recargando eventos tras cambio");
                loadEventsFromDatabase();
            };

            // Obtener información del usuario actual para identificar si es docente
            User currentUser = authService.getCurrentUser();
            boolean isTeacher = currentUser != null && currentUser.isTeacher();

            // Configurar el controlador según el modo
            if ("CREATE".equals(mode)) {
                dialogController.initializeForCreate(date, onEventChanged);
                // Configurar opciones de docente si corresponde
                dialogController.setIsTeacher(isTeacher);
            }

            Stage dialogStage = new Stage();

            //Remover decoraciones de la ventana
            dialogStage.initStyle(StageStyle.UNDECORATED);

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
            dialogStage.setMinHeight(550);

            // Hacer la ventana arrastrable
            makeDialogDraggable(dialogRoot, dialogStage);

            dialogStage.showAndWait();

        } catch (IOException e) {
            System.err.println("Error abriendo diálogo: " + e.getMessage());
            e.printStackTrace();
            showAlert("Error", "No se pudo abrir el diálogo de eventos:\n" + e.getMessage(),
                    Alert.AlertType.ERROR);
        }
    }

    // Hacer diálogo arrastrable
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
    }

    @FXML
    private void handleDayView() {
        // Navegar a vista de día
        navigateToView("day");
    }

    @FXML
    private void handleWeekView() {
        // Navegar a vista de semana
        navigateToView("week");
    }

    @FXML
    private void handleMonthView() {
        // Navegar a vista mensual
        navigateToView("month");
    }

    @FXML
    private void handleYearView() {
        // Ya estamos en vista anual
        updateYearView();
    }

    // Esto es lo q si alguno de usteddes lo ve, lo copie en los demás controllers q tengan una vista Xd
    // 1. Manejador para calendarios predeterminados
    @FXML
    public void handleCalendarNameClick(ActionEvent event) {
        if (!(event.getSource() instanceof Button clickedButton)) return;

        Calendar calendar = findCalendarByName(clickedButton.getText());

        if (calendar != null) {
            handleCalendarSelection(calendar);
        } else {
            System.err.println("Calendario no encontrado: " + clickedButton.getText());
            // Mostrar mensaje de error al usuario si es necesario
        }
    }

    // 2. Método común para ambos
    private void handleCalendarSelection(Calendar calendar) {
        if (calendar == null) {
            System.err.println("Intento de editar calendario nulo");
            return;
        }

        System.out.println("Editando calendario: " + calendar.getName());

        handleShareCalendar(calendar);
    }

    //Hasta acá XD
    private Calendar findCalendarByName(String buttonText) {
        if (buttonText == null || buttonText.trim().isEmpty()) {
            return null;
        }

        String searchName = buttonText.toLowerCase().trim();

        // Primero buscar en el mapa de botones (si está bien inicializado)
        if (buttonCalendarMap.containsKey(searchName)) {
            return buttonCalendarMap.get(searchName);
        }

        // Mapeo directo como fallback
        switch (searchName) {
            case "mis clases":
                return calMisClases;
            case "tareas y proyectos":
            case "tareas":
                return calTareas;
            case "personal":
                return calPersonal;
            case "exámenes":
            case "examenes":
                return calExamenes;
        }

        // Buscar en cache de calendarios
        if (allCalendarsCache != null) {
            for (Calendar cal : allCalendarsCache) {
                String calendarName = cal.getName().toLowerCase().trim();
                if (calendarName.equals(searchName) ||
                        calendarName.contains(searchName) ||
                        searchName.contains(calendarName)) {
                    return cal;
                }
            }
        }

        System.err.println("Calendario no encontrado para: '" + buttonText + "'");
        return null;
    }

    private void navigateToView(String view) {
        try {
            System.out.println("\n[" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "] " +
                    "Navegando a vista: " + view);

            String fxmlFile = "/fxml/calendar-" + view + ".fxml";
            String cssFile = "/css/styles-" + view + ".css";

            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlFile));
            Parent root = loader.load();

            // Obtener la ventana actual y sus propiedades
            Stage stage = (Stage) yearGrid.getScene().getWindow();

            // Guardar las dimensiones actuales SOLO si no están en valores por defecto
            double currentWidth = stage.getWidth() > 100 ? stage.getWidth() : 1200;
            double currentHeight = stage.getHeight() > 100 ? stage.getHeight() : 800;
            boolean isMaximized = stage.isMaximized();

            Scene scene = new Scene(root);
            scene.getStylesheets().add(getClass().getResource(cssFile).toExternalForm());

            // Aplicar la nueva escena
            stage.setScene(scene);
            stage.setTitle("UTEZ Calendar - Vista " + view.substring(0, 1).toUpperCase() + view.substring(1));

            // Configurar propiedades de la ventana
            stage.setResizable(true);
            stage.setMinWidth(1000);
            stage.setMinHeight(700);

            // Restaurar dimensiones o usar las por defecto
            if (isMaximized) {
                stage.setMaximized(true);
            } else {
                stage.setWidth(currentWidth);
                stage.setHeight(currentHeight);
                // Centrar la ventana después del cambio de vista
                Platform.runLater(() -> stage.centerOnScreen());
            }

            System.out.println("Navegación completada a vista: " + view);

        } catch (IOException e) {
            System.err.println("Error navegando a vista " + view + ": " + e.getMessage());
            e.printStackTrace();
            showAlert("Error de Navegación",
                    "No se pudo cargar la vista " + view + ":\n" + e.getMessage(),
                    Alert.AlertType.ERROR);
        }
    }

    private void showAlert(String title, String message, Alert.AlertType type) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    /// ///CERRAR SESION

    //cosas para el logout
    @FXML
    private StackPane contentArea;
    private Timeline clockTimeline;

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

            Stage stage = (Stage) yearGrid.getScene().getWindow();

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
}