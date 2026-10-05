# Rutas con pronóstico y buque parametrizable

`GET /api/v2/maritime/weather` devuelve el manifiesto, su SHA-256 como versión,
la vigencia y el estado de antigüedad. No descarga datos durante una petición.

`POST /api/v2/maritime/weather-routes` calcula una ruta de llegada más temprana
dentro del modelo temporal discretizado y del presupuesto declarado. El endpoint
de distancias de fase 3 conserva su contrato. Esta nueva ruta no estima consumo,
emisiones, costos contractuales ni probabilidades de riesgo.

Ejemplo de estructura; los identificadores y hashes se obtienen de los snapshots.
Los números del buque son **un escenario declarado, sin calibración**:

```json
{
  "originNodeId": "<identificador exacto del grafo>",
  "destinationNodeId": "<identificador exacto del grafo>",
  "departure": "2026-09-22T00:00:00Z",
  "expectedGraphVersion": "<graphVersion de /graph>",
  "expectedForecastVersion": "<forecastVersion de /weather>",
  "mode": "HISTORICAL_RESEARCH_REPLAY",
  "acknowledgeResearchLimitations": true,
  "requireKnownLegalStatus": false,
  "vessel": {
    "calmSpeedKnots": 18,
    "headWaveLossKnotsPerM2": 0.08,
    "beamWaveLossKnotsPerM2": 0.04,
    "followingWaveLossKnotsPerM2": 0.02,
    "headWindLossKnotsPerMps2": 0.0005,
    "maximumWaveHeightM": 8,
    "maximumWindMps": 35
  },
  "search": {
    "timeStepSeconds": 300,
    "horizonHours": 72,
    "maximumExpandedStates": 100000,
    "maximumLabels": 300000,
    "maximumRuntimeSeconds": 120,
    "acknowledgeModelledWaiting": true
  }
}
```

Se aceptan nodos oceánicos existentes y referencias portuarias con estado
`CONNECTED_REFERENCE_POINT`; no se reubican coordenadas ni se habilitan puertos
rechazados por el control de calidad. Los IDs portuarios son también IDs de nodos.
Todos los parámetros del buque son obligatorios, incluidos coeficientes cero.
Los campos desconocidos se rechazan tanto en la petición como dentro del modelo
de buque y de las opciones de búsqueda.

Opcionales: `draftM`, `underKeelClearanceM`, `closedEdgeIds`, `prohibitedZones`.
El calado exige margen explícito; profundidad desconocida bloquea el tramo.
`requireKnownLegalStatus=true` bloquea permisos desconocidos. Los identificadores
de cierres y zonas deben existir en la versión del grafo. La meteorología nunca
anula una prohibición ni una restricción regional pendiente.

La respuesta incluye `route.legs`, llegada UTC, segundos navegando y segundos de
espera modelada, distancia en metros, estados explorados, causas de rechazo,
parámetros, versiones y datos faltantes. `geometry` es un GeoJSON MultiLineString
densificado cada 10 km como máximo y separado en el antimeridiano; esa representación
no agrega aristas al grafo. `edgeCoverage` y `regionalControls` conservan la distinción
entre cobertura global de costa y controles regionales.

`fuelTonnes` y `emissionsTonnes` son **null**, no cero. La comparación con velocidad
constante es únicamente una referencia de escenario: no mide ahorro ni exactitud.

## Modelo temporal y meteorológico

- Cuadrícula original ECMWF de 0,25 grados; celda más cercana, sin buscar otra celda
  que tenga datos. Interpolación lineal entre instantes y circular para dirección.
  Direcciones opuestas sin dirección media definida se rechazan. No extrapolación.
- La dirección de ola indica **desde dónde llega**. Pérdida en nudos:
  `k(ángulo) × altura² + kViento × vientoDeProa²`. Los tres coeficientes angulares
  se interpolan linealmente entre proa, través y popa. El período medio se conserva,
  pero esta ecuación no lo usa. La formulación es un supuesto explícito de esta fase,
  no una curva calibrada extraída del artículo de Chen.
- La velocidad a través del agua se convierte a m/s. Con corriente lateral `cL`
  y longitudinal `cA`, la velocidad de avance es `sqrt(vAgua² − cL²) + cA`.
  Si no se puede mantener el rumbo o avanzar al menos 0,05 m/s, el tramo se rechaza.
- Integración de Euler explícita: pasos de hasta 2.500 m y 300 s, con rumbo geodésico
  local. Los límites se verifican en puntos muestreados y extremos; no se afirma una
  garantía continua entre muestras ni una cota validada del error numérico.
- Estados `(nodo, intervalo temporal)`. Se permiten llegadas posteriores al mismo
  nodo y arcos de espera. El final de cada tramo se redondea hacia arriba al intervalo;
  ese tiempo se registra como espera y se verifica el clima muestreado del nodo.
  El usuario acepta este supuesto; no hay evidencia de permiso de fondeo o capacidad
  de mantener posición del buque. La optimalidad se limita a este modelo discretizado.
- La heurística usa distancia geodésica y velocidad máxima en calma más la mayor
  magnitud de corriente del snapshot. El lector verifica esa cota contra las celdas;
  las pérdidas no negativas mantienen la cota admisible.

`CURRENT_FORECAST_RESEARCH` exige salida futura, corrida de antigüedad máxima de
48 horas y salida dentro del pronóstico. La política de 48 h es una decisión de
ingeniería, no una certificación de precisión. Una repetición del experimento usa
explícitamente `HISTORICAL_RESEARCH_REPLAY`; no se etiqueta como pronóstico actual.

## Límites y respuestas

Intervalo temporal: 60–3.600 s, divisor de una hora. Horizonte: 1–168 h, recortado al
último instante del snapshot. Máximo 200.000 estados expandidos, 500.000 etiquetas
y 120 s por búsqueda. Una búsqueda meteorológica simultánea por instancia; una
petición concurrente recibe 429. No hay descarga, reconstrucción ni renovación
automática en la API. Fijar el snapshot requiere reiniciar la instancia.

| HTTP | Significado |
| --- | --- |
| 400 | Declaración incompleta, parámetro fuera de límites, campo desconocido o ID de restricción inexistente |
| 409 | Versión diferente, corrida demasiado antigua o salida pasada en modo actual |
| 422 | Extremo no disponible, salida fuera del pronóstico, sin ruta en el modelo/horizonte o presupuesto agotado |
| 429 | Otra búsqueda meteorológica está activa |
| 503 | Grafo o pronóstico ausente, corrupto o sin pin/validación exigida |

`SEARCH_BUDGET_EXHAUSTED` no demuestra desconexión. `NO_ROUTE_WITHIN_MODEL_HORIZON`
puede deberse a datos faltantes, límites declarados o horizonte; no establece que
un viaje real sea imposible. Los contadores son evaluaciones rechazadas, no
probabilidades ni un recuento de peligros independientes.
