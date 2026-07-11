package org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.domain.services;

import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.domain.model.entities.Port;
import org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.domain.model.valueobjects.RoutePath;

import java.util.Set;

public interface RouteCalculatorService {
    RoutePath calculateOptimalRoute(Port start, Port end, Set<String> avoidPortIds);
}
