"""Stage the Suez research notice with an exact preimage hash for safe application."""
import hashlib
import json
from pathlib import Path


relative = Path('src/app/components/planning/simple-route.component.ts')
frontend = Path(r'C:\Ciclo_7\Teemo-FrontEnd-Staging')
stage = Path('target/suez-frontend')
source = frontend / relative
target = stage / 'files' / relative
original = source.read_bytes()
content = original.decode('utf-8')
old = '      <details class="route-details">'
new = ('      <p class="hint" *ngIf="usesSuez">Vía canal de Suez y mar Rojo. Recorrido de investigación: no confirma permiso de tránsito, compatibilidad del buque, seguridad actual ni tiempos de convoy. Geometría del canal: <a href="https://www.openstreetmap.org/copyright" target="_blank" rel="noopener noreferrer">© OpenStreetMap contributors · ODbL</a>.</p>\n'
       + old)
if content.count(old) != 1:
    raise ValueError('Unexpected route details template')
content = content.replace(old, new)
old = "  get usesPanama(): boolean { return this.result?.routes[0]?.explanation.some(text => text.startsWith('PANAMA_CANAL_RESEARCH:')) ?? false; }"
new = old + "\n  get usesSuez(): boolean { return this.result?.routes[0]?.explanation.some(text => text.startsWith('SUEZ_CANAL_RESEARCH:')) ?? false; }"
if content.count(old) != 1:
    raise ValueError('Unexpected canal notice getter')
content = content.replace(old, new)
target.parent.mkdir(parents=True, exist_ok=True)
target.write_bytes(content.encode('utf-8'))
(stage / 'original-hashes.json').write_text(json.dumps({relative.as_posix(): hashlib.sha256(original).hexdigest()}, indent=2))
print(json.dumps(dict(file=relative.as_posix(), originalSha256=hashlib.sha256(original).hexdigest(),
                      stagedSha256=hashlib.sha256(target.read_bytes()).hexdigest())))
