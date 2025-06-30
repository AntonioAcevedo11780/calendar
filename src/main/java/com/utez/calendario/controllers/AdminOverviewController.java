package com.utez.calendario.controllers;

import com.utez.calendario.services.AuthService;
import com.utez.calendario.models.User;

import javafx.geometry.Rectangle2D;
import javafx.stage.Screen;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.Priority;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.util.Duration;
import javafx.geometry.Pos;

import javafx.scene.control.Button;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

import java.awt.*;
import java.util.List;

import java.io.IOException;
import java.net.URL;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ResourceBundle;

public class AdminOverviewController implements Initializable {

    @FXML
    private StackPane contentArea;
    @FXML
    private Label statusLabel;
    @FXML
    private Label clockLabel;
    @FXML
    private SplitPane splitPane;

    private Timeline clockTimeline;
    private final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss");

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        try {
            startClock();

            // Hacer el SplitPane no movible
            Platform.runLater(() -> {
                if (splitPane != null) {
                    splitPane.getDividers().get(0).positionProperty().addListener((obs, oldPos, newPos) -> {
                        splitPane.setDividerPositions(0.22);
                    });
                }
                handleGeneralView();
            });
        } catch (Exception e) {
            System.err.println("Error en inicialización: " + e.getMessage());
            setStatus("Error en inicialización");
        }
    }

    private ImageView createIcon(String path) {

        ImageView imageView = new ImageView(new Image(getClass().getResourceAsStream(path)));

        imageView.setFitHeight(30);
        imageView.setFitWidth(30);
        imageView.setPreserveRatio(true);

        return imageView;

    }

    @FXML
    private void handleLogout() {
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

    @FXML
    private void handleGeneralView() {
        try {
            contentArea.getChildren().clear();

            // Crear ScrollPane para permitir desplazamiento
            ScrollPane scrollPane = new ScrollPane();
            scrollPane.setFitToWidth(true);
            scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
            scrollPane.getStyleClass().add("dashboard-scroll");

            // Contenedor principal para todas las secciones
            VBox mainContainer = new VBox();
            mainContainer.setSpacing(30);
            mainContainer.setAlignment(Pos.TOP_CENTER);
            mainContainer.getStyleClass().add("dashboard-container");

            // ---------- SECCIÓN 1: TARJETAS RESUMEN ----------
            VBox summarySection = createSummarySection();

            // ---------- SECCIÓN 2: ADMINISTRACIÓN DE USUARIOS ----------
            VBox userManagementSection = createUserManagementSection();

            // ---------- SECCIÓN 3: ADMINISTRACIÓN DE CALENDARIOS ----------
            VBox calendarManagementSection = createCalendarManagementSection();

            // ---------- SECCIÓN 4: ESTADÍSTICAS ----------
            VBox statisticsSection = createStatisticsSection();

            // Añadir todas las secciones al contenedor principal
            mainContainer.getChildren().addAll(
                    summarySection,
                    userManagementSection,
                    calendarManagementSection,
                    statisticsSection
            );

            // Configurar el ScrollPane con el contenedor principal
            scrollPane.setContent(mainContainer);
            contentArea.getChildren().add(scrollPane);

        } catch (Exception e) {
            System.err.println("Error al cargar vista general: " + e.getMessage());
            setStatus("Error al cargar vista");
        }
    }

    private VBox createSummarySection() {
        VBox summarySection = new VBox();
        summarySection.setSpacing(20);
        summarySection.setAlignment(Pos.CENTER);

        // Encabezado de las tarjetas
        HBox summaryHeader = new HBox();
        summaryHeader.getStyleClass().add("section-header");
        summaryHeader.setAlignment(Pos.CENTER);

        Label summaryTitle = new Label("Vista General");
        summaryTitle.getStyleClass().add("section-title");

        Region spacer1 = new Region();
        HBox.setHgrow(spacer1, Priority.ALWAYS);

        summaryHeader.getChildren().addAll(summaryTitle, spacer1);

        // Contenedor para las tarjetas
        HBox summaryCards = new HBox();
        summaryCards.setSpacing(20);
        summaryCards.setAlignment(Pos.CENTER);
        summaryCards.getStyleClass().add("summary-cards");

        // Obtener datos de la base de datos
        int totalUsers;
        int activeCalendars;
        int eventsThisMonth;
        int upcomingEvents;

        try {
            totalUsers = User.getTotalUsersCount();
            activeCalendars = User.getActiveCalendarsCount();
            eventsThisMonth = User.getEventsForCurrentMonth();
            upcomingEvents = User.getUpcomingEventsCount();
            setStatus("Datos cargados correctamente");
        } catch (Exception e) {
            totalUsers = 0;
            activeCalendars = 0;
            eventsThisMonth = 0;
            upcomingEvents = 0;
            setStatus("Error al cargar datos: " + e.getMessage());
        }

        // Crear las tres tarjetas
        VBox usersCard = createSummaryCard(
                "users-card",
                "Usuarios Totales",
                String.valueOf(totalUsers),
                User.getActiveStudentsCount() + " estudiantes activos"
        );

        VBox activeCalendarsCard = createSummaryCard(
                "activeCalendars-card",
                "Calendarios Activos",
                String.valueOf(activeCalendars),
                "Calendarios disponibles"
        );

        VBox eventsForThisMonthCard = createSummaryCard(
                "eventsForThisMonth-card",
                "Eventos este mes",
                String.valueOf(eventsThisMonth),
                upcomingEvents + " eventos próximos"
        );

        summaryCards.getChildren().addAll(usersCard, activeCalendarsCard, eventsForThisMonthCard);
        summarySection.getChildren().addAll(summaryHeader, summaryCards);

        return summarySection;
    }

    private VBox createUserManagementSection() {
        VBox userManagementSection = new VBox();
        userManagementSection.setSpacing(20);
        userManagementSection.setAlignment(Pos.CENTER);

        HBox userHeader = new HBox();
        userHeader.getStyleClass().add("section-header");
        userHeader.setAlignment(Pos.CENTER);

        Label userTitle = new Label("Administración de Usuarios");
        userTitle.getStyleClass().add("section-title");

        Region spacer2 = new Region();
        HBox.setHgrow(spacer2, Priority.ALWAYS);

        userHeader.getChildren().addAll(userTitle, spacer2);

        // Crear tabla de usuarios
        TableView<User> userTable = new TableView<>();
        userTable.getStyleClass().add("user-table");
        userTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        // Definir columnas
        TableColumn<User, String> matriculaColumn = new TableColumn<>("Matrícula");
        matriculaColumn.setCellValueFactory(new PropertyValueFactory<>("matricula"));

        TableColumn<User, String> nameColumn = new TableColumn<>("Nombre");
        nameColumn.setCellValueFactory(cellData ->
                new SimpleStringProperty(cellData.getValue().getFullName()));

        TableColumn<User, String> emailColumn = new TableColumn<>("Email");
        emailColumn.setCellValueFactory(new PropertyValueFactory<>("email"));

        TableColumn<User, String> roleColumn = new TableColumn<>("Rol");
        roleColumn.setCellValueFactory(cellData ->
                new SimpleStringProperty(cellData.getValue().getRole().getDisplayName()));

        TableColumn<User, Boolean> activeColumn = new TableColumn<>("Activo");
        activeColumn.setCellValueFactory(cellData ->
                new SimpleBooleanProperty(cellData.getValue().isActive()));
        activeColumn.setCellFactory(col -> new TableCell<User, Boolean>() {
            @Override
            protected void updateItem(Boolean item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : (item ? "Sí" : "No"));
            }
        });

        TableColumn<User, Void> actionsColumn = new TableColumn<>("Acciones");
        actionsColumn.setCellFactory(col -> new TableCell<User, Void>() {
            private final Button toggleButton = new Button();
            private final HBox pane = new HBox(5, toggleButton);

            {
                pane.setAlignment(Pos.CENTER);

                toggleButton.setOnAction(event -> {
                    User user = getTableView().getItems().get(getIndex());
                    boolean success = user.toggleActive();

                    if (success) {
                        // Actualizar el texto del botón según el nuevo estado
                        updateButtonText(user);

                        // Refrescar la tabla para mostrar el nuevo estado
                        getTableView().refresh();

                        setStatus(user.isActive()
                                ? "Usuario activado: " + user.getFullName()
                                : "Usuario desactivado: " + user.getFullName());
                    } else {
                        setStatus("Error al cambiar estado del usuario: " + user.getFullName());
                    }
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);

                if (empty) {
                    setGraphic(null);
                } else {
                    User user = getTableView().getItems().get(getIndex());
                    updateButtonText(user);
                    setGraphic(pane);
                }
            }

            private void updateButtonText(User user) {
                if (user.isActive()) {
                    toggleButton.setText("Desactivar");
                    toggleButton.getStyleClass().remove("activate-button");
                    toggleButton.getStyleClass().add("deactivate-button");
                } else {
                    toggleButton.setText("Activar");
                    toggleButton.getStyleClass().remove("deactivate-button");
                    toggleButton.getStyleClass().add("activate-button");
                }
            }
        });

        // Añadir columnas a la tabla
        userTable.getColumns().addAll(
                matriculaColumn, nameColumn, emailColumn,
                roleColumn, activeColumn, actionsColumn
        );

        // Variables para la paginación
        final int itemsPerPage = 10;
        final IntegerProperty currentPageIndex = new SimpleIntegerProperty(0);
        final List<User> masterData = User.getAllUsers();

        // Función para actualizar los datos mostrados
        Runnable updatePageData = () -> {
            int startIndex = currentPageIndex.get() * itemsPerPage;
            int endIndex = Math.min(startIndex + itemsPerPage, masterData.size());

            userTable.getItems().clear();
            userTable.getItems().addAll(masterData.subList(startIndex, endIndex));

            setStatus("Mostrando usuarios " + (startIndex + 1) + " a " + endIndex +
                    " de " + masterData.size());
        };

        // Crear botones de navegación
        Button prevButton = new Button();
        prevButton.getStyleClass().addAll("pagination-button", "icon-button");
        prevButton.setGraphic(createIcon("/images/arrow-left.png"));
        prevButton.setDisable(true);
        prevButton.setOnAction(e -> {
            currentPageIndex.set(currentPageIndex.get() - 1);
            updatePageData.run();
        });

        Button nextButton = new Button();
        nextButton.getStyleClass().addAll("pagination-button", "icon-button");
        nextButton.setGraphic(createIcon("/images/arrow-right.png"));
        nextButton.setOnAction(e -> {
            currentPageIndex.set(currentPageIndex.get() + 1);
            updatePageData.run();
        });

        Label pageInfoLabel = new Label();

        // Listener para actualizar estado de botones
        currentPageIndex.addListener((obs, oldVal, newVal) -> {
            int pageIndex = newVal.intValue();
            prevButton.setDisable(pageIndex == 0);
            nextButton.setDisable((pageIndex + 1) * itemsPerPage >= masterData.size());
            pageInfoLabel.setText("Página " + (pageIndex + 1) + " de " +
                    ((masterData.size() - 1) / itemsPerPage + 1));
        });

        // Barra de paginación
        HBox paginationBar = new HBox(10);
        paginationBar.setAlignment(Pos.CENTER);
        paginationBar.getStyleClass().add("pagination-bar");
        paginationBar.getChildren().addAll(prevButton, pageInfoLabel, nextButton);

        // Inicializar con la primera página
        updatePageData.run();
        pageInfoLabel.setText("Página 1 de " + ((masterData.size() - 1) / itemsPerPage + 1));

        // Contenedor para la tabla y la paginación
        VBox userContent = new VBox(10);
        userContent.getStyleClass().add("table-container");
        userContent.getChildren().addAll(userTable, paginationBar);

        userManagementSection.getChildren().addAll(userHeader, userContent);
        return userManagementSection;
    }

    private VBox createCalendarManagementSection() {
        VBox calendarManagementSection = new VBox();
        calendarManagementSection.setSpacing(20);
        calendarManagementSection.setAlignment(Pos.CENTER);

        HBox calendarHeader = new HBox();
        calendarHeader.getStyleClass().add("section-header");
        calendarHeader.setAlignment(Pos.CENTER);

        Label calendarTitle = new Label("Administración de Calendarios");
        calendarTitle.getStyleClass().add("section-title");

        Region spacer3 = new Region();
        HBox.setHgrow(spacer3, Priority.ALWAYS);

        calendarHeader.getChildren().addAll(calendarTitle, spacer3);

        // Contenido de calendarios
        VBox calendarContent = new VBox();
        calendarContent.getStyleClass().add("table-container");
        calendarContent.setAlignment(Pos.CENTER);

        Label calendarPlaceholder = new Label("Aquí se mostrarán los calendarios disponibles");
        calendarPlaceholder.setStyle("-fx-font-size: 16px; -fx-padding: 40px;");
        calendarContent.getChildren().add(calendarPlaceholder);

        calendarManagementSection.getChildren().addAll(calendarHeader, calendarContent);
        return calendarManagementSection;
    }

    private VBox createStatisticsSection() {
        VBox statisticsSection = new VBox();
        statisticsSection.setSpacing(20);
        statisticsSection.setAlignment(Pos.CENTER);

        HBox statisticsHeader = new HBox();
        statisticsHeader.getStyleClass().add("section-header");
        statisticsHeader.setAlignment(Pos.CENTER);

        Label statisticsTitle = new Label("Estadísticas");
        statisticsTitle.getStyleClass().add("section-title");

        Region spacer4 = new Region();
        HBox.setHgrow(spacer4, Priority.ALWAYS);

        statisticsHeader.getChildren().addAll(statisticsTitle, spacer4);

        // Contenido de estadísticas
        VBox statisticsContent = new VBox();
        statisticsContent.getStyleClass().add("chart-container");
        statisticsContent.setAlignment(Pos.CENTER);

        Label statsPlaceholder = new Label("Aquí se mostrarán las estadísticas del sistema");
        statsPlaceholder.setStyle("-fx-font-size: 16px; -fx-padding: 40px;");
        statisticsContent.getChildren().add(statsPlaceholder);

        statisticsSection.getChildren().addAll(statisticsHeader, statisticsContent);
        return statisticsSection;
    }

    private void setStatus(String message) {
        if (statusLabel != null) {
            Platform.runLater(() -> statusLabel.setText(message));
        }
    }

    private void startClock() {
        if (clockTimeline != null) {
            clockTimeline.stop();
        }

        clockTimeline = new Timeline(
                new KeyFrame(Duration.seconds(0), e -> updateClock()),
                new KeyFrame(Duration.seconds(1))
        );
        clockTimeline.setCycleCount(Timeline.INDEFINITE);
        clockTimeline.play();
    }

    private void updateClock() {
        if (clockLabel != null) {
            Platform.runLater(() -> clockLabel.setText(LocalTime.now().format(timeFormatter)));
        }
    }

    // Crea una tarjeta de resumen con los datos proporcionados
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

    private void returnToLogin() {
        try {
            System.out.println("Regresando al login...");

            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/login.fxml"));
            Parent loginRoot = loader.load();

            Stage stage = (Stage) contentArea.getScene().getWindow();

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

            cleanup();

            System.out.println("Login cargado exitosamente");

        } catch (Exception e) {
            System.err.println("No se pudo volver al login: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Método para limpiar recursos al cerrar
    public void cleanup() {
        if (clockTimeline != null) {
            clockTimeline.stop();
        }
    }
}