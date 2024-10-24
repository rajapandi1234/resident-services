package io.mosip.resident.service.impl;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.crypto.SecretKey;

import io.mosip.resident.dto.IdentityDTO;
import io.mosip.resident.util.AvailableClaimUtility;
import io.mosip.resident.util.IdentityUtil;
import io.mosip.resident.util.SessionUserNameUtility;
import io.mosip.resident.validator.ValidateOtpCharLimit;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemReader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.mosip.kernel.core.crypto.spi.CryptoCoreSpec;
import io.mosip.kernel.core.http.ResponseWrapper;
import io.mosip.kernel.core.logger.spi.Logger;
import io.mosip.kernel.core.util.CryptoUtil;
import io.mosip.kernel.core.util.DateUtils;
import io.mosip.kernel.core.util.HMACUtils2;
import io.mosip.kernel.core.util.JsonUtils;
import io.mosip.kernel.core.util.exception.JsonProcessingException;
import io.mosip.kernel.keygenerator.bouncycastle.KeyGenerator;
import io.mosip.resident.config.LoggerConfiguration;
import io.mosip.resident.constant.ApiName;
import io.mosip.resident.constant.AuthTypeStatus;
import io.mosip.resident.constant.EventStatusFailure;
import io.mosip.resident.constant.EventStatusInProgress;
import io.mosip.resident.constant.EventStatusSuccess;
import io.mosip.resident.constant.LoggerFileConstant;
import io.mosip.resident.constant.RequestType;
import io.mosip.resident.constant.ResidentConstants;
import io.mosip.resident.constant.ResidentErrorCode;
import io.mosip.resident.constant.TemplateType;
import io.mosip.resident.dto.AuthRequestDTO;
import io.mosip.resident.dto.AuthResponseDTO;
import io.mosip.resident.dto.AuthTxnDetailsDTO;
import io.mosip.resident.dto.AuthTypeDTO;
import io.mosip.resident.dto.AuthTypeStatusRequestDto;
import io.mosip.resident.dto.AuthTypeStatusResponseDto;
import io.mosip.resident.dto.AutnTxnDto;
import io.mosip.resident.dto.AutnTxnResponseDto;
import io.mosip.resident.dto.NotificationRequestDtoV2;
import io.mosip.resident.dto.OtpAuthRequestDTO;
import io.mosip.resident.dto.PublicKeyResponseDto;
import io.mosip.resident.entity.ResidentTransactionEntity;
import io.mosip.resident.exception.ApisResourceAccessException;
import io.mosip.resident.exception.CertificateException;
import io.mosip.resident.exception.OtpValidationFailedException;
import io.mosip.resident.exception.ResidentServiceCheckedException;
import io.mosip.resident.repository.ResidentTransactionRepository;
import io.mosip.resident.service.IdAuthService;
import io.mosip.resident.service.NotificationService;
import io.mosip.resident.util.ResidentServiceRestClient;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

@Component
public class IdAuthServiceImpl implements IdAuthService {

	private static final Logger logger = LoggerConfiguration.logConfig(IdAuthServiceImpl.class);

	@Value("${auth.internal.id}")
	private String internalAuthId;

	@Value("${auth.internal.version}")
	private String internalAuthVersion;

	@Value("${auth.type.status.id}")
	private String authTypeStatusId;

	@Value("${mosip.ida.domain-url:null}")
	private String domainUrl;

	@Value("${mosip.ida.env:Staging}")
	private String idaEnv;
	
	@Autowired
	ObjectMapper mapper;

	@Autowired
	private KeyGenerator keyGenerator;

	@Autowired
	private Environment environment;

	@Autowired
	private ResidentServiceRestClient restClient;

	@Autowired
	private ResidentTransactionRepository residentTransactionRepository;

	@Autowired
	private CryptoCoreSpec<byte[], byte[], SecretKey, PublicKey, PrivateKey, String> encryptor;

	@Autowired
	private NotificationService notificationService;

	@Autowired
	private SessionUserNameUtility sessionUserNameUtility;

	@Autowired
	private AvailableClaimUtility availableClaimUtility;

	@Autowired
	private IdentityUtil identityUtil;

	@Autowired
	private ValidateOtpCharLimit validateOtpCharLimit;

