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
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.testkit.engine.EngineExecutionResults;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.stream.Collectors;

/** Out-of-scope Jupiter features fail explicitly under {@link GwtMockitoExtension}. */
class GwtMockitoExtensionUnsupportedFeaturesTest {

  @Test
  void failsNestedContainersButRunsTheOuterClass() {
    EngineExecutionResults results = execute(NestedFixture.class);

    results.testEvents().assertStatistics(stats -> stats.started(1).succeeded(1));
    assertSingleContainerFailure(results, "@Nested");
  }

  @Test
  void failsPerClassLifecycle() {
    EngineExecutionResults results = execute(PerClassFixture.class);

    results.testEvents().assertStatistics(stats -> stats.started(0));
    assertSingleContainerFailure(results, "@TestInstance(PER_CLASS)");
  }

  @Test
  void failsWhenCombinedWithMockitoExtension() {
    EngineExecutionResults results = execute(MockitoExtensionFixture.class);

    results.testEvents().assertStatistics(stats -> stats.started(0));
    assertSingleContainerFailure(results, "MockitoExtension");
  }

  private static void assertSingleContainerFailure(EngineExecutionResults results, String feature) {
    List<String> messages = results.containerEvents().failed().stream()
        .map(event -> event.getRequiredPayload(TestExecutionResult.class)
            .getThrowable().orElseThrow().getMessage())
        .collect(Collectors.toList());
    assertEquals(1, messages.size(), messages.toString());
    assertTrue(messages.get(0).contains(feature), messages.get(0));
    assertTrue(messages.get(0).contains("not supported by GwtMockitoExtension"), messages.get(0));
  }

  @ExtendWith(GwtMockitoExtension.class)
  static class NestedFixture {
    @Test
    void outer() {}

    @Nested
    class Inner {
      @Test
      void inner() {}
    }
  }

  @ExtendWith(GwtMockitoExtension.class)
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  static class PerClassFixture {
    @Test
    void test() {}
  }

  @ExtendWith({GwtMockitoExtension.class, MockitoExtension.class})
  static class MockitoExtensionFixture {
    @Test
    void test() {}
  }
}
