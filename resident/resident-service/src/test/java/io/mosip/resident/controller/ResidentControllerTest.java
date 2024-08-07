package io.mosip.resident.controller;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import javax.crypto.SecretKey;

import io.mosip.resident.util.*;
import org.json.simple.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.client.RestTemplate;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import io.mosip.kernel.cbeffutil.impl.CbeffImpl;
import io.mosip.kernel.core.crypto.spi.CryptoCoreSpec;
import io.mosip.kernel.core.exception.ServiceError;
import io.mosip.kernel.core.http.ResponseWrapper;
import io.mosip.kernel.core.util.DateUtils;
import io.mosip.kernel.openid.bridge.api.service.validator.ScopeValidator;
import io.mosip.resident.constant.EventStatus;
import io.mosip.resident.constant.IdType;
import io.mosip.resident.constant.ResidentErrorCode;
import io.mosip.resident.constant.ServiceType;
import io.mosip.resident.dto.AidStatusRequestDTO;
import io.mosip.resident.dto.AidStatusResponseDTO;
import io.mosip.resident.dto.AuthHistoryRequestDTO;
import io.mosip.resident.dto.AuthHistoryResponseDTO;
import io.mosip.resident.dto.AuthLockOrUnLockRequestDto;
import io.mosip.resident.dto.AuthLockOrUnLockRequestDtoV2;
import io.mosip.resident.dto.AuthTypeStatusDtoV2;
import io.mosip.resident.dto.BellNotificationDto;
import io.mosip.resident.dto.EuinRequestDTO;
import io.mosip.resident.dto.IdResponseDTO1;
import io.mosip.resident.dto.PageDto;
import io.mosip.resident.dto.RegStatusCheckResponseDTO;
import io.mosip.resident.dto.RequestDTO;
import io.mosip.resident.dto.RequestWrapper;
import io.mosip.resident.dto.ResidentDemographicUpdateRequestDTO;
import io.mosip.resident.dto.ResidentDocuments;
import io.mosip.resident.dto.ResidentReprintRequestDto;
import io.mosip.resident.dto.ResidentReprintResponseDto;
import io.mosip.resident.dto.ResidentUpdateRequestDto;
import io.mosip.resident.dto.ResidentUpdateResponseDTO;
import io.mosip.resident.dto.ResponseDTO;
import io.mosip.resident.dto.ResponseDTO1;
import io.mosip.resident.dto.ServiceHistoryResponseDto;
import io.mosip.resident.dto.SortType;
import io.mosip.resident.dto.UnreadNotificationDto;
import io.mosip.resident.dto.UserInfoDto;
import io.mosip.resident.exception.ApisResourceAccessException;
import io.mosip.resident.exception.InvalidInputException;
import io.mosip.resident.exception.ResidentServiceCheckedException;
import io.mosip.resident.exception.ResidentServiceException;
import io.mosip.resident.helper.ObjectStoreHelper;
import io.mosip.resident.service.DocumentService;
import io.mosip.resident.service.ProxyIdRepoService;
import io.mosip.resident.service.ResidentVidService;
import io.mosip.resident.service.impl.IdAuthServiceImpl;
import io.mosip.resident.service.impl.IdentityServiceImpl;
import io.mosip.resident.service.impl.ResidentServiceImpl;
import io.mosip.resident.test.ResidentTestBootApplication;
import io.mosip.resident.validator.RequestValidator;
import reactor.util.function.Tuple3;
import reactor.util.function.Tuples;

/**
 * @author Sowmya Ujjappa Banakar
 * @author Jyoti Prakash Nayak
 *
 */
@RunWith(MockitoJUnitRunner.class)
@SpringBootTest(classes = ResidentTestBootApplication.class)
@AutoConfigureMockMvc
@TestPropertySource(locations = "classpath:application.properties")
public class ResidentControllerTest {

	private static final String LOCALE_EN_US = "en-US";

	@Mock
	private ProxyIdRepoService proxyIdRepoService;

	@Mock
	private ResidentServiceImpl residentService;

	@Mock
	CbeffImpl cbeff;

	@Mock
	private RequestValidator validator;

	@Mock
	private ResidentVidService vidService;

	@Mock
	private Utility utilityBean;

	@Mock
	private IdAuthServiceImpl idAuthServiceImpl;

	@Mock
	private IdentityServiceImpl identityServiceImpl;

	@Mock
	private DocumentService docService;

	@Mock
	private ScopeValidator scopeValidator;

	@Mock
	private ObjectStoreHelper objectStore;

	@Mock
	private AuditUtil audit;

