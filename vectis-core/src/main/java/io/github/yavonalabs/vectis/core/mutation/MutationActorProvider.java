package io.github.yavonalabs.vectis.core.mutation;

import java.security.Principal;

/** Host-owned server context; never resolve an actor from mutation request fields. */
@FunctionalInterface
public interface MutationActorProvider {
    Principal currentActor();
}
