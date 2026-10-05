# Fase 3: integración mundial y controles de Nueva York

Alcance: `PHASE3_GLOBAL_RESEARCH_V2`.
Estado: **PASS**, cierre verificado el 2026-09-22. Informe ejecutable:
`data/maritime-routing/phase3-research-status-v2-20260922.json`.
Las 14 comprobaciones de cierre pasan para el alcance de investigación actualizado.

## Implementación

Se creó un artefacto compuesto con la malla mundial, el piloto NOAA V2 intacto y
una malla costera de transición. Los controles regionales se aplican al tramo de
cada segmento que intersecta la cobertura real de las dos celdas NOAA, también
cuando ambos extremos quedan fuera. La selección preliminar usa distancia
geodésica y desigualdad triangular, evitando omitir la curvatura de un segmento
largo. No se modifica el modelo AIS congelado ni se habilita un permiso pendiente.

La transición contiene centros H3 derivados a resolución 8, dentro de una franja
de 20 km de la cobertura fuente. Los enlaces tienen presupuestos explícitos:
24 candidatos y hasta tres conexiones por nodo, hasta 20 km hacia la malla global
y 1.500 m hacia la regional. Cada enlace se comprueba por completo con pasos de
25 m: agua NOAA dentro de la cobertura y GSHHG fuera. Los puntos no son posiciones
observadas ni accesos certificados a terminales.

En el borde artificial de una carta, la erosión de polígonos de agua recortados
por ese mismo borde dejaba una pared geométrica. La unión usa el agua positiva
original dentro de una banda de dos tolerancias; no la expande hacia zonas sin
agua documentada. Los obstáculos conservan su margen de exclusión. Una prueba
específica comprueba que un hueco real no pueda salvarse con esta regla.

