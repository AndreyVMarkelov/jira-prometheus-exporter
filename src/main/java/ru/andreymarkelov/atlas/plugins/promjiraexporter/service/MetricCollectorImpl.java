package ru.andreymarkelov.atlas.plugins.promjiraexporter.service;

import com.atlassian.jira.cluster.ClusterManager;
import com.atlassian.jira.config.util.AttachmentPathManager;
import com.atlassian.jira.issue.IssueManager;
import com.atlassian.jira.project.ProjectManager;
import com.atlassian.jira.web.session.currentusers.JiraUserSessionTracker;
import io.prometheus.client.Collector;
import io.prometheus.client.Counter;
import io.prometheus.client.Gauge;
import io.prometheus.client.Histogram;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.andreymarkelov.atlas.plugins.promjiraexporter.util.ExceptionRunnable;

import javax.servlet.ServletException;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.apache.commons.io.FileUtils.sizeOfDirectory;
import static org.apache.commons.lang3.StringUtils.defaultString;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

public class MetricCollectorImpl extends Collector implements MetricCollector {
    private static final Logger log = LoggerFactory.getLogger(MetricCollectorImpl.class);

    private final IssueManager issueManager;
    private final ProjectManager projectManager;
    private final AttachmentPathManager attachmentPathManager;
    private final JiraUserSessionTracker jiraUserSessionTracker;
    private final ClusterManager clusterManager;

    public MetricCollectorImpl(
            IssueManager issueManager,
            ProjectManager projectManager,
            AttachmentPathManager attachmentPathManager,
            ClusterManager clusterManager) {
        this.issueManager = issueManager;
        this.projectManager = projectManager;
        this.attachmentPathManager = attachmentPathManager;
        this.jiraUserSessionTracker = JiraUserSessionTracker.getInstance();
        this.clusterManager = clusterManager;
    }

    private final Gauge totalIssuesGauge = Gauge.build()
            .name("jira_total_issues_gauge")
            .help("Total Issues Per Project Gauge")
            .labelNames("projectKey")
            .create();

    private final Gauge totalSessionsGauge = Gauge.build()
            .name("jira_total_sessions_gauge")
            .help("Total Sessions Gauge")
            .labelNames("ip", "username", "requestsCount")
            .create();

    private final Gauge totalAttachmentSizeGauge = Gauge.build()
            .name("jira_total_attachment_size_gauge")
            .help("Total attachments Size Gauge")
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
        projectManager.getProjects()
                .forEach(x -> totalIssuesGauge.labels(x.getKey()).set(issueManager.getIssueCountForProject(x.getId())));

        // resolve sessions count
        jiraUserSessionTracker.getSnapshot()
                .forEach(x -> totalSessionsGauge.labels(defaultString(x.getIpAddress()), defaultString(x.getUserName()), Long.toString(x.getRequestCount())).set(1d));

        // resolve attachment size
        try {
            totalAttachmentSizeGauge.set(sizeOfDirectory(new File(attachmentPathManager.getAttachmentPath())));
        } catch (Exception ex) {
            log.error("Cannot resolve attachments size", ex);
        }

        // collect all metrics
        List<MetricFamilySamples> result = new ArrayList<>();
        result.addAll(issueUpdateCounter.collect());
        result.addAll(issueViewCounter.collect());
        result.addAll(userLoginCounter.collect());
        result.addAll(userLogoutCounter.collect());
        result.addAll(dashboardViewCounter.collect());
        result.addAll(requestDurationOnPath.collect());
        result.addAll(totalIssuesGauge.collect());
        result.addAll(totalSessionsGauge.collect());
        result.addAll(totalAttachmentSizeGauge.collect());
        return result;
    }
}