	@Override
	public boolean validateOtp(String transactionId, String individualId, String otp)
			throws OtpValidationFailedException, ResidentServiceCheckedException {
		return validateOtpV1(transactionId, individualId, otp).getT1();
	}
	
	@Override
	public Tuple2<Boolean, String> validateOtpV1(String transactionId, String individualId, String otp)
			throws OtpValidationFailedException, ResidentServiceCheckedException {
		logger.debug("IdAuthServiceImpl::validateOtpV1()::entry");
		AuthResponseDTO response = null;
		String eventId = ResidentConstants.NOT_AVAILABLE;
		boolean authStatus = false;
		try {
			response = internelOtpAuth(transactionId, individualId, otp);
			if (response.getErrors() != null && !response.getErrors().isEmpty()) {
				response.getErrors().stream().forEach(error -> logger.error(LoggerFileConstant.SESSIONID.toString(),
						LoggerFileConstant.USERID.toString(), error.getErrorCode(), error.getErrorMessage()));
				eventId = updateResidentTransactionAndSendNotification(transactionId, individualId, eventId, authStatus);
				throw new OtpValidationFailedException(response.getErrors().get(0).getErrorMessage(),
						Map.of(ResidentConstants.EVENT_ID, eventId));
			}
			if (response.getResponse() != null) {
				authStatus = response.getResponse().isAuthStatus();
				eventId = updateResidentTransactionAndSendNotification(transactionId, individualId, eventId, authStatus);
			}
		} catch (ApisResourceAccessException | InvalidKeySpecException | NoSuchAlgorithmException | IOException
				| JsonProcessingException | java.security.cert.CertificateException e) {
			logger.error(LoggerFileConstant.SESSIONID.toString(), LoggerFileConstant.USERID.toString(), null,
					"IdAuthServiceImpl::validateOtp():: validate otp method call" + ExceptionUtils.getStackTrace(e));
			eventId = updateResidentTransactionAndSendNotification(transactionId, individualId, eventId, authStatus);
			throw new OtpValidationFailedException(e.getMessage(), Map.of(ResidentConstants.EVENT_ID, eventId));
		}
		logger.debug("IdAuthServiceImpl::validateOtpV1()::exit");
		return Tuples.of(authStatus, eventId);
	}

	private String updateResidentTransactionAndSendNotification(String transactionId, String individualId,
			String eventId, boolean authStatus) throws ResidentServiceCheckedException {
		ResidentTransactionEntity residentTransactionEntity = null;
		IdentityDTO identityDTO = identityUtil.getIdentity(individualId);
		residentTransactionEntity = updateResidentTransaction(authStatus, transactionId, RequestType.VALIDATE_OTP,
				availableClaimUtility.getIDAToken(identityDTO.getUIN()));
		if (residentTransactionEntity != null) {
			eventId = residentTransactionEntity.getEventId();
			TemplateType templateType = authStatus == true ? TemplateType.SUCCESS : TemplateType.FAILURE;
			sendNotificationV2(individualId, templateType, eventId, residentTransactionEntity.getAttributeList(), identityDTO);
		}
		return eventId;
	}

	private void sendNotificationV2(String id, TemplateType templateType, String eventId, String channels, Map identity)
			throws ResidentServiceCheckedException {
		NotificationRequestDtoV2 notificationRequestDtoV2 = new NotificationRequestDtoV2();
		notificationRequestDtoV2.setId(id);
		notificationRequestDtoV2.setRequestType(RequestType.VALIDATE_OTP);
		notificationRequestDtoV2.setTemplateType(templateType);
		notificationRequestDtoV2.setEventId(eventId);
		notificationService.sendNotification(notificationRequestDtoV2,
				(channels != null ? List.of(channels.split(ResidentConstants.ATTRIBUTE_LIST_DELIMITER)) : null), null, null, identity);
	}