	@Mock
	private Utility utility;

	@Mock
	private Environment environment;

	@Mock
	private IdentityDataUtil identityDataUtil;

	@Mock
	private AvailableClaimUtility availableClaimUtility;

	@Mock
	private CryptoCoreSpec<byte[], byte[], SecretKey, PublicKey, PrivateKey, String> encryptor;

	@Mock
	@Qualifier("selfTokenRestTemplate")
	private RestTemplate residentRestTemplate;

	@InjectMocks
	ResidentController residentController;

	RequestWrapper<AuthLockOrUnLockRequestDto> authLockRequest;
	RequestWrapper<EuinRequestDTO> euinRequest;
	RequestWrapper<AuthHistoryRequestDTO> authHistoryRequest;
	RequestWrapper<AuthLockOrUnLockRequestDtoV2> authTypeStatusRequest;

	/** The array to json. */
	private String authLockRequestToJson;
	private String euinRequestToJson;
	private String historyRequestToJson;
	private String authStatusRequestToJson;
	private Gson gson;

	/** The mock mvc. */
	@Autowired
	private MockMvc mockMvc;
	private String schemaJson;

	@Mock
	private UinVidValidator uinVidValidator;

	@Before
	public void setUp() throws ApisResourceAccessException, IOException {
		MockitoAnnotations.initMocks(this);
		this.mockMvc = MockMvcBuilders.standaloneSetup(residentController).build();

		authLockRequest = new RequestWrapper<AuthLockOrUnLockRequestDto>();

		AuthLockOrUnLockRequestDto authLockRequestDto = new AuthLockOrUnLockRequestDto();
		authLockRequestDto.setIndividualId("5734728510");
		authLockRequestDto.setOtp("111111");
		authLockRequestDto.setTransactionID("1234567898");
		List<String> authTypes = new ArrayList<>();
		authTypes.add("bio-FIR");
		authLockRequestDto.setAuthType(authTypes);
		authLockRequest.setRequest(authLockRequestDto);
		euinRequest = new RequestWrapper<EuinRequestDTO>();
		euinRequest.setRequest(new EuinRequestDTO("5734728510", "1234567890", IdType.UIN.name(), "UIN", "111111"));

		AuthLockOrUnLockRequestDtoV2 authLockOrUnLockRequestDtoV2 = new AuthLockOrUnLockRequestDtoV2();
		AuthTypeStatusDtoV2 authTypeStatusDto = new AuthTypeStatusDtoV2();
		authTypeStatusDto.setAuthType("bio-FIR");
		authTypeStatusDto.setLocked(true);
		authTypeStatusDto.setUnlockForSeconds(1L);
		List<AuthTypeStatusDtoV2> authTypeStatusDtoList = new ArrayList<>();
		authTypeStatusDtoList.add(authTypeStatusDto);
		authLockOrUnLockRequestDtoV2.setAuthTypes(authTypeStatusDtoList);
		authTypeStatusRequest = new RequestWrapper<>();
		authTypeStatusRequest.setRequest(authLockOrUnLockRequestDtoV2);
		authTypeStatusRequest.setRequesttime(LocalDateTime.now().toString());
		authTypeStatusRequest.setVersion("v1");
		authTypeStatusRequest.setId("io.mosip.resident.authHistory");
		gson = new GsonBuilder().serializeNulls().create();
		authLockRequestToJson = gson.toJson(authLockRequest);
		euinRequestToJson = gson.toJson(euinRequest);

		authStatusRequestToJson = gson.toJson(authTypeStatusRequest);
		Mockito.doNothing().when(audit).setAuditRequestDto(Mockito.any());

		when(availableClaimUtility.getResidentIndvidualIdFromSession()).thenReturn("5734728510");
		when(environment.getProperty(anyString())).thenReturn("property");
		schemaJson = "schema";
	}

	@Test
	@WithUserDetails("resident")
	public void testGetRidStatusSuccess() throws Exception {
		RegStatusCheckResponseDTO dto = new RegStatusCheckResponseDTO();
		dto.setRidStatus("PROCESSED");
		Mockito.doReturn(dto).when(residentService).getRidStatus((RequestDTO) Mockito.any());
		this.mockMvc
				.perform(post("/rid/check-status").contentType(MediaType.APPLICATION_JSON)
						.content(authLockRequestToJson))
				.andExpect(status().isOk()).andExpect(jsonPath("$.response.ridStatus", is("PROCESSED")));
	}

