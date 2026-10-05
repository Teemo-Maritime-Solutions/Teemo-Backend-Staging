# Panamá: conexión geométrica de investigación

El modo sencillo permite calcular Tambo de Mora–Benagil por el canal de Panamá.
Se incorporó el trazado de las esclusas tradicionales a una nueva versión local
del grafo. Los archivos y resultados previos permanecen disponibles.

## Evidencia y alcance

La geometría procede del extracto público fechado de OpenStreetMap distribuido
por [Geofabrik para Panamá](https://download.geofabrik.de/central-america/panama.html),
`panama-260922.osm.pbf`. Se comprobó el MD5 del proveedor y se guardaron el original,
SHA-256, fecha, licencia y procedencia. SHA-256 del extracto:
`c6864f7f07615769d314b3c4d473d4112646506c028d839e0c0dccab7aa4977c`.
Osmium 4.3.1 se instaló únicamente en `target/panama-python`; no se cambiaron las
dependencias de los entornos usados en experimentos anteriores.

Se conservaron los IDs y coordenadas de los nodos y tramos originales. La ruta
importada tiene 153 nodos, 152 segmentos y 76.050,6853 m entre sus extremos de
fuente. Doce segmentos corresponden a tramos etiquetados como esclusas. Se
añadieron cinco conexiones oceánicas, comprobadas contra la costa GSHHG sin
excepción para canales. Las aristas nuevas suman 314, contando ambos sentidos.

Los interiores del canal se verificaron contra los polígonos OSM de agua y
esclusas: el agua documentada tiene prioridad sobre la máscara terrestre global
solo en los segmentos exactos de fuente. No se abrió una franja artificial ni
se alteraron las aristas globales. Se respetaron los huecos de las islas.
Se excluyeron vías marcadas de sentido único del importador recíproco, vías con
restricciones explícitas de acceso y ramales cuya geometría no pasó el control.
En particular, no se habilitan los ramales de las esclusas ampliadas.

La continuidad sigue nodos compartidos reales; un hueco no se cierra por
proximidad. La geometría de los conectores oceánicos es derivada, no observada:
máximo 30 km, hasta tres por extremo, con control costero y distancia geodésica.
Estos límites son parámetros de ingeniería y no una certificación de acceso.

La ACP publica [mapas de uso de suelo](https://pancanal.com/plan-de-uso-de-suelo/)
y [requisitos para embarcaciones](https://pancanal.com/en/maritime-services/notices-to-shipping/).
Se consultaron como contexto; no se digitalizaron ni se usaron para atribuir
autoridad hidrográfica a OSM. La comprobación geométrica comparte la fuente de
cartografía comunitaria: **no es una validación independiente de navegación**.
Profundidad, permiso actual, dimensiones admisibles del buque, reservas, mareas y
demoras permanecen sin validar. La licencia y atribución ODbL se conservan en el
artefacto, la API y junto al mapa del resultado.

## Validación

`validate_panama_research.py` verifica hashes y compara byte por byte todas las
filas heredadas. Recalcula la conectividad dirigida bajo las restricciones
vigentes, comprueba todos los segmentos nuevos con muestreo geodésico más fino,
las parejas inversas, las distancias y la identidad de las coordenadas de fuente.
Los 1.114 puertos anteriores y los controles NOAA de Nueva York se conservan.
No se reutiliza el PASS anterior para las aristas añadidas.

El servidor exige `panamaControlCheck=PASS`, coincidencia del bloque de controles,
hash de evidencia, cantidad de aristas y conectividad. Verifica además el
contenido de la tabla de evidencia. Una ruta con canal no devuelve ETA aunque
se declare velocidad de crucero. El buscador meteorológico rechaza esas aristas
con `CANAL_LOCK_TRANSIT_TIME_MODEL_UNAVAILABLE`, evitando aplicar navegación
oceánica a las esclusas. El cálculo sencillo de distancia sí las admite, con
estado legal `UNKNOWN`; exigir permiso conocido o profundidad sigue bloqueándolas.

Comprobaciones en el backend local:

| Caso | Resultado |
| --- | --- |
| Tambo de Mora → Benagil | 10.601,094582 km; 153 nodos del canal |
| Benagil → Tambo de Mora | Igual distancia; tránsito inverso |
| Excluir `PANAMA_CANAL_RESEARCH` | 16.883,239788 km; recupera el resultado anterior |
| Declarar velocidad de 14 nudos | Misma distancia; ETA nula |
| Dubai → Mina Khalifa | Conserva 80,417261 km; no atraviesa Panamá |
| Petición con la versión anterior del grafo | HTTP 409; no mezcla versiones |

Sin agotamiento del presupuesto de búsqueda. El ahorro modelado en el caso del
usuario es 6.282,145205 km, aproximadamente 37,2 %. No es una comparación de costos
ni de tiempos operativos.

Pruebas automatizadas: 5 Python de geometría, 36 Java de búsqueda, validación,
restricciones y API, y 71 del frontend en Chrome Headless; sin fallos. Compilación
del frontend correcta, con las advertencias previas de Leaflet y estilos del header.
La comprobación final de la API distingue `SOURCED_CANAL_RESEARCH` de los tramos
costeados y regionales. No se efectuó inspección visual manual del navegador.

## Artefactos y reproducción

Activo: `data/maritime-routing/global-panama-research-v2-20260923.zip` y su
`.validation.json`. La primera revisión local comprobó la misma geometría; la
segunda corrige la fecha del manifiesto para reflejar la adquisición de Panamá.
Los hashes definitivos se conservan en el informe de validación y en la
configuración local ignorada por Git.

- Versión: `561c6b9c195fdd599f1cd58c6bf5b0158b2af4f63f191e21d9d39a45e1031aee`.
- SHA-256: `fdd82312dbac099ec31ead3f3c6a0a584f4d5fdad61b8d6041652b0dcaff949d`.
- Implementación conservada: `panama-implementation-v2-20260923.zip`, con hashes
  de los seis scripts y cuatro clases Java que intervienen en esta ampliación.

```powershell
.venv-maritime/Scripts/python scripts/maritime/build_panama_research.py --base data/maritime-routing/global-regional-research-v4-20260921.zip --base-validation data/maritime-routing/global-regional-research-v4-20260921.validation.json --source-receipt data/maritime-routing/panama-osm-20260923/extract.receipt.json --land-receipt data/maritime-routing/snapshot-20260908/gshhg-shp-2.3.7.zip.receipt.json --output <nuevo-artefacto.zip>
.venv-maritime/Scripts/python scripts/maritime/validate_panama_research.py --artifact <nuevo-artefacto.zip> --base data/maritime-routing/global-regional-research-v4-20260921.zip --base-validation data/maritime-routing/global-regional-research-v4-20260921.validation.json --source-receipt data/maritime-routing/panama-osm-20260923/extract.receipt.json --land-receipt data/maritime-routing/snapshot-20260908/gshhg-shp-2.3.7.zip.receipt.json --output <nuevo-informe.json>
.venv-maritime/Scripts/python scripts/maritime/verify_panama_runtime.py
```

Las salidas son inmutables: usar nombres nuevos para reconstruir. Respuestas de
aceptación: `target/panama-runtime-acceptance.json`. Los experimentos históricos
siguen vinculados a sus versiones originales; este cambio no revalida sus
resultados frente a los archivos Java modificados. No se hizo despliegue público.

El lanzador local desactiva DevTools restart y usa un registro distinto por arranque.
`target/local-research-server-latest.json` identifica el registro vigente. Para
volver al estado previo, restaurar `target/panama-previous-local-config.properties`
en la configuración local y reiniciar mediante el lanzador. Esa reversión cambia
el snapshot activo, no borra los artefactos nuevos.
