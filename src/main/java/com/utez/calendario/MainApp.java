package com.utez.calendario;

import com.utez.calendario.config.DatabaseConfig;
import com.utez.calendario.services.EventService;
import com.utez.calendario.services.MailService;
import jakarta.mail.AuthenticationFailedException;
import jakarta.mail.MessagingException;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Rectangle2D;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.text.Font;
import javafx.stage.Screen;
import javafx.stage.Stage;

public class MainApp extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception {

        loadInriaSansFonts();

        Parent root = FXMLLoader.load(getClass().getResource("/fxml/login.fxml"));

        Rectangle2D screenBounds = Screen.getPrimary().getVisualBounds();
        double width = Math.min(1100, screenBounds.getWidth() * 0.95);
        double height = Math.min(700, screenBounds.getHeight() * 0.95);

        Scene scene = new Scene(root, width, height);
        scene.getStylesheets().add(getClass().getResource("/css/login.css").toExternalForm());

        primaryStage.setTitle("Ithera");
        primaryStage.setScene(scene);
        primaryStage.setMinWidth(800);
        primaryStage.setMinHeight(600);
        primaryStage.show();
        primaryStage.centerOnScreen();

        // Agregar handler para cerrar correctamente los recursos
        primaryStage.setOnCloseRequest(event -> {
            // Cerrar el pool de conexiones
            DatabaseConfig.closeDataSource();
            // Apagar los servicios con ExecutorService
            EventService.getInstance().shutdown();
            System.out.println("Cerrando recursos de la aplicación...");
        });
    }

    private void loadInriaSansFonts() {
        try {
            Font.loadFont(getClass().getResourceAsStream("/fonts/InriaSans-Regular.ttf"), 12);
            Font.loadFont(getClass().getResourceAsStream("/fonts/InriaSans-Bold.ttf"), 12);
            Font.loadFont(getClass().getResourceAsStream("/fonts/InriaSans-Italic.ttf"), 12);
            Font.loadFont(getClass().getResourceAsStream("/fonts/InriaSans-BoldItalic.ttf"), 12);
            Font.loadFont(getClass().getResourceAsStream("/fonts/InriaSans-Light.ttf"), 12);
            Font.loadFont(getClass().getResourceAsStream("/fonts/InriaSans-LightItalic.ttf"), 12);
            Font.loadFont(getClass().getResourceAsStream("/fonts/InriaSans-SemiBold.ttf"), 12);

            System.out.println("Fuentes Inria Sans cargadas exitosamente");
        } catch (Exception e) {
            System.err.println("Error al cargar las fuentes Inria Sans: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static volatile MailService emailService;

    public static synchronized MailService getEmailService() {
        if (emailService == null) {
            String host = "smtp.gmail.com";
            int port = 587;
            String user = System.getenv("SMTP_USER");
            String pass = System.getenv("SMTP_PASSWORD");
            String senderName = "Equipo Ithera Calendar";

            if (user == null || user.isEmpty()) {
                user = "ithera117@gmail.com";
            }

            if (pass == null || pass.isEmpty()) {
                pass = "qlsd dztm iquq zygp"; // Contraseña de aplicación
            }

            emailService = new MailService(host, port, user, pass, senderName);
        }
        return emailService;
    }


    public static void main(String[] args) {
        launch(args);
    }
}