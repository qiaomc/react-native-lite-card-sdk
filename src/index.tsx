import { Linking, Platform, type EventSubscription } from 'react-native';
import LiteCardSdk from './NativeLiteCardSdk';
import type { CardInfo as NativeCardInfo } from './NativeLiteCardSdk';
import {
  CardErrors,
  type CallbackError,
  type CardInfo,
  type PromiseResult,
} from './NativeReactNativeLiteCard';

export type NfcConnectUiState = {
  code: number;
  message: string;
};

const DEFAULT_SLOT = 1;

function decodeUtf8(bytes: number[]): string {
  return decodeURIComponent(
    Array.from(Uint8Array.from(bytes))
      .map((byte) => `%${byte.toString(16).padStart(2, '0')}`)
      .join('')
  );
}

function encodeUtf8(value: string): number[] {
  return Array.from(unescape(encodeURIComponent(value)), (char) =>
    char.charCodeAt(0)
  );
}

function mapCardInfo(info: NativeCardInfo | null): CardInfo | null {
  if (!info) {
    return null;
  }

  const pinActivated = info.isNewCard === true;

  return {
    serialNum: info.serialNumber ?? '',
    pinRetryCount: info.pinRetryCount ?? 0,
    isNewCard: !pinActivated,
    hasBackup: pinActivated,
  };
}

function toCallbackError(error: unknown): CallbackError {
  if (error && typeof error === 'object') {
    const maybeCode = (error as { code?: unknown }).code;
    const maybeMessage = (error as { message?: unknown }).message;

    if (typeof maybeCode === 'number') {
      return {
        code: maybeCode,
        message: typeof maybeMessage === 'string' ? maybeMessage : null,
      };
    }

    if (typeof maybeMessage === 'string' && maybeMessage.length > 0) {
      return { code: CardErrors.ExecFailure, message: maybeMessage };
    }
  }

  return { code: CardErrors.ExecFailure, message: String(error) };
}

class OnekeyLite {
  private connectSubscription: EventSubscription | null = null;

  addConnectListener(listener: (event: NfcConnectUiState) => void) {
    this.removeConnectListeners();
    this.connectSubscription = LiteCardSdk.onNfcTouch((event) => {
      listener({
        code: event.isBackupCard ? 0 : 1,
        message: event.isBackupCard ? '检测到备份卡' : '检测到非备份卡',
      });
    });
    return this.connectSubscription;
  }

  removeConnectListeners() {
    this.connectSubscription?.remove();
    this.connectSubscription = null;
  }

  addAccordListener() {
    return LiteCardSdk.onNfcTouch((event) => {
      if (event.isBackupCard) {
        // Active NFC connection established for backup card.
      }
    });
  }

  async getLiteInfo(): Promise<PromiseResult<CardInfo>> {
    try {
      const info = await LiteCardSdk.getCardInfo();
      const cardInfo = mapCardInfo(info);

      if (!cardInfo) {
        return {
          error: {
            code: CardErrors.ConnectionFail,
            message: 'Failed to get card info',
          },
          data: null,
          cardInfo: null,
        };
      }

      return { error: null, data: cardInfo, cardInfo };
    } catch (error) {
      return { error: toCallbackError(error), data: null, cardInfo: null };
    }
  }

  async checkNFCPermission(): Promise<PromiseResult<boolean>> {
    if (Platform.OS === 'android') {
      return { error: null, data: true, cardInfo: null };
    }

    return { error: null, data: true, cardInfo: null };
  }

