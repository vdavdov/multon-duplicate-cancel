package by.vdavdov;

import by.vdavdov.service.SchedulerService;
import by.vdavdov.utils.YamlUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class Main {
    private static final Logger log = LogManager.getLogger(Main.class);
    public static void main(String[] args) throws IOException {
        if (args.length == 0) {
            try {
                YamlUtil.loadInternalConfig(); // или loadExternalConfig("путь/к/конфигу.yaml")
                SchedulerService scheduler = new SchedulerService();
                scheduler.startScheduling();
                log.info("Запущено со внутреннего конфига, тк аргументы не обнаружены");
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else if (args.length == 1) {
            String path = args[0];
            if(!Files.exists(Path.of(path))) {
                log.error("Файл внешнего конфига не найден по пути из аргументов");
                System.exit(1);
            }
            YamlUtil.loadExternalConfig(path);
            SchedulerService scheduler = new SchedulerService();
            scheduler.startScheduling();
            log.info("Шедулер запущен со внешнего конфига {}.", path);
        }
    }
}