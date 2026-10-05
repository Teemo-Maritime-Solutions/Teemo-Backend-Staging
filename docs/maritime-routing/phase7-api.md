# Planes integrados de investigación

La fase 7 conecta la búsqueda meteorológica de fase 4, las obligaciones de fase 5
y la simulación conjunta de fase 6. Las rutas se calculan en el servidor antes
de evaluar la comparación. Los lugares contractuales y las probabilidades siguen
siendo declaraciones de investigación.

| Método y ruta | Operación |
| --- | --- |
| GET `/api/v2/maritime/plans/model` | Versión y límites |
| POST `/api/v2/maritime/plans` | Calcular rutas y guardar una comparación inmutable |
| GET `/api/v2/maritime/plans/{id}` | Leer revisión íntegra, verificando su hash |
| GET `/api/v2/maritime/plans/{id}/provenance` | Fuentes, versiones y vinculaciones contractuales |
| GET `/api/v2/maritime/plans/{id}/explanation` | Criterios, dominancia, datos ausentes y cambios |
| POST `/api/v2/maritime/plans/{id}/recalculate` | Crear revisión hija desde entradas completas |

Ejemplo completo: [phase7-example.json](phase7-example.json). Usa nodos oceánicos
del experimento Atlántico conservado, nunca presentados como puertos, y contratos,
costos, pérdidas y tiempos adicionales sintéticos. La alternativa `conservative`
declara 14 nudos en calma frente a 18 en `base`; esos nombres no certifican seguridad.

## Entrada de un plan

- `expectedPlanVersion`: `INTEGRATED_RESEARCH_PLAN_1`.
- `acknowledgeDeclaredEventMapping`: `true` explícito.
- `routes`: de 1 a 4 elementos con `id` y `request`, la petición íntegra de
  [rutas meteorológicas](phase4-api.md), incluidos pins y límites de búsqueda.
  Todas deben compartir origen, destino, salida, modo, grafo y pronóstico.
- `alternatives`: de 1 a 16 elementos con `id`, `routeId`, `carriageLegId`,
  `mappingEvidence`, `nonSailingHours` y `contract`, la petición de [fase 5](phase5-api.md).
  Cada ruta debe usarse al menos una vez. Varias alternativas pueden compartirla
  y diferir en condiciones comerciales. No se generan los once términos por
  defecto: evaluar otro requiere sus lugares, costos y contrato explícitos.
- `simulation`: parámetros de [fase 6](phase6-api.md), incluida su versión,
  reconocimiento de probabilidades no calibradas, semilla, muestras, niveles y
  objetivos. En cada resultado de fila conjunta, `additionalHours` sustituye
  `durationHours`; `lossScenarioId` conserva su significado contractual.

La vinculación cubre un **tramo contractual completo**. Sus hitos `fromEventId`
y `toEventId` se obtienen del contrato; el origen y destino físicos, de la ruta.
`mappingEvidence` debe explicar su correspondencia declarada. No se certifica
la asociación de un nodo con un terminal ni que haya ocurrido una entrega real.
Si el riesgo se transfiere dentro del tramo representado, hace falta segmentar
correctamente el contrato y preparar otra representación; el motor no mueve ese
punto por proximidad geográfica.

## Tiempos, costos y alcance temporal

Para cada alternativa y estado conjunto:

`duración = horas físicas de ruta + nonSailingHours + additionalHours`.

Las horas físicas son llegada menos salida; incluyen navegación y espera
modelada, ambas también disponibles por separado en la respuesta original.
Se redondean HALF_UP a seis decimales de hora, con error máximo 0,0018 segundos.
Los tiempos adicionales son explícitos y no negativos, con hasta seis decimales;
el total debe respetar el límite de fase 6 de 87.600 horas.

El pronóstico solo se comprobó durante el recorrido físico calculado. Agregar
una demora no desplaza el buque y vuelve a verificar el clima de cada arista.
Los tiempos de escenario no son nuevas rutas meteorológicas ni ETA calibradas.
Los campos `forecastRun`, `forecastValidUntil`, `mode`, `search`, restricciones,
`edgeCoverage` y datos faltantes se conservan de cada resultado físico.

No hay reestimación automática de fletes, impuestos, daños, probabilidades,
combustible o emisiones. Se aplican exactamente los importes del contrato y los
escenarios declarados. Los costos incompletos se rechazan antes de buscar rutas.
Si una ruta falla, no se guarda una comparación parcial ni se elimina su
alternativa para producir una frontera aparentemente mejor.

## Respuesta, procedencia y explicación

La envoltura contiene `planId` aleatorio, `createdAt`, `contentSha256` y `content`.
El contenido incluye las entradas, rutas completas con geometría, manifiestos de
grafo/pronóstico, vinculaciones, evaluación contractual y comparación.

