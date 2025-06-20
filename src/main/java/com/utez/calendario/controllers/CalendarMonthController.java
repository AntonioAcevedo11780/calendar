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
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.IOException;
import java.net.URL;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.*;


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
        System.out.println("Fecha/Hora UTC: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
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

    /**
     * Carga eventos desde la base de datos
     */
    private void loadEventsFromDatabase() {
        if (authService.getCurrentUser() != null) {
            String userId = authService.getCurrentUser().getUserId();

            System.out.println("\n[" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "] " +
                    "Cargando eventos desde BD...");
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
                System.out.println("[" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "] " +
                        "Eventos cargados correctamente desde BD");

            } catch (Exception e) {
                System.err.println("[" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "] " +
                        "Error cargando eventos: " + e.getMessage());
                e.printStackTrace();
                // NO cargar eventos de muestra - solo mostrar error
                showAlert("Error de Conexión",
                        "No se pueden cargar los eventos desde la base de datos.\nVerifica tu conexión y configuración.",
                        Alert.AlertType.WARNING);
            }
        } else {
            System.out.println(" [" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "] " +
                    "No hay usuario logueado");
            events.clear();
            updateCalendarView();
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

        // Eventos del día
        if (events.containsKey(date)) {
            List<String> dateEvents = events.get(date);
            int maxEventsToShow = 3;
            for (int i = 0; i < Math.min(dateEvents.size(), maxEventsToShow); i++) {
                Label eventLabel = new Label(dateEvents.get(i));
                eventLabel.getStyleClass().add("event-item");
                // Colores rotativos
                switch (i % 4) {
                    case 0: eventLabel.getStyleClass().add("event-item-blue"); break;
                    case 1: eventLabel.getStyleClass().add("event-item-green"); break;
                    case 2: eventLabel.getStyleClass().add("event-item-purple"); break;
                    case 3: eventLabel.getStyleClass().add("event-item-orange"); break;
                }
                cell.getChildren().add(eventLabel);
            }

            // Indicador de "más eventos"
            if (dateEvents.size() > maxEventsToShow) {
                Label moreLabel = new Label("+" + (dateEvents.size() - maxEventsToShow) + " más");
                moreLabel.getStyleClass().add("more-events-label");
                cell.getChildren().add(moreLabel);
            }
        }

        // Event handlers
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

            // Usar Locale.of() en lugar del constructor deprecado
            Locale spanishLocale = Locale.of("es", "ES");
            String status = String.format("Vista: %s | Mes: %s %d | Eventos: %d%s",
                    viewModes.get(currentViewMode),
                    currentYearMonth.getMonth().getDisplayName(TextStyle.FULL, spanishLocale),
                    currentYearMonth.getYear(),
                    events.size(),
                    userInfo);
            statusLabel.setText(status);
        }
    }

    // ========== EVENT HANDLERS ==========

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
            openEventDialog("CREATE", selectedDate != null ? selectedDate : LocalDate.now());
        } else {
            showAlert("Error", "No hay usuario logueado", Alert.AlertType.ERROR);
        }
    }

    private void openEventDialog(String mode, LocalDate date) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/event-dialog.fxml"));
            Parent dialogRoot = loader.load();
            com.utez.calendario.controllers.EventDialogController dialogController = loader.getController();

            Runnable onEventChanged = () -> {
                System.out.println("✓ [" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "] " +
                        "Recargando eventos tras cambio");
                loadEventsFromDatabase();
            };

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

            // Cargar estilos CSS
            try {
                dialogScene.getStylesheets().add(getClass().getResource("/css/dialog-styles.css").toExternalForm());
            } catch (Exception ignored) {
                System.out.println(" No se pudo cargar CSS para el diálogo");
            }

            dialogStage.setScene(dialogScene);
            dialogStage.setResizable(true);
            dialogStage.setMinWidth(600);
            dialogStage.setMinHeight(500);
            dialogStage.showAndWait();

        } catch (IOException e) {
            System.err.println("✗ [" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "] " +
                    "Error abriendo diálogo: " + e.getMessage());
            e.printStackTrace();
            showAlert("Error", "No se pudo abrir el diálogo de eventos:\n" + e.getMessage(),
                    Alert.AlertType.ERROR);
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

        // Abrir diálogo según si hay eventos o no
        if (events.containsKey(date)) {
            openEventDialog("READ", date);
        } else {
            openEventDialog("CREATE", date);
        }
    }

    // ========== MÉTODOS PÚBLICOS ==========

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

    private void showAlert(String title, String message, Alert.AlertType type) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}