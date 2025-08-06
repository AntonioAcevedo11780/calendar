package com.utez.calendario;

import com.utez.calendario.config.DatabaseConfig;
import com.utez.calendario.services.*;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Rectangle2D;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.text.Font;
import javafx.stage.Screen;
import javafx.stage.Stage;

public class MainApp extends Application {

    private static NotificationService notificationService;
    private static volatile MailService emailService;

    @Override
    public void start(Stage primaryStage) throws Exception {
        System.out.println("Iniciando Calendario iTHERA...");

        try {
            // 1. Inicializar servicios básicos
            initializeServices();

            // 2. Verificar estado de la base de datos
            checkDatabaseStatus();

            // 3. Mostrar información del sistema
            displaySystemInfo();

            // 4. Configurar interfaz gráfica
            setupUI(primaryStage);

            System.out.println("Aplicacion iniciada correctamente");

        } catch (Exception e) {
            System.err.println("Error critico durante el inicio: " + e.getMessage());
            e.printStackTrace();
            showCriticalErrorDialog(e);
            throw e;
        }
    }

    /**
     * Inicializar todos los servicios de la aplicación
     */
    private void initializeServices() {
        System.out.println("Inicializando servicios...");

        // 1. Servicio de tiempo (protección contra manipulación)
        TimeService timeService = TimeService.getInstance();
        System.out.println("   TimeService inicializado - Hora actual: " + timeService.now());

        // Verificar manipulación del reloj
        if (timeService.isSystemTimeManipulated()) {
            Platform.runLater(this::showTimeManipulationWarning);
        }

        // 2. Inicializar base de datos - CORREGIDO: no necesita try-catch aquí
        // DatabaseConfig se encarga de la lógica offline/online internamente
        try {
            DatabaseConfig.getConnection().close(); // Test de conexión
            System.out.println("   DatabaseConfig inicializado correctamente");
        } catch (Exception e) {
            System.out.println("   DatabaseConfig inicializado (modo offline activado)");
        }

        // 3. Inicializar servicios de sincronización y email offline
        OfflineSyncService syncService = OfflineSyncService.getInstance();
        syncService.startSyncMonitoring();
        System.out.println("   OfflineSyncService inicializado");

        OfflineMailService offlineMailService = OfflineMailService.getInstance();
        offlineMailService.setMailService(getEmailService());
        offlineMailService.startProcessing();
        System.out.println("   OfflineMailService inicializado");

        // 4. Inicializar sistema de notificaciones
        initializeNotificationSystem();
    }

    /**
     * Inicializa el sistema de notificaciones automáticas
     */
    private void initializeNotificationSystem() {
        try {
            System.out.println("   Inicializando sistema de notificaciones...");

            // Obtener el servicio de email existente
            MailService emailService = getEmailService();

            // Crear e inicializar el scheduler de notificaciones
            notificationService = NotificationService.getInstance(emailService);
            notificationService.startNotificationService();

            System.out.println("   Sistema de notificaciones iniciado correctamente");
            System.out.println("   " + notificationService.getServiceStatus());

        } catch (Exception e) {
            System.err.println("   Error inicializando sistema de notificaciones: " + e.getMessage());
            e.printStackTrace();
            // La aplicación puede continuar sin notificaciones
        }
    }

    /**
     * Verificar estado de la base de datos - MEJORADO
     */
    private void checkDatabaseStatus() {
        System.out.println("Verificando estado de la base de datos...");

        try {
            String dbInfo = DatabaseConfig.getDatabaseInfo();
            System.out.println("   " + dbInfo);

            if (DatabaseConfig.isOfflineMode()) {
                System.out.println("   ATENCION: Funcionando en modo offline");
                System.out.println("   Los datos se sincronizaran cuando la conexion se restaure");

                OfflineSyncService.SyncStats stats = OfflineSyncService.getInstance().getSyncStats();
                System.out.println("   " + stats.toString());

                // Mostrar alerta visual al usuario solo si no es el comportamiento esperado
                Platform.runLater(this::showOfflineModeAlert);
            } else {
                System.out.println("   CONECTADO: Funcionando en modo online");

                // Si había estado offline antes, mostrar mensaje de reconexión
                if (OfflineSyncService.getInstance().getSyncStats().pendingChanges > 0) {
                    System.out.println("   Sincronizando datos pendientes del modo offline...");
                }
            }

        } catch (Exception e) {
            System.err.println("   Error verificando base de datos: " + e.getMessage());
        }
    }

    /**
     * Mostrar información del sistema - MEJORADO
     */
    private void displaySystemInfo() {
        System.out.println("Informacion del sistema:");
        System.out.println("   Hora actual del sistema: " + TimeService.getInstance().now());
        System.out.println("   Base de datos: " + DatabaseConfig.getDatabaseInfo());
        System.out.println("   Modo offline: " + (DatabaseConfig.isOfflineMode() ? "SI" : "NO"));

        if (DatabaseConfig.isOfflineMode()) {
            System.out.println("   Razon del modo offline: " + DatabaseConfig.getOfflineReason());
        }

        // Información de memoria
        Runtime runtime = Runtime.getRuntime();
        long totalMemory = runtime.totalMemory() / 1024 / 1024;
        long freeMemory = runtime.freeMemory() / 1024 / 1024;
        long usedMemory = totalMemory - freeMemory;

        System.out.printf("   Memoria: %d MB usados / %d MB total%n", usedMemory, totalMemory);
        System.out.println("   Java: " + System.getProperty("java.version"));
        System.out.println("   OS: " + System.getProperty("os.name") + " " + System.getProperty("os.version"));
    }

