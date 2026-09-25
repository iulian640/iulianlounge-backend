# Despliegue en iulianlounge.com

Un VPS con Docker. Caddy pone el HTTPS, sirve el frontend y reenvía `/api` al
backend, así que el navegador solo habla con un origen y la cookie del refresh
funciona sin CORS (ADR-08). Postgres no se publica: solo lo ven los contenedores.

```
navegador ──https──> Caddy (web) ──/api──> backend ──> postgres
                        └─ resto: la SPA (dist del frontend)
```

## Una sola vez

1. **VPS**: Hetzner CX22 (Ubuntu 24.04), con tu clave SSH pública al crearlo.
2. **DNS**: en el registrador de `iulianlounge.com`, dos registros `A` a la IP del
   VPS: `@` y `www`. Comprueba con `dig +short iulianlounge.com` que ya responde
   la IP antes del paso 5: Caddy necesita el DNS para pedir el certificado.
3. **En el VPS**, instala Docker y abre solo SSH, 80 y 443:
   ```bash
   curl -fsSL https://get.docker.com | sh
   ufw allow OpenSSH && ufw allow 80,443/tcp && ufw allow 443/udp && ufw enable
   ```
4. **Código y secretos**:
   ```bash
   git clone https://github.com/iulian640/iulianlounge-backend.git && cd iulianlounge-backend
   cp deploy/.env.example deploy/.env
   nano deploy/.env        # rellena cada secreto con: openssl rand -base64 32
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

## Cada despliegue

```bash
cd iulianlounge-backend && git pull
docker compose -f deploy/docker-compose.prod.yml --env-file deploy/.env up -d --build
```

El frontend se construye desde su repo en GitHub, en la rama de `FRONTEND_REF`
(`main` por defecto): lo que se despliega es lo que está en esa rama.

## Qué no hacer

- No añadir `ports` a `postgres` ni a `backend`: Docker se salta `ufw`, y el
  puerto quedaría abierto a internet.
- No cambiar `JWT_SECRET` salvo que se haya filtrado: cierra todas las sesiones.
- No borrar el volumen `pgdata`: es la base de datos. El script de
  `postgres-init/` solo corre con el volumen vacío.
