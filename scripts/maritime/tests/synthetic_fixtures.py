"""SYNTHETIC FIXTURES ONLY: geometries are not real shorelines, ports or voyages."""
from shapely import box

ISLAND = box(-.01, -.01, .01, .01)
WEST = (-.1, 0)
EAST = (.1, 0)
NORTH = (0, .1)
DATELINE_PATH = [(179.9, 0), (-179.9, 0)]

# Fictional time-ordered broadcast points; never presented as observed vessel tracks.
AIS_RECORDS = [(0, WEST, 1), (60, NORTH, 2), (120, EAST, 3)]


def synthetic_enc_source():
    """Fictional ENC schema/geometry, used only in pipeline unit tests."""
    from enc_snapshot import LAYERS
    from shapely import Point
    source = dict(cells=["SYNTHETIC"], layers={str(k): dict(metadata=dict(name="Harbor." + v),
                  objectIdField="OBJECTID", features=[]) for k, v in LAYERS.items()})
    def add(layer, identifier, geom, **attributes):
        source["layers"][str(layer)]["features"].append(dict(type="Feature", geometry=geom.__geo_interface__,
            properties=dict(OBJECTID=identifier, DSNM="SYNTHETIC", **attributes)))
    add(219, 1, ISLAND, CATCOV="coverage available")
    add(227, 2, ISLAND, DRVAL1=10, DRVAL2=20)
    add(233, 3, box(-.001, -.005, .001, .005))
    add(49, 4, Point(-.005, 0), OBJNAM="SYNTHETIC WEST BERTH")
    add(49, 5, Point(.005, 0), OBJNAM="SYNTHETIC EAST BERTH")
    add(49, 6, Point(0, 0), OBJNAM="SYNTHETIC LAND BERTH")
    add(197, 7, ISLAND, INFORM="SYNTHETIC unresolved regulation")
    return source

def synthetic_ais_report(version, status):
    return dict(graphVersion=version, parameters={"SYNTHETIC": True}, validatorSha256="SYNTHETIC",
                implementation={"purpose": "SYNTHETIC"}, selection="SYNTHETIC", method="SYNTHETIC",
                sources=[dict(id="noaa-ais-SYNTHETIC", sha256="SYNTHETIC")],
                selectedCases=1, cases=[dict(trackKey="SYNTHETIC", sourceRows=[1, 2, 3], status=status,
                                             discreteHausdorffM=10.0, discreteFrechetM=20.0)])
