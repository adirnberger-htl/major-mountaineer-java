package org.example.majorgame.game;

import java.util.Map;

public record SectionDefinition(
        String id,
        String title,
        Map<String, Boolean> variables,
        Map<String, SectionDefinition> subSections,
        Map<String, Room> locations
) {
    public SectionDefinition(
            String id,
            String title,
            Map<String, Boolean> variables,
            Map<String, SectionDefinition> subSections,
            Map<String, Room> locations
    ) {
        this.id = id;
        this.title = title;
        this.variables = variables != null ? Map.copyOf(variables) : Map.of();
        this.subSections = subSections != null ? Map.copyOf(subSections) : Map.of();
        this.locations = locations != null ? Map.copyOf(locations) : Map.of();
    }
}
