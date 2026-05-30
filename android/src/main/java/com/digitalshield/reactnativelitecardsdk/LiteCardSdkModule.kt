package com.digitalshield.reactnativelitecardsdk

import android.content.Intent
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.BaseActivityEventListener
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReadableArray
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.WritableMap
import com.facebook.react.bridge.WritableArray
import com.facebook.react.bridge.LifecycleEventListener
import com.ziancube.backupcardsdk.ApduLogger
import com.ziancube.backupcardsdk.BackupCardSdk
import com.ziancube.backupcardsdk.NfcTouchListener
import com.ziancube.backupcardsdk.CardInfo
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

fun CardInfo.toWritableMap(): WritableMap {
  val map = Arguments.createMap()
  map.putString("serialNumber", serialNumber)
  map.putInt("pinRetryCount", pinRetryCount ?: -1)
  map.putBoolean("isNewCard", isNewCard ?: false)
  return map
}

fun ReadableArray.toByteArray(): ByteArray {
  val bytes = ByteArray(size())
  for (i in 0 until size()) {
    bytes[i] = getInt(i).toByte()
  }
  return bytes
}

fun ByteArray?.toWritableArrayOrNull(): WritableArray? {
  if (this == null) return null
  val array = Arguments.createArray()
  for (b in this) {
    array.pushInt(b.toInt() and 0xFF)
  }
  return array
}


class LiteCardSdkModule(val reactContext: ReactApplicationContext) :
        NativeLiteCardSdkSpec(reactContext) ,LifecycleEventListener
{
  private lateinit var backupCardSdk: BackupCardSdk
  private val tag = "LiteCardSdkModule"
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
  override fun invalidate() {
    super.invalidate()
    scope.cancel()
  }
  private val mActivityEventListener =
          object : BaseActivityEventListener() {
            override fun onNewIntent(intent: Intent) {
              super.onNewIntent(intent)
              backupCardSdk.handleIntent(intent)
            }
          }

  override fun initialize() {
    super.initialize()
    Utils.init(reactContext)
    Utils.getActivityLifecycle()
    val activity = Utils.getTopActivity()
    if (activity == null) {
      return
    }
    val apduLogger =
            object : ApduLogger {
              override fun log(message: String, isSent: Boolean, isSuccess: Boolean) {
                val params =
                        Arguments.createMap()
                                .apply {
                                  putString("message", message)
                                  putBoolean("isSent", isSent)
                                  putBoolean("isSuccess", isSuccess)
                                }
                                .copy()
                emitOnApduLog(params)
              }
            }
    val nfcTouchListener =
            object : NfcTouchListener {
              override fun onTouch(isBackupCard: Boolean) {
                val params =
                        Arguments.createMap()
                                .apply { putBoolean("isBackupCard", isBackupCard) }
                                .copy()
                emitOnNfcTouch(params)
              }
            }
    backupCardSdk = BackupCardSdk(activity, apduLogger, nfcTouchListener)
    reactContext.addActivityEventListener(mActivityEventListener)
    reactContext.addLifecycleEventListener(this)
  }

  companion object {
    const val NAME = NativeLiteCardSdkSpec.NAME
  }

  override fun getCardInfo(promise: Promise) =
        promise.launchSuspend(
                block = { backupCardSdk.getCardInfo() },
                transform = { info -> info?.toWritableMap() }
        )

    override fun resetCard(promise: Promise) =
      promise.launchSuspend(
          block = { backupCardSdk.resetCard() },
          transform = { result -> result }
      )

      override fun activateCard(pwd: String, promise: Promise) =
        promise.launchSuspend(
          block = { backupCardSdk.activateCard(pwd) },
          transform = { result -> result }
        )

      override fun changePin(oldPin: String, newPin: String, promise: Promise) =
        promise.launchSuspend(
          block = { backupCardSdk.changePin(oldPin, newPin) },
          transform = { result -> result }
        )

      override fun checkSlotEmpty(slotId: Double, pwd: String, promise: Promise) =
        promise.launchSuspend(
          block = { backupCardSdk.checkSlotEmpty(slotId.toInt(), pwd) },
          transform = { result -> result }
        )

      override fun writeSlot(slotIndex: Double, data: ReadableArray, pwd: String, promise: Promise) =
        promise.launchSuspend(
          block = { backupCardSdk.writeSlot(slotIndex.toInt(), data.toByteArray(), pwd) },
          transform = { result -> result }
        )

      override fun readSlot(slotIndex: Double, pwd: String, promise: Promise) =
        promise.launchSuspend(
          block = { backupCardSdk.readSlot(slotIndex.toInt(), pwd) },
          transform = { result -> result.toWritableArrayOrNull() }
        )



  private fun <T, R> Promise.launchSuspend(
          dispatcher: CoroutineDispatcher = Dispatchers.IO,
          block: suspend () -> T,
          transform: (T) -> R,
          errorCode: (Throwable) -> String = { "E_UNEXPECTED" }
  ) {
    scope.launch {
      try {
        val result = withContext(dispatcher) { block() }

        val mapped = transform(result)

        resolve(mapped)
      } catch (t: Throwable) {
        reject(errorCode(t), t.message, t)
      }
    }
  }

  override fun onHostResume() {
    backupCardSdk.onActivityResumed()
  }

  override fun onHostPause() {
    backupCardSdk.onActivityPaused()
  }

  override fun onHostDestroy() {
    backupCardSdk.onActivityDestroyed()
  }
}
