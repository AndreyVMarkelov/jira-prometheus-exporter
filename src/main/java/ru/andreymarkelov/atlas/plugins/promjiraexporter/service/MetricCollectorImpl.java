package ru.andreymarkelov.atlas.plugins.promjiraexporter.service;

import com.atlassian.application.api.Application;
import com.atlassian.application.api.ApplicationManager;
import com.atlassian.jira.cluster.ClusterManager;
import com.atlassian.jira.issue.IssueManager;
import com.atlassian.jira.user.util.UserUtil;
import com.atlassian.jira.web.session.currentusers.JiraUserSession;
import com.atlassian.jira.web.session.currentusers.JiraUserSessionTracker;
import com.atlassian.sal.api.license.SingleProductLicenseDetailsView;
import io.prometheus.client.Collector;
import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.Counter;
import io.prometheus.client.Gauge;
import io.prometheus.client.Histogram;
import io.prometheus.client.hotspot.DefaultExports;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import ru.andreymarkelov.atlas.plugins.promjiraexporter.util.ExceptionRunnable;

import javax.servlet.ServletException;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static com.atlassian.jira.application.ApplicationKeys.CORE;
import static java.time.Instant.now;
import static java.util.Collections.emptyList;
import static java.util.concurrent.TimeUnit.DAYS;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

public class MetricCollectorImpl extends Collector implements MetricCollector, DisposableBean, InitializingBean {
    private static final Logger log = LoggerFactory.getLogger(MetricCollectorImpl.class);

    private final IssueManager issueManager;
    private final JiraUserSessionTracker jiraUserSessionTracker;
    private final ClusterManager clusterManager;
    private final UserUtil userUtil;
    private final ApplicationManager jiraApplicationManager;
    private final CollectorRegistry registry;

    public MetricCollectorImpl(
            IssueManager issueManager,
            ClusterManager clusterManager,
            UserUtil userUtil,
            ApplicationManager jiraApplicationManager) {
        this.issueManager = issueManager;
        this.jiraUserSessionTracker = JiraUserSessionTracker.getInstance();
        this.clusterManager = clusterManager;
        this.userUtil = userUtil;
        this.jiraApplicationManager = jiraApplicationManager;
        this.registry = CollectorRegistry.defaultRegistry;
    }

    private final Gauge maintenanceExpiryDaysGauge = Gauge.build()
            .name("jira_maintenance_expiry_days_gauge")
            .help("Maintenance Expiry Days Gauge")
            .create();

    private final Gauge allUsersGauge = Gauge.build()
            .name("jira_all_users_gauge")
            .help("All Users Gauge")
            .create();

    private final Gauge activeUsersGauge = Gauge.build()
            .name("jira_active_users_gauge")
            .help("Active Users Gauge")
            .create();

    private final Gauge allowedUsersGauge = Gauge.build()
            .name("jira_allowed_users_gauge")
            .help("Allowed Users Gauge")
            .create();

    private final Gauge issuesGauge = Gauge.build()
            .name("jira_total_issues_gauge")
            .help("Issues Gauge")
            .create();

    private final Gauge totalSessionsGauge = Gauge.build()
            .name("jira_total_sessions_gauge")
            .help("Sessions Gauge")
            .create();

    private final Gauge authorizedSessionsGauge = Gauge.build()
            .name("jira_authorized_sessions_gauge")
            .help("Authorized Sessions Gauge")
            .create();

    private final Gauge totalAttachmentSizeGauge = Gauge.build()
            .name("jira_total_attachment_size_gauge")
            .help("Total Attachments Size Gauge")
            .create();

    private final Gauge totalClusterNodeGauge = Gauge.build()
            .name("jira_total_cluster_nodes_gauge")
            .help("Total Cluster Nodes Gauge")
            .create();

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
    public void requestDuration(String path, ExceptionRunnable runnable) throws IOException, ServletException {
        Histogram.Timer level1Timer = isNotBlank(path) ? requestDurationOnPath.labels(path).startTimer() : null;
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

    private List<MetricFamilySamples> collectInternal() {
        // resolve count issues
        issuesGauge.set(issueManager.getIssueCount());

        // resolve sessions count
        List<JiraUserSession> snapshot = jiraUserSessionTracker.getSnapshot();
        totalSessionsGauge.set(snapshot.size());

        int authorizedSessions = snapshot.stream()
                .filter(e -> e.getUserName() != null)
                .collect(Collectors.toList())
                .size();
        authorizedSessionsGauge.set(authorizedSessions);

        // resolve cluster metrics
        totalClusterNodeGauge.set(clusterManager.getAllNodes().size());

        // users
        allUsersGauge.set(userUtil.getTotalUserCount());
        activeUsersGauge.set(userUtil.getActiveUserCount());

        // license
        SingleProductLicenseDetailsView licenseDetails = jiraApplicationManager.getApplication(CORE)
                .flatMap(Application::getLicense)
                .getOrNull();
        if (licenseDetails != null) {
            maintenanceExpiryDaysGauge.set(DAYS.convert(licenseDetails.getMaintenanceExpiryDate().getTime() - System.currentTimeMillis(), MILLISECONDS));
            allowedUsersGauge.set(licenseDetails.getNumberOfUsers());
        }

        // collect all metrics
        List<MetricFamilySamples> result = new ArrayList<>();
        result.addAll(issueUpdateCounter.collect());
        result.addAll(issueViewCounter.collect());
        result.addAll(userLoginCounter.collect());
        result.addAll(userLogoutCounter.collect());
        result.addAll(dashboardViewCounter.collect());
        result.addAll(requestDurationOnPath.collect());
        result.addAll(issuesGauge.collect());
        result.addAll(totalSessionsGauge.collect());
        result.addAll(authorizedSessionsGauge.collect());
        result.addAll(totalAttachmentSizeGauge.collect());
        result.addAll(totalClusterNodeGauge.collect());
        result.addAll(allUsersGauge.collect());
        result.addAll(activeUsersGauge.collect());
        result.addAll(allowedUsersGauge.collect());
        result.addAll(maintenanceExpiryDaysGauge.collect());
        return result;
    }

    @Override
    public void destroy() throws Exception {
        this.registry.unregister(this);
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        this.registry.register(this);
        DefaultExports.initialize();
    }

    @Override
    public CollectorRegistry getRegistry() {
        return registry;
    }

    @Override
    public List<MetricFamilySamples> collect() {
        Instant start = now();
        try {
            return collectInternal();
        } catch (Throwable throwable) {
            log.error("Error collect prometheus metrics", throwable);
            return emptyList();
        } finally {
            log.debug("Collect execution time is: {}ms", Duration.between(start, now()).toMillis());
        }
    }
}
