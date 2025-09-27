# Click.

An app for taking pictures when the shutter button is inconvenient, impossible, or simply boring.

It uses your phone's sensors to trigger the camera in whatever camera app you're already using.

## The Triggers

You can enable any combination of the following methods from the app's main screen.

### 1. Fingerprint Scroll

* **What it does:** Lets you take a picture by swiping the fingerprint scanner.

* **How it works:** It places an invisible scrollbar on the screen. If your phone uses fingerprint swipes for navigation, that swipe gets registered as a "scroll," which `Click` then translates into a shutter command. Requires the "Draw Over Other Apps" permission.

### 2. Proximity "Tap"

* **What it does:** Lets you take a picture by waving your finger over the top of your phone.

* **How it works:** It uses the proximity sensor (the thing that turns your screen off during calls) to detect a brief shadow, which it interprets as a "tap."

### 3. Vibration "Tap"

* **What it does:** Lets you take a picture by tapping the back of your phone near the camera.

* **How it works:** It uses the accelerometer to feel for the specific jolt of a physical tap.

## The Setup

To make this work, you have to grant two permissions. The app will guide you to the correct settings screen.

1. **Accessibility Service:** This is the core of the app. It's required so `Click` can know when a camera app is active. It does not read any content, passwords, or credit card numbers.

2. **Draw Over Other Apps:** This is only needed for the *Fingerprint Scroll* trigger. It allows the app to place its invisible scrollbar.

## The Inevitable Disclaimer

These methods are indirect by design. They are clever, but not foolproof. Consider the following to be features, not bugs:

* **Inconsistent Performance:** This app's relationship with your specific phone, OS version, and camera app can be described as "it's complicated." Success is not guaranteed.

* **False Positives:** A bumpy car ride might be interpreted as a "Vibration Tap." A stray shadow might trigger a "Proximity Tap." The app may occasionally exhibit a creative disregard for your intent.

* **Battery Use:** Listening to sensors costs energy. Expect slightly higher battery drain while your camera is open and the service is active.
