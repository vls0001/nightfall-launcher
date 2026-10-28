package dev.nightfall.launcher;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

import javax.crypto.Cipher;

/**
 * Minimal ADB client that talks to the TV's own debugging service on 127.0.0.1:5555.
 * This lets Nightfall run a few shell commands (like enabling its Home button helper)
 * without needing a computer. The TV shows an "Allow debugging?" prompt the first time.
 */
public class AdbShell {
    private static final int CNXN = 0x4e584e43;
    private static final int AUTH = 0x48545541;
    private static final int OPEN = 0x4e45504f;
    private static final int OKAY = 0x59414b4f;
    private static final int CLSE = 0x45534c43;
    private static final int WRTE = 0x45545257;

    private static final int AUTH_TOKEN = 1;
    private static final int AUTH_SIGNATURE = 2;
    private static final int AUTH_RSAPUBLICKEY = 3;

    private static final int VERSION = 0x01000000;
    private static final int MAX_DATA = 4096;

    // ASN.1 DigestInfo prefix for SHA-1; adbd signs the 20-byte token as if it were a SHA-1 hash.
    private static final byte[] SHA1_PREFIX = {
            0x30, 0x21, 0x30, 0x09, 0x06, 0x05, 0x2b, 0x0e, 0x03, 0x02, 0x1a, 0x05, 0x00, 0x04, 0x14};

    public static class NotEnabledException extends IOException {
        NotEnabledException(String m) {
            super(m);
        }
    }

    private static class Msg {
        int cmd, arg0, arg1;
        byte[] data;
    }

    private final Socket socket;
    private final DataInputStream in;
    private final OutputStream out;
    private int nextLocalId = 1;

    private AdbShell(Socket s) throws IOException {
        socket = s;
        in = new DataInputStream(s.getInputStream());
        out = s.getOutputStream();
    }

    /** Connects and authenticates. May wait up to a minute for the person to accept the TV prompt. */
    public static AdbShell connect(Context ctx) throws Exception {
        Socket s = new Socket();
        try {
            s.connect(new InetSocketAddress("127.0.0.1", 5555), 3000);
        } catch (IOException e) {
            throw new NotEnabledException("ADB debugging is off");
        }
        AdbShell adb = new AdbShell(s);
        try {
            adb.handshake(loadKeys(ctx));
        } catch (Exception e) {
            adb.close();
            throw e;
        }
        return adb;
    }

    private void handshake(KeyPair keys) throws Exception {
        socket.setSoTimeout(10000);
        send(CNXN, VERSION, MAX_DATA, "host::nightfall\0".getBytes("UTF-8"));
        boolean sentSignature = false;
        while (true) {
            Msg m = read();
            if (m.cmd == CNXN) return;
            if (m.cmd != AUTH || m.arg0 != AUTH_TOKEN) throw new IOException("Unexpected reply from ADB");
            if (!sentSignature) {
                send(AUTH, AUTH_SIGNATURE, 0, sign((RSAPrivateKey) keys.getPrivate(), m.data));
                sentSignature = true;
            } else {
                // Key not trusted yet: offer it, which pops up the "Allow debugging?" prompt on the TV.
                byte[] pub = (androidPublicKey((RSAPublicKey) keys.getPublic()) + " nightfall@firetv\0").getBytes("UTF-8");
                send(AUTH, AUTH_RSAPUBLICKEY, 0, pub);
                socket.setSoTimeout(90000);
                Msg reply = read();
                if (reply.cmd == CNXN) return;
                throw new IOException("Debugging permission was not allowed on the TV");
            }
        }
    }

    /** Runs one shell command and returns its output. */
    public String shell(String command) throws IOException {
        socket.setSoTimeout(30000);
        int local = nextLocalId++;
        send(OPEN, local, 0, ("shell:" + command + "\0").getBytes("UTF-8"));
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        int remote = 0;
        while (true) {
            Msg m = read();
            if (m.arg1 != local) continue;
            if (m.cmd == OKAY) {
                remote = m.arg0;
            } else if (m.cmd == WRTE) {
                remote = m.arg0;
                if (m.data != null) result.write(m.data);
                send(OKAY, local, remote, null);
            } else if (m.cmd == CLSE) {
                send(CLSE, local, m.arg0, null);
                break;
            }
        }
        return result.toString("UTF-8").trim();
    }

