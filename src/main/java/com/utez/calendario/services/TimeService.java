package com.utez.calendario.services;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Servicio centralizado para manejar todas las operaciones de fecha/hora
 * Protege contra manipulación del tiempo del sistema
 */
public class TimeService {
    // Singleton
    private static TimeService instance;

    // Valores constantes
    private static final String TIME_API_URL = "https://worldtimeapi.org/api/ip";
    private static final String BACKUP_TIME_API_URL = "https://timeapi.io/api/Time/current/zone?timeZone=America/Mexico_City";
    private static final String LOCAL_TIME_FILE = "ithera_time_sync.dat";
    private static final long SYNC_INTERVAL_MINUTES = 60; // Sincronizar cada hora
    private static final long MAX_OFFSET_THRESHOLD_MINUTES = 15; // Alerta si hay más de 15 minutos de diferencia

    // Variables para control del tiempo
    private final AtomicLong offsetMillis = new AtomicLong(0); // Diferencia entre tiempo del sistema y tiempo real
    private final AtomicLong lastSyncTimeMillis = new AtomicLong(0); // Último momento de sincronización
    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    // Formateador para almacenar la fecha
    private final DateTimeFormatter storageDateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    private TimeService() {
        initialize();
    }

    public static synchronized TimeService getInstance() {
        if (instance == null) {
            instance = new TimeService();
        }
        return instance;
    }

    /**
     * Inicializar el servicio
     */
    private void initialize() {
        // Intentar cargar el offset guardado en disco
        loadSavedOffset();

        // Intentar sincronizar inmediatamente
        syncWithNetworkTime();

        // Programar sincronizaciones periódicas
        startPeriodicSync();
    }

    /**
     * Inicia sincronización periódica
     */
    private void startPeriodicSync() {
        if (isRunning.compareAndSet(false, true)) {
            scheduler.scheduleAtFixedRate(() -> {
                try {
                    syncWithNetworkTime();
                } catch (Exception e) {
                    System.err.println("Error en sincronización periódica: " + e.getMessage());
                }
            }, SYNC_INTERVAL_MINUTES, SYNC_INTERVAL_MINUTES, TimeUnit.MINUTES);

            System.out.println(" Servicio de tiempo iniciado. Sincronización cada " + SYNC_INTERVAL_MINUTES + " minutos");
        }
    }

    /**
     * Método principal para obtener la hora actual real
     * Este método reemplaza todas las llamadas a LocalDateTime.now()
     */
    public LocalDateTime now() {
        // Obtener el tiempo actual del sistema
        LocalDateTime systemNow = LocalDateTime.now();

        // Si nunca se ha sincronizado, usar tiempo del sistema
        if (lastSyncTimeMillis.get() == 0) {
            return systemNow;
        }

        // Aplicar el offset al tiempo del sistema
        return systemNow.plus(Duration.ofMillis(offsetMillis.get()));
    }

    /**
     * Sincronizar con una fuente de tiempo externa
     */
    public synchronized boolean syncWithNetworkTime() {
        try {
            System.out.println("\n Sincronizando con servidor de tiempo...");

            // Obtener tiempo actual del sistema
            Instant systemTimeInstant = Instant.now();

            // Obtener tiempo de Internet (con fallback)
            Instant networkTimeInstant = getNetworkTime();

            // Si no se pudo obtener el tiempo de la red, mantener el offset actual
            if (networkTimeInstant == null) {
                System.out.println(" No se pudo obtener tiempo de red. Usando última sincronización conocida.");
                return false;
            }

            // Calcular diferencia entre tiempo del sistema y tiempo de la red
            long newOffsetMillis = networkTimeInstant.toEpochMilli() - systemTimeInstant.toEpochMilli();

            // Verificar si el offset es significativamente diferente (para detectar manipulaciones)
            if (lastSyncTimeMillis.get() > 0) {
                long offsetDiffMinutes = Math.abs(TimeUnit.MILLISECONDS.toMinutes(newOffsetMillis - offsetMillis.get()));
                if (offsetDiffMinutes > MAX_OFFSET_THRESHOLD_MINUTES) {
                    System.out.println(" ALERTA: Posible manipulación del reloj detectada!");
                    System.out.println("   Diferencia de " + offsetDiffMinutes + " minutos desde última sincronización");
                }
            }

            // Actualizar el offset
            offsetMillis.set(newOffsetMillis);
            lastSyncTimeMillis.set(System.currentTimeMillis());

            // Guardar la información en disco
            saveCurrentOffset();

            // Log informativo
            System.out.println(" Sincronización exitosa");
            System.out.println("   Tiempo del sistema: " +
                    LocalDateTime.ofInstant(systemTimeInstant, ZoneId.systemDefault()));
            System.out.println("   Tiempo real: " +
                    LocalDateTime.ofInstant(networkTimeInstant, ZoneId.systemDefault()));
            System.out.println("   Diferencia: " + formatOffsetDuration(newOffsetMillis));

            return true;
        } catch (Exception e) {
            System.err.println(" Error sincronizando tiempo: " + e.getMessage());
            return false;
        }
    }

    /**
     * Formatear duración del offset para log
     */
    private String formatOffsetDuration(long millis) {
        boolean isNegative = millis < 0;
        long absDuration = Math.abs(millis);

        long minutes = TimeUnit.MILLISECONDS.toMinutes(absDuration) % 60;
        long seconds = TimeUnit.MILLISECONDS.toSeconds(absDuration) % 60;

        return String.format("%s%d min, %d seg",
                isNegative ? "-" : "+", minutes, seconds);
    }

