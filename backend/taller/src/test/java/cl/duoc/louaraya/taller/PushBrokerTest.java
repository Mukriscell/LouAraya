package cl.duoc.louaraya.taller;

import cl.duoc.louaraya.common.ApiFailure;
import com.sun.net.httpserver.HttpServer;
import nl.martijndwars.webpush.Utils;
import org.bouncycastle.jce.interfaces.ECPublicKey;
import org.junit.jupiter.api.Test;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.spec.ECGenParameterSpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PushBrokerTest {
    private static final String ORIGIN = "http://10.0.2.2:8090";
    private static final String TOPIC = "/upABCDefgh1234";
    private static final String ENDPOINT = ORIGIN + TOPIC + "?up=1";

    @Test
    void allowsOnlyAnExactOriginTopicAndQueryAndRoutesOverFixedTransport() {
        var broker = new PushBroker(ORIGIN, "http://127.0.0.1:8090");
        assertThat(broker.target(ENDPOINT).toString()).isEqualTo("http://127.0.0.1:8090" + TOPIC + "?up=1");
        for (String invalid : new String[] {
                "http://127.0.0.1:8090" + TOPIC + "?up=1",
                "http://10.0.2.2:8090@example.org" + TOPIC + "?up=1",
                ORIGIN + "/%75pABCDefgh1234?up=1",
                ORIGIN + TOPIC + "?up=1&x=2",
                ORIGIN + TOPIC + "?up=1#fragment",
                ORIGIN + "/upABCDefgh12345?up=1",
                ORIGIN + TOPIC + "/json?up=1",
        }) {
            assertThatThrownBy(() -> broker.target(invalid)).isInstanceOf(ApiFailure.class);
        }
        assertThat(new PushBroker("", "").enabled()).isFalse();
        assertThatThrownBy(() -> new PushBroker(ORIGIN, "")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void postsOnlyEncryptedNoticeIdWithUnifiedPushHeadersAndNeverFollowsRedirects() throws Exception {
        var http = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var sent = new AtomicReference<byte[]>();
        var description = new AtomicReference<String>();
        http.createContext(TOPIC, request -> {
            var headers = request.getRequestHeaders();
            description.set(request.getRequestMethod() + " " + request.getRequestURI() + " "
                    + headers.getFirst("Content-Encoding") + " "
                    + headers.getFirst("X-UnifiedPush") + " " + headers.getFirst("X-Cache") + " "
                    + headers.getFirst("TTL"));
            sent.set(request.getRequestBody().readAllBytes());
            request.getResponseHeaders().add("Location", "/unexpected");
            request.sendResponseHeaders(307, -1);
            request.close();
        });
        http.start();
        try {
            var broker = new PushBroker(ORIGIN, "http://127.0.0.1:" + http.getAddress().getPort());
            var generator = KeyPairGenerator.getInstance("EC", "BC");
            generator.initialize(new ECGenParameterSpec("secp256r1"));
            KeyPair recipient = generator.generateKeyPair();
            byte[] auth = "1234567890123456".getBytes(US_ASCII);
            Base64.Encoder base64 = Base64.getUrlEncoder().withoutPadding();
            var subscription = new PushBroker.Subscription(ENDPOINT,
                    base64.encodeToString(Utils.encode((ECPublicKey) recipient.getPublic())), base64.encodeToString(auth));

            broker.validate(subscription);
            assertThat(broker.publish(subscription, 1042)).isEqualTo(307);
            assertThat(description.get()).isEqualTo("POST " + TOPIC + "?up=1 aes128gcm 1 no 60");
            assertThat(new String(decrypt(recipient, auth, sent.get()), StandardCharsets.UTF_8))
                    .isEqualTo("{\"noticeId\":1042}\u0002");
            assertThatThrownBy(() -> broker.validate(new PushBroker.Subscription(ENDPOINT,
                    subscription.publicKey(), "short"))).isInstanceOf(ApiFailure.class);
        } finally {
            http.stop(0);
        }
    }

    // Receiver-side RFC 8291 check with the JDK primitives, independent of HttpEce.
    private static byte[] decrypt(KeyPair recipient, byte[] auth, byte[] record) throws Exception {
        byte[] salt = Arrays.copyOfRange(record, 0, 16);
        int keyLength = Byte.toUnsignedInt(record[20]);
        assertThat(keyLength).isEqualTo(65);
        byte[] serverKey = Arrays.copyOfRange(record, 21, 21 + keyLength);
        var agreement = KeyAgreement.getInstance("ECDH");
        agreement.init(recipient.getPrivate());
        agreement.doPhase(Utils.loadPublicKey(serverKey), true);
        byte[] clientKey = Utils.encode((ECPublicKey) recipient.getPublic());
        byte[] keyInfo = Utils.concat("WebPush: info\0".getBytes(US_ASCII), clientKey, serverKey, new byte[] {1});
        byte[] ikm = hmac(hmac(auth, agreement.generateSecret()), keyInfo);
        byte[] prk = hmac(salt, ikm);
        byte[] key = Arrays.copyOf(hmac(prk, "Content-Encoding: aes128gcm\0\1".getBytes(US_ASCII)), 16);
        byte[] nonce = Arrays.copyOf(hmac(prk, "Content-Encoding: nonce\0\1".getBytes(US_ASCII)), 12);
        var cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
        return cipher.doFinal(record, 21 + keyLength, record.length - 21 - keyLength);
    }

    private static byte[] hmac(byte[] key, byte[] input) throws Exception {
        var mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(input);
    }
}
