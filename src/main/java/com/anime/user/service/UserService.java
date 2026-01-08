package com.anime.user.service;

import com.anime.common.entity.user.User;
import com.anime.common.mapper.user.UserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.time.LocalDateTime;

/**
 * UserService - 负责与用户相关的业务逻辑。
 */
@Slf4j
@Service
public class UserService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserMapper userMapper, PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
    }



    @Transactional
    public Boolean PostUserAvatar(Long userId, Long attachmentId) {
        if (userId == null) throw new IllegalArgumentException("userId required");
        if (attachmentId == null) throw new IllegalArgumentException("attachmentId required");
        User user = userMapper.selectById(userId);
        if (user == null) throw new IllegalArgumentException("user id not found");
        // use setter that matches your User entity (existing code used setAvatar_attachment_id)
        try {
            // try common setter names
            try {
                Method m = user.getClass().getMethod("setAvatar_attachment_id", String.class);
                m.invoke(user, String.valueOf(attachmentId));
            } catch (NoSuchMethodException e1) {
                try {
                    Method m2 = user.getClass().getMethod("setAvatarAttachmentId", Long.class);
                    m2.invoke(user, attachmentId);
                } catch (NoSuchMethodException e2) {
                    // fallback: try setAvatarAttachmentId(String)
                    try {
                        Method m3 = user.getClass().getMethod("setAvatarAttachmentId", String.class);
                        m3.invoke(user, String.valueOf(attachmentId));
                    } catch (NoSuchMethodException ex) {
                        // last resort: set via direct field not attempted here
                        throw new RuntimeException("No appropriate setter for avatar attachment id on User entity");
                    }
                }
            }
            userMapper.updateById(user);
        } catch (RuntimeException re) {
            throw re;
        } catch (Exception ex) {
            log.error("failed to set avatar attachment id via reflection", ex);
            throw new RuntimeException("failed to update avatar", ex);
        }

        log.info("Update avatar_attachment id={} by user={}", attachmentId, userId);
        return true;
    }

    /**
     * 登录成功后的动作：例如更新时间、统计、异步审计等
     */
    public void onLoginSuccess(Long userId) {
        if (userId == null) return;
        try {
            // userMapper.updateLastLogin 期望参数为 String 或 Long，根据你的 mapper 定义调整
            userMapper.updateLastLogin(userId);
        } catch (Exception e) {
            log.debug("更新 last_login 失败: {}", e.getMessage());
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
     */
    public Long authenticateAndGetId(String usernameOrEmail, String password) {
        if (usernameOrEmail == null || password == null) return null;
        try {
            log.debug("authenticateAndGetId: attempt login for identifier='{}'", usernameOrEmail);
            User u = userMapper.findByUsernameOrEmail(usernameOrEmail, usernameOrEmail);
            if (u == null) {
                log.debug("authenticateAndGetId: user not found for '{}'", usernameOrEmail);
                return null;
            }
            String storedHash = u.getPassword();
            if (storedHash == null) {
                log.debug("authenticateAndGetId: user '{}' has no stored password", usernameOrEmail);
                return null;
            }
            boolean ok = passwordEncoder.matches(password, storedHash);
            if (!ok) {
                log.debug("authenticateAndGetId: password mismatch for user '{}'", usernameOrEmail);
                return null;
            }
            log.debug("authenticateAndGetId: login success for userId={}", u.getId());
            return u.getId();
        } catch (Exception e) {
            log.debug("authenticateAndGetId failed: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 注册新用户并返回新 userId
     */
    public Long registerUser(String username, String email, String rawPassword) {
        if (username == null || email == null || rawPassword == null) {
            throw new IllegalArgumentException("username/email/password must not be null");
        }

        User exist = userMapper.findByUsernameOrEmail(username, email);
        if (exist != null) {
            throw new IllegalArgumentException("用户名或邮箱已存在");
        }

        String encoded = passwordEncoder.encode(rawPassword);

        User newUser = new User();
        newUser.setUsername(username);
        newUser.setEmail(email);
        newUser.setPassword(encoded);
        newUser.setCreatedTime(LocalDateTime.now());

        int rows = userMapper.insert(newUser);
        if (rows <= 0) {
            throw new RuntimeException("用户创建失败");
        }
        return newUser.getId();
    }

    /**
     * 获取用户绑定的 avatar attachment id（如果有），否则返回 null。
     * 该方法使用反射尝试多种常见 getter 名称以适配实体命名风格。
     */
    public Long getAvatarAttachmentId(Long userId) {
        if (userId == null) return null;
        try {
            return userMapper.getAvatarAttachmentIdById(userId);
        } catch (Exception e) {
            log.debug("getAvatarAttachmentId failed for userId {}: {}", userId, e.getMessage());
            return null;
        }
    }

    /**
     * 获取用户的 personal signature（如果存在），否则返回 null。
     * 同样使用反射兼容常见 getter 命名。
     */
    public String getPersonalSignature(Long userId) {
        if (userId == null) return null;
        User user = userMapper.selectById(userId);
        if (user == null) return null;

        try {
            String[] getterNames = new String[] {
                    "getPersonalSignature",
                    "getPersonal_signature",
                    "getPersonalSignatureText",
                    "getSignature",
                    "getPersonalSign"
            };
            for (String gn : getterNames) {
                try {
                    Method m = user.getClass().getMethod(gn);
                    Object val = m.invoke(user);
                    if (val != null) return String.valueOf(val);
                } catch (NoSuchMethodException ignore) {
                }
            }

            // fallback to fields
            try {
                java.lang.reflect.Field f = null;
                try { f = user.getClass().getDeclaredField("personalSignature"); } catch (NoSuchFieldException e) {}
                if (f == null) {
                    try { f = user.getClass().getDeclaredField("personal_signature"); } catch (NoSuchFieldException e) {}
                }
                if (f != null) {
                    f.setAccessible(true);
                    Object val = f.get(user);
                    if (val != null) return String.valueOf(val);
                }
            } catch (Throwable t) {
                log.debug("personalSignature field fallback failed: {}", t.getMessage());
            }

        } catch (Throwable t) {
            log.debug("getPersonalSignature failed for userId {}: {}", userId, t.getMessage());
        }
        return null;
    }

    /**
     * 更新用户的个人签名
     * 返回 true 表示更新成功（受影响行数 > 0），false 表示没有更新（比如 userId 不存在）
     */
    @Transactional
    public boolean updatePersonalSignature(Long userId, String signature) {
        if (userId == null) throw new IllegalArgumentException("userId required");
        if (signature == null) signature = "";
        // 可限制签名长度，例如 200 字符
        if (signature.length() > 200) {
            throw new IllegalArgumentException("personalSignature length must <= 200");
        }
        try {
            int rows = userMapper.updatePersonalSignatureById(userId, signature);
            log.info("updatePersonalSignature: userId={} rows={}", userId, rows);
            return rows > 0;
        } catch (Exception e) {
            log.error("updatePersonalSignature failed userId={} err={}", userId, e.getMessage(), e);
            throw new RuntimeException("update personal signature failed", e);
        }
    }
}