package com.utez.calendario.controllers;

import com.utez.calendario.models.Event;
import com.utez.calendario.models.User;
import com.utez.calendario.services.AuthService;
import com.utez.calendario.services.EventService;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URL;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class CalendarWeekController implements Initializable {

    @FXML private Label monthYearLabel;
    @FXML private GridPane calendarGrid;
    @FXML private ScrollPane weekScrollPane;
    @FXML private Button createButton;
    @FXML private CheckBox userCalendarCheck;
    @FXML private CheckBox tasksCalendarCheck;
    @FXML private CheckBox personalCalendarCheck;
    @FXML private CheckBox examsCalendarCheck;
    @FXML private CheckBox holidaysCalendarCheck;
    @FXML private CheckBox utezCalendarCheck;

    private LocalDate startOfWeek;
    private LocalDate selectedDate;
    private Map<LocalDate, List<Event>> events;
    private int currentViewMode = 1; // Semana

    private EventService eventService;
    private AuthService authService;

    // Configuración de horas - 24 HORAS
    private static final int START_HOUR = 0;  // 12 AM
    private static final int END_HOUR = 23;   // 11 PM
    private static final int TOTAL_HOURS = END_HOUR - START_HOUR + 1; // 24 horas

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        System.out.println("\n=== INICIANDO VISTA SEMANAL ===");
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
            loadEventsFromDatabase();
            setupScrollPane();
        });
    }

    private void initializeCalendar() {
        selectedDate = LocalDate.now();
        startOfWeek = selectedDate.with(DayOfWeek.SUNDAY);
        events = new HashMap<>();
        updateCalendarView();
    }

    private void setupCalendarCheckboxes() {
        // Agregar listeners para los checkboxes de calendarios
        if (userCalendarCheck != null) {
            userCalendarCheck.setOnAction(e -> refreshCalendarDisplay());
        }
        if (tasksCalendarCheck != null) {
            tasksCalendarCheck.setOnAction(e -> refreshCalendarDisplay());
        }
        if (personalCalendarCheck != null) {
            personalCalendarCheck.setOnAction(e -> refreshCalendarDisplay());
        }
        if (examsCalendarCheck != null) {
            examsCalendarCheck.setOnAction(e -> refreshCalendarDisplay());
        }
        if (holidaysCalendarCheck != null) {
            holidaysCalendarCheck.setOnAction(e -> refreshCalendarDisplay());
        }
        if (utezCalendarCheck != null) {
            utezCalendarCheck.setOnAction(e -> refreshCalendarDisplay());
        }
    }

    private void refreshCalendarDisplay() {
        Platform.runLater(this::createWeekView);
    }

    private void setupScrollPane() {
        if (weekScrollPane != null) {
            weekScrollPane.setFitToWidth(true);
            weekScrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
            weekScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED); //
            // Scroll a las 8 AM al inicio
            Platform.runLater(() -> {
                double scrollPosition = 8.0 / TOTAL_HOURS;
                weekScrollPane.setVvalue(scrollPosition);
            });
        }
    }

    private void setupCalendarGrid() {

        if (calendarGrid == null) return;

        calendarGrid.getChildren().clear();
        calendarGrid.getColumnConstraints().clear();
        calendarGrid.getRowConstraints().clear();


        ColumnConstraints hourColumn = new ColumnConstraints();
        hourColumn.setMinWidth(60);
        hourColumn.setPrefWidth(60);
        hourColumn.setMaxWidth(60);
        calendarGrid.getColumnConstraints().add(hourColumn);

        for (int i = 0; i < 7; i++) {
            ColumnConstraints dayColumn = new ColumnConstraints();
            dayColumn.setMinWidth(94);
            dayColumn.setPercentWidth(94.0 / 7.0);
            dayColumn.setHgrow(Priority.ALWAYS);
            calendarGrid.getColumnConstraints().add(dayColumn);
        }


        RowConstraints headerRow = new RowConstraints();
        headerRow.setMinHeight(60);
        headerRow.setPrefHeight(60);
        calendarGrid.getRowConstraints().add(headerRow);

        // Filas para cada hora
        for (int i = 0; i < TOTAL_HOURS; i++) {
            RowConstraints hourRow = new RowConstraints();
            hourRow.setVgrow(Priority.NEVER);
            calendarGrid.getRowConstraints().add(hourRow);
        }

        createWeekView();
    }

    private void createWeekView() {
        // Limpiar contenido existente pero mantener constraints
        calendarGrid.getChildren().clear();

        // Celda vacía para esquina superior izquierda
        Label cornerLabel = new Label("");
        cornerLabel.getStyleClass().add("corner-cell");
        calendarGrid.add(cornerLabel, 0, 0);

        // Encabezados de días
        String[] dayNames = {"DOM", "LUN", "MAR", "MIE", "JUE", "VIE", "SAB"};
        for (int day = 0; day < 7; day++) {
            LocalDate date = startOfWeek.plusDays(day);
            VBox dayHeader = createDayHeader(dayNames[day], date);
            calendarGrid.add(dayHeader, day + 1, 0);
        }

        // Etiquetas de horas y celdas
        for (int hour = 0; hour < TOTAL_HOURS; hour++) {
            int actualHour = START_HOUR + hour;

            // Etiqueta de hora
            Label hourLabel = createHourLabel(actualHour);
            calendarGrid.add(hourLabel, 0, hour + 1);

            // Celdas para cada día en esta hora
            for (int day = 0; day < 7; day++) {
                LocalDate cellDate = startOfWeek.plusDays(day);
                VBox hourCell = createHourCell(cellDate, actualHour);
                calendarGrid.add(hourCell, day + 1, hour + 1);
            }
        }

        System.out.println("✓ Vista semanal creada con " + TOTAL_HOURS + " horas");
    }

    private VBox createDayHeader(String dayName, LocalDate date) {
        VBox header = new VBox();
        header.getStyleClass().add("day-header");
        header.setAlignment(Pos.CENTER);

        Label dayLabel = new Label(dayName);
        dayLabel.getStyleClass().add("day-header-name");

        Label dateLabel = new Label(String.valueOf(date.getDayOfMonth()));
        dateLabel.getStyleClass().add("day-header-number");

        // Marcar día actual
        if (date.equals(LocalDate.now())) {
            dateLabel.getStyleClass().add("day-header-today");
        }

        header.getChildren().addAll(dayLabel, dateLabel);
        return header;
    }

    private Label createHourLabel(int hour) {
        String timeText = formatHour(hour);
        Label hourLabel = new Label(timeText);
        hourLabel.getStyleClass().add("hour-label");
        return hourLabel;
    }

    private String formatHour(int hour) {
        if (hour == 0) return "12 AM";
        if (hour < 12) return hour + " AM";
        if (hour == 12) return "12 PM";
        return (hour - 12) + " PM";
    }

    private VBox createHourCell(LocalDate date, int hour) {
        VBox cell = new VBox();
        cell.getStyleClass().add("hour-cell");
        cell.setAlignment(Pos.TOP_LEFT);
        cell.setSpacing(2);

        // Agregar eventos para esta hora y fecha si los calendarios están habilitados
        List<Event> dayEvents = events.get(date);
        if (dayEvents != null) {
            for (Event event : dayEvents) {
                if (shouldShowEvent(event)) {
                    LocalTime eventTime = event.getStartDate().toLocalTime();
                    if (eventTime.getHour() == hour) {
                        Label eventLabel = createEventLabel(event);
                        cell.getChildren().add(eventLabel);
                    }
                }
            }
        }

        // Efectos hover
        cell.setOnMouseEntered(e -> cell.getStyleClass().add("hour-cell-hover"));
        cell.setOnMouseExited(e -> cell.getStyleClass().remove("hour-cell-hover"));

        // Click para crear evento
        cell.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) { // Doble click
                LocalDateTime clickDateTime = LocalDateTime.of(date, LocalTime.of(hour, 0));
                openEventDialogForCreate(clickDateTime.toLocalDate());
            }
        });

        return cell;
    }

    private boolean shouldShowEvent(Event event) {
        String calendarId = event.getCalendarId();
        switch (calendarId) {
            case "CAL0000001": // Mis Clases
                return userCalendarCheck != null && userCalendarCheck.isSelected();
            case "CAL0000002": // Tareas y Proyectos
                return tasksCalendarCheck != null && tasksCalendarCheck.isSelected();
            case "CAL0000003": // Personal
                return personalCalendarCheck != null && personalCalendarCheck.isSelected();
            case "CAL0000004": // Exámenes
                return examsCalendarCheck != null && examsCalendarCheck.isSelected();
            default:
                return true; // Mostrar otros eventos por defecto
        }
    }

    private Label createEventLabel(Event event) {
        Label eventLabel = new Label(event.getTitle());
        eventLabel.getStyleClass().add("event-label");
        eventLabel.setMaxWidth(Double.MAX_VALUE);
        eventLabel.setWrapText(true);

        // Color basado en el calendario
        String calendarId = event.getCalendarId();
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

        // Click para ver/editar evento
        eventLabel.setOnMouseClicked(e -> {
            if (e.getClickCount() == 1) {
                openEventDialogForRead(event.getStartDate().toLocalDate());
            }
        });

        return eventLabel;
    }

    private void loadEventsFromDatabase() {
        if (authService.getCurrentUser() != null) {
            String userId = authService.getCurrentUser().getUserId();

            try {
                // Cargar eventos para la semana
                List<Event> weekEvents = eventService.getEventsForWeek(userId, startOfWeek, startOfWeek.plusDays(6));
                events.clear();

                for (Event event : weekEvents) {
                    LocalDate eventDate = event.getStartDate().toLocalDate();
                    events.computeIfAbsent(eventDate, k -> new ArrayList<>()).add(event);
                }

                // Recrear la vista con los eventos cargados
                Platform.runLater(this::createWeekView);

                System.out.println("✓ Eventos cargados para la semana: " + weekEvents.size());

            } catch (Exception e) {
                System.err.println("✗ Error cargando eventos de BD: " + e.getMessage());
                e.printStackTrace();
                showAlert("Error", "No se pudieron cargar los eventos desde la base de datos", Alert.AlertType.WARNING);
            }
        } else {
            System.out.println("⚠ No hay usuario logueado");
        }
    }

    private void updateCalendarView() {
        if (monthYearLabel != null) {
            LocalDate endOfWeek = startOfWeek.plusDays(6);
            String weekRange = startOfWeek.format(DateTimeFormatter.ofPattern("d MMM")) +
                    " - " +
                    endOfWeek.format(DateTimeFormatter.ofPattern("d MMM yyyy"));
            monthYearLabel.setText(weekRange);
        }
    }

    // ========== NAVEGACIÓN ==========
    @FXML
    private void handleTodayClick() {
        System.out.println("🔄 Navegando a hoy...");
        selectedDate = LocalDate.now();
        startOfWeek = selectedDate.minusDays(selectedDate.getDayOfWeek().getValue() % 7);
        updateCalendarView();
        setupCalendarGrid();
        loadEventsFromDatabase();
    }

    @FXML
    private void handlePreviousWeek() {
        System.out.println("⬅ Semana anterior");
        startOfWeek = startOfWeek.minusWeeks(1);
        updateCalendarView();
        setupCalendarGrid();
        loadEventsFromDatabase();
    }

    @FXML
    private void handleNextWeek() {
        System.out.println("➡ Semana siguiente");
        startOfWeek = startOfWeek.plusWeeks(1);
        updateCalendarView();
        setupCalendarGrid();
        loadEventsFromDatabase();
    }

    // ========== GESTIÓN DE EVENTOS ==========
    @FXML
    private void handleCreateButton() {
        if (authService.getCurrentUser() != null) {
            openEventDialogForCreate(LocalDate.now());
        } else {
            showAlert("Error", "No hay usuario logueado", Alert.AlertType.ERROR);
        }
    }

    private void openEventDialogForCreate(LocalDate date) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/event-dialog.fxml"));
            Parent dialogRoot = loader.load();
            com.utez.calendario.controllers.EventDialogController dialogController = loader.getController();

            Runnable onEventChanged = this::loadEventsFromDatabase;
            dialogController.initializeForCreate(date, onEventChanged);

            Stage dialogStage = new Stage();
            dialogStage.setTitle("UTEZ Calendar - Crear Evento");
            dialogStage.initModality(Modality.WINDOW_MODAL);
            dialogStage.initOwner(createButton.getScene().getWindow());
            Scene dialogScene = new Scene(dialogRoot);

            try {
                dialogScene.getStylesheets().add(getClass().getResource("/css/dialog-styles.css").toExternalForm());
            } catch (Exception ignored) {}

            dialogStage.setScene(dialogScene);
            dialogStage.setResizable(true);
            dialogStage.setMinWidth(600);
            dialogStage.setMinHeight(500);
            dialogStage.showAndWait();

        } catch (IOException e) {
            e.printStackTrace();
            showAlert("Error", "No se pudo abrir el diálogo de eventos: " + e.getMessage(),
                    Alert.AlertType.ERROR);
        }
    }

    private void openEventDialogForRead(LocalDate date) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/event-dialog.fxml"));
            Parent dialogRoot = loader.load();
            com.utez.calendario.controllers.EventDialogController dialogController = loader.getController();

            Runnable onEventChanged = this::loadEventsFromDatabase;
            dialogController.initializeForRead(date, onEventChanged);

            Stage dialogStage = new Stage();
            dialogStage.setTitle("UTEZ Calendar - Ver Eventos");
            dialogStage.initModality(Modality.WINDOW_MODAL);
            dialogStage.initOwner(createButton.getScene().getWindow());
            Scene dialogScene = new Scene(dialogRoot);

            try {
                dialogScene.getStylesheets().add(getClass().getResource("/css/dialog-styles.css").toExternalForm());
            } catch (Exception ignored) {}

            dialogStage.setScene(dialogScene);
            dialogStage.setResizable(true);
            dialogStage.setMinWidth(600);
            dialogStage.setMinHeight(500);
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
        navigateToView("/fxml/calendar-day.fxml", "/css/styles-day.css", "Vista Día");
    }

    @FXML
    private void handleWeekView() {
        // Ya estamos en vista semanal
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
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}