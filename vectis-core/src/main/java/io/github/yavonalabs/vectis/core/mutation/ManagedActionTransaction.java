package io.github.yavonalabs.vectis.core.mutation;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import java.util.function.Supplier;

/** Returns only after commit. No new transaction may silently escape a host transaction. */
public class ManagedActionTransaction {
    private final TransactionTemplate transaction;
    private final EntityManager em;

    public ManagedActionTransaction(PlatformTransactionManager manager, EntityManager em) {
        this.transaction = new TransactionTemplate(manager);
        this.em = em;
    }

    public <T> T commit(Supplier<T> work) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Managed actions must be called outside a host transaction so the result can confirm commit.");
        }
        return transaction.execute(status -> work.get());
    }

    public void guardVersion(Object entity) {
        // Also conflicts when the handler changes no scalar field or races a delete.
        em.lock(entity, LockModeType.OPTIMISTIC_FORCE_INCREMENT);
    }
}
