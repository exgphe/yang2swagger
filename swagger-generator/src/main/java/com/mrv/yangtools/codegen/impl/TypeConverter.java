/*
 * Copyright (c) 2016 MRV Communications, Inc. All rights reserved.
 *  This program and the accompanying materials are made available under the
 *  terms of the Eclipse Public License v1.0 which accompanies this distribution,
 *  and is available at http://www.eclipse.org/legal/epl-v10.html
 *
 *  Contributors:
 *      Christopher Murch <cmurch@mrv.com>
 *      Bartosz Michalik <bartosz.michalik@amartus.com>
 */

package com.mrv.yangtools.codegen.impl;

import com.mrv.yangtools.codegen.DataObjectBuilder;
import com.mrv.yangtools.codegen.impl.swagger.EnhancedIntegerProperty;
import io.swagger.models.Xml;
import io.swagger.models.properties.*;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.model.api.SchemaNode;
import org.opendaylight.yangtools.yang.model.api.TypeDefinition;
import org.opendaylight.yangtools.yang.model.api.type.*;
import org.opendaylight.yangtools.yang.model.util.SchemaContextUtil;
import org.opendaylight.yangtools.yang.model.util.type.BaseTypes;
import org.opendaylight.yangtools.yang.parser.stmt.rfc6020.effective.type.LengthConstraintEffectiveImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Supports type conversion between YANG and swagger
 *
 * @author cmurch@mrv.com
 * @author bartosz.michalik@amartus.com
 */
public class TypeConverter {

    private SchemaContext ctx;
    private DataObjectBuilder dataObjectBuilder;
    private ModuleUtils moduleUtils;

    public TypeConverter(SchemaContext ctx) {
        this.ctx = ctx;
        this.moduleUtils = new ModuleUtils(ctx);
    }

    private static final Logger log = LoggerFactory.getLogger(TypeConverter.class);

