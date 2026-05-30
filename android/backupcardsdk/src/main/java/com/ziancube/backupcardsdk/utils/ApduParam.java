package com.ziancube.backupcardsdk.utils;

public class ApduParam {
    private long cla;
    private long ins;
    private long p1;
    private long p2;
    private String data;
    private boolean logToMain;
    private boolean secureChannel;
    private boolean needData;
    private String apdu;

    public ApduParam(long cla, long ins, long p1, long p2, String data, boolean secureChannel) {
        super();
        this.cla = cla;
        this.ins = ins;
        this.p1 = p1;
        this.p2 = p2;
        this.data = data;
        this.secureChannel = secureChannel;
        this.apdu = "";
    }
    public long getCla() {
        return cla;
    }
    public void setCla(long cla) {
        this.cla = cla;
    }
    public long getIns() {
        return ins;
    }
    public void setIns(long ins) {
        this.ins = ins;
    }
    public long getP1() {
        return p1;
    }
    public void setP1(long p1) {
        this.p1 = p1;
    }
    public long getP2() {
        return p2;
    }
    public void setP2(long p2) {
        this.p2 = p2;
    }
    public String getData() {
        return data;
    }
    public void setData(String data) {
        this.data = data;
    }
    public boolean isSecureChannel() {
        return secureChannel;
    }
    public void setSecureChannel(boolean secureChannel) {
        this.secureChannel = secureChannel;
    }
    public String getApdu() {
        return apdu;
    }
    public void setApdu(String apdu) {
        this.apdu = apdu;
    }

}
