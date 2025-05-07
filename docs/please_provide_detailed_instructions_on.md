
# Instrumenting JUnit 5 Tests to Identify Slow Methods

Slow test cases can significantly impact development productivity and CI/CD pipeline efficiency. Here's a comprehensive guide on how to instrument JUnit 5 tests to identify and evaluate slow test methods.

## 1. Using JUnit 5's Built-in Timeout Features

### 1.1 Using `@Timeout` Annotation

JUnit 5 provides a built-in `@Timeout` annotation that can be used to fail tests that exceed a specified duration:

```java
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.Test;
import java.util.concurrent.TimeUnit;

@Test
@Timeout(value = 500, unit = TimeUnit.MILLISECONDS)
void testMethod() {
    // Test code here
}
```

You can apply this annotation at the method level or at the class level to set a default timeout for all test methods in the class:

```java
@Timeout(value = 1, unit = TimeUnit.SECONDS)
public class MySlowTestClass {
    // All test methods will have a 1-second timeout
}
```

### 1.2 Using `assertTimeout` and `assertTimeoutPreemptively`

For more control, use the `assertTimeout` and `assertTimeoutPreemptively` methods:

```java
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import java.time.Duration;

@Test
void testWithAssertTimeout() {
    assertTimeout(Duration.ofMillis(500), () -> {
        // Test code here
    });
}

@Test
void testWithAssertTimeoutPreemptively() {
    assertTimeoutPreemptively(Duration.ofMillis(500), () -> {
        // Test code here
    });
}
```

The difference is that `assertTimeoutPreemptively` aborts execution if the timeout is exceeded, while `assertTimeout` lets the execution complete but marks the test as failed.

## 2. Creating a Custom JUnit 5 Extension for Performance Measurement

JUnit 5's extension model allows for powerful customizations. Here's how to create an extension that measures and reports test execution time:

### 2.1 Create a Test Execution Timer Extension

```java
import org.junit.jupiter.api.extension.*;
import java.lang.reflect.Method;
import java.util.logging.Logger;

public class TestExecutionTimeExtension implements BeforeTestExecutionCallback, AfterTestExecutionCallback {
    
    private static final Logger logger = Logger.getLogger(TestExecutionTimeExtension.class.getName());
    private static final long THRESHOLD_MS = 100; // Define your threshold for slow tests
    
    @Override
    public void beforeTestExecution(ExtensionContext context) {
        getStore(context).put("start_time", System.currentTimeMillis());
    }
    
    @Override
    public void afterTestExecution(ExtensionContext context) {
        Method testMethod = context.getRequiredTestMethod();
        long startTime = getStore(context).get("start_time", long.class);
        long duration = System.currentTimeMillis() - startTime;
        
        if (duration > THRESHOLD_MS) {
            logger.warning(String.format(
                "Slow test detected: %s.%s took %d ms (threshold: %d ms)",
                testMethod.getDeclaringClass().getSimpleName(),
                testMethod.getName(),
                duration,
                THRESHOLD_MS
            ));
        }
    }
    
    private ExtensionContext.Store getStore(ExtensionContext context) {
        return context.getStore(ExtensionContext.Namespace.create(getClass(), context.getRequiredTestMethod()));
    }
}
```

### 2.2 Apply the Extension to Your Tests

You can apply this extension to individual test methods, classes, or globally:

```java
// Method level
@ExtendWith(TestExecutionTimeExtension.class)
@Test
void slowTestMethod() {
    // Test code
}

// Class level
@ExtendWith(TestExecutionTimeExtension.class)
class SlowTestClass {
    // Test methods
}
```

### 2.3 Global Registration

To apply the extension globally, create a file named `org.junit.jupiter.api.extension.Extension` in the `META-INF/services` directory with the fully qualified name of your extension:

```
com.example.TestExecutionTimeExtension
```

## 3. Creating a Custom Annotation for Slow Test Detection

You can create a custom annotation that combines the extension with metadata:

```java
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import java.lang.annotation.*;

@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(TestExecutionTimeExtension.class)
@Test
public @interface PerformanceTest {
    long thresholdMillis() default 100;
}
```

Then use it in your tests:

```java
@PerformanceTest(thresholdMillis = 50)
void fastOperationTest() {
    // Should complete within 50ms
}

@PerformanceTest(thresholdMillis = 1000)
void slowOperationTest() {
    // Can take up to 1000ms
}
```

## 4. Advanced Reporting with JUnit 5 and External Tools

### 4.1 Creating a Test Execution Listener for Comprehensive Reporting

```java
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;
import java.util.HashMap;
import java.util.Map;

public class PerformanceTestExecutionListener implements TestExecutionListener {
    
    private final Map<String, Long> testStartTimes = new HashMap<>();
    private final Map<String, Long> testExecutionTimes = new HashMap<>();
    
    @Override
    public void testPlanExecutionStarted(TestPlan testPlan) {
        testStartTimes.clear();
        testExecutionTimes.clear();
    }
    
    @Override
    public void executionStarted(TestIdentifier testIdentifier) {
        if (testIdentifier.isTest()) {
            testStartTimes.put(testIdentifier.getUniqueId(), System.currentTimeMillis());
        }
    }
    
    @Override
    public void executionFinished(TestIdentifier testIdentifier, TestExecutionResult result) {
        if (testIdentifier.isTest()) {
            String id = testIdentifier.getUniqueId();
            long startTime = testStartTimes.getOrDefault(id, System.currentTimeMillis());
            long duration = System.currentTimeMillis() - startTime;
            testExecutionTimes.put(id, duration);
            
            System.out.printf("Test '%s' took %d ms%n", 
                testIdentifier.getDisplayName(), duration);
        }
    }
    
    @Override
    public void testPlanExecutionFinished(TestPlan testPlan) {
        // Sort and report the slowest tests
        testExecutionTimes.entrySet().stream()
            .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
            .limit(10) // Top 10 slowest tests
            .forEach(entry -> {
                TestIdentifier test = testPlan.getTestIdentifier(entry.getKey());
                System.out.printf("SLOW TEST: '%s' took %d ms%n", 
                    test.getDisplayName(), entry.getValue());
            });
    }
}
```

