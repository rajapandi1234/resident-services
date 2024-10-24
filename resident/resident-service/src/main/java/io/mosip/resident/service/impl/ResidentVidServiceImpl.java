package io.mosip.resident.service.impl;

import static io.mosip.resident.constant.ResidentConstants.VID_POLICIES;
import static io.mosip.resident.constant.ResidentConstants.VID_POLICY;

import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import io.mosip.resident.util.*;
import jakarta.annotation.PostConstruct;

import io.mosip.resident.exception.IndividualIdNotFoundException;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.mosip.idrepository.core.dto.VidPolicy;
import io.mosip.kernel.core.exception.ServiceError;
import io.mosip.kernel.core.logger.spi.Logger;
import io.mosip.kernel.core.util.DateUtils;
import io.mosip.kernel.core.util.JsonUtils;
import io.mosip.kernel.core.util.exception.JsonProcessingException;
import io.mosip.resident.config.LoggerConfiguration;
import io.mosip.resident.constant.ApiName;
import io.mosip.resident.constant.EventStatusFailure;
import io.mosip.resident.constant.EventStatusSuccess;
import io.mosip.resident.constant.IdType;
import io.mosip.resident.constant.LoggerFileConstant;
import io.mosip.resident.constant.NotificationTemplateCode;
import io.mosip.resident.constant.RequestType;
import io.mosip.resident.constant.ResidentConstants;
import io.mosip.resident.constant.ResidentErrorCode;
import io.mosip.resident.constant.TemplateType;
import io.mosip.resident.constant.TemplateVariablesConstants;
import io.mosip.resident.dto.BaseVidRequestDto;
import io.mosip.resident.dto.BaseVidRevokeRequestDTO;
import io.mosip.resident.dto.GenerateVidResponseDto;
import io.mosip.resident.dto.IdentityDTO;
import io.mosip.resident.dto.NotificationRequestDto;
import io.mosip.resident.dto.NotificationRequestDtoV2;
import io.mosip.resident.dto.NotificationResponseDTO;
import io.mosip.resident.dto.ObjectWithTransactionID;
import io.mosip.resident.dto.RequestWrapper;
import io.mosip.resident.dto.ResponseWrapper;
import io.mosip.resident.dto.RevokeVidResponseDto;
import io.mosip.resident.dto.VidGeneratorRequestDto;
import io.mosip.resident.dto.VidGeneratorResponseDto;
import io.mosip.resident.dto.VidRequestDto;
import io.mosip.resident.dto.VidRequestDtoV2;
import io.mosip.resident.dto.VidResponseDto;
import io.mosip.resident.dto.VidRevokeRequestDTO;
import io.mosip.resident.dto.VidRevokeRequestDTOV2;
import io.mosip.resident.dto.VidRevokeResponseDTO;
import io.mosip.resident.entity.ResidentTransactionEntity;
import io.mosip.resident.exception.ApisResourceAccessException;
import io.mosip.resident.exception.OtpValidationFailedException;
import io.mosip.resident.exception.ResidentServiceCheckedException;
import io.mosip.resident.exception.ResidentServiceException;
import io.mosip.resident.exception.VidAlreadyPresentException;
import io.mosip.resident.exception.VidCreationException;
import io.mosip.resident.exception.VidRevocationException;
import io.mosip.resident.repository.ResidentTransactionRepository;
import io.mosip.resident.service.IdAuthService;
import io.mosip.resident.service.NotificationService;
import io.mosip.resident.service.ResidentVidService;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

@Component
public class ResidentVidServiceImpl implements ResidentVidService {

	private static final Logger logger = LoggerConfiguration.logConfig(ResidentVidServiceImpl.class);

	private static final String VID_ALREADY_EXISTS_ERROR_CODE = "IDR-VID-003";

    @Value("${resident.vid.id}")
	private String id;
	
	@Value("${resident.vid.id.generate}")
	private String generateId;

	@Value("${resident.vid.version}")
	private String version;

	@Value("${resident.vid.version.new}")
	private String newVersion;

	@Value("${vid.create.id}")
	private String vidCreateId;

	@Value("${vid.revoke.id}")
	private String vidRevokeId;

	@Value("${resident.revokevid.id}")
	private String revokeVidId;
	
	@Value("${mosip.resident.revokevid.id}")
	private String revokeVidIdNew;

	@Value("${mosip.resident.vid-policy-url}")
	private String vidPolicyUrl;
	
	@Value("${mosip.resident.create.vid.version}")
	private String residentCreateVidVersion;

	@Autowired
	private ObjectMapper mapper;

	@Autowired
	private Environment env;

	@Autowired
	private UinVidValidator uinVidValidator;

	@Autowired
	private ResidentServiceRestClient residentServiceRestClient;

	@Autowired
	private NotificationService notificationService;

	@Autowired
	private IdAuthService idAuthService;

	@Autowired
	private IdentityServiceImpl identityServiceImpl;
	
	@Autowired
	private Utility utility;
	
	@Autowired
	private ResidentTransactionRepository residentTransactionRepository;

	private String vidPolicy;

	@Autowired
	private Utilities utilities;

	@Autowired
	private AvailableClaimUtility availableClaimUtility;

