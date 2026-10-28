## What is GwtMockito?

Testing GWT applications using `GWTTestCase` can be a pain - it's slower than
using pure Java tests, and you can't use reflection-based tools like mocking
frameworks. But if you've tried to test widgets using normal test cases, you've
probably run into this error:

    ERROR: GWT.create() is only usable in client code!  It cannot be called,
    for example, from server code. If you are running a unit test, check that 
    your test case extends GWTTestCase and that GWT.create() is not called
    from within an initializer or constructor.

GwtMockito solves this and other GWT-related testing problems by allowing you
to call GWT.create from JUnit tests, returning [Mockito][1] mocks.

> **This is a fork** of the original [google/gwtmockito][orig], which is
> no longer maintained. This fork modernizes the project with:
> - **JUnit 5 (Jupiter)** support via `GwtMockitoExtension`, built on JUnit 6
> - **Mockito 5** compatibility (replaces 1.x)
> - **GWT 2.13.1** (`org.gwtproject`) — replaces the archived `com.google.gwt` 2.8.0
> - **Java 17** source/target
> - Full Mockito session lifecycle management (`openMocks` / `tearDown`)
> - Automatic recovery from Mockito 5's `@InjectMocks` ambiguity errors on GWT base classes

## How do I install it?

GwtMockito is published to Maven Central:

```xml
<dependency>
  <groupId>io.github.themarioga</groupId>
  <artifactId>gwtmockito</artifactId>
  <version>2.0.0</version>
  <scope>test</scope>
</dependency>
```

The Java packages are still `com.google.gwtmockito`, so code written for the
original GwtMockito only needs the new Maven coordinates. Don't put
`com.google.gwt.gwtmockito:gwtmockito` on the same classpath: both artifacts
contain the same classes.

`junit:junit` is no longer a transitive dependency. If you keep using the JUnit 4
runner, declare JUnit 4 in your own project.

## How do I use it?

Getting started with GwtMockito using JUnit 5 is easy. Just annotate your test
with `@ExtendWith(GwtMockitoExtension.class)`, then any calls to `GWT.create`
encountered will return Mockito mocks instead of throwing exceptions:

```java
@ExtendWith(GwtMockitoExtension.class)
class MyTest {
  @Test
  void shouldReturnMocksFromGwtCreate() {
    Label myLabel = GWT.create(Label.class);
    when(myLabel.getText()).thenReturn("some text");
    assertEquals("some text", myLabel.getText());
  }
}
```

GwtMockito also creates fake implementations of all UiBinders that automatically
populate `@UiField`s with Mockito mocks. Suppose you have a widget that looks
like this:

```java
public class MyWidget extends Composite {
  interface MyUiBinder extends UiBinder<Widget, MyWidget> {}
  private final MyUiBinder uiBinder = GWT.create(MyUiBinder.class);

  @UiField Label numberLabel;
  private final NumberFormatter formatter;

  public MyWidget(NumberFormatter formatter) {
    this.formatter = formatter;
    initWidget(uiBinder.createAndBindUi(this));
  }

  void setNumber(int number) {
    numberLabel.setText(formatter.format(number));
  }
}
```

When `createAndBindUi` is called, GwtMockito will automatically populate 
`numberLabel` with a mock object. Since `@UiField`s are package-visible, they 
can be read from your unit tests, which lets you test this widget as follows:

```java
@ExtendWith(GwtMockitoExtension.class)
class MyWidgetTest {

  @Mock NumberFormatter formatter;
  private MyWidget widget;

  @BeforeEach
  void setUp() {
    widget = new MyWidget(formatter);
  }

  @Test
  void shouldFormatNumber() {
    when(formatter.format(5)).thenReturn("5.00");
    widget.setNumber(5);
    verify(widget.numberLabel).setText("5.00");
  }
}
```

Note that GwtMockito supports the `@Mock` annotation from Mockito, allowing 
standard Mockito mocks to be mixed with mocks created by GwtMockito.

### Accessing the mock returned from GWT.create

You can reference a mock returned by `GWT.create` by annotating a field with
`@GwtMock`. For example:

