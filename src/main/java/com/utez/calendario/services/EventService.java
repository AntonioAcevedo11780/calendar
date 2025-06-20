package com.utez.calendario.services;

import com.utez.calendario.config.DatabaseConfig;
import com.utez.calendario.models.Event;
import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;


public class EventService {
    private static EventService instance;

    private EventService() {}

    public static EventService getInstance() {
        if (instance == null) {
            instance = new EventService();
        }
        return instance;
    }

    public List<Event> getEventsForMonth(String userId, LocalDate date) {
        List<Event> events = new ArrayList<>();

        LocalDate startOfMonth = date.withDayOfMonth(1);
        LocalDate endOfMonth = date.withDayOfMonth(date.lengthOfMonth());

        String sql = """
            SELECT e.* FROM EVENTS e
            INNER JOIN CALENDARS c ON e.CALENDAR_ID = c.CALENDAR_ID
            WHERE c.OWNER_ID = ? AND e.ACTIVE = 'Y' AND c.ACTIVE = 'Y'
            AND DATE(e.START_DATE) >= ? AND DATE(e.START_DATE) <= ?
            ORDER BY e.START_DATE
        """;

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, userId);
            pstmt.setDate(2, Date.valueOf(startOfMonth));
            pstmt.setDate(3, Date.valueOf(endOfMonth));

            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                Event event = new Event();
                event.setEventId(rs.getString("EVENT_ID"));
                event.setCalendarId(rs.getString("CALENDAR_ID"));
                event.setCreatorId(rs.getString("CREATOR_ID"));
                event.setTitle(rs.getString("TITLE"));
                event.setDescription(rs.getString("DESCRIPTION"));

                Timestamp startDate = rs.getTimestamp("START_DATE");
                if (startDate != null) {
                    event.setStartDate(startDate.toLocalDateTime());
                }

                Timestamp endDate = rs.getTimestamp("END_DATE");
                if (endDate != null) {
                    event.setEndDate(endDate.toLocalDateTime());
                }

                events.add(event);
            }

            System.out.println("✓ Encontrados " + events.size() + " eventos para " + date.getMonth());

        } catch (SQLException e) {
            System.err.println("Error obteniendo eventos: " + e.getMessage());
            e.printStackTrace();
        }

        return events;
    }

    public List<Event> getEventsForDate(String userId, LocalDate date) {
        List<Event> events = new ArrayList<>();

        String sql = """
            SELECT e.* FROM EVENTS e
            INNER JOIN CALENDARS c ON e.CALENDAR_ID = c.CALENDAR_ID
            WHERE c.OWNER_ID = ? AND e.ACTIVE = 'Y' AND c.ACTIVE = 'Y'
            AND DATE(e.START_DATE) = ?
            ORDER BY e.START_DATE
        """;

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, userId);
            pstmt.setDate(2, Date.valueOf(date));

            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                Event event = new Event();
                event.setEventId(rs.getString("EVENT_ID"));
                event.setTitle(rs.getString("TITLE"));

                Timestamp startDate = rs.getTimestamp("START_DATE");
                if (startDate != null) {
                    event.setStartDate(startDate.toLocalDateTime());
                }

                events.add(event);
            }

        } catch (SQLException e) {
            System.err.println("Error obteniendo eventos del día: " + e.getMessage());
        }

        return events;
    }
}