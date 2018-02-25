package ru.andreymarkelov.atlas.plugins.promjiraexporter.listener;

import com.atlassian.event.api.EventListener;
import com.atlassian.event.api.EventPublisher;
import com.atlassian.jira.event.DashboardViewEvent;
import com.atlassian.jira.event.issue.IssueEvent;
import com.atlassian.jira.event.issue.IssueViewEvent;
import com.atlassian.jira.event.type.EventTypeManager;
import com.atlassian.jira.event.user.LoginEvent;
import com.atlassian.jira.event.user.LogoutEvent;
import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.issue.IssueManager;
import com.atlassian.jira.security.JiraAuthenticationContext;
import com.atlassian.jira.user.ApplicationUser;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import ru.andreymarkelov.atlas.plugins.promjiraexporter.service.MetricCollector;

public class MetricListener implements InitializingBean, DisposableBean {
    private final EventPublisher eventPublisher;
    private final IssueManager issueManager;
    private final EventTypeManager eventTypeManager;
    private final JiraAuthenticationContext jiraAuthenticationContext;
    private final MetricCollector metricCollector;

    public MetricListener(
            EventPublisher eventPublisher,
            IssueManager issueManager,
            EventTypeManager eventTypeManager,
            JiraAuthenticationContext jiraAuthenticationContext,
            MetricCollector metricCollector) {
        this.eventPublisher = eventPublisher;
        this.issueManager = issueManager;
        this.eventTypeManager = eventTypeManager;
        this.jiraAuthenticationContext = jiraAuthenticationContext;
        this.metricCollector = metricCollector;
    }

    @Override
    public void afterPropertiesSet() {
        eventPublisher.register(this);
    }

    @Override
    public void destroy() {
        eventPublisher.unregister(this);
    }

    @EventListener
    public void onIssueEvent(IssueEvent issueEvent) {
        Issue issue = issueEvent.getIssue();
        if (issue != null) {
            String eventType = "";
            try {
                eventType = eventTypeManager.getEventType(issueEvent.getEventTypeId()).getName();
            } catch (IllegalArgumentException e) {
            }
            eventTypeManager.getEventType(issueEvent.getEventTypeId());
            metricCollector.issueUpdateCounter(issue.getProjectObject().getKey(), eventType, getCurrentUser());
        }
    }

    @EventListener
    public void onDashboardViewEvent(DashboardViewEvent dashboardViewEvent) {
        metricCollector.dashboardViewCounter(dashboardViewEvent.getId(), getCurrentUser());
    }

    @EventListener
    public void onIssueViewEvent(IssueViewEvent issueViewEvent) {
        Issue issue = issueManager.getIssueObject(issueViewEvent.getId());
        if (issue != null) {
            metricCollector.issueViewCounter(issue.getProjectObject().getKey(), getCurrentUser());
        }
    }

    @EventListener
    public void onLoginEvent(LoginEvent loginEvent) {
        ApplicationUser applicationUser = loginEvent.getUser();
        metricCollector.userLoginCounter((applicationUser != null) ? applicationUser.getUsername() : "");
    }

    @EventListener
    public void onLogoutEvent(LogoutEvent logoutEvent) {
        ApplicationUser applicationUser = logoutEvent.getUser();
        metricCollector.userLogoutCounter((applicationUser != null) ? applicationUser.getUsername() : "");
    }

    private String getCurrentUser() {
        return jiraAuthenticationContext.isLoggedInUser() ? jiraAuthenticationContext.getLoggedInUser().getName() : "";
    }
}
