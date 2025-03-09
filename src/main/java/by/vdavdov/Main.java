package by.vdavdov;

import by.vdavdov.service.AuthService;
import by.vdavdov.service.CheckReportsService;
import by.vdavdov.service.SchedulerService;
import by.vdavdov.utils.YamlUtil;

import java.io.IOException;

public class Main {
    public static void main(String[] args) {
        try {
            YamlUtil.loadInternalConfig(); // или loadExternalConfig("путь/к/конфигу.yaml")
            SchedulerService scheduler = new SchedulerService();
            scheduler.startScheduling();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}