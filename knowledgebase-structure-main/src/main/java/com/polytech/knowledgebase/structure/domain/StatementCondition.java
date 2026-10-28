package com.polytech.knowledgebase.structure.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.Objects;
import org.hibernate.annotations.Check;

@Entity
@Table(name = "statement_conditions", uniqueConstraints = @UniqueConstraint(columnNames = {"statement_id", "kind"}))
@Check(constraints = "length(trim(id)) > 0 and length(trim(text)) > 0"
        + " and (min_value is null or max_value is null or min_value <= max_value)")
public class StatementCondition extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "statement_id", nullable = false)
    private Statement statement;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ConditionKind kind;

    @Column(nullable = false, length = 500)
    private String text;

    private Double minValue;
    private Double maxValue;

    @Column(length = 20)
    private String unit;

    protected StatementCondition() {
    }

    public StatementCondition(String id, Statement statement, ConditionKind kind, String text) {
        this(id, statement, kind, text, null, null);
    }

    public StatementCondition(String id, Statement statement, ConditionKind kind, String text,
                              Double minValue, Double maxValue) {
        super(id);
        this.statement = Objects.requireNonNull(statement);
        this.kind = Objects.requireNonNull(kind);
        this.text = required(text, "text", 500);

        validateRange(kind, minValue, maxValue);
        this.minValue = minValue;
        this.maxValue = maxValue;
        this.unit = kind.getUnit();
    }

    private static void validateRange(ConditionKind kind, Double minValue, Double maxValue) {
        if (!kind.isNumeric()) {
            if (minValue != null || maxValue != null) {
                throw new IllegalArgumentException("Для категориального условия нельзя задавать числовые границы.");
            }

            return;
        }

        if (minValue == null && maxValue == null) {
            throw new IllegalArgumentException("Укажите хотя бы одну границу числового условия.");
        }

        if ((minValue != null && !Double.isFinite(minValue))
                || (maxValue != null && !Double.isFinite(maxValue))) {
            throw new IllegalArgumentException("Границы условия должны быть конечными числами.");
        }

        if (minValue != null && maxValue != null && minValue > maxValue) {
            throw new IllegalArgumentException("Нижняя граница не может превышать верхнюю.");
        }

        if ((kind == ConditionKind.WIND_SPEED || kind == ConditionKind.WAVE_HEIGHT)
                && ((minValue != null && minValue < 0) || (maxValue != null && maxValue < 0))) {
            throw new IllegalArgumentException("Скорость ветра и высота волн не могут быть отрицательными.");
        }
    }

    public Statement getStatement() {
        return statement;
    }

    public ConditionKind getKind() {
        return kind;
    }

    public String getText() {
        return text;
    }

    public Double getMinValue() {
        return minValue;
    }

    public Double getMaxValue() {
        return maxValue;
    }

    public String getUnit() {
        return unit;
    }
}