	@Autowired
	private MaskDataUtility maskDataUtility;

	@Autowired
	private IdentityUtil identityUtil;

	@Autowired
	private PerpetualVidUtility perpetualVidUtility;

	@Autowired
	private UinForIndividualId uinForIndividualId;

	@Override
	public ResponseWrapper<VidResponseDto> generateVid(BaseVidRequestDto requestDto,
			String individualId) throws OtpValidationFailedException, ResidentServiceCheckedException {
		return generateVidV2(requestDto, individualId).getT1();
	}
	
	@Override
	public Tuple2<ResponseWrapper<VidResponseDto>, String> generateVidV2(BaseVidRequestDto requestDto,
			String individualId) throws OtpValidationFailedException, ResidentServiceCheckedException {
		boolean isV2Request = requestDto instanceof VidRequestDtoV2;
		ResponseWrapper<VidResponseDto> responseDto = new ResponseWrapper<>();
		NotificationRequestDto notificationRequestDto = isV2Request? new NotificationRequestDtoV2() : new NotificationRequestDto();
		notificationRequestDto.setId(individualId);

		if (requestDto instanceof VidRequestDto) {
			VidRequestDto vidRequestDto = (VidRequestDto) requestDto;
			if (Objects.nonNull(vidRequestDto.getOtp())) {
				try {
					boolean isAuthenticated = idAuthService.validateOtp(vidRequestDto.getTransactionID(),
							individualId, vidRequestDto.getOtp());
					if (!isAuthenticated)
						throw new OtpValidationFailedException();
	
				} catch (OtpValidationFailedException e) {
					logger.error(AuditEnum.OTP_VALIDATION_FAILED.getDescription(), requestDto.getTransactionID());
					notificationRequestDto.setTemplateTypeCode(NotificationTemplateCode.RS_VIN_GEN_FAILURE);
					notificationService.sendNotification(notificationRequestDto, null);
					logger.error(AuditEnum.SEND_NOTIFICATION_FAILURE.getDescription(), requestDto.getTransactionID());
					throw e;
				}
			}
		}
		IdentityDTO identityDTO = identityUtil.getIdentity(individualId);
		String email = identityDTO.getEmail();
		String phone = identityDTO.getPhone();
		String eventId = ResidentConstants.NOT_AVAILABLE;
		ResidentTransactionEntity residentTransactionEntity=null;
		try {
			if(Utility.isSecureSession()){
				residentTransactionEntity = createResidentTransactionEntity(requestDto, identityDTO.getUIN());
				validateVidFromSession(individualId, requestDto.getVidType(), identityDTO.getUIN());
				if (residentTransactionEntity != null) {
	    			eventId = residentTransactionEntity.getEventId();
	    		}
			}
			String uin = identityDTO.getUIN();
			// generate vid
			VidGeneratorResponseDto vidResponse = vidGenerator(requestDto, uin);
			logger.debug(AuditEnum.VID_GENERATED.getDescription(), requestDto.getTransactionID());
			// send notification
			Map<String, Object> additionalAttributes = new HashMap<>();
			additionalAttributes.put(IdType.VID.name(), vidResponse.getVID());
			notificationRequestDto.setAdditionalAttributes(additionalAttributes);

			NotificationResponseDTO notificationResponseDTO;
			if(isV2Request) {
				VidRequestDtoV2 vidRequestDtoV2 = (VidRequestDtoV2) requestDto;
				NotificationRequestDtoV2 notificationRequestDtoV2=(NotificationRequestDtoV2) notificationRequestDto;
				notificationRequestDtoV2.setTemplateType(TemplateType.SUCCESS);
				notificationRequestDtoV2.setRequestType(RequestType.GENERATE_VID);
				notificationRequestDtoV2.setEventId(eventId);

				notificationResponseDTO=notificationService
						.sendNotification(notificationRequestDto, vidRequestDtoV2.getChannels(), email, phone, identityDTO);
			} else {
				notificationRequestDto.setTemplateTypeCode(NotificationTemplateCode.RS_VIN_GEN_SUCCESS);
				notificationResponseDTO = notificationService.sendNotification(notificationRequestDto, identityDTO);
			}
			logger.debug(AuditEnum.SEND_NOTIFICATION_SUCCESS.getDescription(), requestDto.getTransactionID());
			// create response dto
			VidResponseDto vidResponseDto;
			if(notificationResponseDTO.getMaskedEmail() != null || notificationResponseDTO.getMaskedPhone() != null) {
				GenerateVidResponseDto generateVidResponseDto = new GenerateVidResponseDto();
				vidResponseDto = generateVidResponseDto;
				generateVidResponseDto.setMaskedEmail(notificationResponseDTO.getMaskedEmail());
				generateVidResponseDto.setMaskedPhone(notificationResponseDTO.getMaskedPhone());
				generateVidResponseDto.setStatus(ResidentConstants.SUCCESS);
			} else {
				vidResponseDto = new VidResponseDto();
			}
			vidResponseDto.setVid(vidResponse.getVID());
			vidResponseDto.setMessage(notificationResponseDTO.getMessage());
			responseDto.setResponse(vidResponseDto);

			if(Utility.isSecureSession()) {
				residentTransactionEntity.setRefId(maskDataUtility.convertToMaskData(vidResponseDto.getVid()));
				residentTransactionEntity.setStatusCode(EventStatusSuccess.VID_GENERATED.name());
				residentTransactionEntity.setStatusComment(EventStatusSuccess.VID_GENERATED.name());
				residentTransactionEntity.setRequestSummary(String.format("%s - %s", RequestType.GENERATE_VID.name(), ResidentConstants.SUCCESS));
			}
			
		} catch (JsonProcessingException e) {
			logger.error(AuditEnum.VID_JSON_PARSING_EXCEPTION.getDescription(), requestDto.getTransactionID());
			notifyVidCreationFailureAndThrowException(requestDto, isV2Request, notificationRequestDto, eventId, residentTransactionEntity, e, identityDTO);
		} catch (IOException | ApisResourceAccessException | VidCreationException e) {
			logger.error(AuditEnum.VID_GENERATION_FAILURE.getDescription(), requestDto.getTransactionID());
			notifyVidCreationFailureAndThrowException(requestDto, isV2Request, notificationRequestDto, eventId, residentTransactionEntity, e, identityDTO);
		} catch (VidAlreadyPresentException e) {
			logger.error(AuditEnum.VID_ALREADY_EXISTS.getDescription(), requestDto.getTransactionID());
			notifyVidCreationFailureAndThrowException(requestDto, isV2Request, notificationRequestDto, eventId, residentTransactionEntity, e, identityDTO);
		} catch (ResidentServiceCheckedException e) {
			logger.error(AuditEnum.VID_ALREADY_EXISTS.getDescription(), requestDto.getTransactionID());
			notifyVidCreationFailureAndThrowException(requestDto, isV2Request, notificationRequestDto, eventId, residentTransactionEntity, e, identityDTO);
		} finally {
			if (Utility.isSecureSession() && residentTransactionEntity != null) {
				//if the status code or request summary will come as null, it will set it as failed.
				if(residentTransactionEntity.getStatusCode() == null || residentTransactionEntity.getRequestSummary() == null) {
					residentTransactionEntity.setStatusCode(EventStatusFailure.FAILED.name());
					residentTransactionEntity.setStatusComment(ResidentConstants.FAILED);
					residentTransactionEntity
							.setRequestSummary(String.format("%s - %s", RequestType.GENERATE_VID.name(), ResidentConstants.FAILED));
				}
				residentTransactionRepository.save(residentTransactionEntity);
			}
		}
		if (isV2Request)
		{
			responseDto.setId(generateId);
			responseDto.setVersion(newVersion);
		}
		else
		{
			responseDto.setId(id);
			responseDto.setVersion(version);
		}
		responseDto.setResponsetime(DateUtils.formatToISOString(DateUtils.getUTCCurrentDateTime()));
		if(eventId == null) {
			eventId = ResidentConstants.NOT_AVAILABLE;
		}
		return Tuples.of(responseDto, eventId);
	}