  async setMnemonic(
    mnemonic: string,
    pwd: string,
    overwrite = false
  ): Promise<PromiseResult<boolean>> {
    if (!pwd.trim()) {
      return {
        error: {
          code: CardErrors.InputPasswordEmpty,
          message: 'Password is empty',
        },
        data: null,
        cardInfo: null,
      };
    }

    if (!mnemonic.trim()) {
      return {
        error: {
          code: CardErrors.ExecFailure,
          message: 'Mnemonic is empty',
        },
        data: null,
        cardInfo: null,
      };
    }

    try {
      const cardInfo = mapCardInfo(await LiteCardSdk.getCardInfo());

      if (!overwrite && cardInfo && (!cardInfo.isNewCard || cardInfo.hasBackup)) {
        return {
          error: {
            code: CardErrors.InitializedError,
            message: 'Card already initialized',
          },
          data: null,
          cardInfo,
        };
      }

      if (cardInfo?.isNewCard) {
        const activated = await LiteCardSdk.activateCard(pwd);
        if (!activated) {
          return {
            error: {
              code: CardErrors.InitPasswordError,
              message: 'Failed to activate card',
            },
            data: null,
            cardInfo,
          };
        }
      }

      const written = await LiteCardSdk.writeSlot(
        DEFAULT_SLOT,
        encodeUtf8(mnemonic),
        pwd
      );

      if (!written) {
        return {
          error: {
            code: CardErrors.ExecFailure,
            message: 'Failed to write mnemonic',
          },
          data: null,
          cardInfo,
        };
      }

      return {
        error: null,
        data: true,
        cardInfo: mapCardInfo(await LiteCardSdk.getCardInfo()),
      };
    } catch (error) {
      return { error: toCallbackError(error), data: null, cardInfo: null };
    }
  }

  async getMnemonicWithPin(pwd: string): Promise<PromiseResult<string>> {
    if (!pwd.trim()) {
      return {
        error: {
          code: CardErrors.InputPasswordEmpty,
          message: 'Password is empty',
        },
        data: null,
        cardInfo: null,
      };
    }

    try {
      const cardInfo = mapCardInfo(await LiteCardSdk.getCardInfo());

      if (!cardInfo || cardInfo.isNewCard || !cardInfo.hasBackup) {
        return {
          error: {
            code: CardErrors.NotInitializedError,
            message: 'Card is not initialized',
          },
          data: null,
          cardInfo,
        };
      }

      const result = await LiteCardSdk.readSlot(DEFAULT_SLOT, pwd);
      if (!result) {
        return {
          error: {
            code: CardErrors.PasswordWrong,
            message: 'Failed to read mnemonic',
          },
          data: null,
          cardInfo,
        };
      }

      return {
        error: null,
        data: decodeUtf8(result),
        cardInfo,
      };
    } catch (error) {
      return { error: toCallbackError(error), data: null, cardInfo: null };
    }
  }

  async changePin(
    oldPin: string,
    newPin: string
  ): Promise<PromiseResult<boolean>> {
    if (!oldPin.trim() || !newPin.trim()) {
      return {
        error: {
          code: CardErrors.InputPasswordEmpty,
          message: 'PIN is empty',
        },
        data: null,
        cardInfo: null,
      };
    }

    try {
      const cardInfo = mapCardInfo(await LiteCardSdk.getCardInfo());

      if (!cardInfo || cardInfo.isNewCard || !cardInfo.hasBackup) {
        return {
          error: {
            code: CardErrors.NotInitializedError,
            message: 'Card is not initialized',
          },
          data: null,
          cardInfo,
        };
      }

      const changed = await LiteCardSdk.changePin(oldPin, newPin);
      if (!changed) {
        return {
          error: {
            code: CardErrors.PasswordWrong,
            message: 'Failed to change PIN',
          },
          data: null,
          cardInfo,
        };
      }

      return {
        error: null,
        data: true,
        cardInfo: mapCardInfo(await LiteCardSdk.getCardInfo()),
      };
    } catch (error) {
      return { error: toCallbackError(error), data: null, cardInfo: null };
    }
  }

  async reset(): Promise<PromiseResult<boolean>> {
    try {
      const reset = await LiteCardSdk.resetCard();
      if (!reset) {
        return {
          error: {
            code: CardErrors.ExecFailure,
            message: 'Failed to reset card',
          },
          data: null,
          cardInfo: null,
        };
      }

      return {
        error: null,
        data: true,
        cardInfo: mapCardInfo(await LiteCardSdk.getCardInfo()),
      };
    } catch (error) {
      return { error: toCallbackError(error), data: null, cardInfo: null };
    }
  }

  cancel() {
    // Backup card SDK has no cancel API; kept for API compatibility.
  }

  intoSetting() {
    if (Platform.OS === 'android') {
      Linking.openSettings();
      return;
    }

    Linking.openSettings();
  }
}

const onekeyLite = new OnekeyLite();

export default onekeyLite;
export { LiteCardSdk, CardErrors };
export type * from './NativeReactNativeLiteCard';
