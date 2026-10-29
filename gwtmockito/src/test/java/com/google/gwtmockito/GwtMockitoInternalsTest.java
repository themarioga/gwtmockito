/*
 * Copyright (C) 2026 YCM
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
 */
package com.google.gwtmockito;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.Widget;

import org.gwtproject.fixture.GwtprojectFixture;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.lang.reflect.Method;

/**
 * Unit tests for the private helper methods introduced in the Mockito 5 compatibility upgrade.
 *
 * <p>Covers:
 * <ul>
 *   <li>{@code tearDown()} idempotency — double call must be a no-op and never throw.</li>
 *   <li>{@code isGwtBaseClass()} — both GWT package prefixes must be recognised.</li>
 *   <li>{@code hasInjectMocksField()} — presence / absence of {@code @InjectMocks}.</li>
 *   <li>{@code hasInjectMocksTargetExtendingGwtBase()} — target-type hierarchy walk.</li>
 * </ul>
 *
 * <p>All helpers are tested via reflection so that this file does not grow a public API that
 * should not exist.
 */
public class GwtMockitoInternalsTest {

  // ── tearDown idempotency ──────────────────────────────────────────────────

  @BeforeEach
  public void setUp() {
    GwtMockito.initMocks(this);
  }

  @AfterEach
  public void tearDown() {
    GwtMockito.tearDown();
  }

  @Test
  public void tearDownIsIdempotent_doubleCallDoesNotThrow() {
    // First tearDown() closes the session opened by setUp()'s initMocks().
    GwtMockito.tearDown();
    // Second call: openMocksCloseable is now null — must not throw.
    GwtMockito.tearDown();
    // Re-open a session so that the @After tearDown() has something to close.
    GwtMockito.initMocks(this);
  }

  // ── isGwtBaseClass ────────────────────────────────────────────────────────

  private static boolean isGwtBaseClass(Class<?> clazz) throws Exception {
    Method m = GwtMockito.class.getDeclaredMethod("isGwtBaseClass", Class.class);
    m.setAccessible(true);
    return (Boolean) m.invoke(null, clazz);
  }

  @Test
  public void isGwtBaseClass_recognisesComGoogleGwtPrefix() throws Exception {
    // Widget lives in com.google.gwt.user.client.ui — must be recognised.
    assertTrue(isGwtBaseClass(Widget.class), "com.google.gwt.* classes must be GWT base classes");
  }

  @Test
  public void isGwtBaseClass_recognisesOrgGwtprojectPrefix() throws Exception {
    // GwtprojectFixture is declared in org.gwtproject.fixture — a real class in the
    // org.gwtproject.* namespace, so isGwtBaseClass must return true.
    assertTrue(isGwtBaseClass(GwtprojectFixture.class), "class in org.gwtproject.* must be a GWT base class");
  }

  @Test
  public void isGwtBaseClass_falseForNonGwtClass() throws Exception {
    assertFalse(isGwtBaseClass(String.class), "java.lang.String must not be a GWT base class");
    assertFalse(isGwtBaseClass(GwtMockitoInternalsTest.class), "a class in com.google.gwtmockito must not be a GWT base class");
  }

  @Test
  public void isGwtBaseClass_compositeIsGwtBase() throws Exception {
    // Composite is in com.google.gwt.user.client.ui — must match.
    assertTrue(isGwtBaseClass(Composite.class), "Composite must be recognised as a GWT base class");
  }

  // ── hasInjectMocksField ───────────────────────────────────────────────────

  private static boolean hasInjectMocksField(Object owner) throws Exception {
    Method m = GwtMockito.class.getDeclaredMethod("hasInjectMocksField", Object.class);
    m.setAccessible(true);
    return (Boolean) m.invoke(null, owner);
  }

  /** Owner with an {@code @InjectMocks} field. */
  static class OwnerWithInjectMocks {
    @InjectMocks Composite view;
  }

  /** Owner with no annotation on its field. */
  static class OwnerWithoutInjectMocks {
    Object plain;
  }

  /** Base class carrying {@code @InjectMocks}; subclass does not. */
  static class BaseOwnerWithInjectMocks {
    @InjectMocks Composite baseView;
  }

  static class SubOwnerNoInjectMocks extends BaseOwnerWithInjectMocks {
    Object plain;
  }

  @Test
  public void hasInjectMocksField_trueWhenFieldPresent() throws Exception {
    assertTrue(hasInjectMocksField(new OwnerWithInjectMocks()), "Owner with @InjectMocks must return true");
  }

