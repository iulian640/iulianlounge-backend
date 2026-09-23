# ADR-06: i18n — claves en backend, textos en frontend (vue-i18n)

## Estado

Aceptada — 2026-07-03. Enmendada el 2026-09-23 (ver Historial). El campo
`code` de los errores y el enum `ErrorCode` están pendientes de implementar,
antes de IUL-29.

## Contexto

El juego es bilingüe (ES/EN) desde el día uno, y su personalidad vive en
los textos: la moneda se llama **Chikilicuatres** en castellano y
**Shrutebucks** en inglés, los rangos son "Pejilgero"/"Riffraff", las salas
"La Eterna"/"The Long Game", y hasta los errores hablan con voz de club
(ver `docs/ficcion.md`). Alguien tiene que ser el dueño de esos textos, y
hay dos candidatos: el backend (que sirve los datos) o el frontend (que
los muestra). Si el backend enviara textos localizados, cada nombre nuevo o
corrección de una frase exigiría desplegar el backend, duplicaría catálogos
y complicaría la caché.

## Decisión

- **El backend devuelve claves y códigos, nunca texto de usuario**:
  `item.jacket.name`, `error.wallet.insufficient`, `room.vip.name`.
- **El frontend resuelve las claves con vue-i18n** y sus diccionarios ES/EN.
  Todos los textos con personalidad (moneda, rangos, salas, frases del
  catálogo del barman, textos de UI) viven en los diccionarios.
- **`User.locale` se guarda en el backend** pero solo se usa para lo que el
  backend genera por sí mismo: el idioma del system prompt del barman
  (el barman responde en el idioma del usuario) y futuros emails.
- Consecuencia práctica ya comprobada: renombrar la moneda o una sala es
  **una línea en un diccionario**, no una migración ni un despliegue del
  backend.

### Errores

Cada error es un `ProblemDetail` (RFC 7807) con un campo `code`, que es lo
único que lee el frontend:

```json
{ "status": 401, "code": "auth.invalid_credentials", "detail": "Invalid credentials" }
```

- `code` es una clave estable que el frontend traduce con vue-i18n; ahí el
  club puede hablar con su voz.
- `detail` va en inglés y es solo para quien depura. El frontend no lo
  muestra nunca.
- Los errores de validación llevan un mapa `errors` de campo a clave:
  `{"email": "validation.email", "password": "validation.size"}`. Nada de
  mensajes del validador, que cambian según el `Accept-Language`.
- Todos los códigos viven en un único enum `ErrorCode` del backend. Es la
  lista completa que el frontend tiene que cubrir en sus diccionarios.

### Excepción: texto generado por el LLM

Las respuestas del barman que genera el LLM son texto, y no pueden ser una
clave. Es la única excepción a la regla, y está acotada:

- Si la respuesta sale del **catálogo** de frases (fallback por reglas), el
  backend manda una clave y el frontend busca la frase en su diccionario.
- Si sale del **LLM**, el backend manda el texto tal cual, generado ya en el
  idioma del usuario gracias a `User.locale` en el system prompt.

El campo `source` (`"llm"` o `"catalogo"`) de la respuesta le dice al
frontend qué hacer con ella.

## Alternativas consideradas

- **Textos localizados desde el backend.** Descartado: duplica catálogos
  (BD + frontend), acopla los despliegues de los dos repos, complica la
  caché de respuestas y convierte cada errata en un redeploy.
- **i18n "más adelante".** Descartado: retrofitear i18n con decenas de
  strings hardcodeadas en componentes es dolor asegurado. Los diccionarios
  se configuran en el esqueleto del frontend (ticket IUL-26), antes del
  primer texto.
- **Errores solo con `detail` en castellano.** Era lo que hacía el código
  hasta esta enmienda. Descartado: el frontend tendría que traducir
  comparando frases, y cada cambio de redacción rompería el frontend.
- **Pedir al LLM claves en vez de texto.** Descartado: convierte al barman en
  un selector de frases del catálogo y pierde justo lo que aporta el LLM.

## Consecuencias

- (+) Personalidad y traducciones se editan sin tocar el backend.
- (+) El backend queda libre de concernirse por presentación: códigos
  estables, testeables y cacheables.
- (+) Los textos de la ficción tienen una fuente única (`docs/ficcion.md` →
  diccionarios).
- (+) Con `ErrorCode` no hay mensajes sueltos repetidos por las clases, y
  añadir un error nuevo obliga a darle un código.
- (−) Disciplina necesaria: la tentación de devolver un mensaje "rápido" en
  texto desde Java aparecerá. La única excepción es el texto del LLM.
- (−) Claves huérfanas (el backend emite una clave que el frontend no
  tiene) fallan en silencio mostrando la clave cruda; mitigación: test en el
  frontend que recorra la lista de `ErrorCode` y compruebe que los dos
  diccionarios tienen todas las claves.
- (−) El texto del LLM no pasa por los diccionarios: si el barman dice algo
  fuera de tono, se corrige en el system prompt, no con una línea de
  diccionario.

## Historial

- 2026-07-03: decisión inicial.
- 2026-09-23: la revisión del backend encontró los errores devueltos como
  frases en castellano. Se concreta el formato (`code` + `detail` en inglés +
  `errors` por campo), se centralizan los códigos en `ErrorCode` y se
  documenta la excepción del texto generado por el LLM.
