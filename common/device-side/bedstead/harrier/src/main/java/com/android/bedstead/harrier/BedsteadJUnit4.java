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

import androidx.annotation.Nullable;

import com.android.bedstead.enterprise.annotations.MostImportantCoexistenceTest;
import com.android.bedstead.enterprise.annotations.MostRestrictiveCoexistenceTest;
import com.android.bedstead.harrier.annotations.AnnotationCostRunPrecedence;
import com.android.bedstead.harrier.annotations.AnnotationPriorityRunPrecedence;
import com.android.bedstead.harrier.annotations.EnumTestParameter;
import com.android.bedstead.harrier.annotations.HiddenApiTest;
import com.android.bedstead.harrier.annotations.IntTestParameter;
import com.android.bedstead.harrier.annotations.PolicyArgument;
import com.android.bedstead.harrier.annotations.RequireRunOnInitialUser;
import com.android.bedstead.harrier.annotations.StringTestParameter;
import com.android.bedstead.harrier.annotations.UsesParameterizedTestGenerator;
import com.android.bedstead.harrier.annotations.UsesParameterizedTestWithArgumentGenerator;
import com.android.bedstead.harrier.annotations.meta.ParameterizedAnnotation;
import com.android.bedstead.harrier.annotations.meta.RepeatingAnnotation;
import com.android.bedstead.harrier.annotations.parameterized.IncludeNone;
import com.android.bedstead.harrier.exceptions.RestartTestException;
import com.android.bedstead.multiuser.annotations.EnsureHasSecondaryUser;
import com.android.bedstead.multiuser.annotations.RequireRunOnAdditionalUser;
import com.android.bedstead.multiuser.annotations.RequireRunOnPrimaryUser;
import com.android.bedstead.multiuser.annotations.RequireRunOnSecondaryUser;
import com.android.bedstead.nene.exceptions.NeneException;
import com.android.bedstead.nene.types.OptionalBoolean;
import com.android.bedstead.performanceanalyzer.annotations.PerformanceTest;

import com.google.auto.value.AutoAnnotation;
import com.google.common.collect.ImmutableMap;
import com.google.errorprone.annotations.CanIgnoreReturnValue;

import org.junit.Test;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.Statement;
import org.junit.runners.model.TestClass;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A JUnit test runner for use with Bedstead.
 */
@SuppressWarnings("AndroidJdkLibsChecker")
public final class BedsteadJUnit4 extends BlockJUnit4ClassRunner {

    private static final Set<TestLifecycleListener> sLifecycleListeners = new HashSet<>();

    private static final Map<Annotation, Integer> ANNOTATION_COST_CACHE = new HashMap<>();
    private static final Map<Annotation, Integer> ANNOTATION_PRIORITY_CACHE = new HashMap<>();

    private static final String LOG_TAG = "BedsteadJUnit4";
    private boolean mHasManualHarrierRule = false;
    private static final BedsteadServiceLocator mLocator = new BedsteadServiceLocator();

    @AutoAnnotation
    private static RequireRunOnPrimaryUser requireRunOnPrimaryUser(OptionalBoolean switchedToUser) {
        return new AutoAnnotation_BedsteadJUnit4_requireRunOnPrimaryUser(switchedToUser);
    }

    @AutoAnnotation
    private static RequireRunOnSecondaryUser requireRunOnSecondaryUser(
            OptionalBoolean switchedToUser) {
        return new AutoAnnotation_BedsteadJUnit4_requireRunOnSecondaryUser(switchedToUser);
    }

    @AutoAnnotation
    static RequireRunOnInitialUser requireRunOnInitialUser(OptionalBoolean switchedToUser) {
        return new AutoAnnotation_BedsteadJUnit4_requireRunOnInitialUser(switchedToUser);
    }

    @AutoAnnotation
    private static EnsureHasSecondaryUser ensureHasSecondaryUser() {
        return new AutoAnnotation_BedsteadJUnit4_ensureHasSecondaryUser();
    }

