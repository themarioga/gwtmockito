/*
 * Copyright 2013 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 *
 * Modifications copyright (C) 2026 YCM
 */
package com.google.gwtmockito;

import com.google.gwtmockito.impl.GwtMockitoClassLoader;
import com.google.gwtmockito.impl.GwtMockitoDefaults;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionConfigurationException;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.InvocationInterceptor;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ReflectiveInvocationContext;
import org.junit.jupiter.api.extension.TestExecutionExceptionHandler;
import org.junit.jupiter.api.extension.TestInstanceFactory;
import org.junit.jupiter.api.extension.TestInstanceFactoryContext;
import org.junit.jupiter.api.extension.TestInstantiationException;
import org.junit.platform.commons.support.AnnotationSupport;
import org.objenesis.Objenesis;
import org.objenesis.ObjenesisStd;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * A JUnit Jupiter extension that executes a test using GwtMockito, with the same behaviour as
 * {@link GwtMockitoTestRunner}:
 *
 * <ul>
 * <li> Calls to GWT.create will return mock or fake objects instead of throwing an exception. If a
 *      field in the test is annotated with {@link GwtMock}, that field will be returned. Otherwise,
 *      if a provider is registered via {@link GwtMockito#useProviderForType}, that provider will be
 *      used to create a fake. Otherwise, a new mock instance will be returned.
 * <li> Final modifiers on methods and classes will be removed, so that Javascript overlay types like
 *      {@link com.google.gwt.dom.client.Element} can be mocked.
 * <li> Native methods will be given no-op implementations.
 * <li> The methods of common widget base classes such as {@link com.google.gwt.user.client.ui.Widget}
 *      and {@link com.google.gwt.user.client.ui.Composite} will be replaced with no-ops. This can be
 *      customized with {@link WithClassesToStub} or by overriding {@link #getClassesToStub}.
 * </ul>
 *
 * <pre>
 * &#064;ExtendWith(GwtMockitoExtension.class)
 * class MyWidgetTest {
 *   &#064;Mock NumberFormatter formatter;
 *
 *   &#064;Test
 *   void shouldFormatNumber() { ... }
 * }
 * </pre>
 *
 * <p>The test class is reloaded through GwtMockito's classloader and every lifecycle, test,
 * parameterized, repeated and factory method is invoked on an instance of the reloaded class.
 * Customizations are made by subclassing this extension and registering the subclass with
 * {@code @ExtendWith}.
 *
 * <p>Limitations:
 * <ul>
 * <li> {@code @Nested} classes, {@code @TestInstance(Lifecycle.PER_CLASS)} and combining this
 *      extension with Mockito's {@code MockitoExtension} are not supported and fail the container.
 * <li> Arguments of parameterized tests are created outside GwtMockito's classloader, so they must
 *      be JDK types (primitives, {@code String}, collections...) or classes from packages loaded via
 *      the standard classloader. Other arguments fail the invocation.
 * <li> The test class constructor and field initializers run only on the reloaded instance; other
 *      extensions that inject fields see the instance created by JUnit, not the reloaded one.
 * <li> Parallel execution is not supported, since GwtMockito keeps static state.
 * </ul>
 *
 * @see GwtMockito
 */
public class GwtMockitoExtension implements BeforeAllCallback, AfterAllCallback,
    BeforeEachCallback, AfterEachCallback, TestInstanceFactory, InvocationInterceptor,
    TestExecutionExceptionHandler {

  private static final ExtensionContext.Namespace NAMESPACE =
      ExtensionContext.Namespace.create(GwtMockitoExtension.class);
  private static final String MOCKITO_EXTENSION = "org.mockito.junit.jupiter.MockitoExtension";
  private static final String NOT_SUPPORTED = " not supported by GwtMockitoExtension";

  /**
   * Returns a collection of classes whose non-abstract methods should always be replaced with
   * no-ops. By default this is the same list used by {@link GwtMockitoTestRunner}, plus any classes
   * specified via the {@link WithClassesToStub} annotation on the test class. Subclasses overriding
   * this method will usually want to add to {@code super.getClassesToStub(testClass)}.
   *
   * @param testClass the test class being executed
   * @return a collection of classes whose methods should be stubbed with no-ops while running tests
   */
  protected Collection<Class<?>> getClassesToStub(Class<?> testClass) {
    Collection<Class<?>> classes = GwtMockitoDefaults.classesToStub();
    WithClassesToStub annotation = testClass.getAnnotation(WithClassesToStub.class);
    if (annotation != null) {
      classes.addAll(Arrays.asList(annotation.value()));
    }
    return classes;
  }

  /**
   * Returns a list of package names that should always be loaded via the standard classloader
   * instead of through GwtMockito's custom classloader, including their subpackages. If you're
   * getting "loader constraint violation" errors, add the package the error is complaining about
   * with {@link WithPackagesToLoadViaStandardClassLoader} or by overriding this method.
   *
   * @param testClass the test class being executed
   * @return a collection of package prefixes always loaded via the standard classloader
   */
  protected Collection<String> getPackagesToLoadViaStandardClassloader(Class<?> testClass) {
    Collection<String> packages = GwtMockitoDefaults.packagesToLoadViaStandardClassloader();
    packages.addAll(GwtMockitoDefaults.jupiterPackagesToLoadViaStandardClassloader());
    WithPackagesToLoadViaStandardClassLoader annotation =
        testClass.getAnnotation(WithPackagesToLoadViaStandardClassLoader.class);
    if (annotation != null) {
      packages.addAll(Arrays.asList(annotation.value()));
    }
    return packages;
  }

  /**
   * Returns the classloader to use as the parent of GwtMockito's classloader. By default this is
   * the current thread's context classloader.
   *
   * @return classloader to use for delegation when loading classes via GwtMockito
   */
  protected ClassLoader getParentClassloader() {
    return Thread.currentThread().getContextClassLoader();
  }

  /**
   * Returns a list of additional directories, jars or zip files from which the classloader should
   * read while running tests. Entries must not end with "/"; an entry ending with "/*" includes
   * all jars in that directory. Empty by default.
   *
   * @return a list of strings to be appended to the classpath used when running tests
   */
  protected List<String> getAdditionalClasspaths() {
    return new ArrayList<>();
  }

  @Override
  public void beforeAll(ExtensionContext context) throws Exception {
    Class<?> testClass = context.getRequiredTestClass();
    checkSupported(testClass, context);
    ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
    GwtMockitoClassLoader classLoader = GwtMockitoClassLoader.create(
        getParentClassloader(),
        getAdditionalClasspaths(),
        () -> getClassesToStub(testClass),
        () -> getPackagesToLoadViaStandardClassloader(testClass));

    // Classes loaded via the context classloader during the reload must be compatible with the
    // ones used during the test, and Mockito creates mocks with the context classloader.
    Thread.currentThread().setContextClassLoader(classLoader);
    try {
      context.getStore(NAMESPACE).put(State.class, new State(
          originalClassLoader,
          classLoader,
          classLoader.loadClass(testClass.getName()),
          classLoader.loadClass(GwtMockito.class.getName())));
    } catch (Exception e) {
      Thread.currentThread().setContextClassLoader(originalClassLoader);
      throw e;
    }
  }

  @Override
  public void afterAll(ExtensionContext context) {
    State state = context.getStore(NAMESPACE).remove(State.class, State.class);
    if (state == null) {
      return;
    }
    Thread.currentThread().setContextClassLoader(state.originalClassLoader);
    if (context.getRequiredTestClass().isAnnotationPresent(WithExperimentalGarbageCollection.class)) {
      ThreadLocalCleaner.cleanUpThreadLocalValues(state.classLoader);
    }
  }

  @Override
  public Object createTestInstance(TestInstanceFactoryContext factoryContext,
      ExtensionContext context) {
    Class<?> testClass = factoryContext.getTestClass();
    // With PER_CLASS lifecycle the instance is created before beforeAll runs.
    checkSupported(testClass, context);
    State state = state(context);
    try {
      Constructor<?> constructor = state.reloadedTestClass.getDeclaredConstructor();
      constructor.setAccessible(true);
      Object reloadedInstance = constructor.newInstance();
      // JUnit requires an instance of the original class; its constructor must not run because it
      // may call GWT.create outside GwtMockito's classloader.
      Object originalInstance = state.objenesis.newInstance(testClass);
      state.reloadedInstances.put(originalInstance, reloadedInstance);
      return originalInstance;
    } catch (InvocationTargetException e) {
      throw new TestInstantiationException(
          "Failed to instantiate " + testClass.getName(), e.getCause());
    } catch (ReflectiveOperationException e) {
      throw new TestInstantiationException(
          "Failed to instantiate " + testClass.getName() + " via GwtMockitoExtension", e);
    }
  }

  @Override
  public void beforeEach(ExtensionContext context) throws Exception {
    State state = state(context);
    invokeStatic(state.customLoadedGwtMockito.getMethod("initMocks", Object.class),
        reloadedInstance(state, context));
  }

  @Override
  public void afterEach(ExtensionContext context) throws Exception {
    State state = state(context);
    try {
      invokeStatic(state.customLoadedGwtMockito.getMethod("tearDown"));
    } finally {
      state.reloadedInstances.remove(context.getRequiredTestInstance());
    }
  }

  @Override
  public void interceptBeforeAllMethod(Invocation<Void> invocation,
      ReflectiveInvocationContext<Method> invocationContext, ExtensionContext context)
      throws Throwable {
    invocation.skip();
    invokeReloaded(invocationContext, null, context);
  }

  @Override
  public void interceptAfterAllMethod(Invocation<Void> invocation,
      ReflectiveInvocationContext<Method> invocationContext, ExtensionContext context)
      throws Throwable {
    invocation.skip();
    invokeReloaded(invocationContext, null, context);
  }

  @Override
  public void interceptBeforeEachMethod(Invocation<Void> invocation,
      ReflectiveInvocationContext<Method> invocationContext, ExtensionContext context)
      throws Throwable {
    invocation.skip();
    invokeReloaded(invocationContext, reloadedInstance(state(context), context), context);
  }

  @Override
  public void interceptAfterEachMethod(Invocation<Void> invocation,
      ReflectiveInvocationContext<Method> invocationContext, ExtensionContext context)
      throws Throwable {
    invocation.skip();
    invokeReloaded(invocationContext, reloadedInstance(state(context), context), context);
  }

  @Override
  public void interceptTestMethod(Invocation<Void> invocation,
      ReflectiveInvocationContext<Method> invocationContext, ExtensionContext context)
      throws Throwable {
    invocation.skip();
    invokeReloaded(invocationContext, reloadedInstance(state(context), context), context);
  }

  @Override
  public void interceptTestTemplateMethod(Invocation<Void> invocation,
      ReflectiveInvocationContext<Method> invocationContext, ExtensionContext context)
      throws Throwable {
    invocation.skip();
    invokeReloaded(invocationContext, reloadedInstance(state(context), context), context);
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T> T interceptTestFactoryMethod(Invocation<T> invocation,
      ReflectiveInvocationContext<Method> invocationContext, ExtensionContext context)
      throws Throwable {
    invocation.skip();
    return (T) invokeReloaded(
        invocationContext, reloadedInstance(state(context), context), context);
  }

  @Override
  public void handleTestExecutionException(ExtensionContext context, Throwable throwable)
      throws Throwable {
    if (throwable instanceof ClassCastException
        && throwable.getMessage() != null
        && throwable.getMessage().contains("$MockitoMock$")) {
      throw new FailedCastException(
          "The test failed with a ClassCastException. This often indicates that you are "
              + "using a library that invokes JavaScriptObject.cast() to convert an object "
              + "from one type to another in a way that can't be mimicked in pure Java. "
              + "There are a few ways to deal with this:\n"
              + "  1) Inject the class that calls cast() into the class being tested so "
              + "that you can replace it with a mock.\n"
              + "  2) Annotate your test class with @WithClassesToStub and pass in the "
              + "class that is causing problems.\n"
              + "  3) If the class is part of GWT and not a third-party library, try "
              + "reporting it on the issue tracker at "
              + "https://github.com/themarioga/gwtmockito/issues and it might be possible to "
              + "insert a workaround.\n",
          throwable);
    }
    throw throwable;
  }

  private static void checkSupported(Class<?> testClass, ExtensionContext context) {
    if (testClass.isAnnotationPresent(Nested.class)) {
      throw new ExtensionConfigurationException(
          "@Nested test classes are" + NOT_SUPPORTED + ": " + testClass.getName());
    }
    if (context.getTestInstanceLifecycle().orElse(null) == TestInstance.Lifecycle.PER_CLASS) {
      throw new ExtensionConfigurationException(
          "@TestInstance(PER_CLASS) is" + NOT_SUPPORTED + ": " + testClass.getName());
    }
    for (ExtendWith extendWith
        : AnnotationSupport.findRepeatableAnnotations(testClass, ExtendWith.class)) {
      for (Class<?> extension : extendWith.value()) {
        if (extension.getName().equals(MOCKITO_EXTENSION)) {
          throw new ExtensionConfigurationException(
              "MockitoExtension is" + NOT_SUPPORTED + " (GwtMockitoExtension already initializes "
                  + "@Mock fields): " + testClass.getName());
        }
      }
    }
  }

  private static State state(ExtensionContext context) {
    State state = context.getStore(NAMESPACE).get(State.class, State.class);
    if (state == null) {
      throw new ExtensionConfigurationException(
          "GwtMockitoExtension was not initialized for " + context.getRequiredTestClass().getName());
    }
    return state;
  }

  private static Object reloadedInstance(State state, ExtensionContext context) {
    return state.reloadedInstances.get(context.getRequiredTestInstance());
  }

  private static Object invokeReloaded(ReflectiveInvocationContext<Method> invocationContext,
      Object target, ExtensionContext context) throws Throwable {
    State state = state(context);
    Method original = invocationContext.getExecutable();
    Method reloaded = findReloadedMethod(state.classLoader, original);
    Object[] arguments = invocationContext.getArguments().toArray();
    Class<?>[] parameterTypes = reloaded.getParameterTypes();
    for (int i = 0; i < arguments.length; i++) {
      if (arguments[i] != null
          && !parameterTypes[i].isPrimitive()
          && !parameterTypes[i].isInstance(arguments[i])) {
        throw new ParameterResolutionException(String.format(
            "Argument %d of %s has type %s, which was loaded outside GwtMockito's classloader: "
                + "such arguments are%s. Use JDK types or classes from packages loaded via the "
                + "standard classloader.",
            i, original, arguments[i].getClass().getName(), NOT_SUPPORTED));
      }
    }
    try {
      return reloaded.invoke(target, arguments);
    } catch (InvocationTargetException e) {
      throw e.getCause();
    }
  }

  private static Method findReloadedMethod(ClassLoader classLoader, Method original)
      throws ClassNotFoundException {
    Class<?> declaringClass =
        Class.forName(original.getDeclaringClass().getName(), false, classLoader);
    Class<?>[] originalTypes = original.getParameterTypes();
    for (Method candidate : declaringClass.getDeclaredMethods()) {
      if (candidate.getName().equals(original.getName())
          && sameTypeNames(candidate.getParameterTypes(), originalTypes)) {
        candidate.setAccessible(true);
        return candidate;
      }
    }
    throw new IllegalStateException("Cannot find " + original + " in the reloaded test class");
  }

  private static boolean sameTypeNames(Class<?>[] a, Class<?>[] b) {
    if (a.length != b.length) {
      return false;
    }
    for (int i = 0; i < a.length; i++) {
      if (!a[i].getTypeName().equals(b[i].getTypeName())) {
        return false;
      }
    }
    return true;
  }

  private static void invokeStatic(Method method, Object... arguments) throws Exception {
    try {
      method.invoke(null, arguments);
    } catch (InvocationTargetException e) {
      if (e.getCause() instanceof Exception exception) {
        throw exception;
      }
      if (e.getCause() instanceof Error error) {
        throw error;
      }
      throw e;
    }
  }

  private static final class State {
    final ClassLoader originalClassLoader;
    final GwtMockitoClassLoader classLoader;
    final Class<?> reloadedTestClass;
    final Class<?> customLoadedGwtMockito;
    final Objenesis objenesis = new ObjenesisStd();
    final Map<Object, Object> reloadedInstances = new IdentityHashMap<>();

    State(ClassLoader originalClassLoader, GwtMockitoClassLoader classLoader,
        Class<?> reloadedTestClass, Class<?> customLoadedGwtMockito) {
      this.originalClassLoader = originalClassLoader;
      this.classLoader = classLoader;
      this.reloadedTestClass = reloadedTestClass;
      this.customLoadedGwtMockito = customLoadedGwtMockito;
    }
  }

  private static class FailedCastException extends Exception {
    FailedCastException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
