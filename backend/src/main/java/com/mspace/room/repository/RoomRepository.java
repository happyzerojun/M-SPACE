package com.mspace.room.repository;

import com.mspace.room.domain.Room;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoomRepository extends JpaRepository<Room, Long> {

    /** 현황판·방 목록 순서 (sort_order, 같으면 id). 비활성 방도 포함해서 화면이 "이용 불가"로 보여줘요 */
    List<Room> findAllByOrderBySortOrderAscIdAsc();
}
