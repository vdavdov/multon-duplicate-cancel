package by.vdavdov.service;

import by.vdavdov.utils.YamlUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class SchedulerService {
    private static final Logger log = LogManager.getLogger(SchedulerService.class);
    private final CheckReportsService checkReportsService = new CheckReportsService();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public void startScheduling() {
        int interval = YamlUtil.scheduleInterval;
        log.info("Starting scheduler with interval {} minutes", interval);
        scheduler.scheduleAtFixedRate(() -> {
            try {
                log.info("Starting reports check task...");
                checkReportsService.getRejectedReports();
            } catch (Exception e) {
                log.error("Error during reports check task", e);
            }
        }, 0, interval, TimeUnit.MINUTES);
    }

    public void stopScheduling() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(1, TimeUnit.MINUTES)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
        }
        log.info("Scheduler stopped");
    }
}