    // These are annotations which are not included indirectly
    private static final Set<String> sIgnoredAnnotationPackages = new HashSet<>();

    static {
        sIgnoredAnnotationPackages.add("java.lang.annotation");
        sIgnoredAnnotationPackages.add("com.android.bedstead.harrier.annotations.meta");
        sIgnoredAnnotationPackages.add("kotlin.*");
        sIgnoredAnnotationPackages.add("org.junit");
        sIgnoredAnnotationPackages.add("com.android.networkstack.kotlin.*");
    }

    /**
     * Annotation sorter using the priority method added to an annotation,
     * higher priority numbers are earlier in the list, if a priority is not provided
     * {@link AnnotationPriorityRunPrecedence#PRECEDENCE_NOT_IMPORTANT} will be used
     */
    public static int annotationSorter(Annotation a, Annotation b) {
        return getAnnotationPriority(a) - getAnnotationPriority(b);
    }

    private static int getAnnotationCost(Annotation annotation) {
        return ANNOTATION_COST_CACHE.computeIfAbsent(
                annotation, BedsteadJUnit4::computeAnnotationCost);
    }

    private static int getAnnotationPriority(Annotation annotation) {
        return ANNOTATION_PRIORITY_CACHE.computeIfAbsent(
                annotation, BedsteadJUnit4::computeAnnotationPriority);
    }

    private static int computeAnnotationCost(Annotation annotation) {
        try {
            return (int) annotation.annotationType().getMethod("cost").invoke(annotation);
        } catch (NoSuchMethodException e) {
            // Default to MIDDLE if no cost is found on the annotation.
            return AnnotationCostRunPrecedence.MIDDLE;
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new NeneException("Failed to invoke cost on this annotation: " + annotation, e);
        }
    }

    private static int computeAnnotationPriority(Annotation annotation) {
        if (annotation instanceof DynamicParameterizedAnnotation) {
            return ((DynamicParameterizedAnnotation) annotation).getPriority();
        }

        try {
            return (int) annotation.annotationType().getMethod("priority").invoke(annotation);
        } catch (NoSuchMethodException e) {
            // Default to PRECEDENCE_NOT_IMPORTANT if no priority is found on the annotation.
            return AnnotationPriorityRunPrecedence.PRECEDENCE_NOT_IMPORTANT;
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new NeneException(
                    "Failed to invoke priority on this annotation: " + annotation, e);
        }
    }

    static String getParameterName(Annotation annotation) {
        if (annotation instanceof DynamicParameterizedAnnotation) {
            return ((DynamicParameterizedAnnotation) annotation).name();
        }
        return annotation.annotationType().getSimpleName();
    }

    /**
     * Resolves annotations recursively.
     *
     * @param parameterizedAnnotations The class of the parameterized annotations to expand, if any
     */
    public void resolveRecursiveAnnotations(
            List<Annotation> annotations, List<Annotation> parameterizedAnnotations) {
        resolveRecursiveAnnotations(getHarrierRule(), annotations, parameterizedAnnotations);
    }

    /**
     * Resolves annotations recursively.
     *
     * @param parameterizedAnnotations The class of the parameterized annotation to expand, if any
     */
    public static void resolveRecursiveAnnotations(
            HarrierRule harrierRule,
            List<Annotation> annotations,
            List<Annotation> parameterizedAnnotations) {
        int index = 0;
        while (index < annotations.size()) {
            Annotation annotation = annotations.get(index);
            annotations.remove(index);
            List<Annotation> replacementAnnotations =
                    getReplacementAnnotations(harrierRule, annotation, parameterizedAnnotations);
            replacementAnnotations.sort(BedsteadJUnit4::annotationSorter);
            annotations.addAll(index, replacementAnnotations);
            index += replacementAnnotations.size();
        }
    }

