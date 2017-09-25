package ru.andreymarkelov.atlas.listener;

import com.atlassian.event.api.EventListener;
import com.atlassian.event.api.EventPublisher;
import com.atlassian.jira.event.DashboardViewEvent;
import com.atlassian.jira.event.issue.IssueEvent;
import com.atlassian.jira.event.issue.IssueViewEvent;
import com.atlassian.jira.event.user.LoginEvent;
import com.atlassian.jira.event.user.LogoutEvent;
import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.user.ApplicationUser;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;

public class MetricListener implements InitializingBean, DisposableBean {
    private final EventPublisher eventPublisher;

    public MetricListener(EventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        eventPublisher.register(this);
    }

    @Override
    public void destroy() throws Exception {
        eventPublisher.unregister(this);
    }

    @EventListener
    public void onIssueEvent(IssueEvent issueEvent) {
        Issue issue = issueEvent.getIssue();
    }

    @EventListener
    public void onDashboardViewEvent(DashboardViewEvent dashboardViewEvent) {
        Long dashboardId = dashboardViewEvent.getId();
    }

    @EventListener
    public void onIssueViewEvent(IssueViewEvent issueViewEvent) {
        Long issueId = issueViewEvent.getId();
    }

    @EventListener
    public void onIssueEvent(LoginEvent loginEvent) {
        ApplicationUser applicationUser = loginEvent.getUser();
    }

    @EventListener
    public void onLogoutEvent(LogoutEvent logoutEvent) {
        ApplicationUser applicationUser = logoutEvent.getUser();
    }
}
