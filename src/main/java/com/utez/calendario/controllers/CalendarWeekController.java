package com.utez.calendario.controllers;

import com.utez.calendario.models.Event;
import com.utez.calendario.models.User;
import com.utez.calendario.services.AuthService;
import com.utez.calendario.services.EventService;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.HPos;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URL;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

public class CalendarWeekController implements Initializable {

    @FXML private Label monthYearLabel;
    @FXML private GridPane calendarGrid;
    @FXML private ScrollPane calendarScrollPane;
    @FXML private Button createButton;

    private LocalDate startOfWeek;
    private LocalDate selectedDate;
    private Map<LocalDate, List<Event>> events;
    private int currentViewMode = 1; // Semana

    private EventService eventService;
    private AuthService authService;

    // Configuración de horas - AHORA 24 HORAS
    private static final int START_HOUR = 0;  // 12 AM (medianoche)
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

    private void setupScrollPane() {
        if (calendarScrollPane != null) {
            calendarScrollPane.setFitToWidth(true);
            calendarScrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
            calendarScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);

            // Scroll a las 8 AM al inicio (8/24 = 0.33)
            Platform.runLater(() -> {
                double scrollPosition = 8.0 / TOTAL_HOURS;
                calendarScrollPane.setVvalue(scrollPosition);
            });
        }
    }

    private void setupCalendarGrid() {
        if (calendarGrid == null) return;

        calendarGrid.getChildren().clear();
        calendarGrid.getColumnConstraints().clear();
        calendarGrid.getRowConstraints().clear();

        // Configurar columnas: 1 para horas + 7 para días
        ColumnConstraints hourColumn = new ColumnConstraints();
        hourColumn.setMinWidth(80);
        hourColumn.setPrefWidth(80);
        hourColumn.setMaxWidth(80);
        calendarGrid.getColumnConstraints().add(hourColumn);

        // Columnas para días de la semana
        for (int i = 0; i < 7; i++) {
            ColumnConstraints dayColumn = new ColumnConstraints();
            dayColumn.setPercentWidth(100.0 / 7.0);
            dayColumn.setHgrow(Priority.ALWAYS);
            calendarGrid.getColumnConstraints().add(dayColumn);
        }

        // Configurar filas: 1 para encabezados + TOTAL_HOURS para horas
        RowConstraints headerRow = new RowConstraints();
        headerRow.setMinHeight(50);
        headerRow.setPrefHeight(50);
        calendarGrid.getRowConstraints().add(headerRow);

        // Filas para cada hora (24 horas)
        for (int i = 0; i < TOTAL_HOURS; i++) {
            RowConstraints hourRow = new RowConstraints();
            hourRow.setMinHeight(60);
            hourRow.setPrefHeight(60);
            hourRow.setVgrow(Priority.NEVER);
            calendarGrid.getRowConstraints().add(hourRow);
        }

        createWeekView();
    }

    private void createWeekView() {
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

        // Agregar eventos para esta hora y fecha
        List<Event> dayEvents = events.get(date);
        if (dayEvents != null) {
            for (Event event : dayEvents) {
                LocalTime eventTime = event.getStartDate().toLocalTime();
                if (eventTime.getHour() == hour) {
                    Label eventLabel = createEventLabel(event);
                    cell.getChildren().add(eventLabel);
                }
            }
        }

        // Efectos hover
        cell.setOnMouseEntered(e -> cell.getStyleClass().add("hour-cell-hover"));
        cell.setOnMouseExited(e -> cell.getStyleClass().remove("hour-cell-hover"));

        return cell;
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
                loadSampleEvents();
            }
        } else {
            System.out.println("⚠ No hay usuario logueado, cargando eventos de ejemplo");
            loadSampleEvents();
        }
    }

    private void loadSampleEvents() {
        events.clear();

        // Crear eventos de ejemplo
        Event sampleEvent1 = new Event();
        sampleEvent1.setTitle("Reunión importante");
        sampleEvent1.setStartDate(LocalDateTime.of(LocalDate.now(), LocalTime.of(9, 0)));
        sampleEvent1.setCalendarId("CAL0000001");

        Event sampleEvent2 = new Event();
        sampleEvent2.setTitle("Entrega proyecto");
        sampleEvent2.setStartDate(LocalDateTime.of(LocalDate.now().plusDays(1), LocalTime.of(14, 0)));
        sampleEvent2.setCalendarId("CAL0000002");

        events.computeIfAbsent(LocalDate.now(), k -> new ArrayList<>()).add(sampleEvent1);
        events.computeIfAbsent(LocalDate.now().plusDays(1), k -> new ArrayList<>()).add(sampleEvent2);

        Platform.runLater(this::createWeekView);
        System.out.println("✓ Eventos de ejemplo cargados");
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

    // Navegación - MÉTODOS CORREGIDOS
    @FXML
    private void handleTodayClick() {
        System.out.println("🔄 Navegando a hoy...");
        selectedDate = LocalDate.now();
        startOfWeek = selectedDate.with(DayOfWeek.SUNDAY);
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

    @FXML
    private void handleCloseButton() {
        System.exit(0);
    }

    // Navegación entre vistas
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
            Scene scene = new Scene(root);
            scene.getStylesheets().add(getClass().getResource(cssPath).toExternalForm());

            stage.setScene(scene);
            stage.setTitle("UTEZ Calendar - " + title);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void handleCreateButton() {
        System.out.println("➕ Crear nuevo evento");
    }
}