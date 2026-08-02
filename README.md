# Split or No Split

An Android automation tool that reads bank transaction SMS messages and splits the expense with your group in one tap.

**Features:**

- **Auto-Detection:** Listens for "Sent" or "Debited" SMS alerts from banks.
- **Actionable Notifications:** "Split" or "No Split" buttons right in the notification shade.
- **Instant Sync:** Adds the expense to your group, split equally, in 1 click.
- **Balances In-App:** See who owes what and your recent expenses without leaving the app.
- **Multiple Groups:** Flatmates and a trip at the same time — switch between them.
- **No Account, No API Key:** Create a group from the app. Nothing to sign up for.
- **Pause Mode:** Temporarily disable detection when you don't want to split.

## Installation

1. Go to the [Releases Page](https://github.com/srinandahr/SplitOrNoSplit/releases) and download the latest `.apk` file.
2. Install it on your Android device (You may need to allow "Install from Unknown Sources").

## Setup

No developer account, no API key, no registration. Open the app and pick one:

**Create a new group**

1. Tap **Create a new group**.
2. Enter a group name, your email, and the currency.
3. Add everyone splitting expenses, including yourself.
4. Pick which member _you_ are — expenses from this phone are recorded as paid by you.

**Join an existing group**

1. Tap **Join an existing group**.
2. Scan the group's QR code, paste the invite link, or type the project ID and private code.
3. Pick which member you are.

To invite someone: **⋮ → Share group**, then send them the QR or link.

## Where your data lives

Groups are stored on [I Hate Money](https://ihatemoney.org/), a free and open-source shared-budget service. The app talks to `ihatemoney.org` by default. You can point it at your **own server** instead — under _Create a group → Use my own server_ — if you'd rather host it yourself ([self-hosting docs](https://ihatemoney.readthedocs.io/en/latest/installation.html)).

If you plan to use this heavily, please consider self-hosting. `ihatemoney.org` is run for free by volunteers.

## Privacy and security — read this

This app has **no backend**. Your group credentials are stored only on your phone, encrypted with the Android keystore.

Some honest caveats about the underlying service:

- **A group's private code is a shared secret.** I Hate Money has no per-user accounts. Anyone holding the project ID and private code can view, edit, and **delete** everything in that group. Only share an invite with people who are actually in the group.
- **Invite links carry the code.** Sending one over a chat app leaves it in that chat history.
- **There is no record of who added an expense** — only of who _paid_ it. That's what the balances are based on.
- **Creating a group sends your email address** to the server, where it is used only to recover the private code if you lose it.
- **SMS never leaves your device.** Messages are parsed on-device; only the amount and payee you approve are sent.

## Built With

- **Kotlin** - 100% Native Android.
- **Jetpack Compose** - Material 3 UI.
- **Retrofit** - For networking with the I Hate Money API.
- **BroadcastReceivers** - For listening to SMS and Notification actions.
- **ZXing** - QR codes for sharing and joining groups.

## Upgrading from v1 (Splitwise)

v1 posted to Splitwise. Splitwise now requires a paid subscription for API access, so v2 moves to I Hate Money — free, open source, and with no per-user account requirement.

Your existing Splitwise expenses **stay in Splitwise**; they are not migrated. On first launch after upgrading, the app explains the change and walks you through creating or joining a group.

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE.txt) file for details.
