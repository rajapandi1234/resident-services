package io.mosip.resident.constant;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.mosip.resident.util.DescriptionTemplateVariables;
import org.springframework.core.env.Environment;

import io.mosip.kernel.core.logger.spi.Logger;
import io.mosip.resident.config.LoggerConfiguration;
import io.mosip.resident.dto.NotificationTemplateVariableDTO;
import io.mosip.resident.entity.ResidentTransactionEntity;
import io.mosip.resident.exception.ApisResourceAccessException;
import io.mosip.resident.exception.ResidentServiceCheckedException;
import io.mosip.resident.function.FiveArgsFunction;
import io.mosip.resident.function.FourArgsFunction;
import io.mosip.resident.function.ThreeArgsFunction;
import io.mosip.resident.util.TemplateUtil;
import io.mosip.resident.util.Utility;
import reactor.util.function.Tuple2;

/**
 * The Enum RequestType.
 * 
 * @author Kamesh Shekhar Prasad
 */

public enum RequestType implements PreUpdateInBatchJob {
	AUTHENTICATION_REQUEST("Authentication Request", TemplateUtil::getAckTemplateVariablesForAuthenticationRequest,
			null, DescriptionTemplateVariables::getDescriptionTemplateVariablesForAuthenticationRequest),
	SHARE_CRED_WITH_PARTNER("Share Credential With Partner", TemplateUtil::getAckTemplateVariablesForShareCredentialWithPartner,
			TemplateUtil::getNotificationTemplateVariablesForShareCredentialWithPartner,
			DescriptionTemplateVariables::getDescriptionTemplateVariablesForShareCredentialWithPartner,
			ResidentConstants.ACK_SHARE_CREDENTIAL_NAMING_CONVENTION_PROPERTY),
	DOWNLOAD_PERSONALIZED_CARD("Download Personalized Card",
			TemplateUtil::getAckTemplateVariablesForDownloadPersonalizedCard,
			TemplateUtil::getNotificationTemplateVariablesForDownloadPersonalizedCard,
			DescriptionTemplateVariables::getDescriptionTemplateVariablesForDownloadPersonalizedCard,
			ResidentConstants.ACK_PERSONALIZED_CARD_NAMING_CONVENTION_PROPERTY),
	ORDER_PHYSICAL_CARD("Order a Physical Card", TemplateUtil::getAckTemplateVariablesForOrderPhysicalCard,
			TemplateUtil::getNotificationTemplateVariablesForOrderPhysicalCard,
			DescriptionTemplateVariables::getDescriptionTemplateVariablesForOrderPhysicalCard,
			ResidentConstants.ACK_ORDER_PHYSICAL_CARD_NAMING_CONVENTION_PROPERTY) {
		@Override
		public void preUpdateInBatchJob(Environment env, Utility utility, ResidentTransactionEntity txn, Map<String, String> credentialStatus,
				String newStatusCode)
				throws ResidentServiceCheckedException, ApisResourceAccessException {
			if (this.isSuccessStatus(env, newStatusCode)) {
				String trackingId = utility.getCardOrderTrackingId(txn.getRequestTrnId(), txn.getIndividualId());
				txn.setTrackingId(trackingId);
			}
		}
	},
	GET_MY_ID("Get UIN Card", TemplateUtil::getAckTemplateVariablesForGetMyId,
			TemplateUtil::getNotificationTemplateVariablesForGetMyId,
			DescriptionTemplateVariables::getDescriptionTemplateVariablesForGetMyId),
	UPDATE_MY_UIN("Update UIN Data", TemplateUtil::getAckTemplateVariablesForUpdateMyUin,
			TemplateUtil::getNotificationTemplateVariablesForUpdateMyUin,
			DescriptionTemplateVariables::getDescriptionTemplateVariablesForUpdateMyUin,
			ResidentConstants.ACK_UPDATE_MY_DATA_NAMING_CONVENTION_PROPERTY) {
		@Override
		public void preUpdateInBatchJob(Environment env, Utility utility, ResidentTransactionEntity txn,
				Map<String, String> credentialStatus, String newStatusCode)
				throws ResidentServiceCheckedException, ApisResourceAccessException {
			if (this.isSuccessStatus(env, newStatusCode)) {
				txn.setStatusCode(EventStatusSuccess.CARD_READY_TO_DOWNLOAD.name());
			}
		}
	},
	GENERATE_VID("Generate VID", TemplateUtil::getAckTemplateVariablesForGenerateVid,
			TemplateUtil::getNotificationTemplateVariablesForGenerateOrRevokeVid,
			DescriptionTemplateVariables::getDescriptionTemplateVariablesForManageMyVid,
			ResidentConstants.ACK_MANAGE_MY_VID_NAMING_CONVENTION_PROPERTY),
	REVOKE_VID("Revoke VID", TemplateUtil::getAckTemplateVariablesForRevokeVid,
			TemplateUtil::getNotificationTemplateVariablesForGenerateOrRevokeVid,
			DescriptionTemplateVariables::getDescriptionTemplateVariablesForManageMyVid,
			ResidentConstants.ACK_MANAGE_MY_VID_NAMING_CONVENTION_PROPERTY),
	AUTH_TYPE_LOCK_UNLOCK("Secure My ID",
			TemplateUtil::getAckTemplateVariablesForAuthTypeLockUnlock,
			TemplateUtil::getNotificationTemplateVariablesForAuthTypeLockUnlock,
			DescriptionTemplateVariables::getDescriptionTemplateVariablesForSecureMyId,
			ResidentConstants.ACK_SECURE_MY_ID_NAMING_CONVENTION_PROPERTY),
	VID_CARD_DOWNLOAD("Download VID Card", TemplateUtil::getAckTemplateVariablesForVidCardDownload,
			TemplateUtil::getNotificationTemplateVariablesForVidCardDownload,
			DescriptionTemplateVariables::getDescriptionTemplateVariablesForVidCardDownload) {
		@Override
		public void preUpdateInBatchJob(Environment env, Utility utility, ResidentTransactionEntity txn,
				Map<String, String> credentialStatus, String newStatusCode)
				throws ResidentServiceCheckedException, ApisResourceAccessException {
			if (this.isSuccessStatus(env, newStatusCode)) {
				txn.setStatusCode(EventStatusSuccess.CARD_READY_TO_DOWNLOAD.name());
			}
		}
	},
	SEND_OTP("Send OTP", TemplateUtil::getAckTemplateVariablesForSendOtp,
			TemplateUtil::getNotificationSendOtpVariables, null),
	VALIDATE_OTP("Verify My Phone/Email", TemplateUtil::getAckTemplateVariablesForValidateOtp,
			TemplateUtil::getNotificationCommonTemplateVariables,
			DescriptionTemplateVariables::getDescriptionTemplateVariablesForValidateOtp),
	DEFAULT("Default", TemplateUtil::getAckTemplateVariablesForDefault,
			TemplateUtil::getNotificationCommonTemplateVariables, null);

