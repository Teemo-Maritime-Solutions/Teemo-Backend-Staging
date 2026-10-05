# Paso por Suez en el grafo de investigación

La ruta simple Benagil (`UNLOCODE:PTBNG`) → Dubái (`UNLOCODE:AEDXB`) usa ahora el canal de Suez y el mar Rojo cuando no hay una exclusión declarada. La [Autoridad del Canal de Suez](https://www.suezcanal.gov.eg/English/About/WhySuezCanal/Pages/ImportanceAndAdvantages.aspx) identifica el canal como enlace entre el Mediterráneo y el mar Rojo. Este cambio solo corrige la conectividad geométrica del modelo; no acredita permiso de tránsito, seguridad actual ni aptitud de un buque.

## Evidencia y construcción

- Extracto [Geofabrik Egypt 2026-10-02](https://download.geofabrik.de/africa/egypt.html), `egypt-261002.osm.pbf`, 178 641 115 bytes. MD5 del editor: `11672e3dc92416058bfe8e0cfb794c32`; SHA-256 local: `b1a14a998c3e2b8c90f4891f4eba53fb68d6f3d7e1da2a5cf22f272ad21aaede`. Recibo y archivo originales: `data/maritime-routing/suez-osm-20261003/`.
- Se importó exclusivamente la línea continua `waterway=canal`, `name:en=Suez Canal`, conservando los identificadores de nodos y vías OSM. Longitud de la línea: 161 860,119 m; 92 nodos, 91 segmentos. No se importaron canales de riego ni ramales alternativos.
- Cinco conexiones desde los extremos OSM al grafo oceánico existente pasan la comprobación de línea libre de costa GSHHG. Los datos anteriores, incluidos Panamá, puertos, controles regionales y restricciones, se heredan sin alterar sus registros.
- Artefacto: `data/maritime-routing/global-suez-research-v1-20261003.zip`, SHA-256 `82e591e8ea80671a7630aaad1b175fa88924dfcef39f45e552e843701893e198`, versión `02b3110fcf31611c74327b24cf55e437547202fbd757a59dcdef970eb5f0e762`. Informe: `global-suez-research-v1-20261003.validation.json` (`geometricCheck`, `regionalControlCheck`, `panamaControlCheck` y `suezControlCheck`: `PASS`). El servidor exige coincidencia de artefacto, versión, evidencia y conteos antes de cargarlo.

## Prueba local

Con el backend cargando la nueva versión, `scripts/maritime/verify_suez_runtime.py` obtuvo:

| Caso | Distancia del modelo | Paso |
| --- | ---: | --- |
| Benagil → Dubái | 9 276,432 km | Suez, 92 nodos OSM |
| Dubái → Benagil | 9 276,432 km | Suez, sentido inverso |
| Benagil → Dubái con `userSuppliedProhibitedZones: ["SUEZ_CANAL_RESEARCH"]` | 18 334,395 km | Cabo de Buena Esperanza |
| Tambo de Mora → Benagil | 10 601,095 km | Panamá, sin cambio |
| Dubái → Mina Khalifa | 80,417 km | Sin cambio |

El frontend simple detecta `SUEZ_CANAL_RESEARCH` en la explicación, muestra la procedencia OSM y advierte que el resultado es de investigación. El recorrido con Suez no emite una ETA aun cuando se suministre velocidad: faltan tiempos de convoy y espera. El enrutamiento temporal con clima rechaza el paso con `SUEZ_TRANSIT_TIME_MODEL_UNAVAILABLE`. Puede excluirse la zona declaradamente para comparar con la alternativa por el cabo.

## Límites

La geometría OSM y GSHHG no constituye carta náutica ni validación hidrográfica independiente. `legalStatus=UNKNOWN`, `minimumDepthM=null` y el modelo no sabe si el tránsito está permitido para la fecha, el buque o la situación de seguridad en el mar Rojo. No calcula restricciones de convoy, costos, viento, oleaje o ETA del paso. La [fuente OSM](https://www.openstreetmap.org/copyright) exige conservar atribución y obligaciones ODbL al distribuir los datos derivados.
