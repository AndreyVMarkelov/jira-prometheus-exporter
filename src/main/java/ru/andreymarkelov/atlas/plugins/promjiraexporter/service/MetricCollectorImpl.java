package ru.andreymarkelov.atlas.plugins.promjiraexporter.service;

import com.atlassian.application.api.ApplicationManager;
import com.atlassian.instrumentation.Instrument;
import com.atlassian.instrumentation.InstrumentRegistry;
import com.atlassian.jira.cluster.ClusterManager;
import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.issue.IssueManager;
import com.atlassian.jira.license.LicenseCountService;
import com.atlassian.jira.project.ProjectManager;
import com.atlassian.jira.user.util.UserManager;
import com.atlassian.jira.web.session.currentusers.JiraUserSession;
import com.atlassian.jira.web.session.currentusers.JiraUserSessionTracker;
import com.atlassian.sal.api.license.SingleProductLicenseDetailsView;
import io.prometheus.client.Collector;
import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.Counter;
import io.prometheus.client.Gauge;
import io.prometheus.client.Histogram;
import io.prometheus.client.hotspot.DefaultExports;
import org.ofbiz.core.entity.GenericEntityException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import ru.andreymarkelov.atlas.plugins.promjiraexporter.util.ExceptionRunnable;

import javax.servlet.ServletException;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import static com.atlassian.jira.instrumentation.InstrumentationName.CONCURRENT_REQUESTS;
import static com.atlassian.jira.instrumentation.InstrumentationName.DBCP_ACTIVE;
import static com.atlassian.jira.instrumentation.InstrumentationName.DBCP_IDLE;
import static com.atlassian.jira.instrumentation.InstrumentationName.DBCP_MAX;
import static com.atlassian.jira.instrumentation.InstrumentationName.DB_CONNECTIONS;
import static com.atlassian.jira.instrumentation.InstrumentationName.DB_CONNECTIONS_BORROWED;
import static com.atlassian.jira.instrumentation.InstrumentationName.DB_READS;
import static com.atlassian.jira.instrumentation.InstrumentationName.DB_WRITES;
import static com.atlassian.jira.instrumentation.InstrumentationName.HTTP_SESSION_OBJECTS;
import static com.atlassian.jira.instrumentation.InstrumentationName.REST_REQUESTS;
import static com.atlassian.jira.instrumentation.InstrumentationName.WEB_REQUESTS;
import static java.time.Instant.now;
import static java.util.Collections.emptyList;
import static java.util.Objects.nonNull;
import static java.util.concurrent.TimeUnit.DAYS;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

public class MetricCollectorImpl extends Collector implements MetricCollector, DisposableBean, InitializingBean {
    private static final Logger log = LoggerFactory.getLogger(MetricCollectorImpl.class);

    private final IssueManager issueManager;
    private final JiraUserSessionTracker jiraUserSessionTracker;
    private final ClusterManager clusterManager;
    private final UserManager userManager;
    private final LicenseCountService licenseCountService;
    private final ApplicationManager jiraApplicationManager;
    private final ScheduledMetricEvaluator scheduledMetricEvaluator;
    private final CollectorRegistry registry;
    private final InstrumentRegistry instrumentRegistry;
    private final ProjectManager projectManager;

    public MetricCollectorImpl(
            IssueManager issueManager,
            ClusterManager clusterManager,
            UserManager userManager,
            LicenseCountService licenseCountService,
            ApplicationManager jiraApplicationManager,
            ScheduledMetricEvaluator scheduledMetricEvaluator,
            InstrumentRegistry instrumentRegistry,
            ProjectManager projectManager) {
        this.issueManager = issueManager;
        this.jiraUserSessionTracker = JiraUserSessionTracker.getInstance();
        this.clusterManager = clusterManager;
        this.userManager = userManager;
        this.licenseCountService = licenseCountService;
        this.jiraApplicationManager = jiraApplicationManager;
        this.scheduledMetricEvaluator = scheduledMetricEvaluator;
        this.registry = CollectorRegistry.defaultRegistry;
        this.instrumentRegistry = instrumentRegistry;
        this.projectManager = projectManager;
    }

    private final Gauge maintenanceExpiryDaysGauge = Gauge.build()
            .name("jira_maintenance_expiry_days_gauge")
            .help("Maintenance Expiry Days Gauge")
            .create();