	private void validateVidFromSession(String individualId, String vidType, String uin) {
		try {
			IdType idType = uinVidValidator.getIndividualIdType(individualId);
			Tuple2<Integer, String> numberOfPerpetualVidTuple = getNumberOfPerpetualVidFromUin(
					uin);
			/**
			 * Check If id type is VID.
			 */
			if (idType.equals(IdType.VID)) {
				/**
				 * Checks if VID type is Perpetual VID.
				 */
				if(vidType!=null && vidType.equalsIgnoreCase(ResidentConstants.PERPETUAL)){
					int numberOfPerpetualVid = numberOfPerpetualVidTuple.getT1();
					VidPolicy vidPolicy = getVidPolicyAsVidPolicyDto();
					/**
					 * Checks if VID Policy allowed instance is greater than number of existing Perpetual VID.
					 */
					if(vidPolicy.getAllowedInstances() >= numberOfPerpetualVid
							/**
							 * Checks if VID Policy auto restore allowed is true.
							 */
							&& vidPolicy.getAutoRestoreAllowed()
							/**
							 * Checks if VID Policy restore on action is not ACTIVE.
							 */
							&& !vidPolicy.getRestoreOnAction().
									equalsIgnoreCase(env.getProperty(ResidentConstants.VID_ACTIVE_STATUS))
							/**
							 * Checks if first Perpetual Vid is equal to logged in vid.
							 */
					&& numberOfPerpetualVidTuple.getT2().equals(individualId)){
						throw new ResidentServiceException(ResidentErrorCode.VID_CREATION_FAILED_WITH_REVOCATION,
									ResidentErrorCode.VID_CREATION_FAILED_WITH_REVOCATION.getErrorMessage());
					}
				}
			}
		}catch (ApisResourceAccessException | ResidentServiceCheckedException |
				com.fasterxml.jackson.core.JsonProcessingException | ResidentServiceException e) {
			logger.error(AuditEnum.VID_GENERATION_FAILURE.getDescription() + e);
			throw new ResidentServiceException(ResidentErrorCode.VID_CREATION_FAILED_WITH_REVOCATION.getErrorCode(),
					ResidentErrorCode.VID_CREATION_FAILED_WITH_REVOCATION.getErrorMessage(), e);
		}
	}

