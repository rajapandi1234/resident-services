package io.mosip.resident.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.HashMap;
import java.util.UUID;

import javax.crypto.SecretKey;

import io.mosip.idrepository.core.util.EnvUtil;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestTemplate;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import io.mosip.kernel.core.crypto.spi.CryptoCoreSpec;
import io.mosip.kernel.core.http.ResponseWrapper;
import io.mosip.kernel.core.util.DateUtils;
import io.mosip.resident.dto.GrievanceRequestDTO;
import io.mosip.resident.dto.MainRequestDTO;
import io.mosip.resident.exception.InvalidInputException;
import io.mosip.resident.helper.ObjectStoreHelper;
import io.mosip.resident.service.GrievanceService;
import io.mosip.resident.service.ResidentVidService;
import io.mosip.resident.service.impl.IdentityServiceImpl;
import io.mosip.resident.service.impl.ResidentServiceImpl;
import io.mosip.resident.util.AuditUtil;
import io.mosip.resident.util.Utility;
import io.mosip.resident.validator.RequestValidator;
import org.springframework.web.context.WebApplicationContext;

/**
 * @author Kamesh Shekhar Prasad
 * This class is used to test Grievance controller api.
 */
@ContextConfiguration(classes = { TestContext.class, WebApplicationContext.class })
@RunWith(SpringRunner.class)
@WebMvcTest
@Import(EnvUtil.class)
@ActiveProfiles("test")
public class GrievanceControllerTest {

    @Mock
    private RequestValidator validator;

    @Mock
    private AuditUtil audit;

    @Mock
    private Environment environment;

    @Mock
    private Utility utility;

    @MockBean
    private ObjectStoreHelper objectStore;

    @MockBean
    private Utility utilityBean;


    @MockBean
    @Qualifier("selfTokenRestTemplate")
    private RestTemplate residentRestTemplate;

    @InjectMocks
    GrievanceController grievanceController;

    @Mock
    GrievanceService grievanceService;

    @MockBean
    IdentityServiceImpl identityService;

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ResidentVidService vidService;

    @MockBean
    private ResidentServiceImpl residentService;

    @MockBean
    private CryptoCoreSpec<byte[], byte[], SecretKey, PublicKey, PrivateKey, String> encryptor;

    Gson gson = new GsonBuilder().serializeNulls().create();

    String reqJson;

    byte[] pdfbytes;

    private MainRequestDTO<GrievanceRequestDTO>
            grievanceRequestDTOMainRequestDTO;

    @Before
    public void setup() throws Exception {
        grievanceRequestDTOMainRequestDTO = new MainRequestDTO<>();
        GrievanceRequestDTO grievanceRequestDTO = new GrievanceRequestDTO();
        grievanceRequestDTO.setEventId("7256338756236957");
        grievanceRequestDTO.setMessage("sharing");
        grievanceRequestDTOMainRequestDTO.setRequest(grievanceRequestDTO);
        grievanceRequestDTOMainRequestDTO.setId("mosip.resident.grievance.ticket.request");
        reqJson = gson.toJson(grievanceRequestDTOMainRequestDTO);
        pdfbytes = "uin".getBytes();
        Mockito.when(utility.getFileName(Mockito.anyString(), Mockito.anyString(), Mockito.anyInt(), Mockito.anyString())).thenReturn("file");
        Mockito.when(environment.getProperty(Mockito.anyString())).thenReturn("property");
    }

    @Test
    public void testGetCardSuccess() throws Exception {
        io.mosip.kernel.core.http.ResponseWrapper<Object> responseWrapper = new io.mosip.kernel.core.http.ResponseWrapper<>();
        HashMap<String, String> response = new HashMap<>();
        String ticketId = UUID.randomUUID().toString();
        response.put("ticketId", ticketId);
        responseWrapper.setResponse(response);
        responseWrapper.setId("mosip.resident.grievance.ticket.request");
        responseWrapper.setResponsetime(DateUtils.getUTCCurrentDateTime());
        Mockito.when(grievanceService.getGrievanceTicket(any())).thenReturn(responseWrapper);
        ResponseWrapper<Object> responseWrapper1 = grievanceController.grievanceTicket(grievanceRequestDTOMainRequestDTO);
        Assert.assertEquals("mosip.resident.grievance.ticket.request", responseWrapper1.getId());
    }

    @Test(expected = Exception.class)
    public void testGetCardFailed() throws Exception {
        doThrow(new InvalidInputException()).
                when(validator).validateGrievanceRequestDto(any());
        io.mosip.kernel.core.http.ResponseWrapper<Object> responseWrapper = new io.mosip.kernel.core.http.ResponseWrapper<>();
        HashMap<String, String> response = new HashMap<>();
        String ticketId = UUID.randomUUID().toString();
        response.put("ticketId", ticketId);
        responseWrapper.setResponse(response);
        responseWrapper.setId("mosip.resident.grievance.ticket.request");
        responseWrapper.setResponsetime(DateUtils.getUTCCurrentDateTime());
        Mockito.when(grievanceService.getGrievanceTicket(any())).thenReturn(responseWrapper);
        ResponseWrapper<Object> responseWrapper1 = grievanceController.grievanceTicket(grievanceRequestDTOMainRequestDTO);
        Assert.assertEquals("mosip.resident.grievance.ticket.request", responseWrapper1.getId());
    }

}