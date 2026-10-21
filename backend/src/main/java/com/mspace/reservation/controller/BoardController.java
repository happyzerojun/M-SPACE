package com.mspace.reservation.controller;

import com.mspace.reservation.dto.BoardResponse;
import com.mspace.reservation.service.BoardService;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** GET /api/board?date=2026-10-13 — 현황판, 비로그인 허용 (API 명세서 2장) */
@RestController
@RequiredArgsConstructor
public class BoardController {

    private final BoardService boardService;

    @GetMapping("/api/board")
    public BoardResponse board(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return boardService.board(date);
    }
}
