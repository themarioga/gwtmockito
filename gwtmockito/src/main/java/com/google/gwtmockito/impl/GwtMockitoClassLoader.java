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
package com.google.gwtmockito.impl;

import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtConstructor;
import javassist.CtMethod;
import javassist.Loader;
import javassist.LoaderClassPath;
import javassist.NotFoundException;
import javassist.Translator;
import javassist.bytecode.AccessFlag;

import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;

/**
 * Classloader that removes final modifiers, gives native methods no-op implementations and stubs the
 * methods and constructors of the configured classes. Used by both the JUnit 4 runner and the JUnit
 * Jupiter extension to reload the test class.
 */
public final class GwtMockitoClassLoader extends Loader implements Translator {

  private final Supplier<Collection<Class<?>>> classesToStub;
  private final Supplier<Collection<String>> packagesToLoadViaStandardClassloader;

  /**
   * The suppliers are evaluated on every class load because the runner's overridable hooks have
   * always been invoked lazily.
   */
  public static GwtMockitoClassLoader create(
      ClassLoader parent,
      List<String> additionalClasspaths,
      Supplier<Collection<Class<?>>> classesToStub,
      Supplier<Collection<String>> packagesToLoadViaStandardClassloader) {
    ClassPool classPool = new ClassPool();
    classPool.appendClassPath(new LoaderClassPath(parent));
    for (String path : additionalClasspaths) {
      try {
        classPool.appendClassPath(path);
      } catch (NotFoundException e) {
        throw new IllegalStateException("Cannot find classpath entry: " + path, e);
      }
    }
    return new GwtMockitoClassLoader(
        parent, classPool, classesToStub, packagesToLoadViaStandardClassloader);
  }

  private GwtMockitoClassLoader(
      ClassLoader classLoader,
      ClassPool classPool,
      Supplier<Collection<Class<?>>> classesToStub,
      Supplier<Collection<String>> packagesToLoadViaStandardClassloader) {
    super(classLoader == null ? GwtMockitoClassLoader.class.getClassLoader() : classLoader, classPool);
    this.classesToStub = classesToStub;
    this.packagesToLoadViaStandardClassloader = packagesToLoadViaStandardClassloader;
    try {
      addTranslator(classPool, this);
      setDomain(getClass().getProtectionDomain());
    } catch (NotFoundException e) {
      throw new AssertionError("Impossible since this.start does not throw");
    } catch (CannotCompileException e) {
      throw new AssertionError("Impossible since this.start does not throw");
    }
  }

  @Override
  protected Class<?> findClass(String name) throws ClassNotFoundException {
    // If the class is in a blacklisted package, load it with the parent or default classloader.
    for (String blacklistedPackage : packagesToLoadViaStandardClassloader.get()) {
      if (name.startsWith(blacklistedPackage)) {
        return null;
      }
    }

    // Otherwise load it with our custom classloader.
    return super.findClass(name);
  }

  @Override
  public void onLoad(ClassPool pool, String name)
      throws NotFoundException, CannotCompileException {
    CtClass clazz = pool.get(name);

    // Strip final accessFlag from the class and all methods to allow them to be mocked
    clazz.getClassFile().setAccessFlags(clazz.getClassFile().getAccessFlags() & ~AccessFlag.FINAL);
    for (CtMethod method : clazz.getDeclaredMethods()) {
      method.setModifiers(method.getModifiers() & ~Modifier.FINAL);
    }

    // Computed once per class load: the supplier may call an overridden hook that allocates a new
    // collection on every call, and large GWT widget classes have dozens of methods.
    Collection<Class<?>> classesToStub = this.classesToStub.get();

    // Create stub implementations for certain methods
    for (CtMethod method : clazz.getDeclaredMethods()) {
      if (StubGenerator.shouldStub(method, classesToStub)) {
        method.setModifiers(method.getModifiers() & ~Modifier.NATIVE);
        CtClass returnType = method.getReturnType();
        // TODO(ekuefler): Handle primitives, voids, and enums in StubGenerator
        if (returnType.isPrimitive() || returnType.getName().equals("void")) {
          method.setBody(null);
        } else if (returnType.isEnum()) {
          method.setBody(String.format("return %s.values()[0];", returnType.getName()));
        } else {
          method.setBody(String.format(
              "return (%s) com.google.gwtmockito.impl.StubGenerator.invoke("
                  + "Class.forName(\"%s\"), \"%s\", \"%s\");",
              method.getReturnType().getName(),
              method.getReturnType().getName(),
              clazz.getName(),
              method.getName()));
        }
      }
    }

    // Also stub certain constructors
    for (Class<?> classToStub : classesToStub) {
      if (classToStub.getName().equals(clazz.getName())) {
        for (CtConstructor constructor : clazz.getConstructors()) {
          String parameters = makeNullParameters(
              clazz.getSuperclass().getConstructors()[0].getParameterTypes());
          constructor.setBody("super(" + parameters + ");");
        }
      }
    }
  }

  private String makeNullParameters(CtClass[] paramClasses) {
    if (paramClasses.length == 0) {
      return "";
    }
    StringBuilder params = new StringBuilder();
    for (CtClass paramClass : paramClasses) {
      params.append(',');
      switch (paramClass.getName()) {
        case "boolean" -> params.append("false");
        case "byte"    -> params.append("(byte) 0");
        case "char"    -> params.append("(char) 0");
        case "double"  -> params.append("(double) 0");
        case "int"     -> params.append("(int) 0");
        case "float"   -> params.append("(float) 0");
        case "long"    -> params.append("(long) 0");
        case "short"   -> params.append("(short) 0");
        default        -> params.append(newMockForClassSnippet(paramClass));
      }
    }
    return params.substring(1);
  }

  private String newMockForClassSnippet(CtClass paramClass) {
    return String.format(
        "(%1$s) org.mockito.Mockito.mock("
            + "%1$s.class, new com.google.gwtmockito.impl.ReturnsCustomMocks())",
        paramClass.getName());
  }

  @Override
  public void start(ClassPool pool) {}
}
