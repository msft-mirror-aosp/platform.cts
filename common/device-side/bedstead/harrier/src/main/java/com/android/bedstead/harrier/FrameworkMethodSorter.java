/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.bedstead.harrier;

import com.android.bedstead.harrier.annotations.AnnotationCostRunPrecedence;
import com.android.bedstead.harrier.annotations.TestOrder;
import com.android.bedstead.nene.exceptions.NeneException;

import org.junit.runners.model.FrameworkMethod;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Sorts JUnit {@link FrameworkMethod} objects based on Bedstead's execution order rules.
 *
 * <p>Sorting is a two-level process:
 * <ol>
 * <li><b>Priority:</b> Groups tests by {@code @TestOrder}. Unordered tests (using
 * {@link Integer#MIN_VALUE}) run first.
 * <li><b>Cost:</b> Within each priority group, sorts tests by their annotation {@code cost()},
 * running lower-cost annotated methods before higher-cost ones.
 * </ol>
 */
public final class FrameworkMethodSorter {

    private FrameworkMethodSorter() {}

    private static final Map<Annotation, Integer> ANNOTATION_COST_CACHE = new HashMap<>();

    /**
     * Sorts the given list of {@link FrameworkMethod} objects based on Bedstead's execution order.
     *
     * @param modifiedTests The list of methods to be sorted.
     * @return A new {@link List} containing the methods in their sorted execution order.
     * @throws IllegalArgumentException if a test uses the reserved
     * {@code @TestOrder(Integer.MIN_VALUE)}.
     * @throws com.android.bedstead.nene.exceptions.NeneException if reflection fails when
     * reading an annotation's {@code cost()} method.
     */
    public static List<FrameworkMethod> sort(List<FrameworkMethod> modifiedTests) {
        var defaultPriority = Integer.MIN_VALUE;
        TreeMap<Integer, List<FrameworkMethod>> prioritizedTestRuns = new TreeMap<>(
                Map.of(defaultPriority, new ArrayList<>()));

        modifiedTests.forEach(
                singleTestRun -> {
                    Optional<TestOrder> optTestOrder =
                            Optional.ofNullable(singleTestRun.getAnnotation(TestOrder.class));
                    if (optTestOrder.isPresent()) {
                        int testRunPriority = optTestOrder.get().order();
                        if (testRunPriority == defaultPriority) {
                            throw new IllegalArgumentException(
                                    String.format(
                                            "Value %s restricted for use with TestOrder annotation",
                                            defaultPriority));
                        }
                        prioritizedTestRuns
                                .computeIfAbsent(testRunPriority, k -> new ArrayList<>())
                                .add(singleTestRun);
                    } else {
                        prioritizedTestRuns.get(defaultPriority).add(singleTestRun);
                    }
                });

        prioritizedTestRuns.forEach(
                (key, value) -> sortMethodsByBedsteadAnnotations(value));
        return prioritizedTestRuns.values().stream()
                .flatMap(List::stream)
                .collect(Collectors.toList());
    }

    /**
     * Sort methods by cost and group the ones with identical bedstead annotations together.
     *
     * <p>This will also ensure that all tests methods which are not annotated for bedstead will
     * run before any tests which are annotated.
     */
    private static void sortMethodsByBedsteadAnnotations(List<FrameworkMethod> modifiedTests) {
        List<Annotation> bedsteadAnnotationsSortedByCost =
                bedsteadAnnotationsSortedByCost(modifiedTests);
        Comparator<FrameworkMethod> comparator = ((o1, o2) -> {
            for (Annotation annotation : bedsteadAnnotationsSortedByCost) {
                boolean o1HasAnnotation = o1.getAnnotation(annotation.annotationType()) != null;
                boolean o2HasAnnotation = o2.getAnnotation(annotation.annotationType()) != null;

                if (o1HasAnnotation && !o2HasAnnotation) {
                    // o1 goes to the start
                    return -1;
                } else if (o2HasAnnotation && !o1HasAnnotation) {
                    return 1;
                }
            }
            return 0;
        });

        modifiedTests.sort(comparator);
    }

    private static List<Annotation> bedsteadAnnotationsSortedByCost(List<FrameworkMethod> methods) {
        Map<Annotation, Integer> annotationCosts = mapAnnotationsCost(methods);

        List<Annotation> annotations = new ArrayList<>(annotationCosts.keySet());
        annotations.sort(Comparator.comparingInt(annotationCosts::get));

        return annotations;
    }

    private static Map<Annotation, Integer> mapAnnotationsCost(List<FrameworkMethod> methods) {
        Map<Annotation, Integer> annotationCosts = new HashMap<>();

        for (FrameworkMethod method : methods) {
            for (Annotation annotation : method.getAnnotations()) {
                annotationCosts.put(annotation, getAnnotationCost(annotation));
            }
        }

        return annotationCosts;
    }

    private static int computeAnnotationCost(Annotation annotation) {
        try {
            return (int) annotation.annotationType().getMethod("cost").invoke(annotation);
        } catch (NoSuchMethodException e) {
            // Default to MIDDLE if no cost is found on the annotation.
            return AnnotationCostRunPrecedence.MIDDLE;
        } catch (IllegalAccessException | InvocationTargetException | ClassCastException e) {
            throw new NeneException("Failed to invoke cost on this annotation: " + annotation, e);
        }
    }

    private static int getAnnotationCost(Annotation annotation) {
        return ANNOTATION_COST_CACHE.computeIfAbsent(
                annotation, FrameworkMethodSorter::computeAnnotationCost);
    }
}
