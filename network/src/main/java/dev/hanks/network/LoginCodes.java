package dev.hanks.network;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Purpose-bound HMAC codes. The website atomically consumes each code once. */
public final class LoginCodes {
    // Exactly 32 unambiguous characters; omit I, O, 0 and 1.
    private static final String BASE32 = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";
    private LoginCodes() {}
    public static boolean configured(String key) { return key != null && key.matches("[a-f0-9]{64}"); }
    public static String issue(String key, UUID player, long now) {
        var random = new SecureRandom();
        String nonce = "" + BASE32.charAt(random.nextInt(32)) + BASE32.charAt(random.nextInt(32));
        return code(key, player, Math.floorDiv(now,60_000), nonce);
    }
    public static String code(String key, UUID player, long minute, String nonce) {
        if (!configured(key) || nonce.length()!=2 || nonce.chars().anyMatch(c->BASE32.indexOf(c)<0))
            throw new IllegalArgumentException("Invalid login configuration");
        try {
            var mac=Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(HexFormat.of().parseHex(key),"HmacSHA256"));
            var digest=mac.doFinal(("ringshift:login:v1:"+player.toString().replace("-","")+":"+minute+":"+nonce).getBytes(StandardCharsets.UTF_8));
            var result=new StringBuilder(nonce);
            int buffer=0,bits=0;
            for(byte value:digest) {
                buffer=(buffer<<8)|(value&255); bits+=8;
                while(bits>=5 && result.length()<12) { bits-=5; result.append(BASE32.charAt((buffer>>>bits)&31)); }
                if(result.length()==12)break;
            }
            return result.toString();
        } catch (java.security.GeneralSecurityException error) { throw new IllegalStateException("Cannot issue website code",error); }
    }
    public static String display(String code) { return code.substring(0,4)+"-"+code.substring(4,8)+"-"+code.substring(8); }
}