El cargador admite el esquema 2, exige evidencia regional incluida en el ZIP,
verifica sus hashes y rechaza contratos incompatibles o incompletos. El catálogo
exige un informe de validación regional coincidente con el SHA del artefacto. Un
fallo no activa automáticamente la malla mundial antigua. La API devuelve por ruta
versiones, intervalos de aristas con cobertura regional/global y capas desconocidas.
Ver el [contrato API](api.md#regional-controls-and-route-coverage).

## Artefacto final de la construcción

`data/maritime-routing/global-regional-research-v4-20260921.zip`:

| Medida | Resultado |
| --- | ---: |
| Nodos totales | 102.604 |
| Aristas dirigidas | 2.356.006 |
| Referencias mundiales conectadas | 1.114 |
| Nodos de transición | 842 |
| Aristas de transición costera | 4.134 |
| Enlaces dirigidos entre malla mundial y transición | 66 |
| Enlaces dirigidos entre transición y piloto regional | 152 |
| Nodos regionales en el componente físico mundial principal | 2.925 de 2.951 |
| Aristas sujetas a controles regionales | 16.330 |
| Cambios de disponibilidad de referencias preexistentes | 0 |

SHA-256 del ZIP:
`1eed131c9bb2cb27596ac1aa451ffca555c4ca9ab448599297c8bd0226f7967c`.
Versión del grafo:
`e6ac2932cd9c941202dc840322411105e84b88b4ccf9f83182ea9a0e57e63437`.

Las 16.178 aristas originales del piloto conservan restricciones pendientes. Su
conectividad física no las vuelve utilizables por la política de rutas. Los 19
atraques NOAA siguen no disponibles. Los 26 nodos regionales fuera del componente
principal y todos los puertos descartados permanecen en el censo.

La malla mundial original no tenía aristas que intersectaran estas dos celdas:
por eso la integración no elimina aristas mundiales existentes. La aplicación de
controles en un cruce con ambos extremos fuera se comprueba con regresiones
geométricas específicas; la conexión efectiva del conjunto usa los nuevos enlaces.

## Verificación

- Python: **117 pruebas aprobadas**, incluidas siete nuevas regresiones de
  prioridad regional, unión de cartas y curvatura geodésica.
- Java: **25 pruebas aprobadas**, cero fallos, errores u omisiones; incluyen carga,
  API, búsqueda y la prueba mundial con el artefacto real. Maven terminó con código 0.
- Auditoría del artefacto final: **PASS**. Se comprobaron 1.178.003 pares recíprocos
  que cubren las 2.356.006 aristas dirigidas. La conectividad con controles conserva
  las 1.114 referencias en un mismo componente; cero cambios de disponibilidad.
- Nueve recorridos mundiales: **9/9 aprobados**, presencia geométrica en Pacífico,
  Atlántico e Índico, cuatro cruces del antimeridiano y cinco del ecuador. Todos
  terminan dentro del presupuesto registrado; máximo 94.872 expansiones de 2.000.000.
- Alternativas: tres rutas para GBABA/USAA2, separación solicitada de 50 km, sin
  agotar el presupuesto. HTTP comprueba trazabilidad, cobertura por ruta, tiempo
  ausente sin velocidad y rechazo de permiso/profundidad desconocidos cuando se exigen.
- Los cuatro experimentos AIS previos mantienen los hashes de sus implementaciones
  congeladas: 11, 17, 22 y 27 archivos. No se cambian sus conclusiones.

Medición en un único proceso: carga del grafo 14,65 s; consultas de una ruta entre
0,055 y 0,625 s; tres alternativas en 0,859 s. Heap usado tras las consultas:
1.452.315.744 bytes, con máximo configurado de 1.887.436.800 bytes. Son mediciones
de esta ejecución, no pruebas de carga ni un compromiso de latencia.

Evidencia de ejecución: `target/maritime-routing/combined-20260921/runtime-benchmark.json`;
auditoría: `data/maritime-routing/global-regional-research-v4-20260921.validation.json`.
El resumen vincula hashes de protocolo, implementación, entradas, auditoría,
ejecución y pruebas. Dos controles negativos del propio resumen rechazan la ausencia
de aprobación regional y un hash de protocolo incorrecto; sus informes permanecen
en `target/maritime-routing/combined-20260921/rejection-checks/`.

El [protocolo de ingeniería v3](phase3-research-protocol-v3-20260921.json), SHA-256
`5578c5dea96edf1953cb12dd97b7a8764511b929b01bc84147c70782e045c2f8`, registra los
mismos nueve pares por identificador antes de evaluar este artefacto. Son las ocho
regresiones mundiales anteriores más India/Sudáfrica. Se exigen Pacífico, Atlántico,
Índico, cruces efectivos de ecuador y antimeridiano, búsqueda acotada, metadatos y
rechazo de profundidad/permisos desconocidos cuando la consulta los exige. Estos
casos de ingeniería no son nuevas trayectorias AIS independientes.

La validación de composición conserva las comprobaciones GSHHG previas únicamente
para geometría mundial idéntica a la fuente fijada. Recorre todas las aristas del
conjunto, comprueba reciprocidad y revalida la geometría regional y los enlaces
nuevos. Comparte implementación de política geométrica, acompañada por regresiones
específicas; no es una segunda fuente hidrográfica independiente.

## Versiones de desarrollo y límites

Se conservaron los artefactos intermedios: v1 no lograba conexión regional; v2 añadió
la transición pero seguía separada por la erosión del borde; v3 resolvió la unión
y pasó su auditoría geométrica, pero se sustituyó al descubrir el caso del filtro
de segmentos curvos. v4 incorpora esa corrección. Los protocolos v1 y v2 también
se conservan; no se cambiaron los nueve pares para obtener un resultado favorable.

El informe histórico `phase3-status-20260921.json` mantiene **NOT_PASSED** bajo el
alcance anterior. El cierre nuevo **PASS** se calcula por separado con
`summarize_research_phase3.py`. Esta aprobación solo representa planificación
mundial de investigación con los controles descritos: no valida permisos, calado,
oleaje, exactitud AIS mundial, acceso universal a puertos ni ahorros de combustible.

No se modificó la selección de artefacto de producción, la base de datos ni los
experimentos AIS. No se realizó despliegue, commit o push. Los comandos de
reconstrucción, validación y prueba están en el [runbook](runbook.md).
