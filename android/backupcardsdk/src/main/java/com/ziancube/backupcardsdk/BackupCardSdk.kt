package com.ziancube.backupcardsdk

import android.app.Activity
import android.app.Application
import android.text.TextUtils;
import android.content.Intent
import android.nfc.NfcAdapter
import android.os.Bundle
import androidx.core.util.Consumer
import com.ziancube.backupcardsdk.listener.ApiAsyncListener
import com.ziancube.backupcardsdk.nfc.ApiNfc
import org.json.JSONObject
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import com.ziancube.backupcardsdk.utils.ApduParam
import com.ziancube.backupcardsdk.utils.Utils

interface ApduLogger {
    fun log(message: String, isSent: Boolean, isSuccess: Boolean = true)
}

interface NfcTouchListener {
    fun onTouch(isBackupCard: Boolean)
}

data class ApduResult(val resultCode: Int, val data: String?)
data class CardInfo(val serialNumber: String?, val pinRetryCount: Int?, val isNewCard: Boolean?)

class BackupCardSdk(
        private val activity: Activity,
        private val apduLogger: ApduLogger,
        private val nfcTouchListener: NfcTouchListener
) {
    private val apiNfc = ApiNfc.getInstance(activity)
    private val OCE_CRT =
        "7f2181bc93036170704209646576656c6f7065725f2003617070950200805f2504202605265f24042036052653007f4946b041044f07c304cd5ff2d89bfc28771fe08be408563c58108cb8010e8788c6683bf693d70f62fb7c1f9195b18972b6cf6b91f45ba15818ea72533500a06deb55306400f001005f3746304402204cfd10dd22babed4c1a422a4cfadd02df9ac89436c309cf51ea64526c48a2afb022033ca1b474f3915c44739ed40bc30d8f4aa442718c042f2c3054675ed9df61215";
    private val AID = "6469676974736869656c64" // digitalshield
    private var needReconnect = true;
    private val application = activity.application

    private val lifecycleCallbacks =
            object : Application.ActivityLifecycleCallbacks {
                override fun onActivityResumed(target: Activity) {
                    if (target === activity) {
                        apiNfc.onResume()
                    }
                }

                override fun onActivityPaused(target: Activity) {
                    if (target === activity) {
                        apiNfc.onPause()
                    }
                }

                override fun onActivityDestroyed(target: Activity) {
                    if (target === activity) {
                        apiNfc.onDestroy()
                    }
                }

                override fun onActivityCreated(a: Activity, b: Bundle?) {}
                override fun onActivityStarted(a: Activity) {}
                override fun onActivityStopped(a: Activity) {}
                override fun onActivitySaveInstanceState(a: Activity, b: Bundle) {}
            }

    init {
        handleIntent(activity.intent)
        application.registerActivityLifecycleCallbacks(lifecycleCallbacks)
    }

    fun release() {
        application.unregisterActivityLifecycleCallbacks(lifecycleCallbacks)
    }

    fun handleIntent(intent: Intent?) {
        if (intent == null || !isNfcIntent(intent)) {
            return
        }
        needReconnect = true
        val re = apiNfc.setCardTag(intent)
        if (re == 0) {
            nfcTouchListener.onTouch(true)
        } else {
            nfcTouchListener.onTouch(false)
        }
    }

    private fun isNfcIntent(intent: Intent): Boolean {
        return intent.action == NfcAdapter.ACTION_TAG_DISCOVERED ||
                intent.action == NfcAdapter.ACTION_NDEF_DISCOVERED ||
                intent.action == NfcAdapter.ACTION_TECH_DISCOVERED
    }

    private fun buildApdu(cla: Long,ins: Long,p1: Long,p2: Long,data: String,isSecureChannel: Boolean): ByteArray? {
        if (isSecureChannel) {
            val safeApdu = GPChannelNatives.nativeGPCBuildSafeAPDU(cla, ins, p1, p2, data)
            apduLogger.log(safeApdu, true)
            return Utils.hexString2Bytes(safeApdu)
        }

        val apdu = GPChannelNatives.nativeGPCBuildAPDU(cla, ins, p1, p2, data)
        apduLogger.log(apdu, true)
        return Utils.hexString2Bytes(apdu)
    }

    private fun parseResponse(resp: String, isSecureChannel: Boolean): ApduResult? {
        val res = if (isSecureChannel) {
            GPChannelNatives.nativeGPCParseSafeAPDUResponse(resp)
        } else {
            GPChannelNatives.nativeGPCParseAPDUResponse(resp)
        }
        val apdu_result =  toApduResult(res, -1)
        apduLogger.log(resp, false, apdu_result.resultCode == 0x9000)
        return apdu_result
    }

    private fun toApduResult(parsedResponse: String?, fallbackCode: Int): ApduResult {
        if (parsedResponse.isNullOrBlank()) {
            return ApduResult(fallbackCode, null)
        }

        return runCatching {
            val json = JSONObject(parsedResponse)
            val response = json.optString("response").takeIf { it.isNotEmpty() }
            val wRet = json.optInt("wRet", fallbackCode)
            ApduResult(wRet, response)
        }.getOrElse {
            ApduResult(fallbackCode, parsedResponse)
        }
    }

    suspend private fun sendApdu(apduParam: ApduParam): ApduResult {
        val apdu_bytes = buildApdu(
                apduParam.cla,
                apduParam.ins,
                apduParam.p1,
                apduParam.p2,
                apduParam.data,
                apduParam.isSecureChannel
        ) ?: run {
            apduLogger.log("Failed to build APDU", false)
            return ApduResult(-1, null)
        }

        return suspendCoroutine { continuation ->
            apiNfc.transInstructions(
                    apdu_bytes,
                    object : ApiAsyncListener<String> {
                        override fun onUiChange() {}

                        override fun onResult(resultCode: Int, result: String?) {
                            if (resultCode == 0 && result != null) {
                                var apdu_result = parseResponse(result, apduParam.isSecureChannel)
                                if (apdu_result != null) {
                                    continuation.resume(apdu_result)
                                } else {
                                    needReconnect = true
                                    continuation.resume(ApduResult(-1, null))
                                }                               
                            } else {
                                needReconnect = true
                                continuation.resume(ApduResult(resultCode, null))
                            }
                        }
                    }
            )
        }
    }

    suspend private fun selectApplet(): Boolean {
        val apduSelectMf = ApduParam(0x00, 0xa4, 0x04, 0x00, "", false)
        var result = sendApdu(apduSelectMf)
        if(result.resultCode !=0x9000) {
            return false
        } 
        val apduSelectApplet = ApduParam(0x00, 0xa4, 0x04, 0x00, AID, false)
        result = sendApdu(apduSelectApplet)
        return result.resultCode == 0x9000
    }

    suspend private fun openSecureChannel(): Boolean {
        val apdu1 = ApduParam(0x80, 0xCA, 0xBF, 0x21, "A60483021518", false)
        var result = sendApdu(apdu1)
        if (result.resultCode != 0x9000 || result.data == null) {
            return false
        }
        runCatching {
            val cert = GPChannelNatives.nativeGPCTLVDecode(result.data)
            if (!TextUtils.isEmpty(cert)) {
                val certJson = JSONObject(cert)
                val subject = GPChannelNatives.nativeGPCParseCertificate(certJson.getString("value"))
                if (!TextUtils.isEmpty(subject)) {
                    val subjectJson = JSONObject(subject)
                    val conf = JSONObject().apply {
                        put("scpID", 0x1107)
                        put("keyUsage", 0x3C)
                        put("keyType", 0x88)
                        put("keyLength", 16)
                        put("hostID", "8080808080808080")
                        put("cardGroupID", subjectJson.getString("subjectID"))
                    }
                    val initResult = GPChannelNatives.nativeGPCInitialize(conf.toString())
                    if (initResult !=0) {
                        return false
                    }
                } else {
                    return false
                }
            } else {
                return false
            }
        }.onFailure {
            return false
        }

        var apdu2 = ApduParam(0x80, 0x2A, 0x18, 0x10, "", false)
        apdu2.setData(OCE_CRT)
        result = sendApdu(apdu2)
        if (result.resultCode != 0x9000) {
            return false
        }
        val authData = GPChannelNatives.nativeGPCBuildMutualAuthData()

        var aupd3 =  ApduParam(0x80, 0x82, 0x18, 0x15, "", false)
        aupd3.setData(authData)
        result = sendApdu(aupd3)
        if (result.resultCode != 0x9000) {
            return false
        }

        val open_ret = GPChannelNatives.nativeGPCOpenSecureChannel(result.data);
        if (open_ret != 0) {
            return false
        }

        return true
    }

    suspend private fun getSerialNumber(): String? {

        val apduGetSn = ApduParam(0x80, 0xCB, 0x80, 0x00, "DFFF028101", true)
        val result = sendApdu(apduGetSn)
        if (result.resultCode != 0x9000 || result.data == null) {
            return null
        }
        val serialBytes = Utils.hexString2Bytes(result.data) ?: return null
        return String(serialBytes, Charsets.UTF_8)
    }

    suspend private fun getPinRetryCount(): Int? {

        val apduGetPin = ApduParam(0x80, 0xCB, 0x80, 0x00, "DFFF028102", true)
        val result = sendApdu(apduGetPin)
        if (result.resultCode != 0x9000 || result.data == null) {
            return null
        }
        val pinBytes = Utils.hexString2Bytes(result.data) ?: return null
        if (pinBytes.isEmpty()) {
            return null
        }
        return pinBytes[0].toInt() and 0xFF
    }

    suspend private fun getPinStatus(): Boolean? {
        val apduGetStatus = ApduParam(0x80, 0xCB, 0x80, 0x00, "DFFF028105", true)
        val result = sendApdu(apduGetStatus)
        if (result.resultCode != 0x9000 || result.data == null) {
            return null
        }
        val statusBytes = Utils.hexString2Bytes(result.data) ?: return null
        if (statusBytes.isEmpty()) {
            return null
        }
        return statusBytes[0].toInt() == 0x02
    }

    suspend fun getCardInfo(): CardInfo? {
        if(needReconnect) {
            GPChannelNatives.nativeGPCFinalize()
            if (!selectApplet()) return null
            if (!openSecureChannel()) return null
            needReconnect = false
        }

        val serialNumber = getSerialNumber()
        val pinRetryCount = getPinRetryCount()
        val isNewCard = getPinStatus()
        return CardInfo(serialNumber, pinRetryCount, isNewCard)
    }

    suspend fun resetCard():Boolean{
        if(needReconnect){
            GPChannelNatives.nativeGPCFinalize()
            if (!selectApplet()) return false
            if (!openSecureChannel()) return false
            needReconnect = false
        }
        var apduReset =  ApduParam(0x80, 0xCB, 0x80, 0x00, "DFFE028205", true);
        val result = sendApdu(apduReset)
        if (result.resultCode != 0x9000) {
            return false
        }
        needReconnect = true
        return true
    }

    suspend fun activateCard(pwd:String):Boolean{
        if(needReconnect){
            GPChannelNatives.nativeGPCFinalize()
            if (!selectApplet()) return false
            if (!openSecureChannel()) return false
            needReconnect = false
        }
        val pinTLV = "00" + Utils.buildTLV(null, Utils.stringToHexString(pwd))
        val dataTLV = Utils.buildTLV("8204", pinTLV)
        val apduTLV = Utils.buildTLV("DFFE", dataTLV)
        var apduActivate =  ApduParam(0x80, 0xCB, 0x80, 0x00, apduTLV, true);
        val result = sendApdu(apduActivate)
        if (result.resultCode != 0x9000) {
            return false
        }
        return true 
    }

    suspend fun changePin(oldPin: String, newPin: String): Boolean {
        if(needReconnect){
            GPChannelNatives.nativeGPCFinalize()
            if (!selectApplet()) return false
            if (!openSecureChannel()) return false
            needReconnect = false
        }
        val oldPinTLV = Utils.buildTLV(null, Utils.stringToHexString(oldPin))
        val newPinTLV = Utils.buildTLV(null, Utils.stringToHexString(newPin))
        val dataTLV = Utils.buildTLV("8204", oldPinTLV + newPinTLV)
        val apduTLV = Utils.buildTLV("DFFE", dataTLV)
        var apduChangePin =  ApduParam(0x80, 0xCB, 0x80, 0x00, apduTLV, true);
        val result = sendApdu(apduChangePin)
        if (result.resultCode != 0x9000) {
            return false
        }
        return true 
    }

    suspend private fun verifyPin(pin: String): Boolean {
        if(needReconnect){
            GPChannelNatives.nativeGPCFinalize()
            if (!selectApplet()) return false
            if (!openSecureChannel()) return false
            needReconnect = false
        }
        val pinTLV = Utils.buildTLV(null, Utils.stringToHexString(pin))
        var apduVerifyPin =  ApduParam(0x80, 0x20, 0x00, 0x00, pinTLV, true);
        val result = sendApdu(apduVerifyPin)
        if (result.resultCode != 0x9000) {
            return false
        }
        return true 
    }

    suspend private fun logout(): Boolean {
        if(needReconnect){
            GPChannelNatives.nativeGPCFinalize()
            if (!selectApplet()) return false
            if (!openSecureChannel()) return false
            needReconnect = false
        }
        var apduLogout =  ApduParam(0x80, 0x21, 0x00, 0x00, "", true);
        val result = sendApdu(apduLogout)
        if (result.resultCode != 0x9000) {
            return false
        }
        return true 
    }

    suspend fun checkSlotEmpty(slotIndex: Int, pwd: String): Boolean? {
        if (slotIndex !in 0..39) {
            return null
        }
        if(needReconnect){
            GPChannelNatives.nativeGPCFinalize()
            if (!selectApplet()) return null
            if (!openSecureChannel()) return null
            needReconnect = false
        }
        if (!verifyPin(pwd)) {
            return null
        }
        val apduCheck = ApduParam(0x80, 0x6A, 0x00, 0x00, "", true);
        val result = sendApdu(apduCheck)
        if (result.resultCode != 0x9000 || result.data == null) {
            return null
        }
        val checkBytes = Utils.hexString2Bytes(result.data) ?: return null
        if (checkBytes.size < 5) {
            return null
        }
        val byteIndex = slotIndex / 8
        val bitIndex = slotIndex % 8
        val value = checkBytes[byteIndex].toInt() and 0xFF
        return ((value ushr bitIndex) and 0x01) == 0x00
    }

    suspend fun writeSlot(slotIndex: Int, data: ByteArray,pwd: String): Boolean {
        if (slotIndex !in 0..39) {
            return false
        }
        if (data.size > 231) {
            return false
        }

        if(needReconnect){
            GPChannelNatives.nativeGPCFinalize()
            if (!selectApplet()) return false
            if (!openSecureChannel()) return false
            needReconnect = false
        }

        return try {
            if(!verifyPin(pwd)) {
                return false
            }

            var apduWrite =  ApduParam(0x80, 0x3B, 0x00, slotIndex.toLong(), Utils.bytesToHexString(data), true);
            val result = sendApdu(apduWrite)
            if (result.resultCode != 0x9000) {
                return false
            }
            true
        } catch (e: Exception) {
            false
        } finally {
            runCatching { logout() }
        }
    }

    suspend fun readSlot(slotIndex: Int, pwd: String): ByteArray? {
        if (slotIndex !in 0..39) {
            return null
        }
        if (needReconnect) {
            GPChannelNatives.nativeGPCFinalize()
            if (!selectApplet()) return null
            if (!openSecureChannel()) return null
            needReconnect = false
        }

        return try {
            if (!verifyPin(pwd)) {
                null
            } else {
                val apduRead = ApduParam(0x80, 0x4B, 0x00, slotIndex.toLong(), "", true)
                val result = sendApdu(apduRead)
                if (result.resultCode != 0x9000 || result.data == null) {
                    null
                } else {
                    Utils.hexString2Bytes(result.data)
                }
            }
        } catch (e: Exception) {
            null
        } finally {
            runCatching { logout() }
        }
    }
    fun onActivityResumed(){
        apiNfc.onResume()
    }

    fun onActivityPaused(){
        apiNfc.onPause()
    }

    fun onActivityDestroyed(){
        apiNfc.onDestroy()
    }

}
