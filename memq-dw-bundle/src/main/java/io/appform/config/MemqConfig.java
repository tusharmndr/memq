package io.appform.config;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import javax.validation.Valid;
import java.util.List;

@Value
@Builder
@Jacksonized
public class MemqConfig {

    @Valid
    @Builder.Default
    List<ExecutorConfig> executors = List.of();

}
