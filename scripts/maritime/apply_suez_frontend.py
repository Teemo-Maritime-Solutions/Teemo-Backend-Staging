"""Apply exactly the staged Suez notice if the frontend file has not changed."""
import hashlib
import json
from pathlib import Path


stage = Path('target/suez-frontend')
frontend = Path(r'C:\Ciclo_7\Teemo-FrontEnd-Staging').resolve()
hashes = json.loads((stage / 'original-hashes.json').read_text())
for relative, expected in hashes.items():
    source = stage / 'files' / relative
    target = (frontend / relative).resolve()
    if not target.is_relative_to(frontend) or hashlib.sha256(target.read_bytes()).hexdigest() != expected:
        raise ValueError('Frontend file changed; refusing overwrite: ' + relative)
    target.write_bytes(source.read_bytes())
    print(json.dumps(dict(file=str(target), sha256=hashlib.sha256(target.read_bytes()).hexdigest())))
