package ru.andreymarkelov.atlas.plugins.promjiraexporter.util;

import javax.servlet.ServletException;
import java.io.IOException;

@FunctionalInterface
public interface ExceptionRunnable {
    void run() throws IOException, ServletException;
}
