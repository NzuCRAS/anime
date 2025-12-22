package com.anime.common.dto.collection.items;

import lombok.Data;

@Data
public class ItemResultDTO {
    Long id;
    String name;
    String description;
    String URL;
    Long folder_level2_id;
}