```java
@ExtendWith(GwtMockitoExtension.class)
class MyClassTest {
  @GwtMock SomeInterface mockInterface;

  @Test
  void constructorShouldSetSomething() {
    new MyClass();
    verify(mockInterface).setSomething(true);
  }
}
```

### Returning fake objects

By default, GwtMockito will return fake implementations for any classes extending:

  * UiBinder
  * ClientBundle
  * Messages
  * CssResource
  * SafeHtmlTemplates

You can add fakes for additional types by invoking 
`GwtMockito.useProviderForType(Class, FakeProvider)` in your `setUp` method.

### Mocking final classes and methods

GwtMockito removes all final modifiers from classes and interfaces via its
Javassist classloader, allowing `Element` and other JavaScript overlay types
to be mocked:

```java
@ExtendWith(GwtMockitoExtension.class)
class MyTest {
  @Mock Element element;

  @Test
  void shouldMockFinalMethod() {
    when(element.getClassName()).thenReturn("mockClass");
    assertEquals("mockClass", element.getClassName());
  }
}
```

### Dealing with native methods

GwtMockito provides no-op implementations for all native JSNI methods:

  * `void` methods do nothing.
  * Methods returning primitive types return the default value (0, false, etc.)
  * Methods returning `String`s return the empty string.
  * Methods returning other objects return a mock configured with `RETURNS_MOCKS`.

### Customizing the extension

Use `@WithClassesToStub`, `@WithPackagesToLoadViaStandardClassLoader` and
`@WithExperimentalGarbageCollection` on the test class, or subclass
`GwtMockitoExtension` and override `getClassesToStub(Class<?>)`,
`getPackagesToLoadViaStandardClassloader(Class<?>)`, `getParentClassloader()` or
`getAdditionalClasspaths()`, then register your subclass with
`@ExtendWith(MyGwtMockitoExtension.class)`. `@ExtendWith` on a base class is
inherited by its subclasses.

### JUnit 5 limitations

The extension reloads the test class through GwtMockito's classloader and runs
every test, lifecycle, `@ParameterizedTest`, `@RepeatedTest` and `@TestFactory`
method on the reloaded class. As a consequence:

  * `@Nested` classes, `@TestInstance(Lifecycle.PER_CLASS)` and combining the
    extension with Mockito's `MockitoExtension` are not supported; the container
    fails with an explicit message. `GwtMockitoExtension` already initializes
    `@Mock` fields.
  * Arguments of parameterized tests are created outside GwtMockito's
    classloader. Use JDK types (primitives, `String`, collections...) and create
    GWT objects or mocks inside the test. Arguments of GWT types or of your own
    test classes fail the invocation (or fail in the argument factory itself, if
    it calls `GWT.create` or mocks a GWT class).
  * The test class constructor and field initializers run only on the reloaded
    instance. Extensions that inject fields see the instance created by JUnit,
    not the reloaded one; parameter injection (e.g. `TestInfo`) works.
  * Parallel execution is not supported, since GwtMockito keeps static state.

### JUnit 4 (deprecated)

`GwtMockitoTestRunner` still works for existing JUnit 4 suites, but it is
deprecated in favour of `GwtMockitoExtension`. Annotate the test with
`@RunWith(GwtMockitoTestRunner.class)` and declare `junit:junit` yourself. On the
JUnit Platform, run it with `junit-vintage-engine`.

### Support for JUnit 3 and other tests that can't use custom runners

You can use GwtMockito without `GwtMockitoTestRunner` by calling
`GwtMockito.initMocks` and `GwtMockito.tearDown` directly:

```java
public class MyWidgetTest extends TestCase {

  @Override
  public void setUp() {
    super.setUp();
    GwtMockito.initMocks(this);
  }

  @Override
  public void tearDown() {
    super.tearDown();
    GwtMockito.tearDown();
  }

  public void testSomething() {
    // test code
  }
}
```

Note that when testing in this way, mocking final classes/methods and automatic
native method stubs are not available.

## Releasing

