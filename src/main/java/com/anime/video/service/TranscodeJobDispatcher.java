package com.anime.video.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 简单的 dispatcher：把任务交给 TranscodeWorker（以 videoId 作为任务单元）
 */
@Component
@RequiredArgsConstructor
public class TranscodeJobDispatcher {

    private final TranscodeWorker worker;

    public void dispatchTranscodeJob(Long videoId) {
        // 异步派发
        worker.processTranscode(videoId);
    }
}