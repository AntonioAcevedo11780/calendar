package com.utez.calendario.services;

import com.utez.calendario.models.Event;
import jakarta.mail.*;
import jakarta.mail.internet.*;
import java.io.UnsupportedEncodingException;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

public class MailService {

    // Patrón para validar emails
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,6}$", Pattern.CASE_INSENSITIVE);

    private final String username;
    private final String password;
    private final String senderName;
    private final Properties props;

    // Cache para evitar envíos duplicados
    private static final Set<String> sentVerificationEmails = ConcurrentHashMap.newKeySet();
    private static final ScheduledExecutorService cooldownScheduler = Executors.newScheduledThreadPool(2);
    private static final long VERIFICATION_COOLDOWN_MINUTES = 5; // 5 minutos de enfriamiento

    public MailService(String host, int port, String username, String password, String senderName) {
        this.username = username;
        this.password = password;
        this.senderName = senderName;

        this.props = new Properties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.host", host);
        props.put("mail.smtp.port", port);
        props.put("mail.smtp.ssl.trust", "smtp.gmail.com");
        props.put("mail.smtp.ssl.protocols", "TLSv1.2");
        props.put("mail.smtp.connectiontimeout", "5000");
        props.put("mail.smtp.timeout", "5000");
    }

    public void sendVerificationEmail(String recipient, String verificationCode) throws MessagingException {
        // Validaciones robustas
        if (recipient == null || recipient.isBlank()) {
            throw new IllegalArgumentException("El destinatario no puede estar vacío");
        }

        if (!EMAIL_PATTERN.matcher(recipient).matches()) {
            throw new IllegalArgumentException("Formato de email inválido: " + recipient);
        }

        if (verificationCode == null || verificationCode.isBlank()) {
            throw new IllegalArgumentException("El código de verificación no puede estar vacío");
        }

        // Verificar si ya se envió recientemente
        if (sentVerificationEmails.contains(recipient)) {
            System.out.println("⏳ Correo de verificación ya enviado recientemente a " + recipient);
            return;
        }

        Session session = Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(username, password);
            }
        });

        try {
            Message message = new MimeMessage(session);

            // Personalizar remitente con nombre
            message.setFrom(new InternetAddress(username, senderName));

            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(recipient));
            message.setSubject("Verifica tu cuenta - Ithera");

            // Contenido del correo
            String htmlContent = "<!DOCTYPE html>"
                    + "<html>"
                    + "<head><meta charset='UTF-8'><title>Verificación</title></head>"
                    + "<body style='margin:0; padding:0; font-family: Arial, sans-serif; background-color: #f5f7fa;'>"
                    + "  <div style='max-width: 600px; margin: 20px auto; background: white; border-radius: 10px; box-shadow: 0 0 20px rgba(0,0,0,0.1); overflow: hidden;'>"
                    + "    <div style='background: linear-gradient(135deg, #2c4a6b, #3a6ea5); padding: 30px; text-align: center;'>"
                    + "      <h1 style='color: white; margin:0; font-size: 28px;'>Ithera Calendar</h1>"
                    + "    </div>"
                    + "    <div style='padding: 30px;'>"
                    + "      <h2 style='color: #2c4a6b; margin-top: 0;'>Verificación de cuenta</h2>"
                    + "      <p style='color: #555; line-height: 1.6;'>Gracias por registrarte en Ithera. Usa el siguiente código para completar tu registro:</p>"
                    + "      <div style='background: #f0f7ff; border-radius: 8px; padding: 20px; text-align: center; margin: 30px 0; font-size: 28px; font-weight: bold; letter-spacing: 4px; color: #2c4a6b; border: 1px dashed #3a6ea5;'>"
                    +          verificationCode
                    + "      </div>"
                    + "      <p style='color: #777; font-size: 14px;'>Este código expirará en 15 minutos. Si no realizaste esta solicitud, por favor ignora este mensaje.</p>"
                    + "      <p style='margin-top: 30px; color: #555;'>Saludos,<br><strong>El equipo de Ithera</strong></p>"
                    + "    </div>"
                    + "    <div style='background: #f5f7fa; padding: 20px; text-align: center; color: #777; font-size: 12px; border-top: 1px solid #eaeaea;'>"
                    + "      <p>© " + java.time.Year.now().getValue() + " Ithera Calendar. Todos los derechos reservados.</p>"
                    + "      <p style='margin-top: 5px; font-size: 11px; color: #999;'>Este es un correo automático, por favor no respondas a este mensaje.</p>"
                    + "    </div>"
                    + "  </div>"
                    + "</body>"
                    + "</html>";

            message.setContent(htmlContent, "text/html; charset=utf-8");
            Transport.send(message);

            // Registrar envío en caché
            sentVerificationEmails.add(recipient);

            // Programar eliminación después del tiempo de enfriamiento
            cooldownScheduler.schedule(() -> {
                sentVerificationEmails.remove(recipient);
                System.out.println("✅ Cooldown completado para " + recipient);
            }, VERIFICATION_COOLDOWN_MINUTES, TimeUnit.MINUTES);

        } catch (UnsupportedEncodingException e) {
            throw new MessagingException("Error en codificación del nombre del remitente", e);
        } catch (MessagingException e) {
            System.err.println("Error enviando email a " + recipient + ": " + e.getMessage());
            throw new MessagingException("No se pudo enviar el correo de verificación", e);
        }
    }

    // Generar código de 4 dígitos
    public static String generateVerificationCode() {
        return String.format("%04d", (int) (Math.random() * 10000));
    }

    public void sendEventReminder(String recipient, Event event, long minutesBefore) throws MessagingException {
        if (recipient == null || recipient.isBlank()) {
            throw new IllegalArgumentException("El destinatario no puede estar vacío");
        }

        if (!EMAIL_PATTERN.matcher(recipient).matches()) {
            throw new IllegalArgumentException("Formato de email inválido: " + recipient);
        }

        if (event == null) {
            throw new IllegalArgumentException("El evento no puede ser nulo");
        }

        Session session = Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(username, password);
            }
        });

        try {
            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress(username, senderName));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(recipient));

            // Personalizar asunto con tiempo restante
            String timeUnit = minutesBefore >= 60 ? "horas" : "minutos";
            long timeValue = minutesBefore >= 60 ? minutesBefore / 60 : minutesBefore;
            String subject = String.format("Evento próximo: %s en %d %s",
                    event.getTitle(), timeValue, timeUnit);
            message.setSubject(subject);

            // Formateadores de fecha y hora
            DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM 'de' yyyy", Locale.forLanguageTag("es"));
            DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("h:mm a");

            String formattedDate = event.getStartDate().format(dateFormatter);
            String formattedTime = event.getStartDate().format(timeFormatter);

            // Contenido HTML del correo
            String htmlContent = "<!DOCTYPE html>"
                    + "<html>"
                    + "<head><meta charset='UTF-8'><title>Recordatorio de Evento</title></head>"
                    + "<body style='margin:0; padding:0; font-family: Arial, sans-serif; background-color: #f5f7fa;'>"
                    + "  <div style='max-width: 600px; margin: 20px auto; background: white; border-radius: 10px; box-shadow: 0 0 20px rgba(0,0,0,0.1); overflow: hidden;'>"
                    + "    <div style='background: linear-gradient(135deg, #2c4a6b, #3a6ea5); padding: 30px; text-align: center;'>"
                    + "      <h1 style='color: white; margin:0; font-size: 28px;'>Ithera Calendar</h1>"
                    + "    </div>"
                    + "    <div style='padding: 30px;'>"
                    + "      <h2 style='color: #2c4a6b; margin-top: 0;'>Recordatorio de evento</h2>"
                    + "      <p style='color: #555; line-height: 1.6;'>Tienes un evento programado que comenzará pronto:</p>"
                    + "      <div style='background: #f0f7ff; border-radius: 8px; padding: 20px; margin: 30px 0; border: 1px dashed #3a6ea5;'>"
                    + "        <h3 style='margin-top:0; color: #2c4a6b;'>" + event.getTitle() + "</h3>"
                    + "        <p style='color: #555;'><strong>Fecha:</strong> " + formattedDate + "</p>"
                    + "        <p style='color: #555;'><strong>Hora:</strong> " + formattedTime + "</p>"
                    + (event.getLocation() != null && !event.getLocation().isEmpty() ?
                    "<p style='color: #555;'><strong>Ubicación:</strong> " + event.getLocation() + "</p>" : "")
                    + (event.getDescription() != null && !event.getDescription().isEmpty() ?
                    "<p style='color: #555;'><strong>Descripción:</strong> " + event.getDescription() + "</p>" : "")
                    + "      </div>"
                    + "      <p style='color: #777;'>Recuerda que este evento comienza en <strong>" + timeValue + " " + timeUnit + "</strong>.</p>"
                    + "      <p style='margin-top: 30px; color: #555;'>Saludos,<br><strong>El equipo de Ithera</strong></p>"
                    + "    </div>"
                    + "    <div style='background: #f5f7fa; padding: 20px; text-align: center; color: #777; font-size: 12px; border-top: 1px solid #eaeaea;'>"
                    + "      <p>© " + java.time.Year.now().getValue() + " Ithera Calendar. Todos los derechos reservados.</p>"
                    + "      <p style='margin-top: 5px; font-size: 11px; color: #999;'>Este es un correo automático, por favor no respondas a este mensaje.</p>"
                    + "    </div>"
                    + "  </div>"
                    + "</body>"
                    + "</html>";

            message.setContent(htmlContent, "text/html; charset=utf-8");
            Transport.send(message);
            System.out.println("✓ Recordatorio enviado a " + recipient + " para evento: " + event.getTitle());

        } catch (UnsupportedEncodingException e) {
            throw new MessagingException("Error en codificación del nombre del remitente", e);
        } catch (MessagingException e) {
            System.err.println("Error enviando recordatorio a " + recipient + ": " + e.getMessage());
            throw new MessagingException("No se pudo enviar el recordatorio", e);
        }
    }

    // Metodo para apagar el scheduler al cerrar la aplicación
    public static void shutdown() {
        cooldownScheduler.shutdown();
        try {
            if (!cooldownScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                cooldownScheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            cooldownScheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}