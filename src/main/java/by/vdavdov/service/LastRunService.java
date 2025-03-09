package by.vdavdov.service;

import by.vdavdov.utils.YamlUtil;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class LastRunService {
    private static final Logger log = LogManager.getLogger(LastRunService.class);
    private static final String STATE_FILE = "last_run.txt";

    public Instant getLastRunTime() {
        try {
            if (Files.exists(Path.of(STATE_FILE))) {
                String time = Files.readString(Path.of(STATE_FILE));
                return Instant.parse(time);
            }
        } catch (IOException e) {
            log.error("Ошибка чтения времени последнего запуска", e);
        }
        return Instant.now().minus(1, ChronoUnit.DAYS); // Дефолтное значение
    }

    public void saveLastRunTime(Instant time) {
        try {
            Files.writeString(Path.of(STATE_FILE), time.toString());
        } catch (IOException e) {
            log.error("Ошибка сохранения времени запуска", e);
        }
    }
}