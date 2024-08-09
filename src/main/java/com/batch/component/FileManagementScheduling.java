package com.batch.component;

import com.batch.service.Batching;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class FileManagementScheduling {

    @Autowired
    private Batching batching;

    /**
     * # ┌────────────── วินาที second (optional)
     * # │ ┌──────────── นาที minute
     * # │ │ ┌────────── ชั่วโมง hour
     * # │ │ │ ┌──────── วันที่ day-of-month
     * # │ │ │ │ ┌────── เดือน month
     * # │ │ │ │ │ ┌──── วันในสัปดาห์นั้น ๆ day-of-week
     * # │ │ │ │ │ │
     * # │ │ │ │ │ │
     * # * * * * * *
     * <p>
     * "0 0/1 * * * ?": Every minute
     * "0 0 12 * * ?": Every day at noon
     * "0 0 12 * * MON-FRI": Every weekday at noon
     */
    //    @Scheduled(cron = "30 * * * * ?")
    @Scheduled(fixedRate = 15000)
    private void scheduleCronSecondTask() {
        batching.batch();
    }

}