	private VidPolicy getVidPolicyAsVidPolicyDto() throws ResidentServiceCheckedException, com.fasterxml.jackson.core.JsonProcessingException {
		String vidPolicy = getVidPolicy();
		VidPolicy vidPolicyDto = new VidPolicy();
		Map<Object, Object> vidPolicyMap = mapper.readValue(vidPolicy, Map.class);
		Object vidPolicyMapValue = vidPolicyMap.get(VID_POLICIES);
		List<Map<String, String>> vidList = (List<Map<String, String>>)vidPolicyMapValue;
		if(vidList!=null){
			for(Map<String, String> vid:vidList){
				if(vid.get(TemplateVariablesConstants.VID_TYPE).equalsIgnoreCase(ResidentConstants.PERPETUAL)){
					vidPolicyDto = mapper.convertValue(vid.get(VID_POLICY), VidPolicy.class);
				}
			}
		}
		return vidPolicyDto;
	}

	private Tuple2<Integer, String> getNumberOfPerpetualVidFromUin(String uin) throws ResidentServiceCheckedException, ApisResourceAccessException {
		ResponseWrapper<List<Map<String,?>>> vids = perpetualVidUtility.retrieveVids(ResidentConstants.UTC_TIMEZONE_OFFSET, null, uin);
		List<Map<String, ?>> vidList = vids.getResponse().stream().filter(map -> map.containsKey(TemplateVariablesConstants.VID_TYPE)
		&& String.valueOf(map.get(TemplateVariablesConstants.VID_TYPE)).equalsIgnoreCase((ResidentConstants.PERPETUAL)))
				.collect(Collectors.toList());
		if(vidList.isEmpty()) {
			return Tuples.of(0, "");
		}
		return Tuples.of(vidList.size(), vidList.get(0).get(TemplateVariablesConstants.VID).toString());
	}

	private ResidentTransactionEntity createResidentTransactionEntity(BaseVidRequestDto requestDto, String uin) throws ApisResourceAccessException, ResidentServiceCheckedException {
		ResidentTransactionEntity residentTransactionEntity=utility.createEntity(RequestType.GENERATE_VID);
		residentTransactionEntity.setEventId(utility.createEventId());
		residentTransactionEntity.setIndividualId(availableClaimUtility.getResidentIndvidualIdFromSession());
		residentTransactionEntity.setTokenId(availableClaimUtility.getIDAToken(uin));
		residentTransactionEntity.setAuthTypeCode(identityServiceImpl.getResidentAuthenticationMode());
		residentTransactionEntity.setRefIdType(requestDto.getVidType().toUpperCase());
		return residentTransactionEntity;
	}

	private VidGeneratorResponseDto vidGenerator(BaseVidRequestDto requestDto, String uin)
			throws JsonProcessingException, IOException, ApisResourceAccessException {
		VidGeneratorRequestDto vidRequestDto = new VidGeneratorRequestDto();
		RequestWrapper<VidGeneratorRequestDto> request = new RequestWrapper<>();
		ResponseWrapper<VidGeneratorResponseDto> response = null;

		vidRequestDto.setUIN(uin);
		vidRequestDto.setVidType(requestDto.getVidType());
		request.setId(vidCreateId);
		request.setVersion(residentCreateVidVersion);
		request.setRequest(vidRequestDto);
		request.setRequesttime(DateUtils.formatToISOString(DateUtils.getUTCCurrentDateTime()));

		logger.debug(LoggerFileConstant.SESSIONID.toString(), LoggerFileConstant.REGISTRATIONID.toString(),
				IdType.UIN.name(),
				"ResidentVidServiceImpl::vidGenerator():: post CREATEVID service call started with request data : "
						+ JsonUtils.javaObjectToJsonString(request));

		try {
			response = (ResponseWrapper) residentServiceRestClient
					.postApi(env.getProperty(ApiName.IDAUTHCREATEVID.name()),
							MediaType.APPLICATION_JSON, request, ResponseWrapper.class);
		} catch (Exception e) {
			logger.error(LoggerFileConstant.SESSIONID.toString(), LoggerFileConstant.REGISTRATIONID.toString(),
					IdType.UIN.name(), ResidentErrorCode.API_RESOURCE_UNAVAILABLE.getErrorCode() + e.getMessage()
							+ ExceptionUtils.getStackTrace(e));
			throw new ApisResourceAccessException("Unable to create vid : " + e.getMessage());
		}

		logger.debug(LoggerFileConstant.SESSIONID.toString(), LoggerFileConstant.REGISTRATIONID.toString(),
				IdType.UIN.name(),
				"ResidentVidServiceImpl::vidGenerator():: create Vid response :: "
						+ JsonUtils.javaObjectToJsonString(response));

		if (response.getErrors() != null && !response.getErrors().isEmpty()) {
			List<ServiceError> list = response.getErrors().stream()
					.filter(err -> err.getErrorCode().equalsIgnoreCase(VID_ALREADY_EXISTS_ERROR_CODE))
					.collect(Collectors.toList());
			throw (list.size() == 1)
					? new VidAlreadyPresentException(ResidentErrorCode.VID_ALREADY_PRESENT.getErrorCode(),
							ResidentErrorCode.VID_ALREADY_PRESENT.getErrorMessage())
					: new VidCreationException(response.getErrors().get(0).getMessage());

		}

		VidGeneratorResponseDto vidResponse = mapper.readValue(mapper.writeValueAsString(response.getResponse()),
				VidGeneratorResponseDto.class);

		return vidResponse;
	}