    public void close() {
        try {
            socket.close();
        } catch (IOException ignored) {
        }
    }

    // ---------- Wire format ----------

    private void send(int cmd, int arg0, int arg1, byte[] data) throws IOException {
        int len = data == null ? 0 : data.length;
        int sum = 0;
        for (int i = 0; i < len; i++) sum += data[i] & 0xff;
        ByteBuffer b = ByteBuffer.allocate(24 + len).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(cmd).putInt(arg0).putInt(arg1).putInt(len).putInt(sum).putInt(~cmd);
        if (len > 0) b.put(data);
        out.write(b.array());
        out.flush();
    }

    private Msg read() throws IOException {
        byte[] head = new byte[24];
        in.readFully(head);
        ByteBuffer b = ByteBuffer.wrap(head).order(ByteOrder.LITTLE_ENDIAN);
        Msg m = new Msg();
        m.cmd = b.getInt();
        m.arg0 = b.getInt();
        m.arg1 = b.getInt();
        int len = b.getInt();
        if (len < 0 || len > 1024 * 1024) throw new IOException("Bad ADB message");
        if (len > 0) {
            m.data = new byte[len];
            in.readFully(m.data);
        }
        return m;
    }

    // ---------- Keys ----------

    private static KeyPair loadKeys(Context ctx) throws Exception {
        SharedPreferences p = ctx.getSharedPreferences("adbkey", Context.MODE_PRIVATE);
        String priv = p.getString("priv", null), pub = p.getString("pub", null);
        KeyFactory kf = KeyFactory.getInstance("RSA");
        if (priv != null && pub != null) {
            return new KeyPair(
                    kf.generatePublic(new X509EncodedKeySpec(Base64.decode(pub, Base64.NO_WRAP))),
                    kf.generatePrivate(new PKCS8EncodedKeySpec(Base64.decode(priv, Base64.NO_WRAP))));
        }
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        KeyPair kp = gen.generateKeyPair();
        p.edit()
                .putString("priv", Base64.encodeToString(kp.getPrivate().getEncoded(), Base64.NO_WRAP))
                .putString("pub", Base64.encodeToString(kp.getPublic().getEncoded(), Base64.NO_WRAP))
                .apply();
        return kp;
    }

    private static byte[] sign(RSAPrivateKey key, byte[] token) throws Exception {
        int k = (key.getModulus().bitLength() + 7) / 8;
        byte[] block = new byte[k];
        int tLen = SHA1_PREFIX.length + token.length;
        block[0] = 0;
        block[1] = 1;
        for (int i = 2; i < k - tLen - 1; i++) block[i] = (byte) 0xff;
        block[k - tLen - 1] = 0;
        System.arraycopy(SHA1_PREFIX, 0, block, k - tLen, SHA1_PREFIX.length);
        System.arraycopy(token, 0, block, k - token.length, token.length);
        Cipher c = Cipher.getInstance("RSA/ECB/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, key);
        return c.doFinal(block);
    }

    /** The public key in the format adbd stores in adb_keys (mincrypt RSAPublicKey, base64). */
    static String androidPublicKey(RSAPublicKey key) {
        BigInteger n = key.getModulus();
        int words = 2048 / 32;
        BigInteger r32 = BigInteger.ONE.shiftLeft(32);
        BigInteger n0inv = n.mod(r32).modInverse(r32).negate().mod(r32);
        BigInteger rr = BigInteger.ONE.shiftLeft(2048).pow(2).mod(n);

        ByteBuffer b = ByteBuffer.allocate(4 + 4 + words * 4 + words * 4 + 4).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(words);
        b.putInt(n0inv.intValue());
        putLittleEndianWords(b, n, words);
        putLittleEndianWords(b, rr, words);
        b.putInt(key.getPublicExponent().intValue());
        return Base64.encodeToString(b.array(), Base64.NO_WRAP);
    }

    private static void putLittleEndianWords(ByteBuffer b, BigInteger v, int words) {
        BigInteger mask = BigInteger.ONE.shiftLeft(32).subtract(BigInteger.ONE);
        for (int i = 0; i < words; i++) {
            b.putInt(v.shiftRight(32 * i).and(mask).intValue());
        }
    }
}
