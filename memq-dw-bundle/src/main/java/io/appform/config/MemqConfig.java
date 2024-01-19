package io.appform.config;

import lombok.Value;

import javax.validation.Valid;
import java.util.List;

@Value
public class MemqConfig {

    @Valid
    List<ExecutorConfig> executors = List.of();

}