    private final Gauge licenseExpiryDaysGauge = Gauge.build()
            .name("jira_license_expiry_days_gauge")
            .help("License Expiry Days Gauge")
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
            .labelNames("projectKey", "eventType", "username")
            .create();

    private final Counter issueViewCounter = Counter.build()
            .name("jira_issue_view_count")
            .help("Issue View Count")
            .labelNames("projectKey", "username")
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

    private final Gauge dbcpNumActiveGauge = Gauge.build()
            .name("jira_dbcp_num_active_gauge")
            .help("DBCP Number Of Active Connections Gauge")
            .create();

    private final Gauge dbcpNumIdleGauge = Gauge.build()
            .name("jira_dbcp_num_idle_gauge")
            .help("DBCP Number Of Idle Connections Gauge")
            .create();

    private final Gauge dbcpMaxActiveGauge = Gauge.build()
            .name("jira_dbcp_max_active_gauge")
            .help("DBCP Max Number Of Connections Gauge")
            .create();

    private final Gauge dbConnectionsGauge = Gauge.build()
            .name("jira_db_connections_gauge")
            .help("DB Number Of Connections Gauge")
            .create();

    private final Gauge dbBorrowedConnectionsGauge = Gauge.build()
            .name("jira_db_borrowed_connections_gauge")
            .help("DB Number Of Borrowed Connections Gauge")
            .create();

    private final Gauge dbReadsGauge = Gauge.build()
            .name("jira_db_reads_gauge")
            .help("DB Number Of Reads Gauge")
            .create();

    private final Gauge dbWritesGauge = Gauge.build()
            .name("jira_db_writes_gauge")
            .help("DB Number Of Writes Gauge")
            .create();

    private final Gauge webRequestsGauge = Gauge.build()
            .name("jira_web_requests_gauge")
            .help("Number Of Web Requests Gauge")
            .create();

    private final Gauge restRequestsGauge = Gauge.build()
            .name("jira_rest_requests_gauge")
            .help("Number Of Rest Requests Gauge")
            .create();

    private final Gauge concurrentRequestsGauge = Gauge.build()
            .name("jira_concurrent_requests_gauge")
            .help("Number Of Concurrent Requests Gauge")
            .create();

    private final Gauge httpSessionObjectsGauge = Gauge.build()
            .name("jira_http_session_objects_gauge")
            .help("Number Of Http Session Objects Gauge")
            .create();

    private final Gauge jvmUptimeGauge = Gauge.build()
            .name("jvm_uptime_gauge")
            .help("JVM Uptime Gauge")
            .create();

    private final Gauge projectsGauge = Gauge.build()
            .name("jira_projects_gauge")
            .help("Number Of Projects Gauge")
            .create();

    private final Gauge timeSpentPerProjectGauge = Gauge.build()
            .name("time_spent_per_project_seconds_gauge")
            .help("Time Spent Per Project In Seconds")
            .labelNames("project")
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
    public void issueUpdateCounter(String projectKey, String eventType, String username) {
        issueUpdateCounter.labels(projectKey, eventType, username).inc();
    }

