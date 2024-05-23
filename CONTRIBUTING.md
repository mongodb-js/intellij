# Contributing

## Getting Started

You'll need at least the JDK 17 to work locally on the plugin. While the plugin should
work on any JDK distribution, it is recommended to download the latest JDK version from
[Adoptium](https://adoptium.net/).

After installing the JDK, and ensuring it is accessible on the system, run the following
Gradle task, that will set up the required git hooks for this project:

```sh
./gradlew gitHooks
```

Also, ensure unit tests are working by running the following command:

```sh
./gradlew unitTest
```

It will take a few moments, and you are ready to go.

## Submitting Changes

MongoDB welcomes community contributions! If you’re interested in making a contribution to MongoDB's plugin for
IntelliJ,
please follow the steps below before you start writing any code:

- Sign the contributor's agreement. This will allow us to review and accept contributions.
- Fork the repository on GitHub.
- Create a branch with a name that briefly describes your feature.
- Implement your feature or bug fix.
- Add new cases to cover the new functionality.
- Add comments around your new code that explain what's happening.
- Add the changes to the [Changelog](CHANGELOG.md)
- Commit and push your changes to your branch and submit a pull request.

## Submitting Bugs

You can report new bugs by creating a new issue either in [JIRA](https://jira.mongodb.org/projects/INTELLIJ/issues/) or
[GitHub](https://github.com/mongodb-js/intellij/issues). Please include as much information as possible about your
environment
and include any relevant logs.

## Starting the plugin locally

Starting the plugin locally requires a working local environment, so before running the
plugin, please revisit the `Getting Started` section.

Once the environment works, run the following Gradle task to start an IntelliJ instance
with the plugin:

```sh
./gradlew :packages:jetbrains-plugin:runIde
```

The plugin will hot-reload any change in the source code if it's built. So running the
following command:

```sh
./gradlew build
```

Will apply the latest changes to the running IDE.

> [!INFO]
> It's important to note that this only works because the plugin is a dynamic plugin.
> If you find out that it consistently fail, we might have break one of the dynamic plugin
> rules so run the `./gradlew :packages:jetbrains-plugin:runPluginVerifier` task to check.

## Running UI Tests

UI tests require a working local environment, so before running them, please revisit the
`Getting Started` section.

Once the environment is set up, run the follow commandline script to run all the UI tests of
the project:

```sh
./gradle/run-ui-test.sh
```

It will take a while, and Remote Robot will take ownership of your mouse once it starts
a running IntelliJ environment, so please take a seat and watch it run.

If a test fails, there will be a video recording in `packages/jetbrains-plugin/video` in AVI format
(you might need VLC Player to view it if you use a Mac) and also a screenshot
in `packages/jetbrains-plugin/build/reports`
alongside the hierarchy of the view (the "DOM").

## Running a single UI Test

We can run a single UI test for debugging using Gradle and IntelliJ IDEA. To do so, first we need
to start the plugin with the Remote Robot server, by running the following Gradle task:

```sh
./gradlew :packages:jetbrains-plugin:runIdeForUiTests
```

Once the IDE is started, now we can go to any test in IntelliJ IDEA and press the Play button
to run it. When the button is clicked, it will ask for which Gradle task to use to run the test,
and we will choose `uiTest` instead of `test`.

> [!CAUTION]
> :warning: Using `test` instead of `uiTest` won't run any test, so make sure to use `uiTest`. :warning:

## Managing third-party dependencies

We try to avoid third-party dependencies as much as possible, and only use the MongoDB driver,
the JetBrains plugin ecosystem and the Kotlin standard library. However, sometimes, it can be
convenient to add a new third-party dependency to solve a really specific issue. In that case, to
add a new dependency:

* Go to [the dependency catalogue](https://github.com/mongodb-js/intellij/blob/main/gradle/libs.versions.toml)
* Add the version of the dependency to the `[versions]` section.
* Add the dependency to the `[dependencies]` section.
* Add the dependency reference to the specific package that will use the dependency.

## Releasing

We don't have an automatic cadence of releases. We plan new releases, implement the
features and then release when we are done. To release a new plugin version, is as follows:

* Go to [GitHub Actions](https://github.com/mongodb-js/intellij/actions) and run the `Release Draft` workflow.
    * Choose the type of release that you want to publish, it can be either patch, minor and major. Following semver.
* Wait until the workflow is done.
    * It will validate that all the tests work and will publish a nightly version in the Marketplace.
* Go to the [GitHub Releases](https://github.com/mongodb-js/intellij/releases) page and you'll find a new draft release.
* Publish the release as a normal GitHub Release, by editing it and publishing.
* This will run a workflow _[you can check in GHA](https://github.com/mongodb-js/intellij/actions)_.
    * When done it will update the main branch with the updated changelog and plugin version.
    * And also will publish the package to the JetBrains Marketplace.