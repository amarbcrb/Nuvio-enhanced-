// Verifie que des licences creees par generateur.html sont acceptees avec exactement les memes
// appels Java que LicenceManager.kt (KeyFactory "EC", Signature "SHA256withECDSA", conversion r||s -> DER).
// Lancement : node licence/test/core.test.js vecteurs.json && java licence/test/InteropTest.java vecteurs.json
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class InteropTest {
    static final String ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";

    static String normalize(String text) {
        StringBuilder sb = new StringBuilder();
        for (char c : text.toUpperCase().toCharArray()) {
            char m = c == 'O' ? '0' : (c == 'I' || c == 'L') ? '1' : c;
            if (ALPHABET.indexOf(m) >= 0) sb.append(m);
        }
        return sb.toString();
    }

    static byte[] decode(String s) {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        int bits = 0, value = 0;
        for (char c : s.toCharArray()) {
            value = (value << 5) | ALPHABET.indexOf(c);
            bits += 5;
            if (bits >= 8) {
                out.write((value >>> (bits - 8)) & 0xFF);
                bits -= 8;
                value &= (1 << bits) - 1;
            }
        }
        return out.toByteArray();
    }

    static String encode(byte[] bytes) {
        StringBuilder out = new StringBuilder();
        int bits = 0, value = 0;
        for (byte b : bytes) {
            value = (value << 8) | (b & 0xFF);
            bits += 8;
            while (bits >= 5) { out.append(ALPHABET.charAt((value >>> (bits - 5)) & 31)); bits -= 5; }
            value &= (1 << bits) - 1;
        }
        if (bits > 0) out.append(ALPHABET.charAt((value << (5 - bits)) & 31));
        return out.toString();
    }

    // Meme logique que LicenceFormat.rawSignatureToDer dans LicenceManager.kt.
    static byte[] derInteger(byte[] bytes) {
        int start = 0;
        while (start < bytes.length - 1 && bytes[start] == 0) start++;
        byte[] value = Arrays.copyOfRange(bytes, start, bytes.length);
        if ((value[0] & 0x80) != 0) {
            byte[] padded = new byte[value.length + 1];
            System.arraycopy(value, 0, padded, 1, value.length);
            value = padded;
        }
        byte[] out = new byte[value.length + 2];
        out[0] = 0x02;
        out[1] = (byte) value.length;
        System.arraycopy(value, 0, out, 2, value.length);
        return out;
    }

    static byte[] rawSignatureToDer(byte[] raw) {
        byte[] r = derInteger(Arrays.copyOfRange(raw, 0, 32));
        byte[] s = derInteger(Arrays.copyOfRange(raw, 32, 64));
        byte[] out = new byte[r.length + s.length + 2];
        out[0] = 0x30;
        out[1] = (byte) (r.length + s.length);
        System.arraycopy(r, 0, out, 2, r.length);
        System.arraycopy(s, 0, out, 2 + r.length, s.length);
        return out;
    }

    static String check(PublicKey pub, String key, String device, int today) throws Exception {
        String n = normalize(key);
        if (n.length() != 116) return "format";
        byte[] all = decode(n);
        byte[] payload = Arrays.copyOfRange(all, 0, 8);
        byte[] sig = Arrays.copyOfRange(all, 8, 72);
        if (payload[0] != 1) return "format";
        boolean ok;
        try {
            Signature v = Signature.getInstance("SHA256withECDSA");
            v.initVerify(pub);
            v.update(payload);
            ok = v.verify(rawSignatureToDer(sig));
        } catch (java.security.SignatureException e) {
            ok = false;
        }
        if (!ok) return "signature";
        byte[] dev = decode(normalize(device));
        for (int i = 0; i < dev.length; i++) if (payload[1 + i] != dev[i]) return "device";
        int expiry = ((payload[6] & 0xFF) << 8) | (payload[7] & 0xFF);
        return today > expiry ? "expired" : "valid";
    }

    public static void main(String[] args) throws Exception {
        String json = Files.readString(Path.of(args[0]));
        Matcher pk = Pattern.compile("\"publicKey\": \"([^\"]+)\"").matcher(json);
        if (!pk.find()) throw new AssertionError("publicKey manquante");
        PublicKey pub = KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(pk.group(1))));

        Matcher m = Pattern.compile("\"key\": \"([^\"]+)\",\\s*\"device\": \"([^\"]+)\",\\s*\"today\": (\\d+),\\s*\"expect\": \"([a-z]+)\"").matcher(json);
        int count = 0;
        java.util.Map<String, Integer> seen = new java.util.TreeMap<>();
        while (m.find()) {
            String got = check(pub, m.group(1), m.group(2), Integer.parseInt(m.group(3)));
            if (!got.equals(m.group(4))) throw new AssertionError("cas " + count + " : attendu " + m.group(4) + ", obtenu " + got);
            seen.merge(got, 1, Integer::sum);
            count++;
        }
        if (count != 43) throw new AssertionError("43 cas attendus, " + count + " lus");
        System.out.println("ok - " + seen);

        // Le code appareil calcule cote appli doit etre accepte tel quel par le generateur.
        byte[] hash = MessageDigest.getInstance("SHA-256").digest("nuvio-licence-v1:abc123".getBytes("UTF-8"));
        String code = encode(Arrays.copyOfRange(hash, 0, 5));
        if (code.length() != 8 || !normalize(code).equals(code)) throw new AssertionError("code appareil " + code);
        System.out.println("ok - code appareil " + code.substring(0, 4) + "-" + code.substring(4));
        System.out.println("interop ok");
    }
}
