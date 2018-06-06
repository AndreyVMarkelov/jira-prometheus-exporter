package ru.andreymarkelov.atlas.plugins.promjiraexporter.service;

public interface ScrapingSettingsManager {
    int getDelay();
    void setDelay(int delay);
}
