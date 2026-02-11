package com.anime.user.service;

import com.anime.common.dto.user.UserInfoDTO;
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
        try {
            try {
                Method m = user.getClass().getMethod("setAvatar_attachment_id", String.class);
                m.invoke(user, String.valueOf(attachmentId));
            } catch (NoSuchMethodException e1) {
                try {
                    Method m2 = user.getClass().getMethod("setAvatarAttachmentId", Long.class);
                    m2.invoke(user, attachmentId);
                } catch (NoSuchMethodException e2) {
                    try {
                        Method m3 = user.getClass().getMethod("setAvatarAttachmentId", String.class);
                        m3.invoke(user, String.valueOf(attachmentId));
                    } catch (NoSuchMethodException ex) {
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

    public void onLoginSuccess(Long userId) {
        if (userId == null) return;
        try {
            userMapper.updateLastLogin(userId);
        } catch (Exception e) {
            log.debug("更新 last_login 失败: {}", e.getMessage());
        }
    }

    public String getUsernameById(Long userId) {
        if (userId == null) return null;
        return userMapper.getUserNameById(userId);
    }

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

    public Long getAvatarAttachmentId(Long userId) {
        if (userId == null) return null;
        try {
            return userMapper.getAvatarAttachmentIdById(userId);
        } catch (Exception e) {
            log.debug("getAvatarAttachmentId failed for userId {}: {}", userId, e.getMessage());
            return null;
        }
    }

    public String getPersonalSignature(Long userId) {
        if (userId == null) return null;
        try {
            return userMapper.getPersonalSignatureById(userId);
        } catch (Exception e) {
            log.debug("getPersonalSignature failed for userId {}: {}", userId, e.getMessage());
            return null;
        }
    }

    @Transactional
    public boolean updatePersonalSignature(Long userId, String signature) {
        if (userId == null) throw new IllegalArgumentException("userId required");
        if (signature == null) signature = "";
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

    /**
     * 组装当前用户的 UserInfoDTO。
     * 供 /api/user/me 接口直接调用。
     */
    public UserInfoDTO getUserInfo(Long userId) {
        if (userId == null) return null;
        UserInfoDTO dto = new UserInfoDTO();
        dto.setId(String.valueOf(userId));
        try {
            dto.setUsername(getUsernameById(userId));
        } catch (Exception e) {
            log.debug("getUserInfo: getUsernameById failed userId={} msg={}", userId, e.getMessage());
        }
        try {
            Long avatarId = getAvatarAttachmentId(userId);
            if (avatarId != null) {
                // 这里不直接生成 URL，由控制层使用 AttachmentService 生成，以便统一策略
                // 占位：在 controller 中会补齐
            }
        } catch (Exception e) {
            log.debug("getUserInfo: getAvatarAttachmentId failed userId={} msg={}", userId, e.getMessage());
        }
        try {
            dto.setPersonalSignature(getPersonalSignature(userId));
        } catch (Exception ignored) {}

        return dto;
    }
}