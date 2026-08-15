# Concurrency Strategy in RAAPS

To prevent `ConcurrentModificationException` and ensure data integrity across UI and Core threads, the following strategy is enforced:

## 1. Thread-Safe Collections for Caches
Simple caches (like those in `SampledCarbsCalculationCache` and `SampledInsulinCalculationCache`) must use `ConcurrentHashMap` from `java.util.concurrent`. This allows parallel reads and safe updates without explicit locking.

## 2. Mutex Protection for Repositories
Complex repositories that manage in-memory state (like `TreatmentRepository`) must use a `kotlinx.coroutines.sync.Mutex`.
- All methods accessing or modifying shared mutable state must be `suspend` and wrapped in `mutex.withLock`.
- This ensures atomicity and visibility across all threads.

## 3. Read-Only Snapshots
When a repository returns a list from its internal state, it MUST return a copy (snapshot) using `.toList()`.
- **Reason**: Returning the internal mutable list directly allows other threads to modify it while the caller is iterating, leading to crashes.
- **Rule**: Never expose internal `MutableList` properties directly.

## 4. Reactive Updates via Flows
Use `Flow` and `StateFlow` for state propagation.
- Room DAOs provide thread-safe `Flow`s for database observation.
- ViewModels should consume these flows and transform them into `StateFlow` for the UI.

## Summary Table

| Use Case | Recommended Approach |
| :--- | :--- |
| **Simple Cache** | `ConcurrentHashMap` |
| **Shared State Repo** | `Mutex` + `suspend` + `.toList()` snapshots |
| **State Propagation** | `Flow` / `StateFlow` |
| **Database Access** | Room DAOs (standard practice) |