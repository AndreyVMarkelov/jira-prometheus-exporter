package ru.andreymarkelov.atlas.plugins.promjiraexporter.servlet;

import io.prometheus.client.Histogram;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;

public class AllEndpointFIlter implements Filter {
    static final String PATH_COMPONENT_PARAM = "path-components";
    static final String HELP_PARAM = "help";
    static final String METRIC_NAME_PARAM = "metric-name";
    static final String BUCKET_CONFIG_PARAM = "buckets";

    private Histogram histogram = null;

    // Package-level for testing purposes.
    int pathComponents = 1;
    private String metricName = null;
    private String help = "The time taken fulfilling servlet requests";
    private double[] buckets = null;

    public AllEndpointFIlter() {}

    public AllEndpointFIlter(
            String metricName,
            String help,
            Integer pathComponents,
            double[] buckets) {
        this.metricName = metricName;
        this.buckets = buckets;
        if (help != null) {
            this.help = help;
        }
        if (pathComponents != null) {
            this.pathComponents = pathComponents;
        }
    }

    private boolean isEmpty(String s) {
        return s == null || s.length() == 0;
    }

    private String getComponents(String str) {
        if (str == null || pathComponents < 1) {
            return str;
        }
        int count = 0;
        int i =  -1;
        do {
            i = str.indexOf("/", i + 1);
            if (i < 0) {
                // Path is longer than specified pathComponents.
                return str;
            }
            count++;
        } while (count <= pathComponents);

        return str.substring(0, i);
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        Histogram.Builder builder = Histogram.build()
                .labelNames("path", "method");

        if (filterConfig == null && isEmpty(metricName)) {
            throw new ServletException("No configuration object provided, and no metricName passed via constructor");
        }

        if (filterConfig != null) {
            if (isEmpty(metricName)) {
                metricName = filterConfig.getInitParameter(METRIC_NAME_PARAM);
                if (isEmpty(metricName)) {
                    throw new ServletException("Init parameter \"" + METRIC_NAME_PARAM + "\" is required; please supply a value");
                }
            }

            if (!isEmpty(filterConfig.getInitParameter(HELP_PARAM))) {
                help = filterConfig.getInitParameter(HELP_PARAM);
            }

            // Allow overriding of the path "depth" to track
            if (!isEmpty(filterConfig.getInitParameter(PATH_COMPONENT_PARAM))) {
                pathComponents = Integer.valueOf(filterConfig.getInitParameter(PATH_COMPONENT_PARAM));
            }

            // Allow users to override the default bucket configuration
            if (!isEmpty(filterConfig.getInitParameter(BUCKET_CONFIG_PARAM))) {
                String[] bucketParams = filterConfig.getInitParameter(BUCKET_CONFIG_PARAM).split(",");
                buckets = new double[bucketParams.length];

                for (int i = 0; i < bucketParams.length; i++) {
                    buckets[i] = Double.parseDouble(bucketParams[i]);
                }
            }
        }

        if (buckets != null) {
            builder = builder.buckets(buckets);
        }

        histogram = builder
                .help(help)
                .name(metricName)
                .register();
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
        if (!(servletRequest instanceof HttpServletRequest)) {
            filterChain.doFilter(servletRequest, servletResponse);
            return;
        }

        HttpServletRequest request = (HttpServletRequest) servletRequest;

        String path = request.getRequestURI();

        Histogram.Timer timer = histogram
                .labels(getComponents(path), request.getMethod())
                .startTimer();

        try {
            filterChain.doFilter(servletRequest, servletResponse);
        } finally {
            timer.observeDuration();
        }
    }

    @Override
    public void destroy() {
    }
}
