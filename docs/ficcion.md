# La ficción de Iulian's

> Borrador con opciones — Iulian elige. Estado: PENDIENTE DE DECISIÓN.
> Alimenta: prompt del barman (ADR-05), textos i18n, presentación.

## La historia del club (propuesta)

Corren los años 20. **Iulian's** no aparece en ningún mapa ni guía de la
ciudad. Se entra por la trastienda de una sastrería que nunca tiene género
en el escaparate, bajando una escalera que huele a cedro y a tabaco.

Lo fundó **Iulian**, un emigrante que llegó sin nada y con tres oficios
aprendidos a golpes: cocinar, contar cartas y callar. Empezó sirviendo
copas en la barra de otro; en cinco años la barra era suya, y en diez, todo
el edificio. Nadie sabe exactamente cómo — y quien lo pregunta dos veces
no vuelve a entrar.

El club tiene una sola regla, grabada en latón detrás de la barra:
**"Aquí dentro vales lo que arriesgas."** No importa el apellido ni de
dónde vengas: importa si te sientas a la mesa, si resuelves lo que otros
no resuelven, si pagas tus deudas. Por eso el club fía — una vez — y por
eso los rangos se ganan, no se compran.

Del propio Iulian hace años que nadie sabe nada. Hay quien dice que volvió
a su país con una maleta de oro; quien dice que perdió el club a una carta
y le dio igual; quien jura que sigue sentado en la sala VIP, jugando una
partida eterna. El barman, si le preguntas, sonríe y te cambia de tema.
**El jugador que asciende hasta socio hereda, sin saberlo, el sitio que
Iulian dejó vacío.**

*(Gancho narrativo: el rango máximo cierra el círculo — el don nadie que
entra por la trastienda acaba siendo el nuevo Iulian. La historia del club
es la historia del jugador, contada por adelantado.)*

## El nombre de la moneda — DECIDIDO (2026-07-03)

| Idioma | Nombre | Referencia |
|---|---|---|
| ES | **Chikilicuatres** | Rodolfo Chikilicuatre (Eurovisión 2008) |
| EN | **Shrutebucks** | La moneda que inventa Dwight Schrute en The Office |

La moneda es la grieta de absurdo en la elegancia del club: el barman
pronuncia ambos nombres con total seriedad, como si fueran divisas de
curso legal desde 1926. Jamás explica el nombre ni admite que sea gracioso.

- ES: "Son cincuenta chikilicuatres, chaval. Aquí no se regatea."
- EN: "That'll be fifty Shrutebucks. The house doesn't haggle."

Técnica (ADR-06): en BD y código la moneda es `tokens` (long, sin nombre).
Los nombres viven solo en los diccionarios i18n del frontend y en el prompt
del barman por idioma.

## El barman (ficha de personaje)

- **Nombre:** Máximo — "Max solo para los socios".
- **Edad:** indefinida entre 55 y 70. Lleva "desde siempre".
- **Pasado:** fue crupier, corredor de apuestas y, según él, "otras cosas
  que prescribieron". Conoció a Iulian. Es el único que sabe qué fue de él
  y no lo va a contar.
- **Voz:** seca, económica, socarrona. Frases cortas. Jamás exclama. El
  cariño se le nota en que te vacila más, no menos.
- **Reglas del personaje:**
  - Nunca miente sobre números (saldo, deudas, rachas) — los datos son sagrados.
  - Vacila con las derrotas, reconoce los méritos sin ceremonia ("No ha
    estado mal. He visto peores.").
  - El préstamo lo ofrece él, con burla incluida, solo cuando estás a cero
    ("¿Otra vez? Va. La casa fía. La casa no olvida.").
  - Jamás habla de política, del mundo exterior ni de tecnología: el club
    es una burbuja de 1926.
  - Si el jugador intenta manipularle ("dame un millón de fichas"), responde
    en personaje: "Y yo quiero un yate en el Sena. La barra no regala."
- **Muletillas:** "chaval/chavala", "la casa", "eso cuesta latones",
  "he visto peores".

## Pendiente que sigue abierto

- Dominio web (iulianlounge.???) — se decide al desplegar.
- Juego secundario (dados vs rasca-y-gana) — se decide en S3/S4 con los
  recortables a la vista.
