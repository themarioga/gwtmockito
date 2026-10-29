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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;
import static org.mockito.Mockito.when;

import com.google.gwt.core.client.GWT;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.platform.testkit.engine.EngineExecutionResults;
import org.junit.platform.testkit.engine.EngineTestKit;
import org.mockito.Mock;

/** Lifecycle behaviour of {@link GwtMockitoExtension}, observed through the JUnit Platform. */
class GwtMockitoExtensionLifecycleTest {

  interface Sample {
    String value();
  }

  @Test
  void runsLifecycleAndGivesEachTestFreshMocks() {
    ClassLoader before = Thread.currentThread().getContextClassLoader();

    EngineExecutionResults results = execute(LifecycleFixture.class);

    results.containerEvents().assertStatistics(stats -> stats.failed(0));
    results.testEvents().assertStatistics(stats -> stats.started(4).succeeded(3).failed(1));
    assertEquals("intentional",
        results.testEvents().failed().stream().findFirst().orElseThrow()
            .getRequiredPayload(org.junit.platform.engine.TestExecutionResult.class)
            .getThrowable().orElseThrow().getMessage());
    assertSame(before, Thread.currentThread().getContextClassLoader());
  }

  @Test
  void skipsDisabledTestsAndAbortsOnFailedAssumptionsAfterTearingDown() {
    EngineExecutionResults results = execute(SkippedAndAbortedFixture.class);

    results.containerEvents().assertStatistics(stats -> stats.failed(0));
    results.testEvents().assertStatistics(stats ->
        stats.skipped(1).started(2).succeeded(1).aborted(1).failed(0));
  }

  static EngineExecutionResults execute(Class<?> fixture) {
    return EngineTestKit.engine("junit-jupiter").selectors(selectClass(fixture)).execute();
  }

  // Every assertion below runs on the reloaded class: a violation shows up as a failed test or a
  // failed container in the outer test.
  @ExtendWith(GwtMockitoExtension.class)
  @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
  static class LifecycleFixture {
    static String setInBeforeAll;
    static int afterEachCount;

    @Mock Runnable runnable;
    @GwtMock Sample sample;
    boolean beforeEachRan;

    @BeforeAll
    static void beforeAll() {
      setInBeforeAll = "set";
    }

    @BeforeEach
    void beforeEach() {
      assertNotNull(runnable, "@Mock fields must be initialized before @BeforeEach");
      beforeEachRan = true;
    }

    @Test
    @Order(1)
    void stubsAGwtMockAndFails() {
      when(sample.value()).thenReturn("stubbed");
      fail("intentional");
    }

    @Test
    @Order(2)
    void seesFreshMocksAfterAFailedTest() {
      assertNull(sample.value());
      assertSame(sample, GWT.create(Sample.class));
    }

    @Test
    @Order(3)
    void runsBeforeAllAndBeforeEachOnTheReloadedClass() {
      assertTrue(beforeEachRan);
      assertEquals("set", setInBeforeAll);
    }

    @Test
    @Order(4)
    void usesGwtMockitoClassLoaderAsContextClassLoader() {
      ClassLoader loader = getClass().getClassLoader();
      assertEquals("com.google.gwtmockito.impl.GwtMockitoClassLoader", loader.getClass().getName());
      assertSame(loader, Thread.currentThread().getContextClassLoader());
    }

    @AfterEach
    void afterEach() {
      afterEachCount++;
    }

    @AfterAll
    static void afterAll() {
      assertEquals(4, afterEachCount);
    }
  }

  @ExtendWith(GwtMockitoExtension.class)
  @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
  static class SkippedAndAbortedFixture {
    @GwtMock Sample sample;

    @Test
    @Order(1)
    @Disabled("AC-24")
    void disabled() {
      fail("must not run");
    }

    @Test
    @Order(2)
    void passes() {
      assertSame(sample, GWT.create(Sample.class));
    }

    @Test
    @Order(3)
    void aborted() {
      Assumptions.assumeTrue(false);
    }

    @AfterAll
    static void tearDownRanAfterTheAbortedTest() {
      assertThrows(UnsupportedOperationException.class, () -> GWT.create(Sample.class));
    }
  }
}
