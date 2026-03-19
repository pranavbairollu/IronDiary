# IronDiary 🛡️

**IronDiary** is a robust, offline-first productivity and academic tracking Android application. Built with modern Android architecture, it ensures your data is always available, even without an internet connection, while maintaining seamless cloud synchronization.

---

## 🚀 Key Features

- **✅ Smart Task Management**: Organize your daily goals with instant local feedback and deferred cloud sync.
- **📅 Interactive Calendar & Daily Logs**: Track gym attendance, weight, and personal notes.
- **📊 Academic Analytics**: Visualize study patterns with interactive, zoomable bar graphs.
- **🔄 Bulletproof Sync**: Robust offline-first architecture using Room as the Single Source of Truth (SSOT).
- **🌓 Modern UI**: Fully responsive Jetpack Compose interface with Material 3 theming and consistent layouts.

---

## 🏗️ Technical Architecture

IronDiary is built on a high-performance, offline-first foundation:

### Modern Android Tech Stack
- **UI**: Jetpack Compose (Declarative UI)
- **Database**: Room (Local SSOT)
- **Networking/Cloud**: Firebase Firestore
- **Concurrency**: Kotlin Coroutines & Flow
- **Background Work**: WorkManager (Reliable Sync)
- **Dependency Management**: Hilt/ViewModel

### Core Architecture (SSOT Pattern)
```mermaid
graph TD
    UI[Jetpack Compose UI] --> VM[MainViewModel]
    VM --> Repo[IronDiaryRepository]
    Repo --> Room[Room Database (SSOT)]
    Room --> Sync[SyncWorker (WorkManager)]
    Sync <--> Firestore[Firebase Firestore]
    
    style Room fill:#f9f,stroke:#333,stroke-width:4px
```

---

## 🛠️ Portfolio Highlights

### 1. Offline-First Synchronization
Implementation of a bidirectional sync engine that:
- Writes to local Room DB instantly for zero-latency UI.
- Schedules background sync via **WorkManager**.
- Handles conflicts using a **SyncState State Machine** (Pending, Synced, Failed, Deleted).

### 2. High-Performance UI
- **Lazy List Optimization**: All lists use stable keys and optimized recomposition triggers.
- **Custom Graphics**: Custom-built, zoomable bar graphs for academic tracking.
- **Reactive Data Streams**: End-to-end usage of `Flow` for real-time UI updates from local persistence.

### 3. Unit Testing Excellence
- Comprehensive test suite using **MockK** and **Coroutines Test**.
- Isolated repository mocking strategy for robust ViewModel validation.

---

## 📖 Further Documentation

- **[Architecture Deep-Dive](ARCHITECTURE.md)**: Technical details on sync strategy and conflict resolution.
- **[Setup Guide](SETUP_GUIDE.md)**: Instructions on how to build and run the project locally.

---

## 📷 Screenshots

| Tasks | Calendar | Analytics |
| :---: | :---: | :---: |
| ![Tasks](https://via.placeholder.com/200x400?text=Tasks+Screen) | ![Calendar](https://via.placeholder.com/200x400?text=Calendar+Screen) | ![Analytics](https://via.placeholder.com/200x400?text=Analytics+Screen) |

*(Note: Replace placeholders with actual app screenshots for your portfolio)*

---

## 👤 Author
**Pranav Bairollu**
- [GitHub](https://github.com/pranavbairollu)
