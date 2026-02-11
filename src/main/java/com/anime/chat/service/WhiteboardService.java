package com.anime.chat.service;

import com.anime.chat.socket.WebSocketSessionManager;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;

/**
 * WhiteboardService - 会话级唯一白板（Redis Streams 兼容版）
 *
 * - 不再有 boardId，使用 convId（private:minUserId_maxUserId）唯一标识会话白板
 * - 事件写入 Redis Streams；用 trim(count) 做数量裁剪兜底
 * - JOIN 时按 ts 过滤出“最近 15 分钟”的事件，满足时间窗需求
 * - members Set 只给 JOIN 了的用户转发，避免串扰
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WhiteboardService {

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final WebSocketSessionManager sessionManager;

    // 键空间（会话级）
    private static final String KEY_STREAM  = "whiteboard:conv:%s:stream";
    private static final String KEY_MEMBERS = "whiteboard:conv:%s:members";
    private static final String KEY_LOCK    = "whiteboard:conv:%s:lock";

    // 时间窗：15 分钟
    private static final long WINDOW_MS = 15 * 60_000L;
    // 记录上限（兜底，避免无限增长；按你的场景调大/调小）
    private static final long MAX_RECORDS = 20_000L;

    // 兜底过期
    private static final Duration MEMBERS_TTL = Duration.ofMinutes(30);
    private static final Duration LOCK_TTL    = Duration.ofSeconds(2);

    // 规范化 convId（仅支持私聊）
    private String convId(long a, long b) {
        long x = Math.min(a, b), y = Math.max(a, b);
        return "private:" + x + "_" + y;
    }

    /**
     * 轻量初始化：members 设置兜底过期；Streams 首次 XADD 自动创建
     */
    public void openWhiteboardIfNeeded(Long a, Long b) {
        String conv = convId(a, b);
        String lockKey = String.format(KEY_LOCK, conv);
        Boolean ok = redis.opsForValue().setIfAbsent(lockKey, "1", LOCK_TTL);
        if (Boolean.TRUE.equals(ok)) {
            redis.expire(String.format(KEY_MEMBERS, conv), MEMBERS_TTL);
        }
    }

    /**
     * JOIN：加入 members，并返回最近时间窗内的事件（按 ts 过滤）
     */
    public List<Map<String, Object>> joinAndLoadWindow(Long selfUserId, Long otherUserId) {
        String conv = convId(selfUserId, otherUserId);
        String membersKey = String.format(KEY_MEMBERS, conv);
        String streamKey  = String.format(KEY_STREAM, conv);

        // 成员加入
        redis.opsForSet().add(membersKey, String.valueOf(selfUserId));
        redis.expire(membersKey, MEMBERS_TTL);

        // 读取全量，再按 ts 过滤出 15 分钟窗口（避免依赖 MINID API）
        Range<String> range = Range.unbounded();
        List<MapRecord<String, Object, Object>> records = redis.opsForStream().range(streamKey, range);

        long threshold = System.currentTimeMillis() - WINDOW_MS;
        List<Map<String, Object>> events = new ArrayList<>();
        if (records != null) {
            for (var r : records) {
                Map<Object, Object> body = r.getValue();
                Map<String, Object> ev = new HashMap<>();
                // 还原字段（全部以字符串/对象存储，需要转换为字符串再解析）
                for (Map.Entry<Object, Object> e : body.entrySet()) {
                    ev.put(String.valueOf(e.getKey()), e.getValue());
                }
                ev.put("convId", conv);

                // ts 过滤（字符串/对象转 long）
                long ts = parseLong(ev.get("ts"));
                if (ts == 0L) continue;
                if (ts >= threshold) {
                    // points 恢复为数组（如果存在，且是 JSON 字符串）
                    if (ev.containsKey("points")) {
                        try {
                            List<List<Double>> pts = objectMapper.readValue(
                                    String.valueOf(ev.get("points")),
                                    new TypeReference<List<List<Double>>>(){}
                            );
                            ev.put("points", pts);
                        } catch (Exception ignore) {}
                    }
                    // isEnd 恢复为 boolean
                    if (ev.containsKey("isEnd")) {
                        ev.put("isEnd", Boolean.parseBoolean(String.valueOf(ev.get("isEnd"))));
                    }
                    // width 恢复为 number
                    if (ev.containsKey("width")) {
                        ev.put("width", parseDouble(ev.get("width")));
                    }
                    events.add(ev);
                }
            }
        }
        return events;
    }

    /**
     * 写入笔画并转发（仅 members；不含发送者）
     * 注意：为兼容老 API，这里将事件字段统一作为 Object 写入；points 用 JSON 字符串
     */
    public void appendStrokeAndForward(Long fromUserId, Long otherUserId, Map<String, Object> payload) {
        String conv = convId(fromUserId, otherUserId);
        String streamKey  = String.format(KEY_STREAM, conv);
        String membersKey = String.format(KEY_MEMBERS, conv);

        long ts = payload.get("ts") instanceof Number ? ((Number) payload.get("ts")).longValue() : System.currentTimeMillis();

        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("type", payload.getOrDefault("type", "WHITEBOARD_STROKE_PART"));
        fields.put("convId", conv);
        fields.put("fromUserId", String.valueOf(fromUserId)); // 用字符串便于前端解析
        fields.put("strokeId", payload.get("strokeId"));
        if (payload.get("tool") != null)  fields.put("tool", payload.get("tool"));
        if (payload.get("color") != null) fields.put("color", payload.get("color"));
        if (payload.get("width") != null) fields.put("width", String.valueOf(payload.get("width"))); // 字符串存储
        if (payload.get("isEnd") != null) fields.put("isEnd", String.valueOf(payload.get("isEnd")));
        fields.put("ts", String.valueOf(ts));
        // points 序列化为 JSON 字符串
        if (payload.get("points") != null) {
            try {
                fields.put("points", objectMapper.writeValueAsString(payload.get("points")));
            } catch (Exception e) {
                fields.put("points", "[]");
            }
        }

        MapRecord<String, String, Object> record =
                MapRecord.create(streamKey, fields).withId(RecordId.autoGenerate());
        redis.opsForStream().add(record);

        // 兜底数量裁剪（不依赖 MINID）
        try {
            redis.opsForStream().trim(streamKey, MAX_RECORDS);
        } catch (Exception ignore) {}

        // 转发给 members（不含发送者）
        Set<String> members = redis.opsForSet().members(membersKey);
        if (members != null) {
            for (String uidStr : members) {
                if (uidStr == null) continue;
                long uid = parseLong(uidStr);
                if (uid == fromUserId) continue;
                // 转发原始 map；前端对 points 解析为数组后绘制
                sessionManager.sendToUser(uid, "WHITEBOARD_EVENT", fields);
            }
        }
    }

    /**
     * 清空：写 CLEAR 事件并广播（兼容 Object 字段）
     */
    public void clearAndBroadcast(Long selfUserId, Long otherUserId, Long ts) {
        String conv = convId(selfUserId, otherUserId);
        String streamKey  = String.format(KEY_STREAM, conv);
        String membersKey = String.format(KEY_MEMBERS, conv);
        long now = (ts == null) ? System.currentTimeMillis() : ts;

        Map<String, Object> ev = new LinkedHashMap<>();
        ev.put("type", "WHITEBOARD_CLEAR");
        ev.put("convId", conv);
        ev.put("fromUserId", String.valueOf(selfUserId));
        ev.put("ts", String.valueOf(now));

        MapRecord<String, String, Object> record =
                MapRecord.create(streamKey, ev).withId(RecordId.autoGenerate());
        redis.opsForStream().add(record);

        // 数量裁剪兜底
        try {
            redis.opsForStream().trim(streamKey, MAX_RECORDS);
        } catch (Exception ignore) {}

        // 广播
        Set<String> members = redis.opsForSet().members(membersKey);
        if (members != null) {
            for (String uidStr : members) {
                if (uidStr == null) continue;
                sessionManager.sendToUser(parseLong(uidStr), "WHITEBOARD_CLEAR", ev);
            }
        }
    }

    /**
     * 离开：从 members 移除
     */
    public void leave(Long selfUserId, Long otherUserId) {
        String conv = convId(selfUserId, otherUserId);
        String membersKey = String.format(KEY_MEMBERS, conv);
        try {
            redis.opsForSet().remove(membersKey, String.valueOf(selfUserId));
        } catch (Exception e) {
            log.warn("whiteboard leave failed conv={} userId={} err={}", conv, selfUserId, e.getMessage());
        }
    }

    private long parseLong(Object v) {
        try { return Long.parseLong(String.valueOf(v)); } catch (Exception e) { return 0L; }
    }
    private double parseDouble(Object v) {
        try { return Double.parseDouble(String.valueOf(v)); } catch (Exception e) { return 0d; }
    }
}