# IntelliJ IDEA Module Output Configuration

This document provides instructions for configuring IntelliJ IDEA to define output folders for compiled Java modules in the p2p-java project.

## Issue Description

The p2p-java project is configured to use Java Platform Module System (JPMS), and IntelliJ IDEA requires an output folder to be defined for compiled modules.

## Solution

Follow these steps to configure IntelliJ IDEA to define output folders for compiled modules:

1. Open the p2p-java project in IntelliJ IDEA.

2. Go to **File > Project Structure** (or press Ctrl+Alt+Shift+S).

3. In the Project Structure dialog, select **Project** from the left panel.

4. Make sure the **Project SDK** is set to Java 24 (or your installed JDK version).

5. Set the **Project language level** to match your JDK version.

6. Set the **Project compiler output** to: `[project_root]/target/classes`.

7. Click **Apply**.

8. Now select **Modules** from the left panel.

9. For each module in the project (p2p-tracker, p2p-discovery, etc.):
   - Select the module
   - Go to the **Paths** tab
   - Check the **Inherit project compile output path** option
   - If you prefer to set a custom output path for a specific module, uncheck the inherit option and set:
     - **Output path**: `[module_root]/target/classes`
     - **Test output path**: `[module_root]/target/test-classes`

10. Click **Apply** and then **OK** to save the changes.

## Additional Configuration

If you're still experiencing issues with module compilation, you may need to:

1. Go to **File > Settings** (or press Ctrl+Alt+S).

2. Navigate to **Build, Execution, Deployment > Compiler > Java Compiler**.

3. Make sure the **Use compiler from module target JDK when possible** option is checked.

4. Set the **Project bytecode version** to match your JDK version.

5. Click **Apply** and then **OK** to save the changes.

## Rebuilding the Project

After making these changes, it's a good idea to rebuild the project:

1. Go to **Build > Rebuild Project**.

2. Wait for the build to complete.

3. Check the build output for any errors.

If you're still experiencing issues, try invalidating caches and restarting IntelliJ IDEA:

1. Go to **File > Invalidate Caches / Restart**.

2. Select **Invalidate and Restart**.

This should resolve the issue with IntelliJ IDEA asking for an output folder to be defined for compiled modules.