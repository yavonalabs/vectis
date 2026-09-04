package io.github.yavonalabs.vectis.core.widget;

public record StatCard(
    String title,
    String value,
    String description,
    String descriptionColor, // emerald, rose, indigo, amber, slate
    String icon              // users, currency, chart, check, lightning
) {
    public static StatCard make(String title, String value) {
        return new StatCard(title, value, null, "slate", "chart");
    }

    public StatCard description(String description) {
        return new StatCard(title, value, description, descriptionColor, icon);
    }

    public StatCard descriptionColor(String color) {
        return new StatCard(title, value, description, color, icon);
    }

    public StatCard icon(String icon) {
        return new StatCard(title, value, description, descriptionColor, icon);
    }
}