	@Test(expected = InvalidInputException.class)
	public void testGetRidStatusException() throws Exception {
		RequestWrapper<RequestDTO> requestDTO = new RequestWrapper<>();
		requestDTO.setRequest(new RequestDTO());
		ReflectionTestUtils.setField(residentController, "checkStatusId", "id");
		Mockito.doThrow(InvalidInputException.class).when(validator).validateRidCheckStatusRequestDTO(Mockito.any());
		residentController.getRidStatus(requestDTO);
	}

	@Test
	@WithUserDetails("resident")
	public void testRequestAuthLockSuccess() throws Exception {
		ResponseDTO responseDto = new ResponseDTO();
		responseDto.setStatus("success");
		doNothing().when(validator).validateAuthLockOrUnlockRequest(Mockito.any(), Mockito.any());
		Mockito.doReturn(responseDto).when(residentService).reqAauthTypeStatusUpdate(Mockito.any(), Mockito.any());

		this.mockMvc
				.perform(post("/req/auth-lock").contentType(MediaType.APPLICATION_JSON).content(authLockRequestToJson))
				.andExpect(status().isOk()).andExpect(jsonPath("$.response.status", is("success")));
	}

	@Test
	@WithUserDetails("resident")
	public void testReqAuthTypeLock() throws Exception {
		ResponseDTO responseDto = new ResponseDTO();
		responseDto.setStatus("success");
		doNothing().when(validator).validateAuthLockOrUnlockRequestV2(Mockito.any());
		Mockito.doReturn(Tuples.of(responseDto, "12345")).when(residentService).reqAauthTypeStatusUpdateV2(Mockito.any());
		residentController.reqAauthTypeStatusUpdateV2(authTypeStatusRequest);
		validator.validateAuthLockOrUnlockRequestV2(authTypeStatusRequest);
		this.mockMvc.perform(
				post("/auth-lock-unlock").contentType(MediaType.APPLICATION_JSON).content(authStatusRequestToJson))
				.andExpect(status().isOk()).andExpect(status().isOk());
	}

	@Test(expected = InvalidInputException.class)
	public void testReqAuthTypeLockBadRequest() throws Exception {
		ReflectionTestUtils.setField(residentController, "authLockStatusUpdateV2Id", "id");
		doThrow(InvalidInputException.class).when(validator).validateAuthLockOrUnlockRequestV2(Mockito.any());
		residentController.reqAauthTypeStatusUpdateV2(authTypeStatusRequest);
	}

	@Test
	@WithUserDetails("resident")
	public void testRequestAuthLockBadRequest() throws Exception {

		MvcResult result = this.mockMvc
				.perform(post("/req/auth-lock").contentType(MediaType.APPLICATION_JSON).content(""))
				.andExpect(status().isBadRequest()).andReturn();
	}

	@Test
	@WithUserDetails("resident")
	public void testRequestEuinSuccess() throws Exception {
		doNothing().when(validator).validateEuinRequest(Mockito.any());
		Mockito.doReturn(new byte[10]).when(residentService).reqEuin(Mockito.any());

		MvcResult result = this.mockMvc
				.perform(post("/req/euin").contentType(MediaType.APPLICATION_JSON).content(euinRequestToJson))
				.andExpect(status().isOk()).andReturn();
		assertEquals("application/pdf", result.getResponse().getContentType());
	}

	@Test
	@WithUserDetails("resident")
	public void testRequestEuinBadRequest() throws Exception {

		MvcResult result = this.mockMvc.perform(post("/req/euin").contentType(MediaType.APPLICATION_JSON).content(""))
				.andExpect(status().isBadRequest()).andReturn();
	}

	@Test
	@WithUserDetails("resident")
	public void testReprintUINSuccess() throws Exception {
		ResidentReprintResponseDto reprintResp = new ResidentReprintResponseDto();
		reprintResp.setRegistrationId("123456789");
		reprintResp.setMessage("Notification sent");
		ResponseWrapper<ResidentReprintResponseDto> response = new ResponseWrapper<>();
		response.setResponse(reprintResp);

		Mockito.when(residentService.reqPrintUin(Mockito.any())).thenReturn(reprintResp);

		RequestWrapper<ResidentReprintRequestDto> requestWrapper = new RequestWrapper<>();
		ResidentReprintRequestDto request = new ResidentReprintRequestDto();
		request.setIndividualId("3527812406");
		request.setIndividualIdType(IdType.UIN.name());
		request.setOtp("1234");
		request.setTransactionID("9876543210");
		requestWrapper.setRequest(request);
		requestWrapper.setId(",osip.resident.reprint");
		requestWrapper.setVersion("1.0");
		requestWrapper.setRequesttime(DateUtils.getUTCCurrentDateTimeString());

		Gson gson = new GsonBuilder().serializeNulls().create();
		String requestAsString = gson.toJson(requestWrapper);

		this.mockMvc.perform(post("/req/print-uin").contentType(MediaType.APPLICATION_JSON).content(requestAsString))
				.andExpect(status().isOk());
	}

