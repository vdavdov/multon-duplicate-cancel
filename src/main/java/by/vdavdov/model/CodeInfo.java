package by.vdavdov.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Модель для парсинга.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class CodeInfo {
    @JsonProperty("code")
    private String code;

    public String getCode() { return code; }
}