    private static boolean isParameterizedAnnotation(Annotation annotation) {
        if (annotation instanceof DynamicParameterizedAnnotation) {
            return true;
        }

        return annotation.annotationType().getAnnotation(ParameterizedAnnotation.class) != null;
    }

    private static boolean isAnnotationClassParameterizedAnnotation(Annotation annotation) {
        return annotation.annotationType() != null
                && annotation.annotationType().getAnnotation(ParameterizedAnnotation.class) != null;
    }

    private static Annotation[] getIndirectAnnotations(Annotation annotation) {
        if (annotation instanceof DynamicParameterizedAnnotation) {
            return ((DynamicParameterizedAnnotation) annotation).annotations();
        }
        return annotation.annotationType().getAnnotations();
    }

    private static boolean isRepeatingAnnotation(Annotation annotation) {
        if (annotation instanceof DynamicParameterizedAnnotation) {
            return false;
        }

        return annotation.annotationType().getAnnotation(RepeatingAnnotation.class) != null;
    }

    private HarrierRule mHarrierRule;

    private static final ImmutableMap<
                    Class<? extends Annotation>,
                    BiFunction<HarrierRule, Annotation, Stream<Annotation>>>
            ANNOTATION_REPLACEMENTS =
                    ImmutableMap.of(
                            RequireRunOnInitialUser.class,
                            (harrierRule, a) -> {
                                RequireRunOnInitialUser requireRunOnInitialUserAnnotation =
                                        (RequireRunOnInitialUser) a;

                                if (harrierRule.isHeadlessSystemUserMode()) {
                                    return Stream.of(
                                            a,
                                            ensureHasSecondaryUser(),
                                            requireRunOnSecondaryUser(
                                                    requireRunOnInitialUserAnnotation
                                                            .switchedToUser()));
                                } else {
                                    return Stream.of(
                                            a,
                                            requireRunOnPrimaryUser(
                                                    requireRunOnInitialUserAnnotation
                                                            .switchedToUser()));
                                }
                            },
                            RequireRunOnAdditionalUser.class,
                            (harrierRule, a) -> {
                                RequireRunOnAdditionalUser requireRunOnAdditionalUserAnnotation =
                                        (RequireRunOnAdditionalUser) a;
                                if (harrierRule.isHeadlessSystemUserMode()) {
                                    return Stream.of(ensureHasSecondaryUser(), a);
                                } else {
                                    return Stream.of(
                                            a,
                                            requireRunOnSecondaryUser(
                                                    requireRunOnAdditionalUserAnnotation
                                                            .switchedToUser()));
                                }
                            });

    static List<Annotation> getReplacementAnnotations(
            HarrierRule harrierRule,
            Annotation annotation,
            List<Annotation> parameterizedAnnotations) {
        BiFunction<HarrierRule, Annotation, Stream<Annotation>> specialReplaceFunction =
                ANNOTATION_REPLACEMENTS.get(annotation.annotationType());

        if (specialReplaceFunction != null) {
            List<Annotation> replacement =
                    specialReplaceFunction.apply(harrierRule, annotation)
                            .collect(Collectors.toList());
            return replacement;
        }

        List<Annotation> replacementAnnotations = new ArrayList<>();

        if (isRepeatingAnnotation(annotation)) {
            try {
                Annotation[] annotations =
                        (Annotation[]) annotation.annotationType()
                                .getMethod("value").invoke(annotation);
                Collections.addAll(replacementAnnotations, annotations);
                return replacementAnnotations;
            } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
                throw new NeneException("Error expanding repeated annotations", e);
            }
        }

        if (isParameterizedAnnotation(annotation)
                && !parameterizedAnnotations.contains(annotation)) {
            return replacementAnnotations;
        }

        for (Annotation indirectAnnotation : getIndirectAnnotations(annotation)) {
            if (shouldSkipAnnotation(annotation)) {
                continue;
            }

            replacementAnnotations.addAll(
                    getReplacementAnnotations(
                            harrierRule, indirectAnnotation, parameterizedAnnotations));
        }