	@Override
	public Tuple2<Boolean, ResidentTransactionEntity> validateOtpV2(String transactionId, String individualId, String otp,
																	RequestType requestType)
			throws OtpValidationFailedException, ResidentServiceCheckedException {
		logger.debug("IdAuthServiceImpl::validateOtpV2()::entry");
		validateOtpCharLimit.validateOtpCharLimit(otp);
		AuthResponseDTO response = null;
		String eventId = ResidentConstants.NOT_AVAILABLE;
		ResidentTransactionEntity residentTransactionEntity = null;
		String channels = null;
		try {
			response = internelOtpAuth(transactionId, individualId, otp);
			residentTransactionEntity = updateResidentTransaction(response.getResponse().isAuthStatus(), transactionId,
					requestType, availableClaimUtility.getIDATokenForIndividualId(individualId));
			if (residentTransactionEntity != null) {
				eventId = residentTransactionEntity.getEventId();
				channels = residentTransactionEntity.getAttributeList();
			}
		} catch (ApisResourceAccessException | InvalidKeySpecException | NoSuchAlgorithmException | IOException
				| JsonProcessingException | java.security.cert.CertificateException e) {
			logger.error(LoggerFileConstant.SESSIONID.toString(), LoggerFileConstant.USERID.toString(), null,
					"IdAuthServiceImpl::validateOtp():: validate otp method call" + ExceptionUtils.getStackTrace(e));
			throw new OtpValidationFailedException(e.getMessage(), Map.of(ResidentConstants.EVENT_ID, eventId));
		}
		if (response.getErrors() != null && !response.getErrors().isEmpty()) {
			response.getErrors().stream().forEach(error -> logger.error(LoggerFileConstant.SESSIONID.toString(),
					LoggerFileConstant.USERID.toString(), error.getErrorCode(), error.getErrorMessage()));
			if (response.getErrors().get(0).getErrorCode().equals(ResidentConstants.OTP_EXPIRED_ERR_CODE)) {
				throw new OtpValidationFailedException(ResidentErrorCode.OTP_EXPIRED.getErrorCode(),
						ResidentErrorCode.OTP_EXPIRED.getErrorMessage(), Map.of(ResidentConstants.EVENT_ID, eventId));
			}
			if (response.getErrors().get(0).getErrorCode().equals(ResidentConstants.OTP_INVALID_ERR_CODE)) {
				throw new OtpValidationFailedException(ResidentErrorCode.OTP_INVALID.getErrorCode(),
						ResidentErrorCode.OTP_INVALID.getErrorMessage(), Map.of(ResidentConstants.EVENT_ID, eventId));
			}
			if (response.getErrors().get(0).getErrorCode().equals(ResidentConstants.INVALID_ID_ERR_CODE)) {
				throw new OtpValidationFailedException(ResidentErrorCode.INVALID_TRANSACTION_ID.getErrorCode(),
						response.getErrors().get(0).getErrorMessage(), Map.of(ResidentConstants.EVENT_ID, eventId));
			}
			if (response.getErrors().get(0).getErrorCode().equals(ResidentConstants.OTP_AUTH_LOCKED_ERR_CODE)) {
				if (channels != null) {
					if (channels.equals(ResidentConstants.PHONE)) {
						throw new OtpValidationFailedException(ResidentErrorCode.SMS_AUTH_LOCKED.getErrorCode(),
								ResidentErrorCode.SMS_AUTH_LOCKED.getErrorMessage(),
								Map.of(ResidentConstants.EVENT_ID, eventId));
					}
					if (channels.equals(ResidentConstants.EMAIL)) {
						throw new OtpValidationFailedException(ResidentErrorCode.EMAIL_AUTH_LOCKED.getErrorCode(),
								ResidentErrorCode.EMAIL_AUTH_LOCKED.getErrorMessage(),
								Map.of(ResidentConstants.EVENT_ID, eventId));
					}
					boolean containsPhone = channels.contains(ResidentConstants.PHONE);
					boolean containsEmail = channels.contains(ResidentConstants.EMAIL);
					if (containsPhone && containsEmail) {
						throw new OtpValidationFailedException(
								ResidentErrorCode.SMS_AND_EMAIL_AUTH_LOCKED.getErrorCode(),
								ResidentErrorCode.SMS_AND_EMAIL_AUTH_LOCKED.getErrorMessage(),
								Map.of(ResidentConstants.EVENT_ID, eventId));
					}
				}
			} else
				throw new OtpValidationFailedException(response.getErrors().get(0).getErrorMessage(),
						Map.of(ResidentConstants.EVENT_ID, eventId));
		}
		logger.debug("IdAuthServiceImpl::validateOtpV2()::exit");
		return Tuples.of(response.getResponse().isAuthStatus(), residentTransactionEntity);
	}

