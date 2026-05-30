import { useEffect, useRef, useState } from 'react';
import {
  Text,
  View,
  StyleSheet,
  TouchableOpacity,
  ScrollView,
  NativeEventEmitter,
  ToastAndroid,
  Platform,
  Alert,
} from 'react-native';

import LiteCardSdk from 'react-native-lite-card-sdk';

const buttonTexts = [
  'Get Card Info',
  'Reset Card',
  'Activate Card',
  'Change PIN',
  'Check Slot',
  'Write Slot',
  'Read Slot',
];

export default function App() {
  const [content, setContent] = useState<Array<{ text: string; color: string }>>([]);
  const scrollViewRef = useRef<ScrollView>(null);
  const test_pin = "555555";
  const new_pin = "123456";
  const test_slot_id = 1;
  const test_data = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon";


  useEffect(() => {
    const sub1 = LiteCardSdk.onApduLog(event => {
      console.log('APDU Log:', event.message);
      const color =
        event.isSuccess === false
          ? '#eb5757'
          : event.isSent === true
            ? '#2f80ed'
            : '#27ae60';
      const text = `${event.isSent === true ? '===>  ' : '<====  '}${event.message}`;
      setContent((prev) => [...prev, { text, color }]);
    });

    const sub2 = LiteCardSdk.onNfcTouch(event => {
      console.log('Touch:', event.isBackupCard);
      const message = event.isBackupCard ? '检测到备份卡' : '检测到非备份卡';
      if (Platform.OS === 'android') {
        ToastAndroid.show(message, ToastAndroid.SHORT);
      } else {
        Alert.alert('提示', message);
      }

    });

    return () => {
      sub1.remove();
      sub2.remove();
    };
  }, []);



  const handleButtonPress = async (label: string) => {
    setContent([]);

    if (label === 'Get Card Info') {
      try {
        const result = await LiteCardSdk.getCardInfo();
        const text = `getCardInfo: ${result ? JSON.stringify(result) : 'null'}`;
        setContent((prev) => [...prev, { text, color: '#9b51e0' }]);
      } catch (error) {
        const text = `getCardInfo error: ${String(error)}`;
        setContent((prev) => [...prev, { text, color: '#9b51e0' }]);
      }
      return;
    }

    if (label === 'Reset Card') {
      try {
        const result = await LiteCardSdk.resetCard();
        const text = `resetCard: ${result}`;
        setContent((prev) => [...prev, { text, color: '#9b51e0' }]);
      } catch (error) {
        const text = `resetCard error: ${String(error)}`;
        setContent((prev) => [...prev, { text, color: '#9b51e0' }]);
      }
      return;
    }

    if (label === 'Activate Card') {
      try {
        const result = await LiteCardSdk.activateCard(test_pin);
        const text = `activateCard: ${result}`;
        setContent((prev) => [...prev, { text, color: '#9b51e0' }]);
      } catch (error) {
        const text = `activateCard error: ${String(error)}`;
        setContent((prev) => [...prev, { text, color: '#9b51e0' }]);
      }
      return;
    }

    if (label === 'Change PIN') {
      try {
        const result = await LiteCardSdk.changePin(test_pin, new_pin);
        const text = `changePin: ${result}`;
        setContent((prev) => [...prev, { text, color: '#9b51e0' }]);
      } catch (error) {
        const text = `changePin error: ${String(error)}`;
        setContent((prev) => [...prev, { text, color: '#9b51e0' }]);
      }
      return;
    }

    if (label === 'Check Slot') {
      try {
        const result = await LiteCardSdk.checkSlotEmpty(test_slot_id, new_pin);
        const text = `checkSlotEmpty: ${result}`;
        setContent((prev) => [...prev, { text, color: '#9b51e0' }]);
      } catch (error) {
        const text = `checkSlotEmpty error: ${String(error)}`;
        setContent((prev) => [...prev, { text, color: '#9b51e0' }]);
      }
      return;
    }

    if (label === 'Write Slot') {
      try {
        const utf8Array = Array.from(new TextEncoder().encode(test_data));
        const result = await LiteCardSdk.writeSlot(test_slot_id, utf8Array, new_pin);
        const text = `writeSlot: ${result}`;
        setContent((prev) => [...prev, { text, color: '#9b51e0' }]);
      } catch (error) {
        const text = `writeSlot error: ${String(error)}`;
        setContent((prev) => [...prev, { text, color: '#9b51e0' }]);
      }
      return;
    }

    if (label === 'Read Slot') {
      try {
        const result = await LiteCardSdk.readSlot(test_slot_id, new_pin);
        const decoded = result
          ? decodeURIComponent(
              Array.from(Uint8Array.from(result))
                .map((byte) => `%${byte.toString(16).padStart(2, '0')}`)
                .join('')
            )
          : 'null';
        const text = `readSlot: ${decoded}`;
        setContent((prev) => [...prev, { text, color: '#9b51e0' }]);
      } catch (error) {
        const text = `readSlot error: ${String(error)}`;
        setContent((prev) => [...prev, { text, color: '#9b51e0' }]);
      }
      return;
    }


  };

  return (
    <View style={styles.container}>
      <View style={styles.topHalf}>
        {buttonTexts.map((label) => (
          <TouchableOpacity
            key={label}
            style={styles.button}
            activeOpacity={0.7}
            onPress={() => handleButtonPress(label)}
          >
            <Text style={styles.buttonText}>{label}</Text>
          </TouchableOpacity>
        ))}
      </View>

      <View style={styles.bottomHalf}>
        <ScrollView
          ref={scrollViewRef}
          style={styles.textArea}
          contentContainerStyle={styles.textAreaContent}
          onContentSizeChange={() => scrollViewRef.current?.scrollToEnd({ animated: true })}
        >
          {content.length ? (
            content.map((item, index) => (
              <Text
                key={`${item.text}-${index}`}
                style={[styles.outputLine, { color: item.color }]}
              >
                {item.text}
              </Text>
            ))
          ) : (
            <Text style={styles.placeholderText}></Text>
          )}
        </ScrollView>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  topHalf: {
    flex: 1,
    paddingHorizontal: 12,
    paddingTop: 16,
    gap: 8,
  },
  bottomHalf: {
    flex: 1,
    padding: 12,
  },
  button: {
    width: '100%',
    height: 44,
    backgroundColor: '#2f80ed',
    borderRadius: 8,
    alignItems: 'center',
    justifyContent: 'center',
  },
  buttonText: {
    color: '#fff',
    fontSize: 16,
    fontWeight: '600',
  },
  textArea: {
    flex: 1,
    borderWidth: 1,
    borderColor: '#d0d0d0',
    borderRadius: 8,
    padding: 12,
  },
  textAreaContent: {
    flexGrow: 1,
    paddingBottom: 12,
  },
  outputLine: {
    fontSize: 16,
    marginBottom: 6,
  },
  placeholderText: {
    color: '#999',
    fontSize: 16,
  },
});
