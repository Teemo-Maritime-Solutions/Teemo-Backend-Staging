# Fase 6 completada en el alcance de investigación elegido

**PHASE6_DECLARED_JOINT_SCENARIO_RESEARCH: PASS**. El usuario eligió explícitamente
probabilidades declaradas, sin afirmar calibración real. Pasaron 17 comprobaciones
de cierre y 80 pruebas Java, sin fallos, errores ni omisiones.

La implementación incorpora distribución conjunta finita, muestreo reproducible
con semilla, medias y VaR/CVaR por actor, duración, cotas de error Monte Carlo y
frontera de Pareto con objetivos explícitos. Usa el motor contractual de fase 5.
No extrae probabilidades de las olas ni estima riesgos a partir de constantes
ocultas. El alcance original de calibración empírica no se afirma satisfecho.

API y fórmulas: [phase6-api.md](phase6-api.md). Petición probada por HTTP:
[phase6-example.json](phase6-example.json). Criterios: [phase6-plan.md](phase6-plan.md).

## Ejemplo registrado, enteramente sintético

Estados conjuntos: normal 0,90; demora 0,08; tormenta 0,02. Son hipótesis de
investigación, no frecuencias observadas. Se usa la misma fila para todas las
alternativas y se conserva la relación entre duración y pérdida.

Objetivos minimizados: costo esperado del comprador, CVaR95 de ese costo y
duración esperada. Las cifras siguientes son de la distribución finita declarada;
la API devuelve además sus estimaciones Monte Carlo.

| Alternativa | Costo esperado comprador (USD) | CVaR95 costo comprador (USD) | Duración esperada (h) | Resultado |
| --- | ---: | ---: | ---: | --- |
| economy | 1.520 | 6.350 | 51,36 | En frontera |
| protected | 1.780 | 3.050 | 53,68 | En frontera |
| dominated | 1.720 | 6.550 | 51,36 | Dominada por economy |

`protected` es un nombre de escenario; no significa póliza contratada ni cobertura
verificada. Su menor daño está declarado en la petición. No existe un ganador
único entre los dos miembros de la frontera sin otra preferencia del usuario.

Semilla 20260922 y 20.000 réplicas: 18.053 estados normales, 1.540 demoras y
407 tormentas. Dos llamadas HTTP produjeron respuestas idénticas. Las pruebas
también conservan resultados al reordenar filas y alternativas, y verifican que
cambiar semilla cambia frecuencias sin cambiar la frontera de referencia.

Una prueba adicional compara CIF y DAP para el mismo escenario: costos esperados
comprador/vendedor 1.520/4.750 frente a 1.240/5.030 USD. Ambos permanecen en la
frontera al minimizar los costos de ambas partes. Es una comprobación sintética
de separación de exposición, no una recomendación contractual.

## Verificación y recursos

- 6 pruebas matemáticas: masa fraccionaria de cola, átomo exacto en VaR,
  distribución constante, cola no muestreada y dominancia.
- 8 pruebas del motor: oráculos numéricos, semilla y orden, dependencia,
  intervalos, empates, entradas inválidas y perspectiva comprador/vendedor.
- 5 pruebas HTTP: esquema, versiones, costos faltantes, tamaño, concurrencia
  y liberación del permiso después de una petición inválida.
- 1 prueba de evidencia: repetición HTTP, hashes y capacidad máxima.
- 60 regresiones contractuales, meteorológicas y del grafo de investigación.

En la ejecución local registrada, el ejemplo HTTP tomó 27,68 ms; el caso de
capacidad máxima del motor, con 200.000 réplicas, 16 alternativas y 256 filas,
tomó 61,96 ms. Las réplicas se acumulan en frecuencias; no se construyen 200.000
copias de cada contrato. El caso máximo tiene alternativas idénticas y retiene
las 16 en la frontera. Heap usado después de consultas: 58.941.664 bytes; límite
configurado: 805.306.368 bytes. No es pico RSS, prueba de carga, percentil ni SLA.

El entorno restringido bloqueó inicialmente la resolución Maven. Tras ejecutar
con acceso autorizado, las pruebas detectaron un código HTTP incorrecto para
versión ausente y una pérdida de escala decimal en el auxiliar de reordenación
de pruebas. Ambos se corrigieron; la ejecución final Maven terminó con código 0.
No se cambiaron probabilidades para ajustar resultados ni se omitieron pruebas.

Se verificaron los 41 archivos vinculados al cierre de fase 5, incluidos los
protocolos y las implementaciones de fases 3/4. El grafo y el manifiesto del
pronóstico conservan sus hashes. La evidencia anterior permanece intacta. No se
repitieron adquisiciones AIS, reconstrucciones ni experimentos oceánicos reales.
No hubo despliegue, cambio de configuración, escritura en base de datos o commit.

## Evidencia y reproducción

Evidencia local persistente e ignorada por Git:
`data/maritime-routing/phase6-evidence-v1/status.json`. La carpeta contiene
petición, respuesta, caso de capacidad, tiempos, once informes de pruebas y
documentación con sus hashes. Conservarla con las evidencias previas.

Desde la raíz del repositorio:

```powershell
$env:MAVEN_OPTS = '-Xmx256m'
mvn -q "-DargLine=-Xmx768m" "-Dtest=DiscreteRiskTest,ScenarioSimulationTest,SimulationControllerTest,SimulationEvidenceTest,ContractEvaluatorTest,ContractControllerTest,ContractEvidenceTest,WeatherRoutingTest,GraphArtifactLoaderTest,MaritimeRoutingControllerTest,RouteSearchTest" test
.venv-weather/Scripts/python scripts/maritime/summarize_simulation_phase6.py --output data/maritime-routing/phase6-evidence-replay-new
```

Los scripts Python usan biblioteca estándar. El cierre rechaza sobrescribir su
directorio y rechaza informes de pruebas anteriores a los archivos vinculados.
El ejemplo puede regenerarse en otra ruta con
`scripts/maritime/create_phase6_example.py --output <ruta-nueva>`.

## Límites y siguiente fase

La validación cubre reglas matemáticas, esquema, ejecución y conservación de
dependencia declarada. Las probabilidades, pérdidas y duraciones no están
calibradas. Los intervalos cuantifican error Monte Carlo bajo esa distribución,
sin cubrir eventos omitidos ni errores del modelo comercial. No se infiere
recuperación de seguro, control efectivo o viabilidad física de una alternativa.

La siguiente fase es la **7**: integrar rutas, contratos y comparaciones en APIs
versionadas con procedencia, explicaciones y recálculo. El motor de simulación
actual recibe alternativas declaradas y no vuelve a calcular rutas ni las vincula
automáticamente con hitos de entrega o versiones de pronóstico.
