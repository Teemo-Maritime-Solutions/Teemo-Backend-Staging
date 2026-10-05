"""Apply a reviewed, narrowly scoped authorization branch from a pinned official NOAA publication."""
import argparse
from datetime import timedelta
import hashlib
import json
from pathlib import Path

import pypdf
from build_enc_pilot import load_snapshot, feature_key
from build_graph import encode, verify_receipt
from semantic_pilot import digest, instant

# Actual downloaded publication, not a guessed edition or arbitrary network endpoint.
REVIEWED_PDF_SHA = "4670561a2662ca82a9374f08e86a028ae5016150d39aab3bb0a010b977b9a2c8"


def review(curation_path, chart_receipt, publication_receipt, output, at, review_hours=24):
    if output.exists():
        raise ValueError("Review output already exists")
    if type(review_hours) is not int or not 1 <= review_hours <= 168:
        raise ValueError("Review freshness window must be 1-168 hours (engineering policy, not legal validity)")
    source, chart = load_snapshot(chart_receipt)
    path, publication = verify_receipt(publication_receipt)
    if publication["sha256"] != REVIEWED_PDF_SHA or publication["id"] != "noaa-coast-pilot-2":
        raise ValueError("Publication changed; a new semantic review is required")
    reader = pypdf.PdfReader(path)
    if len(reader.pages) != 446:
        raise ValueError("Reviewed page layout changed")
    page = reader.pages[138].extract_text()
    excerpt = page[page.index("(3255)"):page.index("(3257)")]
    normalized = " ".join(excerpt.split())
    if "06 SEP 2026" not in page or "unless authorized" not in normalized or "must comply with the instructions" not in normalized:
        raise ValueError("Reviewed authorization branch not found in the pinned publication")
    at_time = instant(at)
    if instant(publication["acquiredAt"]) > at_time:
        raise ValueError("Review cannot precede source acquisition")
    curated = json.loads(curation_path.read_text(encoding="utf-8"))
    if curated["sourceSha256"] != chart["sha256"]:
        raise ValueError("Chart source changed")
    features = {}
    layer = source["layers"]["197"]
    for record in layer["features"]:
        features[feature_key(197, record, layer["objectIdField"])] = record
    count = 0
    for row in curated["rules"]:
        record = features.get(row["featureId"])
        if record is None or "33 CFR 165.169" not in (record["properties"].get("INFORM") or ""):
            continue
        if row["classification"] != "UNRESOLVED_BLOCK":
            raise ValueError("Refuse to overwrite an existing reviewed rule")
        if row["featureSha256"] != digest(dict(geometry=record["geometry"], properties=record["properties"])):
            raise ValueError("Chart feature changed")
        row.update(classification="CONDITIONAL_RESTRICTION", reviewer="Codex source-text review; not legal authorization",
                   reviewedAt=at, validFrom=at, validUntil=(at_time + timedelta(hours=review_hours)).isoformat(),
                   reviewWindowHours=review_hours, sourceIds=[chart["id"], publication["id"]],
                   regulatoryCitation="33 CFR 165.169(b)(1)-(2), authorization branch only",
                   regulatoryEvidence=dict(sourceId=publication["id"], locator="PDF page index 138, paragraphs 3255-3256; printed 06 SEP 2026",
                                           explanation="COTP authorization and compliance with instructions; other exceptions and live scope are not inferred"),
                   reason="Conservative authorization branch. This chart polygon alone is not permission, an accurate live zone boundary, or proof of enforcement.",
                   conditions=[dict(key=prefix + row["featureId"], equals=True, requiredSource="SOURCE_DATA", requiredScope="PASSAGE")
                               for prefix in ("cotpAuthorization:", "cotpInstructionsComplied:")])
        count += 1
    curated["reviewImplementation"] = dict(sha256=hashlib.sha256(Path(__file__).read_bytes()).hexdigest(), pypdf=pypdf.__version__,
                                           publicationSha256=publication["sha256"], reviewedParagraphTextSha256=digest(normalized),
                                           freshnessWindowMeaning="Engineering review expiry; NOT a law effective/expiry date")
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_bytes(encode(curated) + b"\n")
    print(json.dumps(dict(conditionalRules=count, unresolvedRules=sum(r["classification"] == "UNRESOLVED_BLOCK" for r in curated["rules"]),
                          suppliedPermissions=0, curatedEndpoints=len(curated["endpoints"]), curationSha256=digest(curated))), flush=True)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ("curation", "chart-receipt", "publication-receipt", "output"):
        parser.add_argument("--" + name, type=Path, required=True)
    parser.add_argument("--at", required=True)
    parser.add_argument("--review-hours", type=int, default=24)
    args = parser.parse_args()
    review(args.curation, args.chart_receipt, args.publication_receipt, args.output, args.at, args.review_hours)
