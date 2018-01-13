package ru.andreymarkelov.atlas.plugins.promjiraexporter.service;

import com.atlassian.application.api.Application;
import com.atlassian.application.api.ApplicationManager;
import com.atlassian.jira.cluster.ClusterManager;
import com.atlassian.jira.config.util.AttachmentPathManager;
import com.atlassian.jira.issue.IssueManager;
import com.atlassian.jira.project.ProjectManager;
import com.atlassian.jira.user.util.UserUtil;
import com.atlassian.jira.util.system.SystemInfoUtils;
import com.atlassian.jira.web.session.currentusers.JiraUserSessionTracker;
import com.atlassian.sal.api.license.SingleProductLicenseDetailsView;
import io.prometheus.client.Collector;
import io.prometheus.client.Counter;
import io.prometheus.client.Gauge;
import io.prometheus.client.Histogram;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import ru.andreymarkelov.atlas.plugins.promjiraexporter.util.ExceptionRunnable;

import javax.servlet.ServletException;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ForkJoinPool;

import static com.atlassian.jira.application.ApplicationKeys.CORE;
import static java.util.concurrent.TimeUnit.DAYS;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.apache.commons.lang3.StringUtils.defaultString;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

public class MetricCollectorImpl extends Collector implements MetricCollector, DisposableBean {
    private static final Logger log = LoggerFactory.getLogger(MetricCollectorImpl.class);

    private ForkJoinPool forkJoinPool = new ForkJoinPool();

    private final IssueManager issueManager;
    private final ProjectManager projectManager;
    private final AttachmentPathManager attachmentPathManager;
    private final JiraUserSessionTracker jiraUserSessionTracker;
    private final ClusterManager clusterManager;
    private final UserUtil userUtil;
    private final SystemInfoUtils systemInfoUtils;
    private final ApplicationManager jiraApplicationManager;

    public MetricCollectorImpl(
            IssueManager issueManager,
            ProjectManager projectManager,
            AttachmentPathManager attachmentPathManager,
            ClusterManager clusterManager,
            UserUtil userUtil,
            SystemInfoUtils systemInfoUtils,
            ApplicationManager jiraApplicationManager) {
        this.issueManager = issueManager;
        this.projectManager = projectManager;
        this.attachmentPathManager = attachmentPathManager;
        this.jiraUserSessionTracker = JiraUserSessionTracker.getInstance();
        this.clusterManager = clusterManager;
        this.userUtil = userUtil;
        this.systemInfoUtils = systemInfoUtils;
        this.jiraApplicationManager = jiraApplicationManager;
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
            .help("Issues Per Project Gauge")
            .labelNames("projectKey")
            .create();

    private final Gauge sessionsGauge = Gauge.build()
            .name("jira_total_sessions_gauge")
            .help("Sessions Gauge")
            .labelNames("ip", "username", "requestsCount")
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

    @Override
    public Collector getCollector() {
        return this;
    }

    @Override
    public List<MetricFamilySamples> collect() {
        // resolve count issues
        forkJoinPool.submit(() -> {
            projectManager.getProjects()
                    .stream()
                    .parallel()
                    .forEach(x -> issuesGauge.labels(x.getKey()).set(issueManager.getIssueCountForProject(x.getId())));
        });

        // resolve sessions count
        forkJoinPool.submit(() -> {
            jiraUserSessionTracker.getSnapshot()
                    .stream()
                    .parallel()
                    .forEach(x -> sessionsGauge.labels(defaultString(x.getIpAddress()), defaultString(x.getUserName()), Long.toString(x.getRequestCount())).set(1d));
        });

        // resolve attachment size
        File attachmentDirectory = new File(attachmentPathManager.getAttachmentPath());
        try {
            long size = Files.walk(attachmentDirectory.toPath())
                    .filter(p -> p.toFile().isFile())
                    .mapToLong(p -> p.toFile().length())
                    .sum();
            totalAttachmentSizeGauge.set(size);
        } catch (Exception ex) {
            log.error("Cannot resolve attachments size", ex);
        }

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
        result.addAll(sessionsGauge.collect());
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
        forkJoinPool.shutdownNow();
    }
}
