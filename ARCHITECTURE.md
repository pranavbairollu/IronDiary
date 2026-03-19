# IronDiary Architecture Deep-Dive

This document outlines the technical decisions and architectural patterns that make IronDiary a robust, production-ready application.

## 1. Offline-First Philosophy
IronDiary uses **Room** as the **Single Source of Truth (SSOT)**. Every user interaction (creating a task, logging a session) follows this flow:
1.  **Local Write**: Data is instantly saved to Room with a `SyncState.PENDING` status.
2.  **UI Update**: The ViewModel, observing a Room `Flow`, reflects the change immediately.
3.  **Background Sync**: A `SyncWorker` is triggered via WorkManager to push the change to Firestore.

## 2. SyncState Machine
To manage data integrity across devices and network conditions, every entity includes a `syncState` and `updatedAt` field.

### State Transitions:
- `PENDING`: Local change ready to be uploaded.
- `SYNCED`: Local data is in parity with the cloud.
- `FAILED`: Cloud sync failed; the worker will retry with exponential backoff.
- `DELETED`: Soft-delete status; the record remains locally until the cloud deletion is confirmed.

## 3. Conflict Resolution Strategy
IronDiary uses a **Timestamp-Based Last-Write-Wins** strategy with a protective "Local-Newer" guard:
- During sync, the `SyncWorker` compares `localUpdatedAt` with the remote `serverUpdatedAt`.
- If the remote version is newer than the local version (e.g., changed on another device), the local version is updated to match the remote version.
- If the local version is newer, it overwrites the remote version.

## 4. UI Rendering & Performance
The UI is built with **Jetpack Compose**, focusing on:
- **Stable Keys**: Every list item uses `key(id)` to prevent unnecessary recompositions and enable efficient animations.
- **Unidirectional Data Flow**: State flows from the Repository -> ViewModel -> UI, while events flow the opposite way.
- **Resource Management**: Using `Flow.collectAsState()` and `viewModelScope` to ensure memory leaks are avoided and coroutines are cancelled properly.

## 5. Testing Strategy
We prioritize confidence over coverage:
- **Repository Pattern**: Extracted all data logic into a clean Repository layer, making ViewModels easily testable.
- **MockK Integration**: Used for reliable mocking of repositories and coroutine-heavy flows.
- **Coroutine Dispatchers**: Injected dispatchers into ViewModels for predictable unit testing (e.g., `StandardTestDispatcher`).
