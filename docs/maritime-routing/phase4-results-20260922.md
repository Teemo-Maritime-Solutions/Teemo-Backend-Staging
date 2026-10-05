# Fase 4 completada: pronóstico y navegación temporal de investigación

**PHASE4_VERSIONED_WEATHER_PARAMETRIC_RESEARCH: PASS**, 22 de septiembre de 2026,
hora de Colombia. El cierre automático pasó **17 comprobaciones**. El usuario eligió
un modelo parametrizable de investigación, sin curva de un buque concreto.

## Qué funciona

- Descarga acotada y archivo de originales ECMWF; construcción local del snapshot
  validando fechas, parámetros, unidades, cuadrícula, valores faltantes y hashes.
- Oleaje, viento y corrientes incorporados al tiempo de avance del buque según
  ubicación, rumbo y hora. Coeficientes, límites y velocidad declarados por el usuario.
- Búsqueda en estados nodo/tiempo, con espera explícita, horizonte finito y presupuesto.
  No se presupone FIFO. La búsqueda conserva llegadas posteriores al mismo nodo.
- API independiente para consultar el pronóstico y calcular rutas con versiones
  fijadas. Respuesta con geometría, tiempos, restricciones, cobertura y causas de rechazo.
- Restricciones de Nueva York conservadas. Los 16.178 tramos originales y los 152
  conectores sujetos a controles pendientes siguen bloqueados: **16.330 en total**.

Contrato: [phase4-api.md](phase4-api.md). Reproducción y configuración:
[phase4-runbook.md](phase4-runbook.md). No se modificaron las propiedades de
despliegue ni se activó un servicio de producción.

## Datos reales conservados

ECMWF IFS, corrida **2026-09-22 00:00 UTC**, 29 instantes cada seis horas hasta
**2026-09-29 00:00 UTC**, siete variables y resolución original de 0,25 grados.
Se conservaron 174.927.055 bytes de índices y mensajes GRIB; el artefacto decodificado
contiene 843.050.880 bytes de valores float32, mapeados fuera del heap de Java.

Variables: altura significativa, dirección media y período medio de ola; viento
este/norte a 10 m; corriente superficial este/norte. El período está disponible,
pero no participa en la ecuación simplificada de velocidad. Los valores faltantes
del bitmap permanecen ausentes. Solo se normaliza el pequeño error de cuantización
GRIB alrededor de 0/360 grados y de magnitudes no negativas, dentro de `packingError`.

Origen y condiciones: [ECMWF Open Data](https://www.ecmwf.int/en/forecasts/datasets/open-data).
La dirección media en GRIB sigue la convención meteorológica **desde dónde viene**
la ola, según la [documentación oficial de dirección](https://confluence.ecmwf.int/spaces/FCST/pages/111155338/What%2Bis%2Bthe%2Bdirection%2Bconvention%2Bfor%2Bwave%2Bfields).
Son pronósticos del modelo, no observaciones. La descarga pública comprobada no
requiere cuenta de Copernicus Marine.

De las **1.114 referencias portuarias conectadas** del grafo, **729** tienen las siete
variables en todos los instantes muestreados; **385** no las tienen en su celda.
Esto es cobertura meteorológica del punto, **no** una certificación de ruta
portuaria completa. Una conexión también puede pasar por otra celda sin datos y ser
rechazada. No se reemplaza un dato faltante por cero ni se busca otra celda válida.

## Resultados de las ejecuciones registradas

Escenario declarado: 18 nudos en calma, coeficientes de ola proa/través/popa
0,08/0,04/0,02 nudos por m²; coeficiente de viento de proa 0,0005 nudos por (m/s)²;
límites de 8 m de ola y 35 m/s de viento. Son entradas de investigación, no medidas
de un buque. Salida 2026-09-22 00:00 UTC; malla temporal de cinco minutos.

Los extremos son nodos oceánicos reales del grafo, seleccionados por cercanía a
coordenadas declaradas antes de ejecutar los casos; **no se presentan como puertos**.

| Cuenca | Distancia | Navegación modelada | Espera modelada | Tiempo de cálculo | Estados expandidos |
| --- | ---: | ---: | ---: | ---: | ---: |
| Atlántico | 442,53 km | 13,223 h | 98,44 s | 2,26 s | 1.136 |
| Pacífico, cruza el antimeridiano | 464,37 km | 12,832 h | 305,25 s | 10,92 s | 6.560 |
| Índico | 579,53 km | 17,758 h | 271,29 s | 12,91 s | 6.724 |

Los tres casos devuelven HTTP 200 dentro de los límites registrados. Se verificó
la contabilidad de tiempos, restricciones de todos los tramos devueltos y geometría
sin líneas que atraviesen el mapa al cruzar el antimeridiano. La carga inicial del
grafo tomó 15,97 s y la del pronóstico 1,14 s. Heap usado al terminar las consultas:
1.395.986.752 bytes; máximo configurado: 2.415.919.104 bytes. El pronóstico mapeado
requiere memoria adicional fuera del heap. Es una ejecución local, no una prueba de carga.

**33 pruebas Java y 3 pruebas Python pasan**, con cero fallos y cero omisiones.
Incluyen 24 regresiones de fase 3, ocho pruebas del modelo/lector/búsqueda/API y una
integración real de tres cuencas; Python verifica originales GRIB, valores faltantes,
normalización de dirección y rechazo de parámetros, fechas o mensajes mezclados.
La API real también rechaza versiones incorrectas, salida fuera de horizonte,
uso de salida pasada como pronóstico actual y coeficientes omitidos.

Los 18 archivos ligados al protocolo de fase 3 conservan sus hashes. El artefacto
físico aprobado sigue siendo el mismo. No se reentrenó ni alteró el modelo AIS.

## Evidencia y límites

- Protocolo final: `phase4-protocol-v3-20260922.json`, SHA-256
  `ebf90c1ef1e7269340c4ceb840663e4b62aa28d8f94623f0c2bd62d8f1e6d8df`.
- Manifiesto del pronóstico: `weather/ifs-20260922-00z-168h-grid-v2/manifest.json`, SHA-256
  `fab2df3821b9ce075b61a669ecab2defb9a36287d6c2d7b97a524fa8acc649a8`.
- Cierre persistente: `data/maritime-routing/phase4-evidence-v1-20260922/status.json`.
  El mismo directorio contiene el informe de ejecución, censo completo, reportes
  de pruebas y vínculos a originales. El ZIP contiguo permite revisar esa evidencia;
  los grandes originales GRIB y las cuadrículas se conservan por separado.

Los protocolos v1/v2 y el primer directorio de construcción se conservan como
historial de desarrollo. La primera integración detectó un conteo esperado que
omitía los 152 conectores regionales; se corrigió la prueba, sin relajar controles.
El primer constructor rechazó una dirección apenas superior a 360 grados por la
cuantización GRIB; la normalización acotada está documentada y comprobada.

Esta fase queda cerrada en el alcance de investigación acordado: **no** establece
exactitud de ETA de un buque real, óptimo de navegación continua, seguridad de espera,
cobertura meteorológica de todos los puertos ni validación operativa mundial.
La espera y el redondeo temporal se exponen y requieren aceptación explícita.
Sin curva de consumo, combustible y emisiones siguen siendo `null`.

La siguiente fase del plan es el modelo contractual de los once Incoterms y la
separación de responsabilidades, costos y exposición del comprador y vendedor.