	@Test
	@WithUserDetails("reg-admin")
	public void testRequestAuthUnLockSuccess() throws Exception {
		ResponseDTO responseDto = new ResponseDTO();
		responseDto.setStatus("success");
		Mockito.doReturn(responseDto).when(residentService).reqAauthTypeStatusUpdate(Mockito.any(), Mockito.any());

		this.mockMvc
				.perform(
						post("/req/auth-unlock").contentType(MediaType.APPLICATION_JSON).content(authLockRequestToJson))
				.andExpect(status().isOk()).andExpect(jsonPath("$.response.status", is("success")));
	}

	@Test
	@WithUserDetails("reg-admin")
	public void testRequestAuthUnLockBadRequest() throws Exception {

		MvcResult result = this.mockMvc
				.perform(post("/req/auth-unlock").contentType(MediaType.APPLICATION_JSON).content(""))
				.andExpect(status().isBadRequest()).andReturn();
	}

	@Test
	@WithUserDetails("reg-admin")
	public void testGetServiceHistorySuccess() throws Exception {
		ResponseWrapper<PageDto<ServiceHistoryResponseDto>> response = new ResponseWrapper<>();
		residentController.getServiceHistory("eng", 1, 12, LocalDate.parse("2022-06-10"),
				LocalDate.parse("2022-06-10"), SortType.ASC.toString(),
				ServiceType.AUTHENTICATION_REQUEST.name(), null, null, 0, LOCALE_EN_US);
		mockMvc.perform(MockMvcRequestBuilders.get("/service-history/eng").contentType(MediaType.APPLICATION_JSON_VALUE))
				.andExpect(status().isOk());
	}

	@Test(expected = InvalidInputException.class)
	public void testGetServiceHistoryException() throws Exception {
		ReflectionTestUtils.setField(residentController, "serviceHistoryId", "id");
		Mockito.doThrow(InvalidInputException.class).when(validator).validateServiceHistoryRequest(Mockito.any(),
				Mockito.any(), Mockito.anyString(), Mockito.anyString(), Mockito.anyString(), Mockito.anyString(),
				Mockito.anyString());
		residentController.getServiceHistory("eng", 1, 12, LocalDate.parse("2022-06-10"),
				LocalDate.parse("2022-06-10"), SortType.ASC.toString(),
				ServiceType.AUTHENTICATION_REQUEST.name(), "success", "123456", 0, LOCALE_EN_US);
	}

	@Test
	@WithUserDetails("reg-admin")
	public void testRequestAuthHistorySuccess() throws Exception {
		authHistoryRequest = new RequestWrapper<AuthHistoryRequestDTO>();
		AuthHistoryRequestDTO hisdto = new AuthHistoryRequestDTO();
		hisdto.setIndividualId("1234");
		hisdto.setOtp("1234");
		hisdto.setTransactionID("1234");
		authHistoryRequest.setRequest(hisdto);
		authHistoryRequest.setId("id");
		authHistoryRequest.setRequesttime("12-12-2009");
		authHistoryRequest.setVersion("v1");
		historyRequestToJson = gson.toJson(authHistoryRequest);
		AuthHistoryResponseDTO responseDto = new AuthHistoryResponseDTO();
		responseDto.setMessage("success");
		doNothing().when(validator).validateAuthHistoryRequest(Mockito.any());
		Mockito.doReturn(responseDto).when(residentService).reqAuthHistory(Mockito.any());

		this.mockMvc
				.perform(
						post("/req/auth-history").contentType(MediaType.APPLICATION_JSON).content(historyRequestToJson))
				.andExpect(status().isOk()).andExpect(jsonPath("$.response.message", is("success")));
	}

	@Test(expected = InvalidInputException.class)
	public void testRequestAuthHistoryBadRequest() throws Exception {
		authHistoryRequest = new RequestWrapper<AuthHistoryRequestDTO>();
		AuthHistoryRequestDTO hisdto = new AuthHistoryRequestDTO();
		hisdto.setIndividualId("1234");
		hisdto.setOtp("1234");
		hisdto.setTransactionID("1234");
		authHistoryRequest.setRequest(hisdto);
		doThrow(InvalidInputException.class).when(validator).validateAuthHistoryRequest(Mockito.any());
		residentController.reqAuthHistory(authHistoryRequest);
	}

