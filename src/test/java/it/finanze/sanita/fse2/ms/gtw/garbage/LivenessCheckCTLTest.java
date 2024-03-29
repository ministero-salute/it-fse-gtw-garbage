package it.finanze.sanita.fse2.ms.gtw.garbage;

import it.finanze.sanita.fse2.ms.gtw.garbage.config.Constants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ComponentScan
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ActiveProfiles(Constants.Profile.TEST)
@AutoConfigureMockMvc
class LivenessCheckCTLTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void testHealthCheck() throws Exception {
        mockMvc.perform(
            get("/status")
            .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk()
        );
    }
}
