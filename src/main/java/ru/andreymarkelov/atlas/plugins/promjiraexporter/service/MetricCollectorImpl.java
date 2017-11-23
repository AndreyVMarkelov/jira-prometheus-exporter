package ru.andreymarkelov.atlas.plugins.promjiraexporter.service;

import io.prometheus.client.Collector;
import io.prometheus.client.Counter;
import io.prometheus.client.Histogram;
import ru.andreymarkelov.atlas.plugins.promjiraexporter.util.ExceptionRunnable;

import javax.servlet.ServletException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

public class MetricCollectorImpl extends Collector implements MetricCollector {
    private final Histogram requestDurationOnPath = Histogram.build()
            .name("jira_request_duration_on_path")
            .help("Request duration on path")
            .labelNames("path")
            .create();

    private final Counter issueUpdateCounter = Counter.build()
            .name("jira_issue_update_count")
            .help("Issue Update Count")
            .labelNames("projectKey", "issueKey", "eventType", "username")
            .create();

    private final Counter issueViewCounter = Counter.build()
            .name("jira_issue_view_count")
            .help("Issue View Count")
            .labelNames("projectKey", "issueKey", "username")
            .create();

    private final Counter userLoginCounter = Counter.build()
            .name("jira_user_login_count")
            .help("User Login Count")
            .labelNames("username")
            .create();

    private final Counter userLogoutCounter = Counter.build()
            .name("jira_user_logout_count")
            .help("User Logout Count")
            .labelNames("username")
            .create();

    private final Counter dashboardViewCounter = Counter.build()
            .name("jira_dashboard_view_count")
            .help("Dashboard View Count")
            .labelNames("dashboardId", "username")
            .create();

    @Override
    public void requestDuration(String level1, ExceptionRunnable runnable) throws IOException, ServletException {
        Histogram.Timer level1Timer = isNotBlank(level1) ? requestDurationOnPath.labels(level1).startTimer() : null;
        try {
            runnable.run();
        } finally {
            if (level1Timer != null) {
                level1Timer.observeDuration();
            }
        }
    }

    @Override
    public void issueUpdateCounter(String projectKey, String issueKey, String eventType, String username) {
        issueUpdateCounter.labels(projectKey, issueKey, eventType, username).inc();
    }

    @Override
    public void issueViewCounter(String projectKey, String issueKey, String username) {
        issueViewCounter.labels(projectKey, issueKey, username).inc();
    }

    @Override
    public void userLoginCounter(String username) {
        userLoginCounter.labels(username).inc();
    }

    @Override
    public void userLogoutCounter(String username) {
        userLogoutCounter.labels(username).inc();
    }

    @Override
    public void dashboardViewCounter(Long dashboardId, String username) {
        dashboardViewCounter.labels(Long.toString(dashboardId), username).inc();
    }

    @Override
    public Collector getCollector() {
        return this;
    }

    @Override
    public List<MetricFamilySamples> collect() {
        List<MetricFamilySamples> result = new ArrayList<>();
        result.addAll(issueUpdateCounter.collect());
        result.addAll(issueViewCounter.collect());
        result.addAll(userLoginCounter.collect());
        result.addAll(userLogoutCounter.collect());
        result.addAll(dashboardViewCounter.collect());
        result.addAll(requestDurationOnPath.collect());
        return result;
    }
}
