package com.utez.calendario.controllers;

import com.utez.calendario.models.Event;
import com.utez.calendario.models.User;
import com.utez.calendario.services.AuthService;
import com.utez.calendario.services.EventService;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Screen;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URL;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class CalendarDayController implements Initializable {

    @FXML private Label monthYearLabel;
    @FXML private GridPane calendarGrid;
    @FXML private ScrollPane dayScrollPane;
    @FXML private Button createButton;
    @FXML private CheckBox userCalendarCheck;
    @FXML private CheckBox tasksCalendarCheck;
    @FXML private CheckBox personalCalendarCheck;
    @FXML private CheckBox examsCalendarCheck;
    @FXML private CheckBox holidaysCalendarCheck;
    @FXML private CheckBox utezCalendarCheck;

    private LocalDate currentDate;
    private List<Event> events;
    private int currentViewMode = 0; // Día

    private EventService eventService;
    private AuthService authService;

    // Configuración de horas - 24 HORAS
    private static final int START_HOUR = 0;  // 12 AM
    private static final int END_HOUR = 23;   // 11 PM
    private static final int TOTAL_HOURS = END_HOUR - START_HOUR + 1; // 24 horas

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
            loadEventsFromDatabase();
            setupScrollPane();
        });
    }

    private void initializeCalendar() {
        currentDate = LocalDate.now();
        events = new ArrayList<>();
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
        Platform.runLater(this::createDayView);
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
        // ASEGURAR QUE NO HAYA MARGINS
        GridPane.setMargin(cornerLabel, new javafx.geometry.Insets(0));
        calendarGrid.add(cornerLabel, 0, 0);

        // Encabezado del día
        VBox dayHeader = createDayHeader();
        GridPane.setMargin(dayHeader, new javafx.geometry.Insets(0));
        calendarGrid.add(dayHeader, 1, 0);

        // Etiquetas de horas y celdas
        for (int hour = 0; hour < TOTAL_HOURS; hour++) {
            int actualHour = START_HOUR + hour;

            // Etiqueta de hora
            Label hourLabel = createHourLabel(actualHour);
            GridPane.setMargin(hourLabel, new javafx.geometry.Insets(0));
            calendarGrid.add(hourLabel, 0, hour + 1);

            // Celda para esta hora
            VBox hourCell = createHourCell(actualHour);
            GridPane.setMargin(hourCell, new javafx.geometry.Insets(0));
            calendarGrid.add(hourCell, 1, hour + 1);
        }

        // Mostrar eventos con rowspan (un solo bloque por evento)
        for (Event event : events) {
            if (shouldShowEvent(event)) {
                LocalDateTime start = event.getStartDate();
                LocalDateTime end = event.getEndDate();

                if (!start.toLocalDate().equals(currentDate)) continue;

                int startHour = start.getHour();
                int endHour = end.getHour();

                int rowIndex = startHour + 1; // +1 por encabezado
                //int rowSpan = Math.max(1, endHour - startHour);
                int rowSpan = Math.max(1, endHour - startHour + 1);


                Label eventLabel = createEventLabel(event);
                eventLabel.setMinHeight(60 * rowSpan); // Ajustar altura visual (opcional)
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

    private VBox createDayHeader() {
        VBox header = new VBox();
        header.getStyleClass().add("day-header");
        header.setAlignment(Pos.CENTER);
        header.setSpacing(4);
        header.setPadding(new javafx.geometry.Insets(8, 15, 8, 15));

        // Nombre del día
        String dayName = currentDate.getDayOfWeek().getDisplayName(
                java.time.format.TextStyle.FULL, Locale.getDefault()).toUpperCase();
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

        /*
        // Agregar eventos para esta hora si los calendarios están habilitados
        if (events != null) {
            for (Event event : events) {
                if (shouldShowEvent(event)) {
                    LocalTime start = event.getStartDate().toLocalTime();
                    LocalTime end = event.getEndDate().toLocalTime();

                    // Si el evento ocurre (aunque sea parcialmente) dentro de esta hora
                    if (!start.isAfter(LocalTime.of(hour + 1, 0)) && !end.isBefore(LocalTime.of(hour, 0))) {
                        Label eventLabel = createEventLabel(event);
                        cell.getChildren().add(eventLabel);
                    }
                }
            }
        }
        */

        // Efectos hover
        cell.setOnMouseEntered(e -> cell.getStyleClass().add("hour-cell-hover"));
        cell.setOnMouseExited(e -> cell.getStyleClass().remove("hour-cell-hover"));

        // Click para crear evento
        cell.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) { // Doble click
                LocalDateTime clickDateTime = LocalDateTime.of(currentDate, LocalTime.of(hour, 0));
                openEventDialogForCreate(clickDateTime.toLocalDate());
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
        // Formatear hora de inicio y fin
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");
        String startTime = event.getStartDate().toLocalTime().format(timeFormatter);
        String endTime = event.getEndDate().toLocalTime().format(timeFormatter);

        String timeRange = "[" + startTime + " - " + endTime + "]";
        String labelText = timeRange + "\n" + event.getTitle();

        Label eventLabel = new Label(labelText);
        eventLabel.getStyleClass().add("event-label");
        eventLabel.setMaxWidth(Double.MAX_VALUE);
        eventLabel.setWrapText(true);

        // Colores por tipo de calendario
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

        // Click para ver/editar
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
                // Cargar eventos para el día actual
                List<Event> dayEvents = eventService.getEventsForDay(userId, currentDate);
                events = dayEvents != null ? dayEvents : new ArrayList<>();

                // Recrear la vista con los eventos cargados
                Platform.runLater(this::createDayView);

                System.out.println("✓ Eventos cargados para el día: " + events.size());

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
            String dateText = currentDate.format(DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM 'de' yyyy"));
            monthYearLabel.setText(dateText.toUpperCase());
        }
    }

    // ========== NAVEGACIÓN ==========
    @FXML
    private void handleTodayClick() {
        System.out.println("🔄 Navegando a hoy...");
        currentDate = LocalDate.now();
        updateCalendarView();
        setupCalendarGrid();
        loadEventsFromDatabase();
    }

    @FXML
    private void handlePreviousDay() {
        System.out.println("⬅ Día anterior");
        currentDate = currentDate.minusDays(1);
        updateCalendarView();
        setupCalendarGrid();
        loadEventsFromDatabase();
    }

    @FXML
    private void handleNextDay() {
        System.out.println("➡ Día siguiente");
        currentDate = currentDate.plusDays(1);
        updateCalendarView();
        setupCalendarGrid();
        loadEventsFromDatabase();
    }

    // ========== GESTIÓN DE EVENTOS ==========
    @FXML
    private void handleCreateButton() {
        if (authService.getCurrentUser() != null) {
            openEventDialogForCreate(currentDate);
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
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }


    /// ///CERRAR SESION

    @FXML private Label statusLabel;

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
    private  void handleLogout(){
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