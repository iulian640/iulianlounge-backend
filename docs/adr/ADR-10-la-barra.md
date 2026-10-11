# ADR-10: La Barra, carta fija, rango derivado del gasto y fiado diario

## Estado

Aceptada, 2026-10-07. Implementada en el sprint 10. Nota del 2026-10-11: la
moneda del producto pasó a llamarse «monedas» (en inglés, «coins»). El texto
de este ADR usa ya ese nombre; las claves y los tipos del ledger no cambian.

## Contexto

Hasta el sprint 9 el producto dejaba registrarse, entrar y ver una cartera con
100 monedas de bienvenida, pero no se podían gastar ni ganar. El rango del
socio existía (`Rank`) y no cambiaba nunca: `UserService` devolvía siempre
`NADIE`. Faltaba una funcionalidad central que diera sentido a las monedas, a
los rangos y al barman de `docs/ficcion.md`.

Quedaban seis días para la entrega. El barman conversacional con LLM (ADR-05)
costaba entre 14 y 18 horas, depende de una clave de API y de la red, y no da
uso a las monedas por sí solo. Se aplazó como extra y acabó entrando después
(enmienda del 2026-10-07 en ADR-05, implementada en IUL-44): este ADR define la
barra sin depender de él.

## Decisión

La funcionalidad central es **La Barra**: se piden copas de una carta fija, se
pagan con la cartera y el barman responde según el rango, que sube con lo que
el socio ha gastado en la barra.

1. **Carta fija en código** (enum `Drink`), sin tabla: cinco copas de época con
   precio entero en monedas (ADR-09). `BATHTUB_GIN` 5, `BEES_KNEES` 10,
   `GIN_RICKEY` 15, `SIDECAR` 25, `FRENCH_75` 40.
2. **Pedir una copa es un débito** de `WalletService` con el tipo `BAR_ORDER`
   y la clave `order:<Idempotency-Key>`. La cabecera `Idempotency-Key` (UUID)
   es obligatoria: un doble clic o un reintento por red no cobra dos veces
   (ADR-04, regla 4). Sin saldo: 422 `wallet.insufficient_funds`.
3. **El rango se deriva del ledger**: `Rank.forSpent(suma de BAR_ORDER)`. No hay
   columna nueva: el ledger es la fuente de verdad (ADR-04) y la suma de un
   socio son pocas filas indexadas por cartera (índice de V6). Umbrales sobre el
   gasto total en la barra:

   | Valor del enum | Gasto mínimo | Nombre visible |
   |---|---|---|
   | `NADIE` | 0 | Recién llegado |
   | `HABITUAL` | 25 | Cliente |
   | `CONFIANZA` | 100 | Habitual |
   | `SOCIO` | 300 | Socio |

   El enum y el nombre visible no coinciden en `HABITUAL` y `CONFIANZA`: el
   nombre vive solo en i18n del frontend (ADR-06). El ascenso no se anuncia con
   un banner: se nota en la frase del barman y en el título del rango.
4. **La casa fía**: si el saldo no llega para la copa más barata
   (`Drink.cheapestPrice()`, hoy 5), el socio puede pedir 50 monedas (tipo
   `HOUSE_CREDIT`) **una vez por día del club** (zona `Europe/Madrid`), con la
   clave `house-credit:<día>`. Es un regalo sin deuda: la deuda y la frase de
   «deuda pagada» quedan para una fase posterior. Con saldo suficiente: 422
   `bar.credit_not_needed`. Ya usado hoy: 409 `bar.credit_used_today`. Si dos
   peticiones llegan a la vez, la clave UNIQUE del ledger impide pagar dos
   veces: la segunda recibe el movimiento original.
5. **El barman habla con claves, no con textos** (ADR-06): el backend devuelve
   `barman.<situación>[.<rango>]` y el frontend guarda los textos en ES y EN.
   Situaciones con rango: `greeting`, `serve`, `promotion` y `broke`. Sin rango:
   `no_credit`, `house_credit` y `busy`. El texto libre pasa por
   `POST /api/v1/bar/talk` y, sin clave de Anthropic o ante cualquier fallo del
   LLM, vuelve a una frase fija de ese catálogo (ADR-05).
6. `BarService` **no abre transacción**: le pasa el trabajo a `WalletService`
   (ADR-04, regla 6).
7. La migración **V5** amplía el CHECK de `token_transaction.type` con
   `BAR_ORDER` y `HOUSE_CREDIT`. V6 añade el índice `(wallet_id, type)
   INCLUDE (amount)` y el CHECK de signo de `BAR_ORDER`. V1 a V4 no se tocan.
8. **Límite por socio**: 30 `POST` por minuto bajo `/api/v1/bar/` (propiedad
   `bar.rate-limit.max-per-minute`), con 429 `bar.too_many_requests`. `talk`
   tiene su propio límite de 10 por minuto (ADR-05).

### API

| Endpoint | Respuesta 200 | Errores |
|---|---|---|
| `GET /api/v1/bar` | `{drinks, balance, rank, creditAvailable, line}` | 401, 404 |
| `POST /api/v1/bar/orders` con `Idempotency-Key` y `{drink}` | `{drink, price, balance, rank, promoted, creditAvailable, line}` | 400, 401, 409, 422, 429 |
| `POST /api/v1/bar/house-credit` | `{amount, balance, rank, line}` | 401, 409, 422, 429 |

## Alternativas consideradas

- **Barman con LLM como núcleo (ADR-05).** Se aplazó: entre 14 y 18 horas,
  coste y dependencia de red, y no da uso a las monedas. Después entró como
  añadido sobre esta barra, sin cambiar sus reglas: el LLM nunca mueve dinero.
- **Columna `total_spent` en `wallet`.** Descartada: duplicaría la verdad del
  ledger y obligaría a mantenerla en cada débito.
- **Tabla `drink` en la BD.** Descartada: la carta no cambia y una tabla añade
  migración, entidad y repositorio sin valor para el usuario.
- **Préstamo con deuda.** Aplazado: necesita su propio estado (deuda pendiente,
  devolución) y no cabía en el plazo.

## Consecuencias

- Las monedas sirven para algo y el rango cambia: el producto tiene un bucle
  que se demuestra en dos minutos (registro, French 75, Cliente).
- El API gana escritura propia del dominio (dos `POST`) con idempotencia
  defendible.
- Sin el LLM, la barra funciona sin dependencia externa ni coste.
- Calcular el rango suma filas del ledger en cada consulta. Con el índice de V6
  es barato hoy; si el ledger crece mucho habrá que medirlo.
- Los nombres de rango y de copas viven también en el backend (para el prompt
  del barman) y duplicados en el i18n del frontend. Los tests del backend los
  fijan contra este ADR.