	private static final String PREFIX_RESIDENT_TEMPLATE_EMAIL_SUBJECT = "resident.template.email.subject.%s.%s";

	private static final String PREFIX_RESIDENT_TEMPLATE_EMAIL_CONTENT = "resident.template.email.content.%s.%s";

	private static final String PREFIX_RESIDENT_TEMPLATE_SMS = "resident.template.sms.%s.%s";

	private static final String PREFIX_RESIDENT_TEMPLATE_PURPOSE = "resident.template.purpose.%s.%s";

	private static final String PREFIX_RESIDENT_TEMPLATE_SUMMARY = "resident.template.summary.%s.%s";

	private static final Logger logger = LoggerConfiguration.logConfig(RequestType.class);
	
	private static final String PREFIX_RESIDENT_REQUEST_NOTIFICATION_STATUS_LIST = "resident.request.notification.status.list.";
	private static final String PREFIX_RESIDENT_REQUEST_FAILED_STATUS_LIST = "resident.request.failed.status.list.";
	private static final String PREFIX_RESIDENT_REQUEST_CANCELLED_STATUS_LIST = "resident.request.cancelled.status.list.";
	private static final String PREFIX_RESIDENT_REQUEST_SUCCESS_STATUS_LIST = "resident.request.success.status.list.";
	private static final String PREFIX_RESIDENT_REQUEST_IN_PROGRESS_STATUS_LIST = "resident.request.in-progress.status.list.";
	private static final String PREFIX_RESIDENT_REQUEST_NEW_STATUS_LIST = "resident.request.new.status.list.";
	private static final String SEPARATOR = ",";
	private FiveArgsFunction<TemplateUtil, ResidentTransactionEntity, String, Integer, String, Tuple2<Map<String, String>, String>> ackTemplateVariablesFunction;
	private ThreeArgsFunction<TemplateUtil, NotificationTemplateVariableDTO, Map<String, Object>, Map<String, Object>> notificationTemplateVariablesFunction;
	private FourArgsFunction<DescriptionTemplateVariables, ResidentTransactionEntity, String, String, String> getDescriptionTemplateVariables;
	private String namingProperty;
	private String name;

