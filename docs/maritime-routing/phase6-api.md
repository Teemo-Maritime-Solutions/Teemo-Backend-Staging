# Simulación contractual y comparación Pareto

`GET /api/v2/incoterms/simulations/model` devuelve versión y límites.
`POST /api/v2/incoterms/simulations` evalúa una distribución conjunta finita y
muestrea resultados con semilla. No accede a fuentes externas ni persiste contratos.

El usuario eligió **escenarios de investigación con probabilidades declaradas**.
La respuesta siempre lleva `USER_DECLARED_UNCALIBRATED`. Validar una tabla y sus
fórmulas no demuestra que sus probabilidades describan viajes reales.

Petición completa: [phase6-example.json](phase6-example.json). Es sintética:
los nombres de alternativas, costos, estados meteorológicos y probabilidades
son hipótesis ilustrativas, no datos descargados o cotizaciones.

## Entradas

- `expectedModelVersion`: `JOINT_DISCRETE_RESEARCH_1`.
- `acknowledgeUncalibratedProbabilities`: `true` explícito.
- `seed`: entero de 64 bits explícito; `samples`: entero entre 100 y 200.000.
- `tailAlpha`: de 0,5 a 0,9999, nivel del cuantil y de la cola VaR/CVaR.
- `confidenceLevel`: de 0,8 a 0,9999, confianza de las cotas de error de muestreo.
  No es probabilidad de que el viaje tenga éxito. Ambos niveles admiten 6 decimales.
- `alternatives`: 1–16 objetos con `id` único y `contract`, una petición completa
  de [fase 5](phase5-api.md). Se requieren costos completos, mismo valor y
  descripción de carga y misma moneda. Las reglas Incoterms pueden ser distintas.
- `distribution`: `version`, `evidenceReference`, `dependenceDescription`,
  `applicability` y 1–256 filas `scenarios`. Se conserva la declaración recibida;
  una referencia documental no equivale a calibración independiente.
- Cada fila tiene `id`, `probability` positiva con hasta 9 decimales y `outcomes`.
  Las probabilidades deben sumar exactamente uno; no se normalizan ni completan.
- `outcomes` contiene exactamente una entrada por alternativa, identificada por
  su ID. Cada entrada declara `lossScenarioId`, existente en su contrato, y
  `durationHours`: duración total 0–87.600 h, hasta 6 decimales.
- `objectives`: lista explícita sin duplicados. Cada objetivo combina una dimensión
  BUYER_LOSS, SELLER_LOSS, BUYER_TOTAL_COST, SELLER_TOTAL_COST o DURATION_HOURS
  con MEAN, VAR o CVAR. Todos se minimizan; no hay pesos predeterminados.

Cada fila conjunta debe ser un estado mutuamente excluyente de **un mismo
experimento comercial comparable**. Un caso sin daños también necesita un
escenario explícito con fracción y gastos cero. No se combinan al azar los daños
de una fila con los tiempos de otra. La correspondencia entre alternativas y
estados es parte de la hipótesis del usuario.

Un resultado referencia un escenario contractual con un tramo de daño y gastos
adicionales agregados por actor. No suma automáticamente daños de varios tramos
ni permite superar el valor de carga por aplicar repetidamente el mismo daño.
La duración no genera automáticamente penalizaciones: deben estar en los gastos
adicionales declarados, excluyendo costos ya incluidos en las partidas base.

## Cálculo y convenciones

Para cada alternativa se evalúa el contrato con el motor de fase 5. Por fila:

`costo total del actor = costo logístico base + pérdida bruta condicional`.

Se excluyen precio de compra, recuperación de seguro y partidas no declaradas.
Los importes base faltantes provocan rechazo, no un total parcial usado para
clasificar. Se conserva la asignación de riesgo del Incoterm y el control de ruta
declarado, sin inferir que un actor pueda ejecutar la alternativa.

La distribución de referencia usa todas las filas y sus masas declaradas. La
media se pondera por esas masas; VaR es el menor valor cuya probabilidad acumulada
alcanza `alpha`; CVaR integra la masa superior `1-alpha`, tomando una fracción
del átomo de frontera cuando es necesario. Por ejemplo, pérdida 0 con masa 0,96
y pérdida 100 con masa 0,04 dan VaR95=0 y CVaR95=80. No se calcula simplemente
la media de todos los valores mayores o iguales a VaR.

Divisiones: DECIMAL128, sin redondear expectativas a centavos antes de comparar.
La asignación contractual conserva su redondeo de fase 5. Correlaciones y cotas
usan punto flotante; se distinguen de los importes decimales.

