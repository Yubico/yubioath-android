package com.yubico.yubikit.application;

import android.util.Log;

import com.yubico.yubikit.transport.Iso7816Backend;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;

public class AbstractApplication {
    private static final byte SW_MORE_DATA = (byte) 0x61;

    private final byte[] aid;
    private final byte insSendRemaining;
    private final Iso7816Backend backend;

    protected AbstractApplication(Iso7816Backend backend, byte[] aid, byte insSendRemaining) {
        this.backend = backend;
        this.aid = aid;
        this.insSendRemaining = insSendRemaining;
    }

    protected AbstractApplication(Iso7816Backend backend, byte[] aid) {
        this(backend, aid, (byte) 0xc0);
    }

    public Iso7816Backend getBackend() {
        return backend;
    }

    private byte[] doSend(int ins, int p1, int p2, byte[] data) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (byte b : data) {
            sb.append(String.format("%02x", b));
        }
        Log.d("yubikit", String.format("%s app SEND: 00 %02x %02x %02x %02x %s", getClass().getSimpleName(), (byte)ins, (byte)p1, (byte)p2, (byte)data.length, sb));
        byte[] resp = backend.send((byte) 0, (byte) ins, (byte) p1, (byte) p2, data);
        sb = new StringBuilder();
        for (byte b : resp) {
            sb.append(String.format("%02x", b));
        }
        Log.d("yubikit", String.format("%s app RECV: %s", getClass().getSimpleName(), sb));
        return resp;
    }

    public byte[] select() throws IOException, ApduException {
        return ApduException.getChecked(doSend(0xa4, 0x04, 0, aid));
    }

    public byte[] send(int ins, int p1, int p2, byte[] data) throws IOException, ApduException {
        ByteBuffer buffer = ByteBuffer.wrap(data);
        byte[] chunk = new byte[0xff];
        while (buffer.remaining() > 0xff) {
            buffer.get(chunk, 0, 0xff);
            doSend(ins, p1, p2, chunk);
        }
        chunk = Arrays.copyOfRange(data, buffer.position(), data.length);
        byte[] resp = doSend(ins, p1, p2, chunk);

        buffer = ByteBuffer.allocate(4096);
        buffer.put(resp, 0, resp.length - 2);

        byte sw1 = resp[resp.length - 2];
        byte sw2 = resp[resp.length - 1];

        while (sw1 == SW_MORE_DATA) {
            resp = doSend(insSendRemaining, 0, 0, new byte[0]);
            buffer.put(resp, 0, resp.length - 2);
            sw1 = resp[resp.length - 2];
            sw2 = resp[resp.length - 1];
        }
        buffer.put(sw1).put(sw2);

        return ApduException.getChecked(buffer.array(), 0, buffer.position());
    }
}
