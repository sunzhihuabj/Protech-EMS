package com.projlife.security.datascope;

import com.baomidou.mybatisplus.extension.plugins.handler.DataPermissionHandler;
import com.projlife.common.context.LoginUser;
import com.projlife.common.context.UserContextHolder;
import com.projlife.common.enums.DataScope;
import com.projlife.security.auth.DeptTreeProvider;
import net.sf.jsqlparser.expression.Expression;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 注解驱动的数据范围处理器（03 册 §1.1 五档 SQL 自动追加）。
 * 条件串只用 Long/转义后的工号拼接（数值型主键，无注入面），统一经 jsqlparser 解析为表达式。
 */
public class AnnotationDataPermissionHandler implements DataPermissionHandler {

    private static final Logger log = LoggerFactory.getLogger(AnnotationDataPermissionHandler.class);

    /** 惰性解析（请求期才取 Bean）：避免 sqlSessionFactory → 拦截器 → Mapper → sqlSessionFactory 循环依赖 */
    private final java.util.function.Supplier<DeptTreeProvider> deptTreeProvider;

    public AnnotationDataPermissionHandler(java.util.function.Supplier<DeptTreeProvider> deptTreeProvider) {
        this.deptTreeProvider = deptTreeProvider;
    }

    @Override
    public Expression getSqlSegment(Expression where, String mappedStatementId) {
        DataScopeRule rule = resolveRule(mappedStatementId);
        if (rule == null) {
            return where;
        }
        LoginUser user = UserContextHolder.get();
        if (user == null) {
            return where;
        }
        DataScope scope = DataScope.of(user.getDataScope());
        if (scope == DataScope.ALL) {
            return where;
        }
        String condition = buildCondition(rule, scope, user);
        if (condition == null) {
            return where;
        }
        try {
            Expression scopeExpr = net.sf.jsqlparser.parser.CCJSqlParserUtil.parseCondExpression(condition);
            if (where == null) {
                return scopeExpr;
            }
            return new net.sf.jsqlparser.expression.operators.conditional.AndExpression(where, scopeExpr);
        } catch (Exception ex) {
            log.error("数据范围条件解析失败 statement={}", mappedStatementId, ex);
            throw new IllegalStateException("数据范围改写失败", ex);
        }
    }

    private DataScopeRule resolveRule(String mappedStatementId) {
        try {
            String className = mappedStatementId.substring(0, mappedStatementId.lastIndexOf('.'));
            String methodName = mappedStatementId.substring(mappedStatementId.lastIndexOf('.') + 1);
            Class<?> mapper = Class.forName(className);
            for (Method method : mapper.getMethods()) {
                if (method.getName().equals(methodName) && method.isAnnotationPresent(DataScopeRule.class)) {
                    return method.getAnnotation(DataScopeRule.class);
                }
            }
        } catch (ClassNotFoundException ex) {
            log.warn("数据范围注解解析找不到 Mapper：{}", mappedStatementId);
        }
        return null;
    }

    private String buildCondition(DataScopeRule rule, DataScope scope, LoginUser user) {
        switch (scope) {
            case SELF:
                if (!rule.selfIdColumn().isEmpty()) {
                    return rule.selfIdColumn() + " = " + user.getUserId();
                }
                return rule.userColumn() + " = '" + escape(user.getUserCode()) + "'";
            case DEPT:
                return rule.deptColumn() + " = " + user.getDeptId();
            case DEPT_SUB:
                List<Long> deptIds = deptTreeProvider.get().selfAndSubDeptIds(user.getDeptId());
                String joined = deptIds.stream().map(String::valueOf).collect(Collectors.joining(","));
                return rule.deptColumn() + " IN (" + joined + ")";
            case PROJECT:
                return rule.projectColumn() + " IN (SELECT project_id FROM prj_member WHERE user_id = "
                        + user.getUserId() + " AND is_active = 1 AND deleted = 0)";
            default:
                return null;
        }
    }

    private String escape(String value) {
        return value == null ? "" : value.replace("'", "''");
    }
}
