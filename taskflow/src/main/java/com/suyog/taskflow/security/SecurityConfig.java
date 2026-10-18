package com.suyog.taskflow.security;

import jakarta.servlet.DispatcherType;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration(proxyBeanMethods = false)
public class SecurityConfig {

    private static final Set<String> APPLICATION_ROLES = Set.of(
            "workflow-read",
            "workflow-write"
    );

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http)
            throws Exception {

        http
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session
                        .sessionCreationPolicy(
                                SessionCreationPolicy.STATELESS
                        )
                )
                .authorizeHttpRequests(authorize -> authorize
                        .dispatcherTypeMatchers(DispatcherType.ERROR)
                        .permitAll()

                        .requestMatchers(
                                HttpMethod.GET,
                                "/actuator/health",
                                "/actuator/health/**"
                        )
                        .permitAll()

                        .requestMatchers(HttpMethod.GET, "/api/me")
                        .authenticated()

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/workflows",
                                "/api/workflows/validate",
                                "/api/workflows/execute-preview"
                        )
                        .hasAuthority("ROLE_workflow-write")

                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/workflows/*/versions/*"
                        )
                        .hasAuthority("ROLE_workflow-read")

                        .anyRequest()
                        .denyAll()
                )
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .jwt(jwt -> jwt
                                .jwtAuthenticationConverter(
                                        authenticationConverter()
                                )
                        )
                );

        return http.build();
    }

    private JwtAuthenticationConverter authenticationConverter() {
        JwtAuthenticationConverter converter =
                new JwtAuthenticationConverter();

        converter.setJwtGrantedAuthoritiesConverter(
                this::extractAuthorities
        );

        return converter;
    }

    private Collection<GrantedAuthority> extractAuthorities(Jwt jwt) {
        Set<GrantedAuthority> authorities = new LinkedHashSet<>();

        Object realmAccessClaim = jwt.getClaims().get("realm_access");

        if (!(realmAccessClaim instanceof Map<?, ?> realmAccess)) {
            return authorities;
        }

        Object rolesClaim = realmAccess.get("roles");

        if (!(rolesClaim instanceof Collection<?> roles)) {
            return authorities;
        }

        for (Object role : roles) {
            if (role instanceof String roleName
                    && APPLICATION_ROLES.contains(roleName)) {
                authorities.add(
                        new SimpleGrantedAuthority("ROLE_" + roleName)
                );
            }
        }

        return authorities;
    }

    @Bean
    public JwtDecoder jwtDecoder(
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}")
            String issuer,

            @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}")
            String jwkSetUri,

            @Value("${flowforge.security.audience}")
            String audience) {

        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withJwkSetUri(jwkSetUri)
                .build();

        DelegatingOAuth2TokenValidator<Jwt> validators =
                new DelegatingOAuth2TokenValidator<>(
                        JwtValidators.createDefaultWithIssuer(issuer),
                        new TenantTokenValidator(audience)
                );

        decoder.setJwtValidator(validators);

        return decoder;
    }
}