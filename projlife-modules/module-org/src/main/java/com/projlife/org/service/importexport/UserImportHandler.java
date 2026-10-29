package com.projlife.org.service.importexport;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.projlife.org.entity.SysDepartment;
import com.projlife.org.entity.SysUser;
import com.projlife.org.mapper.SysDepartmentMapper;
import com.projlife.org.mapper.SysUserMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 用户导入处理器（S0-10 演示实现）。
 * 列：工号/登录名/姓名/手机号/部门编码/岗位/用户类型。
 * 校验：必填、唯一性、部门存在、用户类型合法；初始密码固定为系统配置并强制首登改密。
 */
@Component
public class UserImportHandler implements RowImportHandler<UserImportHandler.UserRow> {

    private final SysUserMapper userMapper;
    private final SysDepartmentMapper deptMapper;
    private final PasswordEncoder passwordEncoder;

    public UserImportHandler(SysUserMapper userMapper, SysDepartmentMapper deptMapper,
                             PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.deptMapper = deptMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public String module() {
        return "org.user";
    }

    @Override
    public List<String> headers() {
        return List.of("工号", "登录名", "姓名", "手机号", "部门编码", "岗位", "用户类型");
    }

    @Override
    public UserRow parse(int rowIdx, Map<Integer, String> cells) {
        UserRow row = new UserRow();
        row.userCode = cell(cells, 0);
        row.username = cell(cells, 1);
        row.realName = cell(cells, 2);
        row.phone = cell(cells, 3);
        row.deptCode = cell(cells, 4);
        row.position = cell(cells, 5);
        row.userType = cell(cells, 6);
        return row;
    }

    @Override
    public List<String> validate(int rowIdx, UserRow row) {
        List<String> errors = new ArrayList<>();
        require(errors, row.userCode, "工号");
        require(errors, row.username, "登录名");
        require(errors, row.realName, "姓名");
        require(errors, row.deptCode, "部门编码");
        if (!errors.isEmpty()) {
            return errors;
        }
        if (userMapper.selectCount(new LambdaQueryWrapper<SysUser>().eq(SysUser::getUserCode, row.userCode)) > 0) {
            errors.add("工号已存在：" + row.userCode);
        }
        if (userMapper.selectCount(new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, row.username)) > 0) {
            errors.add("登录名已存在：" + row.username);
        }
        if (deptMapper.selectCount(new LambdaQueryWrapper<SysDepartment>()
                .eq(SysDepartment::getDeptCode, row.deptCode)) == 0) {
            errors.add("部门编码不存在：" + row.deptCode);
        }
        String type = row.userType == null || row.userType.isEmpty() ? "INTERNAL" : row.userType;
        if (!"INTERNAL".equals(type) && !"SUPPLIER".equals(type) && !"WORKER".equals(type)) {
            errors.add("用户类型非法：" + type + "（仅 INTERNAL/SUPPLIER/WORKER）");
        }
        return errors;
    }

    @Override
    @Transactional
    public void persist(List<UserRow> rows) {
        Map<String, Long> deptCache = new HashMap<>();
        Set<String> seenCodes = new HashSet<>();
        for (UserRow row : rows) {
            if (!seenCodes.add(row.userCode)) {
                throw new IllegalStateException("文件内工号重复：" + row.userCode);
            }
            SysDepartment dept = deptMapper.selectOne(new LambdaQueryWrapper<SysDepartment>()
                    .eq(SysDepartment::getDeptCode, row.deptCode));
            deptCache.put(row.deptCode, dept.getId());

            SysUser user = new SysUser();
            user.setUserCode(row.userCode);
            user.setUsername(row.username);
            user.setRealName(row.realName);
            user.setPasswordHash(passwordEncoder.encode("ProjLife@" + row.userCode));
            user.setPhone(row.phone);
            user.setDeptId(dept.getId());
            user.setPosition(row.position);
            user.setUserType(row.userType == null || row.userType.isEmpty() ? "INTERNAL" : row.userType);
            user.setLegalEntityId(1L);
            user.setStatus("ACTIVE");
            user.setMustChangePwd(1);
            user.setPwdUpdatedAt(LocalDateTime.now());
            userMapper.insert(user);
        }
    }

    private void require(List<String> errors, String value, String label) {
        if (value == null || value.isEmpty()) {
            errors.add(label + "不能为空");
        }
    }

    private String cell(Map<Integer, String> cells, int index) {
        String value = cells.get(index);
        return value == null ? null : value.trim();
    }

    /** 行对象 */
    public static class UserRow {

        public String userCode;
        public String username;
        public String realName;
        public String phone;
        public String deptCode;
        public String position;
        public String userType;
    }
}
