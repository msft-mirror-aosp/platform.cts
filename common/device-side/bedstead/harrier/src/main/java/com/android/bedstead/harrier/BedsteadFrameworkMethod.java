/*
 * Copyright (C) 2023 The Android Open Source Project
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

import com.android.bedstead.harrier.annotations.meta.RequireRunOnAnnotation;
import com.android.bedstead.nene.types.OptionalBoolean;
import com.google.common.base.Equivalence;
import com.google.common.collect.ImmutableList;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.runners.model.FrameworkMethod;

/** {@link FrameworkMethod} subclass which allows modifying the test name and annotations. */
public final class BedsteadFrameworkMethod extends FrameworkMethod {

    private final BedsteadJUnit4 mBedsteadJUnit4;
    private final ImmutableList<Annotation> mParameterizedAnnotations;
    private final Map<Class<? extends Annotation>, Annotation> mAnnotationsMap = new HashMap<>();
    private final Equivalence<Iterable<Annotation>> equivalence =
            Equivalence.equals().pairwise(); // For element-wise comparison

    private Annotation[] mAnnotations;

    public BedsteadFrameworkMethod(BedsteadJUnit4 bedsteadJUnit4, Method method) {
        this(bedsteadJUnit4, method, /* parameterizedAnnotations= */ new ArrayList<>());
    }

    public BedsteadFrameworkMethod(
            BedsteadJUnit4 bedsteadJUnit4,
            Method method,
            List<Annotation> parameterizedAnnotations) {
        super(method);
        mBedsteadJUnit4 = bedsteadJUnit4;
        mParameterizedAnnotations = ImmutableList.copyOf(parameterizedAnnotations);

        calculateAnnotations();
    }

    public ImmutableList<Annotation> getParameterizedAnnotations() {
        return mParameterizedAnnotations;
    }

    private void calculateAnnotations() {
        List<Annotation> annotations =
                getAnnotationWithReplacements(
                        getDeclaringClass().getAnnotations(),
                        mBedsteadJUnit4.getRuntimeClassAnnotations());
        annotations.addAll(
                getAnnotationWithReplacements(
                        getMethod().getAnnotations(),
                        mBedsteadJUnit4.getRuntimeClassAnnotations()));

        mBedsteadJUnit4.resolveRecursiveAnnotations(
                annotations, mParameterizedAnnotations);

        boolean hasRequireRunOnAnnotation =
                annotations.stream()
                        .anyMatch(
                                it ->
                                        it.annotationType()
                                                        .getDeclaredAnnotation(
                                                                RequireRunOnAnnotation.class)
                                                != null);

        // If there is no RequireRunOn annotation, we'll add and resolve RequireRunOnInitialUser
        if (!hasRequireRunOnAnnotation) {
            annotations.addAll(
                    BedsteadJUnit4.getReplacementAnnotations(
                            mBedsteadJUnit4.getHarrierRule(),
                            BedsteadJUnit4.requireRunOnInitialUser(
                                    /* switchToUser= */ OptionalBoolean.ANY),
                            /* parameterizedAnnotations= */ ImmutableList.of()));
        }

        mAnnotations = annotations.toArray(new Annotation[0]);

        for (Annotation annotation : annotations) {
            if (annotation instanceof DynamicParameterizedAnnotation) {
                continue; // don't return this
            }
            mAnnotationsMap.put(annotation.annotationType(), annotation);
        }
    }

    private static List<Annotation> getAnnotationWithReplacements(
            Annotation[] sourceAnnotations, List<Annotation> classAnnotations) {
        var annotations = new ArrayList<Annotation>();
        for (Annotation annotation : sourceAnnotations) {
            var replacements =
                    BedsteadJUnit4.generateReplacementAnnotations(annotation, classAnnotations);
            if (replacements == null) {
                annotations.add(annotation);
            } else {
                annotations.addAll(replacements);
            }
        }
        return AnnotationSorterKt.sortedByPriority(annotations);
    }

    @Override
    public String getName() {
        if (mParameterizedAnnotations.isEmpty()) {
            return super.getName();
        }
        StringBuilder newMethodName = new StringBuilder(super.getName());
        for (Annotation annotation : mParameterizedAnnotations) {
            newMethodName
                    .append("[")
                    .append(getParameterName(annotation))
                    .append("]");
        }
        return newMethodName.toString();
    }

    private String getParameterName(Annotation annotation) {
        if (annotation instanceof DynamicParameterizedAnnotation) {
            return ((DynamicParameterizedAnnotation) annotation).name();
        }
        return annotation.annotationType().getSimpleName();
    }

    @Override
    public boolean equals(Object obj) {
        if (!super.equals(obj)) {
            return false;
        }

        if (!(obj instanceof BedsteadFrameworkMethod)) {
            return false;
        }

        BedsteadFrameworkMethod other = (BedsteadFrameworkMethod) obj;
        return equivalence.equivalent(mParameterizedAnnotations, other.mParameterizedAnnotations);
    }

    @Override
    public Annotation[] getAnnotations() {
        return mAnnotations;
    }

    @Override
    public <T extends Annotation> T getAnnotation(Class<T> annotationType) {
        return (T) mAnnotationsMap.get(annotationType);
    }
}