	private RequestType(String name,
			FiveArgsFunction<TemplateUtil, ResidentTransactionEntity, String, Integer, String, Tuple2<Map<String, String>, String>> ackTemplateVariablesFunction,
			ThreeArgsFunction<TemplateUtil, NotificationTemplateVariableDTO, Map<String, Object>, Map<String, Object>> notificationTemplateVariablesFunction,
			FourArgsFunction<DescriptionTemplateVariables, ResidentTransactionEntity, String, String, String> getDescriptionTemplateVariables) {
		this(name, ackTemplateVariablesFunction,
				notificationTemplateVariablesFunction, getDescriptionTemplateVariables, null);
	}

	private RequestType(String name,
			FiveArgsFunction<TemplateUtil, ResidentTransactionEntity, String, Integer, String, Tuple2<Map<String, String>, String>> ackTemplateVariablesFunction,
			ThreeArgsFunction<TemplateUtil, NotificationTemplateVariableDTO, Map<String, Object>, Map<String, Object>> notificationTemplateVariablesFunction,
			FourArgsFunction<DescriptionTemplateVariables, ResidentTransactionEntity, String, String, String> getDescriptionTemplateVariables,
			String namingProperty) {
		this.name = name;
		this.ackTemplateVariablesFunction = ackTemplateVariablesFunction;
		this.notificationTemplateVariablesFunction = notificationTemplateVariablesFunction;
		this.getDescriptionTemplateVariables = getDescriptionTemplateVariables;
		this.namingProperty = namingProperty;
	}

	public static RequestType getRequestTypeFromString(String requestTypeString) {
        for (RequestType requestType : values()) {
            if (requestType.name().equalsIgnoreCase(requestTypeString)) {
                return requestType;
            }
        }
        return RequestType.DEFAULT;
    }
	
	public Stream<String> getNewStatusList(Environment env) {
		return getStatusListFromProperty(env, PREFIX_RESIDENT_REQUEST_NEW_STATUS_LIST);
	}

	public Stream<String> getSuccessStatusList(Environment env) {
		return getStatusListFromProperty(env, PREFIX_RESIDENT_REQUEST_SUCCESS_STATUS_LIST);
	}

	public Stream<String> getFailedStatusList(Environment env) {
		return getStatusListFromProperty(env, PREFIX_RESIDENT_REQUEST_FAILED_STATUS_LIST);
	}

	public Stream<String> getCancelledStatusList(Environment env) {
		return getStatusListFromProperty(env, PREFIX_RESIDENT_REQUEST_CANCELLED_STATUS_LIST);
	}
	
	public Stream<String> getInProgressStatusList(Environment env) {
		return getStatusListFromProperty(env, PREFIX_RESIDENT_REQUEST_IN_PROGRESS_STATUS_LIST);
	}
	
	public Stream<String> getNotificationStatusList(Environment env) {
		return getStatusListFromProperty(env, PREFIX_RESIDENT_REQUEST_NOTIFICATION_STATUS_LIST);
	}
	
	public boolean isNewStatus(Environment env, String statusCode) {
		return isStatusPresent(statusCode, getNewStatusList(env));
	}

	public boolean isSuccessStatus(Environment env, String statusCode) {
		return isStatusPresent(statusCode, getSuccessStatusList(env));
	}

	public boolean isFailedStatus(Environment env, String statusCode) {
		return isStatusPresent(statusCode, getFailedStatusList(env));
	}
	
	public boolean isSuccessOrFailedStatus(Environment env, String statusCode) {
		return isSuccessStatus(env, statusCode) || isFailedStatus(env, statusCode);
	}

	public boolean isInProgressStatus(Environment env, String statusCode) {
		return isStatusPresent(statusCode, getInProgressStatusList(env));
	}

	public boolean isNotificationStatus(Environment env, String statusCode) {
		return isStatusPresent(statusCode, getNotificationStatusList(env));
	}
	
