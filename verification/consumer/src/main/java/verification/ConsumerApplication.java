package verification;

import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import jakarta.persistence.EntityManager;

/** Disposable installation fixture, not a production security configuration. */
@SpringBootApplication
@EntityScan(basePackageClasses = {ConsumerRecord.class, io.github.yavonalabs.vectis.core.audit.VectisAuditLog.class,
        io.github.yavonalabs.vectis.core.mutation.MutationReceipt.class, io.github.yavonalabs.vectis.core.view.SavedView.class})
public class ConsumerApplication {
    public static void main(String[] args) { SpringApplication.run(ConsumerApplication.class, args); }
    @Bean SecurityFilterChain security(HttpSecurity http) throws Exception {
        return http.authorizeHttpRequests(auth -> auth.requestMatchers("/vectis-assets/**").permitAll().anyRequest().authenticated())
                .httpBasic(org.springframework.security.config.Customizer.withDefaults()).build();
    }
    @Bean UserDetailsService users() {
        return new InMemoryUserDetailsManager(User.withUsername("installer").password("{noop}disposable-fixture").roles("ADMIN").build());
    }
    @Bean CommandLineRunner seed(EntityManager em, PlatformTransactionManager manager) {
        return args -> new TransactionTemplate(manager).executeWithoutResult(tx -> {
            if (em.find(ConsumerRecord.class, 1L) == null) em.persist(new ConsumerRecord());
        });
    }
}