    /**
     * Configurar la interfaz de usuario
     */
    private void setupUI(Stage primaryStage) throws Exception {
        // Carga de fuentes
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

        // Configurar el cierre limpio de la aplicación
        setupShutdownHook(primaryStage);
    }

    /**
     * Configurar el cierre limpio de la aplicación
     */
    private void setupShutdownHook(Stage primaryStage) {
        primaryStage.setOnCloseRequest(event -> {
            System.out.println("Cerrando aplicacion y apagando servicios...");

            try {
                // Cerrar servicios en orden inverso
                if (notificationService != null && notificationService.isRunning()) {
                    notificationService.shutdown();
                    System.out.println("   NotificationService detenido");
                }

                OfflineMailService.getInstance().shutdown();
                System.out.println("   OfflineMailService detenido");

                OfflineSyncService.getInstance().shutdown();
                System.out.println("   OfflineSyncService detenido");

                EventService.getInstance().shutdown();
                System.out.println("   EventService detenido");

                if (emailService != null) {
                    MailService.shutdown();
                    System.out.println("   MailService detenido");
                }

                TimeService.getInstance().shutdown();
                System.out.println("   TimeService detenido");

                DatabaseConfig.closeDataSource();
                System.out.println("   Pool de conexiones cerrado");

                System.out.println("Todos los servicios detenidos correctamente");

            } catch (Exception e) {
                System.err.println("Error durante el cierre: " + e.getMessage());
            }

            // Salir de la aplicación después de un breve delay
            new Thread(() -> {
                try {
                    Thread.sleep(1000);
                    System.out.println("Saliendo de la aplicacion. Hasta pronto!");
                    System.exit(0);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }).start();
        });
    }

    /**
     * Muestra una advertencia cuando se detecta manipulación del reloj
     */
    private void showTimeManipulationWarning() {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Advertencia de Seguridad");
        alert.setHeaderText("Posible manipulacion de fecha detectada");
        alert.setContentText("El sistema ha detectado que la fecha/hora de tu dispositivo podria haber sido modificada. " +
                "Esto puede afectar el funcionamiento de la aplicacion y la programacion de eventos.\n\n" +
                "Por favor, verifica que la fecha y hora del sistema sean correctas.");
        alert.showAndWait();
    }

    /**
     * Mostrar alerta de modo offline - MEJORADO
     */
    private void showOfflineModeAlert() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Modo Offline");
        alert.setHeaderText("Aplicacion funcionando sin conexion");
        alert.setContentText("No se pudo conectar a la base de datos en linea. " +
                "La aplicacion funcionara en modo offline y sincronizara los datos " +
                "automaticamente cuando se restaure la conexion.\n\n" +
                "Razon: " + DatabaseConfig.getOfflineReason());
        alert.showAndWait();
    }

    /**
     * Mostrar diálogo de error crítico
     */
    private void showCriticalErrorDialog(Exception e) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Error Critico");
            alert.setHeaderText("Error durante la inicializacion");
            alert.setContentText("La aplicacion encontro un error critico durante el inicio:\n\n" +
                    e.getMessage() + "\n\n" +
                    "La aplicacion se cerrara. Por favor, contacta al soporte tecnico.");
            alert.showAndWait();
        });
    }

    /**
     * Cargar las fuentes Inria Sans
     */
    private void loadInriaSansFonts() {
        try {
            Font.loadFont(getClass().getResourceAsStream("/fonts/InriaSans-Regular.ttf"), 12);
            Font.loadFont(getClass().getResourceAsStream("/fonts/InriaSans-Bold.ttf"), 12);
            Font.loadFont(getClass().getResourceAsStream("/fonts/InriaSans-Italic.ttf"), 12);
            Font.loadFont(getClass().getResourceAsStream("/fonts/InriaSans-BoldItalic.ttf"), 12);
            Font.loadFont(getClass().getResourceAsStream("/fonts/InriaSans-Light.ttf"), 12);
            Font.loadFont(getClass().getResourceAsStream("/fonts/InriaSans-LightItalic.ttf"), 12);
            Font.loadFont(getClass().getResourceAsStream("/fonts/InriaSans-SemiBold.ttf"), 12);

            System.out.println("   Fuentes Inria Sans cargadas exitosamente");
        } catch (Exception e) {
            System.err.println("   Error al cargar las fuentes Inria Sans: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Obtener servicio de email (singleton)
     */
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

    /**
     * Métodos de utilidad para gestión del estado - MEJORADOS
     */
    public static String getCurrentStatus() {
        StringBuilder status = new StringBuilder();
        status.append("Estado de la aplicacion iTHERA:\n");
        status.append("- Base de datos: ").append(DatabaseConfig.getDatabaseInfo()).append("\n");
        status.append("- Hora actual: ").append(TimeService.getInstance().now()).append("\n");

        OfflineSyncService.SyncStats stats = OfflineSyncService.getInstance().getSyncStats();
        status.append("- Sincronizacion: ").append(stats.toString()).append("\n");

        return status.toString();
    }

    public static boolean isOfflineMode() {
        return DatabaseConfig.isOfflineMode();
    }

    /**
     * MÉTODO MEJORADO: Intentar reconexión manual
     */
    public static boolean attemptReconnection() {
        System.out.println("Intentando reconexion manual...");

        boolean reconnected = DatabaseConfig.attemptReconnection();

        if (reconnected) {
            System.out.println("Reconexion manual exitosa!");

            // Disparar sincronización automática
            OfflineSyncService.getInstance().forceSyncNow();

            return true;
        } else {
            System.out.println("Reconexion manual fallo");
            return false;
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}