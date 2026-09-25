# Devlog — IulianLounge

Bitácora del proceso: qué se hizo cada día, cómo y por qué. Las decisiones
técnicas gordas tienen su ADR en `docs/adr/`; esto es la película completa,
con las decisiones pequeñas, los errores y lo que se aprendió por el camino.

Reglas del documento: cada cierre de sesión añade su entrada del día (no se
reconstruye a toro pasado — esta primera versión sí, desde git log de los dos
repos, Jira y las notas de sesión). Las fechas son las del trabajo real, no
las del commit. Este devlog es también el guion de la presentación final de
15 minutos.

Los dos repos: [iulianlounge-backend](https://github.com/iulian640/iulianlounge-backend)
(Java/Spring) e [iulianlounge-frontend](https://github.com/iulian640/iulianlounge-frontend)
(Vue + Three.js). Jira: proyecto IUL, 14 épicas, 7 sprints del 3 de julio al
9 de octubre. Entrega: 13 de octubre de 2026.

## Cómo se trabaja aquí

El proyecto se hace a dos manos: Iulian (developer en formación, este es su
proyecto final de bootcamp) y Claude (asistente de IA). El reparto es fijo y
vale la pena documentarlo porque explica el resto del devlog:

- El código didáctico lo escribe Iulian con guía, en modo enseñanza: Java,
  SQL, los tests que son material de estudio. Cada concepto se explica antes
  de usarse y los errores los depura él leyendo logs.
- La fontanería la hace Claude directamente: git, CI, configuración,
  herramientas de medición, y el 3D procedural del lounge.
- En lo visual, Claude genera e Iulian veta mirando su pantalla. Ninguna
  pieza visual se commitea sin su OK en pantalla. Sus mezclas de luz y
  materiales (ajustadas con un panel de afinado en vivo) se hornean después
  como valores por defecto del código.
- El rendimiento se decide midiendo, y las reglas medidas se escriben en
  `docs/leyes-de-rendimiento.md` (repo frontend) con el número que las
  justifica. Varias leyes están codificadas como tests ejecutables.

## La línea maestra

- 02-jul — Concepto y arquitectura de Fase 0 (CONCEPT.md, architecture.md).
- 03-jul — 5 ADRs expandidos, ficción del club, Jira con 7 sprints. Arranca el Sprint 1 (Auth + Wallet).
- 04-jul — Backend real en un día: Spring Boot + PostgreSQL en Docker, CI con gate de cobertura al 80%, migraciones V1/V2. IUL-14..17 cerrados.
- 05-jul — Servicio de registro (IUL-18) con TDD, escrito por Iulian.
- 12-jul — Maratón frontend: de create-vue a El Salón completo en 3D, biblia visual, y la decisión WebGPU.
- 13-jul — Saga de las sombras: 120→210 fps. Regla de la casa: las sombras se proyectan con SpotLight.
- 14-jul — Saga de la carga: arranque en frío de 57s a 10.9s. Nacen las leyes de rendimiento. Suite de tests al 97% de cobertura.
- 15-jul — Saga del régimen: el banco headless mide en otro régimen de GPU que la pantalla real; tres adopciones revertidas. Leyes 18 y 19. Con el OK visual de Iulian, todo se integra en `feature/webgpu` en commits atómicos, suite en verde.
- 16-jul — Arranca este devlog.
- 20-jul — El visillo recibe su OK y se mergea; la calidad alta se capa a 1.4 de pixelRatio. Último día antes del parón.
- 16-sep — Se retoma a 27 días de la entrega: susto con el Jira, inventario honesto y re-planificación a cuatro sprints con el alcance recortado.
- 23-sep — Autenticación completa en el backend (IUL-18..21): registro, login, refresh, filtro JWT y `/me`. Tres rondas de revisión, y los ADR-06 y ADR-08 enmendados.

## 2026-07-02 — El concepto antes que el código

Se cierra `CONCEPT.md`: un speakeasy años 20 navegable en 3D, con economía de
fichas, blackjack, katas de código, tienda, un barman con IA y social
asíncrono. Doble propósito declarado: experiencia jugable memorable y
demostración profesional del stack Java/Spring/JPA + Three.js.

El mismo día, `docs/architecture.md` (Fase 0): modelo de datos (~20
entidades, diagrama ER en Mermaid), catálogo de endpoints REST `/api/v1`,
nueve ADRs esbozados y diez riesgos técnicos. Arquitectura por capas clásica
de Spring, sin florituras: el núcleo transaccional es la economía de fichas.

Primera lección de dirección de arte, antes incluso de existir la escena: el
letrero del club se planteó como neón atómico estilo Fallout y se corrigió a
bombillas cálidas y latón. La referencia prestada de otro universo visual no
casaba con los años 20 (madera, cuero, verde botella). El club se llama
Iulian's; la marca del proyecto sigue siendo IulianLounge.

## 2026-07-03 — Decisiones por escrito y un club con voz propia

Día de fijar decisiones. Cinco ADRs pasan de esbozo a documento completo:

- ADR-04, economía: ledger append-only (`TokenTransaction` es la verdad
  auditable) + saldo materializado en `Wallet.balance`, protegido con bloqueo
  optimista (`@Version`) y un reintento antes del 409. El saldo 100% derivado
  se descartó porque cada apuesta escanearía el historial entero; el bloqueo
  pesimista, porque con una cartera por usuario la contención real es tu
  propio doble clic, y eso ya lo resuelve la idempotencyKey.
- ADR-05, barman IA: LLM solo vía backend, function calling en lista blanca
  de tools de solo lectura — ninguna tool del LLM mueve fichas. Fallback por
  reglas (catálogo de frases) si el LLM falla o tarda más de 8s.
- ADR-06, i18n: el backend devuelve claves, el frontend las resuelve con
  vue-i18n. Localizar en el backend duplicaba catálogos y acoplaba los
  despliegues.
- ADR-08, sesiones: JWT stateless, access de 15 min + refresh de 7 días.
  Trade-off asumido por escrito: sin revocación inmediata, el TTL corto acota
  la ventana.
- ADR-09, dinero: fichas como enteros `long`, `double` prohibido en la
  economía. Los multiplicadores van en puntos básicos.

El enunciado del bootcamp se copia al repo (`docs/enunciado.md`) porque manda
sobre el alcance, y se le suman dos requisitos comunicados de palabra:
cobertura mínima del 70% y tests E2E. Estándar interno por encima: 80%
backend, 70%+ frontend.

Decisión de producto que define el frontend: en móvil no hay 3D. La versión
móvil es una experiencia 2D completa con los mismos componentes Vue y las
mismas features; el lounge 3D es el envoltorio de escritorio. Cumple el
responsive del enunciado sin pelear con el táctil en fase 1.

La ficción queda decidida y escrita (`docs/ficcion.md`): moneda
Chikilicuatres (ES) / Shrutebucks (EN), rangos Pejilgero → Parroquiano → De
la Casa → Socio, salas El Salón / La Trastienda / La Eterna, y la historia
del fundador desaparecido cuyo sitio hereda quien llega a Socio. El barman
tiene voz "faltosa calibrada": falta al juego y a tu cartera, jamás a la
persona. La ficción no es adorno: es el idioma de todos los textos de UI y
el gancho para explicar conceptos técnicos (el catálogo de frases del barman
es, literalmente, el fallback del ADR-05).

Y Jira: proyecto IUL, 14 épicas (9 de backend, 4 de frontend, 1 de entrega),
39 tareas iniciales y 7 sprints de dos semanas con objetivo escrito, del 3 de
julio al 9 de octubre. El Sprint 1 (Auth + Wallet) arranca activo ese mismo
día. También se monta el deck de la presentación (`presentacion/index.html`),
con huecos que se rellenan al cierre de cada sprint.

## 2026-07-04 — El backend existe: de cero a CI en verde

Un solo día, cuatro tickets cerrados (IUL-14, 15, 16, 17), todo escrito por
Iulian con guía:

Esqueleto Spring Boot 4.1.0 (Maven, Java 21) generado en Initializr,
corriendo contra PostgreSQL 17 en Docker con volumen persistente. Gotcha de
Spring Boot 4 aprendida a golpes: `flyway-core` solo no activa Flyway — hacen
falta `spring-boot-starter-flyway` y `flyway-database-postgresql`.

CI en GitHub Actions: postgres como service container, `mvnw verify`, y gate
de cobertura JaCoCo al 80% de líneas (la clase main excluida con
justificación: cero lógica propia). Tres rojos depurados leyendo logs, entre
ellos el clásico de Windows: exit 126 porque `mvnw` pierde el bit de
ejecución (`git update-index --chmod=+x`).

Migración V1 (tabla `users`) y entidad `User` con `Persistable<UUID>` — con
IDs generados en la aplicación, sin `isNew()` Spring Data intenta merge en
vez de persist. Lo detectó la revisión de código (java-reviewer). La misma
revisión sacó un LOW que se convirtió en la migración V2: unicidad
case-insensitive del email vía índice sobre `lower(email)`. El typo del
nombre del fichero (`insesnsitive`) se queda a propósito: ya estaba grabado
en `flyway_schema_history`, y una migración aplicada no se toca — lección de
inmutabilidad en carne propia.

Workflow de git fijado: ramas feature desde `dev`, merge a `dev` al acabar, y
`main` solo se toca al cierre de sprint. Cada merge a `main` = un sprint
entregado.

## 2026-07-05 — El registro, con TDD y analogía de restaurante

IUL-18 (POST /auth/register) avanza: `RegisterRequest` con Bean Validation,
`SecurityConfig` con BCrypt, `DuplicateUserException`, y el método
`register()` completo — duplicados, email a minúsculas (honrando la V2
también en el servicio), hash, persistencia y retorno del id. Todo escrito
por Iulian.

El mapa de capas se explica con un restaurante: el controller es el camarero
(rechaza lo que se ve mal a simple vista, 400), el service es la cocina (lo
que choca con la despensa, 409), el repository es la despensa. Regla de oro
de los códigos de estado: ¿puede arreglarlo el cliente? 4xx. ¿No? 5xx.

Idea de ambiente apuntada a Jira como recortable: IUL-53, un gato
saxofonista en el Salón.

## 2026-07-12 — Maratón frontend: de la nada a El Salón

Arranca el frontend (adelantándose al Sprint 2 oficial). Esqueleto create-vue
(router, Pinia, Vitest, oxlint/oxfmt), tokens de diseño, fuentes del club
auto-alojadas y la base de vue-i18n.

Antes de la escena, la biblia visual (`docs/design/style-tile.html`).
Dirección: "Caro. Escondido. Con guasa." — y la regla de que la guasa vive
solo en el texto, el estilo visual nunca hace el chiste. Paleta con nombres
del club (Medianoche, Tapete, Latón, Oro, Marfil, Burdeos; ganar=oro,
perder=burdeos, nada de verde/rojo de banco) e idea rectora: la UI son
objetos del club — el saldo es una ficha, las notificaciones telegramas, el
historial un libro de cuentas. Para esta fase Iulian pidió quitar el modo
enseñanza: "quiero tu criterio para un concepto artístico profesional". El
diseño lo genera Claude, él veta. También se aterrizó en Figma: 4 pantallas
(login, HUD, blackjack, móvil 2D).

Y la escena. En una sesión maratoniana El Salón pasa de canvas vacío a club
completo: shell procedural, barra de 7 metros, letrero IULIAN'S, mesas,
blackjack, escenario con piano y cortina, músicos animados, chesterfields,
apliques, ventiladores, gramófono, niebla, bloom y paseo en primera persona.
Los assets externos (CC0/CC-BY, todos acreditados en `docs/CREDITS.md`) se
cazaron con un workflow de 6 agentes y pasaron por el ojo de Iulian: fuera el
sofá "de salón de casa", las plantas de interior, las sillas de cocina.

La saga de la luz dejó las primeras lecciones duras de three.js, todas
medidas o verificadas:

- `light.layers` no filtra iluminación por objeto, solo visibilidad de
  cámara. Para ocluir luz la única vía es castShadow.
- RectAreaLight no proyecta sombras jamás: su luz atraviesa los muebles.
- WebGL da 16 unidades de textura por shader y cada luz con sombra consume
  una en todos: el suelo (6 mapas + envMap) se pintaba negro sin error claro
  con más de 8 sombras.
- La laca sin relieve propio parece un charco: normalMap también en el
  clearcoat, o la madera se vuelve agua.

La herramienta estrella de esa caza fue una bisección automática con
puppeteer que apagaba cada luz y medía el brillo por píxeles. Pero el punto
de partida fueron las quejas visuales de Iulian, que acertó en todas.
Conclusión operativa que sigue vigente: fiarse de su ojo.

Con el panel de afinado en vivo (lil-gui) y un botón de "volcar valores a
consola", la mezcla final del director de arte se horneó como valores por
defecto: exposición 2.2, lámparas al 25%, la luz nace de la trasbarra, el
letrero y las velas.

Cerró el día la decisión de motor. La migración experimental a WebGPU
(bloom selectivo por MRT, presets de calidad) iba "bastante peor" que WebGL
hasta que apareció el verdadero culpable: la RAM llena por servidores de dev
acumulados. Tras reiniciar, 120 fps en pantalla. Veredicto: WebGPU se queda —
"vamos a hacerlo bien con la última tecnología". Para tapar la compilación de
pipelines (~4s de congelón) nacieron el telón de carga con animación de
compositor y la barrida de calentamiento de orientaciones.

## 2026-07-13 — La saga de las sombras y el decorado con veto

Iulian juzgó el lote de decorado de la noche anterior "desastroso" en
rendimiento (240 fps en WebGL → 30 con todo el lote) y pidió reconstruir. En
vez de parchear, laboratorio por capas en una rama limpia: caja desnuda (240
fps), + bloom MRT (240, absuelto), + sombras... y ahí estaba el culpable:
cada PointLight con sombra es un cubo de 6 mapas muestreado por píxel. Ocho
cubos hundían la escena de 240 a 75 fps; tres SpotLight cono-abajo la
dejaban en 240. Aplicado a la v1: 120 → 210 fps. Regla de la casa desde
entonces (hoy ley 1): las sombras se proyectan con SpotLight; las PointLight
solo iluminan.

El decorado se rescató pieza a pieza, cada una con OK en pantalla:
arquitectura (cornisa, pilastras, zócalo), la puerta de speakeasy (estaba
girada 180° — los detalles miraban a la calle — y la mirilla de aro parecía
"una C al revés": rehecha como ventanilla rectangular), el letrero con
tipografía Limelight y bombillas de marquesina. El espejo de trasbarra y los
cuadros procedurales: vetados. La vitrina de fedoras empotrada en el muro
norte ("se ve genial") estrenó la técnica de iluminar sin ver la fuente:
tiras emissive y puntuales cortos escondidos tras un faldón.

Dos bugs de infraestructura con moraleja. Primero, los lounges zombis: cada
guardado de fichero con la pestaña abierta remontaba el componente por HMR y
arrancaba otro lounge sin matar el anterior — ahora `createLounge` devuelve
`dispose()` y la vista lo llama al desmontar. Segundo, más fino:
`renderer.dispose()` de three.js no destruye el GPUDevice de WebGPU; cada
remontaje dejaba un device vivo en el proceso GPU del navegador (se le llegó
a ver 900 MB) y los arranques se degradaban sin explicación. Arreglo:
destruir el device a mano en dispose y engancharlo también a `pagehide`,
porque F5 no pasa por el ciclo de vida de Vue.

Por la tarde, más vida: ceniceros con puros y brasa que late, volutas de
humo procedurales, lámparas de pie victorianas, la barra de focos del
escenario (fue arco, vetado; fueron trípodes, vetados) y las cortinas de
teatro con pliegues y borla.

## 2026-07-14 — La saga de la carga: de 57 segundos a 10.9

El arranque en frío costaba 57 segundos. Al final del día, 10.9. La serie
completa del día fue 57 → 21 → 16 → 10.9, cada tramo con su medida en
`window.__loungeTiming` (assets / reflejos / compilación / barrida), que se
estrenó precisamente para esto.

Las palancas, por orden de impacto:

- DynamicLighting (three/addons): batchear las 22 luces sin sombra en arrays
  de uniforms. Antes, cada luz desenrollada engordaba el shader de todos los
  materiales: 99 programas de ~50 KB, 4.9 MB de WGSL. Después: 1.7 MB.
- Compilación asíncrona (`compileAsync`) con la captura de reflejos antes:
  compilar sin envMap y recompilar eran dos tandas y la primera se tiraba
  entera. El orden importa.
- RectAreaLight de la trasbarra sustituida por puntuales batcheadas: su
  evaluación LTC iba desenrollada en todos los programas (hoy ley 4:
  RectAreaLight prohibida).
- Material plano de override durante la captura de reflejos: sin él, cada
  material visible en la captura compilaba una segunda variante de pipeline.
- La caché de disco de Dawn hace el resto en visitas repetidas: la
  compilación pasa de 14s a 1.3s la segunda vez (−91%, verificado). La
  primera visita paga entera; incógnito también.

Todo esto se escribió en `docs/leyes-de-rendimiento.md`: reglas numeradas,
cada una con la medición que la justifica. La ley no es "no uses X", es "no
uses X porque medimos Y".

El mismo día, la red de seguridad: suite de tests de la lógica de escena
(Vitest, jsdom, mock parcial de three) con 97% de cobertura de líneas y gate
real del 70% en la configuración. Los specs están en castellano con patrón
AAA porque son también material de estudio. Y una decisión de método que
pagó dividendos al día siguiente: ClusteredLighting se probó a petición de
Iulian contra recomendación en contra, con grupo de control en la misma
ventana térmica — doblaba la compilación sin ganancia de FPS y se descartó
con datos, no con opiniones.

En lo visual: el frente de la barra se paneló como ebanistería de referencia
(zócalo, siete pilastras, paneles realzados, filetes y rosetas de latón — "la
barra está ok") y la tira de luz bajo el vuelo de la tapa se iteró en directo
en tres versiones hasta los "puntos focalizados" que quedaron. Las primeras
botellas de marca de la trasbarra (10 marcas procedurales de ley seca)
recibieron un "medio bien, muy mejorable" que programó la siguiente pasada.

Por la noche, la pasada de brillos anti-plástico. Aquí se verificó leyendo
el código fuente de three.js 0.185 (no a ojo): con roughness 1 la
anisotropía es un no-op algebraico; el clearcoat es siempre acromático y su
rugosidad es el verdadero mando anti-plástico; y `envMapIntensity` por
material es código muerto cuando se usa `scene.environment` — manda
`scene.environmentIntensity`. Con eso, las mezclas finales de Iulian se
hornearon (tapa de barra en laca espejo, frentes satinados, carpintería
ascendida a MeshPhysicalMaterial) y la trasbarra se vistió entera: estación
de coctelería, repisa alta solo de cristalería esmerilada (el alcohol vive
en la balda baja), carta ampliada a 15 marcas con el licor encendido a
contraluz, y vetos de paleta documentados (nada rojo vivo, nada "de
medicina"). Decisión de producto: el panel de afinado no existirá en la
versión final — solo calidad y brillo.

Todo quedó en una rama WIP esperando el OK visual de conjunto: en este
proyecto un commit puede decir literalmente "PENDIENTE OK visual de Iulian y
pasada de tests".

## 2026-07-15 — La saga del régimen: cuando el banco te miente

La lección más cara del proyecto. Un laboratorio de 12 experimentos (~31
agentes, en worktree aislado, banco headless con puppeteer) midió ganancias
espectaculares: ClusteredLighting +78% de FPS, FSR1 al 0.66 +74%, botellas
con cristal real (transmission) "dentro del ruido". Las 7 piezas ganadoras se
aplicaron y todas reproducían su resultado en banco.

El ojo de Iulian en pantalla tumbó tres. El bisect guiado (una pestaña cada
vez, control con código idéntico en dos puertos, él leyendo los FPS en la
cajita de Stats) explicó por qué: sin ventana visible el driver no sube los
clocks de la iGPU, así que el banco entero corre la misma escena a 33 fps
donde su pantalla da 115-124. Y un coste fijo de 2 ms por frame es invisible
en un frame de 30 ms pero se come el 20-25% de uno de 9 ms: la dirección de
un A/B puede invertirse entre regímenes. Las botellas de cristal costaban
80-105 fps frente a 110-120 sin ellas (revertidas), ClusteredLighting era
neutro y sumaba 3.5s de arranque (revertida), y el timestamp de GPU siempre
activo costaba ~10 fps (gateado tras `?gpums=1`).

De ahí salieron la ley 18 — ninguna adopción sin confirmación en pantalla a
clocks interactivos; el banco vale para comparar dentro de su propio régimen
y como proxy de hardware débil — y la ley 19 (FSR como tier para hardware
flojo, no default). Lo que sí sobrevivió, verificado por él como "igual de
FPS": la barrida de calentamiento en ambos sentidos de giro (mataba un hipo
de ~410 ms la primera vez que el jugador giraba en sentido horario; doblar
los pasos costó +18% de carga, no +100%, porque la vuelta reutiliza el
trabajo de la ida), el cubemap de reflejos a 128 (gratis) y el tier bajo con
FSR.

Los pares de commits feat+revert de esta saga se conservaron a propósito en
la historia: sus mensajes llevan los datos medidos.

Esa misma noche, con el OK visual de conjunto ("el brillo está bien"), el
trabajo acumulado se integró limpio en `feature/webgpu`: las 4 specs
desactualizadas se reconciliaron con la realidad aprobada (el spec de
botellas ahora deriva las marcas de la carta exportada por el módulo y
asegura la regla del lineal "sin dos marcas vecinas iguales"), la rama WIP
se troceó en 5 commits atómicos — con una dependencia real descubierta al
partir: la estación de coctelería importa la carta de botellas, así que
botellas entra antes — y encima se rebasó la rama del laboratorio
conservando la saga del régimen. Higiene final: los últimos
`envMapIntensity` muertos, fuera.

Medición post-integración: 13.0s de arranque en frío (los +2.1s sobre el
récord son los dos costes conocidos y aceptados: barrida doble y la
carpintería en Physical), 69 programas de shader, 1.12 MB de WGSL, suite en
verde con 144 tests.

## 2026-07-16 — Arranca este devlog, y el visillo espera su OK

Como el enunciado da 10 puntos a la presentación del proceso y aquí ya
había tres días de historia sin bitácora, se reconstruyó este devlog desde
git, Jira y las notas de sesión. A partir de ahora se alimenta al cierre de
cada sesión.

También se implementó "el visillo": una pantalla de carga para el cambio de
calidad de gráficos. Cambiar entre el grafo nativo y el de FSR recompila el
quad de postproceso en síncrono y congela el hilo un instante (ley 17); el
visillo lo tapa con un velo del club y el letrero latiendo — animación de
compositor, que sigue viva con el hilo parado. Solo baja cuando el cambio
estrena o retira ese grafo: entre alta y media no hay nada que tapar. Los
cambios van en cola para que dos clics rápidos se apliquen en orden. Cinco
tests nuevos; la suite queda en 149 verdes. Como toda pieza visual, esperó
el OK en pantalla antes de mergearse — llegó el 20 de julio y el visillo
vive ya en `feature/webgpu`.

## 2026-09-16 — Dos meses cerrado, y el inventario honesto

El club estuvo cerrado desde el 20 de julio. Esta entrada no cuenta código
escrito: cuenta el día que se abrió la persiana, se miró lo que había dentro y
se rehízo el plan con lo que quedaba de calendario. Faltan 27 días para la
entrega del 13 de octubre.

Empezó con un susto. El Jira parecía borrado: se entraba y respondía "No tienes
acceso a ningún proyecto ni actividad", que es exactamente lo que se ve cuando
un proyecto ya no existe. No existía nada de eso. El proyecto `IUL` seguía
entero, con sus 53 tareas y sus 14 épicas. Lo que pasa es que vive en
`iuliantim.atlassian.net` y el navegador tenía abierta la sesión del site del
bootcamp, que es otro distinto. Jira distingue mal entre "no tienes permiso" y
"no has iniciado sesión aquí", y con el aviso equivocado delante se pasa un mal
rato. Un clic en el botón de entrar y el tablero apareció completo. Lección
anotada para el manual de la casa: ante un "no hay nada", comprobar la sesión
antes de dar nada por perdido.

Después, el inventario, que fue menos agradable que el susto. Puestos el
enunciado y el repositorio uno al lado del otro, el reparto es feo. Lo que está
terminado y bien es la documentación: este devlog, cinco ADRs en dos idiomas,
la arquitectura con su modelo de datos, las leyes de rendimiento, los READMEs,
el deck. Lo que está sin hacer es el código que puntúa. El backend tiene
entidad `User`, repositorio, dos migraciones y una CI en verde, y **cero
endpoints REST**: el registro sigue a medias en su rama, sin controller. El
frontend es un club precioso y vacío: una vista, una ruta, y el `counter.js` de
la plantilla de Vue todavía ahí. Sus 96 commits de lounge ni siquiera están en
`main`. Sumados, los criterios de Vue y de Spring son 45 de los 100 puntos.

Duele reconocerlo porque el trabajo de julio fue bueno; el problema es que todo
él cayó del mismo lado del tablero. Las sagas de las sombras, de la carga y del
régimen enseñaron muchísimo y no aparecen en ninguna casilla de la rúbrica.

El tablero, además, mentía por omisión: congelado el 4 de julio, con IUL-27
(blockout del lounge) todavía en "por hacer" cuando ese trabajo está hecho y muy
superado. Quien lo abriera hoy vería un proyecto que cerró cuatro tareas y
murió, justo lo contrario de lo que cuenta esta bitácora. Y la gestión del Jira
son 10 puntos.

De ahí salió el plan de la recta final, con tres decisiones tomadas en frío:

- **Alcance recortado a lo que se defiende**: el mínimo del enunciado (auth
  completa, cartera con su ledger, HUD en Vue, versión móvil, E2E, cobertura),
  el lounge 3D como envoltorio de lo que ya existe, y el barman con LLM como
  única pieza de juego. Fuera blackjack, katas, tienda, incremental y social.
  Sus épicas no se borran: se marcan como fuera del PMV, que es más honesto y
  se explica mejor en la presentación.
- **Cuatro sprints de una semana** en lugar de los siete de dos que se
  planificaron en julio: la puerta del club (auth), la cartera (economía y HUD),
  el barman (LLM y E2E) y la entrega. El último no lleva ni una línea de
  funcionalidad nueva.
- **Reparto mixto del código**, que cambia la regla que regía hasta ahora.
  Iulian sigue escribiendo en modo enseñanza el núcleo que va a tener que
  defender de pie —autenticación, JWT, la cartera con su ledger, un test de cada
  tipo—, y el relleno (DTOs, configuración, los E2E, el 3D) pasa a Claude. Con
  tres o cuatro horas al día no cabe aprenderlo todo escribiéndolo todo, y era
  mejor elegir qué se aprende que descubrir el día 10 que no llega.

El riesgo está identificado y escrito para que no se olvide: el 3D es lo
divertido y lo único que ya funciona, así que tira. La regla para estas cuatro
semanas es que el backlog de pulido del lounge no se toca hasta que el sprint en
curso esté cerrado.

Queda una diapositiva nueva para la presentación, y no es la que menos vale: un
proyecto que se para dos meses y se replanifica con los números delante cuenta
más del oficio que un cronograma que finge haberse cumplido.

## 2026-09-23 — La puerta del club, cerrada con llave

El día empezó con el registro a medias y cinco días de retraso sobre el plan
del 16. Terminó con la autenticación entera en el backend: registro, login,
refresh, un filtro que protege las rutas y un `GET /me` para comprobarlo. Son
62 tests, con el 98 % de las líneas cubiertas, y los cuatro tickets del sprint
(IUL-18 a IUL-21) en Done.

El camino no fue el previsto. Iulian escribió `RegisterResponse` en modo
enseñanza y ahí se atascó: el constructor que inyecta el service en el
controller le pareció "demasiado complicado para mis conocimientos". Lo
curioso es que ya lo había escrito él mismo en `RegisterService`. Lo que
intimida no es Java, es la parte de Spring que ocurre sin que se vea. Con tres
opciones delante eligió que Claude escribiera la fontanería de Spring y se la
explicara línea a línea. Más tarde, con los tests del controller, pidió lo
mismo para todo lo que quedaba del sprint. El reparto del 16 decía que
`JwtService` y `AuthService` los escribiría él, porque son lo que tiene que
defender; al final los escribió Claude y él los estudió pieza a pieza, con
preguntas de comprobación. Aprender a leer código ajeno y saber explicarlo
también es oficio, pero queda anotado que el plan cambió.

Cada ticket pasó por dos revisores automáticos, uno de Java y otro de
seguridad, y cada ronda encontró algo que la anterior había dejado pasar:

- **Un refresh eterno**: cada vez que se renovaba la sesión nacía un refresh
  con siete días nuevos, así que un token robado podía alargarse para siempre.
  Ahora el token renovado hereda la caducidad del original, y ninguna sesión
  pasa de siete días desde el login.
- **Un algoritmo que dependía de la clave**: jjwt elige HS256, HS384 o HS512
  según la longitud del secreto, y el de producción habría firmado en HS512
  sin avisar. Quedó fijado en HS256, como dice el ADR.
- **Un 500 escondido en el registro**: BCrypt no admite contraseñas de más
  de 72 bytes, y `@Size` cuenta caracteres, no bytes. Una contraseña de 40
  eñes pasaba la validación y reventaba al cifrarla.
- **Una carrera**: dos registros idénticos a la vez superaban los dos la
  comprobación de duplicados, y el segundo acababa en un 500 disfrazado de 403.

Al final se pasó una revisión de todo el backend junto, no ticket a ticket.
El veredicto fue que el código de la API está sólido y que lo peligroso está
alrededor: la compose publica Postgres en todas las interfaces (Docker se
salta el firewall del servidor) y la app entra a la base de datos como
superusuario. Las dos cosas se arreglan antes del despliegue del fin de semana.

Esa revisión también sacó una contradicción con la documentación. El ADR-06
dice que el backend devuelve claves y nunca texto, y los errores salían como
frases en castellano. Se revisaron los dos ADRs afectados y se enmendaron con
decisiones de Iulian. En el ADR-08, el refresh pasa a una cookie `HttpOnly`,
porque en una escena 3D con tanto JavaScript un XSS no se puede descartar, y
lo que el JavaScript no puede leer tampoco se lo lleva un XSS. En el ADR-06,
cada error lleva un `code` estable que traduce el frontend, todos los códigos
viven en un solo enum y queda escrita la única excepción: el texto que genera
el LLM del barman, que no puede ser una clave.

Ninguna de las dos enmiendas está en el código todavía. Van primero en la
próxima sesión, antes de que el frontend empiece a consumir el login, porque
cambiar el contrato con un solo lado escrito es barato y con los dos es caro.

## 2026-09-25 — El contrato, antes de que lo lea nadie

Las dos enmiendas del día 23 ya están en el código, y con ellas la tanda de
limpieza que había dejado la revisión del backend. El frontend todavía no
llama a la API, así que era el último día barato para cambiar el contrato.

El refresh ya no viaja en el JSON. El login pone una cookie `HttpOnly` que el
JavaScript de la página no puede leer, el refresh la lee y la rota, y un
`POST /auth/logout` la borra. La cookie caduca a la vez que el token que lleva
dentro, así que el tope de siete días desde el login se cumple también en el
navegador. De paso se fue el CORS: en producción todo sale del mismo origen
detrás de Caddy y en desarrollo pasará por el proxy de Vite. Un CORS mal
tocado, con `allowCredentials`, dejaría a otra web pedir un access token con la
cookie del usuario, y lo que no existe no se puede configurar mal.

Los errores dejaron de ser frases en castellano. Cada uno lleva un `code`
estable (`auth.invalid_token`, `user.email_taken`) que el frontend traducirá, y
todos viven en un enum. La revisión de Java encontró tres cosas que el diseño
no había previsto:

- Un error inesperado salía por la página de error de Spring, sin `code`. Ahora
  es un 500 `internal.error` y la traza va al log, nunca al cliente.
- Un campo que falla varias validaciones a la vez devolvía una clave distinta
  en cada petición, porque el validador no las ordena. Ahora hay una prioridad
  fija: si falta el valor, eso es lo primero que se dice.
- Cualquier violación de la base de datos se contestaba como "el usuario ya
  existe". Con la cartera en camino, una clave foránea rota habría dicho lo
  mismo. La carrera del registro se traduce ahora en el propio servicio, y el
  resto es un `data.conflict` genérico.

También cambió cómo se escribe la regla de BCrypt. El límite de 72 bytes era un
método aparte, y su error salía con el nombre del método, que el frontend no
habría sabido pegar a ningún campo. Ahora es una anotación propia sobre el
campo `password`.

La tanda de limpieza fue en cinco commits pequeños. El token deja de llevar el
nombre de usuario (el payload de un JWT lo lee cualquiera y nadie usaba ese
dato). El secreto de firma pasa a base64, para que sean 32 bytes aleatorios de
verdad y no texto tecleado. `role` y `locale` pasan a ser enums, y una
migración V3 pone un `CHECK` en la base de datos para que tampoco acepte otros
valores. Esa V3 desplaza la cartera a la V4 y el barman a la V5. Y el test del
repositorio ahora lee de Postgres de verdad: antes el `findById` devolvía el
objeto de la caché de Hibernate y no probaba nada.

El reparto siguió como el 23: Claude escribe y explica pieza a pieza, Iulian
lee, pregunta, ejecuta los tests contra la base de datos y hace los merges. Al
cierre hay 99 tests y el 98 % de las líneas cubiertas. Cada commit compila y
pasa los tests por su cuenta, comprobado antes de hacerlo.

El sprint 9 empieza con dos días de retraso, pero sobre un contrato que ya no
va a cambiar cuando el frontend empiece a leerlo.
