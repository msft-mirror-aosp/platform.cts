/*
 * Copyright (C) 2007 The Android Open Source Project
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

package android.signature.cts.tests;

import static org.junit.Assert.assertEquals;

import android.signature.cts.ApiComplianceChecker;
import android.signature.cts.ClassProvider;
import android.signature.cts.FailureType;
import android.signature.cts.JDiffClassDescription;
import android.signature.cts.ResultObserver;
import android.signature.cts.tests.data.AbstractClass;
import android.signature.cts.tests.data.AbstractClassWithCtor;
import android.signature.cts.tests.data.ComplexEnum;
import android.signature.cts.tests.data.ExtendedNormalInterface;
import android.signature.cts.tests.data.NormalClass;
import android.signature.cts.tests.data.NormalInterface;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.lang.reflect.Modifier;
import java.util.function.Consumer;

/** Test class for JDiffClassDescription. */
@SuppressWarnings("UnnecessaryUnicodeEscape")
@RunWith(JUnit4.class)
public class ApiComplianceCheckerTest extends ApiPresenceCheckerTest<ApiComplianceChecker> {

    @Override
    protected ApiComplianceChecker createChecker(ResultObserver resultObserver,
            ClassProvider provider) {
        return new ApiComplianceChecker(resultObserver, provider);
    }

    @Override
    void runWithApiChecker(
            ResultObserver resultObserver, Consumer<ApiComplianceChecker> consumer, String... excludedRuntimeClasses) {
        super.runWithApiChecker(
                resultObserver,
                checker -> {
                    consumer.accept(checker);
                    checker.checkDeferred();
                },
                excludedRuntimeClasses);
    }

    @Test
    public void testNormalClassCompliance() {
        JDiffClassDescription clz = createClass(NormalClass.class.getSimpleName());
        checkSignatureCompliance(clz);
        assertEquals("public class NormalClass", clz.toSignatureString());
    }

    @Test
    public void testMissingClass() {
        try (ExpectFailure observer = new ExpectFailure(FailureType.MISSING_CLASS)) {
            JDiffClassDescription clz = new JDiffClassDescription(
                    "android.signature.cts.tests.data", "NoSuchClass");
            clz.setType(JDiffClassDescription.JDiffType.CLASS);
            checkSignatureCompliance(clz, observer);
        }
    }

    @Test
    public void testSimpleConstructor() {
        JDiffClassDescription clz = createClass(NormalClass.class.getSimpleName());
        JDiffClassDescription.JDiffConstructor constructor = ctor("NormalClass", Modifier.PUBLIC);
        clz.addConstructor(constructor);
        checkSignatureCompliance(clz);
        assertEquals("public NormalClass()", constructor.toSignatureString());
    }

    @Test
    public void testOneArgConstructor() {
        JDiffClassDescription clz = createClass(NormalClass.class.getSimpleName());
        JDiffClassDescription.JDiffConstructor constructor = ctor("NormalClass", Modifier.PRIVATE);
        constructor.addParam("java.lang.String");
        clz.addConstructor(constructor);
        checkSignatureCompliance(clz);
        assertEquals("private NormalClass(java.lang.String)", constructor.toSignatureString());
    }

    @Test
    public void testConstructorThrowsException() {
        JDiffClassDescription clz = createClass(NormalClass.class.getSimpleName());
        JDiffClassDescription.JDiffConstructor constructor =
                ctor("NormalClass", Modifier.PROTECTED);
        constructor.addParam("java.lang.String");
        constructor.addParam("java.lang.String");
        constructor.addException("android.signature.cts.tests.data.NormalException");
        clz.addConstructor(constructor);
        checkSignatureCompliance(clz);
        assertEquals(
                "protected NormalClass(java.lang.String, java.lang.String) "
                        + "throws android.signature.cts.tests.data.NormalException",
                constructor.toSignatureString());
    }

    @Test
    public void testPackageProtectedConstructor() {
        JDiffClassDescription clz = createClass(NormalClass.class.getSimpleName());
        JDiffClassDescription.JDiffConstructor constructor = ctor("NormalClass", 0);
        constructor.addParam("java.lang.String");
        constructor.addParam("java.lang.String");
        constructor.addParam("java.lang.String");
        clz.addConstructor(constructor);
        checkSignatureCompliance(clz);
        assertEquals(
                "NormalClass(java.lang.String, java.lang.String, java.lang.String)",
                constructor.toSignatureString());
    }

