package org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.infrastructure.persistence.sdmdb.repositories;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.infrastructure.persistence.sdmdb.documents.GlobalFishingWatchOverlayDocument;

import java.util.Optional;

@Repository
public interface GlobalFishingWatchOverlayRepository extends MongoRepository<GlobalFishingWatchOverlayDocument, String> {
    Optional<GlobalFishingWatchOverlayDocument> findFirstBySourceAndConfigHashOrderByRefreshedAtDesc(
            String source,
            String configHash
    );
}
