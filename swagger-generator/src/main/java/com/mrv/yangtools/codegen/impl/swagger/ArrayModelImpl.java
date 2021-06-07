package com.mrv.yangtools.codegen.impl.swagger;

import io.swagger.models.ModelImpl;
import io.swagger.models.properties.Property;

public class ArrayModelImpl extends ModelImpl {
    public static final String TYPE = "array";
    private Boolean uniqueItems;
    private Property items;
    private Integer maxItems;
    private Integer minItems;

    public ArrayModelImpl() {
        super();
        setType(TYPE);
    }

    public Boolean getUniqueItems() {
        return uniqueItems;
    }

    public void setUniqueItems(Boolean uniqueItems) {
        this.uniqueItems = uniqueItems;
    }

    public Property getItems() {
        return items;
    }

    public void setItems(Property items) {
        this.items = items;
    }

    public Integer getMaxItems() {
        return maxItems;
    }

    public void setMaxItems(Integer maxItems) {
        this.maxItems = maxItems;
    }

    public Integer getMinItems() {
        return minItems;
    }

    public void setMinItems(Integer minItems) {
        this.minItems = minItems;
    }
}