Releases are published to Maven Central by `.github/workflows/release.yml` when a
tag `vMAJOR.MINOR.PATCH` (e.g. `v2.0.0`) is pushed. Other tags, such as
`v2.0.0-RC1`, are ignored. The workflow sets the version from the tag without
committing it, so `main` keeps its `-SNAPSHOT` version.

One-time setup:

  1. Sign in to [central.sonatype.com](https://central.sonatype.com) with the
     `themarioga` GitHub account; the `io.github.themarioga` namespace is
     verified automatically.
  2. Generate a user token in the Central Portal and store it as the repository
     secrets `CENTRAL_USERNAME` and `CENTRAL_TOKEN`.
  3. Create a GPG signing key, publish its public key to a keyserver
     (e.g. `gpg --keyserver keys.openpgp.org --send-keys <KEY_ID>`), and store the
     armored private key and its passphrase as `GPG_PRIVATE_KEY` and
     `GPG_PASSPHRASE`.

Anyone allowed to push tags to the repository can publish a release; restrict
this with a tag ruleset if needed. Published versions are immutable: a tag for an
existing version makes the workflow fail, so release a new version instead. If the
workflow times out waiting for Central, check the deployment state in the Central
Portal before doing anything else.

To check a release build locally without uploading:
`mvn -P release deploy -DskipUpload=true` (needs a local GPG key).

## Version history

### 2.0.0
  * Published to Maven Central as `io.github.themarioga:gwtmockito` (Java packages unchanged).
  * New `GwtMockitoExtension` for JUnit 5 (Jupiter), built against JUnit 6, with the same
    behaviour as the runner (see the limitations above).
  * `GwtMockitoTestRunner` is deprecated; `junit:junit` is now an optional dependency.
  * The `ClassCastException` hint now recognizes Mockito 5 mocks in the JUnit 5 extension.
  * Upgraded to Mockito 5.
  * Upgraded to GWT 2.13.1 (`org.gwtproject`).
  * Upgraded to Java 17.
  * `tearDown()` now properly closes the Mockito session opened by `openMocks()`.
  * `GwtMockitoTestRunner` now calls `tearDown()` automatically after each test.
  * Fixed `@InjectMocks` on targets extending GWT base classes (`Composite`, `Widget`)
    that triggered Mockito 5's ambiguity error on inherited private fields.
  * Fixed subclass `@Mock` field priority over same-named superclass fields.
  * Fixed `TypeVariable` field injection in generic base view classes.
  * Replaced deprecated `org.mockito.Matchers` with `org.mockito.ArgumentMatchers`.
  * Dropped PowerMock test dependency (incompatible with Mockito 5).
  * Applied Java 17 API throughout: `instanceof` pattern matching, `Set.of`/`Map.of`
    factory methods, arrow-case `switch`, Stream API — no behaviour changes.
  * Eliminated `LinkedList` in favour of `ArrayList`; deduplicated `@Mock`/`@GwtMock`
    field detection into a shared `isMockField()` helper.
  * Fixed `field.getAnnotations()` → `getDeclaredAnnotations()` and hierarchy-walk
    termination to use identity check (`clazz != Object.class`).
  * Cached `getClassesToStub()` per class load in the Javassist translator to avoid
    repeated allocations during method stubbing.
  * Added unit tests covering `tearDown` idempotency, `isGwtBaseClass`,
    `hasInjectMocksField`, `hasInjectMocksTargetExtendingGwtBase`, and `isMockField`.

### 1.1.9
  * Support ResourcePrototype methods in fake ClientBundles. (Thanks to zbynek)
  * Add a `@WithExperimentalGarbageCollection` annotation. (Thanks to LudoP)
  * Updated Javassist dependency. (Thanks to TimvdLippe)

### 1.1.8
  * Preliminary Java 9 support. (Thanks to benoitf)

### 1.1.7
  * Update GWT to version 2.8.0.
  * Update Javassist to version 3.22.
  * Stubbing for ValueListBox. (Thanks to jschmied)
  * Stubbing for URL encoding. (Thanks to jschmied)
  * Generate hashCode and equals for Messages. (Thanks to zolv)

### 1.1.6 and earlier
  * See the [original project changelog][orig].

[1]: https://site.mockito.org/
[orig]: https://github.com/google/gwtmockito
