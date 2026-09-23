package com.rspsi.cache.definition;

/** Typed backend-neutral value used by definition edit transactions. */
public sealed interface DefinitionEditValue
        permits DefinitionEditValue.StringValue,
                DefinitionEditValue.IntValue,
                DefinitionEditValue.LongValue,
                DefinitionEditValue.BooleanValue {

    record StringValue(String value) implements DefinitionEditValue {
        public StringValue {
            if (value == null) throw new IllegalArgumentException("String edit value cannot be null");
        }
    }

    record IntValue(int value) implements DefinitionEditValue {
    }

    record LongValue(long value) implements DefinitionEditValue {
    }

    record BooleanValue(boolean value) implements DefinitionEditValue {
    }
}
