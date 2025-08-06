package com.utez.calendario.controllers;

import com.utez.calendario.MainApp;
import com.utez.calendario.models.Calendar;
import com.utez.calendario.services.CalendarSharingService;
import com.utez.calendario.services.MailService;
import com.utez.calendario.services.TimeService;
import jakarta.mail.MessagingException;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;

import java.sql.SQLException;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
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
    @FXML private ProgressBar progressBar;
    @FXML private Label statusLabel;

    private volatile MailService mailService;
    private final ObservableList<String> emailList = FXCollections.observableArrayList();

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,6}$", Pattern.CASE_INSENSITIVE);

    private Stage dialogStage;
    private Calendar calendar;
    private DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    @FXML
    private void initialize() {
        System.out.println("🚀 Inicializando diálogo de compartir calendario: " +
                TimeService.getInstance().now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));

        setupUI();
        setupProgressIndicators();
    }

    private void setupUI() {
        // Configuración para emails
        emailListView.setItems(emailList);
        emailErrorLabel.setVisible(false);
        emailErrorLabel.setManaged(false);
        calendarNameField.setEditable(false);
        calendarNameField.setFocusTraversable(false);
        saveButton.setText("Guardar");
    }

    private void setupProgressIndicators() {
        if (progressBar != null) {
            progressBar.setVisible(false);
            progressBar.setManaged(false);
        }
        if (statusLabel != null) {
            statusLabel.setVisible(false);
            statusLabel.setManaged(false);
        }
    }

    public void setCalendar(Calendar calendar) {
        this.calendar = calendar;
        loadCalendarData();
    }

    private void loadCalendarData() {
        if (calendar != null) {
            calendarNameField.setText(calendar.getName());
        }
    }

    public void setMailService(MailService mailService) {
        if (mailService != null) {
            System.out.println("✅ MailService recibido correctamente");
            this.mailService = mailService;
        } else {
            System.err.println("⚠️ MailService es null - obteniendo desde MainApp");
            try {
                this.mailService = MainApp.getEmailService();
                System.out.println("✅ MailService obtenido desde MainApp");
            } catch (Exception e) {
                System.err.println("❌ Error obteniendo MailService: " + e.getMessage());
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
        hideEmailError();
    }

    private boolean isValidEmail(String email) {
        return EMAIL_PATTERN.matcher(email).matches();
    }

    private void showEmailError(String message) {
        emailErrorLabel.setText(message);
        emailErrorLabel.setVisible(true);
        emailErrorLabel.setManaged(true);
    }

    private void hideEmailError() {
        emailErrorLabel.setVisible(false);
        emailErrorLabel.setManaged(false);
    }

    @FXML
    private void handleSendInvitations() {
        // Validaciones rápidas
        if (emailList.isEmpty()) {
            showMessage("Debes agregar al menos un email", true);
            return;
        }

        if (mailService == null) {
            showMessage("Error: Servicio de correo no disponible", true);
            return;
        }

        if (calendar == null) {
            showMessage("Error: No hay calendario seleccionado", true);
            return;
        }

        // UI busy
        setUIBusy(true);
        updateStatus("🚀 Iniciando proceso súper optimizado...");

        // 🔥 PROCESO SÚPER OPTIMIZADO
        handleSendInvitationsUltraFast();
    }

    private void handleSendInvitationsUltraFast() {
        String calendarId = calendar.getCalendarId();
        String calendarName = calendar.getName();
        List<String> emails = new ArrayList<>(emailList);

        long startTime = System.currentTimeMillis();
        AtomicInteger processedCount = new AtomicInteger(0);

        // Usar el patrón Singleton para el servicio
        CalendarSharingService sharingService = CalendarSharingService.getInstance();

        System.out.println("📊 Enviando invitaciones a " + emails.size() +
                " correos electrónicos a las " +
                TimeService.getInstance().now().format(DateTimeFormatter.ofPattern("HH:mm:ss")));

        // 🔥 FASE 1: Compartir calendarios
        sharingService.shareCalendarWithMultipleUsersOptimized(calendarId, emails)
                .thenCompose(shareResult -> { // ✅ Guardar el resultado
                    Platform.runLater(() -> {
                        updateStatus("✅ Calendarios compartidos: " + shareResult.getSuccessCount() +
                                " | Errores: " + shareResult.getErrorCount());
                        updateProgress(0.5);
                    });

                    // 🔥 FASE 2: Enviar emails
                    return sendEmailsInParallel(shareResult.getSuccessfulEmails(), calendarName, processedCount)
                            .thenApply(emailResults -> new Object[]{ shareResult, emailResults }); // ✅ Pasar ambos resultados
                })
                .thenApply(combinedResults -> {
                    // 🔥 FASE 3: Combinar resultados SIN duplicar
                    CalendarSharingService.ShareBatchResult shareResult = (CalendarSharingService.ShareBatchResult) combinedResults[0];
                    @SuppressWarnings("unchecked")
                    Map<String, String> emailResults = (Map<String, String>) combinedResults[1];
                    return combineResults(shareResult, emailResults); // ✅ Usar resultados guardados
                })
                .whenCompleteAsync((finalResult, throwable) -> {
                    Platform.runLater(() -> {
                        long endTime = System.currentTimeMillis();
                        setUIBusy(false);

                        if (throwable != null) {
                            handleProcessError(throwable);
                        } else {
                            handleProcessSuccess(finalResult, endTime - startTime);
                        }
                    });
                });
    }

    // 🔥 Envío de emails EN PARALELO MÁXIMO
    private CompletableFuture<Map<String, String>> sendEmailsInParallel(
            List<String> successfulEmails, String calendarName, AtomicInteger processedCount) {

        if (successfulEmails.isEmpty()) {
            return CompletableFuture.completedFuture(new HashMap<>());
        }

        Platform.runLater(() -> updateStatus("📧 Enviando " + successfulEmails.size() + " emails en paralelo..."));

        // 🚀 Crear tareas paralelas para cada email
        List<CompletableFuture<Map.Entry<String, String>>> emailTasks = successfulEmails.stream()
                .map(email -> CompletableFuture.supplyAsync(() -> {
                    try {
                        mailService.sendCalendarInvitation(email, calendarName);

                        // Actualizar progreso en tiempo real
                        int current = processedCount.incrementAndGet();
                        Platform.runLater(() -> {
                            updateStatus("📧 Enviado a: " + email + " (" + current + "/" + successfulEmails.size() + ")");
                            updateProgress(0.5 + (0.5 * current / successfulEmails.size())); // 50-100%
                        });

                        return Map.entry(email, "SUCCESS");
                    } catch (MessagingException e) {
                        System.err.println("❌ Error enviando a " + email + ": " + e.getMessage());
                        return Map.entry(email, "ERROR: " + e.getMessage());
                    }
                }))
                .toList();

        // 🚀 Esperar a que TODOS los emails terminen
        return CompletableFuture.allOf(emailTasks.toArray(new CompletableFuture[0]))
                .thenApply(v -> {
                    Map<String, String> results = new HashMap<>();
                    emailTasks.forEach(task -> {
                        try {
                            Map.Entry<String, String> entry = task.join();
                            results.put(entry.getKey(), entry.getValue());
                        } catch (Exception e) {
                            System.err.println("❌ Error procesando resultado de email: " + e.getMessage());
                        }
                    });
                    return results;
                });
    }

    // 🔥 Combinar resultados de compartir + emails
    private UltimateResult combineResults(
            CalendarSharingService.ShareBatchResult shareResult,
            Map<String, String> emailResults) {

        UltimateResult result = new UltimateResult();

        // Procesar éxitos de compartir
        for (String email : shareResult.getSuccessfulEmails()) {
            String emailStatus = emailResults.get(email);
            if ("SUCCESS".equals(emailStatus)) {
                result.addCompleteSuccess(email);
            } else {
                result.addPartialSuccess(email, "Compartido pero email falló: " + emailStatus);
            }
        }

        // Procesar errores de compartir
        shareResult.getErrors().forEach(result::addShareError);

        return result;
    }

    private void handleProcessSuccess(UltimateResult result, long timeMs) {
        hideStatus();
        updateProgress(1.0); // 100%

        StringBuilder message = new StringBuilder();

        if (!result.getCompleteSuccesses().isEmpty()) {
            message.append("✅ Correo(s) enviado(s) con éxito a (").append(result.getCompleteSuccesses().size()).append("):\n");
            result.getCompleteSuccesses().forEach(email ->
                    message.append("  • ").append(email).append("\n"));
        }

        if (!result.getPartialSuccesses().isEmpty()) {
            message.append("\n⚠️ Éxitos parciales (").append(result.getPartialSuccesses().size()).append("):\n");
            result.getPartialSuccesses().forEach((email, reason) ->
                    message.append("  • ").append(email).append(": ").append(reason).append("\n"));
        }

        if (!result.getShareErrors().isEmpty()) {
            message.append("\n❌ Errores (").append(result.getShareErrors().size()).append("):\n");
            result.getShareErrors().forEach((email, error) ->
                    message.append("  • ").append(email).append(": ").append(error).append("\n"));
        }

        message.append("\n⚡ Procesado en ").append(timeMs).append("ms");

        boolean hasErrors = !result.getShareErrors().isEmpty();
        showMessage(message.toString(), hasErrors);

        if (result.getCompleteSuccesses().size() == emailList.size()) {
            emailList.clear(); // Solo limpiar si TODO fue exitoso
        }
    }

    private void handleProcessError(Throwable throwable) {
        hideStatus();
        String message = "❌ Error en el proceso: ";
        if (throwable.getCause() != null) {
            message += throwable.getCause().getMessage();
        } else {
            message += throwable.getMessage();
        }
        showMessage(message, true);
    }

    // ============= MÉTODOS ALTERNATIVOS (COMPATIBILIDAD) =============

    /**
     * Método alternativo más simple para enviar invitaciones
     * Útil si hay problemas con el método optimizado
     */
    private void handleSendInvitationsSimple() {
        CalendarSharingService sharingService = CalendarSharingService.getInstance();

        try {
            System.out.println("📊 Enviando invitaciones (modo simple) a " + emailList.size() +
                    " correos electrónicos a las " +
                    TimeService.getInstance().now().format(DateTimeFormatter.ofPattern("HH:mm:ss")));

            for (String email : emailList) {
                String calendarID = calendar.getCalendarId();
                sharingService.shareCalendar(calendarID, email);
                mailService.sendCalendarInvitation(email, calendar.getName());
            }

            showMessage("✅ Invitaciones enviadas exitosamente", false);
            emailList.clear();
        } catch (SQLException e) {
            showMessage("❌ Error al compartir: " + e.getMessage(), true);
        } catch (RuntimeException | MessagingException e) {
            showMessage("❌ Error: " + e.getMessage(), true);
        }
    }

    // ============= MÉTODOS DE UI =============

    private void setUIBusy(boolean busy) {
        sendInvitationsButton.setDisable(busy);
        addEmailButton.setDisable(busy);
        emailField.setDisable(busy);

        if (progressBar != null) {
            progressBar.setVisible(busy);
            progressBar.setManaged(busy);
            if (!busy) progressBar.setProgress(0);
        }
    }

    private void updateStatus(String message) {
        if (statusLabel != null) {
            statusLabel.setText(message);
            statusLabel.setVisible(true);
            statusLabel.setManaged(true);
        }
        System.out.println("📊 " + message);
    }

    private void updateProgress(double progress) {
        if (progressBar != null) {
            progressBar.setProgress(progress);
        }
    }

    private void hideStatus() {
        if (statusLabel != null) {
            statusLabel.setVisible(false);
            statusLabel.setManaged(false);
        }
    }

    // ============= MÉTODOS DE CONTROL =============

    @FXML
    private void handleSave() {
        if (dialogStage != null) {
            handleClose();
        }
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

    // ============= SETTERS =============

    public void setDialogStage(Stage dialogStage) {
        this.dialogStage = dialogStage;
    }

    // ============= MÉTODOS AUXILIARES =============

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

    // ============= CLASE DE RESULTADO FINAL =============

    private static class UltimateResult {
        private final List<String> completeSuccesses = new ArrayList<>();
        private final Map<String, String> partialSuccesses = new HashMap<>();
        private final Map<String, String> shareErrors = new HashMap<>();

        public void addCompleteSuccess(String email) {
            completeSuccesses.add(email);
        }

        public void addPartialSuccess(String email, String reason) {
            partialSuccesses.put(email, reason);
        }

        public void addShareError(String email, String error) {
            shareErrors.put(email, error);
        }

        public List<String> getCompleteSuccesses() {
            return completeSuccesses;
        }

        public Map<String, String> getPartialSuccesses() {
            return partialSuccesses;
        }

        public Map<String, String> getShareErrors() {
            return shareErrors;
        }
    }
}