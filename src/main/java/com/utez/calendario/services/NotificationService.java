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
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);
    private final EventService eventService = EventService.getInstance();
    private final MailService mailService;
    private final AtomicBoolean isRunning = new AtomicBoolean(false);

    // CLAVE MEJORADA: userId + eventId + interval para evitar duplicados
    private final Map<String, Set<String>> sentNotifications = new ConcurrentHashMap<>();

    // Configuración de intervalos más específica
    private static final long[] REGULAR_EVENT_INTERVALS = {1440, 60, 15, 5}; // 24h, 1h, 15min, 5min
    private static final long[] ALL_DAY_EVENT_INTERVALS = {1440}; // Solo 24h para eventos de todo el día

    // Tolerancia reducida para mayor precisión
    private static final double TOLERANCE_PERCENTAGE = 0.02; // 2% de tolerancia
    private static final long MIN_TOLERANCE_MINUTES = 2; // Mínimo 2 minutos
    private static final long MAX_TOLERANCE_MINUTES = 15; // Máximo 15 minutos

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
        scheduler.scheduleAtFixedRate(this::checkAndSendNotifications, 0, 3, TimeUnit.MINUTES); // Más frecuente
        scheduler.scheduleAtFixedRate(this::cleanNotificationCache, 1, 30, TimeUnit.MINUTES); // Limpieza más frecuente

        System.out.println("✓ Servicio de notificaciones iniciado - Revisando cada 3 minutos");
        System.out.println("📧 Eventos normales: 24h, 1h, 15min, 5min");
        System.out.println("📅 Eventos todo el día: solo 24h");
    }

    private void checkAndSendNotifications() {
        if (!isRunning.get()) return;

        LocalDateTime now = TimeService.getInstance().now();
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
                // Solo eventos futuros
                if (event.getStartDate().isAfter(now)) {
                    if (processEventNotifications(userId, event, now)) {
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

    /**
     * Procesa notificaciones de un evento específico
     */
    private boolean processEventNotifications(String userId, Event event, LocalDateTime now) {
        boolean isAllDayEvent = isAllDayEvent(event);
        long[] intervals = isAllDayEvent ? ALL_DAY_EVENT_INTERVALS : REGULAR_EVENT_INTERVALS;

        System.out.printf("🔍 Procesando evento: %s [%s]%n",
                event.getTitle(),
                isAllDayEvent ? "TODO EL DÍA" : "NORMAL");

        boolean sentAny = false;

        for (long interval : intervals) {
            if (shouldSendNotificationForInterval(userId, event, now, interval, isAllDayEvent)) {
                if (sendNotificationForEvent(userId, event, interval)) {
                    sentAny = true;
                }
            }
        }

        return sentAny;
    }

    /**
     * Determina si es evento de todo el día
     */
    private boolean isAllDayEvent(Event event) {
        // Múltiples criterios para detectar eventos de todo el día
        long durationHours = ChronoUnit.HOURS.between(event.getStartDate(), event.getEndDate());

        // Criterio 1: Duración >= 23 horas
        if (durationHours >= 23) {
            return true;
        }

        // Criterio 2: Empieza a medianoche y termina a medianoche
        if (event.getStartDate().getHour() == 0 &&
                event.getStartDate().getMinute() == 0 &&
                event.getEndDate().getHour() == 0 &&
                event.getEndDate().getMinute() == 0) {
            return true;
        }

        // Criterio 3: Duración exacta de 24 horas
        long durationMinutes = ChronoUnit.MINUTES.between(event.getStartDate(), event.getEndDate());
        if (durationMinutes == 1440) { // 24 * 60 = 1440 minutos
            return true;
        }

        return false;
    }

    /**
     * Determina si debe enviar notificación para un intervalo específico
     */
    private boolean shouldSendNotificationForInterval(String userId, Event event, LocalDateTime now,
                                                      long interval, boolean isAllDayEvent) {

        LocalDateTime targetNotificationTime;

        if (isAllDayEvent) {
            // Para eventos de todo el día: notificar a las 9:00 AM del día anterior
            targetNotificationTime = event.getStartDate().minusDays(1).withHour(9).withMinute(0).withSecond(0);
        } else {
            // Para eventos normales: notificar X minutos antes
            targetNotificationTime = event.getStartDate().minusMinutes(interval);
        }

        // Verificar si ya pasó el momento ideal de envío
        if (now.isAfter(targetNotificationTime.plusMinutes(getToleranceMinutes(interval)))) {
            return false;
        }

        // Verificar si aún no es momento de enviar
        if (now.isBefore(targetNotificationTime.minusMinutes(getToleranceMinutes(interval)))) {
            return false;
        }

        // Verificar si ya se envió esta notificación específica
        String notificationKey = buildNotificationKey(userId, event.getEventId(), interval);
        Set<String> userNotifications = sentNotifications.computeIfAbsent(userId, key -> ConcurrentHashMap.newKeySet());

        if (userNotifications.contains(notificationKey)) {
            System.out.printf("⏩ Notificación ya enviada: %s (%s)%n",
                    event.getTitle(),
                    formatInterval(interval));
            return false;
        }

        return true;
    }

    /**
     * Calcula la tolerancia en minutos basada en el intervalo
     */
    private long getToleranceMinutes(long interval) {
        long tolerance = Math.max(MIN_TOLERANCE_MINUTES, (long)(interval * TOLERANCE_PERCENTAGE));
        return Math.min(tolerance, MAX_TOLERANCE_MINUTES);
    }

    /**
     * Construye una clave única para la notificación
     */
    private String buildNotificationKey(String userId, String eventId, long interval) {
        return String.format("%s:%s:%d", userId, eventId, interval);
    }

    /**
     * FUNCIÓN MEJORADA: Envía notificación y marca como enviada
     */
    private boolean sendNotificationForEvent(String userId, Event event, long minutesBefore) {
        String notificationKey = buildNotificationKey(userId, event.getEventId(), minutesBefore);
        Set<String> userNotifications = sentNotifications.computeIfAbsent(userId, key -> ConcurrentHashMap.newKeySet());

        try {
            String userEmail = eventService.getUserEmail(userId);
            if (userEmail == null || userEmail.isEmpty()) {
                System.err.printf("⚠ Email no encontrado para %s%n", userId);
                return false;
            }

            mailService.sendEventReminder(userEmail, event, minutesBefore);

            // Marcar como enviada DESPUÉS del envío exitoso
            userNotifications.add(notificationKey);

            System.out.printf("✅ Notificación enviada: %s (%s) → %s%n",
                    event.getTitle(),
                    formatInterval(minutesBefore),
                    userEmail);

            return true;

        } catch (MessagingException e) {
            System.err.printf("❌ Error enviando notificación para %s: %s%n", event.getTitle(), e.getMessage());
            return false;
        } catch (Exception e) {
            System.err.printf("❌ Error inesperado: %s%n", e.getMessage());
            return false;
        }
    }

    /**
     * Formatea el intervalo para mostrar
     */
    private String formatInterval(long minutes) {
        if (minutes >= 1440) {
            return (minutes / 1440) + " día(s)";
        } else if (minutes >= 60) {
            return (minutes / 60) + " hora(s)";
        } else {
            return minutes + " minuto(s)";
        }
    }

    /**
     * Limpieza del caché de notificaciones
     */
    private void cleanNotificationCache() {
        System.out.println("\n🧹 Iniciando limpieza de caché...");
        LocalDateTime now = TimeService.getInstance().now();
        int initialKeys = sentNotifications.values().stream().mapToInt(Set::size).sum();

        // Limpiar notificaciones de eventos que ya pasaron
        sentNotifications.entrySet().removeIf(entry -> {
            String userId = entry.getKey();
            Set<String> notifications = entry.getValue();

            notifications.removeIf(notificationKey -> {
                try {
                    String[] parts = notificationKey.split(":");
                    if (parts.length != 3) return true;

                    String eventId = parts[1];
                    Event event = eventService.getEventById(userId, eventId);

                    // Remover si el evento no existe o ya terminó hace más de 1 hora
                    return event == null || event.getEndDate().isBefore(now.minusHours(1));

                } catch (Exception e) {
                    System.err.println("⚠ Error limpiando notificación: " + e.getMessage());
                    return true; // Remover en caso de error
                }
            });

            // Remover usuario si no tiene notificaciones
            return notifications.isEmpty();
        });

        int finalKeys = sentNotifications.values().stream().mapToInt(Set::size).sum();
        System.out.printf("🧹 Caché limpiada: %d eliminadas, %d restantes%n",
                initialKeys - finalKeys, finalKeys);
    }

    public void shutdown() {
        if (!isRunning.compareAndSet(true, false)) {
            System.out.println("⚠ Servicio ya detenido");
            return;
        }

        System.out.println("\n⏳ Deteniendo servicio de notificaciones...");

        cleanNotificationCache();
        sentNotifications.clear();
        System.out.println("🧹 Caché liberado");

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

        System.out.println("✅ Servicio de notificaciones detenido correctamente");
    }

    public boolean isRunning() {
        return isRunning.get();
    }

    public String getServiceStatus() {
        if (!isRunning.get()) {
            return "❌ Servicio detenido";
        }
        int totalNotifications = sentNotifications.values().stream().mapToInt(Set::size).sum();
        return String.format("✅ Servicio activo - Usuarios: %d, Notificaciones: %d",
                sentNotifications.size(), totalNotifications);
    }
}