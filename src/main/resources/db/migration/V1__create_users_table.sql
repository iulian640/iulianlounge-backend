CREATE TABLE users (
    id UUID PRIMARY KEY ,
    username VARCHAR(50) NOT NULL UNIQUE,
    email TEXT UNIQUE NOT NULL,
    password_hash VARCHAR(60) NOT NULL, 
    role TEXT NOT NULL,
    locale TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    last_seen_at TIMESTAMPTZ
);