	@Override
	public ResponseWrapper<VidRevokeResponseDTO> revokeVid(BaseVidRevokeRequestDTO requestDto, String vid, String indivudalId)
			throws OtpValidationFailedException, ResidentServiceCheckedException, ApisResourceAccessException, IOException {
		return revokeVidV2(requestDto, vid, indivudalId).getT1();
	}

	@Override
	public Tuple2<ResponseWrapper<VidRevokeResponseDTO>, String> revokeVidV2(BaseVidRevokeRequestDTO requestDto, String vid, String indivudalId)
			throws OtpValidationFailedException, ResidentServiceCheckedException, ApisResourceAccessException, IOException {
		boolean isV2Request = requestDto instanceof VidRevokeRequestDTOV2;
		ResponseWrapper<VidRevokeResponseDTO> responseDto = new ResponseWrapper<>();
		NotificationRequestDto notificationRequestDto = isV2Request? new NotificationRequestDtoV2() : new NotificationRequestDto();
		
		if(requestDto instanceof VidRevokeRequestDTO) {
			VidRevokeRequestDTO vidRevokeRequestDTO = (VidRevokeRequestDTO) requestDto;
			if (Objects.nonNull(vidRevokeRequestDTO.getOtp())) {
				try {
					boolean isAuthenticated = idAuthService.validateOtp(requestDto.getTransactionID(),
							vidRevokeRequestDTO.getIndividualId(), vidRevokeRequestDTO.getOtp());
	
					if (!isAuthenticated)
						throw new OtpValidationFailedException();
					logger.debug(AuditEnum.VALIDATE_OTP_SUCCESS.getDescription(), requestDto.getTransactionID());
				} catch (OtpValidationFailedException e) {
					logger.error(AuditEnum.OTP_VALIDATION_FAILED.getDescription(), requestDto.getTransactionID());
					notificationRequestDto.setId(vidRevokeRequestDTO.getIndividualId());
					notificationRequestDto.setTemplateTypeCode(NotificationTemplateCode.RS_VIN_REV_FAILURE);
					notificationService.sendNotification(notificationRequestDto, null);
					logger.error(AuditEnum.SEND_NOTIFICATION_FAILURE.getDescription(), requestDto.getTransactionID());
					throw e;
				}
			}
		}
		String eventId = ResidentConstants.NOT_AVAILABLE;
		ResidentTransactionEntity residentTransactionEntity = null;
		IdentityDTO identityDTO = new IdentityDTO();
		String uin="";

		if(isV2Request) {
			try {
				identityDTO = identityUtil.getIdentity(indivudalId);
			} catch (Exception e){
				throw new ResidentServiceCheckedException(ResidentErrorCode.VID_NOT_BELONG_TO_USER,
						Map.of(ResidentConstants.EVENT_ID, eventId));
			}
			uin = identityDTO.getUIN();
			if(Utility.isSecureSession()) {
				residentTransactionEntity = createResidentTransEntity(vid, uin);
				if (residentTransactionEntity != null) {
					eventId = residentTransactionEntity.getEventId();
				}
			}
			notificationRequestDto.setId(uin);
			String uinForVid = null;
			try {
				uinForVid = utilities.getUinByVid(vid);
			}catch (IndividualIdNotFoundException e){
				throw new ResidentServiceCheckedException(ResidentErrorCode.INVALID_INDIVIDUAL_ID.getErrorCode(),
						ResidentErrorCode.INVALID_INDIVIDUAL_ID.getErrorMessage());
			}
			if(uinForVid != null && !uin.equalsIgnoreCase(uinForVid)) {
				if(Utility.isSecureSession()) {
					residentTransactionEntity.setStatusCode(EventStatusFailure.FAILED.name());
					residentTransactionEntity.setRequestSummary(String.format("%s - %s", RequestType.REVOKE_VID.name(), ResidentConstants.FAILED));
					residentTransactionRepository.save(residentTransactionEntity);
					throw new ResidentServiceCheckedException(ResidentErrorCode.VID_NOT_BELONG_TO_USER,
							Map.of(ResidentConstants.EVENT_ID, eventId));
				}
			}
		} else {
			String uinForVid;
			try {
				uinForVid = uinForIndividualId.getUinForIndividualId(vid);
			}catch (Exception exception){
				logger.error(ResidentErrorCode.API_RESOURCE_ACCESS_EXCEPTION.getErrorCode()+exception);
				throw new ApisResourceAccessException(ResidentErrorCode.API_RESOURCE_ACCESS_EXCEPTION.getErrorCode(),
						ResidentErrorCode.API_RESOURCE_ACCESS_EXCEPTION.getErrorMessage(), exception);
			}
			identityDTO = identityUtil.getIdentity(indivudalId);
			uin = identityDTO.getUIN();
			notificationRequestDto.setId(uin);
			if(uinForVid == null || !uinForVid.equalsIgnoreCase(uin)) {
				throw new ResidentServiceCheckedException(ResidentErrorCode.VID_NOT_BELONG_TO_INDIVITUAL);
			}
		}

		try {
			// revoke vid
			VidGeneratorResponseDto vidResponse = vidDeactivator(requestDto, uin, vid);
			logger.debug(AuditEnum.DEACTIVATED_VID.getDescription(), requestDto.getTransactionID());
			// send notification
			Map<String, Object> additionalAttributes = new HashMap<>();
			additionalAttributes.put(IdType.VID.name(), vid);
			notificationRequestDto.setAdditionalAttributes(additionalAttributes);

			NotificationResponseDTO notificationResponseDTO;
			if(isV2Request) {
				NotificationRequestDtoV2 notificationRequestDtoV2=(NotificationRequestDtoV2) notificationRequestDto;
				notificationRequestDtoV2.setTemplateType(TemplateType.SUCCESS);
				notificationRequestDtoV2.setRequestType(RequestType.REVOKE_VID);
				notificationRequestDtoV2.setEventId(eventId);

				notificationResponseDTO=notificationService.sendNotification(notificationRequestDto, identityDTO);
			} else {
				notificationRequestDto.setTemplateTypeCode(NotificationTemplateCode.RS_VIN_REV_SUCCESS);
				notificationResponseDTO = notificationService.sendNotification(notificationRequestDto, identityDTO);
			}
			logger.debug(AuditEnum.SEND_NOTIFICATION_SUCCESS.getDescription(), requestDto.getTransactionID());
			// create response dto
			VidRevokeResponseDTO vidRevokeResponseDto;
			if(isV2Request) {
				RevokeVidResponseDto revokeVidResponseDto = new RevokeVidResponseDto();
				vidRevokeResponseDto = revokeVidResponseDto;
				revokeVidResponseDto.setStatus(ResidentConstants.SUCCESS);
			} else {
				vidRevokeResponseDto = new VidRevokeResponseDTO();
			}
			vidRevokeResponseDto.setMessage(notificationResponseDTO.getMessage());
			responseDto.setResponse(vidRevokeResponseDto);
			if(Utility.isSecureSession()) {
				residentTransactionEntity.setStatusCode(EventStatusSuccess.VID_REVOKED.name());
				residentTransactionEntity.setStatusComment(EventStatusSuccess.VID_REVOKED.name());
				residentTransactionEntity.setRequestSummary(String.format("%s - %s", RequestType.REVOKE_VID.name(), ResidentConstants.SUCCESS));
			}
		} catch (JsonProcessingException e) {
			logger.error(AuditEnum.VID_JSON_PARSING_EXCEPTION.getDescription(), requestDto.getTransactionID());
			notifyVidRevokeFailureAndThrowException(requestDto, isV2Request, notificationRequestDto, eventId, residentTransactionEntity, e, identityDTO);
		} catch (IOException | ApisResourceAccessException | VidRevocationException e) {
			logger.error(AuditEnum.VID_REVOKE_EXCEPTION.getDescription(), requestDto.getTransactionID());
			notifyVidRevokeFailureAndThrowException(requestDto, isV2Request, notificationRequestDto, eventId, residentTransactionEntity, e, identityDTO);
		} catch (ResidentServiceCheckedException e) {
			logger.error(AuditEnum.VID_REVOKE_EXCEPTION.getDescription(), requestDto.getTransactionID());
			notifyVidRevokeFailureAndThrowException(requestDto, isV2Request, notificationRequestDto, eventId, residentTransactionEntity, e, identityDTO);
		} finally {
			if (Utility.isSecureSession() && residentTransactionEntity != null) {
				//if the status code or request summary will come as null, it will set it as failed.
				if(residentTransactionEntity.getStatusCode() == null || residentTransactionEntity.getRequestSummary() == null) {
					residentTransactionEntity.setStatusCode(EventStatusFailure.FAILED.name());
					residentTransactionEntity.setStatusComment(ResidentConstants.FAILED);
					residentTransactionEntity
							.setRequestSummary(String.format("%s - %s", RequestType.REVOKE_VID.name(), ResidentConstants.FAILED));
				}
				residentTransactionRepository.save(residentTransactionEntity);
			}
		}

		if (isV2Request) {
			responseDto.setId(revokeVidIdNew);
			responseDto.setVersion(newVersion);
		}
		else
		{
			responseDto.setId(revokeVidId);
			responseDto.setVersion(version);
		}
		responseDto.setResponsetime(DateUtils.formatToISOString(DateUtils.getUTCCurrentDateTime()));

		if(eventId == null) {
			eventId = ResidentConstants.NOT_AVAILABLE;
		}
		return Tuples.of(responseDto, eventId);
	}
	
