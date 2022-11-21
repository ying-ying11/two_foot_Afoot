package com.flyn.sarcopenia_project.service;

import android.util.Log;

class EmgDecoder {

    final static String TAG = "EMG Decoder";

    static short[] decode(byte[] raw) {
        int packageNum = raw[0] & 0xFF;
//        Log.d(TAG, "package number: " + packageNum);

        int size = raw[1] & 0xFF;
        short[] result = new short[size];

        // checksum calculate
        byte checksum = 0;
        for (int i = 0; i < raw.length - 1; i++) checksum += raw[i] & 0xFF;
        if (checksum != raw[raw.length - 1]) {
            System.out.println("checksum error: " + (checksum & 0xFF));
            return null;
        }

        int loc = 2;
        for (int i = 0; i < size; i++) {
            int highBit, lowBit;
            if (i % 2 == 1) {
                highBit = raw[loc] & 0x0F;
                lowBit = raw[loc + 2] & 0xFF;
                loc += 3;
            }
            else {
                highBit = (raw[loc] >> 4) & 0x0F;
                lowBit = raw[loc + 1] & 0xFF;
            }
            result[i] = (short) ((highBit << 8) | lowBit);
        }
        return result;
    }

}
