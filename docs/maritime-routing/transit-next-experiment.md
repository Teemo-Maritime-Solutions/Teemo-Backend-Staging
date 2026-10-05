# Next experiment: origin/destination transit recommendation

User decision: "Recomendar rutas de tránsito entre origen y destino".
This supersedes any expectation that an endpoint-only recommendation reconstructs
every original AIS curve, including long excursions returning near their start.
It does NOT supersede the failed April/May/June results or authorize activation.

Status (2026-09-16): **completed experiment, not a passed phase**. 101 Python tests
pass. July/August were registered before acquisition and all four evaluations
finished without tuning. Directed AIS has one >1 km failure per date; both dates
still FAIL. See [results and diagnosis](transit-results-20260916.md),
[protocol](transit-holdout-protocol-20260916.md) and
[registration](transit-holdout-registration-20260916.json). These dates are no
longer untouched data for a later experiment.

## Explicit regional research population

Use the existing merchant-source, gap, timestamp-conflict, envelope, jump, length
and resource checks. Before any chart-water, path or metric outcome, classify each
original eligible segment using source observations only:

- Original endpoint displacement >=1,000 m.
- Endpoint displacement / observed curve length >=0.5.

These are declared engineering population boundaries for displacement-dominated
regional transits, not measured vessel intent, port calls or universal voyage
classification. Legitimate highly sinuous voyages may lie outside this population;
record that limitation rather than pretending the cohort covers all shipping.
The 1 km population boundary does not replace or relax the separate 1 km spatial
error criterion. No minimum error, successful routing or chart-water test is used
to select the cohort. Original endpoints, timestamps and row IDs stay unchanged.

`transit_cohort.py` returns a complete candidate census: selected transits, eligible
but hash-unselected transits, and curves outside the declared population with
explicit reasons. Preserve both all-candidate counts and selected-transit counts.
Then use the existing source/identity/time hash to select at most twenty cases.
Selected cases conflicting with chart water remain failures in the denominator.

## Registered experiment requirements (execution now complete)

1. Choose and freeze the research model using development sources only. Preserve
   March's model and every earlier failed report; any newly fitted model gets its
   own version, exact training-source set and source-row provenance.
2. Integrate this cohort into a separate evaluator that reports the full census,
   source coverage, failures, unchanged spatial metrics and physical restrictions.
   Do not edit the existing 17 frozen experiment files.
3. Register two untouched dates, exact code/configuration/dependencies/model,
   comparator, thresholds and protocol before downloading either date. Archive
   the replay implementation and verify chronological acquisition receipts.
4. Run both objectives on both dates without intervening tuning. Require >=10
   selected transits, >=80% measurable, every measured Hausdorff/Frechet <=1,000 m
   and zero predicted chart-water conflicts, independently per date/model.
5. Even a regional pass does not satisfy missing global approach/channel/expected
   passage evidence, source-country coverage or operational clearance.

Do not rerun May/June with this new selection and relabel that as independent
validation. An intended intermediate destination must come from supplied/known
input; extracting future waypoints from the held-out curve would leak the answer.
