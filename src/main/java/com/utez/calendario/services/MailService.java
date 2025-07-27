package com.utez.calendario.services;

import jakarta.mail.*;
import jakarta.mail.internet.*;
import java.io.UnsupportedEncodingException;
import java.util.Properties;
import java.util.regex.Pattern;

public class MailService {

    // Patrón para validar emails
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,6}$", Pattern.CASE_INSENSITIVE);

    private final String username;
    private final String password;
    private final String senderName;  // Nuevo campo para nombre del remitente
    private final Properties props;

    public MailService(String host, int port, String username, String password, String senderName) {
        this.username = username;
        this.password = password;
        this.senderName = senderName;  // Nombre personalizado para mostrar

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
}