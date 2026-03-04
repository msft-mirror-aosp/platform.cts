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

import com.google.common.base.Equivalence;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import org.junit.runners.model.FrameworkMethod;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

/** {@link FrameworkMethod} subclass which allows modifying the test name and annotations. */
public final class BedsteadFrameworkMethod extends FrameworkMethod {

    private final ImmutableList<Annotation> mParameterizedAnnotations;
    private final Annotation[] mAnnotations;
    private final ImmutableMap<Class<? extends Annotation>, Annotation> mAnnotationsMap;
    private final Equivalence<Iterable<Annotation>> equivalence =
            Equivalence.equals().pairwise(); // For element-wise comparison

    private static ImmutableMap<Class<? extends Annotation>, Annotation> createAnnotationMap(
            ImmutableList<Annotation> annotations) {
        ImmutableMap.Builder<Class<? extends Annotation>, Annotation> mapBuilder =
                ImmutableMap.builder();
        annotations.stream()
                .filter(it -> !(it instanceof DynamicParameterizedAnnotation))
                .forEach(it -> mapBuilder.put(it.annotationType(), it));
        return mapBuilder.buildKeepingLast();
    }

    public BedsteadFrameworkMethod(
            Method method,
            ImmutableList<Annotation> annotations,
            ImmutableList<Annotation> parameterizedAnnotations) {
        super(method);
        mParameterizedAnnotations = parameterizedAnnotations;
        mAnnotations = annotations.toArray(new Annotation[0]);
        mAnnotationsMap = createAnnotationMap(annotations);
    }

    public ImmutableList<Annotation> getParameterizedAnnotations() {
        return mParameterizedAnnotations;
    }

    @Override
    public String getName() {
        if (mParameterizedAnnotations.isEmpty()) {
            return super.getName();
        }
        StringBuilder newMethodName = new StringBuilder(super.getName());
        for (Annotation annotation : mParameterizedAnnotations) {
            newMethodName.append("[").append(getParameterName(annotation)).append("]");
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