        if (!(annotation instanceof DynamicParameterizedAnnotation)) {
            // We drop the fake annotation once it's replaced
            replacementAnnotations.add(annotation);
        }

        return replacementAnnotations;
    }

    private static boolean shouldSkipAnnotation(Annotation annotation) {
        if (annotation instanceof DynamicParameterizedAnnotation) {
            return false;
        }

        if(annotation.annotationType().equals(IncludeNone.class)) {
            return true;
        }

        String annotationPackage = annotation.annotationType().getPackage().getName();

        for (String ignoredPackage : sIgnoredAnnotationPackages) {
            if (ignoredPackage.endsWith(".*")) {
                if (annotationPackage.startsWith(
                        ignoredPackage.substring(0, ignoredPackage.length() - 2))) {
                    return true;
                }
            } else if (annotationPackage.equals(ignoredPackage)) {
                return true;
            }
        }

        return false;
    }

    public BedsteadJUnit4(Class<?> testClass) throws InitializationError {
        super(testClass);
    }

    private static List<FrameworkMethod> getBasicTests(TestClass testClass) {
        return testClass.getAnnotatedMethods().stream().filter(
                method -> method.getAnnotation(Test.class) != null
                        || method.getAnnotation(MostRestrictiveCoexistenceTest.class) != null
                        || method.getAnnotation(MostImportantCoexistenceTest.class) != null
                        || method.getAnnotation(HiddenApiTest.class) != null
                        || method.getAnnotation(PerformanceTest.class) != null
                        || isMethodAnnotatedIndirectly(method, UsesParameterizedTestGenerator.class)
        ).collect(Collectors.toList());
    }

    private static <A extends Annotation> boolean isMethodAnnotatedIndirectly(
            FrameworkMethod method,
            Class<A> annotationType
    ) {
        return Arrays.stream(method.getAnnotations()).anyMatch(annotation ->
                annotation.annotationType().getDeclaredAnnotation(annotationType) != null
        );
    }

    /**
     * Groups list of annotations of type [ParameterizedAnnotation] by its [scope].
     *
     * @param parameterizedAnnotations the list of annotations of type [ParameterizedAnnotation]
     * @return list of list of [ParameterizedAnnotation] where each sub list corresponds to
     *     annotations of one scope.
     */
    private List<List<Annotation>> getParameterizedAnnotationsGroupedByScope(
            Set<Annotation> parameterizedAnnotations) {
        Map<String, List<Annotation>> annotationsPerScope = new HashMap<>();
        for (Annotation annotation : parameterizedAnnotations) {
            if (isAnnotationClassParameterizedAnnotation(annotation)
                    && !shouldSkipAnnotation(annotation)) {
                ParameterizedAnnotation parameterizedAnnotation =
                        annotation.annotationType().getAnnotation(ParameterizedAnnotation.class);
                annotationsPerScope.putIfAbsent(
                        parameterizedAnnotation.scope().name(), new ArrayList<>());
                annotationsPerScope.get(parameterizedAnnotation.scope().name()).add(annotation);
            }
        }

        return new ArrayList<>(annotationsPerScope.values());
    }

    /**
     * Generates a cartesian product of multiple sets of annotations. For example: If the
     * [annotations] param has value [[A1, A2], [A3, A4]] then it will return [[A1, A3], [A1, A4],
     * [A2, A3], [A2, A4]].
     *
     * @param annotations list of list of annotations whose cartesian product we want to generate.
     * @return cartesian product of the annotation sets.
     */
    private static List<List<Annotation>> calculateCartesianProductOfAnnotationSets(
            List<List<Annotation>> annotations) {
        List<List<Annotation>> result = new ArrayList<>();
        if (!annotations.isEmpty()) {
            generateCartesianProductOfAnnotationSets(annotations, 0, result, new ArrayList<>());
        }
        return result;
    }

    /**
     * Generates a cartesian product of multiple sets of annotations. This method is an internal
     * helper method for {@code calculateCartesianProductOfAnnotationSets()}. Refer {@code
     * calculateCartesianProductOfAnnotationSets()} for an example.
     */
    private static void generateCartesianProductOfAnnotationSets(
            List<List<Annotation>> annotations,
            int position,
            List<List<Annotation>> result,
            List<Annotation> subResult) {
        if (position == annotations.size()) {
            if (!subResult.isEmpty()) {
                result.add(new ArrayList<>(subResult));
            }
            return;
        }
        for (int i = 0; i < annotations.get(position).size(); i++) {
            subResult.add(annotations.get(position).get(i));
            generateCartesianProductOfAnnotationSets(annotations, position + 1, result, subResult);
            subResult.remove(subResult.size() - 1);
        }
    }

    @Override
    protected List<FrameworkMethod> computeTestMethods() {
        // TODO: It appears that the annotations are computed up to 8 times per run. Figure out how
        // to cut this out (this method only seems to be called once)
        List<FrameworkMethod> basicTests = getBasicTests(getTestClass());
        List<FrameworkMethod> modifiedTests = new ArrayList<>();

        for (FrameworkMethod m : basicTests) {
            Set<Annotation> parameterizedAnnotations = getParameterizedAnnotations(m.getAnnotations());

            if (parameterizedAnnotations.isEmpty()) {
                // Unparameterized, just add the original
                modifiedTests.add(new BedsteadFrameworkMethod(this, m.getMethod()));
                continue;
            }

            // Create [BedsteadFrameworkMethod] for parameterized annotation of instance {@Code
            // DynamicParameterizedAnnotation}.
            for (Annotation annotation : parameterizedAnnotations) {
                if (shouldSkipAnnotation(annotation)
                        || isAnnotationClassParameterizedAnnotation(annotation)) {
                    // Special case - does not generate a run
                    continue;
                }
                modifiedTests.add(
                        new BedsteadFrameworkMethod(this, m.getMethod(), List.of(annotation)));
            }

            List<List<Annotation>> parametrizedAnnotationsGroupedByScope =
                    getParameterizedAnnotationsGroupedByScope(parameterizedAnnotations);

            List<List<Annotation>> cartesianProductOfAnnotationSets =
                    calculateCartesianProductOfAnnotationSets(
                            parametrizedAnnotationsGroupedByScope);

            // Create [BedsteadFrameworkMethod] for each parameterized annotation of type
            // [ParameterizedAnnotation].
            for (List<Annotation> annotationsToApplyTogether : cartesianProductOfAnnotationSets) {
                modifiedTests.add(
                        new BedsteadFrameworkMethod(
                                this, m.getMethod(), annotationsToApplyTogether));
            }
        }

        modifiedTests = generateGeneralParameterisationMethods(modifiedTests);

        sortMethodsByBedsteadAnnotations(modifiedTests);

        return modifiedTests;
    }

    private List<FrameworkMethod> generateGeneralParameterisationMethods(
            List<FrameworkMethod> modifiedTests) {
        return modifiedTests.stream()
                .flatMap(this::generateGeneralParameterisationMethods)
                .collect(Collectors.toList());
    }

    private Stream<FrameworkMethod> generateGeneralParameterisationMethods(FrameworkMethod method) {
        Stream<FrameworkMethod> expandedMethods = Stream.of(method);
        if (method.getMethod().getParameterCount() == 0) {
            return expandedMethods;
        }

        for (Parameter parameter : method.getMethod().getParameters()) {
            List<Annotation> annotations =
                    new ArrayList<>(Arrays.asList(parameter.getAnnotations()));
            resolveRecursiveAnnotations(annotations, /* parameterizedAnnotations= */ List.of());

            boolean hasParameterised = false;

            for (Annotation annotation : annotations) {

                if (annotation instanceof PolicyArgument) {
                    if (hasParameterised) {
                        throw new IllegalStateException(
                                "Each parameter can only have a single parameterised annotation");
                    }
                    hasParameterised = true;

                    HarrierToEnterpriseMediator mediator =
                            HarrierToEnterpriseMediator.Companion.getMediatorOrThrowException(
                                    "you can't use @PolicyArgument without the enterprise module"
                            );
                    expandedMethods = mediator.generatePolicyArgumentTests(method, expandedMethods);
                } else if (annotation instanceof StringTestParameter) {
                    if (hasParameterised) {
                        throw new IllegalStateException(
                                "Each parameter can only have a single parameterised annotation");
                    }
                    hasParameterised = true;

                    StringTestParameter stringTestParameter = (StringTestParameter) annotation;

                    expandedMethods = expandedMethods.flatMap(
                            i -> applyStringTestParameter(i, stringTestParameter));
                } else if (annotation instanceof IntTestParameter) {
                    if (hasParameterised) {
                        throw new IllegalStateException(
                                "Each parameter can only have a single parameterised annotation");
                    }
                    hasParameterised = true;

                    IntTestParameter intTestParameter = (IntTestParameter) annotation;

                    expandedMethods = expandedMethods.flatMap(
                            i -> applyIntTestParameter(i, intTestParameter));
                } else if (annotation instanceof EnumTestParameter) {
                    if (hasParameterised) {
                        throw new IllegalStateException(
                                "Each parameter can only have a single parameterised annotation");
                    }
                    hasParameterised = true;

                    EnumTestParameter enumTestParameter = (EnumTestParameter) annotation;

                    expandedMethods = expandedMethods.flatMap(
                            i -> applyEnumTestParameter(i, enumTestParameter));
                } else {
                    var generatorAnnotation = annotation.annotationType()
                            .getAnnotation(UsesParameterizedTestWithArgumentGenerator.class);
                    if (generatorAnnotation != null) {

                        if (hasParameterised) {
                            throw new IllegalStateException(
                                    "Each parameter can only have a single parameterised annotation"
                            );
                        }
                        hasParameterised = true;

                        ParameterizedTestWithArgumentGenerator generator =
                                mLocator.get(generatorAnnotation.value());

                        var list = new ArrayList<FrameworkMethod>();
                        expandedMethods.forEach(item ->
                                list.addAll(generator.handleFrameworkMethod(item, annotation))
                        );
                        expandedMethods = list.stream();
                    }
                }
            }

            if (!hasParameterised) {
                throw new IllegalStateException(
                        "Parameter " + parameter + " must be annotated as parameterised");
            }
        }

        return expandedMethods;
    }

    private static Stream<FrameworkMethod> applyStringTestParameter(FrameworkMethod frameworkMethod,
            StringTestParameter stringTestParameter) {
        return Stream.of(stringTestParameter.value()).map(
                (i) -> new FrameworkMethodWithParameter(frameworkMethod, i)
        );
    }

    private static Stream<FrameworkMethod> applyIntTestParameter(FrameworkMethod frameworkMethod,
            IntTestParameter intTestParameter) {
        return Arrays.stream(intTestParameter.value()).mapToObj(
                (i) -> new FrameworkMethodWithParameter(frameworkMethod, i)
        );
    }

    private static Stream<FrameworkMethod> applyEnumTestParameter(FrameworkMethod frameworkMethod,
            EnumTestParameter enumTestParameter) {
        return Arrays.stream(enumTestParameter.value().getEnumConstants()).map(
                (i) -> new FrameworkMethodWithParameter(frameworkMethod, i)
        );
    }

    /**
     * Sort methods by cost and group the ones with identical bedstead annotations together.
     *
     * <p>This will also ensure that all tests methods which are not annotated for bedstead will
     * run before any tests which are annotated.
     */
    private void sortMethodsByBedsteadAnnotations(List<FrameworkMethod> modifiedTests) {
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

        List<Annotation> bedsteadAnnotationsSortedByMostCommon =
                bedsteadAnnotationsSortedByMostCommon(modifiedTests);
        var unused = comparator.thenComparing((o1, o2) -> {
            for (Annotation annotation : bedsteadAnnotationsSortedByMostCommon) {
                boolean o1HasAnnotation = o1.getAnnotation(annotation.annotationType()) != null;
                boolean o2HasAnnotation = o2.getAnnotation(annotation.annotationType()) != null;

                if (o1HasAnnotation && !o2HasAnnotation) {
                    // o1 goes to the end
                    return 1;
                } else if (o2HasAnnotation && !o1HasAnnotation) {
                    return -1;
                }
            }

            return 0;
        });

        modifiedTests.sort(comparator);
    }

    private List<Annotation> bedsteadAnnotationsSortedByCost(List<FrameworkMethod> methods) {
        Map<Annotation, Integer> annotationCosts = mapAnnotationsCost(methods);

        List<Annotation> annotations = new ArrayList<>(annotationCosts.keySet());
        annotations.sort(Comparator.comparingInt(annotationCosts::get));

        return annotations;
    }

    private List<Annotation> bedsteadAnnotationsSortedByMostCommon(List<FrameworkMethod> methods) {
        Map<Annotation, Integer> annotationCounts = countAnnotations(methods);
        List<Annotation> annotations = new ArrayList<>(annotationCounts.keySet());
        annotations.sort(Comparator.comparingInt(annotationCounts::get));
        Collections.reverse(annotations);

        return annotations;
    }

    private Map<Annotation, Integer> countAnnotations(List<FrameworkMethod> methods) {
        Map<Annotation, Integer> annotationCounts = new HashMap<>();

        for (FrameworkMethod method : methods) {
            for (Annotation annotation : method.getAnnotations()) {
                annotationCounts.put(
                        annotation, annotationCounts.getOrDefault(annotation, 0) + 1);
            }
        }

        return annotationCounts;
    }

    private Map<Annotation, Integer> mapAnnotationsCost(List<FrameworkMethod> methods) {
        Map<Annotation, Integer> annotationCosts = new HashMap<>();

        for (FrameworkMethod method : methods) {
            for (Annotation annotation : method.getAnnotations()) {
                annotationCosts.put(annotation, getAnnotationCost(annotation));
            }
        }

        return annotationCosts;
    }

    /**
     * Filters array of annotations and returns only annotations of type
     * {@link ParameterizedAnnotation} and {@link DynamicParameterizedAnnotation}.
     *
     * @param methodAnnotations the array of annotations of test method
     */
    @CanIgnoreReturnValue
    public static Set<Annotation> getParameterizedAnnotations(Annotation[] methodAnnotations) {
        Set<Annotation> parameterizedAnnotations = new HashSet<>();
        List<Annotation> annotations = new ArrayList<>(Arrays.asList(methodAnnotations));

        for (Annotation annotation : annotations) {
            var replacements = generateReplacementAnnotations(annotation);
            if (replacements != null) {
                parameterizedAnnotations.addAll(replacements);
            }

            if (isParameterizedAnnotation(annotation)) {
                parameterizedAnnotations.add(annotation);
            }
        }

        return parameterizedAnnotations;
    }

    /**
     * Parse annotation using @ParametrizedTestGenerator
     *
     * <p>To be used before general annotation processing.
     */
    @Nullable
    static List<Annotation> generateReplacementAnnotations(
            Annotation annotation) {
        Class<? extends Annotation> annotationType = annotation.annotationType();
        UsesParameterizedTestGenerator usesParameterizedTestGenerator =
                annotationType.getAnnotation(UsesParameterizedTestGenerator.class);
        if (usesParameterizedTestGenerator != null) {
            ParameterizedTestGenerator generator =
                    mLocator.get(usesParameterizedTestGenerator.value());
            var replacementAnnotations = generator.generateReplacementAnnotations(annotation);
            replacementAnnotations.sort(BedsteadJUnit4::annotationSorter);
            return replacementAnnotations;
        }
        return null;
    }

    HarrierRule getHarrierRule() {
        if (mHarrierRule == null) {
            var unused = classRules();
        }
        return mHarrierRule;
    }

    @Override
    protected List<TestRule> getTestRules(Object target) {
        var testRules = super.getTestRules(target);
        if (mHasManualHarrierRule) {
            return testRules;
        }
        var harrier = findHarrier(testRules);
        if (harrier == null) {
            testRules.add(getHarrierRule());
        }
        return testRules;
    }

    @Override
    protected List<TestRule> classRules() {
        List<TestRule> rules = super.classRules();

        mHarrierRule = findHarrier(rules);
        mHasManualHarrierRule = mHarrierRule != null;

        if (mHarrierRule == null) {
            mHarrierRule = new DeviceState();
        }
        if (!rules.contains(mHarrierRule)) {
            rules.add(mHarrierRule);
        }

        mHarrierRule.setSkipTestTeardown(true);
        mHarrierRule.setUsingBedsteadJUnit4(true);

        return rules;
    }

    private HarrierRule findHarrier(List<TestRule> rules) {
        for (TestRule rule : rules) {
            if (rule instanceof HarrierRule) {
                return (HarrierRule) rule;
            }
        }
        return null;
    }

    /**
     * True if the test is running in debug mode.
     *
     * <p>This will result in additional debugging information being added which would otherwise
     * be dropped to improve test performance.
     *
     * <p>To enable this, pass the "bedstead-debug" instrumentation arg as "true"
     */
    public static boolean isDebug() {
        try {
            Class instrumentationRegistryClass = Class.forName(
                        "androidx.test.platform.app.InstrumentationRegistry");

            Object arguments = instrumentationRegistryClass.getMethod("getArguments")
                    .invoke(null);
            return Boolean.parseBoolean((String) arguments.getClass()
                    .getMethod("getString", String.class, String.class)
                    .invoke(arguments, "bedstead-debug", "false"));
        } catch (ClassNotFoundException e) {
            return false; // Must be on the host so can't access debug information
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            throw new IllegalStateException("Error getting isDebug", e);
        }
    }

    @Override
    protected void validateTestMethods(List<Throwable> errors) {
        // We do allow arguments - they will fail validation later on if not properly annotated
    }

    /**
     * Add a listener to be informed of test lifecycle events.
     */
    public static void addLifecycleListener(TestLifecycleListener listener) {
        sLifecycleListeners.add(listener);
    }

    /**
     * Remove a listener being informed of test lifecycle events.
     */
    public static void removeLifecycleListener(TestLifecycleListener listener) {
        sLifecycleListeners.remove(listener);
    }

    @Override
    protected void runChild(final FrameworkMethod method, RunNotifier notifier) {
        Description description = describeChild(method);
        if (isIgnored(method)) {
            notifier.fireTestIgnored(description);
        } else {
            Statement statement = new Statement() {
                @Override
                public void evaluate() throws Throwable {
                    sLifecycleListeners.forEach(l -> l.testStarted(method.getName()));
                    while (true) {
                        try {
                            methodBlock(method).evaluate();
                            sLifecycleListeners.forEach(l -> l.testFinished(method.getName()));
                            return;
                        } catch (RestartTestException e) {
                            sLifecycleListeners.forEach(
                                    l -> l.testRestarted(method.getName(), e.getMessage()));
                            System.out.println(LOG_TAG + ": Restarting test(" + e.toString() + ")");
                        }
                    }
                }
            };
            runLeaf(statement, description, notifier);
        }
    }
}
