package ru.andreymarkelov.atlas.plugins.promjiraexporter.action.admin;

import com.atlassian.jira.permission.GlobalPermissionKey;
import com.atlassian.jira.security.GlobalPermissionManager;
import com.atlassian.jira.security.xsrf.RequiresXsrfCheck;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.web.action.JiraWebActionSupport;
import ru.andreymarkelov.atlas.plugins.promjiraexporter.service.ScheduledMetricEvaluator;
import ru.andreymarkelov.atlas.plugins.promjiraexporter.service.SecureTokenManager;

import java.util.Date;

public class SecureTokenConfigAction extends JiraWebActionSupport {
    private static final String ERROR_INVALID_DELAY = "ru.andreymarkelov.atlas.plugins.promjiraexporter.action.error.invalid.delay";
    private static final String NOT_YET_EXECUTED = "ru.andreymarkelov.atlas.plugins.promjiraexporter.settings.notyetexecuted";

    private final SecureTokenManager secureTokenManager;
    private final GlobalPermissionManager globalPermissionManager;
    private final ScheduledMetricEvaluator scheduledMetricEvaluator;

    private boolean saved = false;
    private String token;
    private int delay;
    private String lastExecutionTimestamp;

    public SecureTokenConfigAction(
            SecureTokenManager secureTokenManager,
            ScheduledMetricEvaluator scheduledMetricEvaluator,
            GlobalPermissionManager globalPermissionManager) {
        this.secureTokenManager = secureTokenManager;
        this.globalPermissionManager = globalPermissionManager;
        this.scheduledMetricEvaluator = scheduledMetricEvaluator;
    }

    @Override
    public String doDefault() throws Exception {
        if (!hasAdminPermission()) {
            return PERMISSION_VIOLATION_RESULT;
        }

        token = secureTokenManager.getToken();
        delay = scheduledMetricEvaluator.getDelay();

        long temp = scheduledMetricEvaluator.getLastExecutionTimestamp();
        if (temp > 0) {
            lastExecutionTimestamp = new Date(temp).toString();
        } else {
            lastExecutionTimestamp = getJiraServiceContext().getI18nBean().getText(NOT_YET_EXECUTED);
        }

        return INPUT;
    }

    @Override
    protected void doValidation() {
        if (delay <= 0) {
            getJiraServiceContext().getErrorCollection().addError("delay",
                    getJiraServiceContext().getI18nBean().getText(ERROR_INVALID_DELAY));
        }
    }

    @Override
    @RequiresXsrfCheck
    protected String doExecute() throws Exception {
        if (hasAnyErrors()) {
            return ERROR;
        }

        secureTokenManager.setToken(token);
        scheduledMetricEvaluator.setDelay(delay);
        scheduledMetricEvaluator.restartScraping(delay);
        setSaved(true);

        return getRedirect("PromForJiraSecureTokenConfigAction!default.jspa?saved=true");
    }

    public boolean hasAdminPermission() {
        ApplicationUser user = getLoggedInUser();
        if (user == null) {
            return false;
        }
        return globalPermissionManager.hasPermission(GlobalPermissionKey.ADMINISTER, user);
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public int getDelay() {
        return delay;
    }

    public void setDelay(int delay) {
        this.delay = delay;
    }

    public String getLastExecutionTimestamp() {
        return lastExecutionTimestamp;
    }

    public boolean isSaved() {
        return saved;
    }

    public void setSaved(boolean saved) {
        this.saved = saved;
    }
}
