package com.utez.calendario.controllers;

import com.utez.calendario.services.AuthService;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.Priority;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.util.Duration;

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

    private Timeline clockTimeline;
    private final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss");

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        startClock();
        setStatus("Sistema listo");
        Platform.runLater(this::handleGeneralView);
    }

    @FXML
    private void handleLogout() {
        setStatus("Cerrando sesión...");
        AuthService.getInstance().logout();
        Platform.runLater(this::returnToLogin);
    }

    @FXML
    private void handleGeneralView() {
        contentArea.getChildren().clear();

        // Crear el contenedor principal para el dashboard
        VBox dashboardContainer = new VBox();
        dashboardContainer.getStyleClass().add("dashboard-container");
        dashboardContainer.setSpacing(20);

        // Crear encabezado de la sección
        HBox sectionHeader = new HBox();
        sectionHeader.getStyleClass().add("section-header");

        Label sectionTitle = new Label("Registros");
        sectionTitle.getStyleClass().add("section-title");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        sectionHeader.getChildren().addAll(sectionTitle, spacer);

        // Crear contenedor para las tarjetas
        HBox summaryCards = new HBox();
        summaryCards.setSpacing(20);
        summaryCards.getStyleClass().add("summary-cards");

        // Crear las tres tarjetas
        VBox usersCard = createSummaryCard(
                "users-card",
                "Usuarios Totales",
                "6",
                "+xd% desde el mes pasado"
        );

        VBox activeCalendarsCard = createSummaryCard(
                "activeCalendars-card",
                "Calendarios Activos",
                "3",
                "1000 próximos esta semana"
        );

        VBox eventsForThisMonthCard = createSummaryCard(
                "eventsForThisMonth-card",
                "Eventos este mes",
                "8",
                "2 eventos waos"
        );

        // Añadir las tarjetas al contenedor
        summaryCards.getChildren().addAll(usersCard, activeCalendarsCard, eventsForThisMonthCard);

        // Añadir todos los elementos al contenedor principal
        dashboardContainer.getChildren().addAll(sectionHeader, summaryCards);

        // Añadir el contenedor al área de contenido
        contentArea.getChildren().add(dashboardContainer);
        setStatus("Vista general");
    }

    @FXML
    private void handleUserManagement() {
        setStatus("Administración de usuarios");
        loadView("user-management.fxml");
    }

    @FXML
    private void handleCalendarManagement() {
        setStatus("Administración de calendarios");
        loadView("calendar-management.fxml");
    }

    @FXML
    private void handleStatistics() {
        setStatus("Estadísticas");
        loadView("statistics.fxml");
    }

    private void loadView(String fxmlFile) {
        try {
            Node node = FXMLLoader.load(getClass().getResource("/fxml/" + fxmlFile));
            contentArea.getChildren().setAll(node);
        } catch (IOException e) {
            setStatus("No se pudo cargar la vista: " + fxmlFile);
        }
    }

    private void setStatus(String message) {
        if (statusLabel != null) {
            statusLabel.setText(message);
        }
    }

    private void startClock() {
        clockTimeline = new Timeline(
                new KeyFrame(Duration.seconds(0), e -> updateClock()),
                new KeyFrame(Duration.seconds(1))
        );
        clockTimeline.setCycleCount(Timeline.INDEFINITE);
        clockTimeline.play();
    }

    private void updateClock() {
        if (clockLabel != null) {
            clockLabel.setText(LocalTime.now().format(timeFormatter));
        }
    }


    //Crea una tarjeta de resumen con los datos proporcionados
    private VBox createSummaryCard(String styleClass, String title, String value, String subtitle) {
        VBox card = new VBox();
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
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/login.fxml"));
            Parent loginRoot = loader.load();
            Stage stage = (Stage) contentArea.getScene().getWindow();
            stage.setScene(new Scene(loginRoot));
            stage.centerOnScreen();
        } catch (Exception e) {
            System.err.println("No se pudo volver al login: " + e.getMessage());
        }
    }
}