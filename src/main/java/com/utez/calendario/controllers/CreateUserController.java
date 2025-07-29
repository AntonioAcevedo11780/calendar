package com.utez.calendario.controllers;

import com.sun.tools.javac.Main;
import com.utez.calendario.MainApp;
import com.utez.calendario.models.User;
import com.utez.calendario.services.AuthService;
import com.utez.calendario.services.MailService;
import jakarta.mail.MessagingException;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;

import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.layout.VBox;
import javafx.stage.Screen;
import javafx.stage.Stage;

import java.net.URL;
import java.time.LocalDateTime;
import java.util.ResourceBundle;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class CreateUserController implements Initializable {

    private AuthService authService;

    @FXML
    private Hyperlink loginLink;

    @FXML
    private Button createAccountButton;

    @FXML
    private TextField emailField;

    @FXML
    private PasswordField passwordField;

    @FXML
    private PasswordField confirmPasswordField;

    @FXML
    private VBox verificationContainer;

    @FXML
    private TextField code1, code2, code3, code4;

    @FXML
    private Button verifyButton;

    @FXML
    private Label errorLabel;

    @FXML
    private TextField firstNamesField;

    @FXML
    private TextField lastNameField;

    // Servicio de email
    private MailService emailService;

    // Código de verificación generado
    private String verificationCode;

    // Ejecutor para manejar hilos
    private Executor executor;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        System.out.println("=== UTEZ CALENDAR - SISTEMA DE CREACION DE USUARIO ===");
        System.out.println("Fecha/Hora: " + LocalDateTime.now());
        System.out.println("========================================");

        //Inicializa los servicios
        authService = AuthService.getInstance();
        emailService = MainApp.getEmailService();

        executor = Executors.newCachedThreadPool(runnable -> {
            Thread t = new Thread(runnable);
            t.setDaemon(true); // Permite que la aplicación se cierre aunque el hilo esté activo
            return t;
        });

        // Configurar la validación del código
        setupCodeValidation();

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
    // Manejador para el botón "Crear cuenta"
    @FXML
    private void handleCreateAccount() {
        // Validar campos
        String firstName = firstNamesField.getText().trim();
        String lastName = lastNameField.getText().trim();
        String email = emailField.getText().trim();
        String password = passwordField.getText().trim();
        String confirmPassword = confirmPasswordField.getText().trim();

        // Verificar si el email ya existe
        User usr = new User();

        if (usr.searchEmail(email)) {
            // Mostrar alerta y limpiar campo
            showError("El email ya está registrado. Por favor, use otro email.");

            emailField.setText("");
            emailField.requestFocus();

            return;
        }

        if (email.isEmpty() || password.isEmpty() || confirmPassword.isEmpty() || firstName.isEmpty() || lastName.isEmpty()) {
            showError("Todos los campos son obligatorios");
            return;
        }

        if (!password.equals(confirmPassword)) {
            showError("Las contraseñas no coinciden");
            return;
        }

        if (!isValidEmail(email)) {
            showError("Ingresa un correo electrónico válido");
            return;
        }

        // Generar código de verificación
        verificationCode = MailService.generateVerificationCode();

        // Enviar correo en segundo plano
        executor.execute(() -> {
            try {
                MailService emailService = MainApp.getEmailService();
                emailService.sendVerificationEmail(email, verificationCode);

                System.out.println("Código de verificacion: " + verificationCode);

                // Actualizar UI en el hilo de JavaFX
                Platform.runLater(() -> {

                    firstNamesField.setVisible(false);
                    lastNameField.setVisible(false);
                    emailField.setVisible(false);
                    passwordField.setVisible(false);
                    confirmPasswordField.setVisible(false);

                    createAccountButton.setVisible(false);

                    verificationContainer.setVisible(true);
                    verificationContainer.setManaged(true);

                    errorLabel.setVisible(false);

                    code1.requestFocus();

                    verificationContainer.requestLayout();

                });

            } catch (MessagingException e) {
                Platform.runLater(() -> {
                    showError("Error enviando el correo: " + e.getMessage());
                    e.printStackTrace();
                });
            }
        });
    }

    // Manejador para el botón "Verificar"
    @FXML
    private void handleVerifyCode() {
        String enteredCode = code1.getText() + code2.getText() + code3.getText() + code4.getText();

        if (enteredCode.equals(verificationCode)) {
            // Código correcto: Registrar usuario
            registerUser();
        } else {

            showError("Código de verificación incorrecto");

            firstNamesField.setVisible(true);
            lastNameField.setVisible(true);
            emailField.setVisible(true);
            passwordField.setVisible(true);
            confirmPasswordField.setVisible(true);
            createAccountButton.setVisible(true);

            verificationContainer.setVisible(false);
            verificationContainer.setManaged(false);

            errorLabel.setVisible(false);

            code1.clear();
            code2.clear();
            code3.clear();
            code4.clear();
            code1.requestFocus();

        }
    }

    // Configurar la validación de los campos de código
    private void setupCodeValidation() {
        // Limitar a 1 carácter por campo y auto-enfocar el siguiente
        code1.textProperty().addListener((obs, old, newVal) -> limitAndFocus(newVal, code1, code2));
        code2.textProperty().addListener((obs, old, newVal) -> limitAndFocus(newVal, code2, code3));
        code3.textProperty().addListener((obs, old, newVal) -> limitAndFocus(newVal, code3, code4));
        code4.textProperty().addListener((obs, old, newVal) -> {
            if (!newVal.isEmpty()) {
                code4.setText(newVal.substring(0, 1));
            }
        });
    }

    private void limitAndFocus(String newValue, TextField current, TextField next) {
        if (newValue.length() > 1) {
            current.setText(newValue.substring(0, 1));
        }
        if (newValue.length() == 1) {
            next.requestFocus();
        }
    }

    private boolean isValidEmail(String email) {
        return email.matches("^[\\w-\\.]+@([\\w-]+\\.)+[\\w-]{2,4}$");
    }

    private void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setVisible(true);
    }

    private void registerUser() {

        String firstName = firstNamesField.getText();
        String lastName = lastNameField.getText();
        String email = emailField.getText();
        String password = passwordField.getText();

        try {
            AuthService.registerUser(firstName, lastName, email, password);

            System.out.println("Usuario registrado exitosamente: " + firstName + " " + lastName + " (" + email + ")");

            Alert successAlert = new Alert(Alert.AlertType.INFORMATION);
            successAlert.setTitle("Registro exitoso");
            successAlert.setHeaderText("Cuenta creada exitosamente");
            successAlert.setContentText("Ahora puedes iniciar sesión con tu nueva cuenta.");
            successAlert.showAndWait();

            // Redirigir al login
            handleReturnToLogin();

        } catch (Exception e) {
            // Mostrar error detallado
            String errorDetails = "Error: " + e.getMessage() + "\n\n";

            if (e.getCause() != null) {
                errorDetails += "Causa: " + e.getCause().getMessage();
            }

            Alert errorAlert = new Alert(Alert.AlertType.ERROR);
            errorAlert.setTitle("Error de registro");
            errorAlert.setHeaderText("No se pudo completar el registro");
            errorAlert.setContentText(errorDetails);
            errorAlert.showAndWait();

            e.printStackTrace();
        }
    }


}