	private <E extends Exception> void notifyVidCreationFailureAndThrowException(BaseVidRequestDto requestDto, boolean isV2Request,
			NotificationRequestDto notificationRequestDto, String eventId,
			ResidentTransactionEntity residentTransactionEntity, E e, Map identityDTO)
			throws ResidentServiceCheckedException, VidAlreadyPresentException {
		if(e instanceof VidAlreadyPresentException) {
			notifyFailureAndThrowException(requestDto, isV2Request, notificationRequestDto, eventId,
					residentTransactionEntity, e, RequestType.GENERATE_VID, NotificationTemplateCode.RS_VIN_GEN_FAILURE,
					"Request to generate VID", this::createVidAlreadyPresentException, VidAlreadyPresentException.class, identityDTO);
		} else{
			notifyFailureAndThrowException(requestDto, isV2Request, notificationRequestDto, eventId,
					residentTransactionEntity, e, RequestType.GENERATE_VID, NotificationTemplateCode.RS_VIN_GEN_FAILURE,
					"Request to generate VID", this::createVidCreationException, VidCreationException.class, identityDTO);
		}
	}

	private VidCreationException createVidCreationException(String eventId, Throwable rootCause) {
		return eventId == null ? new VidCreationException(rootCause.getMessage(), rootCause): new VidCreationException(rootCause.getMessage(), rootCause, Map.of(ResidentConstants.EVENT_ID, eventId));
	}


