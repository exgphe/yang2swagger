package com.mrv.yangtools.codegen.impl.swagger;

import io.swagger.models.properties.AbstractProperty;
import io.swagger.models.properties.Property;

import java.math.BigInteger;

public class EnhancedIntegerProperty extends AbstractProperty implements Property {
    private Long maximum, minimum;
    private Boolean exclusiveMinimum, exclusiveMaximum;

    public EnhancedIntegerProperty() {
        setType("integer");
    }

    public Long getMaximum() {
        return maximum;
    }

    public void setMaximum(Long maximum) {
        this.maximum = maximum;
    }

    public Long getMinimum() {
        return minimum;
    }

    public void setMinimum(Long minimum) {
        this.minimum = minimum;
    }

    public Boolean getExclusiveMinimum() {
        return exclusiveMinimum;
    }

    public void setExclusiveMinimum(Boolean exclusiveMinimum) {
        this.exclusiveMinimum = exclusiveMinimum;
    }

    public Boolean getExclusiveMaximum() {
        return exclusiveMaximum;
    }

    public void setExclusiveMaximum(Boolean exclusiveMaximum) {
        this.exclusiveMaximum = exclusiveMaximum;
    }
}