Cada vinculación expone pagador, portador del riesgo de carga y control declarado
de ruta. La explicación conserva objetivos, vectores y dominadores, frontera y
criterios. Una alternativa Pareto no es autorización para que un actor la ejecute.
La incertidumbre Monte Carlo no sustituye calibración de probabilidades.

`inputSha256`, hashes de rutas y `contentSha256` usan JSON canónico: objetos
ordenados y números decimales normalizados; el orden de arrays se conserva.
`comparisonInputSha256` usa el convenio propio de fase 6 para su entrada derivada.
Estos hashes detectan corrupción respecto del contenido almacenado; no son firmas,
autenticación del contrato ni protección frente a un operador que edite archivo
y hash conjuntamente.

## Recálculo y revisiones

Enviar al endpoint de recálculo:

```json
{
  "expectedParentContentSha256": "<hash de la revisión anterior>",
  "commercialReviewEvidence": "<referencia a la revisión de supuestos comerciales>",
  "replacement": { "...": "petición de plan completa" }
}
```

`replacement` es un objeto de plan válido, no un parche. Permite cambiar salida,
cierres, modelo de buque, contratos o escenarios, sujetos a las mismas reglas.
Se verifican el padre y su hash; un hash desactualizado devuelve 409. Se crea un
ID nuevo y se conserva el padre. Se permiten ramas independientes del mismo padre.

La revisión hija registra ID/hash del padre, evidencia de revisión comercial,
secciones de entrada cambiadas, hashes de rutas anteriores/nuevas, llegadas,
cambios de distancia y fronteras anteriores/nuevas. No resta costos de monedas
distintas ni trata los supuestos conservados como cotizaciones actualizadas.

Los catálogos físicos y meteorológicos siguen siendo snapshots inmutables por
proceso. Un cambio de pronóstico o grafo requiere configurar sus pins y reiniciar,
como en fase 4. Con el mismo almacén, el padre permanece legible y la petición
completa de recálculo debe usar las nuevas versiones disponibles. No hay descarga
ni cambio de archivo de grafo a través de una petición HTTP.

## Almacenamiento y límites

Configurar un directorio dedicado en una instancia local de investigación:

```properties
routing.maritime.plans.directory=target/maritime-routing/local-plans
```

La configuración de grafo y pronóstico sigue [phase4-runbook.md](phase4-runbook.md).
La propiedad está vacía por defecto: crear o leer planes devuelve 503 hasta que
el operador la configure. Esta fase no modifica `application.properties` ni activa
un servidor. No escribe en MongoDB.

Archivos inmutables por UUID; lectura con verificación de integridad. Máximo 128
entradas, reserva conservadora dentro de 256 MiB totales y 8 MiB por archivo.
Al alcanzar la cuota se devuelve 507; no se borran revisiones automáticamente.
No compartir el almacén entre procesos escritores. Respaldar el directorio si se
requiere conservar la historia entre reinicios o cambiar de máquina.

Una creación o recálculo simultáneo por instancia; otra recibe 429. La búsqueda
meteorológica conserva también su permiso compartido con su API original. Sumando
rutas se permiten hasta 120 segundos de presupuesto de búsqueda, 400.000 estados
y 1.000.000 de etiquetas. Esto no es un límite total de tiempo HTTP: carga inicial,
serialización y comparación añaden trabajo. Cuerpo de entrada máximo 2.097.152
caracteres. Los límites adicionales de fases 4–6 permanecen vigentes.

La respuesta usa `Cache-Control: no-store`. No existe un endpoint de listado.
Los IDs son referencias difíciles de adivinar, **no autorización de usuarios**:
la configuración actual del proyecto permite todas las rutas. El almacén contiene
contratos en texto claro; desplegarlo públicamente exige implementar autorización
por propietario y una política de protección/retención. Ese despliegue no forma
parte de esta fase de investigación.

| HTTP | Causa |
| --- | --- |
| 400 | Esquema, vinculación, presupuestos, ámbito de comparación o datos inválidos |
| 404 | ID inexistente o formato de ID no admitido |
| 409 | Versión/pin incompatible, padre distinto o pronóstico no utilizable como actual |
| 413 | Entrada o resultado demasiado grande |
| 422 | Costos/valor ausentes o ruta rechazada por datos, restricciones, horizonte o presupuesto |
| 429 | Creación/recálculo o búsqueda meteorológica en curso |
| 503 | Almacén o snapshots sin configurar, inaccesibles o corruptos |
| 507 | Cuota del almacén alcanzada |

## Aclaración de compatibilidad

El filtro `UnverifiedLegacyRoutingFilter` ya bloquea `/api/incoterms/calculate`
y los cálculos heredados de rutas/clima con 503. Las notas de fase 5 que indican
que el endpoint anterior conserva su comportamiento describen la clase heredada,
pero omiten ese filtro. **No está habilitado como calculador alternativo.** Esas
notas congeladas se conservan como historial; esta aclaración y la prueba HTTP
de fase 7 reflejan el comportamiento efectivo. Usar las APIs v2 explícitamente.
