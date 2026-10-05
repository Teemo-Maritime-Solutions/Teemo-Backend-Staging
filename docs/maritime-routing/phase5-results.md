# Fase 5: modelo contractual de investigación completado

**PHASE5_CONTRACTUAL_RESEARCH: PASS.** Cierre con 12 comprobaciones, 60 pruebas
Java aprobadas, cero fallos, errores u omisiones. Los importes y pérdidas de las
pruebas son sintéticos; no constituyen cotizaciones, contratos o pólizas reales.

Se implementaron los once términos, ambas variantes FCA, lugares e hitos de
entrega explícitos, separación entre compraventa y transporte, costos por actor,
riesgo de carga por tramo y control declarado de ruta. La descarga incluida en
el contrato cambia su pagador sin desplazar el riesgo. CIF/CIP distinguen la
obligación de seguro de una recuperación efectiva desconocida.

API: [phase5-api.md](phase5-api.md). Reglas y fuentes primarias:
[phase5-sources.md](phase5-sources.md). Ejemplo probado por HTTP:
[phase5-example-cif.json](phase5-example-cif.json).

## Resultado del ejemplo sintético CIF

| Magnitud | Resultado |
| --- | ---: |
| Precio declarado de carga | 100.000 USD |
| Costos logísticos del vendedor | 4.750 USD |
| Costos logísticos del comprador | 1.200 USD |
| Monto mínimo de seguro bajo la regla resumida | 110.000 USD |
| Daño hipotético en mar | 10% |
| Pérdida bruta del comprador, incluido gasto adicional declarado | 10.250 USD |
| Recuperación del seguro | Desconocida (`null`) |

Durante el tramo oceánico: transporte a cargo del vendedor, riesgo de carga del
comprador, control de ruta declarado del transportista. El precio de venta no se
añade a los totales logísticos. La pérdida es un resultado condicional, sin
probabilidad. No se agregan valores expuestos de distintos tramos ni escenarios.

## Verificación

- 24 pruebas de dominio: matriz de once términos y variantes FCA, casos de
  descarga, valores faltantes, redondeo, conservación de costos, asignaciones
  adicionales y entradas inválidas.
- 3 pruebas HTTP: catálogo, evaluación, versiones, reconocimiento de alcance,
  rechazo de campos desconocidos, claves duplicadas y coerciones.
- 1 prueba de evidencia: ejecuta el JSON publicado, comprueba resultados
  monetarios concretos y verifica las implementaciones congeladas.
- 32 regresiones: meteorología (8), lector de grafo (5), API física (5), búsqueda
  de rutas (14).

El primer intento Maven fue bloqueado al resolver dependencias desde el entorno
restringido; no se contó como prueba. La ejecución autorizada terminó con código
0. El cierre conserva los hashes de 31 archivos de fases 3/4, el ZIP del grafo y
el manifiesto meteorológico. No se repitieron los experimentos AIS ni la búsqueda
real de tres cuencas: se conserva su evidencia previa y se ejecutaron regresiones
del software. No se modificaron configuración de despliegue, base de datos o
historial Git.

Evidencia persistente: `data/maritime-routing/phase5-evidence-v1/status.json`,
con los siete informes de pruebas, petición, respuesta, documentación y hashes.
El directorio es local e ignorado por Git; debe conservarse junto con el resto de
artefactos de investigación. La implementación contractual permanece en el código
del repositorio.

## Reproducción

Desde la raíz del repositorio, Java 17 y Maven:

```powershell
$env:MAVEN_OPTS = '-Xmx256m'
mvn -q "-DargLine=-Xmx768m" "-Dtest=ContractEvaluatorTest,ContractControllerTest,ContractEvidenceTest,WeatherRoutingTest,GraphArtifactLoaderTest,MaritimeRoutingControllerTest,RouteSearchTest" test
.venv-weather/Scripts/python scripts/maritime/summarize_contract_phase5.py --output data/maritime-routing/phase5-evidence-replay-new
```

La prueba de evidencia requiere los protocolos y archivos congelados locales;
el cierre también requiere el grafo, manifiesto y estados de las fases anteriores.
El script usa solo biblioteca estándar de Python y rechaza sobrescribir una
carpeta de cierre. Si cambia la implementación contractual, actualizar su versión
y registrar nueva evidencia; no reemplazar el cierre anterior.

## Límites y siguiente fase

Se modela cumplimiento ordinario de condiciones sin modificar. Los hitos,
referencias documentales y facultades de control son declaraciones del cliente.
No se decide propiedad, responsabilidad del transportista, reclamaciones o
normativa local, ni se certifican pólizas o permisos de navegación.

El endpoint heredado `/api/incoterms/calculate` sigue usando tarifas fijas y queda
fuera de esta fase. La nueva API exige migración explícita del consumidor.

La siguiente fase es la **6**: distribuciones y correlaciones justificadas,
simulación con semilla, métricas esperadas, VaR/CVaR con convenciones explícitas y
frontera de Pareto. Este cierre no incorpora probabilidades ni recomienda un
Incoterm o una ruta mediante puntuaciones arbitrarias.
