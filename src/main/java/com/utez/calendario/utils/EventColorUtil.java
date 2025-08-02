package com.utez.calendario.utils;

import com.utez.calendario.models.Calendar;
import com.utez.calendario.models.Event;
import javafx.scene.layout.Region;
import java.util.HashMap;
import java.util.Map;

/**
 * Utilidad para manejar los colores de los eventos según el calendario al que pertenecen
 */
public class EventColorUtil {
    // Cache de colores de calendarios para evitar consultas repetidas a la BD
    private static Map<String, String> calendarColorCache = new HashMap<>();

    // Colores predeterminados
    private static final Map<String, String> DEFAULT_CALENDAR_COLORS = Map.of(
            "Mis Clases", "#4CAF50", // Verde
            "Tareas y Proyectos", "#2196F3", // Azul
            "Personal", "#FF9800", // Naranja
            "Exámenes", "#212121"  // Negro
    );

    /**
     * Aplica el color correspondiente al elemento de un evento
     */
    public static void applyEventColor(Event event, Region eventNode) {
        String color = getEventColor(event);
        eventNode.setStyle("-fx-border-color: " + color + "; -fx-border-width: 0 0 0 3;");
    }

    /**
     * Obtiene el color para un evento según su calendario
     */
    public static String getEventColor(Event event) {
        if (event == null || event.getCalendarId() == null) {
            return "#7F8C8D"; // Color gris predeterminado
        }

        // Intentar obtener de caché
        String calendarId = event.getCalendarId();
        if (calendarColorCache.containsKey(calendarId)) {
            return calendarColorCache.get(calendarId);
        }

        // Buscar en la base de datos
        String color = findCalendarColor(calendarId);

        // Guardar en caché
        if (color != null && !color.isEmpty()) {
            calendarColorCache.put(calendarId, color);
            return color;
        }

        // Si no se encuentra, devolver color predeterminado
        return "#7F8C8D"; // Gris
    }

    /**
     * Busca el color de un calendario en la base de datos
     */
    private static String findCalendarColor(String calendarId) {
        try {
            for (Calendar cal : Calendar.getAllActiveCalendars()) {
                if (cal.getCalendarId().equals(calendarId)) {
                    return cal.getColor();
                }
            }
        } catch (Exception e) {
            System.err.println("Error al buscar color del calendario: " + e.getMessage());
        }
        return "#7F8C8D"; // Gris como fallback
    }

    /**
     * Limpia la caché de colores
     */
    public static void clearCache() {
        calendarColorCache.clear();
    }

    /**
     * Devuelve el color predeterminado para un tipo de calendario
     */
    public static String getDefaultCalendarColor(String calendarType) {
        return DEFAULT_CALENDAR_COLORS.getOrDefault(calendarType, "#3498DB");
    }
}