package ru.andreymarkelov.atlas.plugins.promjiraexporter.service;

import com.atlassian.application.api.Application;
import com.atlassian.application.api.ApplicationManager;
import com.atlassian.instrumentation.Instrument;
import com.atlassian.instrumentation.InstrumentRegistry;
import com.atlassian.jira.application.ApplicationRoleManager;
import com.atlassian.jira.cluster.ClusterManager;
import com.atlassian.jira.issue.IssueManager;
import com.atlassian.jira.license.LicenseCountService;
import com.atlassian.jira.user.util.UserManager;
import com.atlassian.jira.web.session.currentusers.JiraUserSession;
import com.atlassian.jira.web.session.currentusers.JiraUserSessionTracker;
import com.atlassian.mail.queue.MailQueue;
import com.atlassian.sal.api.license.SingleProductLicenseDetailsView;
import io.prometheus.client.*;
import io.prometheus.client.hotspot.DefaultExports;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import ru.andreymarkelov.atlas.plugins.promjiraexporter.util.ExceptionRunnable;

import javax.servlet.ServletException;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;

import static com.atlassian.jira.instrumentation.InstrumentationName.*;
import static java.util.Collections.emptyList;
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
    private final MailQueue mailQueue;
    private final ApplicationRoleManager applicationRoleManager;

    public MetricCollectorImpl(
            IssueManager issueManager,
            ClusterManager clusterManager,
            UserManager userManager,
            LicenseCountService licenseCountService,
            ApplicationManager jiraApplicationManager,
            ScheduledMetricEvaluator scheduledMetricEvaluator,
            InstrumentRegistry instrumentRegistry,
            MailQueue mailQueue,
            ApplicationRoleManager applicationRoleManager) {
        this.issueManager = issueManager;
        this.jiraUserSessionTracker = JiraUserSessionTracker.getInstance();
        this.clusterManager = clusterManager;
        this.userManager = userManager;
        this.licenseCountService = licenseCountService;
        this.jiraApplicationManager = jiraApplicationManager;
        this.scheduledMetricEvaluator = scheduledMetricEvaluator;
        this.registry = CollectorRegistry.defaultRegistry;
        this.instrumentRegistry = instrumentRegistry;
        this.mailQueue = mailQueue;
        this.applicationRoleManager = applicationRoleManager;
    }

    //--> Mails

    private final Gauge mailQueueGauge = Gauge.build()
            .name("jira_mail_queue_gauge")
            .help("Mail Queue Gauge")
            .create();

    private final Gauge mailQueueErrorGauge = Gauge.build()
            .name("jira_mail_queue_error_gauge")
            .help("Mail Queue Error Gauge")
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

    //--> Instrumentary

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

    private final Counter dbConnectionsCounter = Counter.build()
            .name("jira_db_connections_counter")
            .help("DB Number Of Connections Counter")
            .create();

    private final Gauge dbBorrowedConnectionsGauge = Gauge.build()
            .name("jira_db_borrowed_connections_gauge")
            .help("DB Number Of Borrowed Connections Gauge")
            .create();

    private final Counter dbReadsCounter = Counter.build()
            .name("jira_db_reads_counter")
            .help("DB Number Of Reads Counter")
            .create();

    private final Counter dbWritesCounter = Counter.build()
            .name("jira_db_writes_counter")
            .help("DB Number Of Writes Counter")
            .create();

    private final Counter webRequestsCounter = Counter.build()
            .name("jira_web_requests_counter")
            .help("Number Of Web Requests Counter")
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

    private final Gauge concurrentQuicksearchesGauge = Gauge.build()
            .name("jira_concurrent_number_of_quicksearches_gauge")
            .help("Concurrent number of quicksearches")
            .create();

    private final Counter issueIndexReadsCounter = Counter.build()
            .name("jira_issue_index_reads_counter")
            .help("Index Reads Counter")
            .create();

    private final Counter issueIndexWritesCounter = Counter.build()
            .name("jira_issue_index_writes_counter")
            .help("Index Writes Counter")
            .create();

    private final Gauge workflowsGauge = Gauge.build()
            .name("jira_total_workflows_gauge")
            .help("Workflows Gauge")
            .create();

    private final Gauge customFieldsGauge = Gauge.build()
            .name("jira_total_customfields_gauge")
            .help("Custom Fields Gauge")
            .create();

    private final Gauge attachmentsGauge = Gauge.build()
            .name("jira_total_attachments_gauge")
            .help("Attachments Gauge")
            .create();

    private final Gauge versionsGauge = Gauge.build()
            .name("jira_total_versions_gauge")
            .help("Versions Gauge")
            .create();

    private final Gauge filtersGauge = Gauge.build()
            .name("jira_total_filters_gauge")
            .help("Filters Gauge")
            .create();

    private final Gauge componentsGauge = Gauge.build()
            .name("jira_total_components_gauge")
            .help("Components Gauge")
            .create();

    private final Gauge groupsGauge = Gauge.build()
            .name("jira_total_groups_gauge")
            .help("Groups Gauge")
            .create();

    private final Gauge projectsGauge = Gauge.build()
            .name("jira_total_projects_gauge")
            .help("Projects Gauge")
            .create();

    private final Gauge jvmUptimeGauge = Gauge.build()
            .name("jvm_uptime_gauge")
            .help("JVM Uptime Gauge")
            .create();

    //--> plugins

    private final Counter pluginEnabledCounter = Counter.build()
            .name("jira_plugin_enabled_count")
            .help("Plugin Enabled Count")
            .labelNames("pluginKey")
            .create();

    private final Counter pluginDisabledCounter = Counter.build()
            .name("jira_plugin_disabled_count")
            .help("Plugin Disabled Count")
            .labelNames("pluginKey")
            .create();

    private final Counter pluginUninstalledCounter = Counter.build()
            .name("jira_plugin_uninstalled_count")
            .help("Plugin Uninstalled Count")
            .labelNames("pluginKey")
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

    @Override
    public void pluginEnabledCounter(String pluginKey) {
        pluginEnabledCounter.labels(pluginKey).inc();
    }

    @Override
    public void pluginDisabledCounter(String pluginKey) {
        pluginDisabledCounter.labels(pluginKey).inc();
    }

    @Override
    public void pluginUninstalledCounter(String pluginKey) {
        pluginUninstalledCounter.labels(pluginKey).inc();
    }

    //------------------------------------------------------------------------------------------------------------------

    //--> Cluster metrics

    private final Gauge clusterTotalNodesGauge = Gauge.build()
            .name("jira_total_cluster_nodes_gauge")
            .help("Total Cluster Nodes Gauge")
            .create();

    private final Gauge clusterActiveNodesGauge = Gauge.build()
            .name("jira_active_cluster_nodes_gauge")
            .help("Active Cluster Nodes Gauge")
            .create();

    private final Gauge clusterHeartbeatCounter = Gauge.build()
            .name("jira_cluster_heartbeat_counter")
            .help("Cluster Heartbeat Counter")
            .create();

    private final Gauge clusterCacheReplicationResumedCounter = Gauge.build()
            .name("jira_cluster_cache_replication_resumed_counter")
            .help("Cluster Cache Replication Resumed Counter")
            .labelNames("nodeId")
            .create();

    private final Gauge clusterCacheReplicationStoppedCounter = Gauge.build()
            .name("jira_cluster_cache_replication_stopped_counter")
            .help("Cluster Cache Replication Stopped Counter")
            .labelNames("nodeId")
            .create();

    private void clusterMetrics() {
        clusterTotalNodesGauge.set(clusterManager.getAllNodes().size());
        clusterActiveNodesGauge.set(clusterManager.findLiveNodes().size());
    }

    @Override
    public void clusterHeartbeatCounter() {
        clusterHeartbeatCounter.inc();
    }

    @Override
    public void clusterCacheReplicationResumedCounter(String nodeId) {
        clusterCacheReplicationResumedCounter.labels(nodeId).inc();
    }

    @Override
    public void clusterCacheReplicationStoppedCounter(String nodeId) {
        clusterCacheReplicationStoppedCounter.labels(nodeId).inc();
    }

    //<-- Cluster metrics

    //------------------------------------------------------------------------------------------------------------------

    //--> Users

    private final Gauge allUsersGauge = Gauge.build()
            .name("jira_all_users_gauge")
            .help("All Users Gauge")
            .create();

    private final Gauge allActiveUsersGauge = Gauge.build()
            .name("jira_all_active_users_gauge")
            .help("All Active Users Gauge")
            .create();

    private void usersMetrics() {
        allUsersGauge.set(userManager.getTotalUserCount());
        allActiveUsersGauge.set(licenseCountService.totalBillableUsers());
    }

    //------------------------------------------------------------------------------------------------------------------

    //--> License metrics

    private final Gauge maintenanceExpiryDaysGauge = Gauge.build()
            .name("jira_maintenance_expiry_days_gauge")
            .help("Maintenance Expiry Days Gauge")
            .labelNames("licenseType")
            .create();

    private final Gauge licenseExpiryDaysGauge = Gauge.build()
            .name("jira_license_expiry_days_gauge")
            .help("License Expiry Days Gauge")
            .labelNames("licenseType")
            .create();

    private final Gauge allowedUsersGauge = Gauge.build()
            .name("jira_allowed_users_gauge")
            .help("Allowed Users Gauge")
            .labelNames("licenseType")
            .create();

    private final Gauge activeUsersGauge = Gauge.build()
            .name("jira_active_users_gauge")
            .help("Active Users Gauge")
            .labelNames("licenseType")
            .create();

    private void licenseMetrics() {
        try {
            // platform
            SingleProductLicenseDetailsView platformProductLicenseDetailsView = jiraApplicationManager.getPlatform().getLicense().getOrNull();
            if (platformProductLicenseDetailsView != null) {
                setLicenseData(jiraApplicationManager.getPlatform(), platformProductLicenseDetailsView);
            }
            // applications
            for (Application application : jiraApplicationManager.getApplications()) {
                if (application != null) {
                    SingleProductLicenseDetailsView singleProductLicenseDetailsView = application.getLicense().getOrNull();
                    if (singleProductLicenseDetailsView != null) {
                        setLicenseData(application, singleProductLicenseDetailsView);
                    }
                }
            }
        } catch (Exception ex) {
            log.error("Error to collect license metrics", ex);
        }
    }

    private void setLicenseData(
            Application application,
            SingleProductLicenseDetailsView singleProductLicenseDetailsView) {
        // because nullable
        if (singleProductLicenseDetailsView.getMaintenanceExpiryDate() != null) {
            maintenanceExpiryDaysGauge
                    .labels(application.getName())
                    .set(DAYS.convert(singleProductLicenseDetailsView.getMaintenanceExpiryDate().getTime() - System.currentTimeMillis(), MILLISECONDS));
        }
        // because nullable
        if (singleProductLicenseDetailsView.getLicenseExpiryDate() != null) {
            licenseExpiryDaysGauge
                    .labels(application.getName())
                    .set(DAYS.convert(singleProductLicenseDetailsView.getLicenseExpiryDate().getTime() - System.currentTimeMillis(), MILLISECONDS));
        }

        int allowedUsers = singleProductLicenseDetailsView.getNumberOfUsers();
        int activeUsers = applicationRoleManager.getUserCount(application.getKey());
        allowedUsersGauge.labels(application.getName()).set(allowedUsers);
        activeUsersGauge.labels(application.getName()).set(activeUsers);
    }

    //<-- License metrics

    //------------------------------------------------------------------------------------------------------------------

    private List<MetricFamilySamples> collectInternal() {
        // resolve count issues
        issuesGauge.set(issueManager.getIssueCount());

        // resolve sessions count
        List<JiraUserSession> snapshot = jiraUserSessionTracker.getSnapshot();
        totalSessionsGauge.set(snapshot.size());

        int countUserSessions = 0;
        for (JiraUserSession jiraUserSession : snapshot) {
            if (jiraUserSession.getUserName() != null) {
                countUserSessions++;
            }
        }
        authorizedSessionsGauge.set(countUserSessions);

        clusterMetrics();
        licenseMetrics();
        usersMetrics();

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
        Instrument concurrentQuickSearches = instrumentRegistry.getInstrument(QUICKSEARCH_CONCURRENT_REQUESTS.getInstrumentName());
        Instrument issueIndexReads = instrumentRegistry.getInstrument(ISSUE_INDEX_READS.getInstrumentName());
        Instrument issueIndexWrites = instrumentRegistry.getInstrument(ISSUE_INDEX_WRITES.getInstrumentName());
        Instrument totalWorkflows = instrumentRegistry.getInstrument(TOTAL_WORKFLOWS.getInstrumentName());
        Instrument totalCustomFields = instrumentRegistry.getInstrument(TOTAL_CUSTOMFIELDS.getInstrumentName());
        Instrument totalAttachments = instrumentRegistry.getInstrument(TOTAL_ATTACHMENTS.getInstrumentName());
        Instrument totalVersions = instrumentRegistry.getInstrument(TOTAL_VERSIONS.getInstrumentName());
        Instrument totalFilters = instrumentRegistry.getInstrument(TOTAL_FILTERS.getInstrumentName());
        Instrument totalComponents = instrumentRegistry.getInstrument(TOTAL_COMPONENTS.getInstrumentName());
        Instrument totalGroups = instrumentRegistry.getInstrument(TOTAL_GROUPS.getInstrumentName());
        Instrument totalProjects = instrumentRegistry.getInstrument(TOTAL_PROJECTS.getInstrumentName());

        dbcpNumActiveGauge.set(getNullSafeValue(dbcpActive));
        dbcpMaxActiveGauge.set(getNullSafeValue(dbcpMaxActive));
        dbcpNumIdleGauge.set(getNullSafeValue(dbcpIdle));
        dbConnectionsCounter.inc(getNullSafeValue(dbConnections) - dbConnectionsCounter.get());
        dbBorrowedConnectionsGauge.set(getNullSafeValue(dbBorrowedConnections));
        dbReadsCounter.inc(getNullSafeValue(dbReads) - dbReadsCounter.get());
        dbWritesCounter.inc(getNullSafeValue(dbWrites) - dbWritesCounter.get());
        webRequestsCounter.inc(getNullSafeValue(webRequests) - webRequestsCounter.get());
        restRequestsGauge.set(getNullSafeValue(restRequests));
        concurrentRequestsGauge.set(getNullSafeValue(concurrentRequests));
        httpSessionObjectsGauge.set(getNullSafeValue(httpSessionObjects));
        concurrentQuicksearchesGauge.set(getNullSafeValue(concurrentQuickSearches));
        issueIndexReadsCounter.inc(getNullSafeValue(issueIndexReads) - issueIndexReadsCounter.get());
        issueIndexWritesCounter.inc(getNullSafeValue(issueIndexWrites) - issueIndexWritesCounter.get());
        workflowsGauge.set(getNullSafeValue(totalWorkflows));
        customFieldsGauge.set(getNullSafeValue(totalCustomFields));
        attachmentsGauge.set(getNullSafeValue(totalAttachments));
        versionsGauge.set(getNullSafeValue(totalVersions));
        filtersGauge.set(getNullSafeValue(totalFilters));
        componentsGauge.set(getNullSafeValue(totalComponents));
        groupsGauge.set(getNullSafeValue(totalGroups));
        projectsGauge.set(getNullSafeValue(totalProjects));

        // jvm uptime
        jvmUptimeGauge.set(ManagementFactory.getRuntimeMXBean().getUptime());

        // mail
        mailQueueGauge.set(mailQueue.size());
        mailQueueErrorGauge.set(mailQueue.errorSize());

        // collect all metrics
        List<MetricFamilySamples> result = new ArrayList<>();
        result.addAll(issueUpdateCounter.collect());
        result.addAll(issueViewCounter.collect());
        result.addAll(userLoginCounter.collect());
        result.addAll(userLogoutCounter.collect());
        result.addAll(dashboardViewCounter.collect());
        result.addAll(pluginEnabledCounter.collect());
        result.addAll(pluginDisabledCounter.collect());
        result.addAll(pluginUninstalledCounter.collect());
        result.addAll(requestDurationOnPath.collect());
        result.addAll(issuesGauge.collect());
        result.addAll(totalSessionsGauge.collect());
        result.addAll(authorizedSessionsGauge.collect());
        result.addAll(totalAttachmentSizeGauge.collect());

        // cluster
        result.addAll(clusterTotalNodesGauge.collect());
        result.addAll(clusterActiveNodesGauge.collect());
        result.addAll(clusterHeartbeatCounter.collect());
        result.addAll(clusterCacheReplicationResumedCounter.collect());
        result.addAll(clusterCacheReplicationStoppedCounter.collect());

        // license
        result.addAll(maintenanceExpiryDaysGauge.collect());
        result.addAll(licenseExpiryDaysGauge.collect());
        result.addAll(allowedUsersGauge.collect());
        result.addAll(activeUsersGauge.collect());

        // users
        result.addAll(allUsersGauge.collect());
        result.addAll(allActiveUsersGauge.collect());

        result.addAll(dbcpNumActiveGauge.collect());
        result.addAll(dbcpNumIdleGauge.collect());
        result.addAll(dbcpMaxActiveGauge.collect());
        result.addAll(dbConnectionsCounter.collect());
        result.addAll(dbBorrowedConnectionsGauge.collect());
        result.addAll(dbReadsCounter.collect());
        result.addAll(dbWritesCounter.collect());
        result.addAll(webRequestsCounter.collect());
        result.addAll(restRequestsGauge.collect());
        result.addAll(concurrentRequestsGauge.collect());
        result.addAll(httpSessionObjectsGauge.collect());
        result.addAll(concurrentQuicksearchesGauge.collect());
        result.addAll(issueIndexReadsCounter.collect());
        result.addAll(issueIndexWritesCounter.collect());
        result.addAll(workflowsGauge.collect());
        result.addAll(customFieldsGauge.collect());
        result.addAll(attachmentsGauge.collect());
        result.addAll(versionsGauge.collect());
        result.addAll(filtersGauge.collect());
        result.addAll(componentsGauge.collect());
        result.addAll(groupsGauge.collect());
        result.addAll(projectsGauge.collect());
        result.addAll(jvmUptimeGauge.collect());
        // mails
        result.addAll(mailQueueGauge.collect());
        result.addAll(mailQueueErrorGauge.collect());
        // scheduled metrics
        result.addAll(scheduledMetricEvaluator.collect());

        return result;
    }

    private double getNullSafeValue(Instrument instrument) {
        if (instrument == null) {
            return 0;
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
        long start = System.currentTimeMillis();
        try {
            return collectInternal();
        } catch (Throwable throwable) {
            log.error("Error collect prometheus metrics", throwable);
            return emptyList();
        } finally {
            log.debug("Collect execution time is: {}ms", System.currentTimeMillis() - start);
        }
    }
}
