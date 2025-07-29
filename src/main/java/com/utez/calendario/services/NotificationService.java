package com.utez.calendario.services;

import com.utez.calendario.models.Event;
import jakarta.mail.MessagingException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class NotificationService {
    private static NotificationService instance;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private final EventService eventService = EventService.getInstance();
    private final MailService mailService;
    private final AtomicBoolean isRunning = new AtomicBoolean(false);

    // Configuración de intervalos de notificación (en minutos)
    private static final long[] NOTIFICATION_INTERVALS = {
            1440,  // 24 horas (1 día)
            60,    // 1 hora
            15,    // 15 minutos
            5      // 5 minutos
    };

    private NotificationService(MailService mailService) {
        this.mailService = mailService;
    }

    public static synchronized NotificationService getInstance(MailService mailService) {
        if (instance == null) {
            instance = new NotificationService(mailService);
        }
        return instance;
    }

    /**
     * Inicia el servicio de notificaciones automáticas
     * Revisa cada 5 minutos si hay eventos próximos que requieren notificación
     */
    public void startNotificationService() {
        if (isRunning.get()) {
            System.out.println("⚠ El servicio de notificaciones ya está en ejecución");
            return;
        }

        isRunning.set(true);
        scheduler.scheduleAtFixedRate(this::checkAndSendNotifications, 0, 5, TimeUnit.MINUTES);
        System.out.println("✓ Servicio de notificaciones iniciado - Revisando cada 5 minutos");
        System.out.println("📧 Intervalos configurados: 24h, 1h, 15min, 5min antes de cada evento");
    }

    /**
     * Revisa todos los eventos próximos y envía notificaciones según corresponda
     */
    private void checkAndSendNotifications() {
        if (!isRunning.get()) return;

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime endRange = now.plusDays(2); // Revisar eventos de los próximos 2 días

        System.out.printf("🔍 Revisando notificaciones [%s]%n", now.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss")));

        try {
            // Obtener todos los usuarios que tienen eventos próximos
            List<String> usersWithEvents = eventService.getUsersWithUpcomingEvents(now, endRange);

            if (usersWithEvents.isEmpty()) {
                System.out.println("📅 No hay usuarios con eventos próximos");
                return;
            }

            System.out.printf("👥 Revisando %d usuarios con eventos próximos%n", usersWithEvents.size());

            for (String userId : usersWithEvents) {
                checkUserNotifications(userId, now, endRange);
            }

        } catch (Exception e) {
            System.err.printf("❌ Error en servicio de notificaciones [%s]: %s%n",
                    now.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss")), e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Revisa las notificaciones para un usuario específico
     */
    private void checkUserNotifications(String userId, LocalDateTime now, LocalDateTime endRange) {
        try {
            // Obtener eventos del usuario en el rango de tiempo
            List<Event> events = eventService.getEventsForDateRange(
                    userId,
                    now.toLocalDate(),
                    endRange.toLocalDate()
            );

            if (events.isEmpty()) {
                return;
            }

            int eventsProcessed = 0;
            for (Event event : events) {
                if (event.getStartDate().isAfter(now)) {
                    if (checkEventNotifications(userId, event, now)) {
                        eventsProcessed++;
                    }
                }
            }

            if (eventsProcessed > 0) {
                System.out.printf("📨 Procesadas %d notificaciones para usuario %s%n", eventsProcessed, userId);
            }

        } catch (Exception e) {
            System.err.printf("❌ Error procesando notificaciones para usuario %s: %s%n", userId, e.getMessage());
        }
    }

    /**
     * Verifica si un evento específico necesita enviar notificaciones
     */
    private boolean checkEventNotifications(String userId, Event event, LocalDateTime now) {
        long minutesUntilEvent = ChronoUnit.MINUTES.between(now, event.getStartDate());

        // Verificar cada intervalo de notificación
        for (long interval : NOTIFICATION_INTERVALS) {
            if (shouldSendNotification(minutesUntilEvent, interval)) {
                return sendNotificationForEvent(userId, event, interval);
            }
        }
        return false;
    }

    /**
     * Determina si se debe enviar una notificación basado en el tiempo restante
     */
    private boolean shouldSendNotification(long minutesUntilEvent, long notificationInterval) {
        // Enviar notificación si estamos dentro del rango de 2 minutos del intervalo
        // Por ejemplo, para 60 minutos, enviar entre 58-62 minutos antes
        long tolerance = 2;

        // Para eventos muy próximos (menos de 10 minutos), ser más preciso
        if (minutesUntilEvent <= 10) {
            tolerance = 1;
        }

        return Math.abs(minutesUntilEvent - notificationInterval) <= tolerance;
    }

    /**
     * Envía una notificación para un evento específico
     */
    private boolean sendNotificationForEvent(String userId, Event event, long minutesBefore) {
        try {
            // Obtener el email del usuario desde la base de datos
            String userEmail = eventService.getUserEmail(userId);

            if (userEmail == null || userEmail.isEmpty()) {
                System.err.printf("⚠ No se encontró email para usuario %s%n", userId);
                return false;
            }

            // Enviar la notificación
            mailService.sendEventReminder(userEmail, event, minutesBefore);

            // Log de éxito
            String timeUnit = minutesBefore >= 60 ? "horas" : "minutos";
            long timeValue = minutesBefore >= 60 ? minutesBefore / 60 : minutesBefore;
            System.out.printf("✅ Notificación enviada: '%s' en %d %s → %s%n",
                    event.getTitle(), timeValue, timeUnit, userEmail);

            return true;

        } catch (MessagingException e) {
            System.err.printf("❌ Error enviando notificación para evento '%s': %s%n",
                    event.getTitle(), e.getMessage());
            return false;
        } catch (Exception e) {
            System.err.printf("❌ Error inesperado enviando notificación: %s%n", e.getMessage());
            return false;
        }
    }

    /**
     * Método para enviar notificación manual (útil para testing)
     */
    public boolean sendManualNotification(String userId, String eventId, long minutesBefore) {
        try {
            // Buscar el evento específico
            List<Event> events = eventService.getEventsForDateRange(
                    userId,
                    LocalDateTime.now().toLocalDate().minusDays(1),
                    LocalDateTime.now().toLocalDate().plusDays(7)
            );

            Event targetEvent = events.stream()
                    .filter(e -> e.getEventId().equals(eventId))
                    .findFirst()
                    .orElse(null);

            if (targetEvent == null) {
                System.err.printf("❌ No se encontró el evento %s para el usuario %s%n", eventId, userId);
                return false;
            }

            return sendNotificationForEvent(userId, targetEvent, minutesBefore);

        } catch (Exception e) {
            System.err.printf("❌ Error en notificación manual: %s%n", e.getMessage());
            return false;
        }
    }

    /**
     * Obtiene el estado del servicio
     */
    public boolean isRunning() {
        return isRunning.get();
    }

    /**
     * Obtiene estadísticas del servicio
     */
    public String getServiceStatus() {
        if (!isRunning.get()) {
            return "❌ Servicio detenido";
        }

        LocalDateTime now = LocalDateTime.now();
        int usersWithEvents = eventService.getUsersWithUpcomingEvents(now, now.plusDays(2)).size();

        return String.format("✅ Servicio activo - %d usuarios con eventos próximos", usersWithEvents);
    }

    /**
     * Detiene el servicio de notificaciones
     */
    public void shutdown() {
        isRunning.set(false);

        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
                System.out.println("✅ Servicio de notificaciones detenido correctamente");
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
                System.out.println("⚠ Servicio de notificaciones forzado a detenerse");
            }
        }
    }
}