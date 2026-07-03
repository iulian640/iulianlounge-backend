# ADR-08: JWT stateless con refresh token

## Estado

Aceptada — 2026-07-03

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

Además, frontend y backend viven en repos y dominios distintos, lo que
complica las cookies de sesión cross-site.

## Decisión

**JWT firmados por el backend, sin estado en servidor, con dos tokens:**

- **Access token (15 min)**: viaja en la cabecera `Authorization` de cada
  petición autenticada. Contiene identidad y rol, firmado con la clave
  secreta del servidor: alterar su contenido invalida la firma.
- **Refresh token (7 días)**: solo sirve para pedir un access token nuevo
  en `POST /auth/refresh`. Mínima exposición: solo viaja a ese endpoint.

Flujo: cuando el access caduca, el backend responde 401, el frontend llama
a `/auth/refresh` en silencio, obtiene un access nuevo y reintenta. El
usuario solo re-loguea tras 7 días de inactividad.

La clave de firma vive en **variable de entorno**, jamás en el repo (que es
público). Si se filtrase, permitiría fabricar tokens de cualquier usuario
con cualquier rol: rotación inmediata (invalida todos los tokens emitidos).

## Alternativas consideradas

- **Sesiones de servidor (cookie + estado en BD).** Descartada: consulta de
  sesión en cada petición, requiere estado compartido, protección CSRF, y
  pelea con cookies cross-site al tener frontend y backend en dominios
  distintos.
- **Lista negra de tokens en BD (revocación inmediata).** Pospuesta:
  reintroduce el estado que el JWT elimina. Solo si aparece una necesidad
  real.
- **Un único token de vida larga.** Descartado: ventana de robo de semanas,
  sin mitigación posible al no existir revocación.

## Consecuencias

- (+) El servidor verifica identidad con una operación criptográfica, sin
  tocar la BD: escala y simplifica.
- (+) Ventana de daño ante robo de access token acotada a 15 minutos.
- (+) Encaja con la separación frontend/backend en dominios distintos.
- (−) **Sin revocación inmediata**: un access token robado es válido hasta
  su caducidad. Trade-off asumido y documentado; el TTL corto acota la
  ventana.
- (−) El frontend debe implementar el flujo de refresh silencioso
  (interceptor de 401 + reintento).