    @Test
    public void testStaticMethod() {
        JDiffClassDescription clz = createClass(NormalClass.class.getSimpleName());
        JDiffClassDescription.JDiffMethod method = method("staticMethod",
                Modifier.STATIC | Modifier.PUBLIC, "void");
        clz.addMethod(method);
        checkSignatureCompliance(clz);
        assertEquals("public static void staticMethod()", method.toSignatureString());
    }

    @Test
    public void testSyncMethod() {
        JDiffClassDescription clz = createClass(NormalClass.class.getSimpleName());
        JDiffClassDescription.JDiffMethod method = method("syncMethod",
                Modifier.SYNCHRONIZED | Modifier.PUBLIC, "void");
        clz.addMethod(method);
        checkSignatureCompliance(clz);
        assertEquals("public synchronized void syncMethod()", method.toSignatureString());
    }

    @Test
    public void testPackageProtectMethod() {
        JDiffClassDescription clz = createClass(NormalClass.class.getSimpleName());
        JDiffClassDescription.JDiffMethod method = method("packageProtectedMethod", 0, "boolean");
        clz.addMethod(method);
        checkSignatureCompliance(clz);
        assertEquals("boolean packageProtectedMethod()", method.toSignatureString());
    }

    @Test
    public void testPrivateMethod() {
        JDiffClassDescription clz = createClass(NormalClass.class.getSimpleName());
        JDiffClassDescription.JDiffMethod method = method("privateMethod", Modifier.PRIVATE,
                "void");
        clz.addMethod(method);
        checkSignatureCompliance(clz);
        assertEquals("private void privateMethod()", method.toSignatureString());
    }

    @Test
    public void testProtectedMethod() {
        JDiffClassDescription clz = createClass(NormalClass.class.getSimpleName());
        JDiffClassDescription.JDiffMethod method = method("protectedMethod", Modifier.PROTECTED,
                "java.lang.String");
        clz.addMethod(method);
        checkSignatureCompliance(clz);
        assertEquals("protected java.lang.String protectedMethod()", method.toSignatureString());
    }

    @Test
    public void testThrowsMethod() {
        JDiffClassDescription clz = createClass(NormalClass.class.getSimpleName());
        JDiffClassDescription.JDiffMethod method = method("throwsMethod", Modifier.PUBLIC, "void");
        method.addException("android.signature.cts.tests.data.NormalException");
        clz.addMethod(method);
        checkSignatureCompliance(clz);
        assertEquals(
                "public void throwsMethod() "
                        + "throws android.signature.cts.tests.data.NormalException",
                method.toSignatureString());
    }

    @Test
    public void testNativeMethod() {
        JDiffClassDescription clz = createClass(NormalClass.class.getSimpleName());
        JDiffClassDescription.JDiffMethod method = method("nativeMethod",
                Modifier.PUBLIC | Modifier.NATIVE, "void");
        clz.addMethod(method);
        checkSignatureCompliance(clz);
        assertEquals("public native void nativeMethod()", method.toSignatureString());
    }

    /**
     * Check that a varargs method is treated as compliant.
     */
    @Test
    public void testVarargsMethod() {
        JDiffClassDescription clz = createClass(NormalClass.class.getSimpleName());
        JDiffClassDescription.JDiffMethod method = method("varargs",
                Modifier.PUBLIC, "void");
        method.addParam("java.lang.String...");
        clz.addMethod(method);
        assertEquals("public void varargs(java.lang.String...)", method.toSignatureString());

        checkSignatureCompliance(clz);
    }

