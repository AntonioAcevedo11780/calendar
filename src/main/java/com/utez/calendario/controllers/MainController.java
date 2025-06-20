package com.utez.calendario.controllers;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import java.io.IOException;

/**
 * Controlador principal para manejar la navegación entre vistas
 */
public class MainController {

    private static MainController instance;
    private Stage primaryStage;
    private Stage calendarStage;

    private MainController() {}

    public static MainController getInstance() {
        if (instance == null) {
            instance = new MainController();
        }
        return instance;
    }

    public void setPrimaryStage(Stage stage) {
        this.primaryStage = stage;

        // Configurar el ícono de la aplicación
        setStageIcon(stage);
    }

    /**
     * Icono global     */
    private void setStageIcon(Stage stage) {
        try {
            Image icon = new Image(getClass().getResourceAsStream("/images/logo.png"));
            stage.getIcons().add(icon);
        } catch (Exception e) {
            System.err.println("No se pudo cargar el ícono: " + e.getMessage());
        }
    }

    /**
     * Navegar a la vista de login
     */
    public void showLoginView() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/login.fxml"));
            Parent root = loader.load();

            Scene scene = new Scene(root, 1200, 700);
            scene.getStylesheets().add(getClass().getResource("/css/login.css").toExternalForm());

            // Configurar con barra de título
            primaryStage.setTitle("Ithera");
            primaryStage.setScene(scene);
            primaryStage.setResizable(false);
            primaryStage.centerOnScreen();

            // Mostrar la ventana solo si no está visible
            if (!primaryStage.isShowing()) {
                primaryStage.show();
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Navegar a la vista del calendario
     */
    public void showCalendarView() {
        try {
            // Cerrar la ventana de login
            if (primaryStage != null) {
                primaryStage.close();
            }

            // Crear nueva ventana para el calendario
            calendarStage = new Stage();
            setStageIcon(calendarStage);

            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/calendar-month.fxml"));
            Parent root = loader.load();

            Scene scene = new Scene(root, 1200, 800);
            scene.getStylesheets().add(getClass().getResource("/css/styles-month.css").toExternalForm());

            // Configurar el calendario como ventana normal y movible
            calendarStage.initStyle(StageStyle.DECORATED);
            calendarStage.setTitle("Ithera - Calendar");
            calendarStage.setScene(scene);
            calendarStage.setResizable(true);
            calendarStage.centerOnScreen();
            calendarStage.show();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Cerrar sesión y volver al login
     */
    public void logout() {
        // Cerrar ventana del calendario
        if (calendarStage != null) {
            calendarStage.close();
        }

        // Crear nueva ventana de login
        primaryStage = new Stage();
        setStageIcon(primaryStage);
        showLoginView();
    }

    /**
     * Cerrar la aplicación
     */
    public void closeApplication() {
        if (primaryStage != null) {
            primaryStage.close();
        }
        if (calendarStage != null) {
            calendarStage.close();
        }
    }
}