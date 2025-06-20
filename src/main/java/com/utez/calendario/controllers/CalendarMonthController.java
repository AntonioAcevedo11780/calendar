package com.utez.calendario.controllers;

import com.utez.calendario.models.Event;
import com.utez.calendario.models.User;
import com.utez.calendario.services.AuthService;
import com.utez.calendario.services.EventService;
import javafx.animation.FadeTransition;
import javafx.animation.ScaleTransition;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.HPos;
import javafx.geometry.VPos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.IOException;
import java.net.URL;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.*;

/**
 * Controlador para vista principal: Mensual
 */

public class CalendarMonthController implements Initializable {

    @FXML private Label monthYearLabel;
    @FXML private GridPane calendarGrid;
    @FXML private Label statusLabel;
    @FXML private Button createButton;
    @FXML private Label todayLabel;

    private YearMonth currentYearMonth;
    private LocalDate selectedDate;
    private Map<LocalDate, List<String>> events;
    private List<String> viewModes = Arrays.asList("Día", "Semana", "Mes", "Año");
    private int currentViewMode = 2; // Mes por defecto

    private EventService eventService;
    private AuthService authService;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        System.out.println("\n=== INICIANDO CALENDARIO ITHERA ===");
        System.out.println("Fecha/Hora: 2025-06-14 04:34:28");
        System.out.println("Usuario Sistema: AntonioAcevedo11780");

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
        updateStatusBar();

        Platform.runLater(() -> {
            setupCalendarGridConstraints();
            loadEventsFromDatabase();
        });