    /**
     * Check that a clone method (which produces a special method that is marked as {@code bridge}
     * and {@code synthetic}) is treated as compliant.
     */
    @Test
    public void testCloneMethod() {
        JDiffClassDescription clz = createClass(NormalClass.class.getSimpleName());
        // The generic method:
        //     NormalClass clone() throws CloneNotSupportedException
        JDiffClassDescription.JDiffMethod method = method("clone",
                Modifier.PUBLIC, NormalClass.class.getName());
        method.addException(CloneNotSupportedException.class.getName());
        clz.addMethod(method);
        assertEquals(
                "public android.signature.cts.tests.data.NormalClass clone()"
                        + " throws java.lang.CloneNotSupportedException",
                method.toSignatureString());

        // The synthetic bridge method:
        //     Object clone() throws CloneNotSupportedException
        method = method("clone",
                Modifier.PUBLIC, Object.class.getName());
        method.addException(CloneNotSupportedException.class.getName());
        clz.addMethod(method);
        assertEquals(
                "public java.lang.Object clone()" + " throws java.lang.CloneNotSupportedException",
                method.toSignatureString());

        checkSignatureCompliance(clz);
    }

    @Test
    public void testFinalField() {
        JDiffClassDescription clz = createClass(NormalClass.class.getSimpleName());
        JDiffClassDescription.JDiffField field = new JDiffClassDescription.JDiffField(
                "FINAL_FIELD", "java.lang.String", Modifier.PUBLIC | Modifier.FINAL, VALUE);
        clz.addField(field);
        checkSignatureCompliance(clz);
        assertEquals("public final java.lang.String FINAL_FIELD", field.toSignatureString());
    }

    @Test
    public void testStaticField() {
        JDiffClassDescription clz = createClass(NormalClass.class.getSimpleName());
        JDiffClassDescription.JDiffField field = new JDiffClassDescription.JDiffField(
                "STATIC_FIELD", "java.lang.String", Modifier.PUBLIC | Modifier.STATIC, VALUE);
        clz.addField(field);
        checkSignatureCompliance(clz);
        assertEquals("public static java.lang.String STATIC_FIELD", field.toSignatureString());
    }

    @Test
    public void testVolatileFiled() {
        JDiffClassDescription clz = createClass(NormalClass.class.getSimpleName());
        JDiffClassDescription.JDiffField field = new JDiffClassDescription.JDiffField(
                "VOLATILE_FIELD", "java.lang.String", Modifier.PUBLIC | Modifier.VOLATILE, VALUE);
        clz.addField(field);
        checkSignatureCompliance(clz);
        assertEquals("public volatile java.lang.String VOLATILE_FIELD", field.toSignatureString());
    }

    @Test
    public void testTransientField() {
        JDiffClassDescription clz = createClass(NormalClass.class.getSimpleName());
        JDiffClassDescription.JDiffField field = new JDiffClassDescription.JDiffField(
                "TRANSIENT_FIELD", "java.lang.String",
                Modifier.PUBLIC | Modifier.TRANSIENT, VALUE);
        clz.addField(field);
        checkSignatureCompliance(clz);
        assertEquals(
                "public transient java.lang.String TRANSIENT_FIELD", field.toSignatureString());
    }

    @Test
    public void testPackageField() {
        JDiffClassDescription clz = createClass(NormalClass.class.getSimpleName());
        JDiffClassDescription.JDiffField field =
                new JDiffClassDescription.JDiffField("PACKAGE_FIELD", "java.lang.String", 0, VALUE);
        clz.addField(field);
        checkSignatureCompliance(clz);
        assertEquals("java.lang.String PACKAGE_FIELD", field.toSignatureString());
    }

    @Test
    public void testPrivateField() {
        JDiffClassDescription clz = createClass(NormalClass.class.getSimpleName());
        JDiffClassDescription.JDiffField field = new JDiffClassDescription.JDiffField(
                "PRIVATE_FIELD", "java.lang.String", Modifier.PRIVATE, VALUE);
        clz.addField(field);
        checkSignatureCompliance(clz);
        assertEquals("private java.lang.String PRIVATE_FIELD", field.toSignatureString());
    }

    @Test
    public void testProtectedField() {
        JDiffClassDescription clz = createClass(NormalClass.class.getSimpleName());
        JDiffClassDescription.JDiffField field = new JDiffClassDescription.JDiffField(
                "PROTECTED_FIELD", "java.lang.String", Modifier.PROTECTED, VALUE);
        clz.addField(field);
        checkSignatureCompliance(clz);
        assertEquals("protected java.lang.String PROTECTED_FIELD", field.toSignatureString());
    }

