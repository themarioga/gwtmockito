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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.platform.engine.TestExecutionResult;
import org.opentest4j.AssertionFailedError;

import java.lang.reflect.Field;

/** How {@link GwtMockitoExtension} reports test and teardown failures. */
class GwtMockitoExtensionFailureHandlingTest {

  @Test
  void keepsTheTestFailureAndSuppressesTheTearDownFailure() {
    Throwable failure = singleFailure(TearDownFailureFixture.class);

    assertInstanceOf(AssertionFailedError.class, failure);
    assertEquals("primary", failure.getMessage());
    assertEquals(1, failure.getSuppressed().length);
    Throwable suppressed = failure.getSuppressed()[0];
    assertEquals("teardown bang", suppressed.getCause().getMessage());
  }

  @Test
  void explainsClassCastExceptionsInvolvingMockitoMocks() {
    Throwable failure = singleFailure(MockCastFixture.class);

    assertEquals("FailedCastException", failure.getClass().getSimpleName());
    assertTrue(failure.getMessage().contains("JavaScriptObject.cast()"), failure.getMessage());
    assertInstanceOf(ClassCastException.class, failure.getCause());
    assertEquals(MockCastFixture.MESSAGE, failure.getCause().getMessage());
  }

  @Test
  void reportsOtherClassCastExceptionsUnchanged() {
    Throwable failure = singleFailure(PlainCastFixture.class);

    assertSame(ClassCastException.class, failure.getClass());
    assertEquals(PlainCastFixture.MESSAGE, failure.getMessage());
  }

  private static Throwable singleFailure(Class<?> fixture) {
    return execute(fixture).testEvents().failed().stream().findFirst().orElseThrow()
        .getRequiredPayload(TestExecutionResult.class).getThrowable().orElseThrow();
  }

  @ExtendWith(GwtMockitoExtension.class)
  static class TearDownFailureFixture {
    @Test
    void failsAndBreaksTearDown() throws Exception {
      // This is the reloaded GwtMockito, the one whose tearDown the extension invokes.
      Field closeable = GwtMockito.class.getDeclaredField("openMocksCloseable");
      closeable.setAccessible(true);
      closeable.set(null, (AutoCloseable) () -> {
        throw new IllegalStateException("teardown bang");
      });
      throw new AssertionFailedError("primary");
    }
  }

  @ExtendWith(GwtMockitoExtension.class)
  static class MockCastFixture {
    static final String MESSAGE =
        "class Foo$MockitoMock$1234 cannot be cast to class com.google.gwt.dom.client.Element";

    @Test
    void castsAMock() {
      throw new ClassCastException(MESSAGE);
    }
  }

  @ExtendWith(GwtMockitoExtension.class)
  static class PlainCastFixture {
    static final String MESSAGE = "class java.lang.String cannot be cast to class java.lang.Integer";

    @Test
    void castsSomethingElse() {
      throw new ClassCastException(MESSAGE);
    }
  }
}
