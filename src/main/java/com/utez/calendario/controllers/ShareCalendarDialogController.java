package com.utez.calendario.controllers;

import com.utez.calendario.MainApp;
import com.utez.calendario.models.Calendar;
import com.utez.calendario.services.CalendarSharingService;
import com.utez.calendario.services.MailService;
import jakarta.mail.MessagingException;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;

import java.sql.SQLException;
import java.time.format.DateTimeFormatter;
import java.util.regex.Pattern;


public class ShareCalendarDialogController {

    @FXML private TextField calendarNameField;
    @FXML private Button cancelButton;
    @FXML private Button saveButton;
    @FXML private Button closeButton;
    @FXML private TextField emailField;
    @FXML private Button addEmailButton;
    @FXML private ListView<String> emailListView;
    @FXML private Label emailErrorLabel;
    @FXML private Button sendInvitationsButton;

    private volatile MailService mailService;

    // Lista para almacenar emails
    private final ObservableList<String> emailList = FXCollections.observableArrayList();

    // Patrón para validar emails
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,6}$", Pattern.CASE_INSENSITIVE);

    private Stage dialogStage;
    private Calendar calendar;
    private DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    @FXML
    private void initialize() {
        // Configuración para emails
        emailListView.setItems(emailList);
        emailErrorLabel.setVisible(false);
        emailErrorLabel.setManaged(false);

        // Bloquear campo de nombre
        calendarNameField.setEditable(false);
        calendarNameField.setFocusTraversable(false);

        saveButton.setText("Guardar");
    }

    public void setCalendar(Calendar calendar) {

        this.calendar = calendar;

        loadCalendarData();

    }

    private void loadCalendarData() {

        if (calendar != null) {
            // Establecer nombre del calendario
            calendarNameField.setText(calendar.getName());
        }

    }

    public void setMailService(MailService mailService) {
        // Verificar si el servicio es válido
        if (mailService != null) {

            System.out.println("MailService recibido correctamente");

            this.mailService = mailService;

        } else {

            System.err.println("¡MailService es null!");

            try {

                this.mailService = MainApp.getEmailService();

                System.out.println("Obtenido MailService desde Singleton directamente");

            } catch (Exception e) {

                System.err.println("Error obteniendo MailService: " + e.getMessage());

            }

        }

    }

    @FXML
    private void handleAddEmail() {

        String email = emailField.getText().trim();

        if (email.isEmpty()) {
            showEmailError("Ingresa un email válido");
            return;
        }

        if (!isValidEmail(email)) {
            showEmailError("Formato de email inválido");
            return;
        }

        if (emailList.contains(email)) {
            showEmailError("Este email ya fue agregado");
            return;
        }

        emailList.add(email);
        emailField.clear();
        emailErrorLabel.setVisible(false);
        emailErrorLabel.setManaged(false);
    }

    private boolean isValidEmail(String email) {
        return EMAIL_PATTERN.matcher(email).matches();
    }

    private void showEmailError(String message) {

        emailErrorLabel.setText(message);
        emailErrorLabel.setVisible(true);
        emailErrorLabel.setManaged(true);

    }

    @FXML
    private void handleSendInvitations() {

        CalendarSharingService sharingService = new CalendarSharingService();

        if (emailList.isEmpty()) {
            showMessage("Debes agregar al menos un email", true);
            return;
        }

        if (mailService == null) {
            showMessage("Error: Servicio de correo no disponible", true);
            return;
        }

        try {

            for (String email : emailList) {

                String calendarID = calendar.getCalendarId();

                sharingService.shareCalendar(calendarID, email);
                mailService.sendCalendarInvitation(
                        email,
                        calendar.getName()
                );
            }

            showMessage("Invitaciones enviadas exitosamente", false);
            emailList.clear();
        } catch (SQLException e) {

            showMessage("Error al compartir: " + e.getMessage(), true);

        } catch (RuntimeException | MessagingException e) {

            showMessage(e.getMessage(), true);

        }

    }

    @FXML
    private void handleSave() {

        if (dialogStage != null){

            handleClose();

        }
        //handleGenerateCode();
    }

    @FXML
    private void handleCancel() {
        handleClose();
    }

    @FXML
    private void handleClose() {
        if (dialogStage != null) {
            dialogStage.close();
        }
    }

    public void setDialogStage(Stage dialogStage) {
        this.dialogStage = dialogStage;
    }

    private void showMessage(String message, boolean isError) {
        Alert alert = new Alert(isError ? Alert.AlertType.ERROR : Alert.AlertType.INFORMATION);
        alert.setTitle(isError ? "Error" : "Información");
        alert.setHeaderText(null);
        alert.setContentText(message);

        if (dialogStage != null) {
            alert.initOwner(dialogStage);
        }

        alert.showAndWait();
    }
}