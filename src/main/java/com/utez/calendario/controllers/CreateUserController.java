package com.utez.calendario.controllers;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Hyperlink;
import javafx.scene.image.Image;
import javafx.stage.Screen;
import javafx.stage.Stage;

import javax.swing.*;
import java.net.URL;
import java.time.LocalDateTime;
import java.util.ResourceBundle;

public class CreateUserController implements Initializable {

    LoginController loginController = new LoginController();

    @FXML
    private Hyperlink loginLink;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        System.out.println("=== UTEZ CALENDAR - SISTEMA DE CREACION DE USUARIO ===");
        System.out.println("Fecha/Hora: " + LocalDateTime.now());
        System.out.println("========================================");

        loginLink.setOnAction(e -> handleReturnToLogin());

    }

    @FXML
    private void handleReturnToLogin() {
        try {
            System.out.println("Ingresando a la creacion de usuario...");

            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/login.fxml"));
            Parent loginRoot = loader.load();

            Rectangle2D screenBounds = Screen.getPrimary().getVisualBounds();
            double width = Math.min(300, screenBounds.getWidth() * 0.95);
            double height = Math.min(650, screenBounds.getHeight() * 0.95);

            Stage loginStage = new Stage();
            Scene loginScene = new Scene(loginRoot, width, height);

            try {
                loginScene.getStylesheets().add(getClass().getResource("/css/login.css").toExternalForm());
                loginScene.getStylesheets().add(getClass().getResource("/css/window-styles.css").toExternalForm());
            } catch (Exception e) {
                System.out.println("No se pudieron cargar los estilos del calendario");
            }

            loginStage.initStyle(javafx.stage.StageStyle.UNDECORATED);
            loginStage.setScene(loginScene);
            loginStage.setMinWidth(1000);
            loginStage.setMinHeight(600);

            try {
                Image icon = new Image(getClass().getResourceAsStream("/images/logo.png"));
                loginStage.getIcons().add(icon);
            } catch (Exception e) {
                System.out.println("No se pudo cargar el ícono del calendario: " + e.getMessage());
            }

            loginStage.show();
            loginStage.centerOnScreen();

            Stage currentStage = (Stage) loginLink.getScene().getWindow();
            currentStage.close();

            System.out.println("Login cargado correctamente");

        } catch (Exception e) {
            System.err.println("ERROR al cargar el login: " + e.getMessage());
            e.printStackTrace();

            Alert errorAlert = new Alert(Alert.AlertType.ERROR);
            errorAlert.setTitle("Error");
            errorAlert.setHeaderText("No se pudo cargar el login");
            errorAlert.setContentText("Error: " + e.getMessage());
            errorAlert.showAndWait();


        }
    }

}
