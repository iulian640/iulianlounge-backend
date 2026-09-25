package com.iulianlounge.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.iulianlounge.backend.security.JwtAuthenticationFilter;
import com.iulianlounge.backend.security.JwtService;
import com.iulianlounge.backend.security.ProblemDetailAuthenticationEntryPoint;

@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder(){
        return new BCryptPasswordEncoder();
    }

    // Sin CORS a propósito (ADR-08): en prod todo va por el mismo origen (Caddy) y en dev por el proxy de Vite.
    // Si algún día hace falta, NUNCA allowCredentials: cualquier origen permitido podría llamar a /refresh
    // con la cookie y leerse el access token
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtService jwtService) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex.authenticationEntryPoint(new ProblemDetailAuthenticationEntryPoint()))
                .authorizeHttpRequests(auth -> auth
                        // Rutas exactas: una ruta nueva bajo /auth nace privada, no pública por accidente
                        .requestMatchers(HttpMethod.POST,
                                "/api/v1/auth/register", "/api/v1/auth/login", "/api/v1/auth/refresh",
                                "/api/v1/auth/logout").permitAll()
                        // /error abierto: si no, cualquier 500 o 404 llega al cliente disfrazado de 401
                        .requestMatchers("/actuator/health", "/error").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(new JwtAuthenticationFilter(jwtService), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
