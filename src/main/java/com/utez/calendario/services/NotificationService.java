package com.utez.calendario.services;

import com.utez.calendario.models.Event;
import jakarta.mail.MessagingException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class NotificationService {
    private static NotificationService instance;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4); // Más hilos
    private final EventService eventService = EventService.getInstance();
    private final MailService mailService;
    private final AtomicBoolean isRunning = new AtomicBoolean(false);

    private final Map<String, Set<Long>> sentNotifications = new ConcurrentHashMap<>();

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

    public void startNotificationService() {
        if (isRunning.get()) {
            System.out.println("⚠ El servicio de notificaciones ya está en ejecución");
            return;
        }

        isRunning.set(true);
        scheduler.scheduleAtFixedRate(this::checkAndSendNotifications, 0, 5, TimeUnit.MINUTES);
        scheduler.scheduleAtFixedRate(this::cleanNotificationCache, 1, 60, TimeUnit.MINUTES);

        System.out.println("✓ Servicio de notificaciones iniciado - Revisando cada 5 minutos");
        System.out.println("📧 Intervalos configurados: 24h, 1h, 15min, 5min antes de cada evento");
        System.out.println("🧹 Limpieza de caché programada cada 60 minutos");
    }

    private void checkAndSendNotifications() {
        if (!isRunning.get()) return;

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime endRange = now.plusDays(2);

        System.out.printf("\n🔍 [%s] Revisando notificaciones...%n",
                now.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss")));

        try {
            List<String> usersWithEvents = eventService.getUsersWithUpcomingEvents(now, endRange);

            if (usersWithEvents.isEmpty()) {
                System.out.println("📅 No hay usuarios con eventos próximos");
                return;
            }

            System.out.printf("👥 %d usuarios con eventos próximos%n", usersWithEvents.size());
            int totalNotifications = 0;

            for (String userId : usersWithEvents) {
                totalNotifications += checkUserNotifications(userId, now, endRange);
            }

            System.out.printf("📨 Total notificaciones enviadas: %d%n", totalNotifications);

        } catch (Exception e) {
            System.err.printf("❌ Error en servicio: %s%n", e.getMessage());
            e.printStackTrace();
        }
    }

    private int checkUserNotifications(String userId, LocalDateTime now, LocalDateTime endRange) {
        try {
            List<Event> events = eventService.getEventsForDateRange(
                    userId, now.toLocalDate(), endRange.toLocalDate());

            if (events.isEmpty()) return 0;

            int notificationsSent = 0;
            for (Event event : events) {
                if (event.getStartDate().isAfter(now)) {
                    if (checkEventNotifications(userId, event, now)) {
                        notificationsSent++;
                    }
                }
            }

            return notificationsSent;

        } catch (Exception e) {
            System.err.printf("❌ Error para usuario %s: %s%n", userId, e.getMessage());
            return 0;
        }
    }

    private boolean checkEventNotifications(String userId, Event event, LocalDateTime now) {
        long minutesUntilEvent = ChronoUnit.MINUTES.between(now, event.getStartDate());
        boolean sentAny = false;

        for (long interval : NOTIFICATION_INTERVALS) {
            if (shouldSendNotification(minutesUntilEvent, interval)) {
                if (sendNotificationForEvent(userId, event, interval)) {
                    sentAny = true;
                }
            }
        }
        return sentAny;
    }

    private boolean shouldSendNotification(long minutesUntilEvent, long notificationInterval) {
        double tolerance = Math.max(1, notificationInterval * 0.05);
        return Math.abs(minutesUntilEvent - notificationInterval) <= tolerance;
    }

    private boolean sendNotificationForEvent(String userId, Event event, long minutesBefore) {
        String eventId = event.getEventId();
        Set<Long> sentIntervals = sentNotifications.computeIfAbsent(
                eventId, k -> ConcurrentHashMap.newKeySet());

        if (sentIntervals.contains(minutesBefore)) {
            System.out.printf("⏩ Notificación ya enviada: %s (%d min)%n", event.getTitle(), minutesBefore);
            return false;
        }

        try {
            String userEmail = eventService.getUserEmail(userId);
            if (userEmail == null || userEmail.isEmpty()) {
                System.err.printf("⚠ Email no encontrado para %s%n", userId);
                return false;
            }

            mailService.sendEventReminder(userEmail, event, minutesBefore);
            sentIntervals.add(minutesBefore);

            String timeUnit = minutesBefore >= 60 ? "horas" : "minutos";
            long timeValue = minutesBefore >= 60 ? minutesBefore / 60 : minutesBefore;
            System.out.printf("✅ Notificación enviada: %s (%d %s) → %s%n",
                    event.getTitle(), timeValue, timeUnit, userEmail);

            return true;

        } catch (MessagingException e) {
            System.err.printf("❌ Error enviando notificación para %s: %s%n", event.getTitle(), e.getMessage());
            return false;
        } catch (Exception e) {
            System.err.printf("❌ Error inesperado: %s%n", e.getMessage());
            return false;
        }
    }

    private void cleanNotificationCache() {
        System.out.println("\n🧹 Iniciando limpieza de caché...");
        LocalDateTime now = LocalDateTime.now();
        int initialSize = sentNotifications.size();

        sentNotifications.keySet().removeIf(eventId -> {
            try {
                Event event = eventService.getEventById("system", eventId);
                if (event == null || event.getEndDate().isBefore(now)) {
                    return true;
                }
                return false;
            } catch (Exception e) {
                System.err.println("⚠ Error limpiando caché: " + e.getMessage());
                return false;
            }
        });

        int finalSize = sentNotifications.size();
        System.out.printf("🧹 Caché limpiada: %d eliminadas, %d restantes%n",
                initialSize - finalSize, finalSize);
    }

    public void shutdown() {
        if (!isRunning.compareAndSet(true, false)) {
            System.out.println("⚠ Servicio ya detenido");
            return;
        }

        System.out.println("\n⏳ Deteniendo servicio de notificaciones...");

        // Limpiar caché final
        cleanNotificationCache();
        sentNotifications.clear();
        System.out.println("🧹 Caché liberado");

        // Detener scheduler
        if (scheduler != null) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(15, TimeUnit.SECONDS)) {
                    List<Runnable> pending = scheduler.shutdownNow();
                    System.out.println("⚠ Tareas canceladas: " + pending.size());
                }
                System.out.println("✅ Scheduler detenido");
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
                System.out.println("⚠ Interrupción durante detención");
            }
        }

        System.out.println("✅ Servicio de notificaciones detenido correctamente");
    }

    public boolean isRunning() {
        return isRunning.get();
    }

    public String getServiceStatus() {
        if (!isRunning.get()) {
            return "❌ Servicio detenido";
        }
        return "✅ Servicio activo - Eventos en caché: " + sentNotifications.size();
    }
}