	private ResidentTransactionEntity updateResidentTransaction(boolean verified, String transactionId, RequestType requestType, String idaToken) {
		ResidentTransactionEntity residentTransactionEntity = residentTransactionRepository.
				findTopByRequestTrnIdAndTokenIdAndStatusCodeInOrderByCrDtimesDesc(transactionId, idaToken
				, List.of(EventStatusInProgress.OTP_REQUESTED.name(), EventStatusFailure.OTP_VERIFICATION_FAILED.name()));
		if (residentTransactionEntity != null ) {
			residentTransactionEntity.setRequestTypeCode(requestType.name());
			residentTransactionEntity.setRequestSummary(verified? "OTP verified successfully": "OTP verification failed");
			residentTransactionEntity.setStatusCode(verified? EventStatusSuccess.OTP_VERIFIED.name(): EventStatusFailure.OTP_VERIFICATION_FAILED.name());
			residentTransactionEntity.setStatusComment(verified? "OTP verified successfully": "OTP verification failed");
			residentTransactionEntity.setUpdBy(sessionUserNameUtility.getSessionUserName());
			residentTransactionEntity.setUpdDtimes(DateUtils.getUTCCurrentDateTime());
			residentTransactionRepository.save(residentTransactionEntity);
		}
		return residentTransactionEntity;
	}

	public AuthResponseDTO internelOtpAuth(String transactionId, String individualId,
			String otp) throws ApisResourceAccessException, InvalidKeySpecException, NoSuchAlgorithmException,
			IOException, JsonProcessingException, CertificateEncodingException {
		logger.debug(LoggerFileConstant.SESSIONID.toString(), LoggerFileConstant.USERID.toString(), null,
				"IdAuthServiceImpl::internelOtpAuth()::entry");
		String dateTime = DateUtils.formatToISOString(DateUtils.getUTCCurrentDateTime());
		AuthRequestDTO authRequestDTO = new AuthRequestDTO();
		authRequestDTO.setId(internalAuthId);
		authRequestDTO.setVersion(internalAuthVersion);

		authRequestDTO.setRequestTime(dateTime);
		authRequestDTO.setTransactionID(transactionId);
		authRequestDTO.setEnv(idaEnv);
		authRequestDTO.setDomainUri(domainUrl);

		AuthTypeDTO authType = new AuthTypeDTO();
		authType.setOtp(true);
		authRequestDTO.setRequestedAuth(authType);

		authRequestDTO.setConsentObtained(true);
		authRequestDTO.setIndividualId(individualId);

		OtpAuthRequestDTO request = new OtpAuthRequestDTO();
		request.setOtp(otp);
		request.setTimestamp(dateTime);

		String identityBlock = mapper.writeValueAsString(request);

		final SecretKey secretKey = keyGenerator.getSymmetricKey();
		// Encrypted request with session key
		byte[] encryptedIdentityBlock = encryptor.symmetricEncrypt(secretKey, identityBlock.getBytes(), null);
		// rbase64 encoded for request
		authRequestDTO.setRequest(CryptoUtil.encodeToURLSafeBase64(encryptedIdentityBlock));
		// encrypted with MOSIP public key and encoded session key
		Tuple2<byte[], String> encryptionResult = encryptRSA(secretKey.getEncoded(), "INTERNAL");
		byte[] encryptedSessionKeyByte = encryptionResult.getT1();
		authRequestDTO.setRequestSessionKey(CryptoUtil.encodeToURLSafeBase64(encryptedSessionKeyByte));

		// sha256 of the request block before encryption and the hash is encrypted
		// using the requestSessionKey
		byte[] byteArray = encryptor.symmetricEncrypt(secretKey,
				HMACUtils2.digestAsPlainText(identityBlock.getBytes()).getBytes(), null);
		authRequestDTO.setRequestHMAC(CryptoUtil.encodeToURLSafeBase64(byteArray));
		String thumbprint = encryptionResult.getT2();
		authRequestDTO.setThumbprint(thumbprint);
		logger.debug(LoggerFileConstant.SESSIONID.toString(), LoggerFileConstant.USERID.toString(), null,
				"internelOtpAuth()::INTERNALAUTH POST service call started with request data "
						+ JsonUtils.javaObjectToJsonString(authRequestDTO));

		AuthResponseDTO response;
		try {
			response = (AuthResponseDTO) restClient.postApi(environment.getProperty(ApiName.INTERNALAUTH.name()),
					MediaType.APPLICATION_JSON, authRequestDTO, AuthResponseDTO.class);

			logger.debug(LoggerFileConstant.SESSIONID.toString(), LoggerFileConstant.USERID.toString(), null,
					"IdAuthServiceImpl::internelOtpAuth()::INTERNALAUTH POST service call ended with response data "
							+ JsonUtils.javaObjectToJsonString(response));
		} catch (Exception e) {
			logger.error(LoggerFileConstant.SESSIONID.toString(), LoggerFileConstant.USERID.toString(), null,
					"IdAuthServiceImp::internelOtpAuth():: INTERNALAUTH GET service call"
							+ ExceptionUtils.getStackTrace(e));
			throw new ApisResourceAccessException("Could not fetch public key from kernel keymanager", e);
		}

		return response;

	}

