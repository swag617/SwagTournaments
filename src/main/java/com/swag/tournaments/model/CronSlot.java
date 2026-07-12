package com.swag.tournaments.model;

import java.time.DayOfWeek;
import java.time.LocalTime;

public final class CronSlot {

    private final DayOfWeek day;
    private final LocalTime time;
    private final int durationMinutes;
    private final String templateId;

    /**
     * @param day           null means DAILY (fires every day)
     * @param time          the wall-clock time this slot fires
     * @param durationMinutes tournament duration
     * @param templateId    template this slot belongs to
     */
    public CronSlot(DayOfWeek day, LocalTime time, int durationMinutes, String templateId) {
        this.day = day;
        this.time = time;
        this.durationMinutes = durationMinutes;
        this.templateId = templateId;
    }

    public DayOfWeek getDay() {
        return day;
    }

    public LocalTime getTime() {
        return time;
    }

    public int getDurationMinutes() {
        return durationMinutes;
    }

    public String getTemplateId() {
        return templateId;
    }

    public boolean matchesNow(java.time.LocalDateTime now) {
        if (!now.toLocalTime().truncatedTo(java.time.temporal.ChronoUnit.MINUTES).equals(time)) {
            return false;
        }
        return day == null || day == now.getDayOfWeek();
    }
}
