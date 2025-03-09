package by.vdavdov;

import by.vdavdov.service.AuthService;
import by.vdavdov.service.CheckReportsService;
import by.vdavdov.utils.YamlUtil;

import java.util.Map;

public class Main {
    public static void main(String[] args) throws Exception {
        YamlUtil.loadInternalConfig();
        ConnectionManager.getDataSource();
        CheckReportsService checkReportsService = new CheckReportsService();
        Map<String, String> rejectedReports = checkReportsService.getRejectedReports();

        System.out.println(rejectedReports.size());
        for (Map.Entry<String, String> entry : rejectedReports.entrySet()) {
            System.out.println(entry.getKey() + ": " + entry.getValue());
        }
    }
}