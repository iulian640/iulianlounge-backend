-- role y locale son enums en Java (Role, Language): la BD tampoco acepta otros valores.
-- Añadir un rol o un idioma exige una migración que amplíe estas listas
ALTER TABLE users ADD CONSTRAINT users_role_check CHECK (role IN ('USER', 'ADMIN'));
ALTER TABLE users ADD CONSTRAINT users_locale_check CHECK (locale IN ('es', 'en'));
