package ru.andreymarkelov.atlas.plugins.promjiraexporter.action.admin;

import com.atlassian.jira.permission.GlobalPermissionKey;
import com.atlassian.jira.security.GlobalPermissionManager;
import com.atlassian.jira.security.xsrf.RequiresXsrfCheck;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.web.action.JiraWebActionSupport;
import ru.andreymarkelov.atlas.plugins.promjiraexporter.service.SecureTokenManager;

public class SecureTokenConfigAction extends JiraWebActionSupport {
    private final SecureTokenManager secureTokenManager;
    private final GlobalPermissionManager globalPermissionManager;

    private boolean saved = false;
    private String token;

    public SecureTokenConfigAction(
            SecureTokenManager secureTokenManager,
            GlobalPermissionManager globalPermissionManager) {
        this.secureTokenManager = secureTokenManager;
        this.globalPermissionManager = globalPermissionManager;
    }

    @Override
    public String doDefault() throws Exception {
        if (!hasAdminPermission()) {
            return PERMISSION_VIOLATION_RESULT;
        }
        token = secureTokenManager.getToken();
        return INPUT;
    }

    @Override
    @RequiresXsrfCheck
    protected String doExecute() throws Exception {
        secureTokenManager.setToken(token);
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

    public boolean isSaved() {
        return saved;
    }

    public void setSaved(boolean saved) {
        this.saved = saved;
    }
}
