package io.github.yavonalabs.vectis.core.query;

import java.util.List;

public record PageResult<T>(
    List<T> content,
    int page,
    int size,
    long totalElements,
    int totalPages
) {
    public boolean hasPrevious() { return page > 0; }
    public boolean hasNext() { return page + 1 < totalPages; }
    public int getPreviousPage() { return page - 1; }
    public int getNextPage() { return page + 1; }
    public int getTotalPages() { return totalPages; }

    public long getFromIndex() {
        if (totalElements == 0) return 0;
        return ((long) page * size) + 1;
    }

    public long getToIndex() {
        if (totalElements == 0) return 0;
        return Math.min(((long) (page + 1) * size), totalElements);
    }
}