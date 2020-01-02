package ru.andreymarkelov.atlas.plugins.promjiraexporter.service;

import io.prometheus.client.Collector;

import java.util.List;

public interface ScheduledMetricEvaluator {
    long getTotalAttachmentSize();

    long getLastExecutionTimestamp();

    void restartScraping(int newDelay);

    int getDelay();

    void setDelay(int delay);

    List<Collector.MetricFamilySamples> collect();
}
