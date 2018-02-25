package ru.andreymarkelov.atlas.plugins.promjiraexporter.service;

import com.atlassian.jira.config.util.AttachmentPathManager;
import com.atlassian.sal.api.pluginsettings.PluginSettings;
import com.atlassian.sal.api.pluginsettings.PluginSettingsFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static java.lang.Thread.MIN_PRIORITY;
import static java.util.Optional.ofNullable;
import static java.util.concurrent.Executors.defaultThreadFactory;
import static java.util.concurrent.Executors.newSingleThreadScheduledExecutor;

public class ScheduledMetricEvaluatorImpl implements ScheduledMetricEvaluator, DisposableBean, InitializingBean {
    private static final Logger log = LoggerFactory.getLogger(ScheduledMetricEvaluator.class);

    private final PluginSettings pluginSettings;

    private final AttachmentPathManager attachmentPathManager;

    private final AtomicLong totalAttachmentSize;
    private final AtomicLong lastExecutionTimestamp;

    /**
     * Scheduled executor to grab metrics.
     */
    private final ThreadFactory threadFactory;
    private ScheduledExecutorService executorService;

    private ScheduledFuture<?> scraper;
    private final Lock lock;

    public ScheduledMetricEvaluatorImpl(
            PluginSettingsFactory pluginSettingsFactory,
            AttachmentPathManager attachmentPathManager) {
        this.pluginSettings = pluginSettingsFactory.createSettingsForKey("PLUGIN_PROMETHEUS_FOR_JIRA");

        this.attachmentPathManager = attachmentPathManager;
        this.totalAttachmentSize = new AtomicLong(0);
        this.lastExecutionTimestamp = new AtomicLong(-1);

        this.threadFactory = defaultThreadFactory();
        this.executorService = newSingleThreadScheduledExecutor(r -> {
            Thread thread = threadFactory.newThread(r);
            thread.setPriority(MIN_PRIORITY);
            return thread;
        });
        this.lock = new ReentrantLock();
    }


    @Override
    public void afterPropertiesSet() {
        lock.lock();
        try {
            startScraping(getDelay());
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void destroy() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(1, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
        }
    }

    @Override
    public void restartScraping(int newDelay) {
        lock.lock();
        try {
            stopScraping();
            startScraping(newDelay);
        } finally {
            lock.unlock();
        }
    }

    private void startScraping(int delay) {
        scraper = executorService.scheduleWithFixedDelay(() -> {
            File attachmentDirectory = new File(attachmentPathManager.getAttachmentPath());
            try {
                long size = Files.walk(attachmentDirectory.toPath())
                        .map(Path::toFile)
                        .filter(File::isFile)
                        .mapToLong(File::length)
                        .sum();
                log.debug("Total attachment size:{}", size);
                totalAttachmentSize.set(size);
                lastExecutionTimestamp.set(System.currentTimeMillis());
            } catch (Exception ex) {
                log.error("Failed to resolve attachments size.", ex);

                // Rethrow exception so that the executor also gets this error so that it can react on it.
                throw new RuntimeException(ex);
            }
        }, 0, delay, TimeUnit.MINUTES);
    }

    private void stopScraping() {
        boolean success = scraper.cancel(true);
        if (!success) {
            log.debug("Unable to cancel scraping, typically because it has already completed.");
        }
    }

    @Override
    public long getTotalAttachmentSize() {
        return totalAttachmentSize.get();
    }

    @Override
    public long getLastExecutionTimestamp() {
        return lastExecutionTimestamp.get();
    }

    @Override
    public int getDelay() {
        return ofNullable(pluginSettings.get("delay"))
                .map(String.class::cast)
                .map(Integer::parseInt)
                .orElse(1);
    }

    @Override
    public void setDelay(int delay) {
        pluginSettings.put("delay", String.valueOf(delay));
    }
}