    @Test
    public void testFieldValue() {
        JDiffClassDescription clz = createClass(NormalClass.class.getSimpleName());
        JDiffClassDescription.JDiffField field = new JDiffClassDescription.JDiffField(
                "VALUE_FIELD", "java.lang.String",
                Modifier.PUBLIC | Modifier.FINAL | Modifier.STATIC, "\u2708");
        clz.addField(field);
        checkSignatureCompliance(clz);
        assertEquals("public static final java.lang.String VALUE_FIELD", field.toSignatureString());
    }

    @Test
    public void testFieldValueChanged() {
        try (ExpectFailure observer = new ExpectFailure(FailureType.MISMATCH_FIELD)) {
            JDiffClassDescription clz = createClass(NormalClass.class.getSimpleName());
            JDiffClassDescription.JDiffField field = new JDiffClassDescription.JDiffField(
                    "VALUE_FIELD", "java.lang.String",
                    Modifier.PUBLIC | Modifier.FINAL | Modifier.STATIC, "\"&#9992;\"");
            clz.addField(field);
            checkSignatureCompliance(clz, observer);
            assertEquals(
                    "public static final java.lang.String VALUE_FIELD", field.toSignatureString());
        }
    }

    @Test
    public void testInnerClass() {
        JDiffClassDescription clz = createClass("NormalClass.InnerClass");
        JDiffClassDescription.JDiffField field = new JDiffClassDescription.JDiffField(
                "innerClassData", "java.lang.String", Modifier.PRIVATE, VALUE);
        clz.addField(field);
        checkSignatureCompliance(clz);
        assertEquals("public class NormalClass.InnerClass", clz.toSignatureString());
    }

    @Test
    public void testInnerInnerClass() {
        JDiffClassDescription clz = createClass(
                "NormalClass.InnerClass.InnerInnerClass");
        JDiffClassDescription.JDiffField field = new JDiffClassDescription.JDiffField(
                "innerInnerClassData", "java.lang.String", Modifier.PRIVATE, VALUE);
        clz.addField(field);
        checkSignatureCompliance(clz);
        assertEquals(
                "public class NormalClass.InnerClass.InnerInnerClass", clz.toSignatureString());
    }

    @Test
    public void testInnerInterface() {
        JDiffClassDescription clz = new JDiffClassDescription(
                "android.signature.cts.tests.data", "NormalClass.InnerInterface");
        clz.setType(JDiffClassDescription.JDiffType.INTERFACE);
        clz.setModifier(Modifier.PUBLIC | Modifier.STATIC | Modifier.ABSTRACT);
        clz.addMethod(
                method("doSomething", Modifier.PUBLIC | Modifier.ABSTRACT, "void"));
        checkSignatureCompliance(clz);
        assertEquals("public interface NormalClass.InnerInterface", clz.toSignatureString());
    }

    @Test
    public void testInterface() {
        JDiffClassDescription clz = createInterface("NormalInterface");
        clz.addMethod(
                method("doSomething", Modifier.ABSTRACT | Modifier.PUBLIC, "void"));
        checkSignatureCompliance(clz);
        assertEquals("public interface NormalInterface", clz.toSignatureString());
    }

    /**
     * Always treat interfaces as if they are abstract, even when the modifiers do not specify that.
     */
    @Test
    public void testInterfaceAlwaysTreatAsAbstract() {
        JDiffClassDescription clz = createInterface("NormalInterface");
        clz.setModifier(Modifier.PUBLIC);
        clz.addMethod(method("doSomething", Modifier.ABSTRACT | Modifier.PUBLIC, "void"));
        checkSignatureCompliance(clz);
    }

    @Test
    public void testComplexEnum() {
        JDiffClassDescription clz = createClass(ComplexEnum.class.getSimpleName());
        clz.setExtendsClass(Enum.class.getName());
        clz.setModifier(Modifier.PUBLIC | Modifier.FINAL);
        checkSignatureCompliance(clz);
    }

    @Test
    public void testFinalClass() {
        JDiffClassDescription clz = new JDiffClassDescription(
                "android.signature.cts.tests.data", "FinalClass");
        clz.setType(JDiffClassDescription.JDiffType.CLASS);
        clz.setModifier(Modifier.PUBLIC | Modifier.FINAL);
        checkSignatureCompliance(clz);
        assertEquals("public final class FinalClass", clz.toSignatureString());
    }

