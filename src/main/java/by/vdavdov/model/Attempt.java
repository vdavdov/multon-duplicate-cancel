package by.vdavdov.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Attempt {
    @JsonProperty("created")
    private String created;

    @JsonProperty("responsePath")
    private String responsePath;

    public Instant getCreated() {
        return Instant.parse(created);
    }

    public String getResponsePath() {
        return responsePath;
    }
}