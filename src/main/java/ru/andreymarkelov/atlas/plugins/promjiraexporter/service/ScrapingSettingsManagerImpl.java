package ru.andreymarkelov.atlas.plugins.promjiraexporter.service;

import com.atlassian.sal.api.pluginsettings.PluginSettings;
import com.atlassian.sal.api.pluginsettings.PluginSettingsFactory;

import static org.apache.commons.lang3.math.NumberUtils.toInt;

public class ScrapingSettingsManagerImpl implements ScrapingSettingsManager {
    private static final int DEFAULT_SCRAPE_DELAY = 5;

    private final PluginSettings pluginSettings;

    public ScrapingSettingsManagerImpl(PluginSettingsFactory pluginSettingsFactory) {
        this.pluginSettings = pluginSettingsFactory.createSettingsForKey("PLUGIN_PROMETHEUS_FOR_JIRA");
    }

    @Override
    public int getDelay() {
        Object storedValue = getPluginSettings().get("delay");
        return storedValue != null ? toInt(storedValue.toString(), DEFAULT_SCRAPE_DELAY) : DEFAULT_SCRAPE_DELAY;
    }

    @Override
    public void setDelay(int delay) {
        getPluginSettings().put("delay", String.valueOf(delay));
    }

    private synchronized PluginSettings getPluginSettings() {
        return pluginSettings;
    }
}
