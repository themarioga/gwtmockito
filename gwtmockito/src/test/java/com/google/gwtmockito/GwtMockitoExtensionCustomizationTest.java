/*
 * Copyright 2026 YCM
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

import static com.google.gwtmockito.GwtMockitoExtensionLifecycleTest.execute;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.when;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Element;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;

import java.util.Collection;

/** Customizing {@link GwtMockitoExtension} by subclassing it and by inheriting it. */
class GwtMockitoExtensionCustomizationTest {

  interface Sample {
    String value();
  }

  @Test
  void stubsClassesAddedBySubclassHook() {
    execute(SubclassedExtensionFixture.class).testEvents()
        .assertStatistics(stats -> stats.started(1).succeeded(1));
  }

  @Test
  void appliesExtensionDeclaredOnAnAbstractBaseClass() {
    execute(InheritingFixture.class).testEvents()
        .assertStatistics(stats -> stats.started(2).succeeded(2));
  }

  static class ClassToStub {
    String doSomething() {
      throw new UnsupportedOperationException("must be stubbed");
    }
  }

  public static class StubbingExtension extends GwtMockitoExtension {
    @Override
    protected Collection<Class<?>> getClassesToStub(Class<?> testClass) {
      Collection<Class<?>> classes = super.getClassesToStub(testClass);
      classes.add(ClassToStub.class);
      return classes;
    }
  }

  @ExtendWith(StubbingExtension.class)
  static class SubclassedExtensionFixture {
    @Test
    void stubbedClassReturnsFakeValue() {
      assertEquals("", new ClassToStub().doSomething());
    }
  }

  @ExtendWith(GwtMockitoExtension.class)
  abstract static class AbstractFixture {
    @GwtMock Sample sample;
  }

  static class InheritingFixture extends AbstractFixture {
    @Mock Element element;

    @Test
    void gwtCreateReturnsInheritedGwtMock() {
      assertSame(sample, GWT.create(Sample.class));
    }

    @Test
    void finalMethodsAreMockable() {
      when(element.getClassName()).thenReturn("mockClass");
      assertEquals("mockClass", element.getClassName());
    }
  }
}
