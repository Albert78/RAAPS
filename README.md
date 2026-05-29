# RAAPS

This project is a playground used to experiment with data structures and application architecture for an Automated Insulin Delivery (APS) system.

## Project Objectives

*   **Architecture Research:** Testing different ways to model APS-specific data and control flows.
*   **Android Integration:** Experimenting with modern Android techniques to build a system that is memory-efficient, performant, and highly optimized for battery life.
*   **Functionality:** The project is functionally inspired by [AndroidAPS (AAPS)](https://github.com/nightscout/AndroidAPS) but doesn't have
      so many functions. It compiles and runs much quicker and with less processor/accu usage then AAPS.

## Architecture & Plugin System

The project follows a clean architecture approach with a strict separation of concerns:

*   **Core Engine:** Handles the heavy lifting of processing glucose data and calculating therapy adjustments.
*   **Plugin System:** Glucose (CGM) and Pump interfaces are completely decoupled from the core.
*   **Modularity:** The goal is to enable an ecosystem where third-party developers can build and maintain plugins for specific 
      pump and CGM hardware independently of the calculation core.

## Tech Stack

*   **Language:** Kotlin
*   **UI:** Jetpack Compose with Navigation 3
*   **Concurrency:** Kotlin Coroutines & Flow (for reactive data pipelines)
*   **Persistence:** Room Database
*   **Background Processing:** Android Foreground Services (optimized for long-running health tasks)