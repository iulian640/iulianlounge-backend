#!/bin/sh
# Solo corre la primera vez (con el volumen vacío). Crea el rol con el que entra la app: dueño de su esquema,
# para que Flyway pueda crear tablas, pero NO superusuario. Si la app cayera, no se llevaría el servidor entero
set -eu

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
    -v app_user="$APP_DB_USER" -v app_password="$APP_DB_PASSWORD" <<'SQL'
CREATE ROLE :"app_user" LOGIN PASSWORD :'app_password';
GRANT CONNECT ON DATABASE iulianlounge TO :"app_user";
ALTER SCHEMA public OWNER TO :"app_user";
SQL
