package io.github.yavonalabs.vectis.core.context;

public class DryRunContextHolder {
    private static final ThreadLocal<Boolean> isDryRun = new ThreadLocal<>();

    public static void set(boolean value) {
        isDryRun.set(value);
    }

    public static boolean isDryRun() {
        return Boolean.TRUE.equals(isDryRun.get());
    }

    public static void clear() {
        isDryRun.remove();
    }
}