	private Tuple2<byte[], String> encryptRSA(final byte[] sessionKey, String refId) throws ApisResourceAccessException,
			InvalidKeySpecException, NoSuchAlgorithmException, IOException, JsonProcessingException, CertificateEncodingException {

		// encrypt AES Session Key using RSA public key
		ResponseWrapper<?> responseWrapper = null;
		PublicKeyResponseDto publicKeyResponsedto;

		String uri = environment.getProperty(ApiName.KERNELENCRYPTIONSERVICE.name());
		UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(uri);

		builder.queryParam("applicationId", "IDA");
		builder.queryParam("referenceId", refId);
		builder.queryParam("timeStamp", DateUtils.formatToISOString(DateUtils.getUTCCurrentDateTime()));

		UriComponents uriComponent = builder.build(false).encode();

		try {
			responseWrapper = (ResponseWrapper<?>) restClient.getApi(uriComponent.toUri(), ResponseWrapper.class);
		} catch (Exception e) {
			logger.error(LoggerFileConstant.SESSIONID.toString(), LoggerFileConstant.USERID.toString(), refId,
					"IdAuthServiceImp::lencryptRSA():: ENCRYPTIONSERVICE GET service call"
							+ ExceptionUtils.getStackTrace(e));
			throw new ApisResourceAccessException("Could not fetch public key from kernel keymanager", e);
		}
		publicKeyResponsedto = mapper.readValue(mapper.writeValueAsString(responseWrapper.getResponse()),
				PublicKeyResponseDto.class);
		X509Certificate req509 = (X509Certificate) convertToCertificate(publicKeyResponsedto.getCertificate());
		String thumbprint = CryptoUtil.encodeToURLSafeBase64(getCertificateThumbprint(req509));

		PublicKey publicKey = req509.getPublicKey();
		if (thumbprint == null) {
			thumbprint = "";
		}
		byte[] asymmetricEncrypt = encryptor.asymmetricEncrypt(publicKey, sessionKey);
		if(asymmetricEncrypt == null) {
			asymmetricEncrypt = new byte[0];
		}
		return Tuples.of(asymmetricEncrypt, thumbprint);
	}
	
	@Override
	public boolean authTypeStatusUpdate(String individualId, List<String> authType, AuthTypeStatus authTypeStatus, Long unlockForSeconds)
			throws ApisResourceAccessException{
		Map<String, AuthTypeStatus> authTypeStatusMap=authType.stream().distinct().collect(Collectors.toMap(Function.identity(), str -> authTypeStatus));
		Map<String, Long> unlockForSecondsMap=authType.stream().distinct().filter(str -> unlockForSeconds!=null).collect(Collectors.toMap(Function.identity(), str -> unlockForSeconds));
		String requestIdForAuthLockUnLock = authTypeStatusUpdate(individualId, authTypeStatusMap, unlockForSecondsMap);
		return requestIdForAuthLockUnLock != null && !requestIdForAuthLockUnLock.isEmpty();
	}

