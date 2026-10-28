package com.polytech.knowledgebase.structure.admin;

public class InvalidInputException extends IllegalArgumentException {
    private final String field;

    public InvalidInputException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
