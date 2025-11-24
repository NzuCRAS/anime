package com.animecommunity.common.result;

import lombok.Data;

import java.util.List;

@Data
public class PageData<T> {
    private List<T> list;           // 数据列表
    private Long total;             // 总记录数
    private Integer pageNum;        // 当前页码
    private Integer pageSize;       // 每页大小
    private Integer totalPages;          // 总页数
}
