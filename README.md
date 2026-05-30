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

```tsx
import LiteCardSdk, { type CardInfo } from 'react-native-lite-card-sdk';

const touchSub = LiteCardSdk.onNfcTouch((event) => {
  console.log('NFC touch:', event.isBackupCard);
});

const apduSub = LiteCardSdk.onApduLog((event) => {
  console.log(event.message);
});

const info: CardInfo | null = await LiteCardSdk.getCardInfo();
await LiteCardSdk.activateCard('555555');
await LiteCardSdk.changePin('555555', '123456');
await LiteCardSdk.writeSlot(1, [/* byte array */], '123456');
const data = await LiteCardSdk.readSlot(1, '123456');
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
