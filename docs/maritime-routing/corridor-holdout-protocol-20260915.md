# Frozen directed-AIS comparison: May and June 2024

Register both dates before acquiring either source. Do not inspect one date then
alter the model, cohort, exclusions, comparator, sampling, criteria or other date.
The machine-readable registration binds all implementation files, dependency
versions, graph, chart, fitted March model and this protocol. Preserve a replay
bundle of the exact code. This is local preregistration, not external attestation.

## Model and baseline

Use the existing immutable NOAA_ENC_REGIONAL_V2 graph and 40-layer chart snapshot.
Do not import the fitted preference model into Java or mutate geometry, ports,
minimumDepthM, legalStatus or source restrictions. Compare two objectives:

1. DISTANCE_V1: shortest physical geodesic distance.
2. DIRECTED_AIS_BALANCED_V1: physical distance plus one times unsupported directed
   distance, using only the previously fitted March 1 AIS model. A complete edge
   or connector requires proximity and direction-compatible original observations.
   The 150 m association radius, 25 m minimum training movement and cosine >=0.5
   are fixed engineering parameters, not AIS accuracy or navigation permission.

Training model uses 874 consecutive observation pairs from 23 of 26 eligible March
tracks. Three training tracks had chart-water conflicts and remain in the quality
report. March resubstitution measured 17/20 with maximum Frechet 678.79 m, but is
NOT validation. April remains a preserved failed earlier experiment; it is not
fitted into this model and is not re-evaluated to choose this experiment's outputs.

## Cohort and checks (unchanged)

Dates: NOAA AIS 2024-05-01 and 2024-06-01, each separately reported. Use the exact
existing FROZEN_CONFIG and source/identity/start-time hash selection, up to 64
merchant vessels and twenty regional segments per date. Keep envelope exits and
conflicting timestamps as track boundaries. No success-only filtering, relocated
endpoints, per-case objective switching, or combined acceptance across dates.

Each date/model must have >=10 selected cases and >=80% measurable. Every measured
discrete Hausdorff and Frechet error must be <=1,000 m. Zero predicted chart-water
conflicts. Full curves checked at 6.25 m, metrics at 100 m and at most 1,000 samples.
Unknown depth/hazards retain existing exclusions; failures remain in denominator.
Both models use identical selected source rows and connector candidate budgets.

Report physical length separately from optimization score, supporting training
segment indices, chart restriction IDs, failures, paired improvements/regressions,
runtime and process working set. Reject training/holdout source digest equality,
unregistered dates or any change to frozen code/model/data/environment bindings.
Run the four evaluations sequentially for comparability. No tuning on their results.

## Gate interpretation

A regional result is route-shape evidence, not current passage clearance, terminal
access or global phase-3 acceptance. The project still requires global-source
coverage, finer geometry, expected passages and Java integration evidence. Do not
claim new current legal directions, depths, vessel/tide facts or canal availability.
Temporal mismatch (AIS 2024 / chart 2026), repeated vessels and terrestrial reception
bias remain explicit limitations even if both regional dates pass.
