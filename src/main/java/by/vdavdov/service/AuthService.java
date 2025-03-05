package by.vdavdov.service;

import by.vdavdov.utils.YamlUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class AuthService {
    private static final Logger log = LogManager.getLogger(AuthService.class);

    /**
     * Запрашивает JWT-токен с сервера.
     *
     * @return JWT-токен.
     * @throws Exception Если запрос не удался.
     */
    public String getToken() throws Exception {
        String authUrl = YamlUtil.host + "/auth/api/auth?locale=ru-RU";
        String login = YamlUtil.authLogin;
        String password = YamlUtil.authPassword;

        String jsonBody = String.format(
                "{\"login\": \"%s\", \"password\": \"%s\"}"
                , login, password
        );

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(new URI(authUrl))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        HttpResponse<String> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode jsonNode = objectMapper.readTree(response.body());
            String token = jsonNode.get("token").asText();

            if (token != null && !token.isEmpty()) {
                return token;
            } else {
                log.error("Токен не найден в ответе сервера");
                throw new RuntimeException("Токен не найден в ответе сервера");
            }
        } else {
            log.error("Ошибка аутентификации: {} {}", response.statusCode(), response.body());
            throw new RuntimeException("Ошибка аутентификации: " + response.statusCode() + " " + response.body());
        }
    }
}
