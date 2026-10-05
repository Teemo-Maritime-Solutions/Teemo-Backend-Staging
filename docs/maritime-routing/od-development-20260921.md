# Origin/destination-conditioned development, 2026-09-21

Declared before fitting the new model. All March–August 1, 2024 sources are now
development data, including the previously failed temporal holdouts. Their frozen
implementations and reports remain unchanged. No new pass can replace those results.

The new model keeps coherent original transit tracks, source rows, timestamps and
vessel grouping. It conditions evidence on the query's two endpoints: choose one
training track whose projected origin precedes its projected destination, both
within 1,000 m, with at least 1,000 m between projections. Rank admissible tracks
by the sum of the two endpoint distances, then source track hash. Support only
consecutive observations overlapping that directed subtrack. Never use interior
observations of the query in prediction. If there is no admissible track, use the
same distance objective with zero support; this rule is declared before evaluation.

Keep the original 150 m directed evidence buffer, cosine >=0.5, movement >=25 m,
distance plus unsupported distance cost, physical graph, original endpoints,
cohort, case selection and 1 km acceptance thresholds. An evidence buffer is not
physical water or a permission. Only original chart-clear training tracks can
support preferences; every other original track remains in the extraction census.

Development checks: five deterministic vessel groups (SHA-256 of MMSI modulo 5),
with all dates of a held-out vessel excluded from its fitted evidence. Evaluate
each date's original twenty hash-selected transits, preserving failures and the
entire pre-geometry census. These grouped checks are development diagnostics, not
untouched temporal validation; one fixed model configuration is proposed here.
Report distance and conditioned objective separately, no per-case outcome switch.

Only after development, freeze the implementation/model/protocol for September 1
and October 1, 2024 before downloading either untouched NOAA source. Both dates
and all original criteria must be reported independently. Global phase 3 remains
separately gated on authoritative finer geometry, expected passages, water-side
access and constraints. A regional result cannot certify worldwide navigation.
