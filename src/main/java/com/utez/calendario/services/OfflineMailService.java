package com.utez.calendario.services;

import java.io.*;
import java.util.Queue;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import com.utez.calendario.models.Event;
import jakarta.mail.MessagingException;

public class OfflineMailService {
    private static OfflineMailService instance;
    private final Queue<EmailTask> pendingEmails = new ConcurrentLinkedQueue<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private final String queueFilePath = "email_queue.dat";
    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private MailService mailService;

    private OfflineMailService() {
        loadQueueFromDisk();
    }

    public static synchronized OfflineMailService getInstance() {
        if (instance == null) {
            instance = new OfflineMailService();
        }
        return instance;
    }

    public void setMailService(MailService mailService) {
        this.mailService = mailService;
    }

    /**
     * Encolar un correo de verificación para envío
     */
    public void queueVerificationEmail(String recipient, String verificationCode) {
        EmailTask task = new EmailTask(
                EmailType.VERIFICATION,
                recipient,
                null,
                verificationCode,
                0,
                null
        );
        pendingEmails.add(task);
        saveQueueToDisk();
        System.out.println("Email de verificación encolado para: " + recipient);
    }

    /**
     * Encolar un recordatorio de evento para envío
     */
    public void queueEventReminder(String recipient, String eventJson, long minutesBefore) {
        EmailTask task = new EmailTask(
                EmailType.EVENT_REMINDER,
                recipient,
                eventJson,
                null,
                minutesBefore,
                null
        );
        pendingEmails.add(task);
        saveQueueToDisk();
        System.out.println("Recordatorio de evento encolado para: " + recipient);
    }

    /**
     * Encolar una invitación a calendario para envío
     */
    public void queueCalendarInvitation(String recipient, String calendarName) {
        EmailTask task = new EmailTask(
                EmailType.CALENDAR_INVITATION,
                recipient,
                null,
                null,
                0,
                calendarName
        );
        pendingEmails.add(task);
        saveQueueToDisk();
        System.out.println("Invitación a calendario encolada para: " + recipient);
    }

    /**
     * Iniciar procesamiento periódico de la cola
     */
    public void startProcessing() {
        if (isRunning.compareAndSet(false, true)) {
            scheduler.scheduleAtFixedRate(() -> {
                try {
                    processQueue();
                } catch (Exception e) {
                    System.err.println("Error procesando cola de emails: " + e.getMessage());
                }
            }, 1, 5, TimeUnit.MINUTES);

            System.out.println("Servicio de email offline iniciado. Procesando cada 5 minutos");
        }
    }

    /**
     * Procesar la cola de correos pendientes
     */
    public synchronized void processQueue() {
        if (mailService == null) {
            System.out.println(" MailService no configurado aún");
            return;
        }

        if (pendingEmails.isEmpty()) {
            return;
        }

        System.out.println(" Procesando cola de emails: " + pendingEmails.size() + " pendientes");
        int sent = 0;
        boolean needsSave = false;

        // Crear copia temporal para iterar (evita ConcurrentModificationException)
        EmailTask[] tasks = pendingEmails.toArray(new EmailTask[0]);

        for (EmailTask task : tasks) {
            try {
                switch (task.type) {
                    case VERIFICATION:
                        mailService.sendVerificationEmail(task.recipient, task.verificationCode);
                        break;
                    case EVENT_REMINDER:
                        // Necesitaríamos convertir de JSON a objeto Event aquí
                        // Esta es una simplificación
                        Event event = convertJsonToEvent(task.eventJson);
                        mailService.sendEventReminder(task.recipient, event, task.minutesBefore);
                        break;
                    case CALENDAR_INVITATION:
                        mailService.sendCalendarInvitation(task.recipient, task.calendarName);
                        break;
                }

                // Si llegamos aquí, el correo se envió con éxito
                pendingEmails.remove(task);
                sent++;
                needsSave = true;

            } catch (MessagingException e) {
                System.err.println("Error enviando email a " + task.recipient + ": " + e.getMessage());

                // Verificar si es un error de conexión para mantener en cola
                if (e.getMessage().contains("Could not connect") ||
                        e.getMessage().contains("Connection timed out")) {
                    System.out.println(" Error de conexión, se mantendrá en cola para reintento");
                } else {
                    // Error permanente, eliminar de la cola
                    pendingEmails.remove(task);
                    needsSave = true;
                }
            } catch (Exception e) {
                System.err.println("Error inesperado: " + e.getMessage());
                // Para errores desconocidos, mejor remover
                pendingEmails.remove(task);
                needsSave = true;
            }
        }

        if (needsSave) {
            saveQueueToDisk();
        }

        System.out.printf("Procesamiento completado. Enviados: %d, Pendientes: %d%n",
                sent, pendingEmails.size());
    }

    // Método simulado para convertir JSON a objeto Event
    private Event convertJsonToEvent(String json) {
        // Implementación simplificada - necesitarías usar Jackson o Gson
        // Retorna un objeto Event dummy para fines de ejemplo
        return new Event(); // Ajustar según tu modelo
    }

    /**
     * Guardar cola en disco para persistencia
     */
    private void saveQueueToDisk() {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(queueFilePath))) {
            oos.writeObject(pendingEmails.toArray(new EmailTask[0]));
            System.out.println(" Cola de emails guardada. Pendientes: " + pendingEmails.size());
        } catch (IOException e) {
            System.err.println("Error guardando cola: " + e.getMessage());
        }
    }

    /**
     * Cargar cola desde disco
     */
    @SuppressWarnings("unchecked")
    private void loadQueueFromDisk() {
        File file = new File(queueFilePath);
        if (!file.exists()) {
            System.out.println(" No hay cola de emails guardada");
            return;
        }

        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file))) {
            EmailTask[] tasks = (EmailTask[]) ois.readObject();
            pendingEmails.clear();

            for (EmailTask task : tasks) {
                pendingEmails.add(task);
            }

            System.out.println("📥 Cola de emails cargada: " + pendingEmails.size() + " pendientes");
        } catch (Exception e) {
            System.err.println("Error cargando cola: " + e.getMessage());
            // En caso de error, mejor empezar con una cola vacía
            pendingEmails.clear();
            file.delete();
        }
    }

    /**
     * Detener el servicio
     */
    public void shutdown() {
        if (isRunning.compareAndSet(true, false)) {
            saveQueueToDisk();
            scheduler.shutdown();
            try {
                scheduler.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
            System.out.println("Servicio de email offline detenido");
        }
    }

    /**
     * Tipos de correos que podemos encolar
     */
    private enum EmailType {
        VERIFICATION,
        EVENT_REMINDER,
        CALENDAR_INVITATION
    }

    /**
     * Clase que representa un email pendiente
     */
    private static class EmailTask implements Serializable {
        private static final long serialVersionUID = 1L;

        public final EmailType type;
        public final String recipient;
        public final String eventJson;  // JSON del evento para recordatorios
        public final String verificationCode;  // Código para verificación
        public final long minutesBefore;  // Para recordatorios
        public final String calendarName;  // Para invitaciones a calendario

        public EmailTask(EmailType type, String recipient, String eventJson,
                         String verificationCode, long minutesBefore, String calendarName) {
            this.type = type;
            this.recipient = recipient;
            this.eventJson = eventJson;
            this.verificationCode = verificationCode;
            this.minutesBefore = minutesBefore;
            this.calendarName = calendarName;
        }
    }
}