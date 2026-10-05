"""Read-only live acceptance of the Panama research graph (route POSTs do not persist)."""
import json
from pathlib import Path
import time
from urllib.request import Request, urlopen
from urllib.error import HTTPError, URLError

ROOT = 'http://localhost:8080/api/v2/maritime'
EXPECTED = json.loads(Path('data/maritime-routing/global-panama-research-v2-20260923.validation.json').read_text())['graphVersion']

def call(path, data=None):
    request = Request(ROOT + path, data=None if data is None else json.dumps(data).encode(), headers={'Content-Type':'application/json'})
    try:
        with urlopen(request, timeout=120) as response:
            return response.status, json.load(response)
    except HTTPError as error:
        return error.code, json.load(error)

def verify():
    for attempt in range(20):
        try:
            status, graph = call('/graph')
            break
        except URLError:
            if attempt == 19: raise
            time.sleep(2)
    assert status == 200 and graph['graphVersion'] == EXPECTED, (status, graph.get('status'))
    request = dict(originPortId='UNLOCODE:PETDM', destinationPortId='UNLOCODE:PTBNG', expectedGraphVersion=EXPECTED,
                   acknowledgeResearchLimitations=True, k=1, maxCandidates=1, maxExpansions=2000000,
                   minimumSeparationM=0, requireKnownLegalStatus=False)
    cases = []
    for name, overrides, expected in [
        ('peru-portugal', {}, 200),
        ('portugal-peru', dict(originPortId='UNLOCODE:PTBNG',destinationPortId='UNLOCODE:PETDM'), 200),
        ('panama-excluded', dict(userSuppliedProhibitedZones=['PANAMA_CANAL_RESEARCH']), 200),
        ('cruising-speed-no-lock-eta', dict(userSuppliedSpeedKnots=14), 200),
        ('unchanged-gulf-route', dict(originPortId='UNLOCODE:AEDXB',destinationPortId='UNLOCODE:AEKHL'), 200),
        ('old-version-rejected', dict(expectedGraphVersion='e6ac2932cd9c941202dc840322411105e84b88b4ccf9f83182ea9a0e57e63437'), 409),
    ]:
        inputs = dict(request, **overrides)
        status, result = call('/routes', inputs)
        assert status == expected, (name, status, result.get('status'))
        routes = result.get('routes', [])
        summary = dict(case=name, httpStatus=status)
        if routes:
            route=routes[0]
            panama=[n for n in route['nodeIds'] if n.startswith('PANAMA:OSM:')]
            summary.update(distanceKm=route['distanceM']/1000, panamaNodes=len(panama), estimatedHours=route['estimatedHours'], budgetExhausted=result['budgetExhausted'])
            assert not result['budgetExhausted']
            if name in ('peru-portugal','portugal-peru','cruising-speed-no-lock-eta'):
                assert len(panama)==153 and route['distanceM']<16883239.787639515 and route['estimatedHours'] is None
                assert [s['scope'] for s in route['coverage']['spans']]==['GLOBAL_COASTLINE_RESEARCH','SOURCED_CANAL_RESEARCH','GLOBAL_COASTLINE_RESEARCH']
            if name=='panama-excluded':
                assert not panama and abs(route['distanceM']-16883239.787639515)<0.01
            if name=='unchanged-gulf-route':
                assert not panama and abs(route['distanceM']-80417.260594)<.01
        cases.append(dict(summary=summary,request=inputs,response=result))
        print(json.dumps(summary),flush=True)
    assert abs(cases[0]['summary']['distanceKm']-cases[1]['summary']['distanceKm'])<.00001
    evidence=dict(graphVersion=EXPECTED, acceptance='PASS', cases=cases,
                  limitations=['Local research geometry only; no operational canal permission/clearance or transit-time validation'])
    Path('target/panama-runtime-acceptance.json').write_text(json.dumps(evidence,indent=2))

if __name__ == '__main__': verify()
