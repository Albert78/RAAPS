# Concurrency Strategy in RAAPS

To prevent `ConcurrentModificationException` and ensure data integrity across UI and Core threads, the following strategy is enforced:

## 1. Thread-Safe Collections for Caches
Simple caches (like those in `SampledCarbsCalculationCache` and `SampledInsulinCalculationCache`) must use `ConcurrentHashMap` from `java.util.concurrent`. This allows parallel reads and safe updates without explicit locking.

## 2. Mutex Protection for Repositories
Complex repositories that manage in-memory state (like `TreatmentRepository`) must use a `kotlinx.coroutines.sync.Mutex`.
- All methods accessing or modifying shared mutable state must be `suspend` and wrapped in `mutex.withLock`.
- This ensures atomicity and visibility across all threads.

## 3. Core Threading & Reentrancy Protection
The APS Core execution is orchestrated by `SystemOrchestrator` and `Core`:
- **Single-Threaded Dispatcher**: Core operations run on a dedicated single-thread dispatcher (`Executors.newSingleThreadExecutor().asCoroutineDispatcher()`). This guarantees thread affinity, FIFO execution ordering, and CPU cache locality.
- **Mutex Protection for Reentrancy**: Because coroutines suspend at I/O boundaries (e.g. database access or pump synchronization), a `kotlinx.coroutines.sync.Mutex` inside `Core` protects all entry points (`processCalculation`, `onNewBgReading`, `onTherapySettingsChanged`, `onMealsChanged`, `onInsulinChanged`, `getAssumedBg`). This prevents reentrancy and logical race conditions across suspension points.
- **External Interface Protection**: Objects exposed to external threads or UI ViewModels (such as `BolusCorrectionCalculator`) are wrapped in a Mutex decorator (`MutexProtectedBolusCorrectionCalculator`). This ensures UI calls acquire the Core's Mutex and wait for active Core ticks to complete, guaranteeing consistent, atomic snapshots.

## 4. Read-Only Snapshots
When a repository returns a list from its internal state, it MUST return a copy (snapshot) using `.toList()`.
- **Reason**: Returning the internal mutable list directly allows other threads to modify it while the caller is iterating, leading to crashes.
- **Rule**: Never expose internal `MutableList` properties directly.

## 5. Reactive Updates via Flows
Use `Flow` and `StateFlow` for state propagation.
- Room DAOs provide thread-safe `Flow`s for database observation.
- ViewModels should consume these flows and transform them into `StateFlow` for the UI.

## Summary Table

| Use Case | Recommended Approach |
| :--- | :--- |
| **Simple Cache** | `ConcurrentHashMap` |
| **Shared State Repo** | `Mutex` + `suspend` + `.toList()` snapshots |
| **APS Core Loop** | Single-Threaded Dispatcher + `Mutex.withLock` on all entry points |
| **Core External Tools (Calculators)** | Mutex Decorator (`MutexProtectedBolusCorrectionCalculator`) |
| **State Propagation** | `Flow` / `StateFlow` |
| **Database Access** | Room DAOs (standard practice) |