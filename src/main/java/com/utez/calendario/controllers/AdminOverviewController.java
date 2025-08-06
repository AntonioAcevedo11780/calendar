package com.utez.calendario.controllers;

import com.utez.calendario.models.Calendar;
import com.utez.calendario.models.Event;
import com.utez.calendario.services.AuthService;
import com.utez.calendario.models.User;
import com.utez.calendario.services.TimeService;

import javafx.beans.property.*;
import javafx.collections.ObservableList;
import javafx.geometry.Rectangle2D;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.stage.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.Priority;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.RowConstraints;
import javafx.geometry.HPos;
import javafx.geometry.VPos;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;
import javafx.geometry.Pos;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

import java.io.IOException;
import java.net.URL;
import java.time.LocalTime;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;

public class AdminOverviewController implements Initializable {

    @FXML private StackPane contentArea;
    @FXML private Label statusLabel;
    @FXML private Label clockLabel;
    @FXML private SplitPane splitPane;
    private Map<String, User> userMap;

    private AuthService authService;

    private Timeline clockTimeline;
    private final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss");
    private final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private VBox usersCard, activeCalendarsCard, eventsForThisMonthCard;
    private int cachedTotalUsers = 0, cachedActiveStudents = 0;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        try {
            startClock();
            User.setDashboardController(this);

            Platform.runLater(() -> {
                if (splitPane != null) {
                    splitPane.getDividers().get(0).positionProperty().addListener((obs, oldPos, newPos) -> {
                        splitPane.setDividerPositions(0.22);
                    });
                }
                List<User> list= User.getUsers(0,1000, false);
                userMap = list.stream().collect(Collectors.toMap(User::getUserId, Function.identity()));

                handleGeneralView();
            });
        } catch (Exception e) {
            System.err.println("Error en inicialización: " + e.getMessage());
            setStatus("Error en inicialización");
        }