        System.out.println("Calendario inicializado correctamente");
        System.out.println("=================================\n");
    }

    private void initializeCalendar() {
        currentYearMonth = YearMonth.now();
        selectedDate = LocalDate.now();
        events = new HashMap<>();
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

    private void loadEventsFromDatabase() {
        if (authService.getCurrentUser() != null) {
            String userId = authService.getCurrentUser().getUserId();

            System.out.println("\nCargando eventos desde BD...");
            System.out.println("Usuario ID: " + userId);
            System.out.println("Mes actual: " + currentYearMonth.getMonth() + " " + currentYearMonth.getYear());

            try {
                List<Event> monthEvents = eventService.getEventsForMonth(userId, currentYearMonth.atDay(1));
                events.clear();
                for (Event event : monthEvents) {
                    LocalDate eventDate = event.getStartDate().toLocalDate();
                    events.computeIfAbsent(eventDate, k -> new ArrayList<>()).add(event.getTitle());
                }
                updateCalendarView();
            } catch (Exception e) {
                System.err.println("Error cargando eventos: " + e.getMessage());
                e.printStackTrace();
                loadSampleEvents();
            }
        } else {
            loadSampleEvents();
        }
    }

    private void loadSampleEvents() { //Por si no jala la bd que ponga los eventos igual
        events.clear();
        LocalDate today = LocalDate.now();
        YearMonth currentMonth = YearMonth.from(today);
        events.put(currentMonth.atDay(14), Arrays.asList("Clase Virtual", "Revisar Proyecto"));
        events.put(currentMonth.atDay(15), Arrays.asList("Programación Web"));
        events.put(currentMonth.atDay(16), Arrays.asList("Base de Datos", "Metodologías"));
        events.put(currentMonth.atDay(18), Arrays.asList("Tarea BD", "Grupo Estudio"));
        events.put(currentMonth.atDay(19), Arrays.asList("Examen Parcial BD"));
        events.put(currentMonth.atDay(20), Arrays.asList("Entrega Proyecto Web"));
        events.put(today, Arrays.asList("Evento actual"));
        updateCalendarView();
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
        String monthName = currentYearMonth.getMonth().getDisplayName(TextStyle.FULL, new Locale("es", "ES"));
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

        for (int i = 0; i < 7; i++) {
            ColumnConstraints colConstraints = new ColumnConstraints();
            colConstraints.setPercentWidth(100.0 / 7);
            colConstraints.setHalignment(HPos.LEFT);
            colConstraints.setHgrow(Priority.ALWAYS);
            colConstraints.setFillWidth(true);
            calendarGrid.getColumnConstraints().add(colConstraints);
        }
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

        for (int row = 0; row < 6; row++) {
            for (int col = 0; col < 7; col++) {
                LocalDate cellDate = startDate.plusDays(row * 7 + col);
                VBox cellContainer = createCalendarCell(cellDate, row == 0 ? dayNames[col] : null);
                calendarGrid.add(cellContainer, col, row);
            }
        }
    }

    private VBox createCalendarCell(LocalDate date, String dayHeader) {
        VBox cell = new VBox();
        cell.getStyleClass().add("calendar-cell");
        cell.setMaxWidth(Double.MAX_VALUE);
        cell.setMaxHeight(Double.MAX_VALUE);

        boolean isCurrentMonth = date.getMonth() == currentYearMonth.getMonth() && date.getYear() == currentYearMonth.getYear();
        boolean isToday = date.equals(LocalDate.now());
        boolean isSelected = date.equals(selectedDate);

        if (!isCurrentMonth) cell.getStyleClass().add("calendar-cell-other-month");
        if (isToday) cell.getStyleClass().add("calendar-cell-today");
        if (isSelected) cell.getStyleClass().add("calendar-cell-selected");

        if (dayHeader != null) {
            Label headerLabel = new Label(dayHeader);
            headerLabel.getStyleClass().add("day-header-integrated");
            cell.getChildren().add(headerLabel);
        }

        Label dayNumber = new Label(String.valueOf(date.getDayOfMonth()));
        dayNumber.getStyleClass().add("day-number");
        if (isToday) dayNumber.getStyleClass().add("day-number-today");
        cell.getChildren().add(dayNumber);

        if (events.containsKey(date)) {
            List<String> dateEvents = events.get(date);
            int maxEventsToShow = 3;
            for (int i = 0; i < Math.min(dateEvents.size(), maxEventsToShow); i++) {
                Label eventLabel = new Label(dateEvents.get(i));
                eventLabel.getStyleClass().add("event-item");
                switch (i % 4) {
                    case 0: eventLabel.getStyleClass().add("event-item-blue"); break;
                    case 1: eventLabel.getStyleClass().add("event-item-green"); break;
                    case 2: eventLabel.getStyleClass().add("event-item-purple"); break;
                    case 3: eventLabel.getStyleClass().add("event-item-orange"); break;
                }
                cell.getChildren().add(eventLabel);
            }
            if (dateEvents.size() > maxEventsToShow) {
                Label moreLabel = new Label("+" + (dateEvents.size() - maxEventsToShow) + " más");
                moreLabel.getStyleClass().add("more-events-label");
                cell.getChildren().add(moreLabel);
            }
        }

        cell.setOnMouseClicked(e -> handleDateClick(date));
        cell.setOnMouseEntered(e -> cell.getStyleClass().add("calendar-cell-hover"));
        cell.setOnMouseExited(e -> cell.getStyleClass().remove("calendar-cell-hover"));

        return cell;
    }

    private void updateStatusBar() {
        if (statusLabel != null) {
            String userInfo = "";
            if (authService.getCurrentUser() != null) {
                User user = authService.getCurrentUser();
                userInfo = " | " + user.getDisplayId() + " (" + user.getRole().getDisplayName() + ")";
            }
            String status = String.format("Vista: %s | Mes: %s %d | Eventos: %d%s",
                    viewModes.get(currentViewMode),
                    currentYearMonth.getMonth().getDisplayName(TextStyle.FULL, new Locale("es", "ES")),
                    currentYearMonth.getYear(),
                    events.size(),
                    userInfo);
            statusLabel.setText(status);
        }
    }

    @FXML
    private void handleTodayClick() {
        currentYearMonth = YearMonth.now();
        selectedDate = LocalDate.now();
        loadEventsFromDatabase();
    }

    @FXML
    private void handlePreviousMonth() {
        currentYearMonth = currentYearMonth.minusMonths(1);
        loadEventsFromDatabase();
    }

    @FXML
    private void handleNextMonth() {
        currentYearMonth = currentYearMonth.plusMonths(1);
        loadEventsFromDatabase();
    }

    @FXML
    private void handleCreateButton() {
        if (authService.getCurrentUser() != null) {
            User user = authService.getCurrentUser();
            openEventDialog("CREATE", selectedDate);
        } else {
            showAlert("Error", "No hay usuario logueado", javafx.scene.control.Alert.AlertType.ERROR);
        }
    }

    private void openEventDialog(String mode, LocalDate date) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/event-dialog.fxml"));
            Parent dialogRoot = loader.load();
            com.utez.calendario.controllers.EventDialogController dialogController = loader.getController();

            Runnable onEventChanged = () -> loadEventsFromDatabase();

            if ("CREATE".equals(mode)) {
                dialogController.initializeForCreate(date, onEventChanged);
            } else {
                dialogController.initializeForRead(date, onEventChanged);
            }

            Stage dialogStage = new Stage();
            dialogStage.setTitle("UTEZ Calendar - Gestión de Eventos");
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
            System.err.println("Error abriendo diálogo: " + e.getMessage());
            e.printStackTrace();
            showAlert("Error", "No se pudo abrir el diálogo de eventos: " + e.getMessage(),
                    javafx.scene.control.Alert.AlertType.ERROR);
        }
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
        currentViewMode = 0;
        updateViewModeUI();
    }

    @FXML
    private void handleWeekView() {
        currentViewMode = 1;
        updateViewModeUI();
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
    }

    private void updateViewModeUI() {
        updateStatusBar();
    }

    private void handleDateClick(LocalDate date) {
        selectedDate = date;
        if (date.getMonth() != currentYearMonth.getMonth() || date.getYear() != currentYearMonth.getYear()) {
            currentYearMonth = YearMonth.from(date);
            loadEventsFromDatabase();
        } else {
            updateCalendarView();
        }
        if (events.containsKey(date)) {
            openEventDialog("READ", date);
        } else {
            openEventDialog("CREATE", date);
        }
    }

    public void addEvent(LocalDate date, String eventName) {
        events.computeIfAbsent(date, k -> new ArrayList<>()).add(eventName);
        updateCalendarView();
    }

    public void removeEvent(LocalDate date, String eventName) {
        if (events.containsKey(date)) {
            events.get(date).remove(eventName);
            if (events.get(date).isEmpty()) {
                events.remove(date);
            }
            updateCalendarView();
        }
    }

    public List<String> getEventsForDate(LocalDate date) {
        return events.getOrDefault(date, new ArrayList<>());
    }

    public void navigateToDate(LocalDate date) {
        currentYearMonth = YearMonth.from(date);
        selectedDate = date;
        loadEventsFromDatabase();
    }

    private void showAlert(String title, String message, javafx.scene.control.Alert.AlertType type) {
        javafx.scene.control.Alert alert = new javafx.scene.control.Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}