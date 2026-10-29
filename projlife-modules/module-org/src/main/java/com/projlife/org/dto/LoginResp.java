package com.projlife.org.dto;

/** 登录出参：双令牌 + 首登改密标记 + 主题偏好 */
public class LoginResp {

    public String accessToken;
    public String refreshToken;
    public String tokenType = "Bearer";
    public long expiresIn;
    public boolean mustChangePwd;
    public Long userId;
    public String userCode;
    public String realName;
    public String themeCode;
}
