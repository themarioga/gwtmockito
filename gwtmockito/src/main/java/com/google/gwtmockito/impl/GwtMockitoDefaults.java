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

import com.google.gwt.user.cellview.client.CellList;
import com.google.gwt.user.cellview.client.CellTable;
import com.google.gwt.user.cellview.client.DataGrid;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.ui.AbsolutePanel;
import com.google.gwt.user.client.ui.CellPanel;
import com.google.gwt.user.client.ui.ComplexPanel;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.DeckLayoutPanel;
import com.google.gwt.user.client.ui.DeckPanel;
import com.google.gwt.user.client.ui.DecoratorPanel;
import com.google.gwt.user.client.ui.DockLayoutPanel;
import com.google.gwt.user.client.ui.DockPanel;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.FocusPanel;
import com.google.gwt.user.client.ui.HTMLPanel;
import com.google.gwt.user.client.ui.HTMLTable;
import com.google.gwt.user.client.ui.HorizontalPanel;
import com.google.gwt.user.client.ui.Image;
import com.google.gwt.user.client.ui.LayoutPanel;
import com.google.gwt.user.client.ui.Panel;
import com.google.gwt.user.client.ui.PopupPanel;
import com.google.gwt.user.client.ui.RenderablePanel;
import com.google.gwt.user.client.ui.ResizeLayoutPanel;
import com.google.gwt.user.client.ui.SimpleLayoutPanel;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.SplitLayoutPanel;
import com.google.gwt.user.client.ui.StackPanel;
import com.google.gwt.user.client.ui.UIObject;
import com.google.gwt.user.client.ui.ValueListBox;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.google.gwt.user.client.ui.Widget;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Defaults shared by {@link com.google.gwtmockito.GwtMockitoTestRunner} and
 * {@link com.google.gwtmockito.GwtMockitoExtension}. Each call returns a new mutable list so callers
 * can append their own entries.
 */
public final class GwtMockitoDefaults {

  private GwtMockitoDefaults() {}

  public static List<Class<?>> classesToStub() {
    return new ArrayList<>(Arrays.asList(
        Composite.class,
        DOM.class,
        UIObject.class,
        Widget.class,
        DataGrid.class,
        HTMLTable.class,
        Image.class,
        AbsolutePanel.class,
        CellList.class,
        CellPanel.class,
        CellTable.class,
        ComplexPanel.class,
        DeckLayoutPanel.class,
        DeckPanel.class,
        DecoratorPanel.class,
        DockLayoutPanel.class,
        DockPanel.class,
        FlowPanel.class,
        FocusPanel.class,
        HorizontalPanel.class,
        HTMLPanel.class,
        LayoutPanel.class,
        Panel.class,
        PopupPanel.class,
        RenderablePanel.class,
        ResizeLayoutPanel.class,
        SimpleLayoutPanel.class,
        SimplePanel.class,
        SplitLayoutPanel.class,
        StackPanel.class,
        VerticalPanel.class,
        ValueListBox.class
    ));
  }

  public static List<String> packagesToLoadViaStandardClassloader() {
    return new ArrayList<>(Arrays.asList(
        "com.vladium",           // To support EMMA code coverage tools
        "jdk.internal.reflect",  // Java 9+ loading mechanism
        "net.bytebuddy",         // Required by Mockito 5's InlineByteBuddyMockMaker
        "net.sf.cglib",          // To support Mockito 1
        "net.sourceforge.cobertura", // To support Cobertura code coverage tools
        "org.jacoco",            // To support JaCoCo code coverage tools
        "org.hamcrest",          // Since this package is referenced directly from org.junit
        "org.junit",             // Make sure the ParentRunner can recognize annotations like @Test
        "org.mockito"            // Mockito 5 injects MockMethodDispatcher via agent; must not be re-loaded by Javassist
    ));
  }

  /**
   * Packages that only the JUnit Jupiter integration must share with the standard classloader:
   * assertion failures thrown by reloaded tests must be the same types Jupiter reports, and the
   * instance handed to Jupiter is created through Objenesis.
   */
  public static List<String> jupiterPackagesToLoadViaStandardClassloader() {
    return new ArrayList<>(Arrays.asList(
        "org.apiguardian",
        "org.objenesis",
        "org.opentest4j"
    ));
  }
}
