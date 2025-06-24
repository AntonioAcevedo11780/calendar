//package com.utez.calendario.controllers;
//
//import com.utez.calendario.models.Event;
//import com.utez.calendario.models.User;
//import com.utez.calendario.services.AuthService;
//import com.utez.calendario.services.EventService;
//import javafx.animation.FadeTransition;
//import javafx.animation.ScaleTransition;
//import javafx.application.Platform;
//import javafx.fxml.FXML;
//import javafx.fxml.FXMLLoader;
//import javafx.fxml.Initializable;
//import javafx.geometry.HPos;
//import javafx.geometry.Pos;
//import javafx.geometry.VPos;
//import javafx.scene.Parent;
//import javafx.scene.Scene;
//import javafx.scene.control.Button;
//import javafx.scene.control.Label;
//import javafx.scene.layout.*;
//import javafx.stage.Modality;
//import javafx.stage.Stage;
//import javafx.util.Duration;
//
//import java.io.IOException;
//import java.net.URL;
//import java.time.*;
//import java.time.format.DateTimeFormatter;
//import java.time.format.TextStyle;
//import java.time.temporal.TemporalAdjusters;
//import java.util.*;
//
///**
// * Controlador para vista semanal
// */
//
//public class CalendarWeekController implements Initializable {
//
//    @FXML private Label monthYearLabel;  // Aquí pondremos rango de la semana
//    @FXML private GridPane calendarGrid;
//    @FXML private Label statusLabel;
//    @FXML private Button createButton;
//    @FXML private Label todayLabel;
//
//    private LocalDate startOfWeek;  // Fecha de inicio de la semana (domingo)
//    private LocalDate selectedDate;
//    private Map<LocalDate, List<String>> events;
//    private List<String> viewModes = Arrays.asList("Día", "Semana", "Mes", "Año");
//    private int currentViewMode = 2; // Semana por defecto
//
//    private EventService eventService;
//    private AuthService authService;
//
//    @Override
//    public void initialize(URL location, ResourceBundle resources) {
//        System.out.println("\n=== INICIANDO CALENDARIO ITHERA ===");
//        System.out.println("Fecha/Hora: 2025-06-14 04:34:28");
//        System.out.println("Usuario Sistema: AlbertoPF");
//
//        eventService = EventService.getInstance();
//        authService = AuthService.getInstance();
//
//        if (authService.getCurrentUser() != null) {
//            User currentUser = authService.getCurrentUser();
//            System.out.println("Usuario logueado: " + currentUser.getDisplayInfo());
//        } else {
//            System.out.println("No hay usuario logueado");
//        }
//
//        initializeCalendar();
//        setupAnimations();
//        updateStatusBar();
//
//        Platform.runLater(() -> {
//            setupCalendarGridConstraints();
//           // loadEventsFromDatabase();
//        });
//    }
//
//    private void initializeCalendar() {
//        selectedDate = LocalDate.now();
//        startOfWeek = selectedDate.with(DayOfWeek.SUNDAY);
//        events = new HashMap<>();
//        updateCalendarView();
//    }
//
//    private void setupCalendarGridConstraints() {
//        if (calendarGrid != null) {
//            VBox.setVgrow(calendarGrid, Priority.ALWAYS);
//            HBox.setHgrow(calendarGrid, Priority.ALWAYS);
//            calendarGrid.setMaxWidth(Double.MAX_VALUE);
//            calendarGrid.setMaxHeight(Double.MAX_VALUE);
//        }
//    }
//
////    private void loadEventsFromDatabase() {
////    if (authService.getCurrentUser() != null) {
////            String userId = authService.getCurrentUser().getUserId();
////
////            try {
////                // Cargar eventos para la semana (filtrar desde base según fechas)
////                List<Event> weekEvents = eventService.getEventsForWeek(userId, startOfWeek, startOfWeek.plusDays(6));
////                events.clear();
////                for (Event event : weekEvents) {
////                    LocalDate eventDate = event.getStartDate().toLocalDate();
////                    events.computeIfAbsent(eventDate, k -> new ArrayList<>()).add(event.getTitle());
////                }
////                updateCalendarView();
////            } catch (Exception e) {
////                e.printStackTrace();
////                loadSampleEvents();
////            }
////        } else {
////            loadSampleEvents();
////        }
////    }
////
////    private void loadSampleEvents() {
////        events.clear();
////        events.put(LocalDate.now(), Arrays.asList("Evento Actual"));
////        updateCalendarView();
////    }
//
//    private void setupAnimations() {
//        if (createButton != null) {
//            createButton.setOnMouseEntered(e -> animateButtonHover(createButton, true));
//            createButton.setOnMouseExited(e -> animateButtonHover(createButton, false));
//        }
//    }
//
//    private void animateButtonHover(Button button, boolean hover) {
//        ScaleTransition scaleTransition = new ScaleTransition(Duration.millis(200), button);
//        scaleTransition.setToX(hover ? 1.05 : 1.0);
//        scaleTransition.setToY(hover ? 1.05 : 1.0);
//        scaleTransition.play();
//    }
//
//    private void updateCalendarView() {
//        updateWeekLabel();
//        populateCalendarGrid();
//
//        updateStatusBar();
//        if (calendarGrid != null) {
//            FadeTransition fadeTransition = new FadeTransition(Duration.millis(300), calendarGrid);
//            fadeTransition.setFromValue(0.8);
//            fadeTransition.setToValue(1.0);
//            fadeTransition.play();
//        }
//    }
//
//    private void updateWeekLabel() {
//        // Mostrar rango de la semana (Ej: 15 JUN - 21 JUN 2025)
//        LocalDate endOfWeek = startOfWeek.plusDays(6);
//        String startStr = startOfWeek.getDayOfMonth() + " " + startOfWeek.getMonth().getDisplayName(TextStyle.SHORT, new Locale("es", "ES")).toUpperCase();
//        String endStr = endOfWeek.getDayOfMonth() + " " + endOfWeek.getMonth().getDisplayName(TextStyle.SHORT, new Locale("es", "ES")).toUpperCase();
//        String yearStr = String.valueOf(startOfWeek.getYear());
//
//        if (monthYearLabel != null) {
//            monthYearLabel.setText(startStr + " - " + endStr + " " + yearStr);
//        }
//    }
//
//    private void populateCalendarGrid() {
//        if (calendarGrid == null) return;
//
//        calendarGrid.getChildren().clear();
//        calendarGrid.getColumnConstraints().clear();
//        calendarGrid.getRowConstraints().clear();
//
//        // 8 columnas: 0 = horas, 1-7 = días
//        for (int i = 0; i < 8; i++) {
//            ColumnConstraints col = new ColumnConstraints();
//            if (i == 0) {
//                col.setPrefWidth(50);  // ancho fijo para la columna de horas
//                col.setHalignment(HPos.RIGHT);
//            } else {
//                col.setPercentWidth(100.0 / 7);
//                col.setHalignment(HPos.CENTER);
//                col.setHgrow(Priority.ALWAYS);
//                col.setFillWidth(true);
//            }
//            calendarGrid.getColumnConstraints().add(col);
//        }
//
//        // 25 filas: 0 para encabezados de días, 1-24 para horas
//        for (int i = 0; i <= 24; i++) {
//            RowConstraints row = new RowConstraints();
//            row.setPrefHeight(100);  // altura preferida
//            row.setVgrow(Priority.NEVER);
//            calendarGrid.getRowConstraints().add(row);
//        }
//
//        // Etiquetas de horas en columna 0, filas 1 a 24
//        for (int hour = 0; hour < 24; hour++) {
//            String hourLabelStr = String.format("%02d:00", hour);
//            Label hourLabel = new Label(hourLabelStr);
//            hourLabel.getStyleClass().add("hour-label");
//            GridPane.setHalignment(hourLabel, HPos.RIGHT);
//            GridPane.setValignment(hourLabel, VPos.TOP);
//            calendarGrid.add(hourLabel, 0, hour + 1);
//        }
//
//        // Nombres de días y números en fila 0, columnas 1 a 7
//        String[] dayNames = {"DOM", "LUN", "MAR", "MIÉ", "JUE", "VIE", "SÁB"};
//        LocalDate date = startOfWeek;
//        for (int dayCol = 0; dayCol < 7; dayCol++) {
//            VBox dayHeader = new VBox();
//
//            dayHeader.setAlignment(Pos.CENTER);
//            dayHeader.setSpacing(2);
//
//            dayHeader.getStyleClass().add("day-header");
//            Label dayNameLabel = new Label(dayNames[dayCol]);
//            dayNameLabel.getStyleClass().add("day-header-integrated");
//
//            Label dayNumberLabel = new Label(String.valueOf(date.getDayOfMonth()));
//            dayNumberLabel.getStyleClass().add("day-number");
//            if (date.equals(LocalDate.now())) {
//                dayNumberLabel.getStyleClass().add("day-number-today");
//            }
//
//            dayHeader.getChildren().addAll(dayNameLabel, dayNumberLabel);
//            calendarGrid.add(dayHeader, dayCol + 1, 0);
//
//            date = date.plusDays(1);
//        }
//
//        // Celdas vacías para horas x días (filas 1 a 24, columnas 1 a 7)
//        for (int row = 1; row <= 24; row++) {
//            for (int col = 1; col <= 7; col++) {
//                Region cell = new Region();
//                cell.getStyleClass().add("calendar-hour-cell");
//                cell.setMinSize(10, 60);
//                cell.setPrefSize(Region.USE_COMPUTED_SIZE, 60);
//                calendarGrid.add(cell, col, row);
//            }
//        }
//    }
//
//
//
//    private VBox createCalendarCell(LocalDate date, String dayHeader) {
//        VBox cell = new VBox();
//        cell.getStyleClass().add("calendar-cell");
//        cell.setMaxWidth(Double.MAX_VALUE);
//        cell.setMaxHeight(Double.MAX_VALUE);
//
//        boolean isToday = date.equals(LocalDate.now());
//        boolean isSelected = date.equals(selectedDate);
//
//        if (isToday) cell.getStyleClass().add("calendar-cell-today");
//        if (isSelected) cell.getStyleClass().add("calendar-cell-selected");
//
//        if (dayHeader != null) {
//            Label headerLabel = new Label(dayHeader);
//            headerLabel.getStyleClass().add("day-header-integrated");
//            cell.getChildren().add(headerLabel);
//        }
//
//        Label dayNumber = new Label(String.valueOf(date.getDayOfMonth()));
//        dayNumber.getStyleClass().add("day-number");
//        if (isToday) dayNumber.getStyleClass().add("day-number-today");
//        cell.getChildren().add(dayNumber);
//
//        if (events.containsKey(date)) {
//            List<String> dateEvents = events.get(date);
//            int maxEventsToShow = 3;
//            for (int i = 0; i < Math.min(dateEvents.size(), maxEventsToShow); i++) {
//                Label eventLabel = new Label(dateEvents.get(i));
//                eventLabel.getStyleClass().add("event-item");
//                switch (i % 4) {
//                    case 0 -> eventLabel.getStyleClass().add("event-item-blue");
//                    case 1 -> eventLabel.getStyleClass().add("event-item-green");
//                    case 2 -> eventLabel.getStyleClass().add("event-item-purple");
//                    case 3 -> eventLabel.getStyleClass().add("event-item-orange");
//                }
//                cell.getChildren().add(eventLabel);
//            }
//            if (dateEvents.size() > maxEventsToShow) {
//                Label moreLabel = new Label("+" + (dateEvents.size() - maxEventsToShow) + " más");
//                moreLabel.getStyleClass().add("more-events-label");
//                cell.getChildren().add(moreLabel);
//            }
//        }
//
//        cell.setOnMouseClicked(e -> handleDateClick(date));
//        cell.setOnMouseEntered(e -> cell.getStyleClass().add("calendar-cell-hover"));
//        cell.setOnMouseExited(e -> cell.getStyleClass().remove("calendar-cell-hover"));
//
//        return cell;
//    }
//
//    private void updateStatusBar() {
//        if (statusLabel != null) {
//            String userInfo = "";
//            if (authService.getCurrentUser() != null) {
//                User user = authService.getCurrentUser();
//                userInfo = " | " + user.getDisplayId() + " (" + user.getRole().getDisplayName() + ")";
//            }
//            String status = String.format("Vista: %s | Semana: %s - %s | Eventos: %d%s",
//                    viewModes.get(currentViewMode),
//                    startOfWeek.format(DateTimeFormatter.ofPattern("dd/MM")),
//                    startOfWeek.plusDays(6).format(DateTimeFormatter.ofPattern("dd/MM")),
//                    events.size(),
//                    userInfo);
//            statusLabel.setText(status);
//        }
//    }
//
//    @FXML
//    public void handleTodayClick() {
//        selectedDate = LocalDate.now();
//        startOfWeek = selectedDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY));
//        //loadEventsFromDatabase();
//    }
//
//    @FXML
//    private void handlePreviousMonth() {
//        // En vista semanal, retroceder una semana
//        startOfWeek = startOfWeek.minusWeeks(1);
//       // loadEventsFromDatabase();
//    }
//
//    @FXML
//    private void handleNextMonth() {
//        // En vista semanal, avanzar una semana
//        startOfWeek = startOfWeek.plusWeeks(1);
//        //loadEventsFromDatabase();
//    }
//
//    @FXML
//    private void handleCreateButton() {
//        if (authService.getCurrentUser() != null) {
//            openEventDialog("CREATE", selectedDate);
//        } else {
//            showAlert("Error", "No hay usuario logueado", javafx.scene.control.Alert.AlertType.ERROR);
//        }
//    }
//
//    private void openEventDialog(String mode, LocalDate date) {
//        try {
//            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/event-dialog.fxml"));
//            Parent dialogRoot = loader.load();
//            com.utez.calendario.controllers.EventDialogController dialogController = loader.getController();
//
//            Runnable onEventChanged = this::loadEventsFromDatabase;
//
//            if ("CREATE".equals(mode)) {
//                dialogController.initializeForCreate(date, onEventChanged);
//            } else {
//                dialogController.initializeForRead(date, onEventChanged);
//            }
//
//            Stage dialogStage = new Stage();
//            dialogStage.setTitle("UTEZ Calendar - Gestión de Eventos");
//            dialogStage.initModality(Modality.WINDOW_MODAL);
//            dialogStage.initOwner(createButton.getScene().getWindow());
//            Scene dialogScene = new Scene(dialogRoot);
//
//            try {
//                dialogScene.getStylesheets().add(getClass().getResource("/css/dialog-styles.css").toExternalForm());
//            } catch (Exception ignored) {}
//
//            dialogStage.setScene(dialogScene);
//            dialogStage.setResizable(true);
//            dialogStage.setMinWidth(600);
//            dialogStage.setMinHeight(500);
//            dialogStage.showAndWait();
//
//        } catch (IOException e) {
//            e.printStackTrace();
//            showAlert("Error", "No se pudo abrir el diálogo de eventos: " + e.getMessage(),
//                    javafx.scene.control.Alert.AlertType.ERROR);
//        }
//    }
//
//    @FXML
//    private void handleCloseButton() {
//        if (authService.getCurrentUser() != null) {
//            authService.logout();
//        }
//        Platform.exit();
//    }
//
//    @FXML
//    private void handleDayView() {
//        currentViewMode = 0;
//        updateViewModeUI();
//
//        try {
//            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/calendar-day.fxml"));
//            Parent dayRoot = loader.load();
//
//            Stage stage = (Stage) calendarGrid.getScene().getWindow();
//
//            double currentWidth = stage.getWidth() > 100 ? stage.getWidth() : 1000;
//            double currentHeight = stage.getHeight() > 100 ? stage.getHeight() : 700;
//            boolean isMaximized = stage.isMaximized();
//
//            Scene scene = new Scene(dayRoot);
//            scene.getStylesheets().add(getClass().getResource("/css/calendar-day.css").toExternalForm());
//
//            stage.setScene(scene);
//            stage.setTitle("UTEZ Calendar - Vista Día");
//
//            stage.setResizable(true);
//            stage.setMinWidth(800);
//            stage.setMinHeight(600);
//
//            if (isMaximized) {
//                stage.setMaximized(true);
//            } else {
//                stage.setWidth(currentWidth);
//                stage.setHeight(currentHeight);
//                Platform.runLater(stage::centerOnScreen);
//            }
//
//        } catch (IOException e) {
//            e.printStackTrace();
//            showAlert("Error", "No se pudo cargar la vista día:\n" + e.getMessage(), javafx.scene.control.Alert.AlertType.ERROR);
//        }
//    }
//
//    @FXML
//    private void handleWeekView() {
//        currentViewMode = 1;
//        selectedDate = LocalDate.now();
//        startOfWeek = selectedDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY));
//        updateViewModeUI();
//        loadEventsFromDatabase();
//    }
//
//    @FXML
//    private void handleMonthView() {
//        currentViewMode = 2;
//        updateViewModeUI();
//
//        try {
//            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/calendar-month.fxml"));
//            Parent monthRoot = loader.load();
//
//            Stage stage = (Stage) calendarGrid.getScene().getWindow();
//
//            double currentWidth = stage.getWidth() > 100 ? stage.getWidth() : 1200;
//            double currentHeight = stage.getHeight() > 100 ? stage.getHeight() : 800;
//            boolean isMaximized = stage.isMaximized();
//
//            Scene scene = new Scene(monthRoot);
//            scene.getStylesheets().add(getClass().getResource("/css/styles-month.css").toExternalForm());
//
//            stage.setScene(scene);
//            stage.setTitle("UTEZ Calendar - Vista Mes");
//
//            stage.setResizable(true);
//            stage.setMinWidth(1000);
//            stage.setMinHeight(700);
//
//            if (isMaximized) {
//                stage.setMaximized(true);
//            } else {
//                stage.setWidth(currentWidth);
//                stage.setHeight(currentHeight);
//                Platform.runLater(stage::centerOnScreen);
//            }
//
//        } catch (IOException e) {
//            e.printStackTrace();
//            showAlert("Error", "No se pudo cargar la vista mes:\n" + e.getMessage(), javafx.scene.control.Alert.AlertType.ERROR);
//        }
//    }
//
//    @FXML
//    private void handleYearView() {
//        currentViewMode = 3;
//        updateViewModeUI();
//
//        // Si tienes una vista de año implementada, haz algo similar, por ejemplo:
//        try {
//            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/calendar-year.fxml"));
//            Parent yearRoot = loader.load();
//
//            Stage stage = (Stage) calendarGrid.getScene().getWindow();
//
//            double currentWidth = stage.getWidth() > 100 ? stage.getWidth() : 1200;
//            double currentHeight = stage.getHeight() > 100 ? stage.getHeight() : 800;
//            boolean isMaximized = stage.isMaximized();
//
//            Scene scene = new Scene(yearRoot);
//            scene.getStylesheets().add(getClass().getResource("/css/calendar-year.css").toExternalForm());
//
//            stage.setScene(scene);
//            stage.setTitle("UTEZ Calendar - Vista Año");
//
//            stage.setResizable(true);
//            stage.setMinWidth(1000);
//            stage.setMinHeight(700);
//
//            if (isMaximized) {
//                stage.setMaximized(true);
//            } else {
//                stage.setWidth(currentWidth);
//                stage.setHeight(currentHeight);
//                Platform.runLater(stage::centerOnScreen);
//            }
//
//        } catch (IOException e) {
//            e.printStackTrace();
//            showAlert("Error", "No se pudo cargar la vista año:\n" + e.getMessage(), javafx.scene.control.Alert.AlertType.ERROR);
//        }
//    }
//
//
//    private void updateViewModeUI() {
//        updateStatusBar();
//    }
//
//    private void handleDateClick(LocalDate date) {
//        selectedDate = date;
//        startOfWeek = selectedDate.with(DayOfWeek.SUNDAY);
//        loadEventsFromDatabase();
//        if (events.containsKey(date)) {
//            openEventDialog("READ", date);
//        } else {
//            openEventDialog("CREATE", date);
//        }
//    }
//
//    private void showAlert(String title, String message, javafx.scene.control.Alert.AlertType type) {
//        javafx.scene.control.Alert alert = new javafx.scene.control.Alert(type);
//        alert.setTitle(title);
//        alert.setHeaderText(null);
//        alert.setContentText(message);
//        alert.showAndWait();
//    }
//}
