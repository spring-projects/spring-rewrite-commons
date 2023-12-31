# Gradle Project Parser

## Structure
**Model** Shared data structures between the **plugin** and  **parser** modules. It has a utility class `SpringRewriteModelBuilder` to fetch `GradleProjectData` from Gradel Build process

**Plugin** Gradle plugin that executes the code inside of Gradle Build process. Responsible for creating serializable instance of `SpringRewriteModelBuilder`

**Parser** Parses Gradle project sources with a help of `GradleProjectData` fetched from Gradle Build process via Gradle Tooling API

## Building
Execute `mvn clean install` - **plugin** artifact should go in local maven repo

## Usage
See `ProjectParserTest` class under the tests of **parser** module
