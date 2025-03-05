package by.vdavdov;

import by.vdavdov.service.AuthService;
import by.vdavdov.utils.YamlUtil;

public class Main {
    public static void main(String[] args) throws Exception {
        YamlUtil.loadInternalConfig();
        AuthService authService = new AuthService();
        System.out.println(authService.getToken());
    }
}