### 4.2 Register the Listener with the JUnit Platform

```java
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectPackage;

public class TestRunner {
    public static void main(String[] args) {
        LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request()
            .selectors(selectPackage("com.example.tests"))
            .build();
            
        Launcher launcher = LauncherFactory.create();
        launcher.registerTestExecutionListeners(new PerformanceTestExecutionListener());
        launcher.execute(request);
    }
}
```

### 4.3 Integration with Build Tools

#### Maven Configuration

Add the following to your `pom.xml`:

```xml
<build>
    <plugins>
        <plugin>
            <artifactId>maven-surefire-plugin</artifactId>
            <version>3.0.0</version>
            <configuration>
                <properties>
                    <configurationParameters>
                        junit.jupiter.extensions.autodetection.enabled=true
                    </configurationParameters>
                </properties>
            </configuration>
        </plugin>
    </plugins>
</build>
```

#### Gradle Configuration

Add the following to your `build.gradle`:

```groovy
test {
    useJUnitPlatform()
    systemProperty 'junit.jupiter.extensions.autodetection.enabled', 'true'
}
```

## 5. Visualizing Test Performance Data

### 5.1 Exporting Performance Data to CSV/JSON

Extend your listener to export data:

```java
@Override
public void testPlanExecutionFinished(TestPlan testPlan) {
    try (PrintWriter writer = new PrintWriter(new FileWriter("test-performance.csv"))) {
        writer.println("Test,Duration (ms)");
        
        testExecutionTimes.entrySet().stream()
            .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
            .forEach(entry -> {
                TestIdentifier test = testPlan.getTestIdentifier(entry.getKey());
                writer.printf("%s,%d%n", 
                    test.getDisplayName().replace(',', ';'), 
                    entry.getValue());
            });
    } catch (IOException e) {
        e.printStackTrace();
    }
}
```

### 5.2 Integration with CI/CD Tools

For Jenkins, you can use the Performance Plugin to visualize test performance trends over time. Create a post-build step that processes your performance data file.

## 6. Practical Implementation Example

Here's a complete example that you can adapt to your project:

```java
import org.junit.jupiter.api.extension.*;
import java.lang.annotation.*;
import java.util.logging.Logger;

// Custom annotation
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(SlowTestDetector.class)
public @interface MonitorPerformance {
    long thresholdMillis() default 100;
    boolean reportAlways() default false;
}

// Extension implementation
public class SlowTestDetector implements BeforeTestExecutionCallback, AfterTestExecutionCallback {
    
    private static final Logger logger = Logger.getLogger(SlowTestDetector.class.getName());
    
    @Override
    public void beforeTestExecution(ExtensionContext context) {
        getStore(context).put("start_time", System.currentTimeMillis());
    }
    
    @Override
    public void afterTestExecution(ExtensionContext context) {
        Method testMethod = context.getRequiredTestMethod();
        long startTime = getStore(context).get("start_time", long.class);
        long duration = System.currentTimeMillis() - startTime;
        
        // Get threshold from annotation if present
        MonitorPerformance annotation = testMethod.getAnnotation(MonitorPerformance.class);
        if (annotation == null) {
            annotation = testMethod.getDeclaringClass().getAnnotation(MonitorPerformance.class);
        }
        
        long threshold = annotation != null ? annotation.thresholdMillis() : 100;
        boolean reportAlways = annotation != null && annotation.reportAlways();
        
        if (duration > threshold || reportAlways) {
            String message = String.format(
                "Test execution time: %s.%s took %d ms (threshold: %d ms)",
                testMethod.getDeclaringClass().getSimpleName(),
                testMethod.getName(),
                duration,
                threshold
            );
            
            if (duration > threshold) {
                logger.warning("SLOW TEST: " + message);
            } else {
                logger.info(message);
            }
        }
    }
    
    private ExtensionContext.Store getStore(ExtensionContext context) {
        return context.getStore(ExtensionContext.Namespace.create(getClass(), context.getRequiredTestMethod()));
    }
}
```

Usage example:

```java
import org.junit.jupiter.api.Test;

@MonitorPerformance(thresholdMillis = 50)
class PerformanceSensitiveTests {
    
    @Test
    void fastOperation() {
        // Should complete quickly
    }
    
    @Test
    @MonitorPerformance(thresholdMillis = 1000, reportAlways = true)
    void slowOperation() {
        // This operation is expected to be slower
    }
}
```

## 7. Best Practices

1. **Set Realistic Thresholds**: Base thresholds on actual performance characteristics of your tests.
2. **Categorize Tests**: Use different thresholds for different types of tests (unit, integration, etc.).
3. **Isolate Environment Factors**: Run performance-sensitive tests in controlled environments.
4. **Track Trends Over Time**: Focus on performance degradation rather than absolute values.
5. **Separate Slow Tests**: Consider moving identified slow tests to a separate test suite that runs less frequently.
6. **Use Parameterized Tests Wisely**: Be aware that parameterized tests run multiple times and may appear as slow tests.

By implementing these techniques, you can effectively identify slow test cases, track performance trends over time, and make informed decisions about test optimization.