  @Test
  public void hasInjectMocksField_falseWhenNoAnnotation() throws Exception {
    assertFalse(hasInjectMocksField(new OwnerWithoutInjectMocks()), "Owner without @InjectMocks must return false");
  }

  @Test
  public void hasInjectMocksField_trueWhenAnnotationOnSuperclass() throws Exception {
    // The walk must climb the hierarchy to find @InjectMocks declared on the base class.
    assertTrue(hasInjectMocksField(new SubOwnerNoInjectMocks()), "@InjectMocks on superclass must still return true from subclass instance");
  }

  // ── hasInjectMocksTargetExtendingGwtBase ──────────────────────────────────

  private static boolean hasInjectMocksTargetExtendingGwtBase(Object owner) throws Exception {
    Method m = GwtMockito.class.getDeclaredMethod(
        "hasInjectMocksTargetExtendingGwtBase", Object.class);
    m.setAccessible(true);
    return (Boolean) m.invoke(null, owner);
  }

  /** Owner whose @InjectMocks target extends a GWT base class. */
  static class OwnerWithGwtTarget {
    @InjectMocks Composite view;
  }

  /** Owner whose @InjectMocks target is a plain non-GWT class. */
  static class PlainTarget {
    String value;
  }

  static class OwnerWithPlainTarget {
    @InjectMocks PlainTarget target;
  }

  /** Owner whose @InjectMocks target transitively extends a GWT class (Widget → UIObject). */
  static class UserWidget extends Widget {}

  static class OwnerWithUserWidgetTarget {
    @InjectMocks UserWidget widget;
  }

  @Test
  public void hasInjectMocksTargetExtendingGwtBase_trueForDirectGwtTarget() throws Exception {
    assertTrue(hasInjectMocksTargetExtendingGwtBase(new OwnerWithGwtTarget()), "Composite target must be detected as GWT base");
  }

  @Test
  public void hasInjectMocksTargetExtendingGwtBase_falseForPlainTarget() throws Exception {
    assertFalse(hasInjectMocksTargetExtendingGwtBase(new OwnerWithPlainTarget()), "Plain (non-GWT) target must return false");
  }

  @Test
  public void hasInjectMocksTargetExtendingGwtBase_trueForIndirectGwtTarget() throws Exception {
    // UserWidget extends Widget (a GWT base class) — the superclass walk must find it.
    assertTrue(hasInjectMocksTargetExtendingGwtBase(new OwnerWithUserWidgetTarget()), "Target that transitively extends Widget must return true");
  }

  @Test
  public void hasInjectMocksTargetExtendingGwtBase_falseForNoInjectMocksField() throws Exception {
    assertFalse(hasInjectMocksTargetExtendingGwtBase(new OwnerWithoutInjectMocks()), "Owner with no @InjectMocks field must return false");
  }

  // ── isMockField ──────────────────────────────────────────────────────────

  private static boolean isMockField(java.lang.reflect.Field field) throws Exception {
    Method m = GwtMockito.class.getDeclaredMethod("isMockField", java.lang.reflect.Field.class);
    m.setAccessible(true);
    return (Boolean) m.invoke(null, field);
  }

  static class HolderWithMock {
    @Mock Object mockField;
    Object plainField;
    @GwtMock SampleGwtInterface gwtMockField;
  }

  interface SampleGwtInterface {}

  @Test
  public void isMockField_trueForMockAnnotatedField() throws Exception {
    java.lang.reflect.Field f = HolderWithMock.class.getDeclaredField("mockField");
    assertTrue(isMockField(f), "@Mock field must return true");
  }

  @Test
  public void isMockField_trueForGwtMockAnnotatedField() throws Exception {
    java.lang.reflect.Field f = HolderWithMock.class.getDeclaredField("gwtMockField");
    assertTrue(isMockField(f), "@GwtMock field must return true");
  }

  @Test
  public void isMockField_falseForPlainField() throws Exception {
    java.lang.reflect.Field f = HolderWithMock.class.getDeclaredField("plainField");
    assertFalse(isMockField(f), "Unannotated field must return false");
  }

  @Test
  public void isMockField_falseForInjectMocksField() throws Exception {
    java.lang.reflect.Field f = OwnerWithInjectMocks.class.getDeclaredField("view");
    // @InjectMocks must NOT trigger isMockField — only @Mock / @GwtMock should.
    assertFalse(isMockField(f), "@InjectMocks field must return false from isMockField");
  }
}
