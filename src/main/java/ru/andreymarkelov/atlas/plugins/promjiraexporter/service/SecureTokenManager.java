package ru.andreymarkelov.atlas.plugins.promjiraexporter.service;

public interface SecureTokenManager {
    String getToken();
    void setToken(String token);
}
