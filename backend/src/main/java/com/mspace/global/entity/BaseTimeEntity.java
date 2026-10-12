package com.mspace.global.entity;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import java.time.Instant;
import lombok.Getter;
import org.springframework.data.annotation.LastModifiedDate;

/** created_at에 더해 엔티티가 바뀔 때마다 updated_at을 자동으로 갱신해요. */
@Getter
@MappedSuperclass
public abstract class BaseTimeEntity extends BaseCreatedTimeEntity {

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