    /**
     * Obtener tiempo de Internet con múltiples fuentes para redundancia
     */
    private Instant getNetworkTime() {
        // Intentar con la API principal
        Instant time = getTimeFromApi(TIME_API_URL, "datetime");
        if (time != null) return time;

        // Si falla, intentar con la API de respaldo
        time = getTimeFromApi(BACKUP_TIME_API_URL, "dateTime");
        if (time != null) return time;

        // Si ambas fallan, no hay tiempo de red disponible
        return null;
    }

    /**
     * Consultar API específica para obtener el tiempo
     */
    private Instant getTimeFromApi(String apiUrl, String jsonField) {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(apiUrl);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);

            int responseCode = connection.getResponseCode();
            if (responseCode == 200) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;

                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();

                // Extraer fecha del JSON (simplificado)
                String json = response.toString();
                int fieldIndex = json.indexOf("\"" + jsonField + "\":");
                if (fieldIndex > 0) {
                    // Extraer el valor del campo datetime
                    int startIndex = json.indexOf("\"", fieldIndex + jsonField.length() + 3) + 1;
                    int endIndex = json.indexOf("\"", startIndex);
                    if (startIndex > 0 && endIndex > startIndex) {
                        String dateStr = json.substring(startIndex, endIndex);

                        // Manejar formato ISO con/sin timezone
                        try {
                            if (dateStr.contains("T") && dateStr.contains("Z")) {
                                // Formato ISO completo
                                return Instant.parse(dateStr);
                            } else {
                                // Otro formato con T pero sin Z
                                return LocalDateTime.parse(
                                                dateStr.substring(0, 19),
                                                DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                                        .atZone(ZoneId.systemDefault())
                                        .toInstant();
                            }
                        } catch (Exception e) {
                            System.err.println("Error parseando fecha: " + dateStr);
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error consultando " + apiUrl + ": " + e.getMessage());
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
        return null;
    }

    /**
     * Guardar offset actual en archivo local
     */
    private void saveCurrentOffset() {
        try {
            File file = new File(LOCAL_TIME_FILE);
            try (FileWriter writer = new FileWriter(file)) {
                LocalDateTime adjustedTime = now();
                writer.write(String.valueOf(offsetMillis.get()) + "\n");
                writer.write(String.valueOf(System.currentTimeMillis()) + "\n");
                writer.write(adjustedTime.format(storageDateFormat));
            }
            System.out.println("Información de tiempo guardada en disco");
        } catch (Exception e) {
            System.err.println("Error guardando datos de tiempo: " + e.getMessage());
        }
    }

    /**
     * Cargar offset guardado previamente
     */
    private void loadSavedOffset() {
        try {
            File file = new File(LOCAL_TIME_FILE);
            if (!file.exists()) {
                System.out.println("No hay archivo de tiempo guardado");
                return;
            }

            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                String offsetLine = reader.readLine();
                String lastSyncLine = reader.readLine();
                String savedTimeLine = reader.readLine();

                if (offsetLine != null && lastSyncLine != null && savedTimeLine != null) {
                    long savedOffset = Long.parseLong(offsetLine);
                    long savedLastSync = Long.parseLong(lastSyncLine);
                    LocalDateTime savedTime = LocalDateTime.parse(savedTimeLine, storageDateFormat);

                    // Calcular cuánto tiempo ha pasado desde la última sincronización
                    long elapsedMillis = System.currentTimeMillis() - savedLastSync;

                    // Actualizar valores
                    offsetMillis.set(savedOffset);
                    lastSyncTimeMillis.set(savedLastSync);

                    // Verificar que el tiempo sea consistente
                    LocalDateTime expectedNow = savedTime.plus(Duration.ofMillis(elapsedMillis));
                    LocalDateTime actualNow = LocalDateTime.now().plus(Duration.ofMillis(savedOffset));

                    long inconsistencyMinutes = Math.abs(Duration.between(expectedNow, actualNow).toMinutes());
                    if (inconsistencyMinutes > MAX_OFFSET_THRESHOLD_MINUTES) {
                        System.out.println("ALERTA: Inconsistencia detectada de " + inconsistencyMinutes + " minutos");
                    }

                    System.out.println("Datos de tiempo cargados desde disco");
                    System.out.println("   Última sincronización: hace " + formatDuration(elapsedMillis));
                    System.out.println("   Offset aplicado: " + formatOffsetDuration(savedOffset));
                }
            }
        } catch (Exception e) {
            System.err.println("Error cargando datos de tiempo: " + e.getMessage());
        }
    }

    /**
     * Formatear duración para log
     */
    private String formatDuration(long millis) {
        long hours = TimeUnit.MILLISECONDS.toHours(millis);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60;
        return String.format("%d horas, %d minutos", hours, minutes);
    }

    /**
     * Detener el servicio
     */
    public void shutdown() {
        if (isRunning.compareAndSet(true, false)) {
            saveCurrentOffset();
            scheduler.shutdown();
            try {
                scheduler.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
            System.out.println("Servicio de tiempo detenido");
        }
    }

    /**
     * Verificar si la fecha del sistema ha sido manipulada significativamente
     * @return true si se detecta manipulación
     */
    public boolean isSystemTimeManipulated() {
        // Si nunca se ha sincronizado, no se puede saber
        if (lastSyncTimeMillis.get() == 0) {
            return false;
        }

        // Calcular cuánto debería haber avanzado el reloj desde la última sincronización
        long elapsedSinceSync = System.currentTimeMillis() - lastSyncTimeMillis.get();

        // Obtener la hora real actual y la hora del sistema
        LocalDateTime realNow = now();
        LocalDateTime systemNow = LocalDateTime.now();

        // Calcular diferencia en minutos
        long diffMinutes = Math.abs(Duration.between(realNow, systemNow).toMinutes());

        // Si la diferencia es mayor que el umbral, considerar manipulado
        return diffMinutes > MAX_OFFSET_THRESHOLD_MINUTES;
    }
}