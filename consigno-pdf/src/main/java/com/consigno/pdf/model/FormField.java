package com.consigno.pdf.model;

import java.util.List;
import java.util.Optional;

/**
 * Représentation d'un champ de formulaire PDF (AcroForm).
 */
public record FormField(
        String name,
        FieldType type,
        String value,
        boolean readOnly,
        boolean required,
        List<String> options
) {
    public FormField {
        options = List.copyOf(options);
    }

    public Optional<String> valueOptional() {
        return Optional.ofNullable(value);
    }

    public enum FieldType {
        TEXT,
        CHECKBOX,
        RADIO,
        COMBO_BOX,
        LIST_BOX,
        SIGNATURE,
        BUTTON,
        UNKNOWN
    }
}
