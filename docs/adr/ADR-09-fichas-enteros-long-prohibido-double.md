# ADR-09: Fichas como enteros `long` — prohibido `double` en la economía

## Estado

Aceptada — 2026-07-03

## Contexto

Toda la economía del juego (apuestas, recompensas, compras, ingresos del
incremental, préstamos) se expresa en una única moneda: los tokens
(Chikilicuatres/Shrutebucks en la ficción). Los números en coma flotante
(`double`, `float`) no pueden representar exactamente muchos valores
decimales: `0.1 + 0.2 != 0.3`. En un sistema donde cada ficha importa y el
ledger debe cuadrar al céntimo (ADR-04: `balance_after` auditable fila a
fila), un solo redondeo silencioso corrompe la contabilidad entera — y esa
familia de bugs es de las más difíciles de detectar porque el error es
minúsculo hasta que se acumula.

## Decisión

- **Los tokens son enteros**: `long` en Java, `BIGINT` en PostgreSQL. La
  ficha es indivisible: no existen "0,5 fichas".
- **`double` y `float` quedan prohibidos en todo el dominio económico**
  (entidades, servicios, cálculos de recompensas e ingresos).
- **Los multiplicadores (prestigio, intereses) se expresan en puntos
  básicos como enteros**: `prestigeMultiplierBps = 10500` significa x1,05;
  `interestBps = 0` significa que el barman fía sin usura. Aplicar un
  multiplicador es aritmética entera: `amount * bps / 10000`.

## Alternativas consideradas

- **`double`.** Descartado: la familia completa de bugs de redondeo, sumas
  que no cuadran con el ledger y comparaciones traicioneras.
- **`BigDecimal`.** Descartado como exceso: resuelve el problema decimal
  pero con coste de verbosidad, rendimiento y API incómoda — y no tenemos
  decimales: la ficha es indivisible por diseño. `BigDecimal` es la
  respuesta correcta para euros con céntimos; aquí no hay céntimos.
- **Fichas con decimales (permitir 0,5).** Descartado por diseño de juego:
  los números enteros son más legibles, más "de casino", y evitan el
  problema de raíz.

## Consecuencias

- (+) La suma del ledger cuadra siempre, exactamente. La auditoría de
  ADR-04 es aritmética entera sin sorpresas.
- (+) Constraints de BD (`CHECK balance >= 0` sobre BIGINT) simples y
  fiables.
- (+) Los tests económicos comparan con `==` sin tolerancias ni deltas.
- (−) Las curvas de coste/producción del incremental deben diseñarse en
  enteros (crecimientos en bps); pequeña gimnasia mental al balancear.
- (−) Si algún día hiciera falta subdividir la ficha, sería una migración
  (multiplicar todo por 100); improbable y asumido.
