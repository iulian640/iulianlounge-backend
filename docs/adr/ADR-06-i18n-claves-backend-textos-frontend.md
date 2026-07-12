# ADR-06: i18n — claves en backend, textos en frontend (vue-i18n)

## Estado

Aceptada — 2026-07-03

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

## Alternativas consideradas

- **Textos localizados desde el backend.** Descartado: duplica catálogos
  (BD + frontend), acopla los despliegues de los dos repos, complica la
  caché de respuestas y convierte cada errata en un redeploy.
- **i18n "más adelante".** Descartado: retrofitear i18n con decenas de
  strings hardcodeadas en componentes es dolor asegurado. Los diccionarios
  se configuran en el esqueleto del frontend (ticket IUL-26), antes del
  primer texto.

## Consecuencias

- (+) Personalidad y traducciones se editan sin tocar el backend.
- (+) El backend queda libre de concernirse por presentación: códigos
  estables, testeables y cacheables.
- (+) Los textos de la ficción tienen una fuente única (`docs/ficcion.md` →
  diccionarios).
- (−) Disciplina necesaria: la tentación de devolver un mensaje "rápido" en
  texto desde Java aparecerá; la regla no admite excepciones.
- (−) Claves huérfanas (el backend emite una clave que el frontend no
  tiene) fallan en silencio mostrando la clave cruda; mitigación: test de
  componentes que verifique diccionarios completos.
