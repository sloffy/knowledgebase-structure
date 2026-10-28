package com.polytech.knowledgebase.structure.schema;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LegacyConditionSchemaUpdaterTests {
    private static final String LEGACY_ARRAY = "CHECK (((kind)::text = ANY ((ARRAY["
            + "'BEGINNER'::character varying, 'EXPERIENCED'::character varying, "
            + "'CALM_WATER'::character varying, 'WAVES'::character varying, "
            + "'LIGHT_WIND'::character varying, 'STRONG_WIND'::character varying])::text[])))";

    @Test
    void recognizesOnlyTheOriginalGeneratedEnumList() {
        assertThat(LegacyConditionSchemaUpdater.isLegacyEnumCheck(LEGACY_ARRAY)).isTrue();
        assertThat(LegacyConditionSchemaUpdater.isLegacyEnumCheck(
                "CHECK (kind IN ('BEGINNER', 'EXPERIENCED', 'CALM_WATER', 'WAVES', 'LIGHT_WIND', 'STRONG_WIND'))"))
                .isTrue();
    }

    @Test
    void keepsUpdatedAndCustomChecksUntouched() {
        assertThat(LegacyConditionSchemaUpdater.isLegacyEnumCheck(
                LEGACY_ARRAY.replace("'STRONG_WIND'::character varying", "'STRONG_WIND'::character varying, 'WIND_SPEED'::character varying")))
                .isFalse();
        assertThat(LegacyConditionSchemaUpdater.isLegacyEnumCheck(LEGACY_ARRAY + " AND length(kind) > 0"))
                .isFalse();
        assertThat(LegacyConditionSchemaUpdater.isLegacyEnumCheck("CHECK (kind <> 'BEGINNER')"))
                .isFalse();
        assertThat(LegacyConditionSchemaUpdater.isLegacyEnumCheck(null)).isFalse();
    }
}
