package com.utez.calendario.controllers;

import com.utez.calendario.services.AuthService;
import com.utez.calendario.models.User;
import com.utez.calendario.services.EventService;
import com.utez.calendario.services.TimeService;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Rectangle2D;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.image.Image;
import javafx.stage.Screen;
import javafx.stage.Stage;

import java.net.URL;
import java.time.LocalDateTime;
import java.util.ResourceBundle;

/**
 * Controlador para la vista del login
 */
public class LoginController implements Initializable {

    @FXML
    private TextField emailField;
    @FXML
    private PasswordField passwordField;
    @FXML
    private Button loginButton;
    @FXML
    private Hyperlink createAccountLink;
    @FXML
    private Label errorLabel;

    private AuthService authService;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        System.out.println("=== UTEZ CALENDAR - SISTEMA DE LOGIN ===");
        System.out.println("Fecha/Hora: " + TimeService.getInstance().now());
        System.out.println("========================================");

        authService = AuthService.getInstance();
        errorLabel.setVisible(false);

        Platform.runLater(() -> {
            configureWindow();
            emailField.requestFocus();
            emailField.setText("20243ds076@utez.edu.mx"); // Prellenado para pruebas
        });

        createAccountLink.setOnAction(e -> handleCreateAccount());
        setupKeyHandling();
        setupFieldListeners();
    }

    private void configureWindow() {
        try {
            Stage stage = (Stage) emailField.getScene().getWindow();
            stage.setTitle("Ithera");

            try {
                Image icon = new Image(getClass().getResourceAsStream("/images/logo.png"));
                stage.getIcons().clear();
                stage.getIcons().add(icon);
            } catch (Exception e) {
                System.out.println("No se pudo cargar el ícono: " + e.getMessage());
            }

            try {
                stage.getScene().getStylesheets().add(getClass().getResource("/css/window-styles.css").toExternalForm());
            } catch (Exception e) {
                System.out.println("No se pudo cargar estilos de ventana");
            }
        } catch (Exception e) {
            System.out.println("Error configurando la ventana: " + e.getMessage());
        }
    }

    private void setupKeyHandling() {
        emailField.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                passwordField.requestFocus();
            }
        });

        passwordField.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                handleLogin();
            }
        });
    }

    private void setupFieldListeners() {
        emailField.textProperty().addListener((obs, oldText, newText) -> hideError());
        passwordField.textProperty().addListener((obs, oldText, newText) -> hideError());
    }

    @FXML
    private void handleLogin() {
        String email = emailField.getText().trim();
        String password = passwordField.getText();

        hideError();

        if (email.isEmpty() || password.isEmpty()) {
            showError("Completa todos los campos.");
            return;
        }

        if (!email.contains("@")) {
            showError("Ingresa un correo electrónico válido.");
            return;
        }

        if (!email.endsWith("@utez.edu.mx")) {
            showError("Debes usar tu correo institucional UTEZ (@utez.edu.mx).");
            return;
        }

        setLoginInProgress(true);

        Platform.runLater(() -> {
            try {
                System.out.println("\n--- PROCESANDO LOGIN ---");
                System.out.println("Email: " + email);
                System.out.println("Hora: " + TimeService.getInstance().now());

                // Verificar si las credenciales son correctas pero la cuenta está desactivada
                if (authService.authenticateOnly(email, password)) {
                    User user = authService.findUserByEmail(email);
                    if (user != null && !user.isActive()) {
                        showError("Tu cuenta está desactivada, favor de contactar con un administrador.");
                        setLoginInProgress(false);
                        return;
                    }

                    // Si las credenciales son correctas y la cuenta está activa
                    if (authService.login(email, password)) {
                        handleSuccessfulLogin();
                    } else {
                        showError("Credenciales incorrectas. Verifica tu correo y contraseña.");
                        setLoginInProgress(false);
                    }
                } else {
                    showError("Credenciales incorrectas. Verifica tu correo y contraseña.");
                    setLoginInProgress(false);
                }
            } catch (Exception e) {
                System.err.println("Error durante la autenticación: " + e.getMessage());
                e.printStackTrace();
                showError("Error de conexión. Verifica la base de datos.");
                setLoginInProgress(false);
            }
        });
    }

    @FXML
    private void handleCreateAccount() {
        try {
            System.out.println("Ingresando a la creacion de usuario...");

            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/create-user.fxml"));
            Parent userCreationRoot = loader.load();

            Rectangle2D screenBounds = Screen.getPrimary().getVisualBounds();

            double width = Math.min(850, screenBounds.getWidth() * 0.95);
            double height = Math.min(1100, screenBounds.getHeight() * 0.95);

            Stage userCreationStage = new Stage();
            Scene userCreationScene = new Scene(userCreationRoot, width, height);

            try {
                userCreationScene.getStylesheets().add(getClass().getResource("/css/create-user.css").toExternalForm());
                userCreationScene.getStylesheets().add(getClass().getResource("/css/window-styles.css").toExternalForm());
            } catch (Exception e) {
                System.out.println("No se pudieron cargar los estilos del calendario");
            }

            userCreationStage.initStyle(javafx.stage.StageStyle.UNDECORATED);
            userCreationStage.setScene(userCreationScene);

            userCreationStage.setMinWidth(1000);
            userCreationStage.setMinHeight(650);

            try {
                Image icon = new Image(getClass().getResourceAsStream("/images/logo.png"));
                userCreationStage.getIcons().add(icon);
            } catch (Exception e) {
                System.out.println("No se pudo cargar el ícono del calendario: " + e.getMessage());
            }

            userCreationStage.show();
            userCreationStage.centerOnScreen();
            addDragFunctionality(userCreationRoot, userCreationStage);

            // Cerrar login
            Stage loginStage = (Stage) emailField.getScene().getWindow();
            loginStage.close();

            System.out.println("Calendario cargado exitosamente");

        } catch (Exception e) {
            System.err.println("ERROR al cargar el calendario: " + e.getMessage());
            e.printStackTrace();

            Alert errorAlert = new Alert(Alert.AlertType.ERROR);
            errorAlert.setTitle("Error");
            errorAlert.setHeaderText("No se pudo cargar el formulario de creación de usuario");
            errorAlert.setContentText("Error: " + e.getMessage());
            errorAlert.showAndWait();

            setLoginInProgress(false);
        }
    }

    private void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setVisible(true);
        errorLabel.setStyle("-fx-text-fill: #e74c3c; -fx-font-weight: bold;");
        System.err.println("ERROR DE LOGIN: " + message);
    }

    private void hideError() {
        if (errorLabel.isVisible()) {
            errorLabel.setVisible(false);
        }
    }

    private void setLoginInProgress(boolean inProgress) {
        loginButton.setDisable(inProgress);
        emailField.setDisable(inProgress);
        passwordField.setDisable(inProgress);
        createAccountLink.setDisable(inProgress);

        if (inProgress) {
            loginButton.setText("Verificando...");
            loginButton.setOpacity(0.7);
        } else {
            loginButton.setText("Iniciar sesión");
            loginButton.setOpacity(1.0);
        }
    }

    private void handleSuccessfulLogin() {
        User user = authService.getCurrentUser();
        if (user != null) {
            System.out.println("\n--- LOGIN EXITOSO ---");
            System.out.println("Usuario: " + user.getFullName());
            System.out.println("Email: " + user.getEmail());
            System.out.println("ID: " + user.getDisplayId());
            System.out.println("Rol: " + user.getRole().getDisplayName());
            System.out.println("Hora login: " + TimeService.getInstance().now());
            System.out.println("----------------------\n");

            // Inicializar los calendarios para el usuario
            EventService.getInstance().initializeUserCalendarsAsync(user.getUserId())
                    .thenRun(() -> {
                        System.out.println("✓ Calendarios inicializados correctamente para: " + user.getUserId());
                    })
                    .exceptionally(ex -> {
                        System.err.println("✗ Error al inicializar calendarios: " + ex.getMessage());
                        return null;
                    });
        }
        clearFields();

        // Verificar el rol del usuario para redirigir a su respectiva wbd
        assert user != null;
        if (user.getRole() == User.Role.ADMIN) {
            navigateToAdminPanel();
        } else {
            navigateToCalendar();
        }
    }

    private void clearFields() {
        passwordField.clear(); // limpiar password y el usuario puesto pa pruebas xD
        hideError();
        setLoginInProgress(false);
    }

    private void navigateToCalendar() {
        try {
            System.out.println("Ingresando al calendario...");

            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/calendar-month.fxml"));
            Parent calendarRoot = loader.load();

            Rectangle2D screenBounds = Screen.getPrimary().getVisualBounds();
            double width = Math.min(1350, screenBounds.getWidth() * 0.95);
            double height = Math.min(750, screenBounds.getHeight() * 0.95);

            Stage calendarStage = new Stage();
            Scene calendarScene = new Scene(calendarRoot, width, height);

            try {
                calendarScene.getStylesheets().add(getClass().getResource("/css/styles-month.css").toExternalForm());
                calendarScene.getStylesheets().add(getClass().getResource("/css/window-styles.css").toExternalForm());
            } catch (Exception e) {
                System.out.println("No se pudieron cargar los estilos del calendario");
            }

            calendarStage.initStyle(javafx.stage.StageStyle.UNDECORATED);
            calendarStage.setScene(calendarScene);
            calendarStage.setMinWidth(1000);
            calendarStage.setMinHeight(600);

            try {
                Image icon = new Image(getClass().getResourceAsStream("/images/logo.png"));
                calendarStage.getIcons().add(icon);
            } catch (Exception e) {
                System.out.println("No se pudo cargar el ícono del calendario: " + e.getMessage());
            }

            calendarStage.show();
            calendarStage.centerOnScreen();
            addDragFunctionality(calendarRoot, calendarStage);

            // Cerrar login
            Stage loginStage = (Stage) emailField.getScene().getWindow();
            loginStage.close();

            System.out.println("Calendario cargado exitosamente");

        } catch (Exception e) {
            System.err.println("ERROR al cargar el calendario: " + e.getMessage());
            e.printStackTrace();

            Alert errorAlert = new Alert(Alert.AlertType.ERROR);
            errorAlert.setTitle("Error");
            errorAlert.setHeaderText("No se pudo cargar el calendario");
            errorAlert.setContentText("Error: " + e.getMessage());
            errorAlert.showAndWait();

            setLoginInProgress(false);
        }
    }

    //Para el panel de Administración
    private void navigateToAdminPanel() {
        try {
            System.out.println("Ingresando al panel de administración...");

            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/admin-overview.fxml"));
            Parent adminRoot = loader.load();

            Rectangle2D screenBounds = Screen.getPrimary().getVisualBounds();
            double width = Math.min(1350, screenBounds.getWidth() * 0.95);
            double height = Math.min(750, screenBounds.getHeight() * 0.95);

            Stage adminStage = new Stage();
            Scene adminScene = new Scene(adminRoot, width, height);

            //Se intentan aplicar los estilos de admin-panel
            try {
                adminScene.getStylesheets().add(getClass().getResource("/css/admin-panel.css").toExternalForm());
                adminScene.getStylesheets().add(getClass().getResource("/css/window-styles.css").toExternalForm());
            } catch (Exception e) {
                System.out.println("No se pudieron cargar los estilos del panel de administración");
            }

            adminStage.initStyle(javafx.stage.StageStyle.UNDECORATED);
            adminStage.setScene(adminScene);
            adminStage.setMinWidth(1000);
            adminStage.setMinHeight(600);

            //Se intenta poner el logo de ITHERA
            try {
                Image icon = new Image(getClass().getResourceAsStream("/images/logo.png"));
                adminStage.getIcons().add(icon);
            } catch (Exception e) {
                System.out.println("No se pudo cargar el ícono del panel de administración: " + e.getMessage());
            }

            adminStage.show();
            adminStage.centerOnScreen();
            addDragFunctionality(adminRoot, adminStage);

            // Cerrar login
            Stage loginStage = (Stage) emailField.getScene().getWindow();
            loginStage.close();

            System.out.println("Panel de administración cargado exitosamente");

        } catch (Exception e) {
            System.err.println("ERROR al cargar el panel de administración: " + e.getMessage());
            e.printStackTrace();

            Alert errorAlert = new Alert(Alert.AlertType.ERROR);
            errorAlert.setTitle("Error");
            errorAlert.setHeaderText("No se pudo cargar el panel de administración");
            errorAlert.setContentText("Error: " + e.getMessage());
            errorAlert.showAndWait();

            setLoginInProgress(false);
        }
    }

    private void addDragFunctionality(Parent root, Stage stage) {
        final double[] xOffset = {0};
        final double[] yOffset = {0};
        root.setOnMousePressed(event -> {
            xOffset[0] = event.getSceneX();
            yOffset[0] = event.getSceneY();
        });
        root.setOnMouseDragged(event -> {
            stage.setX(event.getScreenX() - xOffset[0]);
            stage.setY(event.getScreenY() - yOffset[0]);
        });
    }
}