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
import static org.mockito.Mockito.verifyNoInteractions;

import com.google.gwt.core.client.GWT;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.testkit.engine.EngineExecutionResults;

import java.util.List;
import java.util.stream.Stream;

/** Parameterized, repeated and dynamic tests under {@link GwtMockitoExtension}. */
class GwtMockitoExtensionParameterizedTest {

  interface Sample {
    String value();
  }

  @Test
  void runsEveryInvocationWithFreshMocks() {
    EngineExecutionResults results = execute(InvocationsFixture.class);

    results.containerEvents().assertStatistics(stats -> stats.failed(0));
    // 3 parameterized + 3 repeated + 2 dynamic + 2 method-source strings
    results.testEvents().assertStatistics(stats -> stats.started(10).succeeded(10));
  }

  @Test
  void failsInvocationsWhoseArgumentsWereLoadedOutsideGwtMockito() {
    EngineExecutionResults results = execute(TestSideArgumentFixture.class);

    results.testEvents().assertStatistics(stats -> stats.started(1).failed(1));
    Throwable failure = results.testEvents().failed().stream().findFirst().orElseThrow()
        .getRequiredPayload(TestExecutionResult.class).getThrowable().orElseThrow();
    assertInstanceOf(ParameterResolutionException.class, failure);
    assertTrue(failure.getMessage().contains("Argument 0"), failure.getMessage());
    assertTrue(failure.getMessage().contains(TestSideArgumentFixture.Pojo.class.getName()),
        failure.getMessage());
    assertTrue(failure.getMessage().contains("not supported by GwtMockitoExtension"),
        failure.getMessage());
  }

  @ExtendWith(GwtMockitoExtension.class)
  static class InvocationsFixture {
    @GwtMock Sample sample;

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3})
    void parameterized(int value) {
      verifyNoInteractions(sample);
      sample.value();
    }

    @RepeatedTest(3)
    void repeated() {
      verifyNoInteractions(sample);
      sample.value();
    }

    @TestFactory
    Stream<DynamicTest> dynamic() {
      return Stream.of("first", "second").map(name -> DynamicTest.dynamicTest(name,
          () -> assertSame(sample, GWT.create(Sample.class))));
    }

    static Stream<String> strings() {
      return Stream.of("a", "b");
    }

    @ParameterizedTest
    @MethodSource("strings")
    void methodSourceStrings(String value) {
      assertTrue(List.of("a", "b").contains(value), value);
    }
  }

  @ExtendWith(GwtMockitoExtension.class)
  static class TestSideArgumentFixture {
    static class Pojo {}

    static Stream<Pojo> pojos() {
      return Stream.of(new Pojo());
    }

    @ParameterizedTest
    @MethodSource("pojos")
    void pojo(Pojo pojo) {
      assertEquals(0, 1, "must not run");
    }
  }
}