    /**
     * Convert YANG type to swagger property
     *
     * @param type   YANG
     * @param parent for scope computation (to support leafrefs)
     * @return property
     */
    @SuppressWarnings("ConstantConditions")
    public Property convert(TypeDefinition<?> type, SchemaNode parent) {
        TypeDefinition<?> baseType = type.getBaseType();
        if (baseType == null) baseType = type;

        if (type instanceof LeafrefTypeDefinition) {
            log.debug("leaf node {}", type);
            baseType = SchemaContextUtil.getBaseTypeForLeafRef((LeafrefTypeDefinition) type, ctx, parent);
        }

        if (baseType instanceof BooleanTypeDefinition) {
            return new BooleanProperty();
        }

        if (baseType instanceof DecimalTypeDefinition) {
            StringProperty decimalProperty = new StringProperty();
            decimalProperty.setVendorExtension("x-type", "decimal64");
            DecimalTypeDefinition decimalTypeDefinition = ((DecimalTypeDefinition) baseType);
            if (decimalTypeDefinition.getRangeConstraints() != null) {
                decimalProperty.setVendorExtension("x-range", decimalTypeDefinition.getRangeConstraints());
            }
            if (decimalTypeDefinition.getFractionDigits() != null) {
                decimalProperty.setPattern("^[+-]?((\\d+(\\.\\d{0," + decimalTypeDefinition.getFractionDigits() + "})?)|(\\.\\d{1," + decimalTypeDefinition.getFractionDigits() + "}))$");
                decimalProperty.setVendorExtension("x-fraction-digits", decimalTypeDefinition.getFractionDigits());
            } else {
                // Shouldn't happen according to RFC7950
                decimalProperty.setPattern("^[+-]?((\\d+(\\.\\d*)?)|(\\.\\d+))$");
            }
            return decimalProperty;
        }

        if (baseType instanceof IntegerTypeDefinition || baseType instanceof UnsignedIntegerTypeDefinition) {
            EnhancedIntegerProperty integer = new EnhancedIntegerProperty();
            if (isInt64(baseType) || isUint64(baseType)) {
                StringProperty longInteger = new StringProperty();
                if (isInt64(baseType)) {
                    longInteger.setVendorExtension("x-type", "int64");
                    longInteger.setPattern("^(0|[1-9][0-9]*|-[1-9][0-9]*)$");
                    IntegerTypeDefinition integerTypeDefinition = ((IntegerTypeDefinition) baseType);
                    if (integerTypeDefinition.getRangeConstraints() != null) {
                        longInteger.setVendorExtension("x-range", integerTypeDefinition.getRangeConstraints());
                    }
                } else {
                    longInteger.setVendorExtension("x-type", "uint64");
                    longInteger.setPattern("^([0-9]*)$");
                    UnsignedIntegerTypeDefinition integerTypeDefinition = ((UnsignedIntegerTypeDefinition) baseType);
                    if (integerTypeDefinition.getRangeConstraints() != null) {
                        longInteger.setVendorExtension("x-range", integerTypeDefinition.getRangeConstraints());
                    }
                }
                return longInteger;
            } else
//                if (BaseTypes.isUint32(baseType)) { // TODO BaseTypes.isUint32 has bug
                integer.setFormat("int64");
//            }
//            else {
//                integer.setFormat("int32");
//            }
            if (baseType instanceof IntegerTypeDefinition) {
                IntegerTypeDefinition integerTypeDefinition = ((IntegerTypeDefinition) baseType);
                if (integerTypeDefinition.getRangeConstraints() != null) {
                    BigInteger currentMax = null;
                    BigInteger currentMin = null;
                    for (RangeConstraint rangeConstraint : integerTypeDefinition.getRangeConstraints()) {
                        if (rangeConstraint.getMax() != null) {
                            BigInteger max = new BigInteger(String.valueOf(rangeConstraint.getMax()));
                            if (currentMax == null || currentMax.compareTo(max) < 0) {
                                currentMax = max;
                            }
                        }
                        if (rangeConstraint.getMin() != null) {
                            BigInteger min = new BigInteger(String.valueOf(rangeConstraint.getMin()));
                            if (currentMin == null || currentMin.compareTo(min) > 0) {
                                currentMin = min;
                            }
                        }
                    }
                    if (currentMax != null) {
                        integer.setMaximum(currentMax);
                    }
                    if (currentMin != null) {
                        integer.setMinimum(currentMin);
                    }
                    if (integerTypeDefinition.getRangeConstraints().size() > 1) {
                        integer.setVendorExtension("x-range", integerTypeDefinition.getRangeConstraints());
                    }
                }
            } else if (baseType instanceof UnsignedIntegerTypeDefinition) {
                UnsignedIntegerTypeDefinition unsignedIntegerTypeDefinition = ((UnsignedIntegerTypeDefinition) baseType);
                if (unsignedIntegerTypeDefinition.getRangeConstraints() != null) {
                    BigInteger currentMax = null;
                    BigInteger currentMin = null;
                    for (RangeConstraint rangeConstraint : unsignedIntegerTypeDefinition.getRangeConstraints()) {
                        if (rangeConstraint.getMax() != null) {
                            BigInteger max = new BigInteger(String.valueOf(rangeConstraint.getMax()));
                            if (currentMax == null || currentMax.compareTo(max) < 0) {
                                currentMax = max;
                            }
                        }
                        if (rangeConstraint.getMin() != null) {
                            BigInteger min = new BigInteger(String.valueOf(rangeConstraint.getMin()));
                            if (currentMin == null || currentMin.compareTo(min) > 0) {
                                currentMin = min;
                            }
                        }
                    }
                    if (currentMax != null) {
                        integer.setMaximum(currentMax);
                    }
                    if (currentMin != null) {
                        integer.setMinimum(currentMin);
                    }
                    if (unsignedIntegerTypeDefinition.getRangeConstraints().size() > 1) {
                        integer.setVendorExtension("x-range", unsignedIntegerTypeDefinition.getRangeConstraints());
                    }
                }
            }
            return integer;
        }

        EnumTypeDefinition e = toEnum(type);
        if (e != null) {
            if (enumToModel()) {
                String refString = dataObjectBuilder.addModel(e);
                return new RefProperty(refString);
            }
        }
        if (type instanceof StringTypeDefinition) {
            StringTypeDefinition stringType = (StringTypeDefinition) type;
            StringProperty string = new StringProperty();
            if (stringType.getPatternConstraints() != null && !stringType.getPatternConstraints().isEmpty()) {
                if (stringType.getPatternConstraints().size() == 1) {
                    string.setPattern(stringType.getPatternConstraints().get(0).getRawRegularExpression());
                } else {
                    string.setPattern(stringType.getPatternConstraints().stream().map(patternConstraint -> "(?=" + patternConstraint.getRawRegularExpression() + ")").collect(Collectors.joining()));
                }
            }
            if (stringType.getLengthConstraints() != null) {
                Integer currentMax = null;
                Integer currentMin = null;
                for (LengthConstraint lengthConstraint : stringType.getLengthConstraints()) {
                    if (lengthConstraint.getMax() != null) {
                        if (currentMax == null || currentMax < lengthConstraint.getMax().intValue()) {
                            currentMax = lengthConstraint.getMax().intValue();
                        }
                    }
                    if (lengthConstraint.getMin() != null) {
                        if (currentMin == null || currentMin > lengthConstraint.getMin().intValue()) {
                            currentMin = lengthConstraint.getMin().intValue();
                        }
                    }
                }
                if (currentMax != null) {
                    string.setMaxLength(currentMax);
                }
                if (currentMin != null) {
                    string.setMinLength(currentMin);
                }
                if (stringType.getLengthConstraints().size() > 1) {
                    string.setVendorExtension("x-length", stringType.getLengthConstraints());
                }
            }
            return string;
        }
        if (type instanceof BinaryTypeDefinition) {
            BinaryTypeDefinition binaryType = (BinaryTypeDefinition) type;
            StringProperty binary = new StringProperty();
            binary.setFormat("byte");
            if (binaryType.getLengthConstraints() != null) {
                List<LengthConstraint> constraints = binaryType.getLengthConstraints().stream().map(c -> new LengthConstraintEffectiveImpl(Math.ceil(c.getMin().longValue() / 3.0) * 4, Math.ceil(c.getMax().longValue() / 3.0) * 4, c.getDescription(), c.getReference(), c.getErrorAppTag(), c.getErrorMessage())).collect(Collectors.toList());
                Integer currentMax = null;
                Integer currentMin = null;
                for (LengthConstraint lengthConstraint : constraints) {
                    if (lengthConstraint.getMax() != null) {
                        if (currentMax == null || currentMax < lengthConstraint.getMax().intValue()) {
                            currentMax = lengthConstraint.getMax().intValue();
                        }
                    }
                    if (lengthConstraint.getMin() != null) {
                        if (currentMin == null || currentMin > lengthConstraint.getMin().intValue()) {
                            currentMin = lengthConstraint.getMin().intValue();
                        }
                    }
                }
                if (currentMax != null) {
                    binary.setMaxLength(currentMax);
                }
                if (currentMin != null) {
                    binary.setMinLength(currentMin);
                }
                if (constraints.size() > 1) {
                    binary.setVendorExtension("x-length", constraints);
                }
            }
            return binary;
        }

        if (type instanceof UnionTypeDefinition) {
            Property unionProperty = new StringProperty();
            UnionTypeDefinition unionTypeDefinition = (UnionTypeDefinition) type;
            boolean isNumber = true;
            for (TypeDefinition<?> unionTypeDefinitionType : unionTypeDefinition.getTypes()) {
                if (unionTypeDefinitionType instanceof IntegerTypeDefinition || unionTypeDefinitionType instanceof UnsignedIntegerTypeDefinition || unionTypeDefinitionType instanceof DecimalTypeDefinition) {
                    continue;
                }
                isNumber = false;
                break;
            }
            if (isNumber && !unionTypeDefinition.getTypes().isEmpty()) {
                unionProperty = convert(unionTypeDefinition.getTypes().get(0), parent);
            }
            unionProperty.getVendorExtensions().put("x-union", unionTypeDefinition.getTypes().stream().map(typeDefinition -> convert(typeDefinition, parent)).collect(Collectors.toList()));
            return unionProperty;
        }
        if (type instanceof EmptyTypeDefinition) {
            ArrayProperty empty = new ArrayProperty();
            empty.items(new StringProperty().vendorExtension("x-nullable", true)); // Swagger 2.0 does not have a null type
            empty.setMaxItems(1);
            empty.setMinItems(1);
            empty.setVendorExtension("x-empty", true);
            return empty;
        }
        if (type instanceof IdentityrefTypeDefinition) {
            IdentityrefTypeDefinition identityrefTypeDefinition = (IdentityrefTypeDefinition) type;
            StringProperty identityRefProperty = new StringProperty();
            String parentNameSpace = moduleUtils.toModuleName(parent.getQName());
            identityRefProperty.setEnum(identityrefTypeDefinition.getIdentities().stream().flatMap(identity -> identity.getDerivedIdentities().stream().map(derivedIdentity -> moduleUtils.toModuleName(derivedIdentity.getQName()) + ":" + derivedIdentity.getQName().getLocalName())).collect(Collectors.toList()));
            identityRefProperty.getEnum().addAll(identityrefTypeDefinition.getIdentities().stream().flatMap(identity -> identity.getDerivedIdentities().stream().filter(derivedIdentity -> moduleUtils.toModuleName(derivedIdentity.getQName()).equals(parentNameSpace)).map(derivedIdentity -> derivedIdentity.getQName().getLocalName())).collect(Collectors.toList()));
            identityRefProperty.setVendorExtension("x-identity", true);
            return identityRefProperty;
        }
        return new StringProperty();
    }

    private boolean isInt64(TypeDefinition<?> baseType) {
        if (baseType == null) return false;
        Boolean result = BaseTypes.isInt64(baseType);
        if (!result) {
            result = isInt64(baseType.getBaseType());
        }
        return result;
    }

    private boolean isUint64(TypeDefinition<?> baseType) {
        if (baseType == null) return false;
        Boolean result = BaseTypes.isUint64(baseType);
        if (!result) {
            result = isUint64(baseType.getBaseType());
        }
        return result;
    }


    /**
     * Check if builder is present.
     *
     * @return <code>true</code>
     * @throws IllegalStateException in case it is not present
     */
    protected boolean enumToModel() {
        if (dataObjectBuilder == null) throw new IllegalStateException("no data object builder configured");
        return true;
    }

    private EnumTypeDefinition toEnum(TypeDefinition<?> type) {
        if (type instanceof EnumTypeDefinition) return (EnumTypeDefinition) type;
        if (type.getBaseType() instanceof EnumTypeDefinition) return (EnumTypeDefinition) type.getBaseType();
        return null;
    }


    public void setDataObjectBuilder(DataObjectBuilder dataObjectBuilder) {
        this.dataObjectBuilder = dataObjectBuilder;
    }
}
