"""Source-coverage priority for a combined research graph; no permission inference."""
from dataclasses import dataclass

from shapely import LineString, union_all
from shapely.ops import transform

from build_enc_pilot import coverage_available
from build_graph import GEOD, geodesic

POLICY = "SOURCE_COVERAGE_PRIORITY_V1"
REGION = "NOAA_NEW_YORK_V2"


@dataclass(frozen=True)
class Decision:
    touched: bool
    allowed: bool
    restrictions: tuple = ()


class RegionalPriority:
    def __init__(self, domain):
        self.domain = domain
        self.geographic_coverage = union_all([
            geometry for _, geometry, props in domain.features[219]
            if coverage_available(props.get("CATCOV"))])
        self.coverage = transform(domain.forward, self.geographic_coverage)
        # Do not erode the chart boundary itself at a seam between two models.
        # Positive source water remains eroded and all source obstacles buffered.
        # Interior geometry and the frozen regional pilot are unchanged.
        padding = domain.config.geometry_tolerance_m + domain.config.coastal_buffer_m
        positive = union_all([g for _, g, _ in domain.depths])
        # Source depth polygons can themselves end at the chart edge. Eroding
        # that artificial cut creates a wall between otherwise adjacent water.
        # The seam uses ONLY original positive water (never an outward buffer);
        # buffered obstacles still take precedence throughout the seam.
        seam_water = positive.intersection(self.coverage.boundary.buffer(2 * padding))
        self.water = positive.buffer(-padding, join_style=2).union(seam_water).difference(domain.excluded)
        left, bottom, right, top = self.geographic_coverage.bounds
        self.center = ((left + right) / 2, (bottom + top) / 2)
        # This profile is a small non-polar chart extent. The enclosing cap has
        # an additional 100 m engineering margin, well beyond its chord error.
        self.radius = max(GEOD.inv(*self.center, lon, lat)[2]
                          for lon in (left, right) for lat in (bottom, top)) + 100

    def near(self, points):
        for a, b in zip(points, points[1:]):
            # Triangle inequality on the ellipsoid: if a point of this geodesic
            # reaches the chart cap, its start cannot be farther than length+R.
            # Endpoint bounding boxes are incorrect for curved long segments.
            length = GEOD.inv(*a, *b)[2]
            if GEOD.inv(*a, *self.center)[2] <= length + self.radius:
                return True
        return False

    def projected(self, points):
        dense = []
        for a, b in zip(points, points[1:]):
            _, segment = geodesic(a, b, self.domain.config.geodesic_step_m)
            dense.extend(segment if not dense else segment[1:])
        return transform(self.domain.forward, LineString(dense))

    def inspect(self, points):
        if not self.near(points):
            return Decision(False, True)
        inside = self.projected(points).intersection(self.coverage)
        if inside.is_empty:
            return Decision(False, True)
        ids = tuple(sorted(self.domain.regulatory[i][0] for i in
                           self.domain.regulatory_tree.query(inside, predicate="intersects")))
        return Decision(True, bool(self.water.covers(inside)), ids)

    def outside_clear(self, points, land):
        """GSHHG outside chart coverage, positive NOAA water inside it."""
        if not self.near(points):
            return land.clear(points)
        outside = self.projected(points).difference(self.coverage)
        pieces = list(outside.geoms) if hasattr(outside, "geoms") else [outside]
        for piece in pieces:
            if piece.is_empty:
                continue
            geographic = transform(self.domain.inverse, piece)
            if not land.clear(list(geographic.coords)):
                return False
        return True