    @Test
    public void testRemovingFinalFromAClass() {
        JDiffClassDescription clz = createClass(NormalClass.class.getSimpleName());
        clz.setModifier(Modifier.PUBLIC | Modifier.FINAL);
        checkSignatureCompliance(clz);
    }

    @Test
    public void testRemovingFinalFromAClass_PreviousApi() {
        JDiffClassDescription clz = createClass(NormalClass.class.getSimpleName());
        clz.setModifier(Modifier.PUBLIC | Modifier.FINAL);
        clz.setPreviousApiFlag(true);
        checkSignatureCompliance(clz);
    }

    /**
     * Test that if the API class is final but the runtime is abstract (and not final) that it is
     * an error.
     *
     * @see <a href="http://b/181019981">Bug 181019981</a>
     */
    @Test
    public void testRemovingFinalFromAClassSwitchToAbstract() {
        try (ExpectFailure observer = new ExpectFailure(FailureType.MISMATCH_CLASS)) {
            JDiffClassDescription clz = createClass(AbstractClass.class.getSimpleName());
            clz.setModifier(Modifier.PUBLIC | Modifier.FINAL);
            checkSignatureCompliance(clz, observer);
        }
    }

    /**
     * Test that if the API class in a previous release is final but the runtime is abstract (and
     * not final) that it is not an error.
     *
     * @see <a href="http://b/181019981">Bug 181019981</a>
     */
    @Test
    public void testRemovingFinalFromAClassSwitchToAbstract_PreviousApi() {
        JDiffClassDescription clz = createClass(AbstractClass.class.getSimpleName());
        clz.setModifier(Modifier.PUBLIC | Modifier.FINAL);
        clz.setPreviousApiFlag(true);
        checkSignatureCompliance(clz);
    }

    /**
     * Test that if the API class in a previous release is final but the runtime is abstract (and
     * not final) and has constructors then it is an error.
     *
     * @see <a href="http://b/181019981">Bug 181019981</a>
     */
    @Test
    public void testRemovingFinalFromAClassWithCtorSwitchToAbstract_PreviousApi() {
        try (ExpectFailure observer = new ExpectFailure(FailureType.MISMATCH_CLASS)) {
            String simpleName = AbstractClassWithCtor.class.getSimpleName();
            JDiffClassDescription clz = createClass(simpleName);
            clz.setModifier(Modifier.PUBLIC | Modifier.FINAL);
            clz.setPreviousApiFlag(true);
            clz.addConstructor(ctor(simpleName, Modifier.PUBLIC));
            checkSignatureCompliance(clz, observer);
        }
    }

    /** Test the case where the API declares the method is synchronized, but it actually is not. */
    @Test
    public void testRemovingSync() {
        JDiffClassDescription clz = createClass(NormalClass.class.getSimpleName());
        JDiffClassDescription.JDiffMethod method = method("notSyncMethod",
                Modifier.SYNCHRONIZED | Modifier.PUBLIC, "void");
        clz.addMethod(method);
        checkSignatureCompliance(clz);
    }

    /**
     * API says method is not native, but it actually is. <a href="http://b/1839558">Bug 1839558</a>
     */
    @Test
    public void testAddingNative() {
        JDiffClassDescription clz = createClass(NormalClass.class.getSimpleName());
        JDiffClassDescription.JDiffMethod method = method("nativeMethod", Modifier.PUBLIC, "void");
        clz.addMethod(method);
        checkSignatureCompliance(clz);
    }

    /**
     * API says method is native, but actually isn't. <a href="http://b/1839558">Bug 1839558</a>
     */
    @Test
    public void testRemovingNative() {
        JDiffClassDescription clz = createClass(NormalClass.class.getSimpleName());
        JDiffClassDescription.JDiffMethod method = method("notNativeMethod",
                Modifier.NATIVE | Modifier.PUBLIC, "void");
        clz.addMethod(method);
        checkSignatureCompliance(clz);
    }

