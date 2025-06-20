package com.utez.calendario;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class CalendarMonth extends Application {

    @Override
    public void start(Stage primaryStage) {

        try {

            // Cargar el archivo FXML
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/calendar-month.fxml"));
            Parent root = loader.load();


            Scene scene = new Scene(root, 1200, 800);

            // Para poner los estilos CSS
            scene.getStylesheets().add(getClass().getResource("/css/styles-month.css").toExternalForm());


            primaryStage.setTitle("Calendario");
            primaryStage.setScene(scene);
            primaryStage.setMinWidth(1000);
            primaryStage.setMinHeight(700);
            primaryStage.setResizable(true);


            primaryStage.show();

        } catch (Exception e) {

            e.printStackTrace();
            System.err.println("Error al cargar la aplicación: " + e.getMessage());

        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}