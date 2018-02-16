package ru.andreymarkelov.atlas.plugins.promjiraexporter.service;

public interface ScheduledMetricEvaluator {
    long getTotalAttachmentSize();

    long getLastExecutionTimestamp();

    void restartScraping(int newDelay);

    int getDelay();

    void setDelay(int delay);
}