	@Override
	public String authTypeStatusUpdateForRequestId(String individualId, Map<String, AuthTypeStatus> authTypeStatusMap, Map<String, Long> unlockForSecondsMap) throws ApisResourceAccessException {
		String requestIdForAuthLockUnLock = authTypeStatusUpdate(individualId, authTypeStatusMap, unlockForSecondsMap);
		if(requestIdForAuthLockUnLock != null){
			return requestIdForAuthLockUnLock;
		}
		return "";
	}
	
	@Override
	public String authTypeStatusUpdate(String individualId, Map<String, AuthTypeStatus> authTypeStatusMap, Map<String, Long> unlockForSecondsMap)
			throws ApisResourceAccessException {
		AuthTypeStatusRequestDto authTypeStatusRequestDto = new AuthTypeStatusRequestDto();
		authTypeStatusRequestDto.setConsentObtained(true);
		authTypeStatusRequestDto.setId(authTypeStatusId);
		authTypeStatusRequestDto.setIndividualId(individualId);
		authTypeStatusRequestDto.setVersion(internalAuthVersion);
		authTypeStatusRequestDto.setRequestTime(DateUtils.formatToISOString(DateUtils.getUTCCurrentDateTime()));
		List<io.mosip.resident.dto.AuthTypeStatus> authTypes = new ArrayList<>();
		String requestIdForAuthLockUnLock = null;
		for (Entry<String, AuthTypeStatus> entry : authTypeStatusMap.entrySet()) {

			String[] types = entry.getKey().split("-");
			io.mosip.resident.dto.AuthTypeStatus authTypeStatus = new io.mosip.resident.dto.AuthTypeStatus();
			if(requestIdForAuthLockUnLock==null){
				String requestId = UUID.randomUUID().toString();
				requestIdForAuthLockUnLock = requestId;
			}
			authTypeStatus.setRequestId(requestIdForAuthLockUnLock);
			if (types.length == 1) {
				authTypeStatus.setAuthType(types[0]);
			} else {
				authTypeStatus.setAuthType(types[0]);
				authTypeStatus.setAuthSubType(types[1]);
			}
			if (entry.getValue().equals(AuthTypeStatus.LOCK)) {
				authTypeStatus.setLocked(true);
				authTypeStatus.setUnlockForSeconds(null);
			} else {
				if (unlockForSecondsMap.get(entry.getKey()) != null) {
					authTypeStatus.setUnlockForSeconds(unlockForSecondsMap.get(entry.getKey()));
                }

				authTypeStatus.setLocked(false);
			}

			authTypes.add(authTypeStatus);
		}
		authTypeStatusRequestDto.setRequest(authTypes);
		AuthTypeStatusResponseDto response;
		try {
			response = restClient.postApi(environment.getProperty(ApiName.AUTHTYPESTATUSUPDATE.name()),
					MediaType.APPLICATION_JSON, authTypeStatusRequestDto, AuthTypeStatusResponseDto.class);

			logger.debug(LoggerFileConstant.SESSIONID.toString(), LoggerFileConstant.USERID.toString(), null,
					"IdAuthServiceImp::authLock():: AUTHLOCK POST service call ended with response data "
							+ JsonUtils.javaObjectToJsonString(response));

		} catch (Exception e) {
			logger.error(LoggerFileConstant.SESSIONID.toString(), LoggerFileConstant.USERID.toString(), null,
					"IdAuthServiceImp::authLock():: AUTHLOCK POST service call" + ExceptionUtils.getStackTrace(e));
			throw new ApisResourceAccessException("Could not able call auth status api", e);
		}

		if (response.getErrors() != null && !response.getErrors().isEmpty()) {
			response.getErrors().stream().forEach(error -> logger.error(LoggerFileConstant.SESSIONID.toString(),
					LoggerFileConstant.USERID.toString(), error.getErrorCode(), error.getErrorMessage()));

		}

		return requestIdForAuthLockUnLock;
	}

