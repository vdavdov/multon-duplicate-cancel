package by.vdavdov.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class AttemptsResponse {
    @JsonProperty("content")
    private List<Attempt> content;

    public List<Attempt> getContent() {
        return content;
    }
}