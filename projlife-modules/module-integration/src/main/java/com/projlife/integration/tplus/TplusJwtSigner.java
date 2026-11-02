package com.projlife.integration.tplus;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * T+ authInfo JWT 签名器（SPRINT-INT1，报告③§三 通道 B）：
 * Authorization 头中的 authInfo 为 JWT{appkey, orgid, appsecret}，用 ISV 私钥文件 RS256 签名。
 * 实现零外部依赖：手写三段 Base64url + SHA256withRSA（java.security）。
 * 私钥格式：PKCS#8 Base64（PEM 去头尾），解析失败显式抛出（风险表：绝不降级为未签名请求）。
 */
@Component
public class TplusJwtSigner {

    /** 签发 authInfo JWT（exp 5 分钟） */
    public String sign(TplusConfig config) {
        if (!config.jwtConfigured()) {
            throw new IllegalStateException("T+ 私钥未配置（PROJLIFE_TPLUS_PRIVATE_KEY），authInfo 无法签名");
        }
        try {
            Map<String, Object> header = new LinkedHashMap<>();
            header.put("alg", "RS256");
            header.put("typ", "JWT");
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("appkey", config.getAppKey());
            payload.put("orgid", config.getOrgId());
            payload.put("appsecret", config.getAppSecret());
            payload.put("exp", System.currentTimeMillis() / 1000 + 300);

            String head = b64urlJson(header);
            String body = b64urlJson(payload);
            String signingInput = head + "." + body;

            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(loadPrivateKey(config.getPrivateKey()));
            signature.update(signingInput.getBytes(StandardCharsets.UTF_8));
            String sig = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(signature.sign());
            return signingInput + "." + sig;
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("T+ authInfo JWT 签名失败（检查私钥格式应为 PKCS#8）：" + ex.getMessage(), ex);
        }
    }

    private PrivateKey loadPrivateKey(String keyText) throws Exception {
        String base64 = keyText
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace("-----BEGIN RSA PRIVATE KEY-----", "")
                .replace("-----END RSA PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        byte[] der = Base64.getDecoder().decode(base64);
        return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
    }

    private String b64urlJson(Map<String, Object> map) {
        // 手拼最小 JSON（键值均为程序控制的字面量，无注入面）；避免引入 Jackson 依赖假设
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> e : map.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append('"').append(e.getKey()).append("\":\"").append(e.getValue()).append('"');
        }
        sb.append('}');
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(sb.toString().getBytes(StandardCharsets.UTF_8));
    }
}
