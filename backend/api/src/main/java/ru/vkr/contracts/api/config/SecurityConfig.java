package ru.vkr.contracts.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.beans.factory.annotation.Value;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    private final SecurityExceptionHandlers securityExceptionHandlers;

    public SecurityConfig(SecurityExceptionHandlers securityExceptionHandlers) {
        this.securityExceptionHandlers = securityExceptionHandlers;
    }

    @Value("${security.users.admin.username}")
    private String adminUsername;

    @Value("${security.users.admin.password}")
    private String adminPassword;

    @Value("${security.users.developer.username}")
    private String developerUsername;

    @Value("${security.users.developer.password}")
    private String developerPassword;

    @Value("${security.users.viewer.username}")
    private String viewerUsername;

    @Value("${security.users.viewer.password}")
    private String viewerPassword;

    @Bean
    @Profile("prod")
    public SecurityFilterChain productionSecurityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requiresChannel(channel -> channel.anyRequest().requiresSecure())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(securityExceptionHandlers)
                        .accessDeniedHandler(securityExceptionHandlers))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/actuator/health").permitAll()
                        .requestMatchers("/actuator/info").hasRole("ADMIN")
                        .requestMatchers("/actuator/metrics/**").hasRole("ADMIN")
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/contracts/versions",
                                "/api/generation-jobs",
                                "/api/compatibility-reports/analyze"
                        )
                        .hasAnyRole("ADMIN", "DEVELOPER")
                        .requestMatchers(HttpMethod.GET, "/api/**")
                        .hasAnyRole("ADMIN", "DEVELOPER", "VIEWER")
                        .requestMatchers("/api/**").hasRole("ADMIN")
                        .anyRequest().denyAll())
                .httpBasic(basic -> {})
                .build();
    }

    @Bean
    @Profile("!prod")
    public SecurityFilterChain nonProdSecurityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(securityExceptionHandlers)
                        .accessDeniedHandler(securityExceptionHandlers))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        .requestMatchers("/actuator/metrics/**").hasAnyRole("ADMIN", "DEVELOPER")
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/contracts/versions",
                                "/api/generation-jobs",
                                "/api/compatibility-reports/analyze"
                        )
                        .hasAnyRole("ADMIN", "DEVELOPER")
                        .requestMatchers(HttpMethod.GET, "/api/**")
                        .hasAnyRole("ADMIN", "DEVELOPER", "VIEWER")
                        .requestMatchers("/api/**").hasRole("ADMIN")
                        .anyRequest().denyAll())
                .httpBasic(basic -> {})
                .build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public UserDetailsService userDetailsService(PasswordEncoder passwordEncoder) {
        return new InMemoryUserDetailsManager(
                createUser(adminUsername, adminPassword, "ADMIN", passwordEncoder),
                createUser(developerUsername, developerPassword, "DEVELOPER", passwordEncoder),
                createUser(viewerUsername, viewerPassword, "VIEWER", passwordEncoder)
        );
    }

    private UserDetails createUser(String username, String password, String role, PasswordEncoder passwordEncoder) {
        if (username == null || username.isBlank()) {
            throw new IllegalStateException("security.users." + role.toLowerCase() + ".username must be configured");
        }
        if (password == null || password.isBlank()) {
            throw new IllegalStateException("security.users." + role.toLowerCase() + ".password must be configured");
        }
        return User.withUsername(username)
                .password(passwordEncoder.encode(password))
                .roles(role)
                .build();
    }
}