	@Test(expected = Exception.class)
	@WithUserDetails("reg-admin")
	public void testRequestUINUpdate() throws Exception {
		ResidentUpdateRequestDto dto = new ResidentUpdateRequestDto();
		ResidentDocuments document = new ResidentDocuments();
		document.setName("POA");
		document.setValue("abecfsgdsdg");
		List<ResidentDocuments> list = new ArrayList<>();
		list.add(document);
		dto.setDocuments(list);
		dto.setIdentityJson("sdgfdgsfhfh");
		dto.setIndividualId("9876543210");
		dto.setIndividualIdType(IdType.UIN.name());
		dto.setOtp("1234");
		dto.setTransactionID("12345");
		RequestWrapper<ResidentUpdateRequestDto> reqWrapper = new RequestWrapper<>();
		reqWrapper.setRequest(dto);
		reqWrapper.setId("mosip.resident.uin");
		reqWrapper.setVersion("v1");
		Mockito.when(residentService.reqUinUpdate(Mockito.any())).thenReturn(Tuples.of(new Object(), "123"));
		String requestAsString = gson.toJson(reqWrapper);
		this.mockMvc.perform(post("/req/update-uin").contentType(MediaType.APPLICATION_JSON).content(requestAsString))
				.andExpect(status().isOk());

	}

	@Test
	@WithUserDetails("reg-admin")
	public void testUpdateUinDemographics() throws Exception {
		ResidentDemographicUpdateRequestDTO request = new ResidentDemographicUpdateRequestDTO();
		request.setTransactionID("12345");
		request.setIdentity(JsonUtil.readValue("{\"name\":\"My Name\"}", JSONObject.class));

		RequestWrapper<ResidentDemographicUpdateRequestDTO> requestDTO = new RequestWrapper<>();
		requestDTO.setRequest(request);
		requestDTO.setId("mosip.resident.demographic");
		requestDTO.setVersion("v1");
		JSONObject jsonObject = new JSONObject();
		jsonObject.put("IDSchemaVersion", "0.1");
		IdResponseDTO1 idResponseDTO1 = new IdResponseDTO1();
		ResponseDTO1 responseDTO1 = new ResponseDTO1();
		responseDTO1.setIdentity(jsonObject);
		idResponseDTO1.setResponse(responseDTO1);
		Tuple3<JSONObject, String, IdResponseDTO1> idRepoJsonSchemaJsonAndIdResponseDtoTuple = Tuples.of(jsonObject, schemaJson, idResponseDTO1);
		when(identityDataUtil.
                getIdentityDataFromIndividualID(Mockito.anyString())).thenReturn(idRepoJsonSchemaJsonAndIdResponseDtoTuple);
		when(availableClaimUtility.getResidentIndvidualIdFromSession()).thenReturn("9876543210");
		when(residentService.reqUinUpdate(Mockito.any(), Mockito.any(), Mockito.anyBoolean(), Mockito.any(), Mockito.any(), Mockito.any())).thenReturn(Tuples.of(new ResidentUpdateResponseDTO(), "12345"));
		ResponseEntity<Object> responseEntity = residentController
				.updateUinDemographics(requestDTO);
		assertEquals(new ResidentUpdateResponseDTO(), ((ResponseWrapper<Object>)responseEntity.getBody()).getResponse());
	}

	@Test(expected = InvalidInputException.class)
	public void testUpdateUinDemographicsIdTypeUINException() throws Exception {
		Mockito.when(uinVidValidator.validateUin(Mockito.anyString())).thenReturn(true);
		Mockito.doThrow(InvalidInputException.class).when(validator).validateUpdateRequest(Mockito.any(), Mockito.anyBoolean(), Mockito.anyString());
		ResidentDemographicUpdateRequestDTO request = new ResidentDemographicUpdateRequestDTO();
		request.setTransactionID("12345");
		request.setIdentity(JsonUtil.readValue("{\"name\":\"My Name\"}", JSONObject.class));

		RequestWrapper<ResidentDemographicUpdateRequestDTO> requestDTO = new RequestWrapper<>();
		requestDTO.setRequest(request);
		requestDTO.setId("mosip.resident.demographic");
		requestDTO.setVersion("v1");
		JSONObject jsonObject = new JSONObject();
		jsonObject.put("IDSchemaVersion", "0.1");
		Tuple3<JSONObject, String, IdResponseDTO1> idRepoJsonSchemaJsonAndIdResponseDtoTuple = Tuples.of(jsonObject, schemaJson, new IdResponseDTO1());
		when(identityDataUtil.
                getIdentityDataFromIndividualID(Mockito.anyString())).thenReturn(idRepoJsonSchemaJsonAndIdResponseDtoTuple);
		when(availableClaimUtility.getResidentIndvidualIdFromSession()).thenReturn("9876543210");
		residentController.updateUinDemographics(requestDTO);
	}

