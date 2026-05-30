import {
  TurboModuleRegistry,
  type TurboModule,
  type CodegenTypes,
} from 'react-native';

export type CardInfo = {
  serialNumber: string | null;
  pinRetryCount: number | null;
  isNewCard: boolean | null;
};

export interface Spec extends TurboModule {
  getCardInfo(): Promise<CardInfo | null>;
  resetCard(): Promise<Boolean>;
  activateCard(pwd: string): Promise<Boolean>;
  changePin(oldPin: string, newPin: string): Promise<Boolean>;
  checkSlotEmpty(slotId: number, pwd: string): Promise<Boolean>;
  writeSlot(
    slotIndex: number,
    data: Array<number>,
    pwd: string
  ): Promise<Boolean>;
  readSlot(slotIndex: number, pwd: string): Promise<Array<number> | null>;

  readonly onApduLog: CodegenTypes.EventEmitter<{
    message: string;
    isSent: boolean;
    isSuccess: boolean;
  }>;

  readonly onNfcTouch: CodegenTypes.EventEmitter<{
    isBackupCard: boolean;
  }>;
}

export default TurboModuleRegistry.getEnforcing<Spec>('LiteCardSdk');