	private <E extends Exception> void notifyVidRevokeFailureAndThrowException(BaseVidRevokeRequestDTO requestDto, boolean isV2Request,
			NotificationRequestDto notificationRequestDto, String eventId,
			ResidentTransactionEntity residentTransactionEntity, E e, Map identity)
			throws ResidentServiceCheckedException, VidRevocationException {
		notifyFailureAndThrowException(requestDto, isV2Request, notificationRequestDto, eventId,
				residentTransactionEntity, e, RequestType.REVOKE_VID, NotificationTemplateCode.RS_VIN_REV_FAILURE,
				"Request to revoke VID", this::createVidRevocationException, VidRevocationException.class, identity);
	}

	private <TE extends Throwable> void notifyFailureAndThrowException(ObjectWithTransactionID requestDto, boolean isV2Request,
			NotificationRequestDto notificationRequestDto, String eventId,
			ResidentTransactionEntity residentTransactionEntity, Throwable e, RequestType requestType, NotificationTemplateCode notificationTemplate, 
			String auditEventName, BiFunction<String, Throwable, TE> targetExceptionCreator, Class<TE> targetExceptionClass, Map identity) throws ResidentServiceCheckedException, TE {
		notifyFailure(requestDto, isV2Request, notificationRequestDto, eventId, residentTransactionEntity, e, requestType,
				notificationTemplate, auditEventName, targetExceptionCreator, identity);
		throwException(eventId, e, targetExceptionCreator, targetExceptionClass);
	}

	private <TE extends Throwable> void notifyFailure(ObjectWithTransactionID requestDto, boolean isV2Request,
			NotificationRequestDto notificationRequestDto, String eventId,
			ResidentTransactionEntity residentTransactionEntity, Throwable e, RequestType requestType,
			NotificationTemplateCode notificationTemplate, String auditEventName,
			BiFunction<String, Throwable, TE> targetExceptionCreator, Map identity) throws ResidentServiceCheckedException, TE {
		if(isV2Request) {
			NotificationRequestDtoV2 notificationRequestDtoV2=(NotificationRequestDtoV2) notificationRequestDto;
			notificationRequestDtoV2.setTemplateType(TemplateType.FAILURE);
			notificationRequestDtoV2.setRequestType(requestType);
			notificationRequestDtoV2.setEventId(eventId);

			notificationService.sendNotification(notificationRequestDto, identity);
		} else {
			notificationRequestDto.setTemplateTypeCode(notificationTemplate);
			notificationService.sendNotification(notificationRequestDto, identity);
		}
		logger.error(AuditEnum.SEND_NOTIFICATION_FAILURE.getDescription(), requestDto.getTransactionID());
		if(Utility.isSecureSession()) {
			residentTransactionEntity.setStatusCode(EventStatusFailure.FAILED.name());
			residentTransactionEntity.setStatusComment(ResidentConstants.FAILED);
			residentTransactionEntity.setRequestSummary(String.format("%s - %s", requestType.name(), ResidentConstants.FAILED));
			throw targetExceptionCreator.apply(eventId, e);
		}
	}

	private <TE extends Throwable> void throwException(String eventId, Throwable e,
			BiFunction<String, Throwable, TE> targetExceptionCreator, Class<TE> targetExceptionClass)
			throws TE, ResidentServiceCheckedException {
		if(Utility.isSecureSession()) {
			throw targetExceptionCreator.apply(eventId, e);
		} else {
			if(targetExceptionClass.isInstance(e)) {
				throw targetExceptionClass.cast(e);
			} else if(e instanceof ResidentServiceCheckedException) {
				throw (ResidentServiceCheckedException) e;
			} else {
				throw targetExceptionCreator.apply(null, e);
			}
		}
	}

	private VidRevocationException createVidRevocationException(String eventId, Throwable rootCause) {
		return eventId == null ? new VidRevocationException(rootCause.getMessage(), rootCause): new VidRevocationException(rootCause.getMessage(), rootCause, Map.of(ResidentConstants.EVENT_ID, eventId));
	}
	
	private VidAlreadyPresentException createVidAlreadyPresentException(String eventId, Throwable rootCause) {
		return eventId == null ? new VidAlreadyPresentException(rootCause.getMessage(), rootCause): new VidAlreadyPresentException(rootCause.getMessage(), rootCause, Map.of(ResidentConstants.EVENT_ID, eventId));
	}

