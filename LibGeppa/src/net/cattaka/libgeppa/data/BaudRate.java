
package net.cattaka.libgeppa.data;

import jp.ksksue.driver.serial.FTDriver;

public enum BaudRate {
    BAUD300(FTDriver.BAUD300), //
    BAUD600(FTDriver.BAUD600), //
    BAUD1200(FTDriver.BAUD1200), //
    BAUD2400(FTDriver.BAUD2400), //
    BAUD4800(FTDriver.BAUD4800), //
    BAUD9600(FTDriver.BAUD9600), //
    BAUD14400(FTDriver.BAUD14400), //
    BAUD19200(FTDriver.BAUD19200), //
    BAUD38400(FTDriver.BAUD38400), //
    BAUD57600(FTDriver.BAUD57600), //
    BAUD115200(FTDriver.BAUD115200), //
    BAUD230400(FTDriver.BAUD230400);

    private int baud;

    private BaudRate(int baud) {
        this.baud = baud;
    }

    public int getBaud() {
        return baud;
    }
}