	@Test(expected = InvalidInputException.class)
	public void testUpdateUinDemographicsIdTypeVIDException() throws Exception {
		Mockito.when(uinVidValidator.validateVid(Mockito.anyString())).thenReturn(true);
		Mockito.doThrow(InvalidInputException.class).when(validator).validateUpdateRequest(Mockito.any(), Mockito.anyBoolean(), Mockito.anyString());
		ResidentDemographicUpdateRequestDTO request = new ResidentDemographicUpdateRequestDTO();
		request.setTransactionID("12345");
		request.setIdentity(JsonUtil.readValue("{\"name\":\"My Name\"}", JSONObject.class));

		RequestWrapper<ResidentDemographicUpdateRequestDTO> requestDTO = new RequestWrapper<>();
		requestDTO.setRequest(request);
		requestDTO.setId("mosip.resident.demographic");
		requestDTO.setVersion("v1");
		JSONObject jsonObject = new JSONObject();
		jsonObject.put("IDSchemaVersion", "0.1");
		Tuple3<JSONObject, String, IdResponseDTO1> idRepoJsonSchemaJsonAndIdResponseDtoTuple = Tuples.of(jsonObject, schemaJson, new IdResponseDTO1());
		when(identityDataUtil.
                getIdentityDataFromIndividualID(Mockito.anyString())).thenReturn(idRepoJsonSchemaJsonAndIdResponseDtoTuple);
		when(availableClaimUtility.getResidentIndvidualIdFromSession()).thenReturn("9876543210");
		residentController.updateUinDemographics(requestDTO);
	}

	@Test
	@WithUserDetails("reg-admin")
	public void testAuthLockStatus() throws Exception {
		ResponseWrapper<AuthLockOrUnLockRequestDtoV2> responseWrapper = new ResponseWrapper<>();
		when(availableClaimUtility.getResidentIndvidualIdFromSession()).thenReturn("9876543210");
		when(residentService.getAuthLockStatus(Mockito.any())).thenReturn(responseWrapper);
		ResponseWrapper<AuthLockOrUnLockRequestDtoV2> resultRequestWrapper = residentController.getAuthLockStatus();
		assertEquals(responseWrapper, resultRequestWrapper);
	}

	@Test
	@WithUserDetails("reg-admin")
	public void testAuthLockStatusFailed() throws Exception {
		ResponseWrapper<Object> responseWrapper = new ResponseWrapper<>();
		responseWrapper.setErrors(List.of(new ServiceError(ResidentErrorCode.AUTH_LOCK_STATUS_FAILED.getErrorCode(),
				ResidentErrorCode.AUTH_LOCK_STATUS_FAILED.getErrorMessage())));
		responseWrapper.setResponsetime(null);

		when(availableClaimUtility.getResidentIndvidualIdFromSession()).thenReturn("9876543210");
		when(residentService.getAuthLockStatus(Mockito.any()))
				.thenThrow(new ResidentServiceCheckedException("error", "error"));
		ResponseWrapper<AuthLockOrUnLockRequestDtoV2> resultRequestWrapper = residentController.getAuthLockStatus();
		resultRequestWrapper.setResponsetime(null);
		assertEquals(responseWrapper, resultRequestWrapper);
	}

	@Test
	@WithUserDetails("reg-admin")
	public void testDownloadCardIndividualId() throws Exception {
		ResponseEntity<Object> responseEntity;
		byte[] pdfBytes = "test".getBytes(StandardCharsets.UTF_8);
		InputStreamResource resource = new InputStreamResource(new ByteArrayInputStream(pdfBytes));
		responseEntity = ResponseEntity.ok().contentType(MediaType.parseMediaType("application/pdf"))
				.header("Content-Disposition", "attachment; filename=\"" +
						"abc" + ".pdf\"")
				.body(resource);

		when(residentService.downloadCard(Mockito.anyString())).thenReturn(Tuples.of(pdfBytes, IdType.UIN));
		ResponseEntity<?> resultRequestWrapper = residentController
				.downloadCard("9876543210", 0, LOCALE_EN_US);
		assertEquals(responseEntity.getStatusCode(), resultRequestWrapper.getStatusCode());
	}

