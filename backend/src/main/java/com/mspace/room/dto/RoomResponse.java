package com.mspace.room.dto;

import com.mspace.room.domain.Room;

/** GET /api/rooms 항목 (API 명세서 2장) */
public record RoomResponse(Long id, String name, int capacity, boolean active) {

    public static RoomResponse from(Room room) {
        return new RoomResponse(room.getId(), room.getName(), room.getCapacity(), room.isActive());
    }
}
