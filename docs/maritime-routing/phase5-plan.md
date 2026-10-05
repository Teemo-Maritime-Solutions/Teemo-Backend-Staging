# Fase 5: modelo contractual de investigación

Alcance: once Incoterms® 2020, versión propia de reglas resumidas, lugar nombrado
y punto de entrega explícitos, contrato de compraventa separado del transporte,
pagador separado de riesgo de carga y de control efectivo de ruta. Se implementa
una API independiente del calculador antiguo de tarifas fijas.

La evaluación representa cumplimiento ordinario de las obligaciones, sin
incumplimientos, modificaciones de las reglas ni decisiones jurisdiccionales.
No determina propiedad, condiciones de pago, responsabilidad del transportista
ni indemnización de seguros. Las excepciones necesitan evaluación fuera de este
modelo. Los hitos son declaraciones contractuales, no posiciones verificadas.

## Criterios de cierre

1. Catálogo completo y versionado, fuentes ICC enlazadas y resúmenes propios.
2. Validación de modo, lugar, entrega, variantes FCA y descarga DPU/DAP/DDP.
3. Asignación de transporte, despacho, seguro y descarga; inclusión de descarga
   en el contrato de transporte explícita. Importes faltantes permanecen ausentes.
4. Costos decimales por actor, moneda única, cotizaciones o escenarios declarados,
   sin tarifas por distancia, impuestos o probabilidades inventados.
5. Exposición bruta por tramo y pérdidas condicionales por escenario; no sumar
   valores de carga repetidos en distintos tramos. Control de ruta explícito por
   tramo, con referencia independiente al contrato de transporte.
6. Pruebas de los once términos, casos limítrofes y negativos HTTP; evidencia
   reproducible y comprobación de hashes de las fases congeladas 3 y 4.

El modelo acepta escenarios del usuario porque no se han proporcionado contratos,
tarifas, pólizas ni curvas de deterioro reales. El motor no transforma esos
escenarios en hechos comerciales. La simulación probabilística y selección
Pareto pertenecen a la fase 6.
