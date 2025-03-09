package by.vdavdov.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ReportsResponse {
    @JsonProperty("content")
    private List<ContentItem> content;
//    @JsonProperty("totalPages")
//    private int totalPages;
//    @JsonProperty("number")
//    private int number;
    @JsonProperty("last")
    private boolean last;

    public List<ContentItem> getContent() {
        return content;
    }

    public void setContent(List<ContentItem> content) {
        this.content = content;
    }

//    public int getTotalPages() {
//        return totalPages;
//    }
//
//    public void setTotalPages(int totalPages) {
//        this.totalPages = totalPages;
//    }
//
//    public int getNumber() {
//        return number;
//    }
//
//    public void setNumber(int number) {
//        this.number = number;
//    }

    public boolean isLast() {
        return last;
    }

    public void setLast(boolean last) {
        this.last = last;
    }
}