        Timeline refreshTimeline = new Timeline(new KeyFrame(Duration.minutes(10), e -> refreshDashboard()));
        refreshTimeline.setCycleCount(Timeline.INDEFINITE);
        refreshTimeline.play();
    }

    private User findUserById(String userId) {
        return userMap.getOrDefault(userId, null);
    }

    // ✅ MÉTODOS UTILITARIOS OPTIMIZADOS
    private ImageView createIcon(String path) {
        ImageView icon = new ImageView(new Image(getClass().getResourceAsStream(path)));
        icon.setFitHeight(30);
        icon.setFitWidth(30);
        icon.setPreserveRatio(true);
        return icon;
    }

    private void setStatus(String message) {
        if (statusLabel != null) {
            Platform.runLater(() -> statusLabel.setText(message));
        }
    }

    // ✅ EVENTOS DEL SISTEMA
    public void onUserToggledWithRole(boolean wasActivated, User.Role userRole) {
        Platform.runLater(() -> {
            try {
                cachedTotalUsers += wasActivated ? 1 : -1;
                if (userRole == User.Role.ALUMNO) {
                    cachedActiveStudents += wasActivated ? 1 : -1;
                }

                updateCard(usersCard, String.valueOf(cachedTotalUsers),
                        cachedActiveStudents + " estudiantes activos");

                setStatus("Usuario " + (wasActivated ? "activado" : "desactivado") + " correctamente");

            } catch (Exception e) {
                setStatus("Error actualizando dashboard: " + e.getMessage());
            }
        });
    }

    @FXML
    private void handleLogout() {
        try {
            setStatus("Cerrando sesión...");
            if (clockTimeline != null) clockTimeline.stop();
            AuthService.getInstance().logout();
            Platform.runLater(this::returnToLogin);
        } catch (Exception e) {
            System.err.println("Error al cerrar sesión: " + e.getMessage());
            setStatus("Error al cerrar sesión");
        }
    }

    @FXML
    private void handleGeneralView() {
        try {
            contentArea.getChildren().clear();

            ScrollPane scrollPane = new ScrollPane();
            scrollPane.setFitToWidth(true);
            scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
            scrollPane.getStyleClass().add("dashboard-scroll");

            VBox mainContainer = new VBox(30);
            mainContainer.setAlignment(Pos.TOP_CENTER);
            mainContainer.getStyleClass().add("dashboard-container");

            mainContainer.getChildren().addAll(
                    createSummarySection(),
                    createUserManagementSection(),
                    createCalendarManagementSection(),
                    createStatisticsSection()
            );

            scrollPane.setContent(mainContainer);
            contentArea.getChildren().add(scrollPane);

        } catch (Exception e) {
            System.err.println("Error al cargar vista general: " + e.getMessage());
            setStatus("Error al cargar vista");
        }
    }

    // ✅ SECCIÓN RESUMEN OPTIMIZADA
    private VBox createSummarySection() {
        VBox summarySection = new VBox(20);
        summarySection.setAlignment(Pos.CENTER);

        Label summaryTitle = new Label("Vista General");
        summaryTitle.getStyleClass().add("section-title");

        HBox summaryCards = new HBox(20);
        summaryCards.setAlignment(Pos.CENTER);
        summaryCards.getStyleClass().add("summary-cards");

        // Cargar datos de forma eficiente
        int[] counts = loadDashboardCounts();

        usersCard = createSummaryCard("users-card", "Usuarios Totales",
                String.valueOf(counts[0]), counts[3] + " estudiantes activos");

        activeCalendarsCard = createSummaryCard("activeCalendars-card", "Calendarios Activos",
                String.valueOf(counts[1]), "Calendarios disponibles");

        eventsForThisMonthCard = createSummaryCard("eventsForThisMonth-card", "Eventos este mes",
                String.valueOf(counts[2]), counts[4] + " eventos próximos");

        summaryCards.getChildren().addAll(usersCard, activeCalendarsCard, eventsForThisMonthCard);
        summarySection.getChildren().addAll(createSectionHeader("Vista General"), summaryCards);

        return summarySection;
    }

    private int[] loadDashboardCounts() {
        try {
            // ✅ USAR el método optimizado que creamos
            User.DashboardData data = User.getDashboardData(); // Ya está optimizado
            cachedTotalUsers = data.totalUsers; // ✅ Cambiar esta línea
            cachedActiveStudents = data.activeStudents;

            setStatus("Datos cargados correctamente");
            return new int[]{
                    data.totalUsers,        // ✅ Cambiar esta línea
                    data.activeCalendars,   // ✅ Quitar el -1
                    data.eventsThisMonth,
                    data.activeStudents,
                    data.upcomingEvents
            };
        } catch (Exception e) {
            setStatus("Error al cargar datos: " + e.getMessage());
            return new int[]{0, 0, 0, 0, 0};
        }
    }

    private HBox createSectionHeader(String title) {
        HBox header = new HBox();
        header.getStyleClass().add("section-header");
        header.setAlignment(Pos.CENTER);

        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("section-title");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        header.getChildren().addAll(titleLabel, spacer);
        return header;
    }

    private VBox createSummaryCard(String styleClass, String title, String value, String subtitle) {
        VBox card = new VBox();
        card.setAlignment(Pos.CENTER);
        card.getStyleClass().addAll("summary-card", styleClass);

        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("card-title");

        Label valueLabel = new Label(value);
        valueLabel.getStyleClass().add("card-value");

        Label subtitleLabel = new Label(subtitle);
        subtitleLabel.getStyleClass().add("card-subtitle");

        card.getChildren().addAll(titleLabel, valueLabel, subtitleLabel);
        return card;
    }

    private void updateCard(VBox card, String newValue, String newSubtitle) {
        if (card != null && card.getChildren().size() >= 3) {
            Label valueLabel = (Label) card.getChildren().get(1);
            Label subtitleLabel = (Label) card.getChildren().get(2);

            Platform.runLater(() -> {
                valueLabel.setText(newValue);
                subtitleLabel.setText(newSubtitle);
            });
        }
    }

    // ✅ SECCIÓN USUARIOS OPTIMIZADA
    private VBox createUserManagementSection() {
        VBox userSection = new VBox(20);
        userSection.setAlignment(Pos.CENTER);

        TableView<User> userTable = createUserTable();
        HBox paginationBar = createUserPagination(userTable);

        VBox userContent = new VBox(10);
        userContent.getStyleClass().add("table-container");
        userContent.getChildren().addAll(userTable, paginationBar);

        userSection.getChildren().addAll(createSectionHeader("Administración de Usuarios"), userContent);
        return userSection;
    }

    private TableView<User> createUserTable() {
        TableView<User> table = new TableView<>();
        table.getStyleClass().add("user-table");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setFixedCellSize(40);
        table.setPrefHeight(440);

        // Crear columnas de forma eficiente
        TableColumn<User, String>[] columns = new TableColumn[]{
                createColumn("Matrícula", "matricula"),
                createColumn("Email", "email")
        };

        TableColumn<User, String> nameColumn = new TableColumn<>("Nombre");
        nameColumn.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getFullName()));

        TableColumn<User, String> roleColumn = new TableColumn<>("Rol");
        roleColumn.setCellValueFactory(cellData ->
                new SimpleStringProperty(cellData.getValue().getRole().getDisplayName()));

        TableColumn<User, Boolean> activeColumn = new TableColumn<>("Activo");
        activeColumn.setCellValueFactory(cellData -> new SimpleBooleanProperty(cellData.getValue().isActive()));
        activeColumn.setCellFactory(col -> new TableCell<User, Boolean>() {
            @Override
            protected void updateItem(Boolean item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : (item ? "Sí" : "No"));
            }
        });

        TableColumn<User, Void> actionsColumn = new TableColumn<>("Acciones");
        actionsColumn.setCellFactory(col -> createUserActionCell());

        table.getColumns().addAll(List.of(columns[0], nameColumn, columns[1], roleColumn, activeColumn, actionsColumn));
        return table;
    }

    private TableColumn<User, String> createColumn(String title, String property) {
        TableColumn<User, String> column = new TableColumn<>(title);
        column.setCellValueFactory(new PropertyValueFactory<>(property));
        return column;
    }

    private TableCell<User, Void> createUserActionCell() {
        return new TableCell<User, Void>() {
            private final Button toggleButton = new Button();
            private final HBox pane = new HBox(5, toggleButton);

            {
                pane.setAlignment(Pos.CENTER);
                toggleButton.setOnAction(event -> {
                    User user = getTableView().getItems().get(getIndex());
                    CompletableFuture.runAsync(() -> {
                        boolean success = user.toggleActive();
                        Platform.runLater(() -> {
                            if (success) {
                                updateButtonStyle(user);
                                getTableView().refresh();
                                setStatus((user.isActive() ? "Usuario activado: " : "Usuario desactivado: ") + user.getFullName());
                            } else {
                                setStatus("Error al cambiar estado del usuario: " + user.getFullName());
                            }
                        });
                    });
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    updateButtonStyle(getTableView().getItems().get(getIndex()));
                    setGraphic(pane);
                }
            }

            private void updateButtonStyle(User user) {
                toggleButton.setText(user.isActive() ? "Desactivar" : "Activar");
                toggleButton.getStyleClass().setAll(user.isActive() ? "deactivate-button" : "activate-button");
            }
        };
    }

    private HBox createUserPagination(TableView<User> userTable) {
        final int itemsPerPage = 10;
        final IntegerProperty currentPageIndex = new SimpleIntegerProperty(0);
        final List<User> masterData = User.getUsers(0, 100, false);

        Runnable updatePageData = () -> {
            int startIndex = currentPageIndex.get() * itemsPerPage;
            int endIndex = Math.min(startIndex + itemsPerPage, masterData.size());
            userTable.getItems().setAll(masterData.subList(startIndex, endIndex));
            setStatus("Mostrando usuarios " + (startIndex + 1) + " a " + endIndex + " de " + masterData.size());
        };

        Button[] navButtons = {
                createPaginationButton("◀", "/images/arrow-left.png", e -> {
                    currentPageIndex.set(currentPageIndex.get() - 1);
                    updatePageData.run();
                }),
                createPaginationButton("▶", "/images/arrow-right.png", e -> {
                    currentPageIndex.set(currentPageIndex.get() + 1);
                    updatePageData.run();
                })
        };

        Label pageInfoLabel = new Label();

        currentPageIndex.addListener((obs, oldVal, newVal) -> {
            int pageIndex = newVal.intValue();
            navButtons[0].setDisable(pageIndex == 0);
            navButtons[1].setDisable((pageIndex + 1) * itemsPerPage >= masterData.size());
            pageInfoLabel.setText("Página " + (pageIndex + 1) + " de " + ((masterData.size() - 1) / itemsPerPage + 1));
        });

        updatePageData.run();
        int pageIndex = currentPageIndex.get();
        navButtons[0].setDisable(pageIndex == 0);
        navButtons[1].setDisable((pageIndex + 1) * itemsPerPage >= masterData.size());
        pageInfoLabel.setText("Página " + (pageIndex + 1) + " de " + ((masterData.size() - 1) / itemsPerPage + 1));

        HBox paginationBar = new HBox(10, navButtons[0], pageInfoLabel, navButtons[1]);
        paginationBar.setAlignment(Pos.CENTER);
        paginationBar.getStyleClass().add("pagination-bar");

        return paginationBar;
    }

    private Button createPaginationButton(String text, String iconPath, javafx.event.EventHandler<javafx.event.ActionEvent> action) {
        Button button = new Button();
        button.getStyleClass().addAll("pagination-button", "icon-button");
        button.setGraphic(createIcon(iconPath));
        button.setOnAction(action);
        return button;
    }

    // ✅ SECCIÓN CALENDARIOS SÚPER OPTIMIZADA
    private VBox createCalendarManagementSection() {
        VBox calendarSection = new VBox(20);
        calendarSection.setAlignment(Pos.CENTER);

        VBox calendarContent = new VBox(10);

        calendarContent.getStyleClass().add("table-container");
        calendarContent.setAlignment(Pos.CENTER);

        TableView<Calendar> calendarTable = createOptimizedCalendarTable();

        // Cargar datos asíncronamente
        loadCalendarDataAsync().thenAccept(calendars -> {
            Platform.runLater(() -> {
                HBox paginationBar = createCalendarPagination(calendarTable, calendars);
                calendarContent.getChildren().addAll(calendarTable, paginationBar);
            });
        });

        calendarSection.getChildren().addAll(createSectionHeader("Administración de Calendarios"), calendarContent);
        return calendarSection;
    }

    private TableView<Calendar> createOptimizedCalendarTable() {
        TableView<Calendar> table = new TableView<>();
        table.getStyleClass().add("data-table");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setFixedCellSize(40);
        table.setPrefHeight(440);

        // ✅ Columna Matrícula centrada
        TableColumn<Calendar, String> matriculaColumn = new TableColumn<>("Matrícula del Propietario");
        matriculaColumn.setCellValueFactory(cellData -> {
            Calendar calendar = cellData.getValue();
            User user =findUserById(calendar.getOwnerId());
            if (user == null) {
                System.out.println("No se encontró usuario con ID: " + calendar.getOwnerId());
            }
            String matricula = user != null ? user.getMatricula() : "N/A";
            return new SimpleStringProperty(matricula);
        });
        matriculaColumn.setPrefWidth(150);
        matriculaColumn.setCellFactory(col -> createCenteredTextCell());

        /*TableColumn<Calendar, String> nameOwnerColumn = new TableColumn<>("Nombre del Propietario");
        nameOwnerColumn.setCellValueFactory(cellData ->{
            Calendar calendar = cellData.getValue();
            User user =findUserById(calendar.getOwnerId());
            String nameOwner = user != null ? user.getFullName() :  "N/A";
            return new SimpleStringProperty(nameOwner);
        });
        nameOwnerColumn.setPrefWidth(150);
        nameOwnerColumn.setCellFactory(col -> createCenteredTextCell());*/


        // ✅ Columna Nombre centrada
        TableColumn<Calendar, String> nameColumn = new TableColumn<>("Nombre Del Calendario");
        nameColumn.setCellValueFactory(cellData -> {
            Calendar calendar = cellData.getValue();
            String displayName = calendar.getName() != null && !calendar.getName().isEmpty()
                    ? calendar.getName()
                    : "Calendario " + calendar.getCalendarId();
            return new SimpleStringProperty(displayName);
        });
        nameColumn.setPrefWidth(200);
        nameColumn.setCellFactory(col -> createCenteredTextCell());

        // ✅ Columna Última Modificación centrada
        TableColumn<Calendar, String> modifiedColumn = new TableColumn<>("Última Modificación");
        modifiedColumn.setCellValueFactory(cellData -> {
            Calendar calendar = cellData.getValue();
            return new SimpleStringProperty(calendar.getModifiedDate() != null ?
                    calendar.getModifiedDate().format(dateFormatter) : "N/A");
        });
        modifiedColumn.setPrefWidth(150);
        modifiedColumn.setCellFactory(col -> createCenteredTextCell());

        // ✅ Columna Vista Previa centrada
        TableColumn<Calendar, Button> viewColumn = new TableColumn<>("Vista Previa");
        viewColumn.setCellValueFactory(cellData -> {
            Button viewButton = new Button("📅 Ver Calendario");
            viewButton.getStyleClass().add("view-calendar-button");
            viewButton.setOnAction(e -> openCalendarViewerWindow(cellData.getValue()));
            return new SimpleObjectProperty<>(viewButton);
        });
        viewColumn.setPrefWidth(150);
        viewColumn.setCellFactory(col -> createCenteredButtonCell());

        table.getColumns().addAll(matriculaColumn/*, nameOwnerColumn*/, nameColumn, modifiedColumn, viewColumn);
        return table;
    }

    // ✅ Método helper para celdas de texto centradas
    private TableCell<Calendar, String> createCenteredTextCell() {
        return new TableCell<Calendar, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item);
                }
                setAlignment(Pos.CENTER);
            }
        };
    }

    // Centrar celdas de botones caledarManagemnt
    private TableCell<Calendar, Button> createCenteredButtonCell() {
        return new TableCell<Calendar, Button>() {
            @Override
            protected void updateItem(Button item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                } else {
                    setGraphic(item);
                }
                setAlignment(Pos.CENTER);
            }
        };
    }

    private CompletableFuture<List<Calendar>> loadCalendarDataAsync() {
        setStatus("Cargando calendarios...");

        return CompletableFuture.supplyAsync(Calendar::getAllActiveCalendars)
                .whenComplete((calendars, throwable) -> {
                    Platform.runLater(() -> {
                        if (throwable != null) {
                            setStatus("Error al cargar calendarios");
                        } else {
                            setStatus("Calendarios cargados: " + calendars.size());
                        }
                    });
                });
    }

    private void openCalendarViewerWindow(Calendar calendar) {
        try {
            System.out.println("🔄 Abriendo vista de calendario: " + calendar.getCalendarId());

            Stage calendarStage = createCalendarStage(calendar);
            VBox calendarView = createSuperOptimizedCalendarView(calendar);

            // ✅ Tamaño más compacto para vista admin
            Scene calendarScene = new Scene(calendarView, 800, 600); // ✅ Más pequeño

            try {
                String cssPath = getClass().getResource("/css/calendar-month.css").toExternalForm();
                calendarScene.getStylesheets().add(cssPath);
                System.out.println("✅ CSS cargado correctamente");
            } catch (Exception cssError) {
                System.err.println("⚠️ No se pudo cargar CSS: " + cssError.getMessage());
            }

            calendarStage.setScene(calendarScene);
            calendarStage.show();

            loadAndDisplayCalendarEvents(calendar, calendarView);

            System.out.println("✅ Vista de calendario abierta correctamente");

        } catch (Exception e) {
            System.err.println("❌ Error: " + e.getMessage());
            e.printStackTrace();
            showAlert("Error", "No se pudo abrir la vista del calendario:\n" + e.getMessage(), Alert.AlertType.ERROR);
        }
    }

    private Stage createCalendarStage(Calendar calendar) {
        Stage stage = new Stage();
        stage.setTitle("UTEZ Calendar - Vista: " + calendar.getDisplayName());
        stage.initModality(Modality.NONE);

        Stage currentStage = getCurrentStage();
        if (currentStage != null) {
            stage.initOwner(currentStage);
            stage.setX(currentStage.getX() + 50);
            stage.setY(currentStage.getY() + 50);
        }

        stage.setResizable(true);
        stage.setMinWidth(700); // ✅ Más pequeño
        stage.setMinHeight(500); // ✅ Más pequeño
        return stage;
    }

    private VBox createSuperOptimizedCalendarView(Calendar calendar) {
        VBox calendarView = new VBox(10); // ✅ Espaciado más compacto
        calendarView.getStyleClass().add("calendar-area"); // ✅ Usar clase CSS correcta

        calendarView.getChildren().addAll(
                createCalendarHeader(calendar),
                createReadOnlyInfoBox(),
                createOptimizedCalendarGrid(),
                createCalendarStatusBar()
        );

        VBox.setVgrow(calendarView.getChildren().get(2), Priority.ALWAYS);
        return calendarView;
    }

    // ✅ USAR getDisplayName() del modelo Calendar
    private HBox createCalendarHeader(Calendar calendar) {
        HBox header = new HBox(20);
        header.getStyleClass().add("calendar-header");
        header.setAlignment(Pos.CENTER);

        Button[] navButtons = {
                createNavButton("◀", true),
                createNavButton("▶", true)
        };

        VBox titleBox = new VBox();
        titleBox.setAlignment(Pos.CENTER);

        Label monthYearLabel = new Label();
        monthYearLabel.getStyleClass().add("month-year-label");
        monthYearLabel.setId("monthYearLabel");

        // ✅ CAMBIAR esta línea
        Label calendarNameLabel = new Label(calendar.getDisplayName());
        calendarNameLabel.getStyleClass().add("calendar-name-label");

        titleBox.getChildren().addAll(monthYearLabel, calendarNameLabel);
        header.getChildren().addAll(navButtons[0], titleBox, navButtons[1]);

        return header;
    }

    private Button createNavButton(String text, boolean disabled) {
        Button button = new Button(text);
        button.getStyleClass().add("nav-button");
        button.setDisable(disabled);
        return button;
    }

    private HBox createReadOnlyInfoBox() {
        HBox infoBox = new HBox();
        infoBox.getStyleClass().add("calendar-info");
        infoBox.setAlignment(Pos.CENTER);

        Label infoLabel = new Label("👁️ VISTA DE SOLO LECTURA - El administrador no puede modificar eventos");
        infoLabel.getStyleClass().add("readonly-info");

        infoBox.getChildren().add(infoLabel);
        return infoBox;
    }

    private GridPane createOptimizedCalendarGrid() {
        GridPane grid = new GridPane();
        grid.getStyleClass().add("main-calendar-grid");
        grid.setId("calendarGrid");

        // Configurar constraints optimizadamente
        for (int i = 0; i < 7; i++) {
            ColumnConstraints col = new ColumnConstraints();
            col.setPercentWidth(100.0 / 7);
            col.setHalignment(HPos.CENTER);
            col.setHgrow(Priority.ALWAYS);
            col.setFillWidth(true);
            grid.getColumnConstraints().add(col);
        }

        for (int i = 0; i < 6; i++) {
            RowConstraints row = new RowConstraints();
            row.setMinHeight(70); // ✅ Altura más pequeña para vista admin
            row.setVgrow(Priority.ALWAYS);
            row.setFillHeight(true);
            row.setValignment(VPos.TOP);
            grid.getRowConstraints().add(row);
        }

        return grid;
    }

    private HBox createCalendarStatusBar() {
        HBox statusBar = new HBox();
        statusBar.getStyleClass().add("status-bar");
        statusBar.setAlignment(Pos.CENTER_LEFT);

        Label statusLabel = new Label("Cargando eventos...");
        statusLabel.getStyleClass().add("status-label");
        statusLabel.setId("statusLabel");

        statusBar.getChildren().add(statusLabel);
        return statusBar;
    }

    // ✅ CARGA ULTRA OPTIMIZADA DE EVENTOS
    private void loadAndDisplayCalendarEvents(Calendar calendar, VBox calendarView) {
        CompletableFuture.runAsync(() -> {
            try {
                // ✅ USAR el método del modelo Calendar
                List<Event> events = calendar.getCurrentMonthEvents();

                Map<LocalDate, List<Event>> eventsByDate = new HashMap<>();
                for (Event event : events) {
                    LocalDate eventDate = event.getStartDate().toLocalDate();
                    eventsByDate.computeIfAbsent(eventDate, k -> new ArrayList<>()).add(event);
                }

                Platform.runLater(() -> {
                    updateOptimizedCalendarView(calendarView, YearMonth.from(TimeService.getInstance().now()), eventsByDate);
                    updateCalendarStatusLabel(calendarView, "Eventos cargados: " + events.size());
                });

            } catch (Exception e) {
                Platform.runLater(() -> updateCalendarStatusLabel(calendarView, "Error cargando eventos: " + e.getMessage()));
            }
        });
    }

    private void updateOptimizedCalendarView(VBox calendarView, YearMonth yearMonth, Map<LocalDate, List<Event>> eventsByDate) {
        Label monthYearLabel = (Label) calendarView.lookup("#monthYearLabel");
        if (monthYearLabel != null) {
            Locale spanishLocale = Locale.of("es", "ES");
            String monthName = yearMonth.getMonth().getDisplayName(TextStyle.FULL, spanishLocale);
            monthYearLabel.setText(monthName.toUpperCase() + " " + yearMonth.getYear());
        }

        GridPane calendarGrid = (GridPane) calendarView.lookup("#calendarGrid");
        if (calendarGrid != null) {
            populateCalendarGridOptimized(calendarGrid, yearMonth, eventsByDate); // ✅ Ya actualizado
        }
    }
    private void populateCalendarGridOptimized(GridPane grid, YearMonth yearMonth, Map<LocalDate, List<Event>> eventsByDate) {
        grid.getChildren().clear();

        LocalDate firstOfMonth = yearMonth.atDay(1);
        int dayOfWeek = firstOfMonth.getDayOfWeek().getValue() % 7;
        LocalDate startDate = firstOfMonth.minusDays(dayOfWeek);

        String[] dayNames = {"DOM", "LUN", "MAR", "MIÉ", "JUE", "VIE", "SÁB"};

        // ✅ AGREGAR headers integrados en la primera fila
        for (int col = 0; col < 7; col++) {
            Label dayHeader = new Label(dayNames[col]);
            dayHeader.getStyleClass().add("day-header-integrated"); // ✅ Usar clase CSS correcta
            dayHeader.setMaxWidth(Double.MAX_VALUE);
            dayHeader.setAlignment(Pos.CENTER);
            grid.add(dayHeader, col, 0);
        }

        // ✅ AGREGAR celdas de días empezando en fila 1
        for (int row = 1; row < 7; row++) { // Empezar en fila 1
            for (int col = 0; col < 7; col++) {
                LocalDate cellDate = startDate.plusDays((row - 1) * 7 + col);
                VBox cellContainer = createCompactCalendarCell(cellDate, yearMonth, eventsByDate);
                grid.add(cellContainer, col, row);
            }
        }
    }

    private VBox createCompactCalendarCell(LocalDate date, YearMonth yearMonth, Map<LocalDate, List<Event>> eventsByDate) {
        VBox cell = new VBox(2); // ✅ Espaciado más pequeño
        cell.setAlignment(Pos.TOP_CENTER);
        cell.getStyleClass().add("calendar-cell");
        cell.setMaxWidth(Double.MAX_VALUE);
        cell.setMaxHeight(Double.MAX_VALUE);

        boolean isCurrentMonth = date.getMonth() == yearMonth.getMonth() && date.getYear() == yearMonth.getYear();
        boolean isToday = date.equals(TimeService.getInstance().now().toLocalDate());
        boolean isWeekend = date.getDayOfWeek().getValue() >= 6;

        // ✅ Aplicar clases CSS según estado
        if (!isCurrentMonth) {
            cell.getStyleClass().add("calendar-cell-other-month");
        }
        if (isToday) {
            cell.getStyleClass().add("calendar-cell-today");
        }
        if (isWeekend && isCurrentMonth) {
            cell.getStyleClass().add("weekend-cell");
        }

        // ✅ Número del día con clase CSS correcta
        Label dayNumber = new Label(String.valueOf(date.getDayOfMonth()));
        if (isToday) {
            dayNumber.getStyleClass().add("day-number-today");
        } else {
            dayNumber.getStyleClass().add("day-number");
        }
        dayNumber.setAlignment(Pos.CENTER);
        cell.getChildren().add(dayNumber);

        // ✅ Agregar eventos con clases CSS correctas
        List<Event> dateEvents = eventsByDate.get(date);
        if (dateEvents != null && !dateEvents.isEmpty()) {
            addCompactEventsToCell(cell, dateEvents);
        }

        return cell;
    }

    private void addCompactEventsToCell(VBox cell, List<Event> events) {
        int maxEventsToShow = 2; // ✅ Menos eventos para vista compacta
        String[] colorClasses = {"event-blue", "event-red", "event-green", "event-orange", "event-purple"};

        for (int i = 0; i < Math.min(events.size(), maxEventsToShow); i++) {
            Event event = events.get(i);
            Label eventLabel = new Label(event.getDisplayTitle()); // ✅ Usar método del modelo

            // ✅ Aplicar clases CSS correctas
            eventLabel.getStyleClass().addAll("event-item", colorClasses[i % colorClasses.length]);

            // Hacer que cada evento sea clickeable individualmente
            eventLabel.setOnMouseClicked(e -> {
                e.consume(); // Evitar que se propague al evento de la celda
                openSpecificEvent(event);
            });

            // ✅ Hacer que sea de solo lectura
            //eventLabel.setMouseTransparent(true);

            cell.getChildren().add(eventLabel);
        }

        // ✅ Indicador de más eventos con clase CSS correcta
        if (events.size() > maxEventsToShow) {
            Label moreLabel = new Label("+" + (events.size() - maxEventsToShow) + " más");
            moreLabel.getStyleClass().add("more-events-label");
            moreLabel.setMouseTransparent(true);
            cell.getChildren().add(moreLabel);
        }
    }

    private VBox createHyperOptimizedCalendarCell(LocalDate date, String dayHeader, YearMonth yearMonth,
                                                  Map<LocalDate, List<Event>> eventsByDate) {
        VBox cell = new VBox(3);
        cell.setAlignment(Pos.TOP_LEFT);
        cell.getStyleClass().add("calendar-cell");
        cell.setMaxWidth(Double.MAX_VALUE);
        cell.setMaxHeight(Double.MAX_VALUE);

        boolean isCurrentMonth = date.getMonth() == yearMonth.getMonth() && date.getYear() == yearMonth.getYear();
        boolean isToday = date.equals(TimeService.getInstance().now().toLocalDate());

        if (!isCurrentMonth) cell.getStyleClass().add("calendar-cell-other-month");
        if (isToday) cell.getStyleClass().add("calendar-cell-today");

        if (dayHeader != null) {
            Label headerLabel = new Label(dayHeader);
            headerLabel.getStyleClass().add("day-header");
            cell.getChildren().add(headerLabel);
        }

        Label dayNumber = new Label(String.valueOf(date.getDayOfMonth()));
        dayNumber.getStyleClass().add("day-number");
        if (isToday) dayNumber.getStyleClass().add("day-number-today");
        cell.getChildren().add(dayNumber);

        List<Event> dateEvents = eventsByDate.get(date);
        if (dateEvents != null && !dateEvents.isEmpty()) {
            addEventsToCell(cell, dateEvents);
        }

        return cell;
    }

    private void addEventsToCell(VBox cell, List<Event> events) {
        int maxEventsToShow = 3;
        String[] colorClasses = {"event-item-blue", "event-item-green", "event-item-purple", "event-item-orange"};

        for (int i = 0; i < Math.min(events.size(), maxEventsToShow); i++) {
            Event event = events.get(i);
            Label eventLabel = new Label(event.getTitle());
            eventLabel.getStyleClass().addAll("event-item", "event-readonly", colorClasses[i % 4]);
            cell.getChildren().add(eventLabel);
        }

        if (events.size() > maxEventsToShow) {
            Label moreLabel = new Label("+" + (events.size() - maxEventsToShow) + " más");
            moreLabel.getStyleClass().add("more-events-label");
            cell.getChildren().add(moreLabel);
        }
    }

    private void updateCalendarStatusLabel(VBox calendarView, String message) {
        Label statusLabel = (Label) calendarView.lookup("#statusLabel");
        if (statusLabel != null) {
            statusLabel.setText(message);
        }
    }

    // ✅ SECCIÓN ESTADÍSTICAS
    private VBox createStatisticsSection() {
        VBox statsSection = new VBox(20);
        statsSection.setAlignment(Pos.CENTER);

        VBox statsContent = new VBox();
        statsContent.getStyleClass().add("chart-container");
        statsContent.setAlignment(Pos.CENTER);

        User model = new User();
        ObservableList<PieChart.Data> data = model.generateRolePieData();

        PieChart pieChart = new PieChart(data);
        pieChart.setTitle("Porcentajes de Estudiantes y Docentes");

        // Calcular el total para porcentajes
        int total = 0;
        for (PieChart.Data item : data) {
            total += item.getPieValue();
        }

        // Modificar las etiquetas para mostrar porcentaje
        for (PieChart.Data item : data) {
            double porcentaje = (item.getPieValue() / total) * 100;
            String etiqueta = String.format("%s (%.1f%%)", item.getName(), porcentaje);
            item.setName(etiqueta);
        }

        // 🟦 Gráfica de barras (eventos activos por docente)
        CategoryAxis xAxis = new CategoryAxis();
        xAxis.setLabel("Docente");

        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("Eventos Activos");

        BarChart<String, Number> barChart = new BarChart<>(xAxis, yAxis);
        barChart.setTitle("Docentes con más eventos activos");

        XYChart.Series<String, Number> serie = new XYChart.Series<>();
        serie.setName("Eventos activos");

        User userModel = new User();
        serie.getData().addAll(userModel.getActiveForEducatorEvents());

        barChart.getData().add(serie);
        barChart.setLegendVisible(false); // Opcional

        //Generar colores para las barras
        String[] colores = { "#27AE60", "#C0392B", "#2980B9" };
        Platform.runLater(() -> {
            int index = 0;
            for (XYChart.Data<String, Number> item : serie.getData()) {
                String color = colores[index % colores.length];
                item.getNode().setStyle("-fx-bar-fill: " + color + ";");
                index++;
            }
        });


        // Agregar ambas gráficas al contenedor visual
        statsContent.getChildren().addAll(pieChart, barChart);
        statsSection.getChildren().addAll(createSectionHeader("Estadísticas"), statsContent);

        return statsSection;

    }

    // ✅ MÉTODOS UTILITARIOS FINALES
    private Stage getCurrentStage() {
        try {
            if (statusLabel != null) {
                return (Stage) statusLabel.getScene().getWindow();
            }
            return Window.getWindows().stream()
                    .filter(Window::isShowing)
                    .filter(window -> window instanceof Stage)
                    .map(window -> (Stage) window)
                    .findFirst()
                    .orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    private void showAlert(String title, String message, Alert.AlertType type) {
        Platform.runLater(() -> {
            Alert alert = new Alert(type);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    public void refreshDashboard() {
        CompletableFuture.runAsync(() -> {
            try {
                User.DashboardData data = User.getDashboardData();
                cachedTotalUsers = data.totalUsers - 1;
                cachedActiveStudents = data.activeStudents;

                Platform.runLater(() -> {
                    updateCard(usersCard, String.valueOf(cachedTotalUsers), cachedActiveStudents + " estudiantes activos");
                    updateCard(activeCalendarsCard, String.valueOf(data.activeCalendars), "Calendarios disponibles");
                    updateCard(eventsForThisMonthCard, String.valueOf(data.eventsThisMonth), data.upcomingEvents + " eventos próximos");
                    setStatus("Dashboard actualizado");
                });

            } catch (Exception e) {
                Platform.runLater(() -> setStatus("Error actualizando dashboard: " + e.getMessage()));
            }
        });
    }

    private void startClock() {
        if (clockTimeline != null) clockTimeline.stop();

        clockTimeline = new Timeline(
                new KeyFrame(Duration.seconds(0), e -> updateClock()),
                new KeyFrame(Duration.seconds(1))
        );
        clockTimeline.setCycleCount(Timeline.INDEFINITE);
        clockTimeline.play();
    }

    private void updateClock() {
        if (clockLabel != null) {
            LocalTime time = TimeService.getInstance().now().toLocalTime();
            Platform.runLater(() -> clockLabel.setText(time.format(timeFormatter)));
        }
    }

    private void returnToLogin() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/login.fxml"));
            Parent loginRoot = loader.load();

            Stage stage = (Stage) contentArea.getScene().getWindow();
            Rectangle2D screenBounds = Screen.getPrimary().getVisualBounds();
            double width = Math.min(1100, screenBounds.getWidth() * 0.95);
            double height = Math.min(700, screenBounds.getHeight() * 0.95);

            Scene loginScene = new Scene(loginRoot, width, height);
            loginScene.getStylesheets().add(getClass().getResource("/css/login.css").toExternalForm());

            stage.setTitle("Ithera");
            stage.setScene(loginScene);
            stage.setMinWidth(800);
            stage.setMinHeight(600);
            stage.show();
            stage.centerOnScreen();

            cleanup();

        } catch (Exception e) {
            System.err.println("No se pudo volver al login: " + e.getMessage());
        }
    }

    public void cleanup() {
        if (clockTimeline != null) clockTimeline.stop();
        User.setDashboardController(null);
    }

    //DIVISION DE LA TABLA DE CALENDARIOS
    private HBox createCalendarPagination(TableView<Calendar> calendarTable, List<Calendar> calendars) {
        final int itemsPerPage = 10;
        final IntegerProperty currentPageIndex = new SimpleIntegerProperty(0);
        final List<Calendar> masterData = calendars;

        Runnable updatePageData = () -> {
            int startIndex = currentPageIndex.get() * itemsPerPage;
            int endIndex = Math.min(startIndex + itemsPerPage, masterData.size());
            calendarTable.getItems().setAll(masterData.subList(startIndex, endIndex));
            setStatus("Mostrando calendarios " + (startIndex + 1) + " a " + endIndex + " de " + masterData.size());
        };

        Button[] navButtons = {
                createPaginationButton("◀", "/images/arrow-left.png", e -> {
                    if (currentPageIndex.get() > 0) {
                        currentPageIndex.set(currentPageIndex.get() - 1);
                        updatePageData.run();
                    }
                }),
                createPaginationButton("▶", "/images/arrow-right.png", e -> {
                    currentPageIndex.set(currentPageIndex.get() + 1);
                    updatePageData.run();
                })
        };

        Label pageInfoLabel = new Label();

        currentPageIndex.addListener((obs, oldVal, newVal) -> {
            int pageIndex = newVal.intValue();
            navButtons[0].setDisable(pageIndex == 0);
            navButtons[1].setDisable((pageIndex + 1) * itemsPerPage >= masterData.size());
            pageInfoLabel.setText("Página " + (pageIndex + 1) + " de " + ((masterData.size() - 1) / itemsPerPage + 1));
        });

        calendarTable.getItems().clear();
        updatePageData.run();
        int pageIndex = currentPageIndex.get();
        navButtons[0].setDisable(pageIndex == 0);
        navButtons[1].setDisable((pageIndex + 1) * itemsPerPage >= masterData.size());
        pageInfoLabel.setText("Página " + (pageIndex + 1) + " de " + ((masterData.size() - 1) / itemsPerPage + 1));

        HBox paginationBar = new HBox(10, navButtons[0], pageInfoLabel, navButtons[1]);
        paginationBar.setAlignment(Pos.CENTER);
        paginationBar.getStyleClass().add("pagination-bar");

        return paginationBar;
    }

    @FXML
    private void handleCloseButton() {
        Platform.exit();
        System.exit(0);
    }

    @FXML private Button createButton;
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

            // Establecer tamaño fijo para evitar problemas de visibilidad
            dialogStage.setWidth(600);
            dialogStage.setHeight(500);

            // Centrar el diálogo manualmente
            if (parentStage != null) {
                dialogStage.setX(parentStage.getX() + (parentStage.getWidth() - 600) / 2);
                dialogStage.setY(parentStage.getY() + (parentStage.getHeight() - 500) / 2);
            }

            dialogStage.showAndWait();

        } catch (IOException e) {
            System.err.println("Error abriendo evento específico: " + e.getMessage());
            e.printStackTrace();
            showAlert("Error", "No se pudo abrir el evento:\n" + e.getMessage(), Alert.AlertType.ERROR);
        }
    }
}