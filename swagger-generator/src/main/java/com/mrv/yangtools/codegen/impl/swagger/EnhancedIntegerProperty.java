package com.mrv.yangtools.codegen.impl.swagger;

import io.swagger.models.properties.AbstractProperty;
import io.swagger.models.properties.Property;

import java.math.BigInteger;

public class EnhancedIntegerProperty extends AbstractProperty implements Property {
    private BigInteger maximum, minimum;
    private Boolean exclusiveMinimum, exclusiveMaximum;

    public EnhancedIntegerProperty() {
        setType("integer");
    }

    public BigInteger getMaximum() {
        return maximum;
    }

    public void setMaximum(BigInteger maximum) {
        this.maximum = maximum;
    }

    public BigInteger getMinimum() {
        return minimum;
    }

    public void setMinimum(BigInteger minimum) {
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
