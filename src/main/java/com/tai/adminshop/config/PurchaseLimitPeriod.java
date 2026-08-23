package com.tai.adminshop.config;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.WeekFields;
import java.util.Locale;

public enum PurchaseLimitPeriod {
    NONE,
    EIGHT_HOURS,
    DAILY,
    WEEKLY,
    MONTHLY;

    private static final DateTimeFormatter DAILY_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter MONTHLY_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");

    public static PurchaseLimitPeriod parse(String value) {
        if (value == null || value.isBlank()) {
            return NONE;
        }
        try {
            return PurchaseLimitPeriod.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return NONE;
        }
    }

    public static PurchaseLimitPeriod parseStrict(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Period is required");
        }
        try {
            return PurchaseLimitPeriod.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unsupported period: " + value);
        }
    }

    public Window currentWindow() {
        ZonedDateTime now = ZonedDateTime.now(ZoneId.systemDefault());
        return switch (this) {
            case NONE -> new Window("NONE", now);
            case DAILY -> dailyWindow(now);
            case WEEKLY -> weeklyWindow(now);
            case MONTHLY -> monthlyWindow(now);
            case EIGHT_HOURS -> eightHourWindow(now);
        };
    }

    public String defaultLabel(int limit) {
        return switch (this) {
            case NONE -> "";
            case DAILY -> limit + " per day";
            case WEEKLY -> limit + " per week";
            case MONTHLY -> limit + " per month";
            case EIGHT_HOURS -> limit + " per 8 hours";
        };
    }

    private static Window dailyWindow(ZonedDateTime now) {
        LocalDate date = now.toLocalDate();
        ZonedDateTime nextReset = date.plusDays(1).atStartOfDay(now.getZone());
        return new Window(DAILY_FORMAT.format(date), nextReset);
    }

    private static Window monthlyWindow(ZonedDateTime now) {
        YearMonth month = YearMonth.from(now);
        ZonedDateTime nextReset = month.plusMonths(1).atDay(1).atStartOfDay(now.getZone());
        return new Window(MONTHLY_FORMAT.format(month), nextReset);
    }

    private static Window weeklyWindow(ZonedDateTime now) {
        WeekFields weekFields = WeekFields.ISO;
        int weekYear = now.get(weekFields.weekBasedYear());
        int week = now.get(weekFields.weekOfWeekBasedYear());
        ZonedDateTime nextReset = now.toLocalDate()
                .with(weekFields.dayOfWeek(), 1)
                .plusWeeks(1)
                .atStartOfDay(now.getZone());
        return new Window(String.format(Locale.ROOT, "%04d-W%02d", weekYear, week), nextReset);
    }

    private static Window eightHourWindow(ZonedDateTime now) {
        LocalDate date = now.toLocalDate();
        int hour = now.getHour();
        int windowStartHour = hour < 8 ? 0 : hour < 16 ? 8 : 16;
        LocalDateTime start = LocalDateTime.of(date, LocalTime.of(windowStartHour, 0));
        ZonedDateTime nextReset = start.plusHours(8).atZone(now.getZone());
        return new Window(DAILY_FORMAT.format(date) + ":" + windowStartHour, nextReset);
    }

    public record Window(String key, ZonedDateTime nextReset) {
        public Duration timeUntilReset() {
            Duration duration = Duration.between(ZonedDateTime.now(ZoneId.systemDefault()), nextReset);
            return duration.isNegative() ? Duration.ZERO : duration;
        }
    }
}
