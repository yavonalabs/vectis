package io.github.yavonalabs.vectis.core.mutation;

import java.security.Principal;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/** Loaded only when the optional Spring Security integration is available. */
public class SpringSecurityMutationActorProvider implements MutationActorProvider {
    @Override
    public Principal currentActor() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken) ? authentication : null;
    }
}