Filas y alternativas se ordenan por ID. Las probabilidades se expresan en
1.000.000.000 unidades enteras. `java.util.Random(seed).nextInt(1000000000)`
selecciona una fila completa por réplica. El generador Java usa estado de 48 bits:
distintas semillas de 64 bits pueden producir la misma secuencia. Mismos inputs,
versión y semilla producen iguales frecuencias y resultados, sin depender del
orden de filas o alternativas. No se usan hilos de muestreo ni el reloj como semilla.

## Incertidumbre numérica

Se devuelven cinco distribuciones marginales por alternativa. Con `m` marginales,
`n` réplicas y confianza `c`, se usa:

`epsilon = sqrt(log(2*m/(1-c)) / (2*n))`.

Es la cota DKW de una muestra con unión sobre marginales. No exige independencia
entre alternativas, pero su interpretación supone réplicas IID; el generador
pseudoaleatorio aproxima esa condición. Para soporte declarado `[L,U]`:

- Media: estimación ± `(U-L)*epsilon`.
- VaR: cuantiles empíricos en `alpha-epsilon` y `alpha+epsilon`; si salen de
  `[0,1]`, se usa el límite correspondiente del soporte declarado.
- CVaR: estimación ± `(U-L)*epsilon/(1-alpha)`.

Se recortan los intervalos a `[L,U]`. La cota CVaR se obtiene acotando la diferencia
de las integrales de funciones de exceso mediante la distancia uniforme entre
CDFs. Las cotas pueden ser amplias, especialmente en colas pequeñas. No se
estrechan artificialmente si no apareció una pérdida rara: el soporte completo
de la tabla permanece disponible. `nominalSampleTailCount = n*(1-alpha)` puede
ser fraccionario y no es el número de siniestros observados.

Estos intervalos cubren error Monte Carlo bajo el modelo suministrado; no cubren
error de las probabilidades, eventos omitidos, tarifas, predicciones meteorológicas
o calibración real. La referencia finita ya es calculable sin Monte Carlo y sirve
como comprobación. Una semilla particular no garantiza cobertura de un intervalo.

## Dependencia y Pareto

La dependencia se conserva en las filas conjuntas. La respuesta incluye Pearson
ponderado para los pares de dimensiones de cada alternativa. Si una marginal
es constante, devuelve null y `UNDEFINED_CONSTANT_MARGINAL`, no correlación cero.
No se estima una correlación poblacional ni se ajusta una cópula. Las alternativas
comparten el mismo muestreo y se entregan las frecuencias de cada fila.

Pareto se calcula sobre las métricas de la **distribución finita declarada**, para
que el ruido Monte Carlo no cambie el resultado. Una alternativa domina a otra
si no empeora ningún objetivo seleccionado y mejora al menos uno. Los empates
se conservan. Se devuelven vectores, lista de dominadores y frontera; no hay una
recomendación única ni garantía respecto de alternativas que no se suministraron.

## Reproducción, recursos y errores

La respuesta conserva `declaredInputs` normalizados y `inputSha256`: SHA-256 de
su JSON, con propiedades y mapas ordenados. Es un hash del modelo de entrada,
no de los bytes originales de la petición. No se incluyen timestamps de ejecución
en el resultado determinista.

Una simulación simultánea por instancia; las demás reciben 429. Cuerpo máximo:
2.097.152 caracteres. IDs y metadatos propios: 2.048 caracteres; los campos del
contrato mantienen límites de fase 5. Se almacenan frecuencias por fila, sin
expandir todas las muestras por alternativa.

HTTP 400: esquema/parámetros, probabilidades, IDs, ámbito de carga/moneda o
resultados incompletos inválidos. HTTP 409: versión del modelo o reglas distinta.
HTTP 422: costos o valor de carga ausentes. HTTP 413: cuerpo demasiado grande.
Se rechazan claves duplicadas, campos anidados desconocidos, coerciones de texto,
enums numéricos y semillas/recuentos fraccionarios.

La API no modifica el grafo ni vuelve a buscar rutas. La asociación de hitos,
alternativas y versiones físicas/meteorológicas pertenece a la integración de
fase 7. No se deducen probabilidades de siniestro de la fase 4.

## Fuentes matemáticas y técnicas

- [Rockafellar y Uryasev: CVaR para distribuciones generales](https://sites.math.washington.edu/~rtr/papers/rtr187-CVaR2.pdf):
  tratamiento de distribuciones discretas y masa en el umbral de riesgo.
- [Wei y Dudley: desigualdades DKW](https://arxiv.org/abs/1107.5356): la introducción
  recoge la cota de una muestra con constante 2 de Massart, usada aquí. Este motor
  no usa el resultado de dos muestras del artículo.
- [Java 17: Random](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Random.html):
  algoritmo especificado y reproducción de secuencias a partir de la semilla.

La propagación de la cota a media/CVaR y la unión entre marginales son decisiones
matemáticas explícitas de esta implementación, no validación empírica externa.
