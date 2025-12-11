package com.anime.user.service;

import com.anime.common.entity.user.User;
import com.anime.common.mapper.user.UserMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * UserService - 负责与用户相关的业务逻辑：
 * - authenticateAndGetId: 验证凭证并返回用户ID（null 表示验证失败）
 * - getUsernameById: 根据 userId 返回用户名
 * - onLoginSuccess: 登录成功后的业务（如更新 last_login 等）
 * - registerUser: 注册新用户（返回新用户id 或抛出异常）
 *。
 */
@Service
public class UserService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserMapper userMapper, PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * 登录成功后的动作：例如更新时间、统计、异步审计等
     */
    public void onLoginSuccess(Long userId) {
        if (userId == null) return;
        try {
            // userMapper.updateLastLogin 期望参数为 String 或 Long，根据你的 mapper 定义调整
            userMapper.updateLastLogin(String.valueOf(userId));
        } catch (Exception e) {
            // 不要抛出异常影响主流程，记录日志即可
            // logger.warn("更新 last_login 失败", e);
        }
    }

    /**
     * 根据 userId 获取 username
     */
    public String getUsernameById(Long userId) {
        if (userId == null) return null;
        return userMapper.getUserNameById(userId);
    }

    /**
     * 验证用户名/邮箱 + 明文密码，成功返回 userId，失败返回 null
     * 实现要点：
     * - 通过 userMapper.findByUsernameOrEmail 查询用户（包含数据库写入的哈希密码）
     * - 使用 PasswordEncoder.matches(plain, hash) 验证
     */
    public Long authenticateAndGetId(String usernameOrEmail, String password) {
        if (usernameOrEmail == null || password == null) return null;
        try {
            User u = userMapper.findByUsernameOrEmail(usernameOrEmail, usernameOrEmail);
            if (u == null) return null;
            String storedHash = u.getPassword(); // 请确认 User 实体的字段名
            if (storedHash == null) return null;
            boolean ok = passwordEncoder.matches(password, storedHash);
            return ok ? u.getId() : null;
        } catch (Exception e) {
            // 处理异常（记录日志），并返回 null
            return null;
        }
    }

    /**
     * 注册新用户：检查唯一性 -> 加密密码 -> 插入数据库 -> 返回新 userId
     *
     * 语义与实现要点：
     * - 需要保证 username 与 email 的唯一性（可在 DB 层加唯一索引，并在此处检验抛出友好异常）
     * - 密码使用 PasswordEncoder 加密（实际为 BCryptPasswordEncoder）
     * - 注册完成后会默认视为已登录（由控制器生成 token 并写 cookie）
     */
    public Long registerUser(String username, String email, String rawPassword) {
        if (username == null || email == null || rawPassword == null) {
            throw new IllegalArgumentException("username/email/password must not be null");
        }

        // 1) 唯一性检查（尽早）
        User exist = userMapper.findByUsernameOrEmail(username, email);
        if (exist != null) {
            throw new IllegalArgumentException("用户名或邮箱已存在");
        }

        // 2) 密码加密
        String encoded = passwordEncoder.encode(rawPassword);

        // 3) 构建 User 实体并插入
        User newUser = new User();
        newUser.setUsername(username);
        newUser.setEmail(email);
        newUser.setPassword(encoded);
        newUser.setCreatedTime(LocalDateTime.now());
        // 你可以根据 User 实体补充更多字段（avatar, status 等）

        int rows = userMapper.insert(newUser); // MyBatis-Plus 会回填 id（如果使用自增）
        if (rows <= 0) {
            throw new RuntimeException("用户创建失败");
        }
        // 插入后 newUser.getId() 应该有值（取决于数据库与MyBatis-Plus设置）
        return newUser.getId();
    }
}