	@Test(expected = ResidentServiceException.class)
	@WithUserDetails("reg-admin")
	public void testDownloadCardIndividualIdInvalidInputException() throws Exception {
		ReflectionTestUtils.setField(residentController, "downloadCardEventidId", "id");
		doThrow(new InvalidInputException()).when(validator).validateEventId(any());
		residentController.downloadCard("9876543210", 0, LOCALE_EN_US);
	}

	@Test
	@WithUserDetails("reg-admin")
	public void testCheckAidStatus() throws Exception {
		AidStatusRequestDTO aidStatusRequestDTO = new AidStatusRequestDTO();
		aidStatusRequestDTO.setIndividualId("8251649601");
		aidStatusRequestDTO.setOtp("111111");
		aidStatusRequestDTO.setTransactionId("1234567890");
		RequestWrapper<AidStatusRequestDTO> requestWrapper = new RequestWrapper<>();
		requestWrapper.setRequest(aidStatusRequestDTO);
		requestWrapper.setId("mosip.resident.uin");
		requestWrapper.setVersion("1.0");
		Mockito.when(residentService.getAidStatus(Mockito.any(), Mockito.anyBoolean())).thenReturn(new AidStatusResponseDTO());
		String requestAsString = gson.toJson(requestWrapper);
		this.mockMvc
				.perform(
						post("/aid/status").contentType(MediaType.APPLICATION_JSON).content(requestAsString))
				.andExpect(status().isOk());
	}

	@Test
	@WithUserDetails("reg-admin")
	public void testGetCredentialRequestStatusSuccess() throws Exception {
		residentController.checkEventIdStatus("17", "eng", 0, LOCALE_EN_US);
		this.mockMvc.perform(get("/events/86c2ad43-e2a4-4952-bafc-d97ad1e5e453/?langCode=eng"))
				.andExpect(status().isOk());
	}

	@Test(expected = Exception.class)
	@WithUserDetails("reg-admin")
	public void testCheckEventIdStatusWithException() throws Exception {
		when(residentService.getEventStatus(anyString(), anyString(), anyInt(), anyString())).thenThrow(new ResidentServiceCheckedException());
		residentController.checkEventIdStatus("17", "eng", 0, LOCALE_EN_US);
	}

	@Test
	@WithUserDetails("reg-admin")
	public void testGetUserInfo() throws Exception {
		UserInfoDto user = new UserInfoDto();
		user.setFullName("name");
		ResponseWrapper<UserInfoDto> response = new ResponseWrapper<>();
		response.setResponse(user);
		residentController.userinfo(null, 0, LOCALE_EN_US);
		this.mockMvc.perform(get("/profile"))
				.andExpect(status().isOk());
	}

	@Test(expected = Exception.class)
	@WithUserDetails("reg-admin")
	public void testGetUserInfoWithException() throws Exception {
		Mockito.when(residentService.getUserinfo(Mockito.any(), Mockito.anyString(), Mockito.anyInt(), Mockito.anyString())).thenThrow(new ApisResourceAccessException());
		residentController.userinfo("eng", 0, LOCALE_EN_US);
	}

	@Test
	@WithUserDetails("reg-admin")
	public void testBellClickdttimes() throws Exception {
		BellNotificationDto dto = new BellNotificationDto();
		dto.setLastbellnotifclicktime(LocalDateTime.now());
		ResponseWrapper<BellNotificationDto> response = new ResponseWrapper<>();
		response.setResponse(dto);
		residentController.bellClickdttimes();
	}

	@Test(expected = Exception.class)
	@WithUserDetails("reg-admin")
	public void testBellClickdttimesWithException() throws Exception {
		when(availableClaimUtility.getResidentIdaToken()).thenThrow(new ApisResourceAccessException());
		residentController.bellClickdttimes();
	}

	@Test
	@WithUserDetails("reg-admin")
	public void testNotificationCount() throws Exception {
		UnreadNotificationDto dto = new UnreadNotificationDto();
		dto.setUnreadCount(10L);
		ResponseWrapper<UnreadNotificationDto> response = new ResponseWrapper<>();
		response.setResponse(dto);
		residentController.notificationCount();
	}

	@Test(expected = ResidentServiceCheckedException.class)
	public void testNotificationCountException() throws Exception {
		ReflectionTestUtils.setField(residentController, "serviceEventId", "id");
		Mockito.when(availableClaimUtility.getResidentIdaToken()).thenReturn("1234567890");
		Mockito.when(residentService.getnotificationCount(Mockito.anyString())).thenThrow(ResidentServiceCheckedException.class);
		residentController.notificationCount();
	}

