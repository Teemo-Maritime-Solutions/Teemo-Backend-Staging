# Fase 6: escenarios conjuntos de investigación

El usuario eligió explícitamente probabilidades declaradas, sin afirmar
calibración real. Alcance: `PHASE6_DECLARED_JOINT_SCENARIO_RESEARCH`.
Se conserva el código congelado de fases 3, 4 y 5.

## Decisiones antes de evaluar

1. Distribución finita conjunta: cada fila representa un estado mutuamente
   excluyente, con probabilidad explícita y resultado para cada alternativa.
   Suma exacta uno, sin normalización silenciosa. No se inventan marginales,
   independencia, cópulas ni probabilidades a partir del pronóstico ECMWF.
2. Cada alternativa incorpora una petición contractual de fase 5. Los resultados
   conjuntos referencian sus escenarios de pérdida y declaran duración total.
   Costos base y pérdidas por comprador/vendedor se obtienen del motor contractual.
   Datos económicos incompletos impiden comparar; no se convierten en ceros.
3. Muestreo con semilla, orden canónico por ID y la misma fila sorteada para todas
   las alternativas. Conservar frecuencias; reportar covariación de resultados.
4. Media, VaR y CVaR para pérdidas y costos por actor y duración. VaR es el
   cuantil inferior; CVaR integra exactamente la masa superior `1-alpha`,
   incluyendo solo la fracción necesaria de un átomo en el umbral.
5. Referencia de soporte finito calculada con aritmética decimal, además de la
   estimación Monte Carlo. Intervalos simultáneos conservadores mediante DKW y
   unión sobre las distribuciones marginales reportadas; cuantifican solo error
   de muestreo bajo el modelo suministrado, no incertidumbre del mundo real.
6. Pareto sobre métricas de referencia y objetivos explícitos, todos minimizados.
   Empates conservados, ninguna suma ponderada, ningún ganador inventado. Se
   exponen los dominadores y vectores de objetivos.

## Criterios de aceptación

- Oráculos analíticos con átomos en VaR y cola de tamaño fraccionario; constantes,
  colas no observadas y suma de probabilidades inválida.
- Conservación de dependencia conjunta; repetición exacta con semilla y bajo
  reordenación de filas y alternativas; cambio de semilla cambia el muestreo.
- Pareto conocido, empates y conflictos comprador/vendedor; datos faltantes,
  monedas mezcladas, IDs duplicados y entradas HTTP inválidas rechazadas.
- Ejemplo contractual reproducible, controles de recursos, regresiones y hashes
  congelados. Documentar fórmulas, convenciones, fuentes y límites de evidencia.

La validación es matemática y de ingeniería. No valida las probabilidades
declaradas contra viajes, siniestros o tarifas observados. La fase 7 integrará
las APIs de comparación y procedencia con las rutas y su recálculo.
