package by.vdavdov.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CheckReportsServiceTest {
    final CheckReportsService checkReportsService = new CheckReportsService();
    private final String testInnerJson = "{\"cisList\":[{\"cis\":\"01046100115000532150FLfHxnWiqF%\",\"code\":1,\"description\":\"Повторное нанесение кода\"},{\"cis\":\"01046100115000532150GCgOFD(l_/Z\",\"code\":0,\"description\":\"Ok\"}]}";
    private final String testContent = "{\n" +
            "  \"content\": \"HTTP/1.1 200 OK\\nServer: nginx\\nDate: Mon, 17 Mar 2025 16:43:51 GMT\\nContent-Type: application/json; charset=UTF-8\\nTransfer-Encoding: chunked\\nConnection: keep-alive\\nVary: Accept-Encoding\\nExpires: 0\\nCache-Control: no-cache, no-store, max-age=0, must-revalidate\\nX-XSS-Protection: 1; mode=block\\nPragma: no-cache\\nX-Frame-Options: DENY\\nX-Content-Type-Options: nosniff\\n\\n{\\\"content\\\":\\\"{\\\\\\\"cisList\\\\\\\":[{\\\\\\\"cis\\\\\\\":\\\\\\\"01046100115000532150FLfHxnWiqF%\\\\\\\",\\\\\\\"code\\\\\\\":1,\\\\\\\"description\\\\\\\":\\\\\\\"Повторное нанесение кода\\\\\\\"},{\\\\\\\"cis\\\\\\\":\\\\\\\"01046100115000532150GCgOFD(l_/Z\\\\\\\",\\\\\\\"code\\\\\\\":0,\\\\\\\"description\\\\\\\":\\\\\\\"Ok\\\\\\\"}]}\\\"}\",\n" +
            "  \"size\": 594\n" +
            "}";

    @Test
    void when_content_is_ok_then_return_innerJson() throws JsonProcessingException {
        String innerJson = checkReportsService.getInnerJson(testContent);
        assertNotNull(innerJson);
        assertEquals(testInnerJson, innerJson);
    }

}