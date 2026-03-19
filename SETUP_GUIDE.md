# Setup & Contribution Guide

Follow these steps to get IronDiary running on your local machine.

## ⚙️ Prerequisites
- Android Studio Ladybug (or newer)
- JDK 17
- A Firebase project

## 🚀 Getting Started

### 1. Clone the Repository
```bash
git clone https://github.com/pranavbairollu/IronDiary.git
cd IronDiary
```

### 2. Firebase Configuration
IronDiary depends on Google Services for Authentication and Firestore.
1.  Go to the [Firebase Console](https://console.firebase.google.com/).
2.  Create a new project named **IronDiary**.
3.  Add an Android app with package name `com.example.irondiary`.
4.  Download `google-services.json` and place it in the `app/` directory.
5.  Enable **Anonymous Authentication** and **Cloud Firestore** in the Firebase console.

### 3. Build & Run
- Open the project in Android Studio.
- Sync Gradle.
- Run the `app` module on an emulator or physical device.

## 🧪 Running Tests
We use JUnit 4 and MockK for unit testing.
- **Via Terminal**: `./gradlew test`
- **Via Android Studio**: Right-click the `src/test` folder and select **Run 'Tests in...'**

## 🧩 Key Project Modules
- `com.example.irondiary.data`: Repository, Room Entities, DAOs, and Mappers.
- `com.example.irondiary.viewmodel`: Main business logic and UI state management.
- `com.example.irondiary.ui`: Jetpack Compose screens and components.
- `com.example.irondiary.worker`: WorkManager synchronization logic.
