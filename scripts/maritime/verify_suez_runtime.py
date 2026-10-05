"""Read-only acceptance of the sourced Suez route and the Cape fallback."""
import json
from pathlib import Path
import time
from urllib.request import Request, urlopen
from urllib.error import HTTPError, URLError


ROOT = 'http://localhost:8080/api/v2/maritime'
EXPECTED = json.loads(Path('data/maritime-routing/global-suez-research-v1-20261003.validation.json').read_text())['graphVersion']


def call(path, data=None):
    request = Request(ROOT + path, data=None if data is None else json.dumps(data).encode(), headers={'Content-Type':'application/json'})
    try:
        with urlopen(request, timeout=120) as response:
            return response.status, json.load(response)
    except HTTPError as error:
        return error.code, json.load(error)


def verify():
    for attempt in range(40):
        try:
            status, graph = call('/graph')
            break
        except URLError:
            if attempt == 39: raise
            time.sleep(2)
    assert status == 200 and graph['graphVersion'] == EXPECTED, (status, graph.get('status'), graph.get('graphVersion'))
    request = dict(originPortId='UNLOCODE:PTBNG', destinationPortId='UNLOCODE:AEDXB', expectedGraphVersion=EXPECTED,
                   acknowledgeResearchLimitations=True, k=1, maxCandidates=1, maxExpansions=2000000,
                   minimumSeparationM=0, requireKnownLegalStatus=False)
    cases = []
    for name, overrides in [
        ('portugal-dubai', {}),
        ('dubai-portugal', dict(originPortId='UNLOCODE:AEDXB', destinationPortId='UNLOCODE:PTBNG')),
        ('suez-excluded', dict(userSuppliedProhibitedZones=['SUEZ_CANAL_RESEARCH'])),
        ('speed-no-canal-eta', dict(userSuppliedSpeedKnots=14)),
        ('panama-regression', dict(originPortId='UNLOCODE:PETDM', destinationPortId='UNLOCODE:PTBNG')),
        ('gulf-regression', dict(originPortId='UNLOCODE:AEDXB', destinationPortId='UNLOCODE:AEKHL')),
    ]:
        inputs = dict(request, **overrides)
        status, result = call('/routes', inputs)
        assert status == 200 and result['routes'] and not result['budgetExhausted'], (name, status, result.get('status'))
        route = result['routes'][0]
        suez = [n for n in route['nodeIds'] if n.startswith('SUEZ:OSM:')]
        panama = [n for n in route['nodeIds'] if n.startswith('PANAMA:OSM:')]
        points = [point for part in route['geometry'] for point in part]
        red_sea = any(36 < lon < 42 and 12 < lat < 27 for lon, lat in points)
        cape = any(lat < -30 and 15 < lon < 35 for lon, lat in points)
        summary = dict(case=name, distanceKm=route['distanceM']/1000, suezNodes=len(suez), panamaNodes=len(panama),
                       redSea=red_sea, cape=cape, estimatedHours=route['estimatedHours'],
                       scopes=[s['scope'] for s in route['coverage']['spans']])
        if name in ('portugal-dubai', 'dubai-portugal', 'speed-no-canal-eta'):
            assert len(suez) == 92 and red_sea and not cape and route['estimatedHours'] is None and 'SOURCED_CANAL_RESEARCH' in summary['scopes']
            assert any(text.startswith('SUEZ_CANAL_RESEARCH:') for text in route['explanation'])
        if name == 'suez-excluded':
            assert not suez and cape and summary['distanceKm'] > cases[0]['distanceKm']
        if name == 'panama-regression':
            assert len(panama) == 153 and not suez
        if name == 'gulf-regression':
            assert not suez and not panama and abs(summary['distanceKm']-80.417260594) < .001
        cases.append(summary)
        print(json.dumps(summary), flush=True)
    assert abs(cases[0]['distanceKm']-cases[1]['distanceKm']) < .00001
    Path('target/suez-runtime-acceptance.json').write_text(json.dumps(dict(graphVersion=EXPECTED, acceptance='PASS', cases=cases), indent=2))


if __name__ == '__main__': verify()