    @Test
    public void testAbstractClass() {
        JDiffClassDescription clz = new JDiffClassDescription(
                "android.signature.cts.tests.data", "AbstractClass");
        clz.setType(JDiffClassDescription.JDiffType.CLASS);
        clz.setModifier(Modifier.PUBLIC | Modifier.ABSTRACT);
        checkSignatureCompliance(clz);
        assertEquals("public abstract class AbstractClass", clz.toSignatureString());
    }

    /**
     * API lists class as abstract, reflection does not. <a href="http://b/1839622">Bug 1839622</a>
     */
    @Test
    public void testRemovingAbstractFromAClass() {
        JDiffClassDescription clz =
                new JDiffClassDescription("android.signature.cts.tests.data", "NormalClass");
        clz.setType(JDiffClassDescription.JDiffType.CLASS);
        clz.setModifier(Modifier.PUBLIC | Modifier.ABSTRACT);
        checkSignatureCompliance(clz);
    }

    /**
     * Previous API lists class as abstract, reflection does not. <a href="http://b/1839622">Bug 1839622</a>
     */
    @Test
    public void testRemovingAbstractFromAClass_PreviousApi() {
        JDiffClassDescription clz = new JDiffClassDescription(
                "android.signature.cts.tests.data", "NormalClass");
        clz.setType(JDiffClassDescription.JDiffType.CLASS);
        clz.setModifier(Modifier.PUBLIC | Modifier.ABSTRACT);
        clz.setPreviousApiFlag(true);
        checkSignatureCompliance(clz);
    }

    /**
     * reflection lists class as abstract, api does not. <a href="http://b/1839622">Bug 1839622</a>
     */
    @Test
    public void testAddingAbstractToAClass() {
        try (ExpectFailure observer = new ExpectFailure(FailureType.MISMATCH_CLASS)) {
            JDiffClassDescription clz = createClass("AbstractClass");
            checkSignatureCompliance(clz, observer);
        }
    }

    /**
     * The current API lists the class as being final but the runtime class does not so they are
     * incompatible.
     */
    @Test
    public void testAddingFinalToAClass() {
        try (ExpectFailure observer = new ExpectFailure(FailureType.MISMATCH_CLASS)) {
            JDiffClassDescription clz = createClass("FinalClass");
            checkSignatureCompliance(clz, observer);
        }
    }

    /**
     * A previously released API lists the class as being final but the runtime class does not.
     *
     * <p>While adding a final modifier to a class is not strictly backwards compatible it is when
     * the class has no accessible constructors and so cannot be instantiated or extended, as is the
     * case in this test.</p>
     */
    @Test
    public void testAddingFinalToAClassNoCtor_PreviousApi() {
        JDiffClassDescription clz = createClass("FinalClass");
        clz.setPreviousApiFlag(true);
        checkSignatureCompliance(clz);
    }

    /**
     * A previously released API lists the class as being final but the runtime class does not.
     *
     * <p>Adding a final modifier to a class is not backwards compatible when the class has some
     * accessible constructors and so could be instantiated and/or extended, as is the case of this
     * class.</p>
     */
    @Test
    public void testAddingFinalToAClassWithCtor_PreviousApi() {
        try (ExpectFailure observer = new ExpectFailure(FailureType.MISMATCH_CLASS)) {
            String simpleName = "FinalClassWithCtor";
            JDiffClassDescription clz = createClass(simpleName);
            clz.setPreviousApiFlag(true);
            clz.addConstructor(ctor(simpleName, Modifier.PUBLIC));
            checkSignatureCompliance(clz, observer);
        }
    }

    /**
     * The current API lists the class as being static but the runtime class does not so they are
     * incompatible.
     */
    @Test
    public void testAddingStaticToInnerClass() {
        try (ExpectFailure observer = new ExpectFailure(FailureType.MISMATCH_CLASS)) {
            JDiffClassDescription clz = createClass("AbstractClass.StaticNestedClass");
            checkSignatureCompliance(clz, observer);
        }
    }

    /**
     * A previously released API lists the class as being static but the runtime class does not.
     *
     * <p>While adding a static modifier to a class is not strictly backwards compatible it is when
     * the class has no accessible constructors and so cannot be instantiated or extended, as is the
     * case in this test.</p>
     */
    @Test
    public void testAddingStaticToInnerClassNoCtor_PreviousApi() {
        JDiffClassDescription clz = createClass("AbstractClass.StaticNestedClass");
        clz.setPreviousApiFlag(true);
        checkSignatureCompliance(clz);
    }

