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

    static String lastName;

    // Meme logique que LicenceManager.check : version 2 sans nom, version 3 avec le nom du client.
    static String check(PublicKey pub, String key, String device, int today) throws Exception {
        lastName = null;
        String n = normalize(key);
        byte[] all = decode(n);
        if (all.length < 74 || n.length() != (all.length * 8 + 4) / 5) return "format";
        int version = all[0] & 0xFF;
        int payloadLen;
        if (version == 2) payloadLen = 10;
        else if (version == 3) payloadLen = 11 + (all[10] & 0xFF);
        else return "format";
        if (all.length != payloadLen + 64) return "format";
        byte[] payload = Arrays.copyOfRange(all, 0, payloadLen);
        byte[] sig = Arrays.copyOfRange(all, payloadLen, payloadLen + 64);
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
        int start = ((payload[6] & 0xFF) << 8) | (payload[7] & 0xFF);
        int expiry = ((payload[8] & 0xFF) << 8) | (payload[9] & 0xFF);
        if (version == 3) lastName = new String(payload, 11, payloadLen - 11, java.nio.charset.StandardCharsets.UTF_8);
        if (today < start) return "notyet";
        return today > expiry ? "expired" : "valid";
    }

    public static void main(String[] args) throws Exception {
        String json = Files.readString(Path.of(args[0]));
        Matcher pk = Pattern.compile("\"publicKey\": \"([^\"]+)\"").matcher(json);
        if (!pk.find()) throw new AssertionError("publicKey manquante");
        PublicKey pub = KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(pk.group(1))));

        Matcher m = Pattern.compile("\"key\": \"([^\"]+)\",\\s*\"device\": \"([^\"]+)\",\\s*\"today\": (\\d+),\\s*\"expect\": \"([a-z]+)\"(?:,\\s*\"name\": \"([^\"]+)\")?").matcher(json);
        int count = 0;
        java.util.Map<String, Integer> seen = new java.util.TreeMap<>();
        while (m.find()) {
            String got = check(pub, m.group(1), m.group(2), Integer.parseInt(m.group(3)));
            if (!got.equals(m.group(4))) throw new AssertionError("cas " + count + " : attendu " + m.group(4) + ", obtenu " + got);
            if (m.group(5) != null && !m.group(5).equals(lastName)) throw new AssertionError("nom attendu " + m.group(5) + ", obtenu " + lastName);
            if (m.group(5) != null) System.out.println("ok - nom " + lastName);
            seen.merge(got, 1, Integer::sum);
            count++;
        }
        if (count != 47) throw new AssertionError("47 cas attendus, " + count + " lus");
        System.out.println("ok - " + seen);

        // Le code appareil calcule cote appli doit etre accepte tel quel par le generateur.
        byte[] hash = MessageDigest.getInstance("SHA-256").digest("nuvio-licence-v1:abc123".getBytes("UTF-8"));
        String code = encode(Arrays.copyOfRange(hash, 0, 5));
        if (code.length() != 8 || !normalize(code).equals(code)) throw new AssertionError("code appareil " + code);
        System.out.println("ok - code appareil " + code.substring(0, 4) + "-" + code.substring(4));
        System.out.println("interop ok");
    }
}
