# Despliegue en iulianlounge.com

Un VPS con Docker. Caddy pone el HTTPS, sirve el frontend y reenvía `/api` al
backend, así que el navegador solo habla con un origen y la cookie del refresh
funciona sin CORS (ADR-08). Postgres no se publica: solo lo ven los contenedores.

```
navegador ──https──> Caddy (web) ──/api──> backend ──> postgres
                        └─ resto: la SPA (dist del frontend)
```

## Dónde vive

Desde el 25-sep-2026, en una instancia ARM (aarch64) de Oracle Cloud en Madrid,
`ubuntu@82.70.85.197`, la misma que antes servía MeDeben (ya apagada; su copia
está en `~/backups/`). Todas las imágenes del compose tienen versión ARM.

## Una sola vez

1. **Servidor**: Ubuntu con Docker. En Oracle, los puertos 80 y 443 tienen que
   estar abiertos en la *security list* de la VCN y en el `iptables` de la
   máquina (en esta instancia ya lo estaban por MeDeben).
2. **Nada más en el 80/443**: si hay un Caddy o nginx instalado en el sistema,
   apágalo (`sudo systemctl disable --now caddy`); el de la compose se encarga.
3. **DNS** en Cloudflare: dos registros `A` a la IP del servidor, `@` y `www`,
   con la nube en **gris (DNS only)**. Con la nube naranja, Cloudflare pone su
   propio HTTPS delante y choca con el certificado de Caddy.
4. **Código y secretos**:
   ```bash
   git clone https://github.com/iulian640/iulianlounge-backend.git && cd iulianlounge-backend
   cp deploy/.env.example deploy/.env
   sed -i "s|^POSTGRES_ADMIN_PASSWORD=.*|POSTGRES_ADMIN_PASSWORD=$(openssl rand -hex 24)|; s|^DB_PASSWORD=.*|DB_PASSWORD=$(openssl rand -hex 24)|; s|^JWT_SECRET=.*|JWT_SECRET=$(openssl rand -base64 32)|" deploy/.env
   chmod 600 deploy/.env
   ```
5. **Arranque**:
   ```bash
   docker compose -f deploy/docker-compose.prod.yml --env-file deploy/.env up -d --build
   ```

## Comprobar

```bash
curl -s https://iulianlounge.com/actuator/health          # {"status":"UP"}
curl -sI https://iulianlounge.com | grep -i strict         # la cabecera HSTS
docker compose -f deploy/docker-compose.prod.yml logs -f backend
```

Y en el navegador: hacerse socio, entrar al lounge, recargar y seguir dentro.

Si Caddy no consigue el certificado justo después de crear el DNS, es la caché
negativa: los resolvedores de Let's Encrypt recuerdan el "no existe" de un
intento anterior hasta 30 minutos (el mínimo del SOA de la zona). No reinicies
en bucle, que Let's Encrypt limita los fallos por dominio: Caddy reintenta solo.

## Cada despliegue

Uno por sesión de trabajo, al final, no uno por arreglo.

```bash
cd iulianlounge-backend && git pull
docker compose -f deploy/docker-compose.prod.yml --env-file deploy/.env up -d --build
```

El frontend se construye desde su repo en GitHub, en la rama de `FRONTEND_REF`
(`main` por defecto): lo que se despliega es lo que está en esa rama.

## El barman con LLM (clave opcional)

`POST /api/v1/bar/talk` lleva el texto libre del socio a Claude Haiku. Sin
`ANTHROPIC_API_KEY` en `deploy/.env` la app arranca igual: el LLM queda apagado
y el barman contesta siempre con el respaldo (`source: FALLBACK`, clave
`barman.busy`). Con la variable vacía, que es como viene de `.env.example`, pasa
lo mismo.

- La clave se escribe a mano en `deploy/.env` del servidor, nunca en git ni en
  un comando que la imprima. La compose la pasa al contenedor como
  `ANTHROPIC_API_KEY` y, si falta, la deja vacía.
- El backend necesita salida HTTPS (443) a `api.anthropic.com`. Si no la hay, el
  barman cae al respaldo sin romper nada.
- El techo de gasto mensual (5 $) se pone en la Console de Anthropic, no aquí. En
  la app, el techo es de 0,50 $ al día, más 30 mensajes por socio y día, 60 por
  IP y día, 10 por minuto y socio, y 8 llamadas a la vez.
- Los contadores y la memoria de la charla viven en RAM: un despliegue los
  reinicia. Sirve de reinicio de emergencia si el tope del día se agota.
- Caddy corta con un 413 los cuerpos de más de 16 KB en `/api/*`, antes de que
  lleguen a Spring.

Comprobar sin enseñar la clave:

```bash
docker compose -f deploy/docker-compose.prod.yml --env-file deploy/.env exec backend sh -c 'test -n "$ANTHROPIC_API_KEY" && echo set || echo empty'
docker compose -f deploy/docker-compose.prod.yml logs backend | grep "Barman LLM"
head -c 1048576 /dev/zero > /tmp/grande.json
curl -s -o /dev/null -w '%{http_code}\n' -X POST -H 'Content-Type: application/json' --data-binary @/tmp/grande.json https://iulianlounge.com/api/v1/bar/talk    # 413
```

## BD de desarrollo creada antes del 25-sep

La V2 se renombró (errata `insesnsitive`). Una BD local que ya la tenía aplicada
falla en la validación de Flyway hasta que se actualiza su historial:

```bash
docker exec lounge-db psql -U postgres -d iulianlounge -c \
  "UPDATE flyway_schema_history SET description = 'make email unique case insensitive', script = 'V2__make_email_unique_case_insensitive.sql' WHERE version = '2';"
```

## Qué no hacer

- No añadir `ports` a `postgres` ni a `backend`: Docker se salta `ufw`, y el
  puerto quedaría abierto a internet.
- No cambiar `JWT_SECRET` salvo que se haya filtrado: cierra todas las sesiones.
- No ejecutar `docker compose config` ni imprimir `deploy/.env`: sacarían
  `ANTHROPIC_API_KEY` y el resto de secretos a la terminal.
- No borrar el volumen `iulianlounge_pgdata`: es la base de datos. El script
  de `postgres-init/` solo corre con el volumen vacío.
- No usar `docker compose -p deploy ...` en este servidor: `deploy` era el
  proyecto de MeDeben. El del lounge se llama `iulianlounge` (fijado en la
  compose).
