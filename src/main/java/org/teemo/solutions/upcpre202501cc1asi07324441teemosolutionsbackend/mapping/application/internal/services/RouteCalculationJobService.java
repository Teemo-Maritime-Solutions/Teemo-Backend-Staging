package org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.application.internal.services;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.interfaces.rest.resources.RouteCalculationJobStatusResource;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.interfaces.rest.resources.RouteCalculationResource;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class RouteCalculationJobService {

    private static final Logger logger = LoggerFactory.getLogger(RouteCalculationJobService.class);

    private final RouteService routeService;
    private final ExecutorService executorService;
    private final ConcurrentHashMap<String, RouteCalculationJobRecord> jobs = new ConcurrentHashMap<>();

    public RouteCalculationJobService(RouteService routeService) {
        this.routeService = routeService;
        this.executorService = Executors.newFixedThreadPool(2, new RouteCalculationJobThreadFactory());
    }

    public RouteCalculationJobStatusResource submit(String startPortId,
                                                    String endPortId,
                                                    List<String> viaPortIds,
                                                    boolean enforceViaPorts,
                                                    RouteHistoryContext historyContext) {
        String jobId = UUID.randomUUID().toString();
        Instant now = Instant.now();
        RouteCalculationJobRecord queuedJob = new RouteCalculationJobRecord(
                jobId,
                RouteCalculationJobState.QUEUED,
                "En cola para calcular la ruta.",
                now,
                null,
                null,
                null,
                null
        );
        jobs.put(jobId, queuedJob);

        executorService.submit(() -> executeJob(jobId, startPortId, endPortId, viaPortIds, enforceViaPorts, historyContext));
        return toResource(queuedJob);
    }

    public RouteCalculationJobStatusResource getStatus(String jobId) {
        RouteCalculationJobRecord job = jobs.get(jobId);
        if (job == null) {
            throw new NoSuchElementException("No existe un calculo de ruta con id " + jobId);
        }
        return toResource(job);
    }

    private void executeJob(String jobId,
                            String startPortId,
                            String endPortId,
                            List<String> viaPortIds,
                            boolean enforceViaPorts,
                            RouteHistoryContext historyContext) {
        updateJob(jobId, RouteCalculationJobState.RUNNING, "Calculando ruta maritima...", null, null);
        long startedAt = System.nanoTime();
        logger.info("route.calculation.job.start jobId={} startPortId={} endPortId={} viaCount={} enforceViaPorts={}",
                jobId,
                startPortId,
                endPortId,
                viaPortIds != null ? viaPortIds.size() : 0,
                enforceViaPorts);
        try {
            if ((viaPortIds != null && !viaPortIds.isEmpty()) || enforceViaPorts) {
                logger.warn("route.calculation.job.via-ports.ignored jobId={} startPortId={} endPortId={} viaCount={} enforceViaPorts={}",
                        jobId,
                        startPortId,
                        endPortId,
                        viaPortIds != null ? viaPortIds.size() : 0,
                        enforceViaPorts);
            }
            RouteCalculationResource result = routeService.calculateOptimalRoute(startPortId, endPortId, Collections.emptySet(), historyContext);
            long elapsedMs = java.time.Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
            updateJob(jobId, RouteCalculationJobState.COMPLETED, "Ruta calculada correctamente.", result, null);
            logger.info("route.calculation.job.completed jobId={} startPortId={} endPortId={} elapsedMs={} distanceNm={}",
                    jobId,
                    startPortId,
                    endPortId,
                    elapsedMs,
                    result.totalDistance());
        } catch (RuntimeException ex) {
            long elapsedMs = java.time.Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
            updateJob(jobId, RouteCalculationJobState.FAILED, ex.getMessage(), null, ex.getMessage());
            logger.warn("route.calculation.job.failed jobId={} startPortId={} endPortId={} elapsedMs={} message={}",
                    jobId,
                    startPortId,
                    endPortId,
                    elapsedMs,
                    ex.getMessage());
        }
    }

    private void updateJob(String jobId,
                           RouteCalculationJobState state,
                           String message,
                           RouteCalculationResource result,
                           String errorMessage) {
        jobs.computeIfPresent(jobId, (ignored, current) -> {
            Instant now = Instant.now();
            Instant startedAt = current.startedAt();
            Instant completedAt = current.completedAt();

            if (state == RouteCalculationJobState.RUNNING && startedAt == null) {
                startedAt = now;
            }
            if (state == RouteCalculationJobState.COMPLETED || state == RouteCalculationJobState.FAILED) {
                completedAt = now;
            }

            return new RouteCalculationJobRecord(
                    current.jobId(),
                    state,
                    message,
                    current.createdAt(),
                    startedAt,
                    completedAt,
                    result != null ? result : current.result(),
                    errorMessage != null ? errorMessage : current.errorMessage()
            );
        });
    }

    private RouteCalculationJobStatusResource toResource(RouteCalculationJobRecord job) {
        return new RouteCalculationJobStatusResource(
                job.jobId(),
                job.state().name(),
                job.message(),
                job.createdAt(),
                job.startedAt(),
                job.completedAt(),
                job.errorMessage(),
                job.result()
        );
    }

    @PreDestroy
    void shutdown() {
        executorService.shutdownNow();
    }

    private enum RouteCalculationJobState {
        QUEUED,
        RUNNING,
        COMPLETED,
        FAILED
    }

    private record RouteCalculationJobRecord(
            String jobId,
            RouteCalculationJobState state,
            String message,
            Instant createdAt,
            Instant startedAt,
            Instant completedAt,
            RouteCalculationResource result,
            String errorMessage
    ) {
    }

    private static final class RouteCalculationJobThreadFactory implements ThreadFactory {
        private final AtomicInteger sequence = new AtomicInteger(1);

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "route-calculation-job-" + sequence.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        }
    }
}
