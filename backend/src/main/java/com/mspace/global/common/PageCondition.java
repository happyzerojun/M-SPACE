package com.mspace.global.common;

import jakarta.validation.constraints.Min;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * 목록 API의 ?page=1&size=20 (API 명세서 0장).
 * API는 1부터, Spring Data는 0부터 세서 toPageable()에서 1을 빼요.
 * size가 100을 넘으면 에러 대신 100으로 맞춰요 (상한 없는 목록 방지, NF-07).
 */
public record PageCondition(
        @Min(value = 1, message = "페이지는 1 이상이어야 해요.")
        Integer page,

        @Min(value = 1, message = "한 페이지 크기는 1 이상이어야 해요.")
        Integer size
) {
    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;

    public PageCondition {
        if (page == null) page = 1;
        if (size == null) size = DEFAULT_SIZE;
        if (size > MAX_SIZE) size = MAX_SIZE;
    }

    public Pageable toPageable(Sort sort) {
        return PageRequest.of(page - 1, size, sort);
    }
}
