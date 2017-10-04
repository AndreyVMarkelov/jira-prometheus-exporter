package ru.andreymarkelov.atlas.plugins.promjiraexporter.service;

import com.atlassian.sal.api.pluginsettings.PluginSettings;
import com.atlassian.sal.api.pluginsettings.PluginSettingsFactory;

public class SecureTokenManagerImpl implements SecureTokenManager {
    private final PluginSettings pluginSettings;

    public SecureTokenManagerImpl(PluginSettingsFactory pluginSettingsFactory) {
        this.pluginSettings = pluginSettingsFactory.createSettingsForKey("PLUGIN_PROMETHEUS_FOR_JIRA");
    }

    @Override
    public String getToken() {
        Object storedValue = getPluginSettings().get("securityToken");
        return storedValue != null ? storedValue.toString() : "";
    }

    @Override
    public void setToken(String token) {
        getPluginSettings().put("securityToken", token);
    }

    private synchronized PluginSettings getPluginSettings() {
        return pluginSettings;
    }
}
