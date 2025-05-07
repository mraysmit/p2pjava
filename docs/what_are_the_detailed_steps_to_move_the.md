
# Detailed Steps to Move the `dev.mars.p2pjava.config` Package from `p2p-common` to `p2p-config`

Based on the project structure, I can see that part of the migration has already been completed. The main class `ConfigurationManager.java` has been moved to the `p2p-config` module, but the test classes still need to be moved. Here are the detailed steps to complete the migration:

## 1. Create the Test Directory Structure in the `p2p-config` Module

```powershell
# Create the directory structure for test files
mkdir -p p2p-config\src\test\java\dev\mars\p2pjava\config
```

## 2. Move the Test Files

```powershell
# Move ConfigurationManagerTest.java
move p2p-common\src\test\java\dev\mars\p2pjava\config\ConfigurationManagerTest.java p2p-config\src\test\java\dev\mars\p2pjava\config\

# Move ConfigurationManagerMainTest.java
move p2p-common\src\test\java\dev\mars\p2pjava\config\ConfigurationManagerMainTest.java p2p-config\src\test\java\dev\mars\p2pjava\config\
```

## 3. Update Module Dependencies (if needed)

The `p2p-config` module already has a dependency on `p2p-common-api` in its `pom.xml`. If there are any other dependencies required for the tests, add them to the `p2p-config/pom.xml` file.

## 4. Update Import Statements (if needed)

If the test files reference classes from other modules, you may need to update the import statements in the test files.

## 5. Remove the Original Files from `p2p-common`

After confirming that everything works correctly in the new location, remove the original files from the `p2p-common` module:

```powershell
# Remove the main class (already moved)
del p2p-common\src\main\java\dev\mars\p2pjava\config\ConfigurationManager.java

# Remove the test files
del p2p-common\src\test\java\dev\mars\p2pjava\config\ConfigurationManagerTest.java
del p2p-common\src\test\java\dev\mars\p2pjava\config\ConfigurationManagerMainTest.java
```

## 6. Update References in Other Modules

If other modules directly reference the `p2p-common` module to access the `ConfigurationManager` class, update their dependencies to reference the `p2p-config` module instead.

## 7. Update Module Exports/Requires (if using Java modules)

If your project uses the Java module system, update the `module-info.java` files in both the `p2p-common` and `p2p-config` modules to reflect the changes.

## 8. Run Tests to Verify

After moving the files, run the tests to ensure everything works correctly:

```powershell
# Navigate to the project root
cd p2pjava

# Run tests for the p2p-config module
mvn -pl p2p-config test
```

## 9. Commit the Changes

Once everything is working correctly, commit the changes to your version control system.

```powershell
git add p2p-config/
git add p2p-common/
git commit -m "Move dev.mars.p2pjava.config package from p2p-common to p2p-config"
```

These steps should complete the migration of the `dev.mars.p2pjava.config` package from the `p2p-common` module to the `p2p-config` module.