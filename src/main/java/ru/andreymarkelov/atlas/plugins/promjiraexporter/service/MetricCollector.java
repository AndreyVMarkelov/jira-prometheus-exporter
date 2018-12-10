package ru.andreymarkelov.atlas.plugins.promjiraexporter.service;

import io.prometheus.client.CollectorRegistry;
import ru.andreymarkelov.atlas.plugins.promjiraexporter.util.ExceptionRunnable;

import javax.servlet.ServletException;
import java.io.IOException;

public interface MetricCollector {
    CollectorRegistry getRegistry();
    void issueUpdateCounter(String projectKey, String eventType, String username);
    void issueViewCounter(String projectKey, String username);
    void userLoginCounter(String username);
    void userLogoutCounter(String username);
    void dashboardViewCounter(Long dashboardId, String username);
    void requestDuration(String path, ExceptionRunnable runnable) throws IOException, ServletException;
    void pluginEnabledCounter(String pluginKey);
    void pluginDisabledCounter(String pluginKey);
    void pluginUninstalledCounter(String pluginKey);
}
