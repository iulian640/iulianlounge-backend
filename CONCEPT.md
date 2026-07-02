# IulianLounge — Documento de Concepto

> Estado: concepto cerrado · 2026-07-02
> Siguiente fase: Fase 0 — arquitectura técnica (entidades JPA, endpoints, ADRs)

## Qué es

Un speakeasy virtual navegable en 3D. Entras siendo un don nadie y asciendes hasta
convertirte en socio del club: ganas fichas, mejoras el negocio, desbloqueas salas,
vistes cada vez mejor. No es una web con un canvas 3D decorativo — es un videojuego
en el navegador con un backend serio detrás.

**Doble propósito:** experiencia jugable memorable + demostración profesional del
stack Java/Spring/JPA con un frontend Three.js fuera de lo común.

## Dirección de arte

Speakeasy elegante y oscuro, años 20-30:

- Madera cuidada, cuero, verde botella, negro.
- Acentos de dorado/latón (barra, marcos, detalles) — lo que vende "caro".
- Luz cálida y baja (2700-3000K), fuentes puntuales: lámparas, luces de barra,
  la brasa de un puro. Contraste fuerte, sombras marcadas. Nada de luz plana.
- Jazz de fondo, whisky caro, humo, gente jugando a las cartas.
- Técnica: geometría contenida + PBR con roughness variado (madera mate, latón
  brillante, cristal) + bloom sutil. La atmósfera la hace la luz, no el polycount.
- **El letrero "Iulian's"**: nombre del club dentro de la ficción. Cartel
  luminoso dentro del lounge, estilo Fallout pre-bomba (retro-americana,
  neón/bombillas cálidas, tipografía de época) — pieza de arte destacada,
  candidata a emissive + bloom. La marca del proyecto sigue siendo IulianLounge.

## Cámara y control

- **Tercera persona**, WASD + ratón.
- **Escritorio primero.** Sin decisiones que bloqueen añadir controles táctiles
  en fase 2. En móvil, de momento, pantalla de "visítalo desde tu ordenador".

## El sistema de progresión (el corazón del diseño)

Narrativa, economía, espacio y cosméticos son **el mismo sistema** visto desde
cuatro ángulos:

### 1. Narrativa: tu ascenso
De don nadie a socio del club. La historia ES el gameplay: progresar en la
economía es progresar en la historia. El barman marca los hitos.

### 2. Economía: las fichas
Moneda única (fichas del club) compartida por todas las actividades:

| Fuente | Tipo |
|---|---|
| Katas de la terminal | Solo suma (pago por reto resuelto) |
| Incremental (el negocio) | Solo suma (goteo pasivo) |
| Blackjack y juego secundario | **Riesgo real: apuestas y puedes arruinarte** |

Todas las fuentes pagan a ritmo comparable — el jugador elige cómo ganarse la vida.

**Bancarrota:** si llegas a 0, el barman te fía. Préstamo con burla incluida
("otra vez, ¿eh?"). Convierte la frustración en momento de personaje.
→ Backend: entidad de deuda/préstamo, transacciones atómicas, anti doble-gasto.

### 3. Espacio: las salas
- **Sala principal**: barra, mesa de cartas, escenario de jazz.
- **Trastienda/despacho**: la terminal de retos. Desbloqueable.
- **Sala VIP**: desbloqueable con progreso/estatus.

Subir de estatus = literalmente acceder a más espacio.

### 4. Avatar: tu aspecto
- Personaje base único + **slots de equipamiento** (no editor de personaje).
- Arranque: 2-3 slots (sombrero, accesorio de mano — puro/copa/bastón; después
  chaqueta). Ampliable.
- Cada pieza en **variantes de material/color** (fieltro vs seda, latón vs oro):
  catálogo grande sin modelar más. Más caro = más lujoso visualmente.
- Empiezas con ropa humilde; acabas de seda y oro.

## Las piezas

### Terminal de retos (la trastienda)
Mini-freeCodeCamp propio: **katas de lógica en JavaScript**.

- El código del usuario se ejecuta **sandboxed en su navegador** (Web Worker),
  nunca en el servidor.
- El backend valida el resultado sin fiarse del cliente (verificación del
  resultado, anti-trampas razonable — tema de ADR) y persiste progreso e intentos.
- Cada reto resuelto paga fichas.

### Mesa de cartas
- **Blackjack** como juego principal, con apuestas reales de fichas.
- Backend modela "mesa de juego" genérica para añadir más juegos sin rehacer.
- **Un segundo juego simple** (dados o rasca-y-gana) para que el casino respire.
  - Referencia para el rasca-y-gana: **Scritchy Scratchy**.

### El barman (IA)
**Veterano socarrón que lo sabe todo.** Lleva décadas en el club.

