package com.example.ftransisdk;

/**
 * Created by 89125 on 2017/9/11.
 */

public class FrigerprintControl {
    static {
        System.loadLibrary("frigerprint");
    }

    public static  native int frigerprint_power_on();
    public static  native int frigerprint_power_off();
}