	private ResidentTransactionEntity createResidentTransEntity(String vid, String uin) throws ApisResourceAccessException, ResidentServiceCheckedException {
		ResidentTransactionEntity residentTransactionEntity=utility.createEntity(RequestType.REVOKE_VID);
		residentTransactionEntity.setEventId(utility.createEventId());
		residentTransactionEntity.setRefId(maskDataUtility.convertToMaskData(vid));
		residentTransactionEntity.setIndividualId(availableClaimUtility.getResidentIndvidualIdFromSession());
		try {
			residentTransactionEntity.setRefIdType(getVidTypeFromVid(vid, uin));
		} catch (Exception exception){
			residentTransactionEntity.setRefIdType("");
		}
		residentTransactionEntity.setTokenId(availableClaimUtility.getIDAToken(uin));
		residentTransactionEntity.setAuthTypeCode(identityServiceImpl.getResidentAuthenticationMode());
		return residentTransactionEntity;
	}

	private String getVidTypeFromVid(String vid, String uin) throws ResidentServiceCheckedException, ApisResourceAccessException {
		ResponseWrapper<List<Map<String,?>>> vids = perpetualVidUtility.retrieveVids(ResidentConstants.UTC_TIMEZONE_OFFSET, null, uin);
		return vids.getResponse().stream()
				.filter(map -> ((String)map.get(TemplateVariablesConstants.VID)).equals(vid))
				.map(map -> (String)map.get(TemplateVariablesConstants.VID_TYPE))
				.findAny()
				.orElse(null);
	}

	private VidGeneratorResponseDto vidDeactivator(BaseVidRevokeRequestDTO requestDto, String uin, String vid)
			throws JsonProcessingException, IOException, ApisResourceAccessException, ResidentServiceCheckedException {
		VidGeneratorRequestDto vidRequestDto = new VidGeneratorRequestDto();
		RequestWrapper<VidGeneratorRequestDto> request = new RequestWrapper<>();
		ResponseWrapper<?> response = null;

		vidRequestDto.setUIN(uin);
		vidRequestDto.setVidStatus(requestDto.getVidStatus());
		request.setId(vidRevokeId);
		request.setVersion(version);
		request.setRequest(vidRequestDto);
		request.setRequesttime(DateUtils.formatToISOString(DateUtils.getUTCCurrentDateTime()));
		String apiUrl=env.getProperty(ApiName.IDAUTHREVOKEVID.name()) + "/" + vid;
		
		logger.debug(LoggerFileConstant.SESSIONID.toString(), LoggerFileConstant.REGISTRATIONID.toString(),
				"",
				"ResidentVidServiceImpl::vidDeactivator():: post REVOKEVID service call started with request data : "
						+ JsonUtils.javaObjectToJsonString(request));

		try {
			response = (ResponseWrapper) residentServiceRestClient.patchApi(apiUrl, MediaType.APPLICATION_JSON, request,
					ResponseWrapper.class);
		} catch (Exception e) {
			logger.error(LoggerFileConstant.SESSIONID.toString(), LoggerFileConstant.REGISTRATIONID.toString(),
					"", ResidentErrorCode.API_RESOURCE_UNAVAILABLE.getErrorCode()
							+ e.getMessage() + ExceptionUtils.getStackTrace(e));
			throw new ApisResourceAccessException("Unable to revoke VID : " + e.getMessage());
		}

		logger.debug(LoggerFileConstant.SESSIONID.toString(), LoggerFileConstant.REGISTRATIONID.toString(),
				"", "ResidentVidServiceImpl::vidDeactivator():: revoke Vid response :: "
						+ JsonUtils.javaObjectToJsonString(response));

		if (response == null || response.getErrors() != null && !response.getErrors().isEmpty()) {
			throw new VidRevocationException(ResidentErrorCode.VID_REVOCATION_EXCEPTION.getErrorMessage());

		}

		VidGeneratorResponseDto vidResponse = mapper.convertValue(response.getResponse(), VidGeneratorResponseDto.class);

		return vidResponse;

	}

	/**
	 * The function is used to fetch the VID policy from the URL and store
	 * return it.
	 * 
	 * @return The vidPolicy is being returned.
	 */
	@Override
	@PostConstruct
	public String getVidPolicy() throws ResidentServiceCheckedException {
		if (Objects.isNull(vidPolicy)) {
			try {
				JsonNode policyJson = mapper.readValue(new URL(vidPolicyUrl), JsonNode.class);
				vidPolicy = policyJson.toString();
			} catch (IOException e) {
				logger.error(LoggerFileConstant.SESSIONID.toString(), LoggerFileConstant.REGISTRATIONID.toString(),
						"GetVidPolicy",
						ResidentErrorCode.API_RESOURCE_UNAVAILABLE.getErrorCode() + ExceptionUtils.getStackTrace(e));
				throw new ResidentServiceCheckedException(ResidentErrorCode.POLICY_EXCEPTION.getErrorCode(),
						ResidentErrorCode.POLICY_EXCEPTION.getErrorMessage(), e);
			}
		}
		return vidPolicy;
	}

}