    /**
     * A previously released API lists the class as being static but the runtime class does not.
     *
     * <p>Adding a static modifier to a class is not backwards compatible when the class has some
     * accessible constructors and so could be instantiated and/or extended, as is the case of this
     * class.</p>
     */
    @Test
    public void testAddingStaticToInnerClassWithCtor_PreviousApi() {
        try (ExpectFailure observer = new ExpectFailure(FailureType.MISMATCH_CLASS)) {
            String simpleName = "AbstractClass.StaticNestedClassWithCtor";
            JDiffClassDescription clz = createClass(simpleName);
            clz.setPreviousApiFlag(true);
            clz.addConstructor(ctor(simpleName, Modifier.PUBLIC));
            checkSignatureCompliance(clz, observer);
        }
    }

    /**
     * Compatible (no change):
     * <p>
     * public abstract void AbstractClass#abstractMethod()
     * -> public abstract void AbstractClass#abstractMethod()
     */
    @Test
    public void testAbstractMethod() {
        JDiffClassDescription clz = createAbstractClass(AbstractClass.class.getSimpleName());
        JDiffClassDescription.JDiffMethod method = method("abstractMethod",
                Modifier.PUBLIC | Modifier.ABSTRACT, "void");
        clz.addMethod(method);
        checkSignatureCompliance(clz);
    }

    /**
     * Incompatible (provide implementation for abstract method):
     * <p>
     * public abstract void Normal#notSyncMethod()
     * -> public void Normal#notSyncMethod()
     */
    @Test
    public void testRemovingAbstractFromMethod() {
        JDiffClassDescription clz = createClass(NormalClass.class.getSimpleName());
        JDiffClassDescription.JDiffMethod method =
                method("notSyncMethod", Modifier.PUBLIC | Modifier.ABSTRACT, "void");
        clz.addMethod(method);
        checkSignatureCompliance(clz);
    }

    /**
     * A previously released API lists the method as being abstract but the runtime class does not.
     *
     * <p>While adding an abstract modifier to a method is not strictly backwards compatible it is
     * when the class has no accessible constructors and so cannot be instantiated or extended, as
     * is the case in this test.
     */
    @Test
    public void testRemovingAbstractFromMethodOnClassNoCtor_PreviousApi() {
        JDiffClassDescription clz = createClass(NormalClass.class.getSimpleName());
        JDiffClassDescription.JDiffMethod method = method("notSyncMethod",
                Modifier.PUBLIC | Modifier.ABSTRACT, "void");
        clz.addMethod(method);
        clz.setPreviousApiFlag(true);
        checkSignatureCompliance(clz);
    }

    /**
     * Not compatible (overridden method is not overridable anymore):
     *
     * <p>public abstract void AbstractClass#finalMethod() -> public final void
     * AbstractClass#finalMethod()
     */
    @Test
    public void testAbstractToFinalMethod() {
        JDiffClassDescription clz = createAbstractClass(AbstractClass.class.getSimpleName());
        JDiffClassDescription.JDiffMethod method = method("finalMethod",
                Modifier.PUBLIC | Modifier.ABSTRACT, "void");
        clz.addMethod(method);
        try (ExpectFailure observer = new ExpectFailure(FailureType.MISMATCH_METHOD)) {
            checkSignatureCompliance(clz, observer);
        }
    }

    /**
     * Not compatible (previously implemented method becomes abstract):
     *
     * <p>public void AbstractClass#abstractMethod() -> public abstract void
     * AbstractClass#abstractMethod()
     */
    @Test
    public void testAddingAbstractToMethod() {
        JDiffClassDescription clz = createAbstractClass(AbstractClass.class.getSimpleName());
        JDiffClassDescription.JDiffMethod method = method("abstractMethod",
                Modifier.PUBLIC, "void");
        clz.addMethod(method);
        try (ExpectFailure observer = new ExpectFailure(FailureType.MISMATCH_METHOD)) {
            checkSignatureCompliance(clz, observer);
        }
    }

