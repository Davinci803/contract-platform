package ru.vkr.contracts.api.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import ru.vkr.contracts.api.domain.ContractVersion;
import ru.vkr.contracts.api.domain.EntityContract;
import ru.vkr.contracts.api.domain.GeneratedArtifact;
import ru.vkr.contracts.api.domain.GenerationJob;
import ru.vkr.contracts.api.domain.PublicationLog;
import ru.vkr.contracts.api.repo.GeneratedArtifactRepository;
import ru.vkr.contracts.api.repo.PublicationLogRepository;
import ru.vkr.contracts.shared.model.ContractType;

import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReadModelController.class)
@AutoConfigureMockMvc(addFilters = false)
class ReadModelContractIntegrationTest {
    @Autowired
    MockMvc mockMvc;

    @MockBean
    GeneratedArtifactRepository generatedArtifactRepository;

    @MockBean
    PublicationLogRepository publicationLogRepository;

    @Test
    void shouldReturnStableArtifactReadModelContract() throws Exception {
        GeneratedArtifact artifact = createArtifactFixture();
        given(generatedArtifactRepository.findTop20ByOrderByIdDesc()).willReturn(List.of(artifact));

        mockMvc.perform(get("/api/read-model/artifacts?limit=1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(501))
                .andExpect(jsonPath("$[0].jobId").value(301))
                .andExpect(jsonPath("$[0].coordinates").value("ru.vkr.contracts.generated:payments-rest-client:1.0.0"))
                .andExpect(jsonPath("$[0].publicationUrl").value(
                        "http://localhost:8081/repository/maven-releases/ru/vkr/contracts/generated/payments-rest-client/1.0.0/payments-rest-client-1.0.0.jar"
                ))
                .andExpect(jsonPath("$[0].schemaSubject").value("payments.created.value"))
                .andExpect(jsonPath("$[0].target").doesNotExist())
                .andExpect(jsonPath("$[0].eventType").doesNotExist());
    }

    @Test
    void shouldFilterArtifactsByCorrelationId() throws Exception {
        GeneratedArtifact artifact = createArtifactFixture();
        given(generatedArtifactRepository.findTop20ByJob_CorrelationIdOrderByIdDesc("corr-read-model"))
                .willReturn(List.of(artifact));

        mockMvc.perform(get("/api/read-model/artifacts?limit=1&correlationId=corr-read-model"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(501))
                .andExpect(jsonPath("$[0].jobId").value(301));

        verify(generatedArtifactRepository).findTop20ByJob_CorrelationIdOrderByIdDesc("corr-read-model");
    }

    @Test
    void shouldReturnStablePublicationLogReadModelContract() throws Exception {
        PublicationLog publicationLog = createPublicationLogFixture();
        given(publicationLogRepository.findTop50ByOrderByIdDesc()).willReturn(List.of(publicationLog));

        mockMvc.perform(get("/api/read-model/publication-logs?limit=1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(701))
                .andExpect(jsonPath("$[0].jobId").value(301))
                .andExpect(jsonPath("$[0].target").value("NEXUS"))
                .andExpect(jsonPath("$[0].status").value("SUCCESS"))
                .andExpect(jsonPath("$[0].message").value("Published to Nexus"))
                .andExpect(jsonPath("$[0].eventType").value("PUBLICATION_COMPLETED"))
                .andExpect(jsonPath("$[0].errorCategory").value(PublicationLog.ERROR_CATEGORY_NONE))
                .andExpect(jsonPath("$[0].correlationId").value("corr-read-model"))
                .andExpect(jsonPath("$[0].coordinates").doesNotExist())
                .andExpect(jsonPath("$[0].publicationUrl").doesNotExist());
    }

    @Test
    void shouldFilterPublicationLogsByCorrelationId() throws Exception {
        PublicationLog publicationLog = createPublicationLogFixture();
        given(publicationLogRepository.findTop50ByCorrelationIdOrderByIdDesc("corr-read-model"))
                .willReturn(List.of(publicationLog));

        mockMvc.perform(get("/api/read-model/publication-logs?limit=1&correlationId=corr-read-model"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(701))
                .andExpect(jsonPath("$[0].correlationId").value("corr-read-model"));

        verify(publicationLogRepository).findTop50ByCorrelationIdOrderByIdDesc("corr-read-model");
    }

    private GeneratedArtifact createArtifactFixture() {
        EntityContract contract = new EntityContract("Read model contract", ContractType.OPENAPI);
        ReflectionTestUtils.setField(contract, "id", 101L);
        ContractVersion version = new ContractVersion(contract, "1.0.0", "openapi: 3.0.1\npaths: {}", "tester");
        ReflectionTestUtils.setField(version, "id", 201L);
        GenerationJob job = new GenerationJob(version, "corr-read-model");
        ReflectionTestUtils.setField(job, "id", 301L);

        GeneratedArtifact artifact = new GeneratedArtifact(
                job,
                "ru.vkr.contracts.generated:payments-rest-client:1.0.0",
                "http://localhost:8081/repository/maven-releases/ru/vkr/contracts/generated/payments-rest-client/1.0.0/payments-rest-client-1.0.0.jar",
                "payments.created.value"
        );
        ReflectionTestUtils.setField(artifact, "id", 501L);
        return artifact;
    }

    private PublicationLog createPublicationLogFixture() {
        EntityContract contract = new EntityContract("Read model contract", ContractType.OPENAPI);
        ReflectionTestUtils.setField(contract, "id", 101L);
        ContractVersion version = new ContractVersion(contract, "1.0.0", "openapi: 3.0.1\npaths: {}", "tester");
        ReflectionTestUtils.setField(version, "id", 201L);
        GenerationJob job = new GenerationJob(version, "corr-read-model");
        ReflectionTestUtils.setField(job, "id", 301L);

        PublicationLog publicationLog = new PublicationLog(
                job,
                "NEXUS",
                "SUCCESS",
                "Published to Nexus",
                "PUBLICATION_COMPLETED",
                PublicationLog.ERROR_CATEGORY_NONE
        );
        ReflectionTestUtils.setField(publicationLog, "id", 701L);
        return publicationLog;
    }
}
