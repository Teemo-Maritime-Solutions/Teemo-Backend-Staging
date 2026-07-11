package org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.infrastructure.persistence.sdmdb.repositories;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.infrastructure.persistence.sdmdb.documents.RouteDocument;

import java.util.List;
import java.util.Optional;

@Repository
public interface RouteRepository extends MongoRepository<RouteDocument, String> {

    boolean existsByHomePortAndDestinationPort(String homePort, String destinationPort);
    Optional<RouteDocument> findByHomePortAndDestinationPort(String homePort, String destinationPort);
    List<RouteDocument> findAllByHomePortAndHomePortContinentAndDestinationPortAndDestinationPortContinent(
            String homePort,
            String homePortContinent,
            String destinationPort,
            String destinationPortContinent
    );
    void deleteAll();
}
