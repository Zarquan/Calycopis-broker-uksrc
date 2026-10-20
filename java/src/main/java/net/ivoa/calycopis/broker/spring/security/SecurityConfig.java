/*
 * <meta:header>
 *   <meta:licence>
 *     Copyright (c) 2026, University of Manchester (http://www.manchester.ac.uk/)
 *
 *     This information is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This information is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this software. If not, see <http://www.gnu.org/licenses/>.
 *   </meta:licence>
 * </meta:header>
 *
 * AIMetrics: [
 *     {
 *     "timestamp": "2026-05-30T06:03:00",
 *     "name": "Cursor CLI",
 *     "version": "2026.02.13-41ac335",
 *     "model": "Claude 4.6 Opus (Thinking)",
 *     "contribution": {
 *       "value": 100,
 *       "units": "%"
 *       }
 *     },
 *     {
 *     "timestamp": "2026-05-30T07:55:00",
 *     "name": "Cursor CLI",
 *     "version": "2026.02.13-41ac335",
 *     "model": "Claude 4.6 Opus (Thinking)",
 *     "contribution": {
 *       "value": 50,
 *       "units": "%"
 *       }
 *     },
 *     {
 *     "timestamp": "2026-05-30T11:37:00",
 *     "name": "Cursor CLI",
 *     "version": "2026.02.13-41ac335",
 *     "model": "Claude 4.6 Opus (Thinking)",
 *     "contribution": {
 *       "value": 10,
 *       "units": "%"
 *       }
 *     },
 *     {
 *     "timestamp": "2026-10-20T11:57:26",
 *     "name": "@deepseek-ai/dsh",
 *     "version": "0.2.0-rc.2",
 *     "model": "deepseek-flash",
 *     "contribution": {
 *       "value": 10,
 *       "units": "%"
 *       }
 *     }
 *   ]
 *
 */
package net.ivoa.calycopis.broker.spring.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

import net.ivoa.calycopis.broker.spring.webapp.error.ProblemDetailsAccessDeniedHandler;
import net.ivoa.calycopis.broker.spring.webapp.error.ProblemDetailsAuthenticationEntryPoint;
import net.ivoa.calycopis.broker.spring.webapp.error.ProblemDetailsFactory;
import net.ivoa.calycopis.broker.spring.webapp.error.ProblemDetailsWriter;

/**
 * Spring Security configuration.
 * GET endpoints are publicly accessible (unauthenticated).
 * POST endpoints require authentication via HTTP Basic or Bearer JWT.
 *
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig
    {

    @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri:}")
    private String jwtIssuerUri;

    private final AdminAuthenticationProvider adminAuthenticationProvider;
    private final LocalAuthenticationProvider localAuthenticationProvider;
    private final ProblemDetailsFactory problemDetailsFactory;
    private final ProblemDetailsWriter problemDetailsWriter;

    public SecurityConfig(
        @Lazy final AdminAuthenticationProvider adminAuthenticationProvider,
        @Lazy final LocalAuthenticationProvider localAuthenticationProvider,
        final ProblemDetailsFactory problemDetailsFactory,
        final ProblemDetailsWriter problemDetailsWriter
        ){
        this.adminAuthenticationProvider = adminAuthenticationProvider;
        this.localAuthenticationProvider = localAuthenticationProvider;
        this.problemDetailsFactory = problemDetailsFactory;
        this.problemDetailsWriter = problemDetailsWriter;
        }

    @Bean
    public AuthenticationManager authenticationManager()
        {
        return new ProviderManager(
            this.adminAuthenticationProvider,
            this.localAuthenticationProvider
            );
        }

    @Bean
    public SecurityFilterChain securityFilterChain(final HttpSecurity http) throws Exception
        {
        http
            .authenticationManager(authenticationManager())
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers(HttpMethod.GET).permitAll()
                .requestMatchers("/actuator/**").permitAll()
                .requestMatchers("/admin/**").hasAuthority("ADMIN")
                .anyRequest().authenticated()
                )
            .httpBasic(basic -> {})
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint(
                    new ProblemDetailsAuthenticationEntryPoint(
                        this.problemDetailsFactory,
                        this.problemDetailsWriter,
                        "Basic"
                        )
                    )
                .accessDeniedHandler(
                    new ProblemDetailsAccessDeniedHandler(
                        this.problemDetailsFactory,
                        this.problemDetailsWriter
                        )
                    )
                );

        if (jwtIssuerUri != null && !jwtIssuerUri.isEmpty())
            {
            http.oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> {})
                .authenticationEntryPoint(
                    new ProblemDetailsAuthenticationEntryPoint(
                        this.problemDetailsFactory,
                        this.problemDetailsWriter,
                        "Bearer"
                        )
                    )
                );
            }

        return http.build();
        }

    @Bean
    public PasswordEncoder passwordEncoder()
        {
        return new BCryptPasswordEncoder();
        }

    }
