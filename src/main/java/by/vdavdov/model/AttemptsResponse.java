package by.vdavdov.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class AttemptsResponse {
    @JsonProperty("content")
    private List<Attempt> content;
    @JsonProperty("totalPages")
    private int totalPages;

    public int getTotalPages() {
        return totalPages;
    }

    public void setTotalPages(int totalPages) {
        this.totalPages = totalPages;
    }

    public void setContent(List<Attempt> content) {
        this.content = content;
    }

    public List<Attempt> getContent() {
        return content;
    }
}