	private boolean isStatusPresent(String statusCode, Stream<String> stream) {
		return statusCode != null && stream.anyMatch(statusCode::equals);
	}
	
	private Stream<String> getStatusListFromProperty(Environment env, String propertyPrefix) {
		String propertyName = propertyPrefix + this.name();
		String statusListStr = env.getProperty(propertyName);
		if (statusListStr == null) {
			logger.debug("missing property: " + propertyName);
			return Stream.empty();
		} else {
			return Arrays.stream(statusListStr.split(SEPARATOR));
		}
	}
	
	public Stream<String> getNewOrInprogressStatusList(Environment env) {
		return Stream.concat(getNewStatusList(env), getInProgressStatusList(env));
	}
	
	public static List<String> getAllNewOrInprogressStatusList(Environment env) {
		return Stream.of(values()).flatMap(requestType -> {
			return Stream.concat(requestType.getNewStatusList(env), requestType.getInProgressStatusList(env));
		}).filter(str -> !str.isEmpty())
		.distinct()	
		.collect(Collectors.toUnmodifiableList());
	}

	public static List<String> getAllCancelledStatusList(Environment env) {
		return Stream.of(values()).flatMap(requestType -> {
					return requestType.getCancelledStatusList(env);
				}).filter(str -> !str.isEmpty())
				.distinct()
				.collect(Collectors.toUnmodifiableList());
	}

	public static List<String> getAllFailedStatusList(Environment env) {
		return Stream.of(values()).flatMap(requestType -> {
					return requestType.getFailedStatusList(env);
				}).filter(str -> !str.isEmpty())
				.distinct()
				.collect(Collectors.toUnmodifiableList());
	}

	public static List<String> getAllSuccessStatusList(Environment env) {
		return Stream.of(values()).flatMap(requestType -> {
					return requestType.getSuccessStatusList(env);
				}).filter(str -> !str.isEmpty())
				.distinct()
				.collect(Collectors.toUnmodifiableList());
	}
	
	public String getName() { return  name; }

	public String getEmailSubjectTemplateCodeProperty(TemplateType templateType) {
		return String.format(PREFIX_RESIDENT_TEMPLATE_EMAIL_SUBJECT, templateType.getType(), name());
	}
	
	public String getEmailContentTemplateCodeProperty(TemplateType templateType) {
		return String.format(PREFIX_RESIDENT_TEMPLATE_EMAIL_CONTENT, templateType.getType(), name());
	}

	public String getSmsTemplateCodeProperty(TemplateType templateType) {
		return String.format(PREFIX_RESIDENT_TEMPLATE_SMS, templateType.getType(), name());
	}
	
	public String getPurposeTemplateCodeProperty(TemplateType templateType) {
		return String.format(PREFIX_RESIDENT_TEMPLATE_PURPOSE, templateType.getType(), name());
	}
	
	public String getSummaryTemplateCodeProperty(TemplateType templateType) {
		return String.format(PREFIX_RESIDENT_TEMPLATE_SUMMARY, templateType.getType(), name());
	}

	public Tuple2<Map<String, String>, String> getAckTemplateVariables(TemplateUtil templateUtil, ResidentTransactionEntity residentTransactionEntity, String languageCode, Integer timeZoneOffset, String locale) {
		return ackTemplateVariablesFunction.apply(templateUtil, residentTransactionEntity, languageCode, timeZoneOffset, locale);
	}
	
	public Map<String, Object> getNotificationTemplateVariables(TemplateUtil templateUtil, NotificationTemplateVariableDTO dto, Map<String, Object> notificationAttributes) {
		return notificationTemplateVariablesFunction.apply(templateUtil, dto, notificationAttributes);
	}

	public String getDescriptionTemplateVariables(DescriptionTemplateVariables descriptionTemplateVariables, ResidentTransactionEntity residentTransactionEntity, String fileText, String languageCode){
		return getDescriptionTemplateVariables.apply(descriptionTemplateVariables, residentTransactionEntity, fileText, languageCode);
	}
	
	public String getNamingProperty() {
		return namingProperty;
	}

	@Override
	public void preUpdateInBatchJob(Environment env, Utility utility, ResidentTransactionEntity txn,
			Map<String, String> credentialStatus, String newStatusCode)
			throws ResidentServiceCheckedException, ApisResourceAccessException {
		//Default does nothing
	}


}