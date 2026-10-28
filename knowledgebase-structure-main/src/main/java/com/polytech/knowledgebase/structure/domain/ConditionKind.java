package com.polytech.knowledgebase.structure.domain;

public enum ConditionKind {
    BEGINNER("experience", "Для начинающих"),
    EXPERIENCED("experience", "Для опытных"),
    CALM_WATER("water", "Спокойная вода"),
    WAVES("water", "Волны"),
    LIGHT_WIND("wind", "Слабый ветер"),
    STRONG_WIND("wind", "Сильный ветер"),
    WITH_INSTRUCTOR("supervision", "С инструктором"),
    SOLO("supervision", "Самостоятельно"),
    FRESH_WATER("salinity", "Пресная вода"),
    SALT_WATER("salinity", "Солёная вода"),
    INLAND_WATER("location", "Внутренний водоём"),
    OPEN_SEA("location", "Открытое море"),
    WIND_SPEED("windSpeed", "Скорость ветра", "м/с"),
    WATER_TEMPERATURE("waterTemperature", "Температура воды", "°C"),
    AIR_TEMPERATURE("airTemperature", "Температура воздуха", "°C"),
    WAVE_HEIGHT("waveHeight", "Высота волн", "м");

    private final String dimension;
    private final String label;
    private final String unit;

    ConditionKind(String dimension, String label) {
        this(dimension, label, null);
    }

    ConditionKind(String dimension, String label, String unit) {
        this.dimension = dimension;
        this.label = label;
        this.unit = unit;
    }

    public String getDimension() {
        return dimension;
    }

    public String getLabel() {
        return label;
    }

    public String getUnit() {
        return unit;
    }

    public boolean isNumeric() {
        return unit != null;
    }
}
