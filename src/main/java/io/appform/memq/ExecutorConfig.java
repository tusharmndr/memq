package io.appform.memq;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecutorConfig {

    @NotNull
    @NotEmpty
    private String name;

    @Min(1)
    @Max(300)
    private int threadPoolSize;

}
