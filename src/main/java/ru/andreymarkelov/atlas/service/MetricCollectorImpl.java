package ru.andreymarkelov.atlas.service;

import io.prometheus.client.Collector;
import io.prometheus.client.Counter;

import java.util.List;

public class MetricCollectorImpl extends Collector implements MetricCollector {
    private final Counter bulkDuplicatedMessages = Counter.build()
            .name("jira_events")
            .help("HTTP Proxy Bulk Duplicated Messages Count")
            .labelNames("sample")
            .create();

    @Override
    public Collector getCollector() {
        return this;
    }

    @Override
    public List<MetricFamilySamples> collect() {
        return bulkDuplicatedMessages.collect();
    }
}
