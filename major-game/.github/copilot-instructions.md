# Game Ideas

The game is supposed to be a game where you play as an apple juice addicted mountaineer climbing the "Hohe Dirn" (small peak in austria)

## Description

Summary

You are a mountaineer named major and you are a lonely apple juice addict. In your endless lonelyness you decide to do what you do best, climb mountains and drink apple juice. You hope that maybe one day, you'll meet the woman of your dreams atop one of the many mountains you climb. So you keep climbing and you keep drinking your apple juice.
Game Mechanics
General

This is a text based adventure game, you walk along the well known mountain pass for as long as you can. You arrive at the base-camp of the Hohe Dirn, from this point forth you will be the first human to have ever wandered these lands ... or are you?
Apple Jiuce Meter

You are an apple juice addict. You HAVE to drink apple juice or else ... things happen ... scary things.
Honor System

Do honorable things, be a decent human beeing and you'll be fine. Choose to be a menace and you'll pay the price.

# Copilot instructions for this repository

## Build, test, and run commands

Use the Gradle wrapper from the `major-game` directory.

- Full build: `./gradlew clean build`
- Run tests: `./gradlew test`
- Run a single test class: `./gradlew test --tests "org.example.majorgame.YourTestClass"`
- Run a single test method: `./gradlew test --tests "org.example.majorgame.YourTestClass.yourTestMethod"`
- Run the app: `./gradlew run`
- Build a runtime image: `./gradlew jlink`

## High-level architecture

This is a modular JavaFX application with a small MVC-style flow:

1. `org.example.majorgame.Launcher` is the JVM entry point (`main`).
2. `HelloApplication` extends `javafx.application.Application`, loads FXML, and creates the primary `Scene`/`Stage`.
3. `hello-view.fxml` defines the UI layout and binds actions/fields to `HelloController`.
4. `HelloController` handles UI events and updates JavaFX controls.
5. `module-info.java` declares module boundaries (`requires javafx.controls`, `requires javafx.fxml`), exports the app package, and opens it for FXML reflection.

## Key repository conventions

- Keep JavaFX startup in a dedicated launcher class (`Launcher`) and set Gradle `application.mainClass` to that launcher.
- Keep the application package/module aligned across:
  - `application.mainModule` in `build.gradle.kts`
  - `module-info.java`
  - Java package paths under `src/main/java`
  - Resource paths under `src/main/resources`
- Place FXML files in the same package path as the loading application class so `HelloApplication.class.getResource(...)` resolves correctly.
- When adding controller-bound UI elements, keep `fx:controller`, `@FXML` fields/methods, and FXML `fx:id`/event handler names in sync.
- The project targets Java toolchains compatible with JavaFX 21 (currently Java 21).

