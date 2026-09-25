# ADR-08: JWT stateless con refresh token

## Estado

Aceptada — 2026-07-03. Enmendada el 2026-09-23 (ver Historial). Cookie del
refresh y logout implementados el 2026-09-25.

## Contexto

HTTP es stateless: el servidor no recuerda nada entre peticiones, así que
tras el login necesita alguna forma de saber quién hace cada petición sin
pedir usuario y contraseña cada vez. La credencial que se entregue debe ser
imposible de falsificar: el cliente nunca es de fiar, y cualquiera puede
enviar peticiones directas al API.

Hay dos tensiones:

1. **Verificación**: o el servidor recuerda cada credencial emitida (sesiones
   en BD, una consulta por petición), o emite credenciales firmadas que puede
   verificar sin memoria.
2. **Caducidad**: una credencial de vida larga es cómoda pero, si la roban,
   el daño dura semanas; una de vida corta limita el robo pero obligaría al
   usuario a re-loguearse constantemente (inaceptable en mitad de una mano
   de blackjack).

Frontend y backend se despliegan en el mismo origen: Caddy sirve el frontend
en `iulianlounge.com` y reenvía `/api` al backend. Las cookies del propio
sitio funcionan sin los problemas del cross-site.

El frontend es una escena 3D con mucho JavaScript propio y de terceros, así
que un XSS no se puede descartar. Lo que el JavaScript de la página pueda
leer, un XSS también lo puede leer.

## Decisión

**JWT firmados por el backend, sin estado en servidor, con dos tokens:**

- **Access token (15 min)**: viaja en la cabecera `Authorization` de cada
  petición autenticada. Lleva el id del usuario, su rol y el tipo de token.
  El frontend lo guarda solo en memoria.
- **Refresh token (7 días)**: viaja en una cookie
  `HttpOnly; Secure; SameSite=Strict; Path=/api/v1/auth`. El JavaScript no
  puede leerla, así que un XSS puede usar el access mientras la página siga
  abierta, pero no llevarse la sesión.

Flujo: el login devuelve el access en el cuerpo y pone la cookie. Cuando el
access caduca, el backend responde 401, el frontend llama a
`POST /auth/refresh` (el navegador adjunta la cookie), recibe un access nuevo
y reintenta. `POST /auth/logout` borra la cookie.

Tope de sesión: 7 días desde el login. Cada refresh emite un refresh nuevo
que hereda la caducidad del anterior, así que jugar a diario no alarga la
sesión: el día 8 hay que volver a entrar.

Detalles de implementación que endurecen la decisión (IUL-19, IUL-20 e
IUL-21):

- Firma HS256 fijada en el código. No depende de la longitud de la clave.
- Claims `iss` y `aud` propios: un token firmado con la misma clave por otro
  servicio o entorno no vale aquí.
- Claim `type`: un refresh no sirve como access ni al revés.
- Solo los roles de una lista cerrada se convierten en `ROLE_*`.
- Todos los tokens rechazados reciben el mismo mensaje, que no revela el
  motivo.

La clave de firma vive en la variable de entorno `JWT_SECRET` (32 bytes o
más), jamás en el repo, que es público. Si se filtrase permitiría fabricar
tokens de cualquier usuario con cualquier rol: rotación inmediata, que
invalida todos los tokens emitidos.

CSRF sigue desactivado. La cookie solo acompaña a peticiones del propio sitio
(`SameSite=Strict`) y solo a las rutas de `/api/v1/auth`. El resto de rutas
se autentican con la cabecera `Authorization`, que un formulario de otra web
no puede poner.

Dos límites aceptados. `SameSite` mira el sitio (`iulianlounge.com`), no el
origen: cualquier subdominio cuenta como propio y podría plantar su propia
cookie `refresh_token`, así que no habrá subdominios de terceros. Y
`/auth/logout` es público, así que otra web puede forzar un logout; molesta,
pero no roba nada.

No hay CORS: en producción todo va por el mismo origen y en desarrollo por el
proxy de Vite. Si algún día hiciera falta, nunca con `allowCredentials`: el
origen permitido podría llamar a `/auth/refresh` con la cookie y leer el
access.

## Alternativas consideradas

- **Refresh token en el cuerpo JSON, guardado por el frontend.** Era la
  versión inicial de este ADR. Descartada en la enmienda: cualquier XSS lee
  `localStorage` y se lleva una sesión de 7 días que no se puede revocar.
- **Sesiones de servidor (cookie + estado en BD).** Descartada: una consulta
  de sesión en cada petición y un estado compartido que el JWT evita.
- **Caducidad por inactividad (7 días desde el último uso).** Descartada:
  con uso diario la sesión no caduca nunca, tampoco la de un token robado.
- **Lista negra de tokens en BD (revocación inmediata).** Pospuesta:
  reintroduce el estado que el JWT elimina. Solo si aparece una necesidad
  real.
- **Un único token de vida larga.** Descartado: ventana de robo de semanas,
  sin mitigación posible al no existir revocación.

## Consecuencias

- (+) El servidor verifica identidad con una operación criptográfica, sin
  tocar la BD.
- (+) Un access robado sirve 15 minutos como mucho, y el refresh no es
  legible desde JavaScript.
- (+) Ninguna sesión dura más de 7 días.
- (−) **Sin revocación inmediata.** Dos casos concretos: si se borra una
  cuenta, su access sigue valiendo hasta 15 minutos en las rutas que no
  recargan el usuario (`/me` sí lo recarga y responde 401); y cambiar la
  contraseña no cierra las sesiones abiertas.
- (−) Hay que volver a entrar cada 7 días aunque se juegue a diario.
- (−) En desarrollo el frontend también tiene que ir por el mismo origen:
  proxy de Vite (`/api` → `localhost:8080`). Con eso deja de hacer falta el
  CORS.
- (−) El frontend implementa el refresh silencioso (interceptor de 401 +
  reintento) y manda las peticiones a `/auth` con credenciales.

## Historial

- 2026-07-03: decisión inicial. Refresh en el cuerpo JSON; frontend y backend
  en dominios distintos.
- 2026-09-23: frontend y backend en el mismo origen detrás de Caddy. El
  refresh pasa a una cookie `HttpOnly`, la sesión tiene un tope de 7 días
  desde el login y se documentan los detalles de endurecimiento que salieron
  de la revisión de seguridad.
