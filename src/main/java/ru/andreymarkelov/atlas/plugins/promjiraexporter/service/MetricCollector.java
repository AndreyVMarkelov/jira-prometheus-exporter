package ru.andreymarkelov.atlas.plugins.promjiraexporter.service;

import io.prometheus.client.Collector;
import ru.andreymarkelov.atlas.plugins.promjiraexporter.util.ExceptionRunnable;

import javax.servlet.ServletException;
import java.io.IOException;

public interface MetricCollector {
    Collector getCollector();
    void issueUpdateCounter(String projectKey, String issueKey, String eventType, String username);
    void issueViewCounter(String projectKey, String issueKey, String username);
    void userLoginCounter(String username);
    void userLogoutCounter(String username);
    void dashboardViewCounter(Long dashboardId, String username);
    void requestDuration(String level1, ExceptionRunnable runnable) throws IOException, ServletException;
}
