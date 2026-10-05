# Diagnóstico: Tambo de Mora → Benagil

Verificado el 23 de septiembre de 2026 contra el backend local en ejecución.
La consulta reproduce **16.883,2397876 km**, como en la captura del usuario.
El grafo activo no representa una conexión navegable por el canal de Panamá.
El resultado es el camino más corto del modelo disponible, no una comparación
completa con la alternativa real por Panamá.

## Consulta reproducida

- Endpoint: `POST /api/v2/maritime/routes`.
- Origen: `UNLOCODE:PETDM` (Tambo de Mora).
- Destino: `UNLOCODE:PTBNG` (Benagil).
- Grafo: `e6ac2932cd9c941202dc840322411105e84b88b4ccf9f83182ea9a0e57e63437`.
- Artefacto: `data/maritime-routing/global-regional-research-v4-20260921.zip`.
- Política: distancia; `k=1`, `maxCandidates=1`, `maxExpansions=2000000`,
  `minimumSeparationM=0`, `requireKnownLegalStatus=false`.
- Sin calado, cierres de aristas ni zonas prohibidas aportados por el usuario.
- Respuesta: `RESEARCH_ONLY`, 96 nodos, 44.727 expansiones,
  `budgetExhausted=false`, `rejectedEdgeEvaluations={}`.
- Punto más austral de la geometría: longitud −67,02956287°, latitud −56,01295684°.
  El recorrido rodea el extremo austral por la zona del cabo de Hornos;
  no se debe describir como un tránsito demostrado del estrecho de Magallanes.

## Causa comprobada

El manifiesto y la respuesta declaran `canal_permissions_and_geometry` entre
los datos faltantes. El controlador v2 usa `GraphCatalog` y `PhysicalGraph`,
no el catálogo antiguo de coordenadas manuales `MaritimeNetworkCatalog`, donde
todavía existen identificadores `PANAMA_*`.

Se inspeccionaron todas las aristas del artefacto y se extrajo el subgrafo del
rectángulo longitud [−80,5°, −79°], latitud [8°, 10°]: 44 nodos y 374 aristas,
356 `OCEAN_MESH` y 18 `PORT_CONNECTOR`. No hay aristas de canal. Las referencias
más cercanas a las entradas aproximadas del catálogo antiguo caen en componentes
locales diferentes (26 y 16 nodos). Esto sigue ocurriendo al considerar todas
las aristas locales sin filtrar permisos: no es una exclusión por restricciones.
Las entradas aproximadas se usaron solo para el diagnóstico, no para construir
geometría ni declarar acceso.

La construcción de la malla comprueba las aristas frente a la máscara terrestre;
no incorpora automáticamente canales a partir del mapa base. La cartografía
visible y el grafo de cálculo son capas distintas. Aumentar el presupuesto,
animar de otro modo o pedir más candidatos no crea el enlace ausente.

No se agotó la búsqueda ni se rechazaron aristas por restricciones en esta
consulta. Tampoco intervienen clima, peajes o tamaño del buque en este modo.
No se verificó ni se atribuye el resultado a un cierre real del canal.

## Alcance de la corrección pendiente

Se necesita una nueva versión del grafo con geometría documentada del canal y
conexiones a ambos océanos, restricciones y procedencia explícitas, y pruebas
de continuidad, cruces terrestres y rutas Pacífico–Atlántico. Conservar el
artefacto y la evidencia anteriores. El cambio de selector no resolvió esta
carencia de cobertura del backend.

La [Autoridad del Canal de Panamá](https://pancanal.com/en/maritime-services/trade-routes/)
describe el ahorro de distancia en rutas entre la costa pacífica sudamericana y
Europa. Esa referencia apoya la expectativa del usuario, pero no proporciona
una distancia validada específica para Tambo de Mora–Benagil.

Evidencia local del diagnóstico (en `target`, no versionada):
`panama-diagnosis-live-manifest.json`, `panama-diagnosis-route.json` y
`panama-diagnosis-topology.json`. No se modificaron algoritmos, datos del grafo,
configuración ni frontend durante esta verificación.
