package ru.andreymarkelov.atlas.service;

import io.prometheus.client.Collector;

public interface MetricCollector {
    Collector getCollector();
}
