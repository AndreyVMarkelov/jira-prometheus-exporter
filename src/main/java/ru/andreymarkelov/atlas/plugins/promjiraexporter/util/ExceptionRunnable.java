package ru.andreymarkelov.atlas.plugins.promjiraexporter.util;

import java.io.IOException;
import javax.servlet.ServletException;

public interface ExceptionRunnable {
    void run() throws IOException, ServletException;
}
