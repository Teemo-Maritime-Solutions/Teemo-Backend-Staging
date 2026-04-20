package org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.application.internal.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.domain.model.valueobjects.PortOperationalStatus;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PortOverviewServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void persistRuntimeCacheShouldAllowNullSnapshotFields() throws Exception {
        PortService portService = mock(PortService.class);
        when(portService.getAllPorts()).thenReturn(List.of());
        PortOverviewService service = new PortOverviewService(new ObjectMapper(), portService);

        setField(service, "runtimeCacheFile", tempDir.resolve("port_overview_cache.json"));
        setField(service, "lastSyncedAt", Instant.parse("2026-04-20T15:00:00Z"));
        @SuppressWarnings("unchecked")
        List<PortOverviewService.PortSnapshot> cache =
                (List<PortOverviewService.PortSnapshot>) getField(service, "cache");
        cache.clear();
        cache.add(new PortOverviewService.PortSnapshot(
                "port-1",
                "Callao",
                "PE",
                -12.051012,
                -77.154106,
                PortOperationalStatus.OPEN,
                null,
                500,
                null,
                null,
                null,
                null
        ));

        Method persistRuntimeCache = PortOverviewService.class.getDeclaredMethod("persistRuntimeCache");
        persistRuntimeCache.setAccessible(true);
        persistRuntimeCache.invoke(service);

        Path cacheFile = tempDir.resolve("port_overview_cache.json");
        assertThat(Files.exists(cacheFile)).isTrue();
        String persisted = Files.readString(cacheFile);
        assertThat(persisted).contains("\"portId\" : \"port-1\"");
        assertThat(persisted).contains("\"reason\" : null");
        assertThat(persisted).contains("\"contactPhone\" : null");
        assertThat(persisted).contains("\"contactEmail\" : null");
        assertThat(persisted).contains("\"website\" : null");
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = PortOverviewService.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private Object getField(Object target, String fieldName) throws Exception {
        Field field = PortOverviewService.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }
}
