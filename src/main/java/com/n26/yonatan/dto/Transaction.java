package com.n26.yonatan.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import org.hibernate.validator.constraints.NotEmpty;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;

@Data
public class Transaction {
    @NotNull
    @NotEmpty
    @Pattern(regexp = "[a-zA-Z0-9_]*", message = "can only contain letters, numbers or underscore only")
    private String type;

    // although JSR303 says @Min might not work with double, hibernate validators implementation supports that
    @Min(0)
    private double amount;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty("parent_id")
    private Long parentId;

}
