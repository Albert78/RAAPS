# RAAPS
[[Deutsche Version]](#deutsch)

RAAPS is an open-source project focused on developing a modern, full-featured Automated Insulin Delivery (AID/APS) app for Android.

## Project Goal

The goal of RAAPS is to provide a full-featured APS app based on a modern architecture. It is a **greenfield development** that consistently utilizes current technologies to avoid legacy architectural burdens.

As a person with diabetes, I am developing this app primarily for my own needs to create a solution that meets my expectations for performance and user-friendliness.

While technical development is progressing, areas such as general project organization, building a broader ecosystem, and comprehensive documentation are currently still open. I am very open to support and collaboration if someone is interested in driving these aspects forward.

*   **Modern Android Integration:** Utilizing the latest Android standards for a memory-efficient, high-performance, and battery-friendly system.
*   **Clean Functionality:** Focusing on tidy and intuitive app features while still providing users with the necessary flexibility for individual therapy.
*   **Efficiency:** Inspired by [AndroidAPS (AAPS)](https://github.com/nightscout/AndroidAPS), but optimized for modularity, performance, and fast build times.

## Current Status

The project is actively **under development**.

*   **CGM Integration:** There is an open interface for easy integration of any CGM modules. Currently, for simplicity, data delivery via **xDrip+** is supported. Support for further CGM sources is planned, including contributions from third parties.
*   **Pump Integration:** Connection to real insulin pumps is currently being developed in a **separate repository** (current focus: **Dana-i**). The goal is to support various pump models through a modular system.
*   **Development & Simulation:** The internal **sim-body module** is used for development and algorithm testing.

## Development with the Sim-Body Module

Since working on an AID system without hardware connectivity is difficult, RAAPS includes a specialized simulation module. **To use the simulation features, the `app` module must be built using the `simDebug` build flavor.**

*   **Simulated Body:** The `sim-body` module simulates the glucose response to insulin and carbohydrates.
*   **Scenarios:** Various influences (meals, exercise, stress) can be simulated to test control algorithms under controlled conditions.
*   **Interactive UI:** Meals can be entered and simulations controlled via special dialogs in the app.

## Architecture & Plugins

RAAPS relies on a strict separation of concerns through a modular system:

*   **Core Engine:** Handles the central processing of glucose data and the calculation of therapy adjustments.
*   **Plugins for CGM Sources and Pumps:** Hardware interfaces are completely decoupled from the core. This allows for flexible integration of different pump models and CGM sources.
*   **Modularity:** The goal is an extensible ecosystem where hardware-specific plugins can be developed and maintained independently of the calculation core.

## Tech Stack

*   **Language:** Kotlin
*   **UI:** Jetpack Compose with Navigation 3
*   **Concurrency:** Kotlin Coroutines & Flow for reactive data pipelines.
*   **Persistence:** Room Database
*   **Background Processing:** Optimized Android Foreground Services for long-running health services.

---

<a id="deutsch"></a>
# RAAPS (Deutsche Version)

RAAPS ist ein Open-Source-Projekt zur Entwicklung einer modernen, vollumfänglichen Automated Insulin Delivery (AID/APS) App für Android.

## Projektziel

Das Ziel von RAAPS ist die Bereitstellung einer Full-Featured APS-App, die auf einer modernen Architektur basiert. Es handelt sich um eine **Greenfield-Entwicklung**, die konsequent auf aktuelle Technologien setzt, um architektonische Altlasten zu vermeiden.

Als Diabetiker entwickle ich diese App primär für den eigenen Bedarf, um eine Lösung zu schaffen, die meinen Vorstellungen von Performance und Benutzerfreundlichkeit entspricht. 

Während die technische Entwicklung voranschreitet, sind Bereiche wie die allgemeine Organisation des Projekts, der Aufbau eines breiteren Ökosystems und eine umfassende Dokumentation derzeit noch offen. Ich bin sehr offen für Unterstützung und Zusammenarbeit, falls sich jemand findet, der diese Aspekte vorantreiben möchte.

*   **Moderne Android-Integration:** Einsatz aktuellster Android-Standards für ein speichereffizientes, performantes und batterieschonendes System.
*   **Saubere Funktionalität:** Fokus auf aufgeräumte und intuitive App-Funktionen, die dem Nutzer dennoch die nötige Flexibilität für eine individuelle Therapie bieten.
*   **Effizienz:** Inspiriert von [AndroidAPS (AAPS)](https://github.com/nightscout/AndroidAPS), jedoch optimiert für Modularität, Performance und schnelle Build-Zeiten.

## Aktueller Status

Das Projekt befindet sich aktiv **in der Entwicklung**. 

*   **CGM-Anbindung:** Es gibt ein offenes Interface zur einfachen Integration beliebiger CGM-Module. Aktuell wird der Einfachheit halber die Datenanlieferung durch **xDrip+** unterstützt. Die Unterstützung weiterer CGM-Quellen ist geplant, auch durch Zulieferung von Dritten.
*   **Pumpen-Anbindung:** Die Anbindung an reale Insulinpumpen wird aktuell in einem **separaten Repository** entwickelt (aktueller Fokus: **Dana-i**). Ziel ist die Unterstützung verschiedener Pumpenmodelle über ein modulares System.
*   **Entwicklung & Simulation:** Für die Entwicklung und das Testen von Algorithmen wird das interne **Sim-Body-Modul** verwendet.

## Entwicklung mit dem Sim-Body-Modul

Da die Arbeit an einem AID-System ohne Hardware-Anbindung schwierig ist, enthält RAAPS ein spezialisiertes Simulations-Modul. **Um die Simulationsfunktionen nutzen zu können, muss das `app`-Modul im Build-Flavor `simDebug` gebaut werden.**

*   **Simulierter Körper:** Das `sim-body`-Modul simuliert die Glukose-Reaktion auf Insulin und Kohlenhydrate.
*   **Szenarien:** Es können verschiedene Einflüsse (Mahlzeiten, Sport, Stress) simuliert werden, um die Regelalgorithmen unter kontrollierten Bedingungen zu testen.
*   **Interaktive UI:** Über spezielle Dialoge in der App können Mahlzeiten eingegeben und Simulationen gesteuert werden.

## Architektur & Plugins

RAAPS setzt auf eine strikte Trennung der Verantwortlichkeiten durch ein modulares System:

*   **Core Engine:** Übernimmt die zentrale Verarbeitung von Glukosedaten und die Berechnung von Therapieanpassungen.
*   **Plugins für CGM-Quellen und Pumpen:** Die Schnittstellen für Hardware sind vollständig vom Kern entkoppelt. Dies ermöglicht es, verschiedene Pumpenmodelle und CGM-Quellen flexibel zu integrieren.
*   **Modularität:** Das Ziel ist ein erweiterbares Ökosystem, in dem Hardwarespezifische Plugins unabhängig vom Rechenkern entwickelt und gewartet werden können.

## Tech Stack

*   **Sprache:** Kotlin
*   **UI:** Jetpack Compose mit Navigation 3
*   **Nebenläufigkeit:** Kotlin Coroutines & Flow für reaktive Daten-Pipelines.
*   **Persistenz:** Room Database
*   **Hintergrundverarbeitung:** Optimierte Android Foreground Services für dauerhafte Gesundheitsdienste.