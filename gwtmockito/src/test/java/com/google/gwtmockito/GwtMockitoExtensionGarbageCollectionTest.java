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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.google.gwtmockito.subpackage.ThreadLocalHolder;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.ref.WeakReference;

/**
 * {@link WithExperimentalGarbageCollection} under {@link GwtMockitoExtension}. A value left in a
 * ThreadLocal of the test thread keeps the reloaded classes, and so GwtMockito's classloader, alive
 * unless the extension cleans it up.
 */
class GwtMockitoExtensionGarbageCollectionTest {

  // System properties are shared by both classloaders, so the fixture can hand its loader out.
  private static final String LOADER_KEY = GwtMockitoExtensionGarbageCollectionTest.class.getName();

  @AfterEach
  void clearLoaderReference() {
    System.getProperties().remove(LOADER_KEY);
  }

  @Test
  void releasesTheClassLoaderWhenAnnotated() throws InterruptedException {
    execute(CollectedFixture.class);

    assertNull(collect(), "Expected GwtMockito's classloader to be garbage collected");
  }

  @Test
  void keepsTheClassLoaderWithoutTheAnnotation() throws InterruptedException {
    execute(LeakingFixture.class);

    assertNotNull(collect(), "Without the annotation the ThreadLocal value keeps the classloader");
  }

  @SuppressWarnings("unchecked")
  private static ClassLoader collect() throws InterruptedException {
    WeakReference<ClassLoader> loader =
        (WeakReference<ClassLoader>) System.getProperties().remove(LOADER_KEY);
    assertNotNull(loader, "fixture did not run");
    for (int i = 0; i < 10 && loader.get() != null; i++) {
      System.gc();
      Thread.sleep(50);
    }
    return loader.get();
  }

  static void storeThreadLocalAndPublishLoader(Object fixture) {
    ThreadLocalHolder.store();
    System.getProperties().put(LOADER_KEY, new WeakReference<>(fixture.getClass().getClassLoader()));
  }

  @ExtendWith(GwtMockitoExtension.class)
  @WithExperimentalGarbageCollection
  static class CollectedFixture {
    @Test
    void leavesAThreadLocalValue() {
      storeThreadLocalAndPublishLoader(this);
    }
  }

  @ExtendWith(GwtMockitoExtension.class)
  static class LeakingFixture {
    @Test
    void leavesAThreadLocalValue() {
      storeThreadLocalAndPublishLoader(this);
    }
  }
}
