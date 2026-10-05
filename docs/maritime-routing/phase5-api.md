# API contractual de investigación

`GET /api/v2/incoterms/rules`: catálogo de once términos, edición, versión y
fuentes. `POST /api/v2/incoterms/evaluate`: evaluación determinista de un término
y un itinerario contractual declarado. No necesita cargar el grafo ni acceder a
una base de datos. No descarga tarifas y no decide qué ruta navegar.

El ejemplo ejecutable está en [phase5-example-cif.json](phase5-example-cif.json).
Todos sus puntos, importes y pérdidas son **sintéticos**, incluso si la referencia
se parece al nombre de un contrato. No es una cotización comercial.

## Petición

- `expectedRuleVersion`: `INCOTERMS_2020_RESEARCH_1`.
- `acknowledgeResearchLimitations`: `true` explícito.
- `term`: EXW, FCA, FAS, FOB, CPT, CIP, CFR, CIF, DAP, DPU o DDP.
- `mode`: SEA_INLAND_WATERWAY, MULTIMODAL, ROAD, RAIL o AIR. FAS/FOB/CFR/CIF
  requieren el primero. La entrega de contenedores a un transportista antes del
  embarque debe representarse con el término y el hito que realmente se pactaron.
- `saleContractReference`, `carriage.contractReference`: referencias separadas.
- `namedEventId`, `deliveryEventId`: identificadores de hitos existentes. En C,
  el lugar nombrado es destino y debe ser posterior a la entrega. En los demás,
  ambos identifican el mismo hito.
- `events`: de 2 a 64 hitos ordenados, cada uno con `id`, `type`, `place` preciso.
  Los tipos admitidos aparecen en `ContractModel.EventType`. El modelo representa
  una descarga de destino como dos hitos consecutivos en el mismo lugar:
  `DESTINATION_READY_FOR_UNLOADING` y `DESTINATION_UNLOADED`. En C el primero es
  el destino nombrado; en DAP/DDP también es entrega; en DPU entrega es el segundo.
  Se pueden añadir tramos antes de entrega o después del destino contractual.
- `legs`: exactamente un tramo entre cada par consecutivo de hitos, con `id`,
  `fromEventId`, `toEventId`, `routingControl` y `controlEvidence`. El control es
  BUYER, SELLER, CARRIER, SHARED o UNKNOWN. UNKNOWN requiere evidencia nula;
  cualquier otro valor necesita referencia. Pagar no implica controlar.
- `carriage.destinationUnloadingIncluded`: booleano obligatorio. Si el vendedor
  organiza transporte, indica si asume el costo de descarga bajo ese contrato.
  DPU lo asigna siempre al vendedor. No mueve el punto de transferencia de riesgo.
- `currency`: código ISO reconocido, una sola moneda; no conversión.
- `cargo`: descripción y `saleValue` con `valueEvidence`. Se permite valor nulo
  con evidencia nula; la exposición monetaria correspondiente queda desconocida.
- `fees`: hasta 128 partidas. Cada una tiene `id`, `kind`, `legId`, `amount`,
  `evidence`, `agreedPayer`. Lista vacía significa costos faltantes.
- `lossScenarios`: hasta 64 resultados hipotéticos independientes; puede estar
  vacía. Cada escenario identifica un tramo, fracción de daño 0–1, pérdidas
  adicionales explícitas de comprador/vendedor y evidencia. No tiene probabilidad.

Toda evidencia usa `kind` USER_DECLARED_SCENARIO o DOCUMENT_REFERENCE y
`reference` no vacía. Una referencia documental es una atribución del cliente,
no una confirmación automática de autenticidad, vigencia o aplicabilidad.

## Partidas y totales

Categorías exigidas para un total completo dentro del modelo:
CHECK_PACK_MARK, EXPORT_CLEARANCE, TRANSIT_CLEARANCE,
IMPORT_CLEARANCE_DUTY_TAX, DESTINATION_UNLOADING y una TRANSPORT por tramo,
excepto el tramo de descarga, que ya tiene su propia partida. CIF/CIP exigen
además INSURANCE. Una categoría que no aplica debe tener cero con evidencia;
omitirla no equivale a cero. Las categorías agrupan costos: el usuario debe
incluir los conceptos aplicables en su importe documentado.

Las partidas TRANSPORT deben excluir descarga, seguros y despachos itemizados
por separado, aunque la cotización original los agrupe. Hay que desglosarla antes
de evaluar; el motor detecta partidas duplicadas, pero no puede detectar que una
cotización opaca incluya dos veces un concepto.

OTHER_AGREED y seguro voluntario requieren `agreedPayer` y referencia. Las demás
partidas no admiten sobrescribir el pagador de la regla. No hay tasas de arancel,
prima de seguro ni flete por kilómetro predeterminados. Precio de compraventa y
otros cargos no listados quedan fuera de estos totales logísticos.

Se aceptan importes no negativos hasta 10^15 con las unidades menores de la
moneda (USD: 2 decimales). Cada actor recibe `knownSubtotal`, `total` y
`missingItems`. Si le falta una partida, `total` es null. Un total cero solo es
completo si todas las partidas exigidas de ese actor están declaradas.
`DECLARED_COSTS_COMPLETE` se refiere a esas categorías, no a auditoría comercial.

## Exposición y seguros

Por tramo se devuelve pagador del transporte, portador de riesgo de carga,
control declarado y valor bruto de carga expuesta. Ese valor se repite en cada
tramo: **no se suma** para obtener riesgo total. Una pérdida condicional es
`valor × fracción de daño`, más pérdidas adicionales declaradas por actor.
Se redondea HALF_EVEN a unidades menores; no se agregan escenarios entre sí.
Las pérdidas adicionales pueden representar demoras o deterioro según el
contrato suministrado, sin deducir responsabilidad de demora del Incoterm.

La obligación de seguro devuelve cobertura mínima, encargado y monto mínimo
cuando el precio se conoce; no confirma contratación ni compensación.
`insuranceRecovery` es null. El monto mínimo se redondea hacia arriba para no
quedar por debajo del umbral. La propiedad, pagos, responsabilidad del
transportista y cobertura efectiva están fuera de esta evaluación.

## Errores, integración y compatibilidad

HTTP 400: datos ausentes/incompatibles, campos desconocidos incluso anidados,
claves duplicadas, enums numéricos, coerciones de cadenas o JSON adicional.
HTTP 409: versión de reglas distinta. HTTP 413: cuerpo mayor de 262.144 caracteres.
IDs y referencias tienen un máximo de 512 caracteres. No se consulta ninguna URL
recibida como referencia.

Los hitos deben segmentar cualquier ruta física en los límites contractuales
antes de incorporarla. No hay asociación automática entre lugar contractual,
puerto UN/LOCODE, arista meteorológica y operación de entrega. Las limitaciones
de navegación y pronóstico de fases 3/4 se conservan.

El antiguo `/api/incoterms/calculate` conserva su implementación heredada de
tarifas fijas; **no forma parte de la fase 5 ni de su evidencia**. Los clientes
deben migrar explícitamente a esta API. No se mezclan sus puntuaciones con los
resultados nuevos. Simulación, probabilidades y Pareto quedan para fase 6.

Semántica y alcance: [fuentes primarias y matriz](phase5-sources.md).
