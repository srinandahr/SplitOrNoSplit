# Split or No Split 💸

An Android automation tool that reads bank transaction SMS messages and automatically splits the expense on Splitwise.

**Features:**
* 📩 **Auto-Detection:** Listens for "Spent" or "Debited" SMS alerts from banks.
* 🔔 **Actionable Notifications:** "Split" or "No Split" buttons right in the notification shade.
* 🚀 **Instant Sync:** Adds expenses to your chosen Splitwise group in 1 click.
* 🔒 **Privacy Focused:** "Bring Your Own Key" model. Your API keys never leave your device.
* ⏸️ **Pause Mode:** Temporarily disable detection when you don't want to split.

## 📱 Installation

1. Go to the [Releases Page](https://github.com/srinandahr/SplitOrNoSplit/releases) and download the latest `.apk` file.
2. Install it on your Android device (You may need to allow "Install from Unknown Sources").

## ⚙️ Setup Guide (Important!)

Since this is an open-source tool, you need to use your own **Splitwise Personal Token**. This ensures your data stays yours.

1. **Get your Key:**
   * Go to [https://secure.splitwise.com/apps](https://secure.splitwise.com/apps).
   * Click on Register your Application
   * Provide Application Name, Application Description and Homepage URL
   * Agree to the terms and conditions and click on Register and get API Key
   * Click on the Create API Key button
   * Copy the token
2. **Configure App:**
   * Open **Split or No Split**.
   * Paste your token into the "API Key" field.
   * Click **Fetch Groups** to verify it works.
   * Select your default group (e.g., "Flatmates").
   * Click **Save Settings**.

## 🛠️ Built With
* **Kotlin** - 100% Native Android.
* **Retrofit** - For networking with Splitwise API.
* **BroadcastReceivers** - For listening to SMS and Notification actions.

## 📄 License
This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
