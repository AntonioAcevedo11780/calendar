package com.utez.calendario.controllers;

import com.utez.calendario.models.Event;
import com.utez.calendario.models.User;
import com.utez.calendario.services.AuthService;
import com.utez.calendario.services.EventService;
import javafx.animation.FadeTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.Timeline;
import javafx.application.Platform;
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
import javafx.util.Duration;

import java.io.IOException;
import java.net.URL;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.*;

public class CalendarYearController implements Initializable {

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

    // ========== VARIABLES DE ESTADO ==========
    private int currentYear;
    private LocalDate selectedDate;
    private Map<LocalDate, List<String>> events;
    private List<String> viewModes = Arrays.asList("Día", "Semana", "Mes", "Año");
    private int currentViewMode = 3; // Año por defecto

    private EventService eventService;
    private AuthService authService;

    private double xOffset = 0;
    private double yOffset = 0;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        System.out.println("\n=== INICIANDO VISTA ANUAL ===");
        System.out.println("Fecha/Hora UTC: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));

        eventService = EventService.getInstance();
        authService = AuthService.getInstance();

        if (authService.getCurrentUser() != null) {
            User currentUser = authService.getCurrentUser();
            System.out.println("Usuario logueado: " + currentUser.getDisplayInfo());
        } else {
            System.out.println("No hay usuario logueado");
        }

        initializeYearView();
        setupAnimations();
        updateStatusBar();

        Platform.runLater(() -> {
            setupYearGridConstraints();
            loadEventsFromDatabase();
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

    private void loadEventsFromDatabase() {
        if (authService.getCurrentUser() != null) {
            String userId = authService.getCurrentUser().getUserId();

            System.out.println("\n[" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "] " +
                    "Cargando eventos anuales desde BD...");
            System.out.println("Usuario ID: " + userId);
            System.out.println("Año actual: " + currentYear);

            try {
                // Cargar todos los eventos del año
                LocalDate startOfYear = LocalDate.of(currentYear, 1, 1);
                LocalDate endOfYear = LocalDate.of(currentYear, 12, 31);

                List<Event> yearEvents = eventService.getEventsForDateRange(userId, startOfYear, endOfYear);
                events.clear();

                for (Event event : yearEvents) {
                    LocalDate eventDate = event.getStartDate().toLocalDate();
                    events.computeIfAbsent(eventDate, k -> new ArrayList<>()).add(event.getTitle());
                }

                updateYearView();
                System.out.println("[" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "] " +
                        "Eventos anuales cargados correctamente desde BD");

            } catch (Exception e) {
                System.err.println("[" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "] " +
                        "Error cargando eventos anuales: " + e.getMessage());
                e.printStackTrace();
                showAlert("Error de Conexión",
                        "No se pueden cargar los eventos desde la base de datos.\nVerifica tu conexión y configuración.",
                        Alert.AlertType.WARNING);
            }
        } else {
            System.out.println(" [" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "] " +
                    "No hay usuario logueado");
            events.clear();
            updateYearView();
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

        // Nombre del mes
        Label lblMonth = new Label(Month.of(month + 1).getDisplayName(TextStyle.FULL, new Locale("es")));
        lblMonth.getStyleClass().add("mini-month-title");
        lblMonth.setMaxWidth(Double.MAX_VALUE);
        lblMonth.setAlignment(Pos.CENTER);

        // Días de la semana
        HBox weekDays = new HBox(2);
        weekDays.setAlignment(Pos.CENTER);
        weekDays.getStyleClass().add("mini-week-days");

        String[] dayNames = {"D", "L", "M", "X", "J", "V", "S"};
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

        int dayNum = 1;
        for (int week = 0; week < 6 && dayNum <= lengthOfMonth; week++) {
            for (int dow = 0; dow < 7; dow++) {
                if ((week == 0 && dow < startDay) || dayNum > lengthOfMonth) {
                    Label emptyDay = new Label(" ");
                    emptyDay.getStyleClass().add("mini-day-empty");
                    emptyDay.setPrefWidth(20);
                    emptyDay.setAlignment(Pos.CENTER);
                    daysGrid.add(emptyDay, dow, week);
                } else {
                    LocalDate dayDate = LocalDate.of(currentYear, month + 1, dayNum);
                    Label dayLabel = new Label(String.valueOf(dayNum));
                    dayLabel.getStyleClass().add("mini-day");
                    dayLabel.setPrefWidth(20);
                    dayLabel.setAlignment(Pos.CENTER);

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
                    dayLabel.setOnMouseClicked(e -> navigateToMonth(finalMonth, finalDay));

                    daysGrid.add(dayLabel, dow, week);
                    dayNum++;
                }
            }
        }

        vbox.getChildren().addAll(lblMonth, weekDays, daysGrid);
        return vbox;
    }

    private void navigateToMonth(int month, int day) {
        try {
            // Cargar la vista mensual
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/calendar-month.fxml"));
            Parent monthRoot = loader.load();


            // Cambiar la escena
            Stage stage = (Stage) yearGrid.getScene().getWindow();
            Scene scene = new Scene(monthRoot);
            scene.getStylesheets().add(getClass().getResource("/css/styles-month.css").toExternalForm());
            stage.setScene(scene);

        } catch (IOException e) {
            System.err.println("Error navegando a vista mensual: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void updateStatusBar() {
        if (statusLabel != null) {
            String userInfo = "";
            if (authService.getCurrentUser() != null) {
                User user = authService.getCurrentUser();
                userInfo = " | " + user.getDisplayId() + " (" + user.getRole().getDisplayName() + ")";
            }

            String status = String.format("Vista: %s | Año: %d | Eventos: %d%s",
                    viewModes.get(currentViewMode),
                    currentYear,
                    events.size(),
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

    private void openEventDialog(String mode, LocalDate date) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/event-dialog.fxml"));
            Parent dialogRoot = loader.load();
            // EventDialogController dialogController = loader.getController();

            Runnable onEventChanged = () -> {
                System.out.println("✓ [" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "] " +
                        "Recargando eventos tras cambio");
                loadEventsFromDatabase();
            };

            Stage dialogStage = new Stage();
            dialogStage.setTitle("UTEZ Calendar - Gestión de Eventos");
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
            System.err.println("Error abriendo diálogo: " + e.getMessage());
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