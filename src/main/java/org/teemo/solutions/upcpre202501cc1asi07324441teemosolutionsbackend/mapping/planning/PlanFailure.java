package org.teemo.solutions.upcpre202501cc1asi07324441teemosolutionsbackend.mapping.planning;

public final class PlanFailure extends RuntimeException {
    final int http;
    final Object details;
    PlanFailure(int http, String code) { this(http, code, null); }
    PlanFailure(int http, String code, Object details) { super(code); this.http = http; this.details = details; }
}
