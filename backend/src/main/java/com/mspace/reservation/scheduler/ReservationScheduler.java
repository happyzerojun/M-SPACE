package com.mspace.reservation.scheduler;

import com.mspace.reservation.service.NoShowService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 1분마다 노쇼·자동 이용 종료를 처리해요 (NS-01, NS-02, OP-03). fixedDelay라 앞 실행이 끝난 뒤 1분을 기다려요(겹쳐 돌지 않아요).
 * 테스트는 시각을 고정하고 NoShowService.runOnce()를 직접 불러서, 테스트 프로필에서는 꺼요(mspace.scheduler.enabled=false).
 */
@Component
public class ReservationScheduler {

    private final NoShowService noShowService;
    private final boolean enabled;

    public ReservationScheduler(NoShowService noShowService, @Value("${mspace.scheduler.enabled:true}") boolean enabled) {
        this.noShowService = noShowService;
        this.enabled = enabled;
    }

    @Scheduled(fixedDelayString = "${mspace.scheduler.fixed-delay-ms:60000}", initialDelayString = "${mspace.scheduler.initial-delay-ms:30000}")
    public void run() {
        if (enabled) {
            noShowService.runOnce();
        }
    }
}