	@Test
	@WithUserDetails("reg-admin")
	public void testBellupdateClickdttimes() throws Exception {
		residentController.bellupdateClickdttimes();
	}

	@Test
	@WithUserDetails("reg-admin")
	public void testGetNotificationsList() throws Exception {
		PageDto<ServiceHistoryResponseDto> dto = new PageDto<ServiceHistoryResponseDto>();
		dto.setData(List.of());
		ResponseWrapper<PageDto<ServiceHistoryResponseDto>> response = new ResponseWrapper<>();
		response.setResponse(dto);
		residentController.getNotificationsList("eng", 0, 10, 0, LOCALE_EN_US);
	}

	@Test(expected = Exception.class)
	@WithUserDetails("reg-admin")
	public void testGetNotificationsListWithException() throws Exception {
		when(availableClaimUtility.getResidentIdaToken()).thenThrow(new ApisResourceAccessException());
		residentController.getNotificationsList("eng", 0, 10, 0, LOCALE_EN_US);
	}

	@Test
	@WithUserDetails("reg-admin")
	public void testDownLoadServiceHistory() throws Exception {
		ReflectionTestUtils.setField(residentController, "maxEventsServiceHistoryPageSize", 10);
		ServiceHistoryResponseDto serviceHistoryResponseDto = new ServiceHistoryResponseDto();
		serviceHistoryResponseDto.setEventId("1234567890");
		serviceHistoryResponseDto.setEventStatus("Success");
		PageDto<ServiceHistoryResponseDto> dto = new PageDto<>();
		dto.setData(List.of(serviceHistoryResponseDto));
		dto.setPageIndex(0);
		dto.setPageSize(10);
		ResponseWrapper<PageDto<ServiceHistoryResponseDto>> response = new ResponseWrapper<>();
		response.setResponse(dto);
		Mockito.when(residentService.getServiceHistory(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(),
				Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.anyInt(),
				Mockito.any())).thenReturn(response);
		byte[] bytes = "abc".getBytes(StandardCharsets.UTF_8);
		Mockito.when(
				residentService.downLoadServiceHistory(Mockito.any(), Mockito.anyString(), Mockito.any(), Mockito.any(),
						Mockito.any(), Mockito.anyString(), Mockito.anyString(), Mockito.anyInt(), Mockito.anyString()))
				.thenReturn(bytes);
		residentController.downLoadServiceHistory(LocalDateTime.now(), LocalDate.now(), LocalDate.now(), SortType.ASC.name(),
				ServiceType.ID_MANAGEMENT_REQUEST.name(), EventStatus.SUCCESS.name(), "", "eng", 0, LOCALE_EN_US);
	}

	@Test(expected = Exception.class)
	@WithUserDetails("reg-admin")
	public void testDownLoadServiceHistoryWithException() throws Exception {
		ReflectionTestUtils.setField(residentController, "maxEventsServiceHistoryPageSize", 10);
		Mockito.when(residentService.getServiceHistory(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(),
				Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.anyInt(),
				Mockito.any())).thenThrow(new ApisResourceAccessException());
		residentController.downLoadServiceHistory(LocalDateTime.now(), LocalDate.now(), LocalDate.now(), SortType.ASC.name(),
				ServiceType.ID_MANAGEMENT_REQUEST.name(), EventStatus.SUCCESS.name(), "", "eng", 0, LOCALE_EN_US);
	}

	@Test(expected = ResidentServiceException.class)
	@WithUserDetails("reg-admin")
	public void testCheckAidStatusWithException() throws Exception {
		ReflectionTestUtils.setField(residentController, "checkStatusId", "id");
		AidStatusRequestDTO aidStatusRequestDTO = new AidStatusRequestDTO();
		aidStatusRequestDTO.setIndividualId("5734728510");
		aidStatusRequestDTO.setOtp("111111");
		aidStatusRequestDTO.setTransactionId("1234567890");
		RequestWrapper<AidStatusRequestDTO> reqDto = new RequestWrapper<>();
		reqDto.setRequest(aidStatusRequestDTO);
		AidStatusResponseDTO response = new AidStatusResponseDTO();
		response.setAidStatus("uin generator");
		Mockito.when(residentService.getAidStatus(Mockito.any(), Mockito.anyBoolean())).thenThrow(new ResidentServiceCheckedException("res-ser", "error"));
		residentController.checkAidStatus(reqDto);
	}
}
