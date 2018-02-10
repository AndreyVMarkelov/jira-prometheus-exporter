package ru.andreymarkelov.atlas.plugins.promjiraexporter.service;

import com.atlassian.jira.config.util.AttachmentPathManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static java.lang.Thread.MIN_PRIORITY;
import static java.util.concurrent.Executors.defaultThreadFactory;
import static java.util.concurrent.Executors.newScheduledThreadPool;

public class ScheduledMetricEvaluatorImpl implements ScheduledMetricEvaluator, DisposableBean, InitializingBean {
    private static final Logger log = LoggerFactory.getLogger(ScheduledMetricEvaluator.class);

    private final AttachmentPathManager attachmentPathManager;
    private final AtomicLong totalAttachmentSize;

    public ScheduledMetricEvaluatorImpl(
            AttachmentPathManager attachmentPathManager) {
        this.attachmentPathManager = attachmentPathManager;
        this.totalAttachmentSize = new AtomicLong(0);
    }

    /**
     * Scheduled executor to grab metrics.
     */
    private ScheduledExecutorService executorService = newScheduledThreadPool(2, r -> {
        Thread thread = defaultThreadFactory().newThread(r);
        thread.setPriority(MIN_PRIORITY);
        return thread;
    });

    @Override
    public long getTotalAttachmentSize() {
        return totalAttachmentSize.get();
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
    public void afterPropertiesSet() {
        executorService.scheduleWithFixedDelay(() -> {
            File attachmentDirectory = new File(attachmentPathManager.getAttachmentPath());
            try {
                long size = Files.walk(attachmentDirectory.toPath())
                        .map(Path::toFile)
                        .filter(File::isFile)
                        .mapToLong(File::length)
                        .sum();
                log.debug("Total attachment size:{}", size);
                totalAttachmentSize.set(size);
            } catch (Throwable th) {
                log.error("Cannot resolve attachments size", th);
            }
        }, 0, 1, TimeUnit.MINUTES);
    }
}
