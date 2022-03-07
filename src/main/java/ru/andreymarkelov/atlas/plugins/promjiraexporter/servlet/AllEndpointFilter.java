package ru.andreymarkelov.atlas.plugins.promjiraexporter.servlet;

import java.io.IOException;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;

import com.atlassian.sal.api.ApplicationProperties;
import com.atlassian.sal.api.UrlMode;
import ru.andreymarkelov.atlas.plugins.promjiraexporter.service.MetricCollector;
import ru.andreymarkelov.atlas.plugins.promjiraexporter.util.ExceptionRunnable;

import static org.apache.commons.lang3.StringUtils.removeStart;

public class AllEndpointFilter implements Filter {
    private final MetricCollector metricCollector;
    private final ApplicationProperties applicationProperties;

    public AllEndpointFilter(MetricCollector metricCollector, ApplicationProperties applicationProperties) {
        this.metricCollector = metricCollector;
        this.applicationProperties = applicationProperties;
    }

    @Override
    public void doFilter(
            final ServletRequest servletRequest,
            final ServletResponse servletResponse,
            final FilterChain filterChain) throws IOException, ServletException {
        if (!(servletRequest instanceof HttpServletRequest)) {
            filterChain.doFilter(servletRequest, servletResponse);
            return;
        }

        String path = removeStart(((HttpServletRequest) servletRequest).getRequestURI(), applicationProperties.getBaseUrl(UrlMode.RELATIVE));
        metricCollector.requestDuration(
                getComponents(path, 1),
                new ExceptionRunnable() {
                    @Override
                    public void run() throws IOException, ServletException {
                        filterChain.doFilter(servletRequest, servletResponse);
                    }
                }
        );
    }

    @Override
    public void init(FilterConfig filterConfig) {
    }

    @Override
    public void destroy() {
    }

    private static String getComponents(String str, int pathComponents) {
        if (str == null || pathComponents < 1) {
            return str;
        }

        int count = 0;
        int i =  -1;
        do {
            i = str.indexOf("/", i + 1);
            if (i < 0) {
                return str;
            }
            count++;
        } while (count <= pathComponents);
        return (i >= pathComponents) ? str.substring(0, i) : null;
    }
}