    @Test
    public void testFinalMethod() {
        JDiffClassDescription clz = createClass(NormalClass.class.getSimpleName());
        JDiffClassDescription.JDiffMethod method = method("finalMethod",
                Modifier.PUBLIC | Modifier.FINAL, "void");
        clz.addMethod(method);
        checkSignatureCompliance(clz);
        assertEquals("public final void finalMethod()", method.toSignatureString());
    }

    /**
     * Final Class, API lists methods as non-final, reflection has it as final.
     * @see <a href="http://b/1839589">Bug 1839589</a>
     */
    @Test
    public void testAddingFinalToAMethodInAFinalClass() {
        JDiffClassDescription clz = new JDiffClassDescription(
                "android.signature.cts.tests.data", "FinalClass");
        clz.setType(JDiffClassDescription.JDiffType.CLASS);
        clz.setModifier(Modifier.PUBLIC | Modifier.FINAL);
        JDiffClassDescription.JDiffMethod method = method("finalMethod", Modifier.PUBLIC, "void");
        clz.addMethod(method);
        checkSignatureCompliance(clz);
    }

    /**
     * Final Class, API lists methods as final, reflection has it as non-final.
     * @see <a href="http://b/1839589">Bug 1839589</a>
     */
    @Test
    public void testRemovingFinalToAMethodInAFinalClass() {
        JDiffClassDescription clz = new JDiffClassDescription(
                "android.signature.cts.tests.data", "FinalClass");
        clz.setType(JDiffClassDescription.JDiffType.CLASS);
        clz.setModifier(Modifier.PUBLIC | Modifier.FINAL);
        JDiffClassDescription.JDiffMethod method = method("nonFinalMethod",
                Modifier.PUBLIC | Modifier.FINAL, "void");
        clz.addMethod(method);
        checkSignatureCompliance(clz);
    }

    /**
     * non-final Class, API lists methods as non-final, reflection has it as
     * final. <a href="http://b/1839589">Bug 1839589</a>
     */
    @Test
    public void testAddingFinalToAMethodInANonFinalClass() {
        try (ExpectFailure observer = new ExpectFailure(FailureType.MISMATCH_METHOD)) {
            JDiffClassDescription clz = createClass("NormalClass");
            JDiffClassDescription.JDiffMethod method = method("finalMethod", Modifier.PUBLIC,
                    "void");
            clz.addMethod(method);
            checkSignatureCompliance(clz, observer);
        }
    }

    @Test
    public void testExtendedNormalInterface() {
        try (NoFailures observer = new NoFailures()) {
            runWithApiChecker(observer, checker -> {
                JDiffClassDescription iface = createInterface(
                        NormalInterface.class.getSimpleName());
                iface.addMethod(method("doSomething", Modifier.PUBLIC, "void"));
                checker.addBaseClass(iface);

                JDiffClassDescription clz =
                        createInterface(ExtendedNormalInterface.class.getSimpleName());
                clz.addMethod(
                        method("doSomethingElse", Modifier.PUBLIC | Modifier.ABSTRACT, "void"));
                clz.addImplInterface(iface.getAbsoluteClassName());
                checker.checkSignatureCompliance(clz);
            });
        }
    }

    @Test
    public void testAddingRuntimeMethodToInterface() {
        try (ExpectFailure observer = new ExpectFailure(FailureType.MISMATCH_INTERFACE_METHOD)) {
            runWithApiChecker(observer, checker -> {
                JDiffClassDescription iface = createInterface(
                        ExtendedNormalInterface.class.getSimpleName());
                iface.addMethod(method("doSomething", Modifier.PUBLIC | Modifier.ABSTRACT, "void"));
                checker.checkSignatureCompliance(iface);
            });
        }
    }

    @Test
    public void testAddingRuntimeMethodToInterface_PreviousApi() {
        try (NoFailures observer = new NoFailures()) {
            runWithApiChecker(observer, checker -> {
                JDiffClassDescription iface = createInterface(
                        ExtendedNormalInterface.class.getSimpleName());
                iface.addMethod(method("doSomething", Modifier.PUBLIC | Modifier.ABSTRACT, "void"));
                iface.setPreviousApiFlag(true);
                checker.checkSignatureCompliance(iface);
            });
        }
    }
}
