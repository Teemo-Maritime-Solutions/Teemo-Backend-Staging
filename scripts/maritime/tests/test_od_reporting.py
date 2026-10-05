"""End-to-end synthetic grouped evaluation and tamper-resistant paired reporting."""
from collections import Counter
from contextlib import ExitStack
from copy import deepcopy
import hashlib
import json
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch

sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from build_enc_pilot import mesh
from compare_od_runs import audit
from enc_snapshot import LAYERS_V2
from evaluate_od_corridors import run
from od_corridor_model import DEVELOPMENT_DATES
from synthetic_fixtures import synthetic_enc_source
from test_regional_corridors import domain,GOOD,BAD
from transit_cohort import select
from validate_regional_ais import FROZEN_CONFIG


class GroupedPipelineTests(unittest.TestCase):
    def test_actual_selection_search_metrics_census_and_failure_audit(self):
        source=synthetic_enc_source()
        source['layerRegistryVersion']=2
        for key,name in LAYERS_V2.items():
            source['layers'].setdefault(str(key),dict(metadata=dict(name='Harbor.'+name),objectIdField='OBJECTID',features=[]))
        nodes,edges,_,_,water=mesh(source,dict(id='SYNTHETIC'),domain().config)
        datasets=[]
        for index,date in enumerate(DEVELOPMENT_DATES):
            source_sha=hashlib.sha256(date.encode()).hexdigest()
            selected,census,counts=select({'GOOD':GOOD,'BAD':BAD},FROZEN_CONFIG,[-.01,-.01,.01,.01],source_sha)
            tracks=[dict(trackKey=k,observations=t,fold=index%2,vesselGroup=str(index%2),
                trainingStatus='USED' if t==GOOD else 'CHART_WATER_CONFLICT') for k,t in selected]
            datasets.append(dict(date=date,source=dict(sha256=source_sha),candidateCensus=census,
                                 selectedKeys=[k for k,_ in selected],counts=counts,tracks=tracks))
        # Use the exact JSON representation that the real model loader returns.
        model=json.loads(json.dumps(dict(modelVersion='SYNTHETIC',datasets=datasets)))
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp)
            for name in ('graph','model'):
                (root/name).write_text('SYNTHETIC')
            with ExitStack() as stack:
                stack.enter_context(patch('evaluate_od_corridors.physical_context',return_value=(
                    dict(graphVersion='SYNTHETIC'),dict(sha256='CHART'),water,nodes,edges)))
                stack.enter_context(patch('evaluate_od_corridors.read_model',return_value=model))
                result=run(root/'graph',root/'chart',root/'model',root/'result')
                with self.assertRaisesRegex(ValueError,'already exists'):
                    run(root/'graph',root/'chart',root/'model',root/'result')
            summary=audit(result,model)
            self.assertEqual(6,len(summary['paired']))
            for paired in summary['paired']:
                self.assertEqual(1,paired['commonMeasuredCases'])
                self.assertTrue(all(s['acceptance']['status']=='FAIL' for s in paired['summary']))
            self.assertEqual('NOT_PASSED',summary['validationGate'])
            for mutation in ('case','census','acceptance','fold','template','source','role','objective'):
                changed=deepcopy(result)
                row=changed['reports'][1]
                measured=next(c for c in row['cases'] if c['status']=='PHYSICAL_MODEL_MEASURED')
                if mutation=='case': row['cases'].pop()
                elif mutation=='census': row['candidateCensus'].pop()
                elif mutation=='acceptance': row['regionalPhysicalAcceptance']['status']='PASS'
                elif mutation=='fold': measured['evaluationFold']=99
                elif mutation=='template': measured['templateSelection']['trackKey']='UNKNOWN'
                elif mutation=='source': row['source']['sha256']='CHANGED'
                elif mutation=='role': changed['evaluationRole']='INVENTED_SUCCESS'
                elif mutation=='objective': row['routingObjective']='SELECT_BEST_AFTER_OUTCOME'
                with self.subTest(mutation=mutation):
                    with self.assertRaises(ValueError): audit(changed,model)


if __name__=='__main__': unittest.main()
