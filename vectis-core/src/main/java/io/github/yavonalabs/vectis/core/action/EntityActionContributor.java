package io.github.yavonalabs.vectis.core.action;

import java.util.List;

public interface EntityActionContributor<T> {
    Class<T> getEntityClass();
    List<EntityAction<T>> getActions();
}