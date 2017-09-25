package ru.andreymarkelov.atlas.service;

import io.prometheus.client.Collector;

public interface MetricCollector {
    Collector getCollector();
    void issueUpdateCounter(String projectKey, String issueKey, String username);
    void issueViewCounter(String projectKey, String issueKey, String username);
    void userLoginCounter(String username);
    void userLogoutCounter(String username);
    void dashboardViewCounter(Long dashboardId, String username);
}
