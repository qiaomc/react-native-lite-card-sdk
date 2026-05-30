package com.ziancube.backupcardsdk.utils;

import java.math.BigInteger;

import static com.ziancube.backupcardsdk.nfc.NfcComm.ERROR_NFC_DISABLED;
import static com.ziancube.backupcardsdk.nfc.NfcComm.ERROR_NO_NFC;
import static com.ziancube.backupcardsdk.nfc.NfcComm.ERROR_NO_TAG;
import static com.ziancube.backupcardsdk.nfc.NfcComm.ERROR_RECV_DATA;
import static com.ziancube.backupcardsdk.nfc.NfcComm.EXCP_COMM_CONNECT;
import static com.ziancube.backupcardsdk.nfc.NfcComm.EXCP_COMM_DISCONNECT;
import static com.ziancube.backupcardsdk.nfc.NfcComm.EXCP_COMM_TRANSCEIVE;
import static com.ziancube.backupcardsdk.nfc.NfcComm.FT_FAIL;
import static com.ziancube.backupcardsdk.nfc.NfcComm.FT_RECV_DATA_SPECIAL;
import static com.ziancube.backupcardsdk.nfc.NfcComm.FT_RECV_LEN_ERROR;

public class Utils {

    public static String bytesToHexString(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }

    public static byte[] hexString2Bytes(String hex) {
        return String2Bytes(hex, 16);
    }

    private static byte[] String2Bytes(String str, int digit) {
        byte[] bArray = new BigInteger("10".concat(str) , digit).toByteArray();
        byte[] ret = new byte[bArray.length - 1];
        for (int i = 0; i < ret.length; i++) {
            ret[i] = bArray[i + 1];
        }

        return ret;
    }


    public static String stringToHexString(String s) {
        String str = "";
        for (int i = 0; i < s.length(); i++) {
            int ch = s.charAt(i);
            String s4 = Integer.toHexString(ch);
            str = str.concat(s4);
        }
        return str;
    }

    public static String hexStringToString(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i += 2) {
            String hexPair = s.substring(i, i + 2);
            int charCode = Integer.parseInt(hexPair, 16);
            sb.append((char) charCode);
        }
        return sb.toString();
    }

    public static String buildTLV(String tag,String value){
        int len = value.length() / 2;
        String lenHex = String.format("%02X", len);
        return (tag != null ? tag : "") + lenHex + value;
    }

    public static String getError(int ret) {
        String msg;
        switch (ret) {
            case 0:
                msg = "success";
                break;
            case FT_FAIL:
                msg = "error: fail";
                break;
            case FT_RECV_DATA_SPECIAL:
                msg = "error: receive data 6c8f";
                break;
            case FT_RECV_LEN_ERROR:
                msg = "error: receive data len error";
                break;
            case EXCP_COMM_CONNECT:
                msg = "error: connect except";
                break;
            case EXCP_COMM_DISCONNECT:
                msg = "error: disconnect except";
                break;
            case EXCP_COMM_TRANSCEIVE:
                msg = "error: transceive except";
                break;
            case ERROR_NO_TAG:
                msg = "error: no tag";
                break;
            case ERROR_NO_NFC:
                msg = "error: no nfc";
                break;
            case ERROR_NFC_DISABLED:
                msg = "error: nfc disabled";
                break;
            case ERROR_RECV_DATA:
                msg = "error: receive data error";
                break;
            default:
                msg = "error : other error";
                break;
        }
        return msg;
    }
}
