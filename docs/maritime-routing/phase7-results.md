# Fase 7: resultados de integración

**PHASE7_INTEGRATED_RESEARCH_PLANS: PASS**, 2026-09-22. Los 16 controles de
cierre y las 89 pruebas Java pasan, sin fallos, errores ni pruebas omitidas.
Se cumplieron los [criterios de la fase](phase7-plan.md): rutas meteorológicas
calculadas en el servidor, contratos, comparación conjunta, procedencia,
explicaciones y recálculo con revisiones inmutables.

## Evidencia real y alcance

Se cargó el grafo congelado de 102.604 nodos y 2.356.006 aristas dirigidas y el
pronóstico ECMWF de 2026-09-22 00 UTC hasta +168 h. La integración HTTP ejecutó
dos rutas entre los nodos oceánicos del caso Atlántico, con velocidades declaradas
de 18 y 14 nudos, y tres alternativas comerciales sintéticas. Los nodos no se
presentan como puertos ni terminales verificados.

El recálculo cerró la arista **812064**, usada por la ruta original. Ambas rutas
recalculadas la evitan; se guardó una nueva revisión vinculada al ID y hash del
padre. El contenido original permaneció idéntico al recuperarlo desde un nuevo
almacén. La explicación identifica `routes` como única sección de entrada cambiada.

| Revisión | Ruta | Distancia | Llegada UTC del 22 de septiembre |
| --- | --- | --- | --- |
| Original | base | 442,529 km | 13:15 |
| Original | conservative | 442,529 km | 17:00 |
| Recalculada | base | 447,754 km | 13:30 |
| Recalculada | conservative | 447,754 km | 17:25 |

Las llegadas incluyen navegación y espera modelada. Cada resultado de escenario
combina ese tiempo físico con horas no marítimas y horas adicionales declaradas.
El cierre comprueba esa identidad para todas las alternativas y escenarios, con
redondeo a seis decimales de hora. Las tarifas y probabilidades conservan su origen
declarado: no se recalibran a partir de la distancia o el clima.

Los 54 archivos vinculados de fases anteriores y sus evidencias permanecen
idénticos. Se verificaron los hashes del grafo y manifiesto meteorológico y la
exclusión de las 16.330 aristas regionales restringidas. Un pronóstico incorrecto
devuelve 409; cerrar todas las salidas del origen devuelve 422. Ninguno de esos
fallos añade un plan ni guarda una comparación parcial.

## Pruebas y mediciones

Ocho pruebas de integración aislada cubren tiempos/costos, vinculaciones,
versiones, JSON estricto, valores nulos, integridad, persistencia, cuota,
concurrencia y errores. Una prueba adicional ejecuta el caso real descrito arriba.
Las otras 80 son regresiones de rutas físicas, clima, contratos y simulación.
El filtro heredado sigue rechazando `/api/incoterms/calculate` con 503; la
[aclaración de compatibilidad](phase7-api.md#aclaración-de-compatibilidad)
se encuentra al final del contrato de la API.

En esta ejecución local, cargar el grafo tomó 14,50 s y el pronóstico 1,98 s;
crear el plan tomó 5,13 s y recalcularlo 5,30 s. El heap ocupado observado después
de las consultas fue 1.188.891.408 bytes, con límite de 2.304 MiB. Es una medición
de una ejecución, no un máximo de memoria ni una garantía de latencia o capacidad.

## Uso y reproducción

La [API de fase 7](phase7-api.md) documenta los endpoints, el
[ejemplo completo](phase7-example.json), límites y errores. Configurar
`routing.maritime.plans.directory` en una instancia local para crear o leer
planes; por defecto está vacío y esas operaciones responden 503. Grafo y
pronóstico requieren las versiones fijadas del [runbook de fase 4](phase4-runbook.md).

Desde la raíz, con los artefactos conservados disponibles:

```powershell
$env:MAVEN_OPTS = '-Xmx256m'
mvn -q "-DargLine=-Xmx2304m" "-Dmaritime.plan.real=true" "-Dtest=PlanIntegrationTest,RealPlanIntegrationTest,DiscreteRiskTest,ScenarioSimulationTest,SimulationControllerTest,SimulationEvidenceTest,ContractEvaluatorTest,ContractControllerTest,ContractEvidenceTest,WeatherRoutingTest,GraphArtifactLoaderTest,MaritimeRoutingControllerTest,RouteSearchTest" test
.venv-weather/Scripts/python scripts/maritime/summarize_plan_phase7.py --output data/maritime-routing/phase7-evidence-v2
```

Elegir siempre un directorio de evidencia nuevo. La prueba real es optativa y
requiere `-Dmaritime.plan.real=true`; omitirla no reproduce este cierre. El
verificador exige los 89 resultados sin omisiones, informes posteriores a sus
entradas, hashes conservados y los resultados reales de creación/recálculo.

La evidencia original está en
`data/maritime-routing/phase7-evidence-v1/status.json`: informes de pruebas,
entradas, ambas revisiones, métricas y hashes. Los archivos de datos permanecen
locales según la política existente del repositorio; deben conservarse junto con
los artefactos físicos para reproducir el experimento.

## Límites del cierre

La correspondencia contractual, costos, pérdidas, probabilidades conjuntas y
tiempos adicionales son escenarios declarados de investigación. No se afirma
calibración empírica, recuperación de seguros, consumo, emisiones, ETA operativa
ni acceso verificado a terminales. Agregar una demora de escenario no vuelve a
calcular el clima de un recorrido desplazado en el tiempo.

El almacenamiento local tiene cuota y verifica integridad; no incorpora
autorización por propietario, cifrado ni firmas. No compartirlo entre procesos
escritores. Esta fase no despliega el servidor, cambia su configuración, escribe
en MongoDB ni modifica las implementaciones congeladas. El despliegue público
requiere un alcance posterior de protección de datos y control de acceso.
