package com.projlife.security.auth;

import com.projlife.common.exception.BizException;
import com.projlife.common.exception.ErrorCode;
import com.projlife.common.kv.KeyValStore;
import com.projlife.security.config.SecurityProperties;
import org.springframework.stereotype.Service;

/**
 * 登录失败锁定（S0-3 完成标准：5 次错密码锁定 15 分钟）。
 */
@Service
public class LoginProtectionService {

    private static final String KEY_PREFIX = "login:fail:";

    private final KeyValStore store;
    private final SecurityProperties props;

    public LoginProtectionService(KeyValStore store, SecurityProperties props) {
        this.store = store;
        this.props = props;
    }

    /** 登录前置校验：已锁定则抛 429，提示剩余分钟 */
    public void checkLocked(String username) {
        long count = store.getLong(KEY_PREFIX + username);
        if (count >= props.getMaxLoginFail()) {
            throw BizException.of(ErrorCode.TOO_MANY,
                    "密码连续错误已达 " + props.getMaxLoginFail() + " 次，账号锁定 " + props.getLockMinutes() + " 分钟");
        }
    }

    /** 记录一次失败；达到阈值返回 true */
    public boolean recordFail(String username) {
        long count = store.increment(KEY_PREFIX + username, props.getLockMinutes() * 60L);
        return count >= props.getMaxLoginFail();
    }

    /** 登录成功清除计数 */
    public void clear(String username) {
        store.remove(KEY_PREFIX + username);
    }
}
