# Bounded balanced-channel development variant

March is development evidence. Distance-only V2 measured 17/20 with eight spatial
failures. Channel-first V2 measured the same 17/20 with two spatial failures:
overly long channel detours can be worse than a direct physical-water path.

Test CHART_CHANNEL_BALANCED_V1: minimize geodesic route distance plus one times
projected distance outside the same NOAA FAIRWY/DRGARE polygons. Tie-break with
physical distance, then outside-channel distance. Include all source/destination
connector costs. The multiplier 1 is an explicit engineering research setting,
not a sourced legal rule, vessel characteristic, actual extra distance or speed.
Always report physical length separately from this optimization score.

No changes to graph, AIS cohort, observations, endpoints, metric sampling or
acceptance targets. No extra coefficient sweep or outcome-specific switching
between candidates. After this development comparison, freeze the balanced model
and distance-only comparator for a new prospectively registered April 1, 2024
NOAA AIS evaluation, regardless of whether March passes. Do not tune on April.
Global phase 3 and operational navigation remain separate, unpassed gates.
