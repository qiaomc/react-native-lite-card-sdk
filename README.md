# react-native-lite-card-sdk

DigitalShield Lite Card SDK for React Native. NFC backup card operations with native Android code bundled in the npm package (no separate mavenLocal publish required).

- React Native **0.85.1** / React **19.2.3**
- Android: full NFC support (backupcardsdk + GPChannel NDK bundled under `android/backupcardsdk/`)
- iOS: stub implementation

## Installation

```sh
yarn add react-native-lite-card-sdk
```

## Usage

### Compatible API (`@onekeyfe/react-native-lite-card`)

Drop-in facade with the same methods and `PromiseResult` error shape:

```tsx
import onekeyLite, { CardErrors, type NfcConnectUiState } from 'react-native-lite-card-sdk';

const result = await onekeyLite.getLiteInfo();
if (result.error) {
  console.log(CardErrors[result.error.code], result.error.message);
} else {
  console.log(result.data);
}

await onekeyLite.setMnemonic(mnemonic, pin);
await onekeyLite.getMnemonicWithPin(pin);
await onekeyLite.changePin(oldPin, newPin);
await onekeyLite.reset();

onekeyLite.addConnectListener((event: NfcConnectUiState) => {
  console.log(event.code, event.message);
});
```

### Low-level API (backup card SDK)

```tsx
import { LiteCardSdk } from 'react-native-lite-card-sdk';

const info = await LiteCardSdk.getCardInfo();
await LiteCardSdk.activateCard('555555');
await LiteCardSdk.writeSlot(1, [/* bytes */], '123456');
```

### API

| Method | Description |
|--------|-------------|
| `getCardInfo()` | Read card serial, PIN retry count, new-card flag |
| `resetCard()` | Factory reset |
| `activateCard(pwd)` | Activate new card with PIN |
| `changePin(oldPin, newPin)` | Change PIN |
| `checkSlotEmpty(slotId, pwd)` | Check if slot is empty |
| `writeSlot(slotIndex, data, pwd)` | Write data to slot |
| `readSlot(slotIndex, pwd)` | Read data from slot |

Events: `onApduLog`, `onNfcTouch`

## Development

```sh
cd /Users/vv5/Documents/digital-shield/front-end/react-native-lite-card-sdk
yarn install
yarn prepare

# Run example on Android (NFC device required)
yarn example android
```

Ensure NFC intent filters are configured in your app's `AndroidManifest.xml` (see `example/android/app/src/main/AndroidManifest.xml`).

## Troubleshooting (Android build)

If `./gradlew` fails with `Could not GET https://dl.google.com/...` or TLS handshake errors:

1. Pull the latest code (example Android build uses Aliyun Maven mirrors as fallback).
2. Ensure JDK 17+ is used: `java -version`
3. Clean and rebuild:

```sh
cd example/android
./gradlew clean
cd ../..
yarn example android --device <device-id>
```

4. If Google Maven is still unreachable, use a VPN or configure your network proxy in `~/.gradle/gradle.properties`:

```properties
systemProp.https.proxyHost=127.0.0.1
systemProp.https.proxyPort=7890
```

## Project structure

```
react-native-lite-card-sdk/
├── src/                         # Turbo Module JS spec
├── android/
│   ├── backupcardsdk/           # Bundled native NFC + GPChannel SDK
│   └── src/.../LiteCardSdk*     # RN Android wrapper
├── ios/                         # RN iOS stub
└── example/                     # Demo app
```

## License

MIT