- Humor seco: te vacila con tus derrotas al blackjack, reconoce tus logros
  en los retos, comenta tu ascenso.
- **Function calling contra el backend**: conoce tus datos reales (saldo, racha,
  progreso, deudas). Un NPC que sabe de verdad quién eres.
- Es la puerta de todo: contexto, retos ("¿te ves capaz con esa terminal?"),
  hitos narrativos, y el préstamo cuando te arruinas.
- LLM vía API externa **desde el backend Java** (la clave nunca toca el cliente).
  Fallback por reglas si hace falta.
- Historial de conversación persistido.

### El incremental (el negocio del bar)
**El lounge 3D ES el incremental.** Las mejoras aparecen físicamente:

- Compras mesas → aparecen mesas con clientes NPC.
- Contratas músicos → suben al escenario.
- Mejoras la bodega → barriles y botellas en la trastienda.
- El bar se llena y prospera visualmente con tu progreso.

Mecánicas (las tres, por capas según avance el proyecto):
1. Generadores automáticos (mesas/músicos producen fichas solos).
2. Mejoras de nivel por "instalación" (subir de nivel una mesa, un músico).
3. **Prestigio**: vender el bar y abrir uno más lujoso con bonus permanente
   (la escena 3D se transforma).

La gestión numérica densa vive en un panel 2D (el libro de cuentas); lo visual,
en la escena. Mitigación de coste de assets: las mejoras son **instancias** de
pocos modelos con variaciones, no assets únicos.

## Presencia social (asíncrona)

Sin multijugador en tiempo real (fase 2). En su lugar, **huellas de los demás**:

- Avatares de otros usuarios sentados en el lounge con su outfit y última actividad.
- Leaderboards con avatares.
- (Posible) notas dejadas en la barra.

Justifica los cosméticos (te ven) sin pagar el coste de sincronización en vivo.

## Idioma

**i18n desde el principio: castellano e inglés.** Todo texto en diccionarios,
prompt del barman por idioma, retos en ambos idiomas.

## Stack

| Capa | Tecnología |
|---|---|
| 3D | Three.js vanilla (skills threejs-* instaladas) |
| UI 2D overlay | Vue (login, HUD, paneles, libro de cuentas, chat) |
| Build | Vite |
| Backend | Java + Spring Boot + Spring Data JPA + Spring Security (JWT) |
| BD | PostgreSQL, migraciones versionadas (Flyway) |
| IA barman | LLM vía API externa, orquestado por el backend |

## Estándar de calidad (proyecto serio, no ejercicio)

- TDD, cobertura 80%+ en backend.
- ADRs para decisiones grandes.
- Seguridad: JWT, rate limiting, validación exhaustiva, secrets en entorno,
  el cliente nunca es de fiar (apuestas y resultados de katas se verifican).
- CI/CD (GitHub Actions): tests + build + lint en cada push.
- OpenAPI/Swagger, logging estructurado, errores explícitos en cada capa.
- Revisión con java-reviewer / security-reviewer al cerrar cada pieza.

## Plan general (3 meses, trabajo diario)

| Semanas | Bloque |
|---|---|
| 1-2 | Backend base: auth JWT, entidades núcleo, wallet/transacciones, probado end-to-end |
| 3-5 | Lounge 3D navegable en tercera persona + avatar base + login integrado |
| 6-7 | Terminal de retos (katas JS + validación + progreso) |
| 8-9 | Blackjack + juego secundario + leaderboard |
| 10-11 | Barman IA (function calling, préstamos, narrativa) |
| 12 | Incremental visual, tienda de ropa, presencia asíncrona, pulido, demo |

*(El reparto fino se decidirá en Fase 0; el incremental puede adelantarse por capas.)*

Extras para días sueltos: streaming del chat del barman (WebSocket), sonido
posicional, más katas, más variantes de ropa, segunda sala.

**Fase 2 (post-3 meses):** multijugador en tiempo real, controles táctiles,
póker.

## Riesgos señalados

1. **Que el 3D se coma el tiempo del backend.** Presupuesto duro de tiempo para
   arte; el vertical slice del backend va primero.
2. **Coste de assets del incremental visual + salas.** Instanciado y variantes,
   no assets únicos.
3. **Confianza en el cliente** (katas y apuestas). El backend verifica todo;
   diseño anti-trampas en ADR.
4. **Bugs en la lógica del blackjack con dinero real del juego.**
   Transacciones atómicas y tests exhaustivos de la lógica de apuestas.

## Pendiente (no bloquea Fase 0)

- Extensión del dominio (iulianlounge.???).
- Nombre propio de la moneda/fichas.
- Elegir el juego secundario (dados vs rasca-y-gana — referencia: Scritchy Scratchy).
- Historia de fondo del club (quién lo fundó, por qué está escondido) — se
  desarrolla junto al prompt del barman.
