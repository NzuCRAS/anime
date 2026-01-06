package com.anime.common.dto.video;

import lombok.Data;

import java.util.List;

@Data
public class PlayUrlResponse {
    private List<PlayUrlItem> items;
}