    @Override
    public void issueViewCounter(String projectKey, String username) {
        issueViewCounter.labels(projectKey, username).inc();
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
        authorizedSessionsGauge.set(snapshot.stream().filter(e -> nonNull(e.getUserName())).count());

        // resolve cluster metrics
        totalClusterNodeGauge.set(clusterManager.getAllNodes().size());

        // users
        allUsersGauge.set(userManager.getTotalUserCount());
        activeUsersGauge.set(licenseCountService.totalBillableUsers());

        // license
        SingleProductLicenseDetailsView licenseDetails = jiraApplicationManager.getPlatform().getLicense().getOrNull();
        if (licenseDetails != null) {
            maintenanceExpiryDaysGauge.set(DAYS.convert(licenseDetails.getMaintenanceExpiryDate().getTime() - System.currentTimeMillis(), MILLISECONDS));
            licenseExpiryDaysGauge.set(DAYS.convert(licenseDetails.getLicenseExpiryDate().getTime() - System.currentTimeMillis(), MILLISECONDS));
            allowedUsersGauge.set(licenseDetails.getNumberOfUsers());
        }

        // attachment size
        totalAttachmentSizeGauge.set(scheduledMetricEvaluator.getTotalAttachmentSize());

        // instruments
        Instrument dbcpActive = instrumentRegistry.getInstrument(DBCP_ACTIVE.getInstrumentName());
        Instrument dbcpIdle = instrumentRegistry.getInstrument(DBCP_IDLE.getInstrumentName());
        Instrument dbcpMaxActive = instrumentRegistry.getInstrument(DBCP_MAX.getInstrumentName());
        Instrument dbConnections = instrumentRegistry.getInstrument(DB_CONNECTIONS.getInstrumentName());
        Instrument dbBorrowedConnections = instrumentRegistry.getInstrument(DB_CONNECTIONS_BORROWED.getInstrumentName());
        Instrument dbReads = instrumentRegistry.getInstrument(DB_READS.getInstrumentName());
        Instrument dbWrites = instrumentRegistry.getInstrument(DB_WRITES.getInstrumentName());
        Instrument webRequests = instrumentRegistry.getInstrument(WEB_REQUESTS.getInstrumentName());
        Instrument restRequests = instrumentRegistry.getInstrument(REST_REQUESTS.getInstrumentName());
        Instrument concurrentRequests = instrumentRegistry.getInstrument(CONCURRENT_REQUESTS.getInstrumentName());
        Instrument httpSessionObjects = instrumentRegistry.getInstrument(HTTP_SESSION_OBJECTS.getInstrumentName());
        dbcpNumActiveGauge.set(getNullSafeValue(dbcpActive));
        dbcpMaxActiveGauge.set(getNullSafeValue(dbcpMaxActive));
        dbcpNumIdleGauge.set(getNullSafeValue(dbcpIdle));
        dbConnectionsGauge.set(getNullSafeValue(dbConnections));
        dbBorrowedConnectionsGauge.set(getNullSafeValue(dbBorrowedConnections));
        dbReadsGauge.set(getNullSafeValue(dbReads));
        dbWritesGauge.set(getNullSafeValue(dbWrites));
        webRequestsGauge.set(getNullSafeValue(webRequests));
        restRequestsGauge.set(getNullSafeValue(restRequests));
        concurrentRequestsGauge.set(getNullSafeValue(concurrentRequests));
        httpSessionObjectsGauge.set(getNullSafeValue(httpSessionObjects));

        // jvm uptime
        jvmUptimeGauge.set(ManagementFactory.getRuntimeMXBean().getUptime());

        // number of projects
        projectsGauge.set(projectManager.getProjectCount());

        // calculate time spent per project
        projectManager.getProjects().forEach(p -> {
            try {
                // get all issues for the current project and sum the times spent per issue
                Collection<Long> issueIdsForProject = issueManager.getIssueIdsForProject(p.getId());
                long totalTimeSpentPerProject = issueManager.getIssueObjects(issueIdsForProject)
                        .stream()
                        .filter(i -> i.getTimeSpent() != null)
                        .mapToLong(Issue::getTimeSpent)
                        .sum();
                timeSpentPerProjectGauge.labels(p.getName()).set(totalTimeSpentPerProject);
            } catch (GenericEntityException e) {
                log.error("GenericEntityException caught. Detailed message: ", e);
            }
        });

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
        result.addAll(licenseExpiryDaysGauge.collect());
        result.addAll(dbcpNumActiveGauge.collect());
        result.addAll(dbcpNumIdleGauge.collect());
        result.addAll(dbcpMaxActiveGauge.collect());
        result.addAll(dbConnectionsGauge.collect());
        result.addAll(dbBorrowedConnectionsGauge.collect());
        result.addAll(dbReadsGauge.collect());
        result.addAll(dbWritesGauge.collect());
        result.addAll(webRequestsGauge.collect());
        result.addAll(restRequestsGauge.collect());
        result.addAll(concurrentRequestsGauge.collect());
        result.addAll(httpSessionObjectsGauge.collect());
        result.addAll(jvmUptimeGauge.collect());
        result.addAll(projectsGauge.collect());
        result.addAll(timeSpentPerProjectGauge.collect());

        return result;
    }

    private double getNullSafeValue(Instrument instrument) {
        if (instrument == null) {
            return -1;
        }
        return instrument.getValue();
    }

    @Override
    public void destroy() {
        this.registry.unregister(this);
    }

    @Override
    public void afterPropertiesSet() {
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
