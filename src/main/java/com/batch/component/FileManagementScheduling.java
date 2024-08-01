package com.batch.component;

import com.batch.repository.mongodb.PersonNosqlRepository;
import com.batch.service.Batch;
import com.opencsv.exceptions.CsvValidationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class FileManagementScheduling {

//    @Scheduled(fixedRate = 5000)
//    private void scheduleFixedRateTask() {
//        System.out.println("Fixed rate task - " + System.currentTimeMillis() / 2000);
//    }
//
//    @Scheduled(fixedDelay = 5000)
//    private void scheduleFixedDelayTask() {
//        System.out.println("Fixed delay task - " + System.currentTimeMillis() / 2000);
//    }

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
     *
     * "0 0/1 * * * ?": Every minute
     * "0 0 12 * * ?": Every day at noon
     * "0 0 12 * * MON-FRI": Every weekday at noon
     * */
//    @Scheduled(cron = "0 0/1 * * * ?")
//    private void scheduleCronEveryMinuteTask() {
//        System.out.println("Fixed cron task - Every minute");
//    }

    @Autowired
    private Batch batch;

//    @Scheduled(cron = "30 * * * * ?")
    @Scheduled(fixedRate = 10000)
    private void scheduleCronSecondTask() throws IOException, CsvValidationException {
        batch.batch();
    }

}
