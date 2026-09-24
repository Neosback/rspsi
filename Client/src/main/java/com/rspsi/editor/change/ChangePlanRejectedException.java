package com.rspsi.editor.change;

import java.util.Objects;

/** Raised when a validated change plan cannot be committed as authored. */
public final class ChangePlanRejectedException extends IllegalStateException {
    private final ChangePlanValidation validation;

    public ChangePlanRejectedException(ChangePlanValidation validation) {
        super(message(Objects.requireNonNull(validation, "validation")));
        this.validation = validation;
    }

    public ChangePlanValidation validation() {
        return validation;
    }

    private static String message(ChangePlanValidation validation) {
        if (validation.canCommit()) return "Change plan was rejected";
        ChangePlanValidation.Conflict first = validation.conflicts().get(0);
        return "Change plan rejected: " + first.code() + " at " + first.tile()
                + " - " + first.message();
    }
}
