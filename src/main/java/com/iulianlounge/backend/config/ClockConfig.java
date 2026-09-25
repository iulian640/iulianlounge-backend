package com.iulianlounge.backend.config;

import java.time.Clock;
import java.time.Duration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// Reloj inyectable: en producción el de verdad, en los tests uno fijo
@Configuration
public class ClockConfig {

    // A microsegundos, la precisión de Postgres: un Instant guardado y releído es igual al original
    @Bean
    public Clock clock() {
        return Clock.tick(Clock.systemUTC(), Duration.ofNanos(1_000));
    }
}
