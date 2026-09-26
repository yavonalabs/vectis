package com.example.demo;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, @org.springframework.beans.factory.annotation.Value("${vectis.path:/admin}") String adminPath) throws Exception {
        var loginEntryPoint = new org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint("/login") {
            @Override
            public void commence(jakarta.servlet.http.HttpServletRequest request,
                    jakarta.servlet.http.HttpServletResponse response,
                    org.springframework.security.core.AuthenticationException exception)
                    throws java.io.IOException, jakarta.servlet.ServletException {
                if ("true".equalsIgnoreCase(request.getHeader("HX-Request"))) {
                    response.setStatus(401);
                    response.setHeader("HX-Redirect", request.getContextPath() + "/login?expired");
                    response.setHeader("Cache-Control", "no-store");
                    return;
                }
                super.commence(request, response, exception);
            }
        };
        var defaultDeniedHandler = new org.springframework.security.web.access.AccessDeniedHandlerImpl();
        var trustResolver = new org.springframework.security.authentication.AuthenticationTrustResolverImpl();
        http
            .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(loginEntryPoint)
                .accessDeniedHandler((request, response, denied) -> {
                    var authentication = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
                    if ("true".equalsIgnoreCase(request.getHeader("HX-Request"))
                            && (authentication == null || trustResolver.isAnonymous(authentication))) {
                        loginEntryPoint.commence(request, response,
                                new org.springframework.security.authentication.InsufficientAuthenticationException("Sign in required"));
                    } else {
                        defaultDeniedHandler.handle(request, response, denied);
                    }
                }))
            .authorizeHttpRequests((requests) -> requests
                .requestMatchers("/h2-console/**").permitAll() // Allow H2 console
                .requestMatchers(adminPath, adminPath + "/**").authenticated()
                .anyRequest().permitAll()
            )
            .formLogin((form) -> form.loginPage("/login").defaultSuccessUrl(adminPath, true).permitAll())
            .logout((logout) -> logout.permitAll())
            .csrf(csrf -> csrf.ignoringRequestMatchers("/h2-console/**"))
            .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin())); // Allow H2 console frames

        return http.build();
    }

    @Bean
    public UserDetailsService userDetailsService() {
        UserDetails admin = User.withDefaultPasswordEncoder()
            .username("admin")
            .password("admin")
            .roles("ADMIN")
            .build();

        UserDetails user = User.withDefaultPasswordEncoder()
            .username("user")
            .password("password")
            .roles("USER")
            .build();

        return new InMemoryUserDetailsManager(admin, user);
    }
}
