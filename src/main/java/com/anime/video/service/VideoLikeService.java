package com.anime.video.service;

import com.anime.common.entity.video.VideoLike;
import com.anime.common.mapper.video.VideoLikeMapper;
import com.anime.common.mapper.video.VideoMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 点赞服务：首次点赞插入记录（active=1），后续只更新 active 字段
 */
@Service
@RequiredArgsConstructor
public class VideoLikeService {

    private final VideoLikeMapper likeMapper;
    private final VideoMapper videoMapper;

    @Transactional
    public boolean toggleLike(Long videoId, Long userId) {
        VideoLike existing = likeMapper.selectByVideoAndUser(videoId, userId);
        if (existing != null) {
            int newActive = (existing.getActive() != null && existing.getActive() == 1) ? 0 : 1;
            likeMapper.updateActiveById(existing.getId(), newActive);

            // Update cached likeCount on videos table
            if (newActive == 1) {
                videoMapper.incrementLikeCount(videoId);
            } else {
                videoMapper.decrementLikeCount(videoId);
            }
            return newActive == 1;
        } else {
            // insert new record active=1
            likeMapper.insertLike(videoId, userId, 1);
            videoMapper.incrementLikeCount(videoId);
            return true;
        }
    }

    public Long getLikeCount(Long videoId) {
        Long c = likeMapper.countActiveByVideoId(videoId);
        return c == null ? 0L : c;
    }
}