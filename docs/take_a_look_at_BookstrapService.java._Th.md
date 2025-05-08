

# Service Name Configuration Refactoring

## Problem
In the `BootstrapService.java` file, the service names for "indexserver", "tracker", and "peer" are hard-coded in the `ServiceInstance.start()` method. This creates inflexibility and makes it difficult to add new services or modify existing ones.

## Solution
I'll refactor the code to replace the hard-coded service names with a configurable list using a static Map. This will make the code more maintainable and extensible.

### Implementation Steps

1. Add a static Map in the `BootstrapService` class to define service configurations:
```java
// Service configuration map
private static final Map<String, ServiceConfig> SERVICE_CONFIGS = new HashMap<>();

// Static initializer to populate the service configurations
static {
    // Tracker service configuration
    SERVICE_CONFIGS.put("tracker", new ServiceConfig(6000, "tracker.port", "tracker"));
    
    // Index server configuration
    SERVICE_CONFIGS.put("indexserver", new ServiceConfig(6001, "indexserver.port", "index server"));
    
    // Peer service configuration
    SERVICE_CONFIGS.put("peer", new ServiceConfig(7000, "peer.port", "peer"));
}

// Service configuration class
private static class ServiceConfig {
    private final int basePort;
    private final String portProperty;
    private final String serviceType;
    
    public ServiceConfig(int basePort, String portProperty, String serviceType) {
        this.basePort = basePort;
        this.portProperty = portProperty;
        this.serviceType = serviceType;
    }
    
    public int getBasePort() {
        return basePort;
    }
    
    public String getPortProperty() {
        return portProperty;
    }
    
    public String getServiceType() {
        return serviceType;
    }
}
```

2. Modify the `ServiceInstance.start()` method to use this map instead of hard-coded if-else conditions:
```java
public void start() throws Exception {
    try {
        // Create service instance if it doesn't exist
        if (serviceInstance == null) {
            serviceInstance = serviceClass.getDeclaredConstructor().newInstance();
        }

        // Configure dynamic ports if enabled
        ConfigurationManager config = ConfigurationManager.getInstance();
        if (config.getBoolean("bootstrap.dynamic.ports", true)) {
            // Get service configuration from the map
            ServiceConfig serviceConfig = null;
            
            // Check for exact match first
            if (SERVICE_CONFIGS.containsKey(serviceId)) {
                serviceConfig = SERVICE_CONFIGS.get(serviceId);
            } 
            // For peer services that might have a suffix (e.g., peer1, peer2)
            else if (serviceId.startsWith("peer")) {
                serviceConfig = SERVICE_CONFIGS.get("peer");
            }
            
            // Configure dynamic port if service configuration exists
            if (serviceConfig != null) {
                int dynamicPort = config.findAvailablePort(serviceConfig.getBasePort());
                config.set(serviceConfig.getPortProperty(), String.valueOf(dynamicPort));
                // Set system property for service to use
                System.setProperty(serviceConfig.getPortProperty(), String.valueOf(dynamicPort));
                logger.info("Using dynamic port for " + serviceConfig.getServiceType() + ": " + dynamicPort);
            }
        }

        // Call start method
        Method startMethod = serviceClass.getMethod(startMethodName);
        startMethod.invoke(serviceInstance);

        // Mark as ready
        ready = true;
    } catch (Exception e) {
        failed = true;
        throw e;
    }
}
```

## Benefits

1. **Maintainability**: Service configurations are centralized in one place, making them easier to manage.
2. **Extensibility**: New services can be added by simply updating the `SERVICE_CONFIGS` map without modifying the core logic.
3. **Consistency**: All services are handled in a consistent manner.
4. **Readability**: The code is more readable and easier to understand.

## Testing

The changes should be tested to ensure they don't break existing functionality:
1. Run the `BootstrapServiceTest` to verify that all tests still pass.
2. Manually test the application to ensure that dynamic port allocation still works correctly for all services.

This refactoring maintains the same behavior while making the code more maintainable and extensible.