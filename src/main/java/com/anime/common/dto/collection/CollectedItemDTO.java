package com.anime.common.dto.collection;

import lombok.Data;

@Data
public class CollectedItemDTO {
    private Long id;
    private Long attachment_id;
    String name;
    String description;
    Long father_level2_id;

}
