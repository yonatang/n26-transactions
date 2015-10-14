package com.n26.yonatan.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class Status {
    public Status(String status) {
        this.status = status;
    }

    private String status;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String path;
}