	@Override
	public List<AuthTxnDetailsDTO> getAuthHistoryDetails(String individualId,
			String pageStart, String pageFetch) throws ApisResourceAccessException {
		List<AuthTxnDetailsDTO> details = null;
		int count = 1;
		AutnTxnResponseDto autnTxnResponseDto;
		List<String> pathsegments = new ArrayList<>();
		pathsegments.add(0, "individualId");
		pathsegments.add(1, individualId);
		String queryParamName = null;
		String queryParamValue = null;
		if (pageStart != null && pageFetch != null && !pageStart.isEmpty() && !pageFetch.isEmpty()) {
			queryParamName = "pageFetch,pageStart";
			queryParamValue = pageFetch + "," + pageStart;
			count = count + Integer.parseInt(pageFetch) * ( Integer.parseInt(pageStart)- 1);
		}
		try {
			autnTxnResponseDto = (AutnTxnResponseDto) restClient.getApi(ApiName.INTERNALAUTHTRANSACTIONS, pathsegments,
					queryParamName, queryParamValue, AutnTxnResponseDto.class);

		} catch (Exception e) {
			logger.error(LoggerFileConstant.SESSIONID.toString(), LoggerFileConstant.USERID.toString(), null,
					"IdAuthServiceImp::getAuthHistoryDetails():: AUTHTransactions GET service call"
							+ ExceptionUtils.getStackTrace(e));
			throw new ApisResourceAccessException("Could not able call auth transactions api", e);
		}
		if (autnTxnResponseDto.getErrors() != null && !autnTxnResponseDto.getErrors().isEmpty()) {
			autnTxnResponseDto.getErrors().stream()
					.forEach(error -> logger.error(LoggerFileConstant.SESSIONID.toString(),
							LoggerFileConstant.USERID.toString(), error.getErrorCode(), error.getErrorMessage()));

		} else if (autnTxnResponseDto.getResponse().get("authTransactions") != null) {
			details = new ArrayList<AuthTxnDetailsDTO>();
			if (!autnTxnResponseDto.getResponse().get("authTransactions").isEmpty()) {
				for (AutnTxnDto autnTxnDto : autnTxnResponseDto.getResponse().get("authTransactions")) {
					details.add(getDetails(autnTxnDto, count));
					count++;
				}
			}
		}
		return details;
	}

	private AuthTxnDetailsDTO getDetails(AutnTxnDto autnTxnDto, int count) {
		AuthTxnDetailsDTO authTxnDetailsDTO = new AuthTxnDetailsDTO();
		authTxnDetailsDTO.setSerialNumber(count);
		authTxnDetailsDTO.setAuthModality(autnTxnDto.getAuthtypeCode());
		authTxnDetailsDTO.setAuthResponse(autnTxnDto.getStatusComment());
		authTxnDetailsDTO.setIdUsed(autnTxnDto.getReferenceIdType());
		authTxnDetailsDTO.setPartnerName(autnTxnDto.getEntityName());
		authTxnDetailsDTO.setPartnerTransactionId(autnTxnDto.getTransactionID());
		authTxnDetailsDTO.setResponseCode(autnTxnDto.getStatusCode());
		authTxnDetailsDTO.setDate(autnTxnDto.getRequestdatetime().format(DateTimeFormatter.ISO_LOCAL_DATE));
		authTxnDetailsDTO.setTime(autnTxnDto.getRequestdatetime().format(DateTimeFormatter.ISO_LOCAL_TIME));
		return authTxnDetailsDTO;
	}

	private java.security.cert.Certificate convertToCertificate(String certData) {
		try {
			StringReader strReader = new StringReader(certData);
			PemReader pemReader = new PemReader(strReader);
			PemObject pemObject = pemReader.readPemObject();
			if (Objects.isNull(pemObject)) {
				throw new CertificateException(ResidentErrorCode.API_RESOURCE_UNAVAILABLE.getErrorCode(),
						ResidentErrorCode.API_RESOURCE_UNAVAILABLE.getErrorMessage());
			}
			byte[] certBytes = pemObject.getContent();
			CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
			return certFactory.generateCertificate(new ByteArrayInputStream(certBytes));
		} catch (IOException | java.security.cert.CertificateException e) {
			throw new CertificateException(ResidentErrorCode.API_RESOURCE_UNAVAILABLE.getErrorCode(),
					ResidentErrorCode.API_RESOURCE_UNAVAILABLE.getErrorMessage(), e);
		}
	}

	private byte[] getCertificateThumbprint(java.security.cert.Certificate cert)
			throws CertificateEncodingException {

        return DigestUtils.sha256(cert.getEncoded());
	}
}
	

