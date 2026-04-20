package org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.application.internal.inboundservices;

public interface MaritimeCorridorOverlayProvider {

    MaritimeCorridorOverlay currentOverlay();

    MaritimeCorridorOverlay refreshOverlay();
}
