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
import javafx.stage.StageStyle;

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
            if (day == 0) {
                dayHeader.getStyleClass().add("day-header-first");
            } else {
                dayHeader.getStyleClass().add("day-header");
            }

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
                if (day == 0) {
                    hourCell.getStyleClass().add("hour-cell-first");
                } else {
                    hourCell.getStyleClass().add("hour-cell");
                }

                calendarGrid.add(hourCell, day + 1, hour + 1);
            }
        }

        renderEventsOverlay();

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

        // Hover
        cell.setOnMouseEntered(e -> cell.getStyleClass().add("hour-cell-hover"));
        cell.setOnMouseExited(e -> cell.getStyleClass().remove("hour-cell-hover"));

        //Click para crear evento con hora específica
        cell.setOnMouseClicked(e -> {
            if (e.getClickCount() == 1) {
                LocalTime startTime = LocalTime.of(hour, 0);
                LocalTime endTime = LocalTime.of(hour + 1, 0);
                openEventDialogWithTime(date, startTime, endTime);
            }
        });

        return cell;
    }

    private void renderEventsOverlay() {
        eventOverlay.getChildren().clear();

        double rowHeight = 60; // altura por hora (asegúrate que coincida con tu CSS)
        double colWidth = (calendarGrid.getWidth() - 60) / 7; // 60 px = columna de horas

        for (int day = 0; day < 7; day++) {
            LocalDate date = startOfWeek.plusDays(day);
            List<Event> dayEvents = events.get(date);
            if (dayEvents == null) continue;

            for (Event event : dayEvents) {
                if (!shouldShowEvent(event)) continue;

                LocalTime start = event.getStartDate().toLocalTime();
                LocalTime end = event.getEndDate().toLocalTime();

                // convertir hora a píxeles
                double startY = start.getHour() * rowHeight +
                        (start.getMinute() / 60.0) * rowHeight;
                double endY = end.getHour() * rowHeight +
                        (end.getMinute() / 60.0) * rowHeight;

                double height = Math.max(rowHeight * 0.5, endY - startY);
                double x = 60 + day * colWidth; // 60 = ancho de columna de horas

                // Crear un contenedor para el bloque de evento
                VBox eventContainer = new VBox();
                eventContainer.setLayoutX(x);
                double headerHeight = 60; // coincide con .day-header
                eventContainer.setLayoutY(startY + headerHeight);
                eventContainer.setPrefWidth(colWidth);
                eventContainer.setPrefHeight(height);
                eventContainer.getStyleClass().addAll("event-container");

                Label eventBlock = new Label(event.getTitle() + " (" +
                        start.format(DateTimeFormatter.ofPattern("HH:mm")) + " - " +
                        end.format(DateTimeFormatter.ofPattern("HH:mm")) + ")");
                eventBlock.getStyleClass().addAll("event-label", "event-block");
                eventBlock.setPrefWidth(colWidth);
                eventBlock.setPrefHeight(height);

                //Añadir evento de clic para abrir el evento específico
                final Event currentEvent = event;
                eventContainer.setOnMouseClicked(e -> {
                    openSpecificEvent(currentEvent);
                });

                eventContainer.getChildren().add(eventBlock);
                eventOverlay.getChildren().add(eventContainer);
            }
        }
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

        //Click para abrir evento específico
        final Event currentEvent = event;
        eventLabel.setOnMouseClicked(e -> {
            openSpecificEvent(currentEvent);
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
            monthYearLabel.setText(weekRange.toUpperCase());
        }
    }

    // ========== NAVEGACIÓN ==========
    @FXML private StackPane weekContainer;
    @FXML private Pane eventOverlay;

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

    //  Abrir diálogo con tiempo específico
    private void openEventDialogWithTime(LocalDate date, LocalTime startTime, LocalTime endTime) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/event-dialog.fxml"));
            Parent dialogRoot = loader.load();
            EventDialogController dialogController = loader.getController();

            Runnable onEventChanged = this::loadEventsFromDatabase;

            // Usar el método específico para crear con tiempo
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

    // Abrir evento específico
    private void openSpecificEvent(Event event) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/event-dialog.fxml"));
            Parent dialogRoot = loader.load();
            EventDialogController dialogController = loader.getController();

            Runnable onEventChanged = this::loadEventsFromDatabase;

            // Usar el método para visualizar evento específico
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

    private void openEventDialogForCreate(LocalDate date) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/event-dialog.fxml"));
            Parent dialogRoot = loader.load();
            EventDialogController dialogController = loader.getController();

            Runnable onEventChanged = this::loadEventsFromDatabase;
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

    private void openEventDialogForRead(LocalDate date) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/event-dialog.fxml"));
            Parent dialogRoot = loader.load();
            EventDialogController dialogController = loader.getController();

            Runnable onEventChanged = this::loadEventsFromDatabase;
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