# Click.

An app for taking pictures when the shutter button is inconvenient or out of reach. It uses your phone's sensors to trigger the camera in your favorite camera app.

## The Triggers

You can enable any combination of the following methods from the app's main screen.

### 1. Fingerprint Scroll
* **What it does:** Lets you take a picture by swiping the fingerprint scanner.
* **How it works:** It places an invisible scrollbar on the screen. If your phone uses fingerprint swipes for navigation, that swipe is registered as a "scroll," which `Click` then translates into a shutter command. Requires the "Draw Over Other Apps" permission.

### 2. Proximity "Tap"
* **What it does:** Lets you take a picture by waving your finger over the top of your phone (near the earpiece).
* **How it works:** It uses the proximity sensor to detect a brief shadow, which it interprets as a "tap."

### 3. Vibration "Tap"
* **What it does:** Lets you take a picture by tapping the back of your phone near the camera.
* **How it works:** It uses the accelerometer to feel for the specific jolt of a physical tap.
* **Sensitivity:** To prevent accidental triggers from ambient movement (like a bumpy car ride), you can adjust the sensitivity of this trigger in the app. A higher sensitivity requires a harder tap.

## The Setup

To make this work, you have to grant two permissions. The app will guide you to the correct settings screen for each.

1.  **Accessibility Service:** This is the core of the app. It's required so `Click` can know when a camera app is active. It **does not** read any screen content, passwords, or other personal information.
2.  **Draw Over Other Apps:** This is **only** needed for the *Fingerprint Scroll* trigger. It allows the app to place its invisible scrollbar.

## A Note on Battery

To conserve battery, the app's sensors are only active when a known camera app is running in the foreground. When you switch to another app or turn off the screen, sensor listening is automatically paused.
