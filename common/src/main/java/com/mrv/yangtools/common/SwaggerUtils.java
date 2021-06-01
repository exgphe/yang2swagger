/*
 * Copyright (c) 2018 Amartus. All rights reserved.
 *  This program and the accompanying materials are made available under the
 *  terms of the Eclipse Public License v1.0 which accompanies this distribution,
 *  and is available at http://www.eclipse.org/legal/epl-v10.html
 *
 *  Contributors:
 *      Bartosz Michalik <bartosz.michalik@amartus.com>
 */
package com.mrv.yangtools.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author bartosz.michalik@amartus.com
 */
public class SwaggerUtils {
    private static final Logger log = LoggerFactory.getLogger(SwaggerUtils.class);

    public static <T> Map<String, T> sortMap(Map<String, T> toSort) {
        toSort.remove(null);
//        log.info("sort map {}", toSort);
        return toSort.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                        (u, v) -> {
                            throw new IllegalStateException(String.format("Duplicate key %s", u));
                        },
                        LinkedHashMap::new));
    }
}
