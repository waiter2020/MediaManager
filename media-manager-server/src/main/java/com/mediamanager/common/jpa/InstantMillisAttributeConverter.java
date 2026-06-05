package com.mediamanager.common.jpa;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.time.Instant;

@Converter(autoApply = false)
public class InstantMillisAttributeConverter implements AttributeConverter<Instant, Long> {

    @Override
    public Long convertToDatabaseColumn(Instant attribute) {
        if (attribute == null) {
            return null;
        }
        return attribute.toEpochMilli();
    }

    @Override
    public Instant convertToEntityAttribute(Long dbData) {
        if (dbData == null) {
            return null;
        }
        return Instant.ofEpochMilli(dbData);
    }
}

