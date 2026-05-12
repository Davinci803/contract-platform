package ru.vkr.contracts.api.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;
import ru.vkr.contracts.shared.model.ContractType;

import java.time.Duration;
import java.util.List;
import java.util.Locale;

@Component
public class GenerationMetrics {
    private final MeterRegistry meterRegistry;

    public GenerationMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void recordPipelineDuration(ContractType contractType, String outcome, Duration duration) {
        Timer.builder("generation.pipeline.duration")
                .description("Pipeline duration for contract generation")
                .tags(tags(contractType, outcome))
                .register(meterRegistry)
                .record(duration);
    }

    public void incrementPipelineOutcome(ContractType contractType, String outcome) {
        Counter.builder("generation.pipeline.outcome.total")
                .description("Total generation pipeline outcomes")
                .tags(tags(contractType, outcome))
                .register(meterRegistry)
                .increment();
    }

    public void incrementRetryNeeded(ContractType contractType, String reason) {
        Counter.builder("generation.pipeline.retry_needed.total")
                .description("Generation jobs that require retry")
                .tags(List.of(
                        Tag.of("contract_type", normalize(contractType)),
                        Tag.of("reason", reason)
                ))
                .register(meterRegistry)
                .increment();
    }

    private Iterable<Tag> tags(ContractType contractType, String outcome) {
        return List.of(
                Tag.of("contract_type", normalize(contractType)),
                Tag.of("outcome", outcome)
        );
    }

    private String normalize(ContractType contractType) {
        if (contractType == null) {
            return "unknown";
        }
        return contractType.name().toLowerCase(Locale.ROOT);
    }
}
