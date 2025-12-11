package com.anime.common.enums;

/**
 * 可选的枚举：如果你在代码中更偏好类型安全，可以使用这个枚举并在 Attachment.status 处改为枚举映射（需实现 MyBatis TypeHandler）
 */
public enum AttachmentStatus {
    UPLOADING("uploading"),
    AVAILABLE("available"),
    PROCESSING("processing"),
    DELETED("deleted");

    private final String value;

    AttachmentStatus(String value) { this.value = value; }

    public String getValue() { return value; }

    public static AttachmentStatus fromValue(String v) {
        for (AttachmentStatus s : values()) {
            if (s.value.equalsIgnoreCase(v)) return s;
        }
        return null;
    }
}