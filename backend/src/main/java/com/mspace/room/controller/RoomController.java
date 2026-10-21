package com.mspace.room.controller;

import com.mspace.room.dto.RoomResponse;
import com.mspace.room.repository.RoomRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** GET /api/rooms — 방 목록 (비로그인 허용, API 명세서 2장). 방이 5개뿐이라 페이지를 두지 않아요 */
@RestController
@RequiredArgsConstructor
public class RoomController {

    private final RoomRepository roomRepository;

    @GetMapping("/api/rooms")
    @Transactional(readOnly = true)
    public List<RoomResponse> rooms() {
        return roomRepository.findAllByOrderBySortOrderAscIdAsc().stream().map(RoomResponse::from).toList();
    }
}
