package com.utez.calendario.controllers;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.time.*;
import java.time.format.TextStyle;
import java.util.Locale;

public class CalendarYearController {
    @FXML private Label lblYear;
    @FXML private Button btnPrevYear, btnNextYear;
    @FXML private GridPane gridYear;
    @FXML private TextField searchField;

    private int currentYear = Year.now().getValue();

    @FXML
    public void initialize() {
        lblYear.setText(String.valueOf(currentYear));
        renderYear(currentYear);

        btnPrevYear.setOnAction(e -> {
            currentYear--;
            lblYear.setText(String.valueOf(currentYear));
            renderYear(currentYear);
        });

        btnNextYear.setOnAction(e -> {
            currentYear++;
            lblYear.setText(String.valueOf(currentYear));
            renderYear(currentYear);
        });
    }

    private void renderYear(int year) {
        gridYear.getChildren().clear();
        int monthIndex = 0;
        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 3; col++) {
                VBox miniMonth = buildMiniCalendar(year, monthIndex);
                gridYear.add(miniMonth, col, row);
                monthIndex++;
            }
        }
    }

    private VBox buildMiniCalendar(int year, int month) {
        VBox vbox = new VBox(4);
        vbox.setStyle("-fx-background-color:white;-fx-border-color:#e1e4e8;-fx-border-radius:8;-fx-background-radius:8;-fx-padding:10 8 12 8;");
        vbox.setPrefWidth(180);

        // Nombre del mes
        Label lblMonth = new Label(Month.of(month + 1).getDisplayName(TextStyle.FULL, new Locale("es")));
        lblMonth.setStyle("-fx-font-size:16px;-fx-font-weight:600;-fx-text-fill:#4E6688;");
        lblMonth.setMaxWidth(Double.MAX_VALUE);
        lblMonth.setAlignment(javafx.geometry.Pos.CENTER);

        // Días de la semana
        HBox weekDays = new HBox(3);
        weekDays.setAlignment(javafx.geometry.Pos.CENTER);
        for (DayOfWeek dow : DayOfWeek.values()) {
            Label day = new Label(dow.getDisplayName(TextStyle.SHORT, new Locale("es")).substring(0,2));
            day.setStyle("-fx-font-size:11px;-fx-text-fill:#555;-fx-font-weight:bold;");
            day.setPrefWidth(22);
            day.setAlignment(javafx.geometry.Pos.CENTER);
            weekDays.getChildren().add(day);
        }

        // Días del mes (grid)
        GridPane daysGrid = new GridPane();
        daysGrid.setHgap(2);
        daysGrid.setVgap(2);
        daysGrid.setAlignment(javafx.geometry.Pos.CENTER);

        LocalDate firstDay = LocalDate.of(year, month + 1, 1);
        int lengthOfMonth = firstDay.lengthOfMonth();
        int startDay = firstDay.getDayOfWeek().getValue() % 7; // Lunes=1,...,Domingo=7=>0

        int dayNum = 1;
        for (int week = 0; week < 6 && dayNum <= lengthOfMonth; week++) {
            for (int dow = 0; dow < 7; dow++) {
                if ((week == 0 && dow < startDay) || dayNum > lengthOfMonth) {
                    daysGrid.add(new Label(" "), dow, week);
                } else {
                    Label dayLabel = new Label(String.valueOf(dayNum));
                    dayLabel.setStyle("-fx-font-size:12px;-fx-text-fill:#1c1e21;");
                    dayLabel.setPrefWidth(22);
                    dayLabel.setAlignment(javafx.geometry.Pos.CENTER);
                    // (Opcional: destacados, eventos, etc)
                    daysGrid.add(dayLabel, dow, week);
                    dayNum++;
                }
            }
        }

        vbox.getChildren().addAll(lblMonth, weekDays, daysGrid);
        return